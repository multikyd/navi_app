@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0restore-apks.ps1" %*
if errorlevel 1 (
  echo.
  echo Restore failed. Keep all package files together and check the error above.
  pause
  exit /b 1
)
echo.
echo APK files are in the dist folder.
pause
