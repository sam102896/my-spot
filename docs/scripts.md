# 脚本使用说明

说明：脚本按操作系统分目录存放。

## 一键启动（推荐）
- Windows：`.\scripts\windows\start-all.ps1`
- Linux：`./scripts/linux/start-all.sh`

启动后
- 后端：`http://localhost:3001`
- 前端：`http://localhost:5173`

## 单独启动后端
- Windows：`.\scripts\windows\start-backend.ps1`
- Linux：`./scripts/linux/start-backend.sh`

## 单独启动前端
- Windows：`.\scripts\windows\start-web.ps1`
- Linux：`./scripts/linux/start-web.sh`

说明
- 前端脚本会在首次运行时自动执行依赖安装

## 重置本地数据库
- Windows：`.\scripts\windows\reset-db.ps1`
- Linux：`./scripts/linux/reset-db.sh`

说明
- 后端使用 H2 文件库，默认位于 `backend\data\`。重置会删除该目录。

## 性能压测（JMH）
- Windows：`.\scripts\windows\run-perf-jmh.ps1`
- Linux：`./scripts/linux/run-perf-jmh.sh`

示例（4 线程，输出 JSON）
- Windows：
  - `.\scripts\windows\run-perf-jmh.ps1 ".*SpringTradingBenchmark.*" -t 4 -wi 2 -i 3 -f 1 -rf json -rff perf-result.json`
- Linux：
  - `./scripts/linux/run-perf-jmh.sh ".*SpringTradingBenchmark.*" -t 4 -wi 2 -i 3 -f 1 -rf json -rff perf-result.json`
