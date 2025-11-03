#!/bin/bash
set -e

# 启动 server（本地窗口）
./run.sh &
SERVER_PID=$!

sleep 2

# 启动 client（连接到本机）
./run_client.sh 127.0.0.1

# 退出时清理 server 进程
trap 'kill ${SERVER_PID} 2>/dev/null || true' EXIT


