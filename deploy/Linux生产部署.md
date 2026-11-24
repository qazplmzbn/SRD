# VIS4SRD 阿里云 ECS 首次部署：从 Windows 上传到 HTTPS 验收

适用：新 Ubuntu 22.04/24.04 单机、systemd、本机 MySQL 8、Nginx、已有域名。以下以 `/opt/vis4srd` 为项目根目录；仅管理系统、安装软件和安装服务时使用 sudo。不要直接运行旧 `deploy/production/deploy.sh`：它会导入旧库，且在 8000 启动已移除的 Python 业务后端。已有服务器请先做快照，把数据库、上传文件、配置与上一版产物独立备份，在测试环境完成恢复演练，再安排切换。宝塔已有 Nginx/MySQL 的机器请沿用已有实例，不要重复安装或覆盖现有站点。

## 0. 在阿里云购买 ECS、设置网络

1. ECS 控制台 → 创建实例 → 自定义购买。地域选靠近用户且符合机构数据存放要求的地域；选 VPC、交换机、公共镜像 **Ubuntu 22.04 LTS x86_64**，不用 GPU。中国内地公开网站先确认域名备案要求。
2. 单台同时跑 MySQL、Java 和 BERT，推荐 **8 vCPU / 32 GiB**（如 `ecs.g7.2xlarge`）；低预算单人演示可先试 **4 vCPU / 16 GiB**（如 `ecs.g7.xlarge`）。选 **120 GiB ESSD 系统盘、5 Mbps 公网带宽起步、公网 IPv4**。这是容量起点，不是并发保证。先按量用合成数据测两种模型的耗时、峰值内存，再决定长期规格。记入云盘、快照、公网和云模型费用。
3. 选 SSH 密钥对、登录名 `ecs-user`，私钥留在 Windows。安全组入方向 `22/TCP` 只放行管理员公网 IP `/32`；网站 `80/443` 按受众开放。不要公开 `8000/8001/3306/5432/5173`；出站须能访问依赖源和获准的云模型 HTTPS 接口。
4. 在域名 DNS 添加 A 记录指向 ECS 公网 IPv4。以下 `care.example.org`、公网 IP、密钥路径均替换为自己的值。Windows PowerShell 测试：`ssh -i "$HOME\.ssh\vis4srd.pem" ecs-user@公网IP`。首次连接核对主机指纹。

