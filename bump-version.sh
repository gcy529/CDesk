#!/bin/bash
# 每次迭代 bump 一个小版本:versionName 末位 +1,versionCode +1
# 用法: ./bump-version.sh [--dry]  (--dry 只打印不写回)
set -euo pipefail

GRADLE="app/build.gradle"
DRY=0
[ "${1:-}" = "--dry" ] && DRY=1

if [ ! -f "$GRADLE" ]; then
    echo "找不到 $GRADLE,请在项目根目录运行" >&2
    exit 1
fi

cur_ver=$(grep -oP 'versionName\s+"\K[^"]+' "$GRADLE")
cur_code=$(grep -oP 'versionCode\s+\K\d+' "$GRADLE")

if [ -z "$cur_ver" ] || [ -z "$cur_code" ]; then
    echo "未找到 versionName / versionCode" >&2
    exit 1
fi

IFS='.' read -r major minor patch <<< "$cur_ver"
new_patch=$((patch + 1))
new_ver="${major}.${minor}.${new_patch}"
new_code=$((cur_code + 1))

echo "版本: $cur_ver -> $new_ver"
echo "code: $cur_code -> $new_code"

if [ "$DRY" -eq 1 ]; then
    echo "[dry] 不写回"
    exit 0
fi

sed -i "s/versionName \"${cur_ver}\"/versionName \"${new_ver}\"/" "$GRADLE"
sed -i "s/versionCode ${cur_code}/versionCode ${new_code}/" "$GRADLE"

echo "已写回 $GRADLE"
grep -n "versionName\|versionCode" "$GRADLE" | head -2
