# VIS4SRD

VIS4SRD 是一个面向心理健康服务与自杀风险识别场景的前后端分离系统，围绕「心理档案 → 风险检测 → 量表评估 → 知识支持 → 对话辅助 → 地图资源」组织功能。

- **业务后端**：Spring Boot（`backend/`，端口 `8000`）
- **前端**：React + Vite（`frontend/`，端口 `5173`）
- **可选推理侧车**：`inference-python/`（端口 `8001`），用于 Emocc / FeaLearner 真实神经网络推理；未启动时风险检测回退 Java 启发式


---

## 1. 仓库

### 1.1 GitHub 仓库通常包含

| 目录 / 文件 | 作用 |
|-------------|------|
| `backend/` | Spring Boot 业务后端源码 |
| `frontend/` | React 前端源码 |
| `inference-python/` | 推理侧车源码（不含本地 `.venv`） |
| `knowledge/` | RAG 知识语料与 `knowledge_catalog.json` |
| `scales/` | 心理量表 JSON 定义 |
| `README.md`、`.gitignore` 等 | 说明与忽略规则 |

克隆后即可跑通：**登录、档案、量表、知识库、聊天（需 LLM Key）、风险检测（启发式）** 等主流程（需 MySQL；地图另需 PostgreSQL）。

### 1.2 未上传、但对完整运行很重要的目录

以下内容因体积大或属于本地/个人资料，**默认不进 Git**。克隆后若缺这些目录，对应功能会不可用或降级。

| 目录 | 是否运行「必须」 | 影响功能 | 如何补齐 |
|------|------------------|----------|----------|
| `Emocc/` | **真实 Emocc 推理必须** | 侧车 Emocc 神经网络检测；无权重则只能启发式 | 按现有目录结构单独拷贝整目录（含 `.pth` / `.pkl` 等） |
| `Fealeaner/` | **真实 FeaLearner 推理必须** | 侧车 FeaLearner 检测；无权重则只能启发式 | 单独同步模型与特征资源（权重文件已按扩展名忽略） |
| `datasets/` | **内置数据集 / 档案模板必须** | `POST /api/datasets/builtin/sync`、档案 CSV 模板等 | 单独同步到项目根 `datasets/`（配置默认 `../datasets`） |
| `map_data/` | 可选（地图主要靠 PG） | 地图相关本地资源 / 离线数据 | 需要时单独同步；日常地图接口依赖 PostgreSQL |
| `uploads/` | **运行时写入必须存在** | 档案上传、知识文档上传落盘 | 本地建空目录即可：`uploads/archives/`、`uploads/knowledge/`（可用 `.gitkeep`） |


**最小可演示**：`backend` + `frontend` + `knowledge` + `scales` + MySQL（+ 可选 LLM）。  
**真实风险模型**：再补 `Emocc/`、`Fealeaner/`，并启动 `inference-python`。  
**内置数据集同步**：再补 `datasets/`。  
**地图**：MySQL 之外还需 PostgreSQL（常用 SSH 隧道到本机 `5432`）。

---

## 2. 项目结构

```text
vis4srd/
├─ backend/                 # Spring Boot 后端（:8000）
├─ frontend/                # React + Vite 前端（:5173）
├─ inference-python/        # Emocc / FeaLearner 推理侧车（:8001，可选）
├─ knowledge/               # RAG 知识语料
├─ scales/                  # 量表 JSON
├─ uploads/                 # 上传落盘（仓库常为空，需本地建目录）
├─ datasets/                # 内置数据集 CSV（默认不上传，需自备）
├─ Emocc/                   # Emocc 权重与资源（默认不上传）
├─ Fealeaner/               # FeaLearner 模型与脚本（权重默认不上传）
├─ map_data/                # 地图本地资源（默认不上传）
└─ deploy/                  # 部署相关脚本（若有）
```

---

## 3. 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17+、Spring Boot 3、Maven、MySQL、PostgreSQL（地图） |
| 前端 | React 18、TypeScript、Vite 5、Ant Design 5、Zustand、ECharts |
| 推理（可选） | Python 3、FastAPI / Uvicorn、PyTorch 等（见 `inference-python/requirements.txt`） |
| LLM（可选） | 兼容 OpenAI 协议的 API（开发推荐 `qwen-flash`） |

