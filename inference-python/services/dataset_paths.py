"""内置数据集 CSV 路径与列名配置（对齐 Java BuiltinCsvDatasetService / Python DatasetCSVService）。"""
from __future__ import annotations

from pathlib import Path
from typing import Any, Dict, List, Optional


def project_root() -> Path:
    """仓库根目录（inference-python 的上一级）。"""
    return Path(__file__).resolve().parents[2]


def datasets_dir() -> Path:
    return project_root() / "datasets"


# csv_path 相对 datasets/
DATASET_CONFIG: Dict[str, Dict[str, Any]] = {
    "reddit": {
        "csv_path": "reddit/reddit_500.csv",
        "emoji_csv_path": "reddit/reddit_500_emoji_batch.csv",
        "user_id_column": "User",
        "post_column": "Post",
        "risk_column": "Label",
        "emoji_column": "Post",
    },
    "bigdata": {
        "csv_path": "bigdata/bigdata.csv",
        "emoji_csv_path": "bigdata/bigdata_emoji_batch.csv",
        "user_id_column": "user_id",
        "post_column": "post_sequence",
        "risk_column": "suicide_risk",
        "emoji_column": "emjio_sequenc",
    },
    "sigir": {
        "csv_path": "sigir/sigir.csv",
        "emoji_csv_path": "sigir/sigir_emojis.csv",
        "user_id_column": None,
        "post_column": "Post",
        "risk_column": "Label",
        "emoji_column": "Post",
    },
    "weibo": {
        "csv_path": "weibo/weibo_1000.csv",
        "emoji_csv_path": "weibo/weibo_1000_emoji_batch.csv",
        "user_id_column": "user_id",
        "post_column": "Post",
        "risk_column": "label",
        "emoji_column": "emoji_sequence",
    },
}


def resolve_csv_path(dataset_key: str) -> Optional[Path]:
    """解析主 CSV 绝对路径；优先 datasets/，其次 Emocc/<ds>/data/。"""
    cfg = DATASET_CONFIG.get((dataset_key or "").strip().lower())
    if not cfg:
        return None
    rel = str(cfg["csv_path"]).replace("\\", "/").strip("/")
    filename = Path(rel).name
    ds = (dataset_key or "").strip().lower()
    root = project_root()
    candidates = [
        datasets_dir() / rel,
        root / "Emocc" / ds / "data" / filename,
        root / "Emocc" / "data" / filename,
    ]
    for p in candidates:
        if p.is_file():
            return p
    return datasets_dir() / rel


def resolve_emoji_csv_path(dataset_key: str) -> Optional[Path]:
    cfg = DATASET_CONFIG.get((dataset_key or "").strip().lower())
    if not cfg:
        return None
    rel = str(cfg.get("emoji_csv_path") or "").replace("\\", "/").strip("/")
    if not rel:
        return None
    filename = Path(rel).name
    ds = (dataset_key or "").strip().lower()
    root = project_root()
    candidates = [
        datasets_dir() / rel,
        root / "Emocc" / ds / "data" / filename,
        root / "Emocc" / "data" / filename,
    ]
    for p in candidates:
        if p.is_file():
            return p
    return None


def resolve_user_id_from_row(dataset_key: str, row: Dict[str, str], row_index: int) -> str:
    """与 Java resolveUserId 一致：有列则用列值，否则 row_{1-based index}。"""
    cfg = DATASET_CONFIG.get((dataset_key or "").strip().lower(), {})
    col = cfg.get("user_id_column")
    if col:
        value = (row.get(col) or "").strip()
        if value:
            return value
    return f"row_{row_index}"


def parse_posts(post_str: Optional[str]) -> List[str]:
    if not post_str:
        return []
    text = str(post_str).strip()
    if text.startswith("[") and text.endswith("]"):
        import ast

        try:
            parsed = ast.literal_eval(text.replace('\\"', '"'))
            if isinstance(parsed, list):
                return [str(x).strip() for x in parsed if str(x).strip()]
        except Exception:
            pass
        import re

        items = re.findall(r"[\"']([^\"']+)[\"']", text)
        if items:
            return [x.strip() for x in items if x.strip()]
    if "\n" in text:
        parts = [p.strip() for p in text.splitlines() if p.strip()]
        if len(parts) > 1:
            return parts
    return [text]
