@echo off
title Medical System - Local Server
if exist "%~dp0.local\database.xml" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Start-Medical.ps1" -Mode mysql -Port 18082 -OpenBrowser
) else (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\Start-Medical.ps1" -Mode demo -Port 18081 -OpenBrowser
)
if errorlevel 1 (
    echo.
    echo Startup could not complete. See the message above.
    pause
    exit /b 1
)
exit /b 0
