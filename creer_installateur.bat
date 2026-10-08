@echo off
chcp 65001 >nul
cd /d "%~dp0"

echo =======================================================
echo     Création de l'Installateur Autonome OmeRyth
echo =======================================================
echo.

set "ISCC=%LOCALAPPDATA%\Programs\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
if not exist "%ISCC%" set "ISCC=C:\Program Files\Inno Setup 6\ISCC.exe"

if not exist "%ISCC%" (
    echo [ERREUR] Inno Setup Compiler (ISCC.exe) est introuvable.
    pause
    exit /b 1
)

echo [1/2] Compilation et préparation des sources...
call build.ps1
if %errorlevel% neq 0 (
    echo [ERREUR] Échec de la compilation d'OmeRyth.
    pause
    exit /b %errorlevel%
)

echo.
echo [2/2] Création de l'installateur Windows (Setup)...
if not exist dist mkdir dist
"%ISCC%" /Qp "installer.iss"
if %errorlevel% neq 0 (
    echo [ERREUR] Échec de la création de l'installateur.
    pause
    exit /b %errorlevel%
)

echo.
echo =======================================================
echo  [SUCCÈS] Installateur généré dans dist\OmeRyth_Setup_v1.2.exe
echo =======================================================
echo.
pause
