@echo off
setlocal
set JAVA_HOME=D:\Android\Android Studio\jbr
set ANDROID_HOME=D:\Android\Sdk
cd /d "%~dp0"
call gradlew.bat :androidApp:installDebug
