@echo off
rem Booster CLI launcher for Windows (packages/cli/bin/run.cmd). See bin/boost for the lookup rules.
rem Paths are only expanded quoted or outside parenthesised blocks: cmd.exe expands %VAR% when it reads a whole
rem block, so the ')' of a path such as C:\Program Files (x86)\... would end the block early.
setlocal
set "CLI_DIR=%~dp0.."
set "JAR=%BOOSTER_CLI_JAR%"
if "%JAR%"=="" if exist "%CLI_DIR%\lib\booster-cli.jar" set "JAR=%CLI_DIR%\lib\booster-cli.jar"
if "%JAR%"=="" for %%f in ("%CLI_DIR%\target\booster-cli-*.jar") do (
  echo %%~nxf | findstr /b /c:"original-" >nul || set "JAR=%%f"
)
if "%JAR%"=="" echo boost: cannot find the Booster CLI jar. Build it with "mvn -B package" in "%CLI_DIR%", or set BOOSTER_CLI_JAR. 1>&2
if "%JAR%"=="" exit /b 1
set "JAVA=java"
if not "%JAVA_HOME%"=="" if exist "%JAVA_HOME%\bin\java.exe" set "JAVA=%JAVA_HOME%\bin\java.exe"
if not "%JAVA_HOME%"=="" if not exist "%JAVA_HOME%\bin\java.exe" echo boost: JAVA_HOME is set to "%JAVA_HOME%", which has no bin\java.exe; using java from the PATH. 1>&2
rem The jar's entry point runs on Java 8+ and stops with a clear message when this Java is older than the CLI's release
"%JAVA%" -XX:TieredStopAtLevel=1 -XX:+UseSerialGC %BOOST_JAVA_OPTS% -jar "%JAR%" %*
exit /b %ERRORLEVEL%
