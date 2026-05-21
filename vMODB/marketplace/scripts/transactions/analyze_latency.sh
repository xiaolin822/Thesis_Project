#!/bin/bash
set -euo pipefail

usage() {
  echo "Usage: $0 [-c] [latency_file]"
  echo "  -c: output CSV (count,avg,min,max,p50,p90,p95,p99)"
  exit 1
}

csv_mode=0
file="latency_ms.csv"

# 解析参数
while [[ $# -gt 0 ]]; do
  case "$1" in
    -c)
      csv_mode=1
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      file="$1"
      shift
      ;;
  esac
done

if [[ ! -f "$file" ]]; then
  echo "File not found: $file" >&2
  exit 1
fi

# 基本统计：count / avg / min / max
read count avg min max <<< "$(awk '
NR==1 {min=$1; max=$1}
{
  sum += $1
  if ($1 < min) min = $1
  if ($1 > max) max = $1
}
END {
  if (NR > 0) {
    printf "%d %.6f %d %d\n", NR, sum/NR, min, max
  }
}' "$file")"

if [[ "$count" -eq 0 ]]; then
  echo "No data in $file" >&2
  exit 1
fi

# 排序后算分位数
tmpfile=$(mktemp)
trap 'rm -f "$tmpfile"' EXIT

sort -n "$file" > "$tmpfile"

percentile() {
  local p="$1"   # 例如 50 / 90 / 95 / 99
  local n="$2"
  # ceil(p * n / 100)
  local idx=$(( (p * n + 99) / 100 ))
  (( idx < 1 )) && idx=1
  (( idx > n )) && idx=$n
  sed -n "${idx}p" "$tmpfile"
}

p50=$(percentile 50 "$count")
p90=$(percentile 90 "$count")
p95=$(percentile 95 "$count")
p99=$(percentile 99 "$count")

if [[ "$csv_mode" -eq 1 ]]; then
  # 只输出值，逗号分隔，方便汇总
  # count,avg,min,max,p50,p90,p95,p99
  printf "%s,%.6f,%s,%s,%s,%s,%s,%s\n" \
    "$count" "$avg" "$min" "$max" "$p50" "$p90" "$p95" "$p99"
else
  echo "file   : $file"
  echo "count  : $count"
  echo "avg    : $avg ms"
  echo "min    : $min ms"
  echo "max    : $max ms"
  echo "p50    : $p50 ms"
  echo "p90    : $p90 ms"
  echo "p95    : $p95 ms"
  echo "p99    : $p99 ms"
fi
