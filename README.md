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

**第一次从 GitHub 下载？** 从[快速启动](#5-快速启动)开始，按用途选择：

- **只看页面**：安装 Node.js，按 5.1 下载代码，只执行 5.3 的「终端二」，打开 <http://127.0.0.1:5173/mobile/index.html>。不需要 Java 或数据库；此时不能验证登录、预约等接口。
- **注册、登录并使用业务功能**：准备 JDK、Maven、Node.js 和已初始化的 MySQL 业务库，依次执行 5.1 至 5.3，再按第 6 节验证。不需要先安装 Python、下载模型或配置地图。

### 1.2 未上传、但对完整运行很重要的目录

对照[忽略规则](.gitignore)，以下资源不会全部随源码下载。缺少可选资源时，只需为要使用的功能补齐对应项。

| 目录 | 是否运行「必须」 | 影响功能 | 如何补齐 |
|------|------------------|----------|----------|
| `Emocc/` | **真实 Emocc 推理必须** | 侧车 Emocc 神经网络检测；无权重则只能启发式 | 按现有目录结构单独拷贝整目录（含 `.pth` / `.pkl` 等） |
| `Fealeaner/` | **真实 FeaLearner 推理必须** | 有算法脚本不代表能加载模型 | 目录中的脚本已入库；另向维护者获取匹配的权重和特征资源（如 `*.pth`、`*.pkl`），按原相对路径放回 |
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

- JDK 17+、Maven、Node.js（建议 22 LTS，含 npm）、MySQL；使用 `git clone` 时还需 Git。
- MySQL：`127.0.0.1:3306`，库名示例 `vis4srd`
- PostgreSQL：地图功能；本地常用 SSH 隧道映射到 `127.0.0.1:5432`
- **端口固定**：后端 `8000`、前端 `5173`、侧车 `8001`；被占用时先释放，不要改端口绕过

---

## 5. 快速启动

### 5.1 下载代码（Windows PowerShell）

```powershell
git clone https://github.com/qazplmzbn/SRD.git
cd SRD
```

也可以在 [GitHub 仓库](https://github.com/qazplmzbn/SRD) 选择 **Code → Download ZIP** 并解压。下文的「项目根目录」指同时包含 `backend`、`frontend` 和 `scales` 的目录。

**下载后没有 `node_modules`、`target`、`dist` 或 `.venv` 是正常的。** 这些目录被 Git 忽略，不要从别人的电脑复制：5.3 的 `npm.cmd ci` 安装前端依赖，Maven 启动时编译后端，Vite 开发模式不需要 `dist`；只有运行 5.4 的推理服务才需创建 `.venv`。首次安装依赖需要能访问相应的软件源。

### 5.2 准备 MySQL 和本机配置

**已有可用业务库**：直接连接它，不必重新建库。**全新电脑**：先创建 `vis4srd` 库，再从项目维护者处取得与代码版本匹配的表结构和必要的非敏感初始化数据并恢复。仓库的 [`backend/src/main/resources/db/`](backend/src/main/resources/db/) 包含部分模块的建表脚本，不是完整业务库；只有空库时，即使健康检查通过，注册也可能报表不存在。请勿将真实患者记录、账号数据或密钥上传到 GitHub。

从项目根目录执行：

```powershell
New-Item -ItemType Directory -Force -Path 'uploads/archives','uploads/knowledge' | Out-Null
cd backend
$config = '.\src\main\resources\application-local.yml'
if (-not (Test-Path $config)) { Copy-Item "$config.example" $config }
notepad $config
```

按 [本机配置示例](backend/src/main/resources/application-local.yml.example) 填写以下字段：

| 字段 | 如何填写 |
|---|---|
| `app.datasource.mysql.jdbc-url` | 按实际主机、端口和库名修改。若是本机 `3306/vis4srd`，示例 URL 可保持不变。 |
| `app.datasource.mysql.username`、`password` | 填写能访问该业务库的 MySQL 账号及密码。当前主配置没有把这两个字段绑定到 `DB_USER`、`DB_PASSWORD`，仅设置这两个环境变量不会生效。 |
| `app.jwt.secret` | 替换为自己生成的随机密钥，建议至少 32 字节。请保存在本机；更换后需重新登录。 |
| `app.datasource.postgres` | 使用地图服务时配置 PostgreSQL 地址、账号和密码，并准备对应地图数据。 |

### 5.3 启动后端和前端

**终端一**：保持在上一步的 `backend` 目录，启用 `local` 配置启动后端，并保持终端运行。

```powershell
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

**终端二**：在项目根目录打开新的 PowerShell，按锁文件安装依赖并启动前端。

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev -- --host 127.0.0.1 --port 5173 --strictPort
```

打开 [Web 注册](http://127.0.0.1:5173/register) 创建专业账号，再到 [Web 登录](http://127.0.0.1:5173/login) 登录。医生可在登录后进入「专业服务 → 医生认证」，使用管理员单独提供的邀请码；本机测试需在启动后端前设置 `DOCTOR_CERTIFICATION_INVITE_CODE`，不要将其提交到仓库。个人用户从[移动端浏览器预览](http://127.0.0.1:5173/mobile/index.html)进入，预约等联网功能仍需要业务后端。

### 5.4 可选：启动本地模型推理

在第三个终端，从项目根目录执行。按所用模型准备权重与特征资源（见 1.2 节）。研究者导入数据的 BERT 检测需要将 `RESEARCH_BERT_DIR` 设为本机预训练编码器目录；医生复核使用 `CARE_BERT_DIR` 和匹配的微博 EmoCC 权重。将变量设置在**启动 Python 的终端**，路径指向包含配置、词表和权重的实际目录。

```powershell
cd inference-python
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt -i https://pypi.tuna.tsinghua.edu.cn/simple
.\.venv\Scripts\python.exe -m uvicorn app:app --host 127.0.0.1 --port 8001
```

健康检查：`http://127.0.0.1:8001/health`  
侧车不可用时，部分旧风险检测流程可按配置回退到启发式规则；研究 BERT 检测和医生模型复核需要真实编码器，不会生成模拟预测。使用外部大模型问答时另配 `LLM_API_KEY`、`LLM_MODEL` 和 `LLM_BASE_URL`。

### 5.5 可选：PostgreSQL SSH 隧道（仅已有对应 SSH 配置时）

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

前端开发代理：`/api` → `http://localhost:8000`（见 `frontend/vite.config.ts`）。健康检查不能代替业务验证：用测试账号成功注册、登录并进入首页后，再验证地图、模型等可选功能。

---

## 7. 前端主要页面

| 路径 | 说明 |
|------|------|
| `/home` | 首页 |
| `/research-data` | 研究数据、评估集和实验 |
| `/model-analysis` | 风险检测 |
| `/appointments`、`/care-users` | 医生预约管理与已获授权的用户资料 |
| `/knowledge` | 知识库 |
| `/chat` | AI 对话 |
| `/map` | 援助地图 |
| `/model`、`/model/template` | 模型与提示词配置，按账号角色展示 |
| `/mobile/index.html` | 移动端浏览器预览，不是 Android 安装包 |
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
| `npm.cmd ci` 提示找不到锁文件，或 Maven 提示没有 POM | 检查当前目录：npm 在含 `package-lock.json` 的 `frontend/` 下执行，Maven 在含 `pom.xml` 的 `backend/` 下执行。ZIP 解压后可能还有一层目录。 |
| 能打开页面，但 `/api` 请求失败 | 先直接访问 `http://127.0.0.1:8000/api/health`。若不通，检查后端终端的启动日志；若正常，再检查第 6 节的前端代理地址。 |
| `8000` / `5173` 占用 | 释放进程，不要改端口 |
| MySQL 报 `Access denied` | 检查本地配置的数据库账号、密码和权限；确认启用了 `local` profile |
| 注册时报 `Table ... doesn't exist` | 检查 JDBC URL 指向的库及业务表结构；不能只建空库或仅执行模块增量脚本 |
| 地图接口失败 | 检查 PostgreSQL / SSH 隧道是否仍为 `127.0.0.1:5432` |
| 风险结果像「规则」而非模型 | 未起侧车或缺 `Emocc/` / `Fealeaner/` 权重，属预期回退 |
| 内置数据集同步失败 | 确认项目根存在 `datasets/` 且路径配置正确 |
| 上传失败 | 确认已创建 `uploads/archives`、`uploads/knowledge` |

---

## 10. 适用范围

适合课Demo、系统联调与可视化展示。涉及心理健康与危机干预敏感内容，**请勿将模型或系统输出直接当作临床诊断**。
