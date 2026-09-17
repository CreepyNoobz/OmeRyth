@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo =======================================================
echo    Compilation et Lancement d'OmeRyth
echo =======================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
if %errorlevel% neq 0 (
    echo.
    echo [ERREUR] Erreur de compilation ou de packaging.
    pause
    exit /b %errorlevel%
)

echo.
echo Lancement d'OmeRyth...
if exist "OmeRyth.exe" (
    start "" "OmeRyth.exe" %*
) else (
    start "" javaw -cp "bin;libs/*" app.Launcher %*
)
exit /b 0
