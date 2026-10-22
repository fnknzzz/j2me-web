@echo off
setlocal enabledelayedexpansion

REM ============================================================
REM wtk-run.bat — Windows WTK build + emulator launcher
REM
REM 用法:
REM   wtk-run.bat --name MyApp --src MyApp.java
REM   wtk-run.bat --name MyApp --main com.example.MyMIDlet --src src\ --resources res\ --output out\
REM   wtk-run.bat --name MyApp --src MyApp.java --device DefaultColorPhone
REM
REM 说明:
REM   先调用同目录 build-jar.bat 完成 javac -> preverify -> jar/jad，
REM   然后自动运行 WTK emulator 打开生成的 JAD。
REM ============================================================

set "NAME="
set "OUTPUT_DIR=."
REM WTK 2.5.2 common devices:
REM   DefaultColorPhone, DefaultGrayPhone, MediaControlSkin, QwertyDevice
set "DEVICE=QwertyDevice"
set "BUILD_ARGS="
set "SCRIPT_DIR=%~dp0"

REM ============== 参数解析 ==============
:parse_args
if "%~1"=="" goto :done_args
if "%~1"=="--name" (
  set "NAME=%~2"
  set "BUILD_ARGS=!BUILD_ARGS! --name ^"%~2^""
  shift
  shift
  goto :parse_args
)
if "%~1"=="--main" (
  set "BUILD_ARGS=!BUILD_ARGS! --main ^"%~2^""
  shift
  shift
  goto :parse_args
)
if "%~1"=="--src" (
  set "BUILD_ARGS=!BUILD_ARGS! --src ^"%~2^""
  shift
  shift
  goto :parse_args
)
if "%~1"=="--resources" (
  set "BUILD_ARGS=!BUILD_ARGS! --resources ^"%~2^""
  shift
  shift
  goto :parse_args
)
if "%~1"=="--output" (
  set "OUTPUT_DIR=%~2"
  set "BUILD_ARGS=!BUILD_ARGS! --output ^"%~2^""
  shift
  shift
  goto :parse_args
)
if "%~1"=="--device" (
  set "DEVICE=%~2"
  shift
  shift
  goto :parse_args
)
if "%~1"=="-h" goto :show_help
if "%~1"=="--help" goto :show_help
echo 未知参数: %~1
exit /b 1

:show_help
echo 用法: wtk-run.bat --name ^<名称^> --src ^<源码路径^> [选项]
echo.
echo 构建选项:
echo   --name        MIDlet 名称（必需）
echo   --main        完整主类名（如 com.example.MyMIDlet）
echo   --src         Java 源码文件或目录（必需）
echo   --resources   资源目录（可选）
echo   --output      JAR/JAD 输出目录（默认当前目录）
echo.
echo 运行选项:
echo   --device      WTK 设备名（默认 QwertyDevice；常见值 DefaultColorPhone、DefaultGrayPhone、MediaControlSkin、QwertyDevice）
echo.
echo 环境变量:
echo   WTK_HOME      WTK 2.5.2 安装路径（必需）
echo   JAVA_HOME     JDK 8 路径（必需，由 build-jar.bat 使用）
exit /b 0

:done_args

if "%NAME%"=="" (
  echo 错误: 必须指定 --name
  exit /b 1
)

if "%WTK_HOME%"=="" (
  echo 错误: 未设置 WTK_HOME 环境变量
  echo 请设置: set WTK_HOME=C:\WTK2.5.2
  exit /b 1
)

set "EMULATOR=%WTK_HOME%\bin\emulator.exe"
if not exist "%EMULATOR%" (
  echo 错误: 找不到 emulator: %EMULATOR%
  exit /b 1
)

REM ============== 构建 JAR/JAD ==============
call "%SCRIPT_DIR%build-jar.bat" %BUILD_ARGS%
if errorlevel 1 (
  echo 错误: 构建失败，未启动模拟器
  exit /b 1
)

for %%f in ("%OUTPUT_DIR%\%NAME%.jad") do set "JAD_PATH=%%~ff"
if not exist "%JAD_PATH%" (
  echo 错误: 找不到生成的 JAD: %JAD_PATH%
  exit /b 1
)

REM ============== 启动 WTK 模拟器 ==============
echo ==^> 启动 WTK emulator...
echo     JAD: %JAD_PATH%

echo     Device: %DEVICE%
"%EMULATOR%" -Xdevice:%DEVICE% -Xdescriptor:"%JAD_PATH%"

exit /b %ERRORLEVEL%