参考：[阿里云自定义购买步骤](https://help.aliyun.com/zh/ecs/user-guide/create-an-instance-by-using-the-wizard)、[通用型实例规格](https://help.aliyun.com/zh/ecs/user-guide/general-purpose-instance-families)。

## 1. 准备发布材料

从当前已修改的工作区发布，不要假定远程仓库或旧 zip 已包含最新修改。准备可信来源、与当前代码匹配的：

- 完整 MySQL 表结构与必要的非敏感初始化数据；只有空库不能完成登录。启用地图另需已初始化的 PostgreSQL 地图库及账号。
- `backend`、`frontend`、`inference-python`、`Emocc`、`Fealeaner`（目录就是这个拼法）、`datasets`、`knowledge`、`scales` 及实际需要的地图资源。两套模型要复制完整目录，包括模型代码、权重、词表、词典、冻结的 TF-IDF，以及平台任务需要的预计算特征。
- 与训练流程匹配的本地 BERT 目录（配置、tokenizer、模型权重），不是只下载一个 `.pth`。匹配的 BERT 与 Weibo 分类器是两份资源。
- 有权使用的云端模型 ID、部署 API Key、域名和 HTTPS。不要把数据库备份、个人 Key、真实用户上传、开发日志、`.env`、`node_modules`、`target`、`.venv` 一起打进公开发布包。

服务器安装基础依赖：

```bash
sudo apt update
sudo apt install openjdk-17-jdk maven python3 python3-venv build-essential mysql-server nginx certbot python3-certbot-nginx curl rsync
sudo systemctl enable --now mysql nginx
java -version
mvn -v
```

按 Node.js 官方发行方式在**构建用户**下安装 Node 22 LTS（已有则跳过），用 `node -v && npm -v` 确认。创建运行账号和目录：

```bash
id vis4srd >/dev/null 2>&1 || sudo useradd --system --create-home --home-dir /var/lib/vis4srd --shell /usr/sbin/nologin vis4srd
sudo install -d -o "$USER" /opt/vis4srd
sudo install -d -m 0755 /etc/vis4srd /var/www/vis4srd
```

### Windows → ECS 上传清单

先在 ECS 运行 `sudo install -d -o "$USER" /opt/models/care-bert` 和 `install -d -m 0700 ~/private-import`。Windows 打开 WinSCP，协议选 SFTP，使用上面的公网 IP、登录名、私钥和 22 端口。项目文件以下列本机根目录为准：`E:\AAA这学期项目\服务器版本V1.0.zip\服务器版本V1.0`。

| 从 Windows 选择 | 上传到 ECS | 说明 |
|---|---|---|
| `backend/pom.xml`、`backend/src/` | `/opt/vis4srd/backend/` | 保留 `src/main/resources/db/*.sql`；不传含秘密的本地配置 |
| `frontend/` 的源码、public、mobile、scripts、构建配置、HTML、package.json 和 package-lock.json | `/opt/vis4srd/frontend/` | 不传 node_modules、缓存、旧 dist、开发日志、私有 .env；网页不需要 android |
| `inference-python/` 源码和 requirements.txt | `/opt/vis4srd/inference-python/` | 不传 Windows 虚拟环境 |
| `Emocc/`、`Fealeaner/` | `/opt/vis4srd/` 下同名目录 | 两目录合计约 10.2 GiB；保留配套代码和特征，仅传获准使用的资源 |
| `datasets/`、`knowledge/`、`scales/`；地图需要时加 `map_data/` | `/opt/vis4srd/` 下同名目录 | 不传真实用户上传、个人 Key 或整个旧部署包 |
| `E:\models\bert-base-chinese` 内的 **config.json、tokenizer.json、tokenizer_config.json、vocab.txt、model.safetensors** | `/opt/models/care-bert/` | 直接选这五个文件，不要多套 `bert-base-chinese/`。本机 safetensors 权重为 411553788 字节，不需要重复传 PyTorch bin、TF、Flax、`.cache` 或 `.incomplete` |
| `deploy/vis4srd-init.sql` | `~/private-import/` | 新空库仅导入一次，不放入网站根目录或 JAR |

传完后应直接看到 `/opt/vis4srd/backend/pom.xml`。Windows 可用 `Get-FileHash 'E:\models\bert-base-chinese\model.safetensors'`，ECS 用 `sha256sum /opt/models/care-bert/model.safetensors`；散列应一致。确保服务账号可读源码、词典和模型，仅对必要运行目录授予写权限：

```bash
cd /opt/vis4srd
sudo install -d -o vis4srd -g vis4srd -m 0750 uploads/archives uploads/knowledge
sudo -u vis4srd test -r /opt/models/care-bert/config.json
sudo -u vis4srd test -r Emocc/weibo/Emocc_model/checkpoints/emocc_model.pth
sudo -u vis4srd test -r Fealeaner/bestmodel/my_weibo_model.pth
sudo -u vis4srd test -r Fealeaner/weibo_tfidf.json
```

## 2. 初始化数据库（仅新库）

使用 `sudo mysql` 进入本机管理员会话。以下为新实例示例；将密码换为密码管理器生成的独立值。运行时用专用账号，授权范围限本应用库，不用 root。

```sql
CREATE DATABASE vis4srd CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'vis4srd'@'127.0.0.1' IDENTIFIED BY '替换为随机数据库密码';
GRANT ALL PRIVILEGES ON vis4srd.* TO 'vis4srd'@'127.0.0.1';
```

**项目自带 SQL 不等于完整初始库。** `backend/src/main/resources/db/` 中 `personal-posts.sql` 创建贴文、预约授权和社交账号等表；`doctor-verification.sql` 创建医生认证表；`research-versions.sql` 和 `research-experiments.sql` 创建研究版本与实验表。Java 的数据库初始化器会从 JAR 加载这些脚本；不必手工逐个执行。它们不包含完整的登录等基础业务表，也不初始化生产账号。`CREATE TABLE IF NOT EXISTS` 不能更新已存在表的旧结构。

**ECS 终端**：只上传 `deploy/vis4srd-init.sql`。它包含 55 张 MySQL 表和公共种子（7 个首页入口、11 个首页统计项、1 个检测类型、3 个模型目录项），没有账号、患者数据或模型凭证。先确认 `SHOW TABLES` 返回空集，非空则停止。若已按旧手册导入结构和种子，不要再执行此文件。

```bash
chmod 600 ~/private-import/vis4srd-init.sql
mysql -h 127.0.0.1 -u vis4srd -p vis4srd -e 'SHOW TABLES;'
mysql --default-character-set=utf8mb4 -h 127.0.0.1 -u vis4srd -p vis4srd < ~/private-import/vis4srd-init.sql
mysql -h 127.0.0.1 -u vis4srd -p vis4srd -e 'SHOW TABLES;'
```

已有库不要重复导入；先与维护者核对版本。需要地图时，另恢复匹配的 `mental_health` 数据及空间数据库依赖，并用应用账号测试查询。只有在相应功能实际验收后才算完成。

### 2.1 医生认证：现版网页使用邀请码

不需要部署移动网页、单独的认证服务或上传目录。医生在桌面 Web 注册（初始角色 `researcher`），患者 APK 账号不能用来申请。**注册和输入邀请码都不是执业资质核验**，运营方应先通过机构认可的渠道人工核对本人身份、执业资质与所属机构。

1. 默认不开通认证。确需邀请已核验人员时，在 ECS 用 `sudoedit /etc/vis4srd/api.env` 写入 `DOCTOR_CERTIFICATION_INVITE_CODE=` 并在等号后填写密码管理器生成的随机长码。按第 5 节启动 API；已运行时执行 `sudo systemctl restart vis4srd-api`。不要放入 SQL、网页、APK 或群公告。
2. 私下发给已核验的医生，在 HTTPS 网页登录后打开 `/certification` 输入。成功时后端在同一事务中设置 `app_users.role='doctor'` 并登记 `care_doctors`。重新登录，用合成患者账号确认可在预约医生列表中找到该医生；未认证的研究者不应出现在列表中。
3. 此码可复用且不绑定申请人。办完即清空该变量并重启 API；用另一测试研究者确认旧码不能再认证。关闭邀请码**不会**撤销已授予的医生身份。不要用直接改角色的 SQL 代替认证流程。

**如果机构要求在线材料和逐人审批**：保持邀请码为空。项目有申请表组件、管理员审核页 `/verification-review` 和审核 API，但当前 `/certification` 接的是邀请码组件，且人工审核通过只更新角色，不登记预约目录。不能只靠初始化 SQL 开通完整流程。上线前须接通申请表，将审核通过与 `care_doctors` 登记放在同一事务，并测试通过、驳回、撤销及患者侧列表。首个审核员应由维护者核实专用 Web 账号后单独授予 `admin` 并重新登录，不能自审；初始化包不预置管理员。

材料流程接受不超过 5 MiB 的 PDF、PNG、JPEG，证明内容保存在 MySQL `doctor_applications.proof_content`，仅本人或管理员可取，审核及材料下载记录在 `doctor_application_events`。不要把材料暴露为静态文件；数据库备份也含证件，须限制访问、加密并设定保留期限。验收只用合成材料。

## 3. 建立推理环境、构建产物

```bash
cd /opt/vis4srd/inference-python
python3 -m venv .venv
.venv/bin/python -m pip install --upgrade pip
# CPU 部署；GPU 部署需按服务器驱动选择 PyTorch 的官方安装命令。
.venv/bin/python -m pip install torch --index-url https://download.pytorch.org/whl/cpu
.venv/bin/python -m pip install -r requirements.txt
.venv/bin/python -m pip check
cd /opt/vis4srd/backend
mvn clean package
test -s target/vis4srd-backend-1.0.0-SNAPSHOT.jar
cd /opt/vis4srd/frontend
npm ci --registry=https://registry.npmmirror.com
npm run build
sudo rsync -a dist/ /var/www/vis4srd/
```

以上是桌面 Web 前端。需要移动聊天 Web 页面时，在 `frontend` 下另执行下面命令，替换为最终公网 HTTPS 域名（无路径、无尾斜线）；使用独立发布目录和入口，避免覆盖桌面站静态资源：

```bash
VITE_API_BASE=https://care.example.org npm run build:mobile -- --base=/mobile/
sudo install -d /var/www/vis4srd/mobile
sudo rsync -a dist-mobile/ /var/www/vis4srd/mobile/
```

Android APK 在 Windows 上单独构建，不是启动服务器的前提。先完成第 7 节 HTTPS，手机能访问正式域名后，再按第 9 节制作安装包。APK 不依赖网站的 `/mobile/` 目录。

## 4. 配置 Java 环境与云服务

首次运行 `sudo install -m 0600 /dev/null /etc/vis4srd/api.env`，随后 `sudoedit /etc/vis4srd/api.env`。已有文件不要重新初始化。写入以下键值并替换示例值。这里使用 systemd 环境变量覆盖基础配置，**不需要 local profile**，也不依赖自动加载 `.env`。不要把开发用的 `application-local.yml` 或密钥打进 JAR。

```ini
HOST=127.0.0.1
PORT=8000
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=vis4srd
APP_DATASOURCE_MYSQL_USERNAME=vis4srd
APP_DATASOURCE_MYSQL_PASSWORD=替换为实际数据库密码
JWT_SECRET_KEY=替换为独立随机长密钥
PYTHON_INFERENCE_BASE_URL=http://127.0.0.1:8001
APP_PYTHONINFERENCE_FALLBACKHEURISTIC=false
LLM_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_API_KEY=替换为部署密钥
LLM_MODEL=qwen-flash
REPORT_MODEL=qwen-flash
PET_MODEL=qwen-flash
MOBILE_CHAT_MODEL=qwen-flash
EMOJI_MODEL=qwen-flash
CARE_ALLOW_EXTERNAL_MODELS=false
```

JWT 可用 `openssl rand -hex 48` 独立生成。当前基础 YAML 的 MySQL 用户和密码是字面值，所以不能只设 `DB_USER`、`DB_PASSWORD`。使用地图时补 `PG_HOST`、`PG_PORT`、`PG_NAME`、`PG_USER`、`PG_PASSWORD` 及需要的 `AMAP_WEB_SERVICE_KEY`。数据库均在本机或私网，不对公网开放。远程数据库应设置 TLS 连接，而非照搬默认本机 JDBC 配置。

医生直接分析使用模型中心的 API 配置；检测后报告用部署的 `REPORT_MODEL`。桌宠、移动问答、Emocc 表情转换使用各自的部署模型。研究者在登录后的个人模型设置中输入自己的供应商、模型 ID、Key 和报告提示词，不要将个人 Key 放进服务器配置。医生预约数据默认禁止外发；仅在机构批准供应商、授权和数据处理流程后，才设 `CARE_ALLOW_EXTERNAL_MODELS=true` 并重启 Java，业务上的逐次授权仍须有效。代理和应用日志不要记录请求体或授权头。

## 5. 使用 systemd 启动服务

使用 `sudoedit /etc/systemd/system/vis4srd-inference.service` 建立侧车：

```ini
[Unit]
Description=VIS4SRD model inference
After=network.target
[Service]
User=vis4srd
WorkingDirectory=/opt/vis4srd/inference-python
Environment=CARE_BERT_DIR=/opt/models/care-bert
ExecStart=/opt/vis4srd/inference-python/.venv/bin/python -m uvicorn app:app --host 127.0.0.1 --port 8001 --workers 1
Restart=on-failure
RestartSec=5
UMask=0027
[Install]
WantedBy=multi-user.target
```

使用 `sudoedit /etc/systemd/system/vis4srd-api.service` 建立 Java 服务：

```ini
[Unit]
Description=VIS4SRD Spring Boot API
After=network.target mysql.service vis4srd-inference.service
[Service]
User=vis4srd
WorkingDirectory=/opt/vis4srd/backend
EnvironmentFile=/etc/vis4srd/api.env
ExecStart=/usr/bin/java -jar /opt/vis4srd/backend/target/vis4srd-backend-1.0.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=5
UMask=0027
[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now vis4srd-inference vis4srd-api
systemctl --no-pager status vis4srd-inference vis4srd-api
curl -fsS http://127.0.0.1:8001/health
curl -fsS http://127.0.0.1:8000/api/health
```

## 6. 验收两套 Weibo 模型

在服务器本机执行以下请求，只用合成文本，不使用患者数据。首次加载较慢。两个请求分别测试真实分类器，不消耗云端 Key。

```bash
curl --fail-with-body -sS http://127.0.0.1:8001/infer/appointment-review -H 'Content-Type: application/json' -d '{"model":"fealearner","posts":["今天整理了书桌。","下午去公园散步。"]}'
curl --fail-with-body -sS http://127.0.0.1:8001/infer/appointment-review -H 'Content-Type: application/json' -d '{"model":"emocc","posts":["今天整理了书桌。"],"emojiSequences":["📚📝🙂"]}'
```

验收：HTTP 200、有对应模型、`classIndex` 为 0 或 1、有 `riskLevel` 和 `riskScore`；Emocc 还应有 `inferenceBackend: torch_checkpoint`。不要要求某段合成文本一定被判为某类。Emocc 这里是手工提供表情，只证明分类部分，云端转表情须在第 8 步验证。遇到 503 优先查 `CARE_BERT_DIR`、模型权重、词表、词典及服务账号读权限；不要改为启发式来通过验收。

## 7. 配置域名和 HTTPS

将域名解析到服务器；云安全组仅向公众开放 80/443，SSH 限管理来源。不要公开 8000、8001、3306、5432 或 Vite 5173。使用 `sudoedit /etc/nginx/conf.d/vis4srd.conf`，替换域名：

```nginx
server {
    listen 80;
    server_name care.example.org;
    root /var/www/vis4srd;
    index index.html;
    client_max_body_size 120m;
    location /api/ {
        proxy_pass http://127.0.0.1:8000;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 300s;
        proxy_buffering off;
    }
    location /uploads/ { proxy_pass http://127.0.0.1:8000/knowledge/; }
    location /mobile/ { try_files $uri $uri/ /mobile/index.html; }
    location / { try_files $uri $uri/ /index.html; }
}
```

不要把项目根目录或整个 `uploads` 作为静态目录发布。若未部署移动 Web，删除 `/mobile/` 的 location。使用已有证书或执行：

```bash
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx --redirect -d care.example.org
sudo certbot renew --dry-run
curl -fsS https://care.example.org/api/health
```

## 8. 放行前验收与回退

1. 用合成测试账户登录，刷新桌面页面、打开移动入口；检查浏览器 API 请求走正式 HTTPS 域名，不是 localhost。验证档案上传和授权读取；未授权用户不得取得档案、报告或附件。
2. 研究者用测试供应商账号配置 Key 和模型，在自有档案分别执行直接分析、Emocc、FeaLearner。核对供应商用量、所选模型和报告；用无效测试 Key 时必须明确失败，不得悄悄改用部署 Key。Emocc 的转表情用部署密钥，另行核对。
3. 医生用合成预约测试未授权、禁止外发、已授权且允许外发三种情况；前两种不得发送受限数据。后一种分别验收直接 API、Emocc、FeaLearner，检查报告没有覆盖检测器原始等级和分数。
4. 平台风险任务另测：确保数据集的预计算特征齐全，运行任务并检查结果来源。`heuristic_fallback` 不算真实模型推理。在测试环境短暂停侧车，确认失败能被看见，然后恢复服务。
5. 验证桌宠气泡、移动聊天和模型供应商用量；若部署地图，另测地图查询。所有测试通过后才导入或开放真实授权数据。
6. 排障：`sudo journalctl -u vis4srd-api -u vis4srd-inference -n 100 --no-pager`。502 先查 Java；503 先查侧车和模型；401/403 分辨登录、业务授权与供应商鉴权；不要把正文或密钥贴入工单。
7. 后续升级保留上一版 JAR、前端目录、侧车环境、模型清单和数据库快照；配置改动后重启对应服务，unit 改动后先 `daemon-reload`。回退时配套恢复兼容版本；已产生业务写入的数据库不要直接覆盖，应先停写并制定数据恢复方案。

## 9. Windows 制作、安装和发布安卓 APK

本项目用 Capacitor 8 将 `dist-mobile` 页面装进 APK。应用名「我的日常」，包名 `com.vis4srd.companion`，最低 Android 7.0（API 24）。模型、数据库、云端 Key 都留在 ECS；APK 只需要知道 API 的公网 HTTPS 地址。

### 9.1 准备 Windows 构建环境

1. 安装 Node.js 22 和 Android Studio。Studio 的 SDK Manager 中勾选 **Android SDK Platform 36**、**Android SDK Build-Tools 36.0.0**（需要时勾选 Show Package Details）和 **Android SDK Platform-Tools**。记录 SDK Location。
2. 准备 **JDK 21**。可使用 Android Studio 自带的 `jbr`，前提是下方脚本检查通过。它与服务器运行 Java 后端的 JDK 17 互不影响。
3. 用手机浏览器访问正式域名的 `/api/health`，应成功且没有证书警告。在 Windows 同一个 PowerShell 窗口执行下列命令；替换域名，安装目录不同也要改。`VITE_API_BASE` 必须是 HTTPS 源地址，不能带 `/api`、`/mobile/`、尾斜线或填 localhost。不要将密码、Token 或模型 Key 写入 `VITE_*`。

```powershell
Set-Location 'E:\AAA这学期项目\服务器版本V1.0.zip\服务器版本V1.0\frontend'
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:VITE_API_BASE = 'https://care.example.org'
.\scripts\build-android.ps1 -CheckOnly
node -v
npm.cmd ci --registry=https://registry.npmmirror.com
```

看到 `Android build prerequisites passed.` 且依赖安装成功后继续。如 Gradle 报项目路径含非 ASCII 字符，复制干净的 `frontend` 源码（含 `android` 和 Gradle wrapper，不含 `node_modules`、构建缓存）到如 `C:\src\vis4srd\frontend`，在那里重新执行本节。若需抖音登录回调，应在构建前按开放平台登记的实际域名设置 `DOUYIN_ANDROID_CALLBACK_HOST`；不启用可跳过。

### 9.2 先做 Debug 包并用真机测试

```powershell
.\scripts\build-android.ps1
```

脚本依次执行 `npm run android:sync`（构建移动页面并同步 Android 工程）和 `gradlew.bat assembleDebug`，成功后打印 APK 路径、大小及 SHA-256。产物是 `android\app\build\outputs\apk\debug\app-debug.apk`。这里不要沿用网页构建的 `--base=/mobile/`。域名是构建时写入的；修改后必须重新同步、打包并安装。

手机开启开发者选项和 USB 调试，连接电脑并确认授权。在刚才的 PowerShell 窗口执行：

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
& $adb devices
& $adb install -r '.\android\app\build\outputs\apk\debug\app-debug.apk'
```

只有设备状态为 `device` 且安装返回 `Success` 才继续。用移动网络打开「我的日常」，用合成测试账号完成登录、读取记录、发送一条测试聊天并收到回复。浏览器能打开健康页不代表 APK 能登录；若仅 App 请求失败，核对包内目标域名、证书链及服务端是否允许实际 WebView 来源的跨域请求，不要关闭 TLS 校验或对所有来源开放。Debug 包仅供测试。

### 9.3 给用户分发：生成签名 Release APK

1. 在 `android/app/build.gradle` 核对 `applicationId`；覆盖升级要保留原包名和原签名密钥，每次发布递增 `versionCode`，同步设置给用户看的 `versionName`。
2. 在第 9.1 节同一窗口执行 `npm.cmd run android:sync`，成功后再执行 `npm.cmd run android:open`。在 Studio 等待 Gradle 同步完成，确认 Gradle JDK 为 21。不要跳过同步，否则可能打入旧页面或旧域名。
3. 选择 **Build → Generate Signed App Bundle or APK → APK**（不是 AAB）→ 模块 `app`。首次自发行选 **Create new**，把 `.jks` 放在项目外的受保护位置；已有正式版本选原 keystore 和 alias。选 `release`，记录向导显示的输出目录，完成后点击 **locate** 找到签名包。只运行 `assembleRelease` 不能证明 APK 已签名。
4. 在 PowerShell 验证签名和文件哈希。按提示输入向导实际输出的 APK 完整路径（不带引号）：

```powershell
$apk = Read-Host '签名 Release APK 完整路径'
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs "$apk"
if ($LASTEXITCODE -ne 0) { throw '签名校验失败，不可分发' }
Get-FileHash -Algorithm SHA256 -LiteralPath $apk
```

5. 在测试机上安装并重复第 9.2 节业务验收。有旧正式版时测试覆盖升级；若测试机上是同包名 Debug 包，因签名不同不能覆盖，请换干净测试机，或确认不需要测试数据后卸载 Debug 包再安装。不要让正式用户卸载来绕过签名不匹配。
6. 仅分发**已验签且通过真机验收的 Release APK**。可通过机构认可的渠道发送；如需 ECS 提供公开下载，通过 SFTP 上传 APK 到管理账号家目录，重命名为 `vis4srd-1.0.apk`（版本号按实际修改），再在 ECS 执行 `sudo install -D -m 0644 ~/vis4srd-1.0.apk /var/www/vis4srd/downloads/vis4srd-1.0.apk`。用手机下载 `https://实际域名/downloads/vis4srd-1.0.apk`，核对下载文件 SHA-256 与第 4 步一致。公开目录不适合限制受众的内测包。签名私钥与密码分别安全备份，绝不上传 ECS、提交到仓库或随 APK 分发。