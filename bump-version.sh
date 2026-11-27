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

new_ver="${cur_ver%.*}.$(( ${cur_ver##*.} + 1 ))"
new_code=$((cur_code + 1))

echo "版本: $cur_ver -> $new_ver"
echo "code: $cur_code -> $new_code"

if [ "$DRY" -eq 1 ]; then
    echo "[dry] 不写回"
    exit 0
fi

sed -i "s/versionName \"${cur_ver}\"/versionName \"${new_ver}\"/" "$GRADLE"
sed -i "s/versionCode ${cur_code}/versionCode ${new_code}/" "$GRADLE"

# version.json：版本号 + 资产名（稳定版资产名是 app-debug.apk，只换 tag 目录）
if [ -f version.json ]; then
    sed -i "s/\"version\": *\"${cur_ver}\"/\"version\": \"${new_ver}\"/" version.json
    sed -i "s#/download/v${cur_ver}/#/download/v${new_ver}/#g" version.json
fi

# 官网：版本号与下载链接
for f in index.html classic/index.html; do
    [ -f "$f" ] || continue
    sed -i "s/v${cur_ver}/v${new_ver}/g" "$f"
done

# 自检：三处版本必须一致，否则直接失败
GVER=$(grep -oP 'versionName "\K[^"]+' "$GRADLE")
JVER=$(grep -oP '"version"\s*:\s*"\K[^"]+' version.json 2>/dev/null || echo "")
HVER=$(grep -oP '当前版本 v\K[0-9.]+' index.html 2>/dev/null | head -1 || echo "")
if [ "$GVER" != "$JVER" ] || { [ -n "$HVER" ] && [ "$GVER" != "$HVER" ]; }; then
    echo "版本仍不一致：gradle=$GVER json=$JVER index=$HVER" >&2
    exit 1
fi

if ! grep -q "^## ${new_ver}" CHANGELOG.md 2>/dev/null; then
    echo "提醒：CHANGELOG.md 还没有 '## ${new_ver}' 段，CI 会因此失败。" >&2
fi

echo "已写回 $GRADLE / version.json / index.html"
grep -n "versionName\|versionCode" "$GRADLE" | head -2
