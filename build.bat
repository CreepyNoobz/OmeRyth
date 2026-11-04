@echo off
cd /d "%~dp0"
echo =======================================================
echo           Compilation et Construction d'OmeRyth        
echo =======================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ERREUR] La compilation a echoue avec le code %ERRORLEVEL%.
) else (
    echo.
    echo [SUCCES] Compilation terminee avec succes !
)
pause