---

## 4. 环境要求与端口

- JDK 17+、Maven、Node.js（建议 18+）
- MySQL：`127.0.0.1:3306`，库名示例 `vis4srd`
- PostgreSQL：地图功能；本地常用 SSH 隧道映射到 `127.0.0.1:5432`
- **端口固定**：后端 `8000`、前端 `5173`、侧车 `8001`；被占用时先释放，不要改端口绕过

---

## 5. 快速启动

### 5.1 配置后端

在 `backend/` 使用 `local` profile（见 `application.yml` / local 示例）。常用环境变量：

```text
PORT=8000
DB_HOST / DB_PORT / DB_USER / DB_PASSWORD / DB_NAME
PG_HOST / PG_PORT / PG_USER / PG_PASSWORD / PG_NAME
LLM_MODEL / LLM_API_KEY / LLM_BASE_URL
KNOWLEDGE_ROOT_DIR=../knowledge
KNOWLEDGE_UPLOAD_DIR=../uploads/knowledge
ARCHIVE_UPLOAD_DIR=../uploads/archives
DATASETS_DIR=../datasets
PYTHON_INFERENCE_BASE_URL=http://127.0.0.1:8001
```



### 5.2 启动业务后端

```powershell
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

### 5.3启动推理

需已准备好 `Emocc/`、`Fealeaner/`：

```powershell
cd inference-python
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
.\.venv\Scripts\python.exe -m uvicorn app:app --host 0.0.0.0 --port 8001
```

健康检查：`http://127.0.0.1:8001/health`  
不可用时，风险检测自动启发式回退。

### 5.4 启动前端

```powershell
npm install --prefix frontend --registry=https://registry.npmmirror.com
npm run dev --prefix frontend -- --host 0.0.0.0 --port 5173 --strictPort
```

### 5.5PostgreSQL SSH 隧道

```powershell
ssh -o ExitOnForwardFailure=yes -o ServerAliveInterval=60 -N -L 5432:127.0.0.1:5432 vis4srd-server
```

### 5.6 内置数据集同步（需已有 `datasets/`）

管理员调用：

```text
POST /api/datasets/builtin/sync
```

---

## 6. 启动后验证

```powershell
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:8000/api/health
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173/
Invoke-WebRequest -UseBasicParsing http://127.0.0.1:5173/api/health
```

前端开发代理：`/api` → `http://localhost:8000`（见 `frontend/vite.config.ts`）。

---

## 7. 前端主要页面

| 路径 | 说明 |
|------|------|
| `/home` | 首页 |
| `/archive` | 心理档案 |
| `/risk` | 风险检测 |
| `/scale` | 量表 |
| `/knowledge` | 知识库 |
| `/chat` | AI 对话 |
| `/map` | 援助地图 |
| `/model` | 模型中心 |
| `/profile` | 账号 |

未登录默认跳转 `/login`。

---

## 8. 关键代码入口

- 后端入口：`backend/src/main/java/com/vis4srd/Vis4srdApplication.java`
- 配置：`backend/src/main/resources/application.yml`
- 风险：`backend/.../risk/service/RiskService.java`
- 聊天 RAG：`backend/.../chat/rag/SuiAgentRagService.java`（读项目根 `knowledge/`）
- 前端入口：`frontend/src/main.tsx`、`frontend/src/App.tsx`
- 推理侧车：`inference-python/app.py`

更多接口说明见 `backend/README.md`、`inference-python/README.md`。

---

## 9. 常见问题

| 现象 | 处理 |
|------|------|
| `8000` / `5173` 占用 | 释放进程，不要改端口 |
| 地图接口失败 | 检查 PostgreSQL / SSH 隧道是否仍为 `127.0.0.1:5432` |
| 风险结果像「规则」而非模型 | 未起侧车或缺 `Emocc/` / `Fealeaner/` 权重，属预期回退 |
| 内置数据集同步失败 | 确认项目根存在 `datasets/` 且路径配置正确 |
| 上传失败 | 确认已创建 `uploads/archives`、`uploads/knowledge` |

---

## 10. 适用范围

适合课Demo、系统联调与可视化展示。涉及心理健康与危机干预敏感内容，**请勿将模型或系统输出直接当作临床诊断**。
