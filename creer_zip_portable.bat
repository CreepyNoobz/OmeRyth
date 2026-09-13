@echo off
echo =======================================================
echo    Creation du ZIP Portable OmeRyth pour distribution
echo =======================================================
echo.

set OUTPUT_ZIP=OmeRyth_Portable.zip

if exist %OUTPUT_ZIP% del %OUTPUT_ZIP%

echo Compression des composants autonomes (EXE, JAR, JRE, VLC, FFmpeg, Scripts)...
tar.exe -a -c -f %OUTPUT_ZIP% OmeRyth.exe OmeRyth.jar logo.ico associer_fichiers_rythmo.bat jre vlc ffmpeg whisperx_engine customization.properties keybinds.properties appstate.properties

if %errorlevel% equ 0 (
    echo.
    echo [SUCCES] Le fichier portable "%OUTPUT_ZIP%" a ete cree avec succes !
    echo Vous pouvez envoyer ce fichier ZIP a n'importe qui sur Windows.
    echo Ils ont juste a extraire le ZIP et double-cliquer sur OmeRyth.exe !
) else (
    echo.
    echo [ERREUR] Echec lors de la creation du ZIP.
)

echo.
pause
