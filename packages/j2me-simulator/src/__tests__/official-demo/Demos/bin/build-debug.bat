@echo off
rem 调试版本的构建脚本
rem 会显示详细的诊断信息

echo ========================================
echo J2ME Demo 构建脚本 - 调试模式
echo ========================================
echo.

rem 检查运行目录
echo [检查] 当前目录: %CD%
echo.

if "%OS%" == "Windows_NT" setlocal
set DEMO=demos

rem 检查 JAD 文件
echo [检查] 查找 %DEMO%.jad 文件...
if not exist .\%DEMO%.jad (
  echo [错误] 找不到 %DEMO%.jad 文件！
  echo [提示] 请确保在 bin\ 目录下运行此脚本
  echo [提示] 当前目录: %CD%
  pause
  goto end
)
echo [成功] 找到 %DEMO%.jad 文件
echo.

rem 设置路径
set LIB_DIR=..\..\..\lib
set CLDCAPI=%LIB_DIR%\cldcapi10.jar
set MIDPAPI=%LIB_DIR%\midpapi20.jar
set PREVERIFY=..\..\..\bin\preverify

rem 检查 WTK 库文件
echo [检查] WTK 库文件...
echo   CLDCAPI: %CLDCAPI%
if not exist "%CLDCAPI%" (
  echo [错误] 找不到 CLDC API: %CLDCAPI%
  pause
  goto end
)
echo [成功] 找到 CLDC API

echo   MIDPAPI: %MIDPAPI%
if not exist "%MIDPAPI%" (
  echo [错误] 找不到 MIDP API: %MIDPAPI%
  pause
  goto end
)
echo [成功] 找到 MIDP API
echo.

rem 检查 preverify 工具
echo [检查] Preverify 工具...
echo   PREVERIFY: %PREVERIFY%.exe
if not exist "%PREVERIFY%.exe" (
  echo [错误] 找不到 preverify 工具: %PREVERIFY%.exe
  pause
  goto end
)
echo [成功] 找到 preverify 工具
echo.

rem 设置 Java 工具
set JAVAC=javac
set JAR=jar

echo [检查] Java 环境...
echo   JAVA_HOME: %JAVA_HOME%

if not "%JAVA_HOME%" == "" (
    set JAVAC=%JAVA_HOME%\bin\javac
    set JAR=%JAVA_HOME%\bin\jar
    echo [信息] 使用 JAVA_HOME 中的工具
) else (
    echo [警告] JAVA_HOME 未设置，使用系统 PATH 中的工具
)

echo   JAVAC: %JAVAC%
echo   JAR: %JAR%

rem 测试 javac 是否可用
"%JAVAC%" -version >nul 2>&1
if errorlevel 1 (
  echo [错误] 无法运行 javac！
  echo [提示] 请检查 JAVA_HOME 设置或 PATH 环境变量
  "%JAVAC%" -version
  pause
  goto end
)
echo [成功] javac 可用
echo.

rem 设置源文件
set JAVA_FILES=
set JAVA_FILES=%JAVA_FILES% ..\src\example\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\auction\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\chooser\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\fonts\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\http\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\manyballs\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\stock\*.java

echo [检查] 源文件目录...
if not exist ..\src\example (
  echo [错误] 找不到源文件目录: ..\src\example
  pause
  goto end
)
echo [成功] 找到源文件目录
echo.

rem 创建目录
echo ========================================
echo 步骤 1: 创建目录
echo ========================================
if not exist ..\tmpclasses (
  echo [创建] ..\tmpclasses
  md ..\tmpclasses
) else (
  echo [存在] ..\tmpclasses
)

if not exist ..\classes (
  echo [创建] ..\classes
  md ..\classes
) else (
  echo [存在] ..\classes
)
echo.

rem 编译
echo ========================================
echo 步骤 2: 编译 Java 源码
echo ========================================
echo [命令] %JAVAC% -bootclasspath %CLDCAPI%;%MIDPAPI% -source 1.3 -target 1.3 -d ..\tmpclasses -classpath ..\tmpclasses %JAVA_FILES%
echo.
%JAVAC% -bootclasspath %CLDCAPI%;%MIDPAPI% -source 1.3 -target 1.3 -d ..\tmpclasses -classpath ..\tmpclasses %JAVA_FILES%

if errorlevel 1 (
  echo.
  echo [错误] 编译失败！
  echo [提示] 请检查上面的错误信息
  pause
  goto end
)
echo.
echo [成功] 编译完成
echo.

rem 预验证
echo ========================================
echo 步骤 3: 预验证字节码
echo ========================================
echo [命令] %PREVERIFY% -classpath %CLDCAPI%;%MIDPAPI%;..\tmpclasses -d ..\classes ..\tmpclasses
echo.
%PREVERIFY% -classpath %CLDCAPI%;%MIDPAPI%;..\tmpclasses -d ..\classes ..\tmpclasses

if errorlevel 1 (
  echo.
  echo [错误] 预验证失败！
  pause
  goto end
)
echo.
echo [成功] 预验证完成
echo.

rem 打包 JAR
echo ========================================
echo 步骤 4: 打包 JAR
echo ========================================
echo [命令] %JAR% cmf MANIFEST.MF %DEMO%.jar -C ..\classes .
echo.
%JAR% cmf MANIFEST.MF %DEMO%.jar -C ..\classes .

if errorlevel 1 (
  echo.
  echo [错误] 打包失败！
  pause
  goto end
)
echo.
echo [成功] JAR 打包完成
echo.

rem 打包资源文件
if exist ..\res (
    echo ========================================
    echo 步骤 5: 打包资源文件
    echo ========================================
    echo [命令] %JAR% uf %DEMO%.jar -C ..\res .
    echo.
    %JAR% uf %DEMO%.jar -C ..\res .
    
    if errorlevel 1 (
      echo.
      echo [错误] 资源打包失败！
      pause
      goto end
    )
    echo.
    echo [成功] 资源打包完成
    echo.
)

rem 检查 JAR 大小
echo ========================================
echo 构建完成！
echo ========================================
for %%F in ("%DEMO%.jar") do set JAR_SIZE=%%~zF
echo JAR 文件: %DEMO%.jar
echo JAR 大小: %JAR_SIZE% 字节
echo.
echo [重要] 请更新 %DEMO%.jad 文件中的 MIDlet-Jar-Size 为: %JAR_SIZE%
echo.

:end
if "%OS%" == "Windows_NT" endlocal
echo.
echo 按任意键退出...
pause >nul
