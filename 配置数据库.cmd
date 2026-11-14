@echo off
title Medical System - Local Database Setup
powershell.exe -NoProfile -STA -ExecutionPolicy Bypass -File "%~dp0scripts\Initialize-MedicalDatabase.ps1" -ConsoleInput
echo.
echo Setup finished. Return to Codex to verify the database connection.
pause
