@echo off
SET "JAR_NAME=my_app.jar"

IF "%1"=="start" GOTO START_APP
IF "%1"=="stop" GOTO STOP_APP
GOTO USAGE

:START_APP
echo Starting application in background...
:: javaw runs without a console window; start /B keeps it in the background
start /B javaw -jar "%JAR_NAME%" > app.log 2>&1
echo Application launched.
GOTO END

:STOP_APP
echo Stopping application...
:: taskkill searches for the specific command-line string of your JAR
taskkill /F /FI "IMAGENAME eq javaw.exe" /FI "COMMANDLINE eq *%JAR_NAME%*" >nul 2>&1
:: Fallback to standard check if command-line filtering fails on older Windows versions
if %errorlevel% NEQ 0 (
    taskkill /F /FI "IMAGENAME eq javaw.exe"
)
echo Application stopped.
GOTO END

:USAGE
echo Usage: %~nx0 {start^|stop}
GOTO END

:END
