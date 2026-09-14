#!/usr/bin/env bash
#
# 一键发版（本地）：构建签名 release APK → 归档到 app/release/ → 同步 version.json 与元数据。
#
# 刻意不做的事：不自动 commit / push。发布是对外动作，留给人确认。
#
# 用法：  ./scripts/release.sh
# 前置：  1) 根目录 keystore.properties 已填好（不入库）
#         2) JAVA_HOME 指向 JDK 17 或 21（Gradle 8.11.1 不支持 Java 24+）
#
# 发版前记得先改 app/build.gradle.kts 里的 versionCode / versionName。

set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
echo "仓库根目录：$ROOT"

# ── 0. 找 python（脚本用它同步版本号）────────────────────────────────
PY=""
for candidate in python python3 py; do
  if command -v "$candidate" >/dev/null 2>&1; then PY="$candidate"; break; fi
done
if [ -z "$PY" ]; then
  echo "[错误] 找不到 python，无法同步版本号。装一个 Python 3 再试。" >&2
  exit 1
fi

# ── 1. 前置检查 ────────────────────────────────────────────────────
if [ ! -f keystore.properties ]; then
  echo "[错误] 缺少 keystore.properties。先执行：" >&2
  echo "         cp keystore.properties.example keystore.properties  然后填好四项" >&2
  exit 1
fi

if [ -z "${JAVA_HOME:-}" ]; then
  echo "[提示] JAVA_HOME 未设置，Gradle 会自行查找 JDK。本项目需要 17 或 21，不能用 24+。"
fi

# ── 2. 构建 ───────────────────────────────────────────────────────
echo
echo "▶ 构建 release APK…"
./gradlew assembleRelease

APK_SRC="$(ls -t app/build/outputs/apk/release/*_release.apk 2>/dev/null | head -1 || true)"
if [ -z "$APK_SRC" ]; then
  echo "[错误] 没找到产物 app/build/outputs/apk/release/*_release.apk" >&2
  exit 1
fi
APK_NAME="$(basename "$APK_SRC")"
echo "产物：$APK_SRC"

# ── 3. 归档到 app/release/ ────────────────────────────────────────
echo
echo "▶ 归档到 app/release/…"
cp -f "$APK_SRC" "app/release/$APK_NAME"
echo "已归档：app/release/$APK_NAME"

# ── 4. 同步版本号 ─────────────────────────────────────────────────
# 放在构建之后：构建失败时 version.json 不会被提前改到不存在的产物上。
echo
echo "▶ 同步 version.json 与 app/release/output-metadata.json…"
"$PY" scripts/sync_version.py

# ── 5. 校验（顺便兜住 apkUrl / 产物缺失这类漂移）───────────────────
echo
echo "▶ 校验版本号一致性…"
"$PY" scripts/sync_version.py --check

# ── 6. 签名自检（尽力而为，找不到工具就跳过）───────────────────────
echo
echo "▶ 签名自检…"
APK_PATH="app/release/$APK_NAME"

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$SDK_DIR" ] && [ -f local.properties ]; then
  # 本机通常没设 ANDROID_HOME，从 local.properties 兜底读 sdk.dir。
  # 值是 Java properties 转义过的（形如 C\:\\Users\\...），要反转义成 C:/Users/...
  SDK_DIR="$("$PY" -c 'import re, pathlib
text = pathlib.Path("local.properties").read_text(encoding="utf-8")
m = re.search(r"^sdk\.dir=(.*)$", text, re.M)
print(m.group(1).replace("\\\\", "/").replace("\\:", ":") if m else "")' 2>/dev/null || true)"
fi

APKSIGNER=""
if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/build-tools" ]; then
  BT_DIR="$(ls -d "$SDK_DIR"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
  # Windows 的 build-tools 里只有 apksigner.bat，且 Git Bash 下 .bat 没有 exec 位，
  # 所以用 -f 判断存在，不能用 -x
  for candidate in "${BT_DIR}apksigner.bat" "${BT_DIR}apksigner"; do
    if [ -n "$BT_DIR" ] && [ -f "$candidate" ]; then APKSIGNER="$candidate"; break; fi
  done
fi
[ -n "$APKSIGNER" ] || APKSIGNER="$(command -v apksigner || true)"

if [ -z "$APKSIGNER" ]; then
  echo "[跳过] 未找到 apksigner。可设 ANDROID_HOME 指向 Android SDK 后重试。"
  echo "        务必确认签名与已发布版本一致，否则老用户只能卸载重装。"
else
  # apksigner 失败时不要中断整个发版流程（此时 APK 已构建完、版本号已同步）
  set +e
  SIGN_OUT="$("$APKSIGNER" verify --print-certs "$APK_PATH" 2>&1)"
  SIGN_RC=$?
  set -e
  if [ "$SIGN_RC" -eq 0 ]; then
    echo "$SIGN_OUT" | head -6
    echo "（签名者必须与已发布版本一致，否则老用户无法覆盖安装）"
  else
    echo "[警告] apksigner 执行失败（退出码 $SIGN_RC）："
    echo "$SIGN_OUT" | head -4
    echo "        apksigner 需要 JAVA_HOME 指向 JDK；这不是签名本身的问题。"
  fi
fi

# ── 7. 后续手工步骤 ───────────────────────────────────────────────
echo
echo "──────────────────────────────────────────────"
echo "接下来还需要手工做："
echo "  1. 编辑 version.json 的 changelog（脚本不动它）"
echo "  2. 提交：git add -A && git commit -m 'release: 发布 <版本号>'"
echo "  3. 推送：git push origin master   （GitHub Pages 约 1 分钟后生效）"
echo
echo "当前改动："
git status --short
