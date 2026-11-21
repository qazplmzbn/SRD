import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from transformers import BertConfig, BertModel, BertTokenizerFast
from services.research_probe import encoder, predict


class ProbeTest(unittest.TestCase):
    def test_local_encoding_and_label_permutation(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, RESEARCH_BERT_DIR=directory):
            vocab=Path(directory)/'vocab.txt'
            vocab.write_text('[PAD]\n[UNK]\n[CLS]\n[SEP]\n[MASK]\nred\nblue\n',encoding='utf8')
            BertTokenizerFast(vocab_file=str(vocab)).save_pretrained(directory)
            BertModel(BertConfig(vocab_size=7,hidden_size=24,num_attention_heads=2,num_hidden_layers=1,intermediate_size=32)).save_pretrained(directory)
            encoder.cache_clear()
            try:
                texts=['red','blue']
                # Exact matches exercise the classifier contract, not held-out accuracy.
                self.assertEqual(predict(texts,[0,1],texts[::-1])['predictions'],[1,0])
                self.assertEqual(predict(texts,[1,0],texts[::-1])['predictions'],[0,1])
            finally:encoder.cache_clear()