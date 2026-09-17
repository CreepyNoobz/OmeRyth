# Build script for OmeRyth
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "          Compilation et Construction d'OmeRyth        " -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Clean / create bin
if (Test-Path 'bin') {
    Remove-Item 'bin' -Recurse -Force
}
New-Item -ItemType Directory -Path 'bin' | Out-Null

# 2. Compile Java sources
Write-Host "[1/4] Compilation des sources Java..." -ForegroundColor Yellow
$javaFiles = Get-ChildItem -Path 'src\app' -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
$srcListFile = '.src_files.txt'
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines($srcListFile, $javaFiles, $utf8NoBom)

& javac -cp "libs/*;src" -d "bin" -encoding UTF-8 "@$srcListFile"
if ($LASTEXITCODE -ne 0) {
    Remove-Item $srcListFile -Force -ErrorAction SilentlyContinue
    Write-Error "Erreur lors de la compilation Java."
    exit 1
}
Remove-Item $srcListFile -Force -ErrorAction SilentlyContinue

# 3. Extract libraries into bin to create a fat JAR
Write-Host "[2/4] Intégration des librairies dans le paquet..." -ForegroundColor Yellow
Push-Location 'bin'
Get-ChildItem '..\libs\*.jar' | ForEach-Object {
    & jar xf $_.FullName
}
Pop-Location

# 4. Copy resource images
if (-not (Test-Path 'bin\images')) {
    New-Item -ItemType Directory -Path 'bin\images' | Out-Null
}
Copy-Item -Path 'src\images\*' -Destination 'bin\images\' -Recurse -Force

# 5. Create OmeRyth.jar
Write-Host "[3/4] Création du JAR exécutable (OmeRyth.jar)..." -ForegroundColor Yellow
& jar cfm 'OmeRyth.jar' 'manifest.txt' -C 'bin' .
if ($LASTEXITCODE -ne 0) {
    Write-Error "Erreur lors de la création d'OmeRyth.jar."
    exit 1
}

# 6. Create OmeRyth.exe with Launch4j
Write-Host "[4/4] Création de l'exécutable OmeRyth.exe (Launch4j GUI)..." -ForegroundColor Yellow
Get-Process -Name 'OmeRyth' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue

$launch4jc = 'C:\Program Files (x86)\Launch4j\launch4jc.exe'
if (Test-Path $launch4jc) {
    $l4jConfig = @"
<launch4jConfig>
  <dontWrapJar>false</dontWrapJar>
  <headerType>gui</headerType>
  <jar>OmeRyth.jar</jar>
  <outfile>OmeRyth.exe</outfile>
  <errTitle>OmeRyth</errTitle>
  <cmdLine></cmdLine>
  <chdir>.</chdir>
  <priority>normal</priority>
  <downloadUrl>http://java.com/download</downloadUrl>
  <stayAlive>false</stayAlive>
  <restartOnCrash>false</restartOnCrash>
  <icon>logo.ico</icon>
  <jre>
    <path>jre</path>
    <bundledJre64Bit>true</bundledJre64Bit>
    <bundledJreAsFallback>false</bundledJreAsFallback>
    <minVersion>1.8.0</minVersion>
    <runtimeBits>64/32</runtimeBits>
  </jre>
</launch4jConfig>
"@
    Set-Content -Path '.l4j_tmp.xml' -Value $l4jConfig -Encoding UTF8
    & $launch4jc '.l4j_tmp.xml'
    Remove-Item '.l4j_tmp.xml' -Force -ErrorAction SilentlyContinue
    Write-Host "OmeRyth.exe généré avec succès !" -ForegroundColor Green
} else {
    Write-Warning "Launch4j introuvable dans $launch4jc"
}

Write-Host ""
Write-Host "Build terminé avec succès !" -ForegroundColor Green
