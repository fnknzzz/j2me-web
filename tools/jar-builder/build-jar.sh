#!/bin/bash
#
# build-jar.sh — Mac 本地 J2ME JAR 构建脚本（无需 WTK）
#
# 用法:
#   bash build-jar.sh --name MyApp --src ./MyApp.java
#   bash build-jar.sh --name MyApp --main com.example.MyMIDlet --src ./src/ --resources ./res/ --output ./out/
#

set -e

# ============== 默认值 ==============
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RUNTIME_JAR="${REPO_ROOT}/crates/j2me-core/j2me-rt/classes.jar"
OUTPUT_DIR="."
NAME=""
MAIN_CLASS=""
SRC_PATH=""
RESOURCES_PATH=""

# ============== 参数解析 ==============
while [[ $# -gt 0 ]]; do
  case $1 in
    --name)    NAME="$2"; shift 2 ;;
    --main)    MAIN_CLASS="$2"; shift 2 ;;
    --src)     SRC_PATH="$2"; shift 2 ;;
    --resources) RESOURCES_PATH="$2"; shift 2 ;;
    --output)  OUTPUT_DIR="$2"; shift 2 ;;
    --runtime) RUNTIME_JAR="$2"; shift 2 ;;
    -h|--help)
      echo "用法: build-jar.sh --name <名称> --src <源码路径> [选项]"
      echo ""
      echo "选项:"
      echo "  --name        MIDlet 名称（必需）"
      echo "  --main        完整主类名（如 com.example.MyMIDlet）"
      echo "  --src         Java 源码文件或目录（必需）"
      echo "  --resources   资源目录（可选）"
      echo "  --output      输出目录（默认当前目录）"
      echo "  --runtime     J2ME runtime JAR（默认 crates/j2me-core/j2me-rt/classes.jar）"
      exit 0
      ;;
    *) echo "未知参数: $1"; exit 1 ;;
  esac
done

# ============== 参数校验 ==============
if [ -z "$NAME" ]; then
  echo "错误: 必须指定 --name"
  exit 1
fi

if [ -z "$SRC_PATH" ]; then
  echo "错误: 必须指定 --src"
  exit 1
fi

if [ ! -e "$SRC_PATH" ]; then
  echo "错误: 源码路径不存在: $SRC_PATH"
  exit 1
fi

# ============== 检查 JDK ==============
if [ -n "$JAVA_HOME" ]; then
  JAVAC="${JAVA_HOME}/bin/javac"
  JAR_CMD="${JAVA_HOME}/bin/jar"
else
  JAVAC="javac"
  JAR_CMD="jar"
fi

# 验证 javac 可用
if ! command -v "$JAVAC" &>/dev/null; then
  echo "错误: 找不到 javac。请安装 JDK 8 或设置 JAVA_HOME。"
  exit 1
fi

# ============== J2ME runtime ==============
if [ ! -f "$RUNTIME_JAR" ]; then
  echo "错误: 未找到 J2ME runtime JAR: $RUNTIME_JAR"
  echo "请先运行: pnpm --dir crates/j2me-core build:j2me-rt:prod"
  exit 1
fi

# ============== 创建临时目录 ==============
BUILD_DIR=$(mktemp -d)
CLASSES_DIR="${BUILD_DIR}/classes"
mkdir -p "$CLASSES_DIR"

cleanup() {
  rm -rf "$BUILD_DIR"
}
trap cleanup EXIT

# ============== 收集源文件 ==============
if [ -f "$SRC_PATH" ]; then
  # 单文件
  JAVA_FILES="$SRC_PATH"
else
  # 目录：递归查找所有 .java 文件
  JAVA_FILES=$(find "$SRC_PATH" -name "*.java" -type f)
fi

if [ -z "$JAVA_FILES" ]; then
  echo "错误: 未找到任何 .java 文件"
  exit 1
fi

echo "==> 编译 Java 源码..."
"$JAVAC" -encoding UTF-8 \
  -bootclasspath "$RUNTIME_JAR" \
  -source 1.3 -target 1.3 \
  -d "$CLASSES_DIR" \
  $JAVA_FILES

echo "==> 编译成功"

# ============== 复制资源文件 ==============
if [ -n "$RESOURCES_PATH" ] && [ -d "$RESOURCES_PATH" ]; then
  echo "==> 复制资源文件..."
  cp -r "${RESOURCES_PATH}/"* "$CLASSES_DIR/" 2>/dev/null || true
fi

# ============== 推断主类名 ==============
if [ -z "$MAIN_CLASS" ]; then
  if [ -f "$SRC_PATH" ]; then
    # 从单文件推断：检查 package 声明和类名
    PKG=$(grep -m1 "^package " "$SRC_PATH" | sed 's/package //;s/;//;s/[[:space:]]//g')
    CLASS_NAME=$(basename "$SRC_PATH" .java)
    if [ -n "$PKG" ]; then
      MAIN_CLASS="${PKG}.${CLASS_NAME}"
    else
      MAIN_CLASS="$CLASS_NAME"
    fi
  else
    echo "错误: 多文件项目必须指定 --main 参数"
    exit 1
  fi
fi

echo "==> 主类: $MAIN_CLASS"

# ============== 生成 MANIFEST.MF ==============
MANIFEST="${BUILD_DIR}/MANIFEST.MF"
printf 'Manifest-Version: 1.0\n' > "$MANIFEST"
printf 'MIDlet-1: %s, , %s\n' "$NAME" "$MAIN_CLASS" >> "$MANIFEST"
printf 'MIDlet-Name: %s\n' "$NAME" >> "$MANIFEST"
printf 'MIDlet-Vendor: j2me-web\n' >> "$MANIFEST"
printf 'MIDlet-Version: 1.0.0\n' >> "$MANIFEST"
printf 'MicroEdition-Configuration: CLDC-1.1\n' >> "$MANIFEST"
printf 'MicroEdition-Profile: MIDP-2.0\n' >> "$MANIFEST"
printf '\n' >> "$MANIFEST"

# ============== 打包 JAR ==============
mkdir -p "$OUTPUT_DIR"
JAR_PATH="${OUTPUT_DIR}/${NAME}.jar"

echo "==> 打包 JAR..."
"$JAR_CMD" cmf "$MANIFEST" "$JAR_PATH" -C "$CLASSES_DIR" .
echo "==> 生成: $JAR_PATH"

# ============== 生成 JAD ==============
JAR_SIZE=$(stat -f%z "$JAR_PATH" 2>/dev/null || stat -c%s "$JAR_PATH" 2>/dev/null)
JAD_PATH="${OUTPUT_DIR}/${NAME}.jad"

cat > "$JAD_PATH" << EOF
MIDlet-Name: ${NAME}
MIDlet-Version: 1.0.0
MIDlet-Vendor: j2me-web
MIDlet-1: ${NAME}, , ${MAIN_CLASS}
MIDlet-Jar-URL: ${NAME}.jar
MIDlet-Jar-Size: ${JAR_SIZE}
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
EOF

echo "==> 生成: $JAD_PATH"
echo ""
echo "构建完成！"
echo "  JAR: $JAR_PATH (${JAR_SIZE} bytes)"
echo "  JAD: $JAD_PATH"
