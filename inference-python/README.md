# VIS4SRD 推理侧车（Emocc / FeaLearner）

仅负责神经网络推理，主业务仍由 `backend`（端口 8000）提供。

## 启动

在仓库根目录准备 Python 3.10+ 环境后：

```powershell
cd inference-python
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -U pip
.\.venv\Scripts\python.exe -m pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
.\.venv\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 8001
```

健康检查：`http://127.0.0.1:8001/health`

## 接口

- `POST /infer/fealearner` `{ "userHash", "dataSource" }`
- `POST /infer/emocc` `{ "userHash", "dataSource", "posts?": [] }`

模型权重读取仓库根下的 `Emocc/`、`Fealeaner/`。
