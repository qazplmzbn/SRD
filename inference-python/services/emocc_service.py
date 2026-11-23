"""
Emocc模型推理服务
基于 BERT + Emoji 双模态层次融合模型进行风险检测
支持输出每个帖子的注意力分数。

说明：
1. 不在模块导入阶段固定导入 Reddit 的 Emocc_model。
2. 在 load_model(dataset_key=...) 时，按数据集绝对路径独立加载 model.py 和 emoji_utils.py。
3. 保留原有 BertEmojiModelWithAttention 的参数命名，确保可以加载现有训练权重。
"""

import importlib.util
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, List, Optional

import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F


def _project_root() -> Path:
    """
    inference-python/services/emocc_service.py
    parents[0] = services
    parents[1] = inference-python
    parents[2] = project root
    """
    return Path(__file__).resolve().parents[2]


def _load_module_from_file(module_name: str, file_path: Path):
    """
    从指定文件独立加载模块。

    不使用 Emocc_model.model 这种相同的顶层模块名，
    避免 Reddit / Weibo 因 sys.modules 缓存而串用。
    """
    file_path = Path(file_path).resolve()

    if not file_path.is_file():
        raise FileNotFoundError(f"模块文件不存在: {file_path}")

    # 如果同一数据集模块已加载，且路径一致，则复用。
    cached = sys.modules.get(module_name)
    if cached is not None:
        cached_file = getattr(cached, "__file__", None)
        if cached_file and Path(cached_file).resolve() == file_path:
            return cached
        sys.modules.pop(module_name, None)

    spec = importlib.util.spec_from_file_location(module_name, str(file_path))
    if spec is None or spec.loader is None:
        raise ImportError(f"无法创建模块加载器: {file_path}")

    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module

    try:
        spec.loader.exec_module(module)
    except Exception:
        sys.modules.pop(module_name, None)
        raise

    return module


@dataclass
class EmoccInferenceResult:
    """Emocc 推理结果。"""
    user_hash: str
    risk_level: str
    risk_score: float
    risk_class: int
    confidence: float
    post_attention_scores: List[Dict[str, Any]]
    model_info: Dict[str, Any]


@dataclass
class ModelArgs:
    """推理模型参数；实际类别数由数据集配置决定。"""
    dropout: float = 0.5
    gru_size: int = 128
    class_num: int = 5


class GlobalSelfAttentionWithScores(nn.Module):
    """
    与训练模型 GlobalSelfAttention 计算保持一致，
    额外返回每条帖子对应的注意力分数。

    注意：这里保留 W_Q / W_K / W_V 的命名和维度，
    使其 state_dict 与训练权重兼容。
    """

    def __init__(self, input_dim: int, hidden_dim: Optional[int] = None):
        super().__init__()
        self.input_dim = input_dim
        self.hidden_dim = hidden_dim or input_dim

        self.W_Q = nn.Linear(input_dim, self.hidden_dim)
        self.W_K = nn.Linear(input_dim, self.hidden_dim)
        self.W_V = nn.Linear(input_dim, self.hidden_dim)
        self.scale = torch.sqrt(torch.tensor(self.hidden_dim, dtype=torch.float32))

        self._init_weights()

    def _init_weights(self):
        nn.init.xavier_uniform_(self.W_Q.weight)
        nn.init.xavier_uniform_(self.W_K.weight)
        nn.init.xavier_uniform_(self.W_V.weight)
        nn.init.zeros_(self.W_Q.bias)
        nn.init.zeros_(self.W_K.bias)
        nn.init.zeros_(self.W_V.bias)

    def forward(self, x, mask=None, return_scores: bool = False):
        batch_size, seq_len, _ = x.size()

        q = self.W_Q(x)
        k = self.W_K(x)
        v = self.W_V(x)

        attn_scores = torch.matmul(q, k.transpose(-2, -1)) / self.scale

        if mask is not None:
            # 屏蔽无效帖子作为 key 的位置。
            key_mask = mask.unsqueeze(1)
            attn_scores = attn_scores.masked_fill(key_mask == 0, -1e9)

        attn_weights = F.softmax(attn_scores, dim=-1)
        v_common = torch.matmul(attn_weights, v)

        if mask is not None:
            query_mask = mask.unsqueeze(-1).to(dtype=v_common.dtype)
            v_common = v_common * query_mask

        # 与训练版 GlobalSelfAttention 一样，对时间维求和。
        v_common = v_common.sum(dim=1)

        if not return_scores:
            return v_common

        # 对有效 query 的注意力分布取平均，得到每条帖子的重要性分数。
        if mask is None:
            queries = torch.ones(
                batch_size, seq_len,
                dtype=attn_weights.dtype,
                device=attn_weights.device
            )
        else:
            queries = mask.to(dtype=attn_weights.dtype, device=attn_weights.device)

        post_scores = (
            attn_weights * queries.unsqueeze(-1)
        ).sum(dim=1) / queries.sum(dim=1, keepdim=True).clamp_min(1.0)

        return v_common, post_scores


