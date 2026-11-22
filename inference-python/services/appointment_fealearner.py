"""Weibo FeaLearner inference for request-local post histories."""
import json
import os
import re
import string
from collections import defaultdict
from functools import lru_cache
from pathlib import Path

import jieba
import jieba.posseg
import numpy as np
import pandas as pd
import torch
from sklearn.feature_extraction.text import CountVectorizer

from .appointment_review import encode_posts
from .fealearner_service import get_fealearner_service

ROOT = Path(__file__).resolve().parents[2] / 'Fealeaner'
POS = 'a ad ag an b c d df dg e eng f g h i j k l m mg mq n ng nr nrfg nrt ns nt nz o p q r rg rr rz s t tg u ud ug uj ul uv uz v vd vg vi vn vq x y yg z zg'.split()
NRC = 'Anger Anticipation Disgust Fear Joy Negative Positive Sadness Surprise Trust'.split()
SUI = 'A B D C1 C2 C3 C4 C5 C6 C7 C8 C9 C10'.split()

@lru_cache(maxsize=1)
def resources():
    folder = Path(os.environ.get('FEALEARNER_DICT_DIR', ROOT / 'tools_dataset'))
    stop = set((folder / 'hit_stopwords.txt').read_text(encoding='utf8').splitlines())
    table = pd.read_csv(folder / 'NRC-Emotion-Lexicon-v0.92.csv', usecols=['Chinese (Simplified) (zh-CN)', *NRC])
    emotions = defaultdict(set)
    for word, *values in table[['Chinese (Simplified) (zh-CN)', *NRC]].itertuples(index=False, name=None):
        emotions[str(word).strip()].update(i for i, value in enumerate(values) if value == 1)
    terms = defaultdict(list)
    table = pd.read_csv(folder / 'Chinese suicide dictionary.csv')
    for word, category, weight in table[['WORD','CATEGORY','WEIGHT']].itertuples(index=False, name=None):
        key = str(category).strip().upper()
        if key in SUI:
            terms[str(word).strip()].append((SUI.index(key), float(weight)))
    frozen = json.loads((ROOT / 'weibo_tfidf.json').read_text(encoding='utf8'))
    return stop, emotions, terms, CountVectorizer(vocabulary=frozen['terms']), np.asarray(frozen['idf'])

def clean(post, stop):
    post = re.sub(r'@[\u4e00-\u9fa5\w]+', '', post)
    post = re.sub(r'http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*\\(\\),]|(?:%[0-9a-fA-F][0-9a-fA-F]))+', '', post)
    post = re.sub(r'<.*?>', '', post)
    post = re.sub('[' + re.escape(string.punctuation + '，。！？；："《》【】()（）[]') + ']+', ' ', post)
    return ' '.join(t for t in re.sub(r'\d+', '', post).split() if t not in stop)

def feature_row(posts):
    stop, emotions, terms, vectorizer, idf = resources()
    pos, nrc, sui = (np.zeros(n) for n in (57, 10, 13))
    tokens = []
    cleaned = [s for post in posts if (s := clean(post, stop))]
    if not cleaned:
        raise ValueError('No usable Weibo text')
    for post in cleaned:
        for word, flag in jieba.posseg.cut(post):
            flag = flag.lower()
            for tag in (flag, flag[:2], flag[:1]):
                if word.strip() and tag in POS:
                    pos[POS.index(tag)] += 1
                    break
        for word in jieba.cut(post):
            if word.strip() and word not in stop:
                tokens.append(word)
            for i in emotions.get(word, ()):
                nrc[i] += 1
            for i, weight in terms.get(word, ()):
                sui[i] += weight
    tf = vectorizer.transform([' '.join(tokens)]).toarray()[0] * idf
    row = np.concatenate((pos / len(cleaned), tf / (np.linalg.norm(tf) or 1),
                          nrc / (nrc.sum() or 1), sui / (sui.sum() or 1)))
    return torch.tensor(row, dtype=torch.float32).unsqueeze(0)

@lru_cache(maxsize=1)
def classifier():
    predictor = get_fealearner_service()._load_predict_module()
    state = torch.load(ROOT / 'bestmodel/my_weibo_model.pth', map_location='cpu', weights_only=True)
    model, _, _ = predictor._build_model_from_state('weibo', predictor._load_train_module('weibo'),
                                                    predictor._model_config('weibo'), 130, state)
    model.load_state_dict(state, strict=True)
    return model.eval()

@torch.inference_mode()
def review(posts):
    vectors = encode_posts(posts)
    logits = classifier()(vectors, torch.tensor([len(posts)]), torch.zeros(1, dtype=torch.long), feature_row(posts))
    if logits.shape != (1, 2) or not torch.isfinite(logits).all():
        raise ValueError('Invalid FeaLearner output')
    index = int(logits.argmax(dim=1).item())
    level, score = get_fealearner_service()._map_label_to_risk('weibo', index)
    return {'model':'FeaLearner-Weibo', 'encoder':Path(os.environ['CARE_BERT_DIR']).name,
            'classIndex':index, 'riskLevel':level, 'riskScore':score}