import os
from functools import lru_cache
from pathlib import Path

import torch
import torch.nn.functional as F


@lru_cache(maxsize=1)
def encoder():
    from transformers import BertModel, BertTokenizerFast
    directory = os.getenv('RESEARCH_BERT_DIR') or os.getenv('CARE_BERT_DIR')
    if not directory or not Path(directory).is_dir():
        raise RuntimeError('Missing local BERT directory')
    return (BertTokenizerFast.from_pretrained(directory, local_files_only=True),
            BertModel.from_pretrained(directory, local_files_only=True).eval(), Path(directory).name)


@torch.inference_mode()
def predict(train, labels, test):
    tokenizer, model, name = encoder()
    def encode(texts):
        batches = []
        for start in range(0, len(texts), 8):
            inputs = tokenizer(texts[start:start+8], padding=True, truncation=True, max_length=128, return_tensors='pt')
            mask = inputs.attention_mask.unsqueeze(-1)
            hidden = model(**inputs).last_hidden_state
            batches.append(F.normalize((hidden * mask).sum(1) / mask.sum(1), dim=1))
        return torch.cat(batches)
    features = encode(train)
    y = torch.tensor(labels)
    centers = F.normalize(torch.stack([features[y == i].mean(0) for i in (0, 1)]), dim=1)
    similarities = encode(test) @ centers.T
    if not torch.isfinite(similarities).all():
        raise ValueError('Invalid BERT features')
    return {'encoder': name, 'predictions': similarities.argmax(1).tolist()}