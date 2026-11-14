@echo off
title Medical System - V002 Database Migration
powershell.exe -NoProfile -NoExit -ExecutionPolicy Bypass -File "%~dp0scripts\Update-MedicalSchema.ps1"
