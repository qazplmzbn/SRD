"""
VIS4SRD 推理侧车：仅 Emocc / FeaLearner 神经网络推理。
Java 主服务（:8000）通过 HTTP 调用本服务（:8001）。
"""
from __future__ import annotations

import traceback
from typing import Any, Dict, List, Optional

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from services.dataset_csv import load_user_posts
from services.embedding_loader import load_emocc_features
from services.emocc_service import get_emocc_service, load_emocc_model
from services.fealearner_service import get_fealearner_service

app = FastAPI(title="VIS4SRD Inference Sidecar", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


class InferRequest(BaseModel):
    userHash: str = Field(..., min_length=1)
    dataSource: str = "reddit"
    posts: Optional[List[str]] = None


def _ok(data: Dict[str, Any]) -> Dict[str, Any]:
    return {"success": True, "data": data}


def _fail(error: str) -> Dict[str, Any]:
    return {"success": False, "error": error}


@app.get("/health")
def health():
    fea_ok = True
    try:
        get_fealearner_service()
    except Exception:
        fea_ok = False
    emocc = get_emocc_service()
    return {
        "status": "ok",
        "emocc_loaded": bool(getattr(emocc, "is_loaded", False)),
        "fealearner": fea_ok,
    }


@app.post("/infer/fealearner")
def infer_fealearner(body: InferRequest):
    try:
        ds = (body.dataSource or "reddit").strip().lower()
        svc = get_fealearner_service()
        result = svc.predict_single_user(user_hash=body.userHash.strip(), dataset=ds)
        probs = result.probabilities or {}
        prob_list = []
        # probabilities may be class_0.. or ordered
        if probs:
            keys = sorted(probs.keys(), key=lambda k: int("".join(ch for ch in k if ch.isdigit()) or "0"))
            prob_list = [float(probs[k]) for k in keys]
        data = {
            "userHash": result.user_hash,
            "dataSource": result.dataset,
            "personId": result.person_id,
            "predLabel": result.pred_label,
            "trueLabel": result.true_label,
            "riskLevel": result.risk_level,
            "riskScore": result.risk_score,
            "confidence": result.confidence,
            "probabilities": prob_list if prob_list else list(probs.values()),
            "probabilityMap": result.probabilities,
            "modelType": "fealearner_local",
            "modelInfo": result.model_info,
        }
        return _ok(data)
    except Exception as ex:
        traceback.print_exc()
        return _fail(str(ex))


@app.post("/infer/emocc")
def infer_emocc(body: InferRequest):
    try:
        ds = (body.dataSource or "reddit").strip().lower()
        user_hash = body.userHash.strip()

        person_id, csv_posts = load_user_posts(ds, user_hash)
        posts = body.posts if body.posts else csv_posts
        if not posts:
            return _fail(f"无法获取用户 {user_hash} 的帖子（dataSource={ds}）")

        feats = load_emocc_features(ds, user_hash)
        if feats is None or feats.get("bert_embeddings") is None:
            return _fail(f"无法在 Emocc 嵌入 pkl 中匹配 userHash={user_hash}（dataSource={ds}）")

        bert = feats["bert_embeddings"]
        emoji_sequences = feats.get("emoji_sequences") or [[] for _ in range(len(posts))]
        # 对齐长度
        T = int(bert.shape[0]) if hasattr(bert, "shape") else len(posts)
        while len(emoji_sequences) < T:
            emoji_sequences.append([])
        emoji_sequences = emoji_sequences[:T]
        post_texts = list(posts)[:T]
        while len(post_texts) < T:
            post_texts.append(f"Post {len(post_texts) + 1}")

        service = get_emocc_service()
        if not service.is_loaded or getattr(service, "_loaded_dataset", None) != ds:
            ok = load_emocc_model(dataset_key=ds)
            if not ok:
                return _fail(f"Emocc 模型加载失败 dataset={ds}")
            service._loaded_dataset = ds

        result = service.predict_single_user(
            user_hash=user_hash,
            bert_embeddings=bert,
            emoji_sequences=emoji_sequences,
            post_texts=post_texts,
        )
        if result.risk_class < 0:
            return _fail(str((result.model_info or {}).get("error") or "Emocc 推理失败"))

        data = {
            "userHash": result.user_hash,
            "dataSource": ds,
            "personId": feats.get("person_id") or person_id,
            "riskLevel": result.risk_level,
            "riskScore": result.risk_score,
            "riskClass": result.risk_class,
            "confidence": result.confidence,
            "postAttentionScores": result.post_attention_scores,
            "classProbs": (result.model_info or {}).get("class_probs", []),
            "postCount": len(post_texts),
            "modelType": "emocc_local",
            "modelInfo": result.model_info,
        }
        return _ok(data)
    except Exception as ex:
        traceback.print_exc()
        return _fail(str(ex))
