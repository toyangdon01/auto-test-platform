# 自动化测试管理平台 - 打包脚本
# 将前端打包到后端 static 目录，生成单一可执行 JAR

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "自动化测试管理平台 - 打包脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$ErrorActionPreference = "Stop"

# 获取脚本所在目录
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$FrontendDir = Join-Path $ScriptDir "frontend"
$BackendDir = Join-Path $ScriptDir "backend"
$StaticDir = Join-Path $BackendDir "src\main\resources\static"

try {
    # 1. 构建前端
    Write-Host "`n[1/4] 构建前端..." -ForegroundColor Yellow
    Push-Location $FrontendDir
    npm run build
    if ($LASTEXITCODE -ne 0) {
        throw "前端构建失败"
    }
    Pop-Location
    Write-Host "前端构建完成" -ForegroundColor Green

    # 2. 清理并创建 static 目录
    Write-Host "`n[2/4] 准备静态资源目录..." -ForegroundColor Yellow
    if (Test-Path $StaticDir) {
        Remove-Item -Path "$StaticDir\*" -Recurse -Force
    } else {
        New-Item -ItemType Directory -Path $StaticDir -Force | Out-Null
    }

    # 3. 复制前端构建产物到 static 目录
    Write-Host "`n[3/4] 复制前端资源..." -ForegroundColor Yellow
    $DistDir = Join-Path $FrontendDir "dist"
    Copy-Item -Path "$DistDir\*" -Destination $StaticDir -Recurse -Force
    Write-Host "前端资源已复制到 static 目录" -ForegroundColor Green

    # 4. 打包后端
    Write-Host "`n[4/4] 打包后端..." -ForegroundColor Yellow
    Push-Location $BackendDir
    mvn clean package -DskipTests -q
    if ($LASTEXITCODE -ne 0) {
        throw "后端打包失败"
    }
    Pop-Location
    Write-Host "后端打包完成" -ForegroundColor Green

    # 完成
    $JarFile = Get-ChildItem -Path "$BackendDir\target" -Filter "*.jar" | 
               Where-Object { $_.Name -notlike "*-sources.jar" } | 
               Select-Object -First 1
    
    Write-Host "`n========================================" -ForegroundColor Cyan
    Write-Host "打包成功！" -ForegroundColor Green
    Write-Host "输出文件: $($JarFile.FullName)" -ForegroundColor White
    Write-Host "文件大小: $([math]::Round($JarFile.Length / 1MB, 2)) MB" -ForegroundColor White
    Write-Host "`n启动命令: java -jar $($JarFile.Name)" -ForegroundColor Yellow
    Write-Host "访问地址: http://localhost:8080" -ForegroundColor Yellow
    Write-Host "========================================" -ForegroundColor Cyan

} catch {
    Write-Host "`n打包失败: $_" -ForegroundColor Red
    exit 1
}
