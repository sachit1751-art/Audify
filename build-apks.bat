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

rem If the build succeeded and --upload wasn't passed, offer to publish the
rem APKs to a GitHub release right away.
if errorlevel 1 goto done
echo %* | findstr /C:"--upload" >nul
if not errorlevel 1 goto done
echo.
set UPLOAD=
set /p UPLOAD=Upload these APKs to a GitHub release too? [y/N]: 
if /i "%UPLOAD%"=="y" bash "scripts\build-apks.sh" --upload --flavor foss --with-debug

:done
echo.
echo ============================================================
if errorlevel 1 (
  echo Build FAILED. See the errors above.
) else (
  echo Build succeeded. APKs are in the dist\ folder shown above.
)
pause
