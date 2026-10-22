@echo on
rem
rem This batch file builds and preverifies the code for the demos.
rem it then packages them in a JAR file appropriately.
rem 此版本为调试后的本机可执行版本
rem
if "%OS%" == "Windows_NT" setlocal
set DEMO=Demos
set LIB_DIR=%WTK_HOME%\lib
set CLDCAPI=%LIB_DIR%\cldcapi10.jar
set MIDPAPI=%LIB_DIR%\midpapi20.jar
set PREVERIFY=%WTK_HOME%\bin\preverify

set JAVA_FILES=
set JAVA_FILES=%JAVA_FILES% ..\src\example\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\auction\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\chooser\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\fonts\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\http\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\manyballs\*.java
set JAVA_FILES=%JAVA_FILES% ..\src\example\stock\*.java

set JAVAC="%JAVA_HOME%\bin\javac"
set JAR="%JAVA_HOME%\bin\jar"
echo JAVA_HOME value: %JAVA_HOME%\bin\javac
pause

if not "%JAVA_HOME%" == "" (
    @REM set JAVAC=%JAVA_HOME%\bin\javac
    @REM set JAR=%JAVA_HOME%\bin\jar
)

if not exist .\%DEMO%.jad (
  echo *** Run this batch file from its location directory only. ***
  goto end
)


echo *** Creating directories ***
if not exist ..\tmpclasses md ..\tmpclasses
if not exist ..\classes md ..\classes

echo *** Compiling source files ***
%JAVAC% -bootclasspath %CLDCAPI%;%MIDPAPI% -source 1.3 -target 1.3 -d ..\tmpclasses -classpath ..\tmpclasses %JAVA_FILES%

echo *** Preverifying class files ***

rem WARNING: When running under windows 9x the JAR may be incomplete
rem due to an error in Windows 98. Simply place a pause statement between
rem the preverify and JAR stages and wait 5 seconds before continuing
rem the build.

%PREVERIFY% -classpath %CLDCAPI%;%MIDPAPI%;..\tmpclasses -d ..\classes ..\tmpclasses

echo *** Jaring preverified class files ***
%JAR% cmf MANIFEST.MF %DEMO%.jar -C ..\classes .

if exist ..\res (
    echo *** Jaring resource files ***
    %JAR% uf %DEMO%.jar -C ..\res .
)

echo ***
echo *** Don't forget to update the JAR file size in the JAD file!!! ***
echo ***

:end
if "%OS%" == "Windows_NT" endlocal

rem do a "pause" always
pause
