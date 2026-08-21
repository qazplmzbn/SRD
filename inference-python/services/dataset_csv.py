"""内置 CSV 配置：user_hash ↔ 原始 user_id、贴文列表。"""
from __future__ import annotations

import csv
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from .hash_util import generate_user_hash, project_root

DATASET_CONFIG: Dict[str, Dict[str, Any]] = {
    "reddit": {
        "csv_path": "datasets/reddit/reddit_500.csv",
        "user_id_column": "User",
        "post_column": "Post",
    },
    "bigdata": {
        "csv_path": "datasets/bigdata/bigdata.csv",
        "user_id_column": "user_id",
        "post_column": "post_sequence",
    },
    "sigir": {
        "csv_path": "datasets/sigir/sigir.csv",
        "user_id_column": None,  # 按行号 row_N
        "post_column": "Post",
    },
    "weibo": {
        "csv_path": "datasets/weibo/weibo_1000.csv",
        "user_id_column": "user_id",
        "post_column": "Post",
    },
}


def _parse_posts(post_str: Optional[str]) -> List[str]:
    if not post_str:
        return []
    text = str(post_str).strip()
    if text.startswith("[") and text.endswith("]"):
        try:
            import ast

            items = ast.literal_eval(text.replace('\\"', '"'))
            if isinstance(items, list):
                return [str(i).strip() for i in items if str(i).strip()]
        except Exception:
            pass
        import re

        items = re.findall(r"[\"']([^\"']+)[\"']", text)
        if items:
            return [i.strip() for i in items if i.strip()]
    if "\n" in text:
        parts = [p.strip() for p in text.splitlines() if p.strip()]
        if len(parts) > 1:
            return parts
    return [text] if text else []


def resolve_user_id(dataset_key: str, user_hash: str) -> Optional[str]:
    cfg = DATASET_CONFIG.get(dataset_key)
    if not cfg:
        return None
    csv_path = project_root() / cfg["csv_path"]
    if not csv_path.is_file():
        return None
    user_col = cfg.get("user_id_column")
    with open(csv_path, "r", encoding="utf-8-sig", errors="replace") as f:
        reader = csv.DictReader(f)
        for idx, row in enumerate(reader, start=1):
            if user_col:
                uid = str(row.get(user_col) or "").strip()
            else:
                uid = f"row_{idx}"
            if not uid:
                continue
            if generate_user_hash(dataset_key, uid) == user_hash:
                return uid
    return None


def load_user_posts(dataset_key: str, user_hash: str) -> Tuple[Optional[str], List[str]]:
    cfg = DATASET_CONFIG.get(dataset_key)
    if not cfg:
        return None, []
    csv_path = project_root() / cfg["csv_path"]
    if not csv_path.is_file():
        return None, []
    user_col = cfg.get("user_id_column")
    post_col = cfg["post_column"]
    with open(csv_path, "r", encoding="utf-8-sig", errors="replace") as f:
        reader = csv.DictReader(f)
        for idx, row in enumerate(reader, start=1):
            if user_col:
                uid = str(row.get(user_col) or "").strip()
            else:
                uid = f"row_{idx}"
            if not uid:
                continue
            if generate_user_hash(dataset_key, uid) != user_hash:
                continue
            return uid, _parse_posts(row.get(post_col))
    return None, []
