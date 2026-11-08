@echo off
setlocal
set DIR=%~dp0
set WRAPPER=%DIR%gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER%" (
  echo Downloading official Gradle wrapper JAR...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/gradle/gradle/v8.11.1/gradle/wrapper/gradle-wrapper.jar' -OutFile '%WRAPPER%'" 
  if errorlevel 1 exit /b 1
)
java -classpath "%WRAPPER%" org.gradle.wrapper.GradleWrapperMain %*
endlocal
