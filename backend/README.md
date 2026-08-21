# VIS4SRD Java 后端

按**业务域分包**的 Spring Boot 3 后端（目录名 `backend/`）。  
风险检测可选连接 `inference-python`（:8001）做 Emocc / FeaLearner 真实推理。

## 已完成业务域

| 包 | 状态 | 说明 |
|----|------|------|
| `auth` / `system` | ✅ | 认证、健康检查 |
| `home` | ✅ | 首页统计 |
| `dataset` / `upload` | ✅ | 数据集、档案导入 |
| `archive` / `scale` | ✅ | 用户档案、心理量表 |
| `knowledge` / `model` | ✅ | 知识库、模型中心 |
| `map` | ✅ | PostgreSQL / PostGIS 机构与热线；高德 IP/逆地理 |
| `risk` | ✅ | 任务 CRUD / 报告；优先 Python 侧车神经网络，否则启发式 + 可选 LLM |
| `chat` | ✅ | 会话消息 CRUD、知识库检索 + LLM / SSE 流式 |

## 启动

```powershell
cd backend
# 配置 DB_* / PG_* / JWT_SECRET_KEY（可复制 application-local.yml.example）
mvn spring-boot:run "-Dspring-boot.run.profiles=local"
```

### 可选环境变量

| 变量 | 默认 | 说明 |
|------|------|------|
| `PORT` | `8000` | 服务端口 |
| `AMAP_WEB_SERVICE_KEY` | 空 | 高德 Web 服务 Key；未配置时 IP/逆地理降级 |
| `LLM_*` / 模型中心配置 | — | chat / risk 可选调用 LLM；未配置时走启发式/降级回复 |

## API 摘要

### map

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/institutions` | 机构列表 |
| GET | `/api/institutions/nearby` | 附近机构（PostGIS） |
| GET | `/api/hotlines` 等 | 热线 |
| GET | `/api/ip-location`、`/api/geocode/reverse` | IP / 逆地理 |

### risk（纯 Java）

`/api/risk/**`：`task-types`、`tasks` CRUD/execute/report、`emocc-*`、`fealearner-*`、`compare`、`predict` 等。  
Emocc / FeaLearner 为 Java 启发式证据聚合（对齐前端流程）；通用任务可走模型中心 LLM。

### chat（纯 Java）

| 方法 | 路径 | 说明 |
|------|------|------|
| CRUD | `/api/chat/sessions` | 会话 |
| GET/POST | `/api/chat/sessions/{id}/messages` | 消息 |
| POST | `/api/chat/sessions/{id}/messages/stream` | SSE 流式 |
| GET/POST | `/api/chat/recommended-questions`、`/quick-questions` | 推荐问题 |

对话 RAG：`chat/rag/SuiAgentRagService` 读取项目根 `knowledge/`（catalog 选文件 + 正文/PDF 片段），SSE 推送 `rag_sources` / `rag_evidence` / `mind_map`；回复走 `ModelService` / `LlmChatClient`。

## 说明

- 前端仍代理到 `:8000`，契约保持 `{ success, data }`。
- 已移除 Python `backend/`；知识语料在 `../knowledge`，上传在 `../uploads`。
- 不再需要 `PYTHON_SERVICE_BASE_URL` 或 8001 侧车进程。
