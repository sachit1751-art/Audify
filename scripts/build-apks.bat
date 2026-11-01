@echo off
rem Audify (Sachit Music) - double-click wrapper that builds every shippable APK
rem with the bundled Gradle wrapper, drops a fresh set into dist\ at the project
rem root, and pauses at the end so the results stay visible from Explorer.

rem The script lives in the project root, so work from its own directory.
pushd "%~dp0"

rem Use the bundled Gradle wrapper so the exact Gradle version in
rem gradle/wrapper/gradle-wrapper.properties is used, not whatever happens to be
rem on the machine's PATH.
if exist "gradlew.bat" (
  set "BUILD_CMD=gradlew.bat"
) else if exist "gradlew" (
  set "BUILD_CMD=gradlew"
) else (
  echo ERROR: Neither gradlew.bat nor gradlew found in the project root.
  echo Run this from a checkout that contains the Gradle wrapper, then re-run this file.
  pause
  exit /b 1
)

rem The three release builds that ship: the phone (split per ABI plus a
rem universal), the watch app, and the TV app. One pass so they all share a
rem single Gradle run. The phone build also embeds the watch APK in its assets
rem (see app\build.gradle.kts), which is why the watch module is built here too.
call %BUILD_CMD% :app:assembleProdRelease :wearos:assembleProdRelease :tv:assembleProdRelease
if errorlevel 1 goto failed

rem Fresh copies only: dist\ is wiped first, so it can never hold an APK left
rem over from an earlier run.
set "DIST=%CD%\dist"
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%DIST%"
if errorlevel 1 goto failed

set COPIED=0
for %%D in (
  "app\build\outputs\apk\prod\release"
  "wearos\build\outputs\apk\prod\release"
  "tv\build\outputs\apk\prod\release"
) do (
  if exist "%%D\" (
    for %%F in (%%D\*.apk) do (
      if exist "%%F" (
        copy /y "%%F" "%DIST%\" >nul
        if errorlevel 1 goto failed
        echo   %%~nxF
        set /a COPIED+=1
      )
    )
  )
)

if not "%COPIED%"=="0" goto ok
echo ERROR: the build succeeded but no APKs were found to copy.
goto failed

:ok
echo.
echo ============================================================
echo %COPIED% fresh APK(s) copied to:
echo   %DIST%
pause
exit /b 0

:failed
echo.
echo ============================================================
echo Build FAILED. See the errors above.
pause
exit /b 1
