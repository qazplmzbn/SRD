import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from transformers import BertConfig, BertModel, BertTokenizerFast
from services.appointment_review import review, models


class AppointmentReviewTest(unittest.TestCase):
    def test_encoder_and_checkpoint_contract(self):
        # Random tiny encoder for shape/attention tests only, never for serving.
        with tempfile.TemporaryDirectory() as folder, patch.dict(os.environ, CARE_BERT_DIR=folder):
            vocab = Path(folder) / 'vocab.txt'
            vocab.write_text('[PAD]\n[UNK]\n[CLS]\n[SEP]\n[MASK]\n早\n晚\n安\n', encoding='utf8')
            BertTokenizerFast(vocab_file=str(vocab)).save_pretrained(folder)
            BertModel(BertConfig(vocab_size=8,hidden_size=768,num_hidden_layers=1,num_attention_heads=12,intermediate_size=64)).save_pretrained(folder)
            models.cache_clear()
            try:
                result = review(['早安','晚安'])
                self.assertEqual(len(result['weights']),2)
                self.assertAlmostEqual(sum(result['weights']),1,places=5)
                self.assertIn(result['classIndex'],(0,1))
            finally:
                models.cache_clear()