class BertEmojiModelWithAttention(nn.Module):
    """
    SRD 推理包装模型。

    保留训练权重所需的参数名称：
      emoji_embed
      gru1
      global_attention
      adaptive_gate_fusion
      class_fc

    global_attention 使用可返回 attention score 的同构实现。
    AdaptiveGateFusion 与 MLP 从当前 dataset 的 model.py 注入。
    """

    def __init__(
        self,
        args,
        emoji_vocab_size: int,
        device,
        emoji_weights=None,
        is_pretrain_emoji: bool = False,
        adaptive_gate_fusion_cls=None,
        mlp_cls=None,
    ):
        super().__init__()

        if adaptive_gate_fusion_cls is None:
            raise RuntimeError("未传入当前数据集的 AdaptiveGateFusion")
        if mlp_cls is None:
            raise RuntimeError("未传入当前数据集的 MultiLayerPerceptron")

        self.args = args
        self.device = torch.device(device)
        self.dropout = args.dropout
        self.gru_size = args.gru_size
        self.class_num = args.class_num
        self.bert_dim = 768
        self.emoji_dim = 300

        if is_pretrain_emoji and emoji_weights is not None:
            if tuple(emoji_weights.shape) != (emoji_vocab_size, self.emoji_dim):
                raise ValueError(
                    "Emoji2Vec 权重形状不匹配："
                    f"期望 {(emoji_vocab_size, self.emoji_dim)}，"
                    f"实际 {tuple(emoji_weights.shape)}"
                )
            self.emoji_embed = nn.Embedding.from_pretrained(
                emoji_weights, freeze=False
            )
        else:
            self.emoji_embed = nn.Embedding(
                emoji_vocab_size, self.emoji_dim
            )

        self.emoji_embed_drop = nn.Dropout(args.dropout)

        self.gru1 = nn.GRU(
            input_size=self.bert_dim,
            hidden_size=self.gru_size,
            num_layers=1,
            bidirectional=True,
            batch_first=True,
        )
        self.gru1_drop = nn.Dropout(args.dropout)

        self.global_attention = GlobalSelfAttentionWithScores(
            input_dim=2 * self.gru_size + self.emoji_dim,
            hidden_dim=2 * self.gru_size,
        )

        self.adaptive_gate_fusion = adaptive_gate_fusion_cls(
            2 * self.gru_size
        )

        self.class_fc = mlp_cls(
            input_dim=2 * self.gru_size,
            embed_dims=[
                2 * self.gru_size,
                self.gru_size,
                self.gru_size // 2,
            ],
            dropout=self.dropout,
            class_num=self.class_num,
        )

        self._init_weights()

    def _init_weights(self):
        # 仅初始化 GRU；随后会加载训练 checkpoint 覆盖对应参数。
        for name, param in self.gru1.named_parameters():
            if "weight" in name:
                nn.init.xavier_uniform_(param)

    def forward(
        self,
        bert_embeddings,
        emoji_ids,
        post_masks,
        emoji_masks,
        return_attention: bool = False,
    ):
        batch_size, total_posts, _ = bert_embeddings.size()

        post_lengths = post_masks.sum(dim=1).to(dtype=torch.long).cpu()
        non_zero_indices = (post_lengths > 0).nonzero(as_tuple=True)[0]

        if len(non_zero_indices) == 0:
            empty_logits = bert_embeddings.new_zeros(
                batch_size, self.class_num
            )
            empty_attention = bert_embeddings.new_zeros(
                batch_size, total_posts
            )
            if return_attention:
                return empty_logits, empty_attention
            return empty_logits

        valid_bert = bert_embeddings[non_zero_indices]
        valid_lengths = post_lengths[non_zero_indices]

        packed_bert = nn.utils.rnn.pack_padded_sequence(
            valid_bert,
            valid_lengths,
            batch_first=True,
            enforce_sorted=False,
        )
        gru1_output, _ = self.gru1(packed_bert)
        gru1_output, _ = nn.utils.rnn.pad_packed_sequence(
            gru1_output,
            batch_first=True,
        )
        gru1_output = self.gru1_drop(gru1_output)

        emoji_embeds = self.emoji_embed(emoji_ids)
        emoji_embeds = self.emoji_embed_drop(emoji_embeds)

        emoji_mask_expanded = emoji_masks.unsqueeze(-1).to(
            dtype=emoji_embeds.dtype
        )
        emoji_embeds_masked = emoji_embeds * emoji_mask_expanded
        emoji_counts = emoji_masks.sum(
            dim=-1, keepdim=True
        ).clamp(min=1)
        emoji_mean = emoji_embeds_masked.sum(dim=2) / emoji_counts

        max_len = int(valid_lengths.max().item())
        max_len = max(max_len, 1)

        valid_emoji = emoji_mean[non_zero_indices][:, :max_len, :]
        fused_features = torch.cat(
            [gru1_output, valid_emoji],
            dim=-1,
        )

        valid_post_masks = post_masks[non_zero_indices][:, :max_len]

        # 找到每个用户最后一条有效帖子对应的 BiGRU 表示。
        last_indices = (
            valid_post_masks.cumsum(dim=1)
            == valid_post_masks.sum(dim=1, keepdim=True)
        ).to(dtype=gru1_output.dtype)

        v_short_valid = (
            gru1_output * last_indices.unsqueeze(-1)
        ).sum(dim=1)

        v_common_valid, post_attn_scores_valid = self.global_attention(
            fused_features,
            mask=valid_post_masks,
            return_scores=True,
        )

        v_short = bert_embeddings.new_zeros(
            batch_size, 2 * self.gru_size
        )
        v_short[non_zero_indices] = v_short_valid

        v_common = bert_embeddings.new_zeros(
            batch_size, 2 * self.gru_size
        )
        v_common[non_zero_indices] = v_common_valid

        post_attn_scores = bert_embeddings.new_zeros(
            batch_size, total_posts
        )
        valid_score_len = post_attn_scores_valid.size(1)
        post_attn_scores[
            non_zero_indices, :valid_score_len
        ] = post_attn_scores_valid

        v_final = self.adaptive_gate_fusion(v_short, v_common)
        logits = self.class_fc(v_final)

        if return_attention:
            return logits, post_attn_scores
        return logits


class EmoccInferenceService:
    """Emocc 模型推理服务。"""

    CLASS_TO_RISK = {
        0: ("low", 0.1),
        1: ("low", 0.3),
        2: ("medium", 0.5),
        3: ("medium", 0.7),
        4: ("high", 0.9),
    }

    def __init__(self):
        self.model = None
        self.emoji_to_id = None
        self.device = torch.device("cpu")
        self.is_loaded = False
        self.model_config = None
        self.dataset_key = None
        self.model_module = None
        self.emoji_utils_module = None

    def _get_paths(self, dataset_key: str = "reddit"):
        """按数据集获取模型与资源路径。"""
        from .emocc_config import get_emocc_dataset_spec

        spec = get_emocc_dataset_spec(dataset_key)
        model_path = (
            spec.checkpoint_path()
            if spec.checkpoint_path().exists()
            else None
        )
        bert_path = spec.embedding_pickle_path()
        emoji_csv_path = spec.emoji_csv_path()
        emoji2vec_path = spec.emoji2vec_path()

        return (
            model_path,
            bert_path,
            emoji_csv_path,
            emoji2vec_path,
            spec,
        )

    @staticmethod
    def _extract_state_dict(checkpoint):
        """兼容常见 checkpoint 包装格式。"""
        if not isinstance(checkpoint, dict):
            raise TypeError(
                f"Checkpoint 应为 dict，实际为 {type(checkpoint).__name__}"
            )

        if "model_state_dict" in checkpoint:
            state_dict = checkpoint["model_state_dict"]
        elif "state_dict" in checkpoint:
            state_dict = checkpoint["state_dict"]
        else:
            state_dict = checkpoint

        if not isinstance(state_dict, dict):
            raise TypeError("Checkpoint 中未找到有效的 state_dict")

        # 兼容 DataParallel 保存的 module. 前缀。
        if state_dict and all(
            isinstance(key, str) and key.startswith("module.")
            for key in state_dict
        ):
            state_dict = {
                key[len("module."):]: value
                for key, value in state_dict.items()
            }

        return state_dict

    def load_model(
        self,
        model_path: Optional[str] = None,
        use_pretrained_emoji: bool = True,
        dataset_key: str = "reddit",
    ) -> bool:
        """
        加载指定数据集的 EmoCC 模型。

        dataset_key 例如：weibo / reddit。
        """
        self.is_loaded = False
        self.model = None
        self.emoji_to_id = None
        self.model_config = None

        try:
            paths = self._get_paths(dataset_key)
            default_model_path, bert_path, emoji_csv, emoji2vec_path, spec = paths

            dataset_key = str(
                getattr(spec, "dataset_key", dataset_key)
            ).lower()

            if dataset_key not in {"weibo", "reddit"}:
                raise ValueError(
                    f"暂不支持的数据集: {dataset_key}"
                )

            # 通过配置对象取得该数据集的 Emocc 根目录。
            dataset_root = Path(spec.emocc_root()).resolve()
            model_file = (
                Path(model_path).resolve()
                if model_path
                else (
                    Path(default_model_path).resolve()
                    if default_model_path is not None
                    else None
                )
            )

            if model_file is None or not model_file.is_file():
                raise FileNotFoundError(
                    f"模型权重文件不存在: {model_file}"
                )

            if not Path(emoji_csv).is_file():
                raise FileNotFoundError(
                    f"Emoji CSV 不存在: {emoji_csv}"
                )

            model_py = dataset_root / "Emocc_model" / "model.py"
            emoji_utils_py = (
                dataset_root
                / "Emocc_model"
                / "utils"
                / "emoji_utils.py"
            )

            # 每个数据集使用独立模块名，避免 Emocc_model 同名缓存冲突。
            model_module = _load_module_from_file(
                f"_srd_emocc_{dataset_key}_model",
                model_py,
            )
            emoji_utils_module = _load_module_from_file(
                f"_srd_emocc_{dataset_key}_emoji_utils",
                emoji_utils_py,
            )

            required_model_components = (
                "BertEmojiModel",
                "AdaptiveGateFusion",
                "MultiLayerPerceptron",
            )
            for component_name in required_model_components:
                if not hasattr(model_module, component_name):
                    raise ImportError(
                        f"{dataset_key} model.py 缺少组件: "
                        f"{component_name}"
                    )

            for function_name in (
                "get_emoji_vocabulary",
                "load_pretrained_emoji_weights",
            ):
                if not hasattr(emoji_utils_module, function_name):
                    raise ImportError(
                        f"{dataset_key} emoji_utils.py 缺少函数: "
                        f"{function_name}"
                    )

            print(
                f"[EmoccService] 数据集: {dataset_key}"
            )
            print(
                f"[EmoccService] 模型模块: {model_module.__file__}"
            )
            print(
                f"[EmoccService] Emoji 工具: "
                f"{emoji_utils_module.__file__}"
            )

            # 从当前数据集配置读取类别数和风险映射。
            class_num = int(
                getattr(spec, "class_num", 5) or 5
            )
            if class_num <= 0:
                raise ValueError(
                    f"非法 class_num: {class_num}"
                )

            coarse_mapping = (
                getattr(spec, "coarse_risk_mapping", {}) or {}
            )
            score_mapping = (
                getattr(spec, "risk_score_mapping", {}) or {}
            )

            self.CLASS_TO_RISK = {}
            for class_index in range(class_num):
                risk_level = coarse_mapping.get(
                    class_index,
                    coarse_mapping.get(str(class_index), "medium"),
                )
                risk_score = score_mapping.get(
                    class_index,
                    score_mapping.get(str(class_index), 0.5),
                )
                self.CLASS_TO_RISK[class_index] = (
                    str(risk_level),
                    float(risk_score),
                )

            # 使用当前数据集自己的 emoji_utils 构建词汇表。
            print("[EmoccService] 构建 Emoji 词汇表...")
            emoji_vocab, emoji_to_id = (
                emoji_utils_module.get_emoji_vocabulary(
                    str(emoji_csv)
                )
            )

            if not emoji_vocab or not isinstance(emoji_to_id, dict):
                raise RuntimeError(
                    "Emoji 词汇表构建结果无效"
                )
            if len(emoji_vocab) != len(emoji_to_id):
                raise RuntimeError(
                    "Emoji 词汇表与 emoji_to_id 长度不一致"
                )

            self.emoji_to_id = emoji_to_id

            # 按需加载 Emoji2Vec 初始化矩阵。
            emoji_weights = None
            if use_pretrained_emoji:
                if not Path(emoji2vec_path).is_file():
                    raise FileNotFoundError(
                        f"已要求使用预训练 Emoji，但文件不存在: "
                        f"{emoji2vec_path}"
                    )

                print(
                    f"[EmoccService] 加载 Emoji2Vec: {emoji2vec_path}"
                )
                emoji_weights_np = (
                    emoji_utils_module.load_pretrained_emoji_weights(
                        emoji_vocab,
                        str(emoji2vec_path),
                        embedding_dim=300,
                    )
                )
                emoji_weights_np = np.asarray(
                    emoji_weights_np,
                    dtype=np.float32,
                )

                expected_shape = (len(emoji_vocab), 300)
                if emoji_weights_np.shape != expected_shape:
                    raise ValueError(
                        "Emoji2Vec 矩阵形状不匹配："
                        f"期望 {expected_shape}，"
                        f"实际 {emoji_weights_np.shape}"
                    )
                if not np.isfinite(emoji_weights_np).all():
                    raise ValueError(
                        "Emoji2Vec 矩阵包含 NaN 或 Inf"
                    )

                emoji_weights = torch.from_numpy(
                    emoji_weights_np
                )

            args = ModelArgs(
                dropout=0.5,
                gru_size=128,
                class_num=class_num,
            )

            print("[EmoccService] 创建双模态推理模型...")
            model = BertEmojiModelWithAttention(
                args=args,
                emoji_vocab_size=len(emoji_vocab),
                device=self.device,
                emoji_weights=emoji_weights,
                is_pretrain_emoji=(
                    use_pretrained_emoji
                    and emoji_weights is not None
                ),
                adaptive_gate_fusion_cls=(
                    model_module.AdaptiveGateFusion
                ),
                mlp_cls=model_module.MultiLayerPerceptron,
            )

            print(
                f"[EmoccService] 加载权重: {model_file}"
            )
            checkpoint = torch.load(
                model_file,
                map_location=self.device,
            )
            state_dict = self._extract_state_dict(checkpoint)

            # 严格加载：不允许遗漏或多余参数静默通过。
            incompatible = model.load_state_dict(
                state_dict,
                strict=True,
            )

            model.to(self.device)
            model.eval()

            self.model = model
            self.model_module = model_module
            self.emoji_utils_module = emoji_utils_module
            self.dataset_key = dataset_key

            self.model_config = {
                "dataset_key": dataset_key,
                "model_path": str(model_file),
                "model_module": str(model_py),
                "emoji_utils_module": str(emoji_utils_py),
                "bert_embedding_path": str(bert_path),
                "vocab_size": len(emoji_vocab),
                "emoji_csv": str(emoji_csv),
                "emoji2vec_path": (
                    str(emoji2vec_path)
                    if use_pretrained_emoji
                    else None
                ),
                "use_pretrained_emoji": (
                    use_pretrained_emoji
                    and emoji_weights is not None
                ),
                "class_num": class_num,
                "gru_size": args.gru_size,
                "bert_dim": 768,
                "emoji_dim": 300,
                "strict_checkpoint_loading": True,
                "missing_keys": list(incompatible.missing_keys),
                "unexpected_keys": list(incompatible.unexpected_keys),
            }

            self.is_loaded = True
            print(
                f"[EmoccService] {dataset_key} 模型加载成功！"
            )
            print(
                f"[EmoccService] Emoji 词汇表大小: "
                f"{len(emoji_vocab)}"
            )
            print(
                f"[EmoccService] Checkpoint 严格加载: 成功"
            )
            return True

        except Exception as exc:
            self.is_loaded = False
            self.model = None
            print(f"[EmoccService] 模型加载失败: {exc}")
            import traceback
            traceback.print_exc()
            return False

    def predict_single_user(
        self,
        user_hash: str,
        bert_embeddings: np.ndarray,
        emoji_sequences: List[List[str]],
        post_texts: Optional[List[str]] = None,
    ) -> EmoccInferenceResult:
        """对单个用户的帖子序列进行双模态风险预测。"""

        if not self.is_loaded or self.model is None:
            return EmoccInferenceResult(
                user_hash=user_hash,
                risk_level="unknown",
                risk_score=0.0,
                risk_class=-1,
                confidence=0.0,
                post_attention_scores=[],
                model_info={"error": "模型未加载"},
            )

        try:
            if bert_embeddings is None:
                raise ValueError("bert_embeddings 不能为 None")

            bert_embeddings = np.asarray(
                bert_embeddings,
                dtype=np.float32,
            )

            if bert_embeddings.ndim != 2:
                raise ValueError(
                    "bert_embeddings 必须是二维数组 (T, 768)，"
                    f"实际形状: {bert_embeddings.shape}"
                )

            if bert_embeddings.shape[1] != 768:
                raise ValueError(
                    "BERT 特征维度必须为 768，"
                    f"实际为 {bert_embeddings.shape[1]}"
                )

            if not np.isfinite(bert_embeddings).all():
                raise ValueError(
                    "BERT embeddings 包含 NaN 或 Inf"
                )

            total_posts = int(bert_embeddings.shape[0])
            if total_posts == 0:
                raise ValueError(
                    "用户没有可供推理的帖子"
                )

            if emoji_sequences is None:
                emoji_sequences = []

            batch_size = 1
            max_emojis = 10
            max_posts = min(50, total_posts)

            bert_embeds = torch.zeros(
                (batch_size, max_posts, 768),
                dtype=torch.float32,
                device=self.device,
            )
            post_mask = torch.zeros(
                (batch_size, max_posts),
                dtype=torch.float32,
                device=self.device,
            )

            bert_embeds[0] = torch.from_numpy(
                bert_embeddings[:max_posts]
            ).to(self.device)
            post_mask[0] = 1.0

            emoji_ids = torch.zeros(
                (batch_size, max_posts, max_emojis),
                dtype=torch.long,
                device=self.device,
            )
            emoji_mask = torch.zeros(
                (batch_size, max_posts, max_emojis),
                dtype=torch.float32,
                device=self.device,
            )

            unknown_id = self.emoji_to_id.get("<UNK>", 1)

            for post_index in range(max_posts):
                emojis = (
                    emoji_sequences[post_index]
                    if post_index < len(emoji_sequences)
                    else []
                )
                if emojis is None:
                    emojis = []

                for emoji_index, emoji in enumerate(
                    emojis[:max_emojis]
                ):
                    emoji_id = self.emoji_to_id.get(
                        emoji,
                        unknown_id,
                    )
                    emoji_ids[0, post_index, emoji_index] = int(
                        emoji_id
                    )
                    emoji_mask[0, post_index, emoji_index] = 1.0

            with torch.no_grad():
                logits, attention_scores = self.model(
                    bert_embeds,
                    emoji_ids,
                    post_mask,
                    emoji_mask,
                    return_attention=True,
                )

            probabilities = F.softmax(logits, dim=-1)
            predicted_class = int(
                torch.argmax(probabilities, dim=-1).item()
            )
            confidence = float(
                probabilities[0, predicted_class].item()
            )

            risk_level, risk_score = self.CLASS_TO_RISK.get(
                predicted_class,
                ("medium", 0.5),
            )

            attention_np = (
                attention_scores[0]
                .detach()
                .cpu()
                .numpy()
            )

            post_attention_scores = []
            for index in range(max_posts):
                if post_texts and index < len(post_texts):
                    text = str(post_texts[index])
                    text_preview = (
                        text[:100] + "..."
                        if len(text) > 100
                        else text
                    )
                else:
                    text_preview = f"Post {index + 1}"

                post_emojis = (
                    emoji_sequences[index]
                    if index < len(emoji_sequences)
                    and emoji_sequences[index] is not None
                    else []
                )

                post_attention_scores.append(
                    {
                        "post_index": index,
                        "attention_score": float(
                            attention_np[index]
                        ),
                        "text_preview": text_preview,
                        "emoji_count": len(post_emojis),
                    }
                )

            post_attention_scores.sort(
                key=lambda item: item["attention_score"],
                reverse=True,
            )

            return EmoccInferenceResult(
                user_hash=user_hash,
                risk_level=risk_level,
                risk_score=float(risk_score),
                risk_class=predicted_class,
                confidence=confidence,
                post_attention_scores=post_attention_scores,
                model_info={
                    "model_type": "emocc",
                    "dataset_key": self.dataset_key,
                    "total_posts": total_posts,
                    "analyzed_posts": max_posts,
                    "class_probs": (
                        probabilities[0].detach().cpu().tolist()
                    ),
                },
            )

        except Exception as exc:
            print(f"[EmoccService] 推理失败: {exc}")
            import traceback
            traceback.print_exc()

            return EmoccInferenceResult(
                user_hash=user_hash,
                risk_level="unknown",
                risk_score=0.0,
                risk_class=-1,
                confidence=0.0,
                post_attention_scores=[],
                model_info={"error": str(exc)},
            )

    def predict_batch(
        self,
        user_data_list: List[Dict[str, Any]],
    ) -> List[EmoccInferenceResult]:
        """批量预测多个用户。"""
        results = []

        for user_data in user_data_list:
            result = self.predict_single_user(
                user_hash=user_data.get("user_hash", ""),
                bert_embeddings=user_data.get("bert_embeddings"),
                emoji_sequences=user_data.get("emoji_sequences", []),
                post_texts=user_data.get("post_texts"),
            )
            results.append(result)

        return results


# 全局单例
_emocc_service: Optional[EmoccInferenceService] = None


def get_emocc_service() -> EmoccInferenceService:
    """获取全局 Emocc 推理服务单例。"""
    global _emocc_service

    if _emocc_service is None:
        _emocc_service = EmoccInferenceService()

    return _emocc_service


def load_emocc_model(
    model_path: Optional[str] = None,
    use_pretrained_emoji: bool = True,
    dataset_key: str = "reddit",
) -> bool:
    """加载指定数据集的 Emocc 模型。"""
    service = get_emocc_service()
    return service.load_model(
        model_path=model_path,
        use_pretrained_emoji=use_pretrained_emoji,
        dataset_key=dataset_key,
    )
