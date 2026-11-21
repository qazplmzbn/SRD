"""Request-local text features; only model parameters are cached."""
import os
from functools import lru_cache
from pathlib import Path
import torch
import importlib.util
from types import SimpleNamespace

@lru_cache(maxsize=1)
def models():
    from transformers import BertModel, BertTokenizerFast
    path = os.environ.get('CARE_BERT_DIR')
    if not path or not Path(path).is_dir():
        raise RuntimeError('CARE_BERT_DIR not configured')
    tokenizer = BertTokenizerFast.from_pretrained(path, local_files_only=True)
    encoder = BertModel.from_pretrained(path, local_files_only=True).eval()
    if encoder.config.hidden_size != 768:
        raise ValueError('Expected 768-dimensional BERT')
    ckpt = Path(__file__).resolve().parents[2] / 'Emocc/weibo/Emocc_model/checkpoints/emocc_model.pth'
    state = torch.load(ckpt, weights_only=True, map_location='cpu')['model_state_dict']
    spec = importlib.util.spec_from_file_location('care_weibo_model',ckpt.parents[1]/'model.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    model = module.BertEmojiModel(SimpleNamespace(dropout=0.5,gru_size=128,class_num=2),state['emoji_embed.weight'].shape[0],'cpu')
    model.load_state_dict(state,strict=True)
    return tokenizer,encoder,model.eval()

@torch.inference_mode()
def review(texts):
    tokenizer,encoder,model = models()
    vectors=[]
    for i in range(0,len(texts),8):
        inputs=tokenizer(texts[i:i+8], return_tensors='pt',padding=True,truncation=True,max_length=128)
        vectors.append(encoder(**inputs).last_hidden_state[:,0])
    x=torch.cat(vectors).unsqueeze(0)
    missing=torch.zeros((1,len(texts),1),dtype=torch.long)
    logits,weights=model(x,missing,torch.ones_like(missing[:,:,0]),missing,return_attention=True)
    if not torch.isfinite(logits).all() or not torch.isfinite(weights).all(): raise ValueError('invalid output')
    return {'model':'EmoCC · Weibo','encoder':Path(os.environ['CARE_BERT_DIR']).name,'classIndex':int(logits.argmax(-1).item()),'weights':weights[0].tolist()}