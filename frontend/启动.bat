@echo off
chcp 65001 >nul
title 驾校预约系统 - 前端

rem bat 本身就在 frontend 目录里，%~dp0 已经指向该目录，不要再拼 frontend
cd /d "%~dp0"

where npm >nul 2>nul
if errorlevel 1 (
    echo [错误] 找不到 npm，请先安装 Node.js 并加入 PATH
    pause
    exit /b 1
)

if not exist "node_modules" (
    echo [1/2] 首次运行，正在安装依赖，可能需要几分钟...
    call npm install
    if errorlevel 1 (
        echo [错误] 依赖安装失败
        echo        可先执行：npm config set registry https://registry.npmmirror.com
        pause
        exit /b 1
    )
) else (
    echo [1/2] 依赖已存在，跳过安装
)

echo [2/2] 启动开发服务器 http://localhost:25173
echo        接口代理到 http://localhost:8000（网关）
echo.
call npm run dev

pause
