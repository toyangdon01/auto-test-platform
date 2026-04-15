
# 设置NPM源，提升安装速度
npm config set registry https://registry.npmmirror.com

cd frontend
npm install
npm run build
mkdir -p ../backend/src/main/resources/static
cp -r dist/* ../backend/src/main/resources/static/
echo "前端构建完成"

#JDK环境
cd ..
mkdir -p temp-linux/jdk dist/auto-test-platform-linux-arm64 
curl -L  https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz -o temp-linux/jdk.tar.gz

tar -xzf temp-linux/jdk.tar.gz -C temp-linux/jdk/
JDK_DIR="$(pwd)/temp-linux/jdk/jdk-17.0.9+9"

export JAVA_HOME="$JDK_DIR/"
export PATH="$JAVA_HOME/bin:$PATH"
cd backend
mvn package -DskipTests
mkdir -p ../dist
cp target/*.jar ../dist/auto-test-platform-1.0.0.jar
echo "后端构建完成“

#打包linux
cd ..
curl -L  https://gh-proxy.org/https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jre_aarch64_linux_hotspot_17.0.9_9.tar.gz -o temp-linux/jre.tar.gz
tar -xzf temp-linux/jre.tar.gz -C dist/auto-test-platform-linux-arm64
cp dist/auto-test-platform-1.0.0.jar dist/auto-test-platform-linux-arm64/auto-test-platform.jar
cat > dist/auto-test-platform-linux-arm64/start.sh << 'EOF'
#!/bin/bash
JRE_DIR="./jdk-17.0.9+9"
export JAVA_HOME="$JRE_DIR/jdk-17.0.9+9-jre"
export PATH="$JAVA_HOME/bin:$PATH"
java -jar "./auto-test-platform.jar"
EOF
chmod +x dist/auto-test-platform-linux-arm64/start.sh
# 创建说明文件
cat > dist/auto-test-platform-linux-arm64/README.txt << 'EOF'
使用说明：
1. 运行 ./start.sh
2. 启动后访问 http://localhost:8080
EOF
cd dist && tar -czf auto-test-platform-linux-arm64.tar.gz auto-test-platform-linux-arm64 && cd ..
echo "Linux ARM64 打包完成"

#打包windows
mkdir -p temp-windows dist/auto-test-platform-windows-x64
curl -L  https://gh-proxy.org/https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9.1/OpenJDK17U-jre_x64_windows_hotspot_17.0.9_9.zip -o temp-windows/jre.zip
unzip temp-windows/jre.zip
cd ..

# 整理目录
JRE_DIR=$(ls temp-windows | grep -i jdk | grep -i jre | head -1)
mv jdk-17.0.9+9-jre dist/auto-test-platform-windows-x64/jdk-17.0.9+9-jre

# 复制 JAR
cp dist/auto-test-platform-1.0.0.jar dist/auto-test-platform-windows-x64/auto-test-platform.jar

# 创建启动脚本
cat > dist/auto-test-platform-windows-x64/start.bat << 'EOF'
@echo off
cd /d "%~dp0"
set "JAVA_HOME=%~dp0jdk-17.0.9+9-jre"
set "PATH=%JAVA_HOME%\bin;%PATH%"
java -jar auto-test-platform.jar
pause
EOF

# 创建说明文件
cat > dist/auto-test-platform-windows-x64/README.txt << 'EOF'
使用说明：
1. 双击运行 start.bat
2. 启动后访问 http://localhost:8080
EOF

# 打包成 zip
cd dist
zip -r auto-test-platform-windows-x64.zip auto-test-platform-windows-x64
cd ..


echo "Windows x86 打包完成"
