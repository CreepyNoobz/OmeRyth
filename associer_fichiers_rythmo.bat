@echo off
chcp 65001 >nul
echo =======================================================
echo    Association des fichiers .rythmo a OmeRyth
echo =======================================================
echo.

set "SCRIPT_DIR=%~dp0"
set "ICO_PATH=%SCRIPT_DIR%logo.ico"
set "EXE_PATH=%SCRIPT_DIR%OmeRyth.exe"

if not exist "%ICO_PATH%" (
    if exist "%SCRIPT_DIR%src\images\logo.ico" (
        copy /y "%SCRIPT_DIR%src\images\logo.ico" "%ICO_PATH%" >nul
    )
)

if not exist "%ICO_PATH%" (
    echo [ERREUR] Le fichier logo.ico est introuvable dans %SCRIPT_DIR%
    pause
    exit /b 1
)

echo 1. Association de l'extension .rythmo...
reg add "HKCU\Software\Classes\.rythmo" /ve /d "OmeRyth.Project" /f >nul
reg add "HKCU\Software\Classes\.rythmo" /v "Content Type" /d "application/x-omeryth" /f >nul

echo 2. Configuration du type OmeRyth.Project...
reg add "HKCU\Software\Classes\OmeRyth.Project" /ve /d "Projet Bande Rythmo OmeRyth" /f >nul

echo 3. Definition de l'icone du logo...
reg add "HKCU\Software\Classes\OmeRyth.Project\DefaultIcon" /ve /d "\"%ICO_PATH%\",0" /f >nul

echo 4. Definition de la commande d'ouverture...
if exist "%EXE_PATH%" (
    reg add "HKCU\Software\Classes\OmeRyth.Project\shell\open\command" /ve /d "\"%EXE_PATH%\" \"%%1\"" /f >nul
) else (
    reg add "HKCU\Software\Classes\OmeRyth.Project\shell\open\command" /ve /d "javaw.exe -jar \"%SCRIPT_DIR%OmeRyth.jar\" \"%%1\"" /f >nul
)

echo 5. Enregistrement dans les extensions utilisateur Windows...
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Explorer\FileExts\.rythmo\OpenWithProgids" /v "OmeRyth.Project" /t REG_NONE /f >nul

echo 6. Actualisation du cache d'icones de l'Explorateur Windows...
powershell -NoProfile -Command "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class S { [DllImport(\"shell32.dll\")] public static extern void SHChangeNotify(int w, int u, IntPtr d1, IntPtr d2); }'; [S]::SHChangeNotify(0x08000000, 0, [IntPtr]::Zero, [IntPtr]::Zero)" >nul 2>&1

echo.
echo [SUCCES] L'icone du logo OmeRyth est maintenant associee aux fichiers .rythmo !
echo Vos fichiers .rythmo s'affichent desormais avec le logo OmeRyth.
echo.
pause
