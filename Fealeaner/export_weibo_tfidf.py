"""Recover the frozen TF-IDF transform and verify it against training features."""
import argparse
import importlib.util
import json
import os
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer


def verified_transform(documents, expected):
    vectorizer = TfidfVectorizer(max_features=expected.shape[1])
    values = vectorizer.fit_transform(documents).toarray()
    if values.shape != expected.shape or not np.allclose(values, expected, rtol=1e-6, atol=1e-8):
        raise ValueError('TF-IDF differs from the training table; check corpus order and preprocessor')
    return {'terms': vectorizer.get_feature_names_out().tolist(), 'idf': vectorizer.idf_.tolist()}


def main():
    root = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--extractor', required=True, type=Path, help='Trusted original Weibo training script')
    parser.add_argument('--corpus', type=Path, default=root.parent / 'datasets/weibo/weibo_data.csv')
    parser.add_argument('--features', type=Path, default=root / 'feature_data/feature_weibo_2.csv')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    script = args.extractor.resolve(strict=True)
    corpus = args.corpus.resolve(strict=True)
    table = pd.read_csv(args.features, usecols=[f'TFIDF_{i}' for i in range(50)])
    expected = table[[f'TFIDF_{i}' for i in range(50)]].to_numpy()
    output = args.output.resolve()
    # The original script resolves its stopword dictionary relative to its working directory.
    os.chdir(script.parent)
    spec = importlib.util.spec_from_file_location('training_preprocessor', script)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    if not module.STOP_WORDS:
        raise ValueError('Training stopword dictionary is missing')

    def documents():
        for posts in module.load_data_from_csv(str(corpus))['text']:
            yield ' '.join(w for post in posts for w in module.jieba.cut(module.preprocess_text_chinese(post))
                           if w.strip() and w not in module.STOP_WORDS)

    result = verified_transform(documents(), expected)
    result['extractor'] = script.name
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()