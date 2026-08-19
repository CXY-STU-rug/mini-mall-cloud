#!/usr/bin/env bash
# ============================================================
# 一键打包所有模块的 jar（在 Linux 服务器 / Git Bash 里跑）
# 打完 target 下有 jar，compose build 才拷得到
# ============================================================
set -e   # 任一步失败就停，别带着半成品继续

# 切到项目根(脚本在 deploy/ 下，上一级才是根)
cd "$(dirname "$0")/.."

echo ">>> 开始打包全部模块(跳过测试加速)..."
# -am 连带把依赖的 common 模块也打了；-DskipTests 上线打包先跳测试(测试单独在CI跑)
mvn clean package -DskipTests

echo ">>> 打包完成，各模块 target 下的 jar："
# 列一下产物确认(排除 common 这种没有主类的)
find . -path ./deploy -prune -o -name "*.jar" -path "*/target/*" -print | grep -v sources

echo ">>> 接下来在 deploy/ 下执行："
echo "    docker compose -f docker-compose.prod.yml --env-file .env up -d --build"
