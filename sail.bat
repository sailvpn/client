@echo off
setlocal enabledelayedexpansion

:: Configuration
set "JAR_NAME=app.jar"
set "APP_TITLE=my_java_app_title"
set "LOG_FILE=app.log"

:: Read command-line argument
set "COMMAND=%~1"

if "%COMMAND%"=="start" goto do_start
if "%COMMAND%"=="stop" goto do_stop
if "%COMMAND%"=="status" goto do_status

:usage
echo Usage: %~nx0 {start^|stop^|status}
exit /b 1

:do_start
    :: Check if the app is already running by searching for the window title
    tasklist /FI "WINDOWTITLE eq %APP_TITLE%" 2>NUL | find /I "javaw.exe" >NUL
    if %ERRORLEVEL% equ 0 (
        echo Application is already running.
        exit /b 0
    )

    echo Starting application...
    :: 'start' runs it in the background, '/B' hides the window, 'javaw' keeps it backgrounded
    start "%APP_TITLE%" /B javaw -jar "%JAR_NAME%" > "%LOG_FILE%" 2>&1
    echo Application started in background.
    exit /b 0

:do_stop
    :: Check if the app is actually running before trying to kill it
    tasklist /FI "WINDOWTITLE eq %APP_TITLE%" 2>NUL | find /I "javaw.exe" >NUL
    if %ERRORLEVEL% neq 0 (
        echo Application is not running.
        exit /b 0
    )

    echo Stopping application...
    :: Gracefully request termination first
    taskkill /FI "WINDOWTITLE eq %APP_TITLE%" >NUL 2>&1

    :: Wait up to 3 seconds for it to close
    timeout /t 3 /nobreak >nul

    :: Force kill (/F) if it is still stubborn and running
    tasklist /FI "WINDOWTITLE eq %APP_TITLE%" 2>NUL | find /I "javaw.exe" >NUL
    if %ERRORLEVEL% equ 0 (
        echo Application did not stop gracefully. Forcing shutdown...
        taskkill /F /FI "WINDOWTITLE eq %APP_TITLE%" >NUL 2>&1
    )

    echo Application stopped.
    exit /b 0

:do_status
    tasklist /FI "WINDOWTITLE eq %APP_TITLE%" 2>NUL | find /I "javaw.exe" >NUL
    if %ERRORLEVEL% equ 0 (
        echo Application is RUNNING.
    ) else (
        echo Application is STOPPED.
    )
    exit /b 0
