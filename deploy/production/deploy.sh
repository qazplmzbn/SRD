#!/bin/bash
# =============================================
# VIS4SRD 部署脚本
# 使用方法: ./deploy.sh
# =============================================

set -euo pipefail

echo "========================================"
echo "VIS4SRD 生产环境部署脚本"
echo "========================================"

GREEN='\033[0;32m'
NC='\033[0m'

PROJECT_ROOT="/www/wwwroot/vis4srd"
DEPLOY_DIR="$PROJECT_ROOT/deploy/production"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"
VENV_DIR="$PROJECT_ROOT/.venv"
LOG_DIR="$DEPLOY_DIR/logs"
MYSQL_DB="vis4srd"
PG_DB="mental_health"

cd "$PROJECT_ROOT"
mkdir -p "$LOG_DIR"

echo -e "\n${GREEN}[1/7]${NC} 准备 Python 虚拟环境..."
if [ ! -d "$VENV_DIR" ]; then
    python3 -m venv "$VENV_DIR"
fi
"$VENV_DIR/bin/pip" install --upgrade pip setuptools wheel -i https://pypi.tuna.tsinghua.edu.cn/simple
"$VENV_DIR/bin/pip" install -r "$BACKEND_DIR/requirements.txt" -i https://pypi.tuna.tsinghua.edu.cn/simple

echo -e "\n${GREEN}[2/7]${NC} 准备数据库..."
mysql -h 127.0.0.1 -u root -proot -e "CREATE DATABASE IF NOT EXISTS ${MYSQL_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='${PG_DB}'" | grep -q 1 || sudo -u postgres createdb "$PG_DB"

echo -e "\n${GREEN}[3/7]${NC} 导入数据库..."
mysql -h 127.0.0.1 -u root -proot "$MYSQL_DB" < "$BACKEND_DIR/deploy/mysql_full.sql"
sudo -u postgres psql -d "$PG_DB" -f "$BACKEND_DIR/deploy/postgres_full.sql" >/dev/null

if [ -f "$DEPLOY_DIR/.env.production" ]; then
    echo -e "\n${GREEN}[4/7]${NC} 更新后端环境变量..."
    cp "$DEPLOY_DIR/.env.production" "$BACKEND_DIR/.env"
else
    echo -e "\n${GREEN}[4/7]${NC} 跳过环境变量覆盖（未找到 $DEPLOY_DIR/.env.production）..."
fi

echo -e "\n${GREEN}[5/7]${NC} 构建前端..."
npm install --prefix "$FRONTEND_DIR" --registry=https://registry.npmmirror.com
if [ -d "$FRONTEND_DIR/node_modules/.bin" ]; then
    find "$FRONTEND_DIR/node_modules/.bin" -type f -exec chmod +x {} +
fi
npm run build --prefix "$FRONTEND_DIR"

echo -e "\n${GREEN}[6/7]${NC} 配置 Nginx..."
mkdir -p /usr/share/nginx/html/vis4srd
rm -rf /usr/share/nginx/html/vis4srd/*
cp -r "$FRONTEND_DIR/dist/"* /usr/share/nginx/html/vis4srd/

if [ -x /www/server/nginx/sbin/nginx ] && [ -d /www/server/panel/vhost/nginx ]; then
    cp "$FRONTEND_DIR/nginx.conf" /www/server/panel/vhost/nginx/vis4srd.conf
    /www/server/nginx/sbin/nginx -t -c /www/server/nginx/conf/nginx.conf
    /www/server/nginx/sbin/nginx -s reload -c /www/server/nginx/conf/nginx.conf
else
    cp "$FRONTEND_DIR/nginx.conf" /etc/nginx/conf.d/vis4srd.conf
    nginx -t
    systemctl enable nginx >/dev/null 2>&1 || true
    systemctl restart nginx
fi

echo -e "\n${GREEN}[7/7]${NC} 配置并启动后端服务..."
cat > /etc/systemd/system/vis4srd-api.service <<EOF
[Unit]
Description=VIS4SRD FastAPI Backend Service
After=network.target

[Service]
User=root
WorkingDirectory=$BACKEND_DIR
Environment=PYTHONUNBUFFERED=1
Environment=PATH=$VENV_DIR/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/bin
ExecStart=$VENV_DIR/bin/uvicorn src.app:app --host 0.0.0.0 --port 8000 --workers 2
Restart=always
RestartSec=5
StandardOutput=append:$LOG_DIR/vis4srd-api.log
StandardError=append:$LOG_DIR/vis4srd-api-error.log

[Install]
WantedBy=multi-user.target
EOF

systemctl unmask vis4srd-api >/dev/null 2>&1 || true
systemctl daemon-reload
systemctl enable vis4srd-api >/dev/null
systemctl restart vis4srd-api

echo -e "\n${GREEN}[完成]${NC} 部署完成"
systemctl --no-pager --full status vis4srd-api || true
