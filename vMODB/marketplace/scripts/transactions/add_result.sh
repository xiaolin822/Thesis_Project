#!/bin/bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
  echo "Usage: $0 <system> <concurrency> <tx>" >&2
  exit 1
fi

system="$1"      # 比如 baseline / new_impl
concurrency="$2" # 比如 8 / 16 / 32
tx="$3"          # 比如 1000 / 5000

# 第一次建文件时写表头
if [[ ! -f results_all.csv ]]; then
  echo "system,concurrency,tx,count,avg,min,max,p50,p90,p95,p99" > results_all.csv
fi

# 调用之前写好的 analyze_latency.sh
line=$(./analyze_latency.sh -c latency_ms.csv)

# 拼在前面
echo "$system,$concurrency,$tx,$line" >> results_all.csv
