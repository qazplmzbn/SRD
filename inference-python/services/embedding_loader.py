"""从 Emocc 嵌入 pkl 按 user_hash 取 BERT 向量与 emoji。"""
from __future__ import annotations

import pickle
from typing import Any, Dict, List, Optional, Tuple

import numpy as np

from .emocc_config import get_emocc_dataset_spec
from .hash_util import generate_user_hash


def _record_user_id(item: Any) -> Optional[str]:
    if not isinstance(item, dict):
        return None
    for k in ("user", "user_id", "author", "author_id", "reddit_id", "name", "id"):
        v = item.get(k)
        if v is not None and str(v).strip() != "":
            return str(v).strip()
    return None


def _record_embeddings(item: Dict[str, Any]):
    emb = item.get("embeddings")
    if emb is None:
        return None
    return np.asarray(emb)


def _record_emojis(item: Dict[str, Any]) -> List[List[str]]:
    raw = item.get("emoji_sequences") or item.get("emojis") or item.get("emoji_sequence")
    if raw is None:
        return []
    if isinstance(raw, list):
        out: List[List[str]] = []
        for x in raw:
            if isinstance(x, list):
                out.append([str(e) for e in x])
            elif isinstance(x, str) and x.strip():
                out.append([p.strip() for p in x.split(",") if p.strip()])
            else:
                out.append([])
        return out
    if isinstance(raw, str) and raw.strip():
        return [[p.strip() for p in raw.split(",") if p.strip()]]
    return []


def load_emocc_features(dataset_key: str, user_hash: str) -> Optional[Dict[str, Any]]:
    spec = get_emocc_dataset_spec(dataset_key)
    pkl = spec.embedding_pickle_path()
    if pkl is None or not pkl.is_file():
        raise FileNotFoundError(f"Emocc 嵌入 pkl 不存在: {pkl}")

    with open(pkl, "rb") as f:
        data = pickle.load(f)

    if isinstance(data, dict):
        # 某些 pkl 为 {user_id: record}
        for uid, item in data.items():
            if not isinstance(item, dict):
                continue
            uh = generate_user_hash(dataset_key, str(uid))
            if uh == user_hash:
                emb = _record_embeddings(item)
                if emb is None:
                    continue
                return {
                    "bert_embeddings": emb,
                    "emoji_sequences": _record_emojis(item),
                    "person_id": str(uid),
                }
        records = list(data.values()) if data else []
    elif isinstance(data, list):
        records = data
    else:
        raise ValueError(f"不支持的 pkl 结构: {type(data)}")

    for idx, item in enumerate(records):
        if not isinstance(item, dict):
            continue
        uid = _record_user_id(item)
        if uid is None:
            uid = f"row_{idx + 1}"
        if generate_user_hash(dataset_key, uid) != user_hash:
            continue
        emb = _record_embeddings(item)
        if emb is None:
            continue
        return {
            "bert_embeddings": emb,
            "emoji_sequences": _record_emojis(item),
            "person_id": uid,
        }
    return None
