@echo off
echo [1/3] Compilation...
if not exist bin mkdir bin
dir /s /b src\*.java > .src_files.txt
javac -cp "libs/*" -d bin -encoding UTF-8 @.src_files.txt
if %errorlevel% neq 0 (echo ERREUR DE COMPILATION & del .src_files.txt & pause & exit /b 1)
del .src_files.txt
if not exist bin\images mkdir bin\images
xcopy /s /y src\images\* bin\images\ >nul 2>&1

echo [2/3] Creation du JAR...
jar cfm OmeRyth.jar MANIFEST.MF -C bin .

echo [3/3] Creation du .exe...
"C:\Program Files (x86)\Launch4j\launch4jc.exe" launch4j-config.xml

echo Termine ! Lancement...
java -cp "bin;libs/*" app.MainFenetre 2>NUL