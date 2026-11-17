# Build script for OmeRyth
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host "          Compilation et Construction d'OmeRyth        " -ForegroundColor Cyan
Write-Host "=======================================================" -ForegroundColor Cyan
Write-Host ""

# 0. Arrêter toute instance existante pour libérer les fichiers JAR et bin
Get-Process -Name 'OmeRyth' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
Get-Process -Name 'javaw', 'java' -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like '*OmeRyth*' } | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 300

# 0.5. Restaurer les binaires locaux s'ils ont été remplacés par des pointeurs Git LFS (< 1 Ko)
$localInstall = Join-Path $env:LOCALAPPDATA 'OmeRyth'
if (Test-Path $localInstall) {
    $jnaJar = 'libs\jna-5.13.0.jar'
    if ((-not (Test-Path $jnaJar)) -or ((Get-Item $jnaJar).Length -lt 1024)) {
        Write-Host "[0/4] Restauration des librairies depuis $localInstall..." -ForegroundColor Magenta
        Copy-Item -Path (Join-Path $localInstall 'libs\*') -Destination 'libs\' -Force -ErrorAction SilentlyContinue
    }
    if (-not (Test-Path 'jre\bin\javaw.exe') -and (Test-Path (Join-Path $localInstall 'jre\bin\javaw.exe'))) {
        Write-Host "[0/4] Restauration du JRE embarqué depuis $localInstall..." -ForegroundColor Magenta
        Copy-Item -Path (Join-Path $localInstall 'jre\*') -Destination 'jre\' -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (((-not (Test-Path 'vlc\libvlc.dll')) -or ((Get-Item 'vlc\libvlc.dll').Length -lt 1024)) -and (Test-Path (Join-Path $localInstall 'vlc\libvlc.dll'))) {
        Write-Host "[0/4] Restauration de VLC embarqué depuis $localInstall..." -ForegroundColor Magenta
        Copy-Item -Path (Join-Path $localInstall 'vlc\*') -Destination 'vlc\' -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (((-not (Test-Path 'ffmpeg\ffprobe.exe')) -or ((Get-Item 'ffmpeg\ffprobe.exe').Length -lt 1024)) -and (Test-Path (Join-Path $localInstall 'ffmpeg\ffprobe.exe'))) {
        Write-Host "[0/4] Restauration de FFmpeg embarqué depuis $localInstall..." -ForegroundColor Magenta
        Copy-Item -Path (Join-Path $localInstall 'ffmpeg\*') -Destination 'ffmpeg\' -Recurse -Force -ErrorAction SilentlyContinue
    }
    if (((-not (Test-Path 'NativeDialog.exe')) -or ((Get-Item 'NativeDialog.exe').Length -lt 1024)) -and (Test-Path (Join-Path $localInstall 'NativeDialog.exe'))) {
        Copy-Item -Path (Join-Path $localInstall 'NativeDialog.exe') -Destination 'NativeDialog.exe' -Force -ErrorAction SilentlyContinue
    }
}

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

& javac --release 21 -cp "libs/*;src" -d "bin" -encoding UTF-8 "@$srcListFile"
if ($LASTEXITCODE -ne 0) {
    Remove-Item $srcListFile -Force -ErrorAction SilentlyContinue
    Write-Error "Erreur lors de la compilation Java."
    exit 1
}
Remove-Item $srcListFile -Force -ErrorAction SilentlyContinue

# Compilation du helper natif d'exploration de fichiers (NativeDialog.exe)
if (Test-Path 'src\native\NativeDialog.cs') {
    $csc = 'C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe'
    if (Test-Path $csc) {
        Write-Host "Compilation de NativeDialog.exe..." -ForegroundColor DarkGray
        & $csc /nologo /target:exe /platform:anycpu /out:NativeDialog.exe /lib:"C:\Windows\Microsoft.NET\Framework64\v4.0.30319\WPF" /r:PresentationFramework.dll /r:PresentationCore.dll /r:WindowsBase.dll /r:System.Windows.Forms.dll /r:System.Drawing.dll src\native\NativeDialog.cs
        if (Test-Path 'bin') {
            Copy-Item -Path 'NativeDialog.exe' -Destination 'bin\NativeDialog.exe' -Force -ErrorAction SilentlyContinue
        }
    }
}

# Resolve jar executable (often missing from Oracle javapath symlinks)
$jarCmd = (Get-Command jar -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source)
if (-not $jarCmd) {
    $candidates = @(
        'C:\Program Files\Java\jdk-23\bin\jar.exe',
        'C:\Program Files\Java\jdk-25.0.4.1\bin\jar.exe'
    ) + (Get-ChildItem 'C:\Program Files\Java' -Recurse -Filter 'jar.exe' -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    $jarCmd = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $jarCmd) {
    $jarCmd = 'jar'
}

# 3. Extract libraries into bin to create a fat JAR
Write-Host "[2/4] Intégration des librairies dans le paquet..." -ForegroundColor Yellow
Push-Location 'bin'
Get-ChildItem '..\libs\*.jar' | ForEach-Object {
    & $jarCmd xf $_.FullName
}
Pop-Location

# 4. Copy resource images and dictionaries
if (-not (Test-Path 'bin\images')) {
    New-Item -ItemType Directory -Path 'bin\images' | Out-Null
}
Copy-Item -Path 'src\images\*' -Destination 'bin\images\' -Recurse -Force

if (-not (Test-Path 'bin\dictionaries')) {
    New-Item -ItemType Directory -Path 'bin\dictionaries' | Out-Null
}
if (Test-Path 'src\dictionaries') {
    Copy-Item -Path 'src\dictionaries\*' -Destination 'bin\dictionaries\' -Recurse -Force
}

# 5. Create OmeRyth.jar
Write-Host "[3/4] Création du JAR exécutable (OmeRyth.jar)..." -ForegroundColor Yellow
if (-not (Test-Path 'manifest.txt')) {
    [System.IO.File]::WriteAllText((Join-Path $PWD 'manifest.txt'), "Main-Class: app.Launcher`r`n", (New-Object System.Text.UTF8Encoding($false)))
}
& $jarCmd cfm 'OmeRyth.jar' 'manifest.txt' -C 'bin' .
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
    <opt>-XX:+UseG1GC</opt>
    <opt>-Xms64m</opt>
    <opt>-Xmx1024m</opt>
    <opt>-Dfile.encoding=UTF-8</opt>
    <opt>-Dsun.awt.windows.useCommonItemDialog=true</opt>
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
