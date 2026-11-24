import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from services.emocc_config import EmoccDatasetSpec, get_emocc_dataset_spec


class EmoccEmbeddingPathTest(unittest.TestCase):
    def test_packaged_alias_is_used_only_when_primary_is_missing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(EmoccDatasetSpec, 'emocc_root', return_value=root):
                for dataset in ('bigdata', 'sigir'):
                    with self.subTest(dataset=dataset):
                        spec = get_emocc_dataset_spec(dataset)
                        primary = root / f'data/{dataset}_bert_embeddings.pkl'
                        alias = root / f'data/{dataset}_bert.pkl'
                        self.assertEqual(spec.embedding_pickle_path(), primary)
                        alias.parent.mkdir(exist_ok=True)
                        alias.touch()
                        self.assertEqual(spec.embedding_pickle_path(), alias)
                        primary.touch()
                        self.assertEqual(spec.embedding_pickle_path(), primary)