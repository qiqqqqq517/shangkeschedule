@echo off
setlocal
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto java_ready
if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" (
    set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
    goto java_ready
)
echo JDK 21 is required. Set JAVA_HOME or install Android Studio.
exit /b 1

:java_ready
cd /d "%~dp0"
call gradlew.bat :androidApp:installDebug %*
exit /b %ERRORLEVEL%
