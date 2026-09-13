@echo off
chcp 65001 >nul
echo =======================================================
echo    Lancement et Compilation d'OmeRyth
echo =======================================================
echo.

echo [1/3] Compilation des sources Java...
if not exist bin mkdir bin
dir /s /b src\*.java > .src_files.txt
javac -cp "libs/*;src" -d bin -encoding UTF-8 @.src_files.txt
if %errorlevel% neq 0 (
    echo [ERREUR] Erreur de compilation Java.
    del .src_files.txt 2>nul
    pause
    exit /b 1
)
del .src_files.txt 2>nul

if not exist bin\images mkdir bin\images
xcopy /s /y src\images\* bin\images\ >nul 2>&1

echo [2/3] Creation du JAR executable...
jar cfm OmeRyth.jar manifest.txt -C bin .

echo [3/3] Creation / Mise a jour d'OmeRyth.exe...
if exist "C:\Program Files (x86)\Launch4j\launch4jc.exe" (
    (
        echo ^<launch4jConfig^>
        echo   ^<dontWrapJar^>false^</dontWrapJar^>
        echo   ^<headerType^>gui^</headerType^>
        echo   ^<jar^>OmeRyth.jar^</jar^>
        echo   ^<outfile^>OmeRyth.exe^</outfile^>
        echo   ^<errTitle^>OmeRyth^</errTitle^>
        echo   ^<cmdLine^>^</cmdLine^>
        echo   ^<chdir^>.^</chdir^>
        echo   ^<priority^>normal^</priority^>
        echo   ^<downloadUrl^>http://java.com/download^</downloadUrl^>
        echo   ^<stayAlive^>false^</stayAlive^>
        echo   ^<restartOnCrash^>false^</restartOnCrash^>
        echo   ^<icon^>logo.ico^</icon^>
        echo   ^<jre^>
        echo     ^<path^>jre^</path^>
        echo     ^<bundledJre64Bit^>true^</bundledJre64Bit^>
        echo     ^<bundledJreAsFallback^>true^</bundledJreAsFallback^>
        echo     ^<minVersion^>1.8.0^</minVersion^>
        echo     ^<runtimeBits^>64/32^</runtimeBits^>
        echo   ^</jre^>
        echo ^</launch4jConfig^>
    ) > .l4j_tmp.xml
    "C:\Program Files (x86)\Launch4j\launch4jc.exe" .l4j_tmp.xml >nul 2>&1
    del .l4j_tmp.xml 2>nul
)

echo.
echo Lancement d'OmeRyth...
if exist "OmeRyth.exe" (
    start "" "OmeRyth.exe"
) else (
    java -cp "bin;libs/*" app.Launcher
)