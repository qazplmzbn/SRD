"""
FeaLearner 本地推理服务
封装 Fealeaner/predict_with_bestmodel.py，提供按 user_hash 的单用户预测能力。
"""
from __future__ import annotations

import csv
import importlib.util
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional, Tuple


@dataclass
class FeaLearnerInferenceResult:
    user_hash: str
    dataset: str
    person_id: Optional[str]
    pred_label: int
    true_label: Optional[int]
    risk_level: str
    risk_score: float
    confidence: float
    probabilities: Dict[str, float]
    model_info: Dict[str, Any]


class FeaLearnerInferenceService:
    """FeaLearner 推理服务。"""

    def __init__(self) -> None:
        self._predict_module = None
        self._module_loaded = False

    def _project_root(self) -> Path:
        return Path(__file__).resolve().parents[2]

    def _fealeaner_root(self) -> Path:
        return self._project_root() / "Fealeaner"

    def _load_predict_module(self):
        if self._module_loaded and self._predict_module is not None:
            return self._predict_module

        script_path = self._fealeaner_root() / "predict_with_bestmodel.py"
        if not script_path.exists():
            raise FileNotFoundError(f"未找到 FeaLearner 预测脚本: {script_path}")

        spec = importlib.util.spec_from_file_location("fealearner_predict_module", script_path)
        if spec is None or spec.loader is None:
            raise RuntimeError("加载 FeaLearner 预测模块失败")

        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self._predict_module = module
        self._module_loaded = True
        return module

    def _resolve_person_id_from_hash(self, dataset_key: str, user_hash: str) -> Optional[str]:
        from .dataset_csv import resolve_user_id
        return resolve_user_id(dataset_key, user_hash)

    def _fealearner_embedding_path(self, dataset: str) -> Path:
        """
        嵌入 pkl 唯一约定（不做其它路径探测）：
        - weibo（FeaLearner）：Fealeaner/data/user_post_embeddings_bert_wwm.pkl
        - reddit / bigdata / sigir：Emocc/<集>/data/ 下
          reddit_500_bert_embeddings.pkl、bigdata_bert_embeddings.pkl、sigir_bert_embeddings.pkl
          （与 emocc_config.embedding_pickle_file 一致；Emocc 的 weibo 为 user_post_embeddings_filtered.pkl，仅 Emocc 管线使用）
        """
        ds = (dataset or "").strip().lower()
        if ds == "weibo":
            return self._fealeaner_root() / "data" / "user_post_embeddings_bert_wwm.pkl"
        if ds in FEAL_SUPPORTED_DATASETS:
            from .emocc_config import get_emocc_dataset_spec

            spec = get_emocc_dataset_spec(ds)
            p = spec.embedding_pickle_path()
            if p is None:
                raise ValueError(f"数据集 {ds} 未配置 FeaLearner 所用嵌入 pkl")
            return p
        raise ValueError(f"未知数据集: {dataset}")

    def _default_paths(self, dataset: str) -> Tuple[Path, Path, Path]:
        """特征 CSV 与权重仅 Fealeaner；嵌入按 _fealearner_embedding_path，不做其它路径探测。"""
        module = self._load_predict_module()
        _emb_script_default, feat, ckpt = module._default_paths(dataset)
        emb_path = self._fealearner_embedding_path(dataset)
        return emb_path, Path(feat), Path(ckpt)

    @staticmethod
    def _map_label_to_risk(dataset: str, pred_label: int) -> Tuple[str, float]:
        # 与现有页面等级保持一致：low / medium / high
        if dataset == "reddit":
            mapping = {
                0: ("low", 0.10),
                1: ("low", 0.30),
                2: ("medium", 0.50),
                3: ("medium", 0.70),
                4: ("high", 0.90),
            }
            return mapping.get(pred_label, ("medium", 0.50))
        if dataset in ("weibo", "sigir"):
            return ("high", 0.90) if pred_label >= 1 else ("low", 0.20)
        if dataset == "bigdata":
            mapping = {
                0: ("low", 0.15),
                1: ("low", 0.35),
                2: ("medium", 0.65),
                3: ("high", 0.90),
            }
            return mapping.get(pred_label, ("medium", 0.50))
        return ("medium", 0.50)

    def predict_single_user(self, user_hash: str, dataset: str = "reddit", seed: int = 24) -> FeaLearnerInferenceResult:
        module = self._load_predict_module()
        person_id = self._resolve_person_id_from_hash(dataset, user_hash)
        if not person_id:
            raise ValueError(f"无法在 {dataset} 数据集中根据 user_hash={user_hash} 找到原始 user_id")

        emb, feat, ckpt = self._default_paths(dataset)
        for p, name in ((emb, "embeddings"), (feat, "features"), (ckpt, "checkpoint")):
            if not p.exists():
                raise FileNotFoundError(f"FeaLearner {name} 文件不存在: {p}")

        result = module.predict_one_person(
            dataset=dataset,
            emb_path=emb,
            feat_path=feat,
            ckpt_path=ckpt,
            sample_index=None,
            person_id=person_id,
            output_path=None,
            seed=seed,
        )

        pred_label = int(result.get("pred_label", 0))
        true_label = result.get("true_label")
        probabilities = result.get("probabilities", {}) or {}
        confidence = float(max(probabilities.values())) if probabilities else 0.0
        risk_level, risk_score = self._map_label_to_risk(dataset, pred_label)

        return FeaLearnerInferenceResult(
            user_hash=user_hash,
            dataset=dataset,
            person_id=result.get("person_id"),
            pred_label=pred_label,
            true_label=int(true_label) if true_label is not None else None,
            risk_level=risk_level,
            risk_score=risk_score,
            confidence=confidence,
            probabilities={str(k): float(v) for k, v in probabilities.items()},
            model_info={
                "model_type": "fealearner_local",
                "dataset": dataset,
                "person_id": result.get("person_id"),
                "sample_index": result.get("sample_index"),
            },
        )


_fealearner_service: Optional[FeaLearnerInferenceService] = None

# 与 Fealeaner/predict_with_bestmodel.py 中 _default_paths 支持的数据集一致
FEAL_SUPPORTED_DATASETS = frozenset({"reddit", "weibo", "bigdata", "sigir"})

_FEAL_DATASET_LABEL = {
    "reddit": "Reddit",
    "weibo": "Weibo",
    "bigdata": "BigData",
    "sigir": "SIGIR",
}

# 模型中心列表中的产品名（不含具体数据源，避免与「仅 Reddit」混淆）
FEAL_MODEL_CATALOG_DISPLAY = "FeaLearner"

# 模型中心 / API 展示的权威说明（与 Fealeaner/predict_with_bestmodel 行为一致）
FEAL_MODEL_CENTER_DESCRIPTION = (
    "基于 BERT 文本嵌入与统计特征的本地深度分类模型。"
    "reddit/bigdata/sigir 的嵌入仅读取 Emocc/<数据集>/data/ 下与 emocc_config 一致的 pkl；"
    "weibo 嵌入仅读取 Fealeaner/data/user_post_embeddings_bert_wwm.pkl；"
    "特征与权重仅读取 Fealeaner/feature_data 与 Fealeaner/bestmodel（与 predict_with_bestmodel 默认文件名一致）。"
    "路径不做多目录兜底，缺失即报错。"
)

# 模型中心展示用（与库中历史 model_code / 单一路径脱钩）
FEAL_MODEL_CENTER_CODE = "fealearner"
FEAL_MODEL_CENTER_WEIGHTS_DISPLAY = (
    "Fealeaner/bestmodel：reddit→my_reddit_model.pth；weibo→my_weibo_model.pth；"
    "bigdata→my_bigdata_model.pth；sigir→my_sigir_model.pth（由任务 data_source 选用）"
)
FEAL_MODEL_CENTER_EMBED_DISPLAY = (
    "FeaLearner：weibo→Fealeaner/data/user_post_embeddings_bert_wwm.pkl；"
    "reddit→Emocc/reddit/data/reddit_500_bert_embeddings.pkl；"
    "bigdata→Emocc/bigdata/data/bigdata_bert_embeddings.pkl；"
    "sigir→Emocc/sigir/data/sigir_bert_embeddings.pkl。"
    "Emocc-weibo 嵌入为 Emocc/weibo/data/user_post_embeddings_filtered.pkl（与 FeaLearner 的 weibo pkl 分离）。"
)


def fealearner_model_display_name(data_source: str | None = None) -> str:
    """任务/报告中的展示名：带数据源后缀，便于区分当前检测所用的内置集。"""
    ds = (data_source or "reddit").strip().lower()
    if ds not in FEAL_SUPPORTED_DATASETS:
        ds = "reddit"
    return f"FeaLearner-{_FEAL_DATASET_LABEL[ds]}"


def fealearner_dataset_label(data_source: str) -> str:
    """可读数据源名（Reddit / Weibo 等）。"""
    ds = (data_source or "reddit").strip().lower()
    if ds not in FEAL_SUPPORTED_DATASETS:
        ds = "reddit"
    return _FEAL_DATASET_LABEL[ds]


def get_fealearner_service() -> FeaLearnerInferenceService:
    global _fealearner_service
    if _fealearner_service is None:
        _fealearner_service = FeaLearnerInferenceService()
    return _fealearner_service
