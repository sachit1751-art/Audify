@echo off
rem Audify (Sachit Music) — double-click wrapper for build-apks.sh
rem Runs the bash script with Git Bash (bundled with Git for Windows) and
rem pauses at the end so the results stay visible when launched from Explorer.

setlocal
cd /d "%~dp0.."

where bash >nul 2>nul
if errorlevel 1 (
  echo ERROR: bash was not found on PATH.
  echo Install Git for Windows ^(https://git-scm.com/download/win^) which provides bash,
  echo then re-run this file.
  pause
  exit /b 1
)

bash "scripts\build-apks.sh" %*

echo.
echo ============================================================
if errorlevel 1 (
  echo Build FAILED. See the errors above.
) else (
  echo Build succeeded. APKs are in the dist\ folder shown above.
)
pause
