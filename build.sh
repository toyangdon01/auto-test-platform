#!/bin/bash

# 自动化测试管理平台 - 打包脚本 (Linux/Mac)
# 将前端打包到后端 static 目录，生成单一可执行 JAR

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${CYAN}========================================${NC}"
echo -e "${CYAN}自动化测试管理平台 - 打包脚本${NC}"
echo -e "${CYAN}========================================${NC}"

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FRONTEND_DIR="${SCRIPT_DIR}/frontend"
BACKEND_DIR="${SCRIPT_DIR}/backend"
STATIC_DIR="${BACKEND_DIR}/src/main/resources/static"

# 1. 构建前端
echo -e "\n${YELLOW}[1/4] 构建前端...${NC}"
cd "${FRONTEND_DIR}"
npm run build
echo -e "${GREEN}前端构建完成${NC}"

# 2. 清理并创建 static 目录
echo -e "\n${YELLOW}[2/4] 准备静态资源目录...${NC}"
if [ -d "${STATIC_DIR}" ]; then
    rm -rf "${STATIC_DIR:?}"/*
else
    mkdir -p "${STATIC_DIR}"
fi

# 3. 复制前端构建产物到 static 目录
echo -e "\n${YELLOW}[3/4] 复制前端资源...${NC}"
DIST_DIR="${FRONTEND_DIR}/dist"
cp -r "${DIST_DIR}"/* "${STATIC_DIR}/"
echo -e "${GREEN}前端资源已复制到 static 目录${NC}"

# 4. 打包后端
echo -e "\n${YELLOW}[4/4] 打包后端...${NC}"
cd "${BACKEND_DIR}"
mvn clean package -DskipTests -q
echo -e "${GREEN}后端打包完成${NC}"

# 完成
JAR_FILE=$(find "${BACKEND_DIR}/target" -name "*.jar" ! -name "*-sources.jar" | head -n 1)
JAR_NAME=$(basename "${JAR_FILE}")
JAR_SIZE=$(du -h "${JAR_FILE}" | cut -f1)

echo -e "\n${CYAN}========================================${NC}"
echo -e "${GREEN}打包成功！${NC}"
echo -e "输出文件：${JAR_FILE}"
echo -e "文件大小：${JAR_SIZE}"
echo -e "\n启动命令：java -jar ${JAR_NAME}"
echo -e "访问地址：http://localhost:8080"
echo -e "${CYAN}========================================${NC}"
