"""
VIS4SRD 推理侧车：仅 Emocc / FeaLearner 神经网络推理。
Java 主服务（:8000）通过 HTTP 调用本服务（:8001）。
"""
from __future__ import annotations

import traceback
import threading
from functools import wraps

_emocc_lock = threading.RLock()
def serialized_emocc(fn):
    @wraps(fn)
    def wrapped(*args, **kwargs):
        with _emocc_lock:
            return fn(*args, **kwargs)
    return wrapped

from typing import Any, Dict, List, Optional, Literal

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

class ReviewRequest(BaseModel):
    model: Literal['emocc', 'fealearner'] = 'emocc'
    posts: List[str] = Field(...,min_length=1,max_length=32)

class ProbeRequest(BaseModel):
    train: List[str] = Field(...,min_length=2,max_length=99)
    labels: List[int] = Field(...,min_length=2,max_length=99)
    test: List[str] = Field(...,min_length=1,max_length=98)

@app.post('/infer/research-probe')
@serialized_emocc
def research_probe(body:ProbeRequest):
    from fastapi import HTTPException
    from services.research_probe import predict
    if (len(body.train)!=len(body.labels) or set(body.labels)!={0,1} or len(body.train)+len(body.test)>100
            or any(not t.strip() or len(t)>8000 for t in body.train+body.test)):
        raise HTTPException(422,'Invalid research split')
    try:return predict(body.train,body.labels,body.test)
    except (RuntimeError,OSError,ValueError,ImportError):raise HTTPException(503,'Local BERT unavailable')

@app.post('/infer/appointment-review')
@serialized_emocc
def appointment_review(body:ReviewRequest):
    from fastapi import HTTPException
    if any(not t.strip() or len(t)>20000 for t in body.posts):raise HTTPException(422,'Invalid post')
    from services.appointment_review import review
    try:
        if body.model == 'fealearner':
            from services.appointment_fealearner import review as review_features
            return review_features(body.posts)
        return review(body.posts)
    except (ImportError,RuntimeError,OSError,ValueError,KeyError):raise HTTPException(503,'Local encoder or checkpoint unavailable')


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
@serialized_emocc
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

class UploadedEmoccRequest(BaseModel):
    userHash: str = Field(..., min_length=1, max_length=100)
    dataSource: str = Field(..., pattern="^(reddit|weibo|bigdata|sigir)$")
    bertEmbeddings: List[List[float]] = Field(..., min_length=1, max_length=50)
    emojiSequences: List[List[str]] = Field(..., min_length=1, max_length=50)
    posts: List[str] = Field(..., min_length=1, max_length=50)


@app.post("/infer/emocc-upload")
@serialized_emocc
def infer_emocc_upload(body: UploadedEmoccRequest):
    """Infer from supplied features only; never substitute cached user embeddings."""
    import numpy as np
    try:
        count = len(body.bertEmbeddings)
        if len(body.posts) != count or len(body.emojiSequences) != count:
            return _fail("特征、表情与贴文数量不一致")
        if any(len(row) != 768 for row in body.bertEmbeddings):
            return _fail("每条贴文需要768维BERT特征")
        features = np.asarray(body.bertEmbeddings, dtype=np.float32)
        if not np.isfinite(features).all():
            return _fail("特征包含无效数值")
        service = get_emocc_service()
        if not service.is_loaded or getattr(service, "_loaded_dataset", None) != body.dataSource:
            if not load_emocc_model(dataset_key=body.dataSource):
                return _fail("对应数据源的Emocc权重加载失败")
            service._loaded_dataset = body.dataSource
        result = service.predict_single_user(body.userHash, features, body.emojiSequences, body.posts)
        if result.risk_class < 0:
            return _fail("Emocc模型未完成推理")
        return _ok({"userHash": body.userHash, "dataSource": body.dataSource,
                    "modelType": "emocc_local", "inputType": "uploaded_features",
                    "postCount": count, "riskClass": result.risk_class,
                    "riskLevel": result.risk_level, "confidence": result.confidence,
                    "classProbs": (result.model_info or {}).get("class_probs", []),
                    "postAttentionScores": result.post_attention_scores})
    except Exception:
        traceback.print_exc()
        return _fail("上传特征推理失败，请检查特征与模型版本是否匹配")

