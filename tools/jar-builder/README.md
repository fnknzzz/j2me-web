# J2ME Build Tools

J2ME 应用编译打包工具集。支持两种构建方式：

1. **Mac 本地构建**（`build-jar.sh`）— 跳过 preverify，默认使用 jvm-rust 的 `classes.jar`
2. **Windows WTK 完整构建**（`build-jar.bat`）— 包含 preverify 预校验，产出标准 J2ME JAR

## Mac 本地构建（build-jar.sh）

流程：`javac → jar`（跳过 preverify，本项目模拟器不做字节码校验）。

### 环境要求

- **JDK 8**（`-source 1.3 -target 1.3` 仅 JDK 8 支持）

### 用法

```bash
bash tools/wtk-build-server/build-jar.sh \
  --name HelloWorld \
  --src path/to/HelloWorld.java \
  --output out/
```

### 参数

| 参数 | 必需 | 说明 |
|------|------|------|
| `--name` | 是 | MIDlet 名称，也用于输出文件名 |
| `--main` | 否 | 完整主类名（如 `com.example.MyMIDlet`），单文件时自动推断 |
| `--src` | 是 | Java 源码文件或目录 |
| `--resources` | 否 | 资源目录，内容打入 JAR 根目录 |
| `--output` | 否 | 输出目录，默认当前目录 |
| `--runtime` | 否 | J2ME runtime JAR，默认 `crates/j2me-core/j2me-rt/classes.jar` |

### 示例

```bash
# 单文件
bash tools/wtk-build-server/build-jar.sh \
  --name DemoMIDlet \
  --src path/to/DemoMIDlet.java

# 多文件 + 资源
bash tools/wtk-build-server/build-jar.sh \
  --name AudioPlayerSuite \
  --main com.com.example.AudioPlayerSuite \
  --src path/to/sources/ \
  --resources path/to/resources/ \
  --output out/
```

## Windows WTK 完整构建（build-jar.bat）

标准 J2ME 构建流程：`javac → preverify → jar`。包含字节码预校验，产出可在真实设备/WTK 模拟器上运行的标准 JAR。

### 环境要求

- Windows
- JDK 8（设置 `JAVA_HOME` 环境变量）
- WTK 2.5.2（设置 `WTK_HOME` 环境变量）

### 用法

```cmd
set WTK_HOME=C:\WTK2.5.2
set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202

build-jar.bat --name HelloWorld --src HelloWorld.java --output out\
```

### 参数

与 `build-jar.sh` 相同（`--name`、`--main`、`--src`、`--resources`、`--output`）；Windows WTK 使用其安装目录内置的 API JAR。

## 文件结构

```
tools/wtk-build-server/
├── build-jar.sh                   # Mac 本地构建脚本（无 preverify）
├── build-jar.bat                  # Windows WTK 构建脚本（含 preverify）
└── README.md                      # 本文档
```
