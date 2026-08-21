"""项目根与用户哈希（与 Java BuiltinCsvDatasetService 一致）。"""
from __future__ import annotations

import hashlib
from pathlib import Path


def project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def generate_user_hash(dataset_key: str, user_id: str) -> str:
    raw = f"{dataset_key}_{user_id}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()[:12]
