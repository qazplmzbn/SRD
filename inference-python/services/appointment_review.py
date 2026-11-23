"""Request-local text features; only model parameters are cached."""
import os
from functools import lru_cache
from pathlib import Path
import torch
import regex
import importlib.util
from types import SimpleNamespace

@lru_cache(maxsize=1)
def bert_models():
    from transformers import BertModel, BertTokenizerFast
    path = os.environ.get('CARE_BERT_DIR')
    if not path or not Path(path).is_dir():
        raise RuntimeError('CARE_BERT_DIR not configured')
    tokenizer = BertTokenizerFast.from_pretrained(path, local_files_only=True)
    encoder = BertModel.from_pretrained(path, local_files_only=True).eval()
    if encoder.config.hidden_size != 768:
        raise ValueError('Expected 768-dimensional BERT')
    return tokenizer, encoder

@lru_cache(maxsize=1)
def models():
    ckpt = Path(__file__).resolve().parents[2] / 'Emocc/weibo/Emocc_model/checkpoints/emocc_model.pth'
    state = torch.load(ckpt, weights_only=True, map_location='cpu')['model_state_dict']
    spec = importlib.util.spec_from_file_location('care_weibo_model',ckpt.parents[1]/'model.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    model = module.BertEmojiModel(SimpleNamespace(dropout=0.5,gru_size=128,class_num=2),state['emoji_embed.weight'].shape[0],'cpu')
    model.load_state_dict(state,strict=True)
    return model.eval()

@torch.inference_mode()
def encode_posts(texts):
    tokenizer,encoder = bert_models()
    vectors=[]
    for i in range(0,len(texts),8):
        inputs=tokenizer(texts[i:i+8], return_tensors='pt',padding=True,truncation=True,max_length=128)
        vectors.append(encoder(**inputs).last_hidden_state[:,0])
    return torch.cat(vectors).unsqueeze(0)

@lru_cache(maxsize=1)
def emoji_vocab():
    from .emocc_config import EMOCC_DATASET_SPECS
    spec = EMOCC_DATASET_SPECS['weibo']
    source = spec.emocc_root() / 'Emocc_model/utils/emoji_utils.py'
    loader = importlib.util.spec_from_file_location('care_weibo_vocab', source)
    module = importlib.util.module_from_spec(loader)
    loader.loader.exec_module(module)
    return module.get_emoji_vocabulary(str(spec.emoji_csv_path()))[1]


_symbol = r'\p{Extended_Pictographic}\uFE0F?\p{Emoji_Modifier}?'
_emoji = regex.compile(rf'(?:{_symbol})(?:\u200D{_symbol})*|\p{{Regional_Indicator}}{{2}}|[0-9#*]\uFE0F?\u20E3')


def emoji_features(texts, sequences, vocab):
    if len(texts) != len(sequences):
        raise ValueError('Expected one emoji sequence per post')
    ids = torch.zeros((1,len(texts),10),dtype=torch.long)
    mask = torch.zeros_like(ids)
    for i, seq in enumerate(sequences):
        clusters = regex.findall(r'\X', seq)
        if not 3 <= len(clusters) <= 5 or not all(_emoji.fullmatch(c) for c in clusters):
            raise ValueError('Expected 3-5 emojis only')
        # Training vocabulary tokenizes by Unicode code point, not grapheme.
        tokens = [vocab.get(c,vocab['<UNK>']) for c in seq[:10]]
        ids[0,i,:len(tokens)] = torch.tensor(tokens)
        mask[0,i,:len(tokens)] = 1
    return ids, mask


@torch.inference_mode()
def review(texts, sequences):
    model = models()
    x = encode_posts(texts)
    vocab = emoji_vocab()
    if len(vocab) != model.emoji_embed.num_embeddings:
        raise ValueError('Emoji vocabulary does not match checkpoint')
    ids, mask = emoji_features(texts,sequences,vocab)
    logits,weights=model(x,ids,torch.ones_like(ids[:,:,0]),mask,return_attention=True)
    if not torch.isfinite(logits).all() or not torch.isfinite(weights).all(): raise ValueError('invalid output')
    from .emocc_config import EMOCC_DATASET_SPECS
    spec = EMOCC_DATASET_SPECS['weibo']
    index = int(logits.argmax(-1).item())
    return {'model':spec.model_name,'encoder':Path(os.environ['CARE_BERT_DIR']).name,
            'emojiSequences':sequences,'inferenceBackend':'torch_checkpoint',
            'classIndex':index,'riskLevel':spec.coarse_risk_mapping[index],
            'riskScore':spec.risk_score_mapping[index],'weights':weights[0].tolist()}