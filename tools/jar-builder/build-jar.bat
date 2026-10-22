@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM build-jar.bat — Windows WTK J2ME JAR 构建脚本（含 preverify）
REM
REM 用法:
REM   build-jar.bat --name MyApp --src MyApp.java
REM   build-jar.bat --name MyApp --main com.example.MyMIDlet --src src\ --resources res\ --output out\
REM ============================================================

set "NAME="
set "MAIN_CLASS="
set "SRC_PATH="
set "RESOURCES_PATH="
set "OUTPUT_DIR=."
set "SCRIPT_DIR=%~dp0"

REM ============== 参数解析 ==============
:parse_args
if "%~1"=="" goto :done_args
if "%~1"=="--name" (set "NAME=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--main" (set "MAIN_CLASS=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--src" (set "SRC_PATH=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--resources" (set "RESOURCES_PATH=%~2" & shift & shift & goto :parse_args)
if "%~1"=="--output" (set "OUTPUT_DIR=%~2" & shift & shift & goto :parse_args)
if "%~1"=="-h" goto :show_help
if "%~1"=="--help" goto :show_help
echo 未知参数: %~1
exit /b 1

:show_help
echo 用法: build-jar.bat --name ^<名称^> --src ^<源码路径^> [选项]
echo.
echo 选项:
echo   --name        MIDlet 名称（必需）
echo   --main        完整主类名（如 com.example.MyMIDlet）
echo   --src         Java 源码文件或目录（必需）
echo   --resources   资源目录（可选）
echo   --output      输出目录（默认当前目录）
echo.
echo 环境变量:
echo   WTK_HOME      WTK 2.5.2 安装路径（必需）
echo   JAVA_HOME     JDK 8 路径（必需）
exit /b 0

:done_args

REM ============== 参数校验 ==============
if "%NAME%"=="" (
  echo 错误: 必须指定 --name
  exit /b 1
)

if "%SRC_PATH%"=="" (
  echo 错误: 必须指定 --src
  exit /b 1
)

if not exist "%SRC_PATH%" (
  echo 错误: 源码路径不存在: %SRC_PATH%
  exit /b 1
)

REM ============== 检查环境 ==============
if "%WTK_HOME%"=="" (
  echo 错误: 未设置 WTK_HOME 环境变量
  echo 请设置: set WTK_HOME=C:\WTK2.5.2
  exit /b 1
)

if "%JAVA_HOME%"=="" (
  echo 错误: 未设置 JAVA_HOME 环境变量
  echo 请设置: set JAVA_HOME=C:\Program Files\Java\jdk1.8.0_202
  exit /b 1
)

set "JAVAC=%JAVA_HOME%\bin\javac.exe"
set "JAR_CMD=%JAVA_HOME%\bin\jar.exe"
set "PREVERIFY=%WTK_HOME%\bin\preverify.exe"
set "CLDCAPI=%WTK_HOME%\lib\cldcapi11.jar"
set "MIDPAPI=%WTK_HOME%\lib\midpapi20.jar"

if exist "%JAVAC%" goto :javac_ok
echo 错误: 找不到 javac: "%JAVAC%"
exit /b 1

:javac_ok
if exist "%PREVERIFY%" goto :preverify_ok
echo 错误: 找不到 preverify: "%PREVERIFY%"
exit /b 1

:preverify_ok

REM ============== 构建 bootclasspath ==============
set "BOOTCP=%CLDCAPI%;%MIDPAPI%"

REM ============== 创建临时目录 ==============
set "BUILD_DIR=%TEMP%\j2me-build-%RANDOM%"
set "CLASSES_DIR=%BUILD_DIR%\classes"
set "PREVERIFIED_DIR=%BUILD_DIR%\preverified"

mkdir "%CLASSES_DIR%" 2>nul
mkdir "%PREVERIFIED_DIR%" 2>nul

REM ============== 收集源文件 ==============
set "JAVA_FILES="

REM 检查是文件还是目录
if exist "%SRC_PATH%\*" (
  REM 是目录，递归查找 .java 文件
  for /r "%SRC_PATH%" %%f in (*.java) do (
    set "JAVA_FILES=!JAVA_FILES! "%%f""
  )
) else (
  REM 是单文件
  set "JAVA_FILES="%SRC_PATH%""
)

if "%JAVA_FILES%"=="" (
  echo 错误: 未找到任何 .java 文件
  goto :cleanup_fail
)

REM ============== 编译 ==============
echo ==^> 编译 Java 源码...
"%JAVAC%" -encoding UTF-8 -bootclasspath "%BOOTCP%" -source 1.3 -target 1.3 -d "%CLASSES_DIR%" %JAVA_FILES%
if errorlevel 1 (
  echo 错误: 编译失败
  goto :cleanup_fail
)
echo ==^> 编译成功

REM ============== 预校验 ==============
echo ==^> 预校验 (preverify)...
set "PREVERIFY_CP=%BOOTCP%;%CLASSES_DIR%"
"%PREVERIFY%" -classpath "%PREVERIFY_CP%" -d "%PREVERIFIED_DIR%" "%CLASSES_DIR%"
if errorlevel 1 (
  echo 错误: 预校验失败
  goto :cleanup_fail
)
echo ==^> 预校验成功

REM ============== 复制资源文件 ==============
if not "%RESOURCES_PATH%"=="" (
  if exist "%RESOURCES_PATH%" (
    echo ==^> 复制资源文件...
    xcopy /s /e /q /y "%RESOURCES_PATH%\*" "%PREVERIFIED_DIR%\" >nul 2>nul
  )
)

REM ============== 推断主类名 ==============
if "%MAIN_CLASS%"=="" (
  REM 单文件模式：从源码推断
  if not exist "%SRC_PATH%\*" (
    REM 提取 package 名
    set "PKG="
    for /f "tokens=2 delims= " %%a in ('findstr /r /c:"^package " "%SRC_PATH%"') do (
      set "PKG=%%a"
    )
    REM 去掉分号
    if defined PKG set "PKG=!PKG:;=!"
    REM 提取文件名作为类名
    for %%f in ("%SRC_PATH%") do set "CLASS_NAME=%%~nf"
    if defined PKG (
      set "MAIN_CLASS=!PKG!.!CLASS_NAME!"
    ) else (
      set "MAIN_CLASS=!CLASS_NAME!"
    )
  ) else (
    echo 错误: 多文件项目必须指定 --main 参数
    goto :cleanup_fail
  )
)

echo ==^> 主类: %MAIN_CLASS%

REM ============== 生成 MANIFEST.MF ==============
set "MANIFEST=%BUILD_DIR%\MANIFEST.MF"
(
  echo Manifest-Version: 1.0
  echo MIDlet-1: %NAME%, , %MAIN_CLASS%
  echo MIDlet-Name: %NAME%
  echo MIDlet-Vendor: j2me-web
  echo MIDlet-Version: 1.0.0
  echo MicroEdition-Configuration: CLDC-1.1
  echo MicroEdition-Profile: MIDP-2.0
  echo.
) > "%MANIFEST%"

REM ============== 打包 JAR ==============
if not exist "%OUTPUT_DIR%" mkdir "%OUTPUT_DIR%"
set "JAR_PATH=%OUTPUT_DIR%\%NAME%.jar"

echo ==^> 打包 JAR...
"%JAR_CMD%" cmf "%MANIFEST%" "%JAR_PATH%" -C "%PREVERIFIED_DIR%" .
if errorlevel 1 (
  echo 错误: JAR 打包失败
  goto :cleanup_fail
)
echo ==^> 生成: %JAR_PATH%

REM ============== 生成 JAD ==============
for %%f in ("%JAR_PATH%") do set "JAR_SIZE=%%~zf"
set "JAD_PATH=%OUTPUT_DIR%\%NAME%.jad"

(
  echo MIDlet-Name: %NAME%
  echo MIDlet-Version: 1.0.0
  echo MIDlet-Vendor: j2me-web
  echo MIDlet-1: %NAME%, , %MAIN_CLASS%
  echo MIDlet-Jar-URL: %NAME%.jar
  echo MIDlet-Jar-Size: %JAR_SIZE%
  echo MicroEdition-Configuration: CLDC-1.1
  echo MicroEdition-Profile: MIDP-2.0
) > "%JAD_PATH%"

echo ==^> 生成: %JAD_PATH%
echo.
echo 构建完成！
echo   JAR: %JAR_PATH% (%JAR_SIZE% bytes)
echo   JAD: %JAD_PATH%

REM ============== 清理 ==============
:cleanup
rmdir /s /q "%BUILD_DIR%" 2>nul
exit /b 0

:cleanup_fail
rmdir /s /q "%BUILD_DIR%" 2>nul
exit /b 1
