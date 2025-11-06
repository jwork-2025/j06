@echo off
setlocal enabledelayedexpansion

rem 启动 server（本地窗口）
echo Starting server...
start "Game Server" cmd /c "run.bat"

rem 等待 server 启动
timeout /t 2 /nobreak >nul

rem 启动 client（连接到本机）
echo Starting client...
call run_client.bat 127.0.0.1

echo.
echo Client closed. Server window may still be running.
echo Please close the server window manually if needed.
