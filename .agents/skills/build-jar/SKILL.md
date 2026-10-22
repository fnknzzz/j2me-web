---
name: build-jar
description: "在 Mac 本地将 Java 源码编译打包为 J2ME JAR/JAD 文件（无需 WTK/Windows）。当用户要求编译 J2ME 代码、构建 MIDlet、打包 JAR 时使用。"
---

# Build JAR — Mac 本地 J2ME 编译打包

## 使用场景

- 编译 J2ME MIDlet 源码为 JAR
- 生成 JAD 描述文件
- 构建视觉回归或本地开发用的 MIDlet

## 前置条件

- **JDK 8**（`javac -source 1.3 -target 1.3` 仅 JDK 8 支持）
- macOS 环境

## 原理

标准 J2ME 构建流程为 `javac → preverify → jar`，其中 `preverify` 是 WTK 专有工具（仅 Windows）。
由于本项目的 JS/Rust 模拟器**不做字节码预校验**，可安全跳过 preverify 步骤，直接 `javac → jar`。

## 指令

当用户要求编译 J2ME 代码或构建 JAR 时，执行以下流程：

### 1. 确认 JDK 8 可用

```bash
java -version 2>&1 | head -1
```

如果不是 JDK 8，检查是否存在 `/usr/libexec/java_home -v 1.8` 或通过 JAVA_HOME 环境变量指定。
如果系统无 JDK 8，提示用户安装（`brew install --cask temurin8`）。

### 2. 执行构建脚本

使用项目自带的构建脚本：

```bash
bash tools/jar-builder/build-jar.sh \
  --name "<MIDlet名称>" \
  --main "<完整主类名>" \
  --src "<源码目录或单个java文件>" \
  --resources "<资源目录（可选）>" \
  --output "<输出目录>"
```

**参数说明：**

| 参数 | 必需 | 说明 |
|------|------|------|
| `--name` | 是 | MIDlet 显示名称，也用于输出文件名 |
| `--main` | 否 | 完整主类名（如 `com.example.MyMIDlet`）。省略时从单文件源码自动推断 |
| `--src` | 是 | Java 源码路径。可以是单个 `.java` 文件或包含多个文件的目录 |
| `--resources` | 否 | 资源目录，内容会打入 JAR 根目录 |
| `--output` | 否 | 输出目录，默认为当前目录 |
| `--runtime` | 否 | J2ME runtime JAR，默认 `crates/j2me-core/j2me-rt/classes.jar` |

### 3. 构建产物

脚本成功后在输出目录生成：
- `<name>.jar` — 可运行的 MIDlet JAR
- `<name>.jad` — Java Application Descriptor

## 手动构建步骤（脚本内部逻辑）

如果脚本不可用或需要手动操作：

```bash
# 变量
RUNTIME_JAR="crates/j2me-core/j2me-rt/classes.jar"
BUILD_DIR=$(mktemp -d)

# 1. 编译
javac -encoding UTF-8 \
  -bootclasspath "${RUNTIME_JAR}" \
  -source 1.3 -target 1.3 \
  -d "${BUILD_DIR}/classes" \
  src/*.java

# 2. 复制资源（如有）
cp -r resources/* "${BUILD_DIR}/classes/" 2>/dev/null || true

# 3. 创建 MANIFEST.MF
cat > "${BUILD_DIR}/MANIFEST.MF" << 'EOF'
Manifest-Version: 1.0
MIDlet-1: MyApp, , com.example.MyMIDlet
MIDlet-Name: MyApp
MIDlet-Vendor: j2me-web
MIDlet-Version: 1.0.0
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0

EOF

# 4. 打包 JAR
jar cmf "${BUILD_DIR}/MANIFEST.MF" MyApp.jar -C "${BUILD_DIR}/classes" .

# 5. 生成 JAD
JAR_SIZE=$(stat -f%z MyApp.jar)
cat > MyApp.jad << EOF
MIDlet-Name: MyApp
MIDlet-Version: 1.0.0
MIDlet-Vendor: j2me-web
MIDlet-1: MyApp, , com.example.MyMIDlet
MIDlet-Jar-URL: MyApp.jar
MIDlet-Jar-Size: ${JAR_SIZE}
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0
EOF

# 6. 清理
rm -rf "${BUILD_DIR}"
```

## bootclasspath

统一使用 `crates/j2me-core/j2me-rt/classes.jar`。它与 Rust/WASM 实际加载的系统类保持一致；需要新增 API 时，应先将其实现加入 j2me-rt，再重新构建 runtime JAR，而不是为应用单独拼接 API JAR。

## 注意事项

- **必须使用 JDK 8**：JDK 9+ 移除了 `-source 1.3` 支持，JDK 11+ 移除了 `-bootclasspath`
- **MANIFEST.MF 末尾必须有空行**（JAR 规范要求）
- **包路径一致性**：`MIDlet-1` 中的类名必须与 Java 源码的 `package` 声明 + 类名完全匹配
- **无需 preverify**：本项目模拟器不做字节码校验，跳过此步骤不影响运行
- **路径分隔符**：macOS 用 `:`（不是 Windows 的 `;`）
