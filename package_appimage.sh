#!/bin/bash
set -euo pipefail

# ==============================================================================
# Script de création de l'AppImage universelle pour OmeRyth
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================================="
echo "       Génération de l'AppImage Linux pour OmeRyth    "
echo "======================================================="
echo ""

# 1. Vérifier / lancer la compilation
if [ ! -f "OmeRyth.jar" ]; then
    echo "[1/5] Compilation d'OmeRyth et génération de l'archive JAR..."
    ./build.sh
else
    echo "[1/5] Archive OmeRyth.jar existante validée."
fi

# 2. Préparation du répertoire AppDir
echo "[2/5] Préparation de la structure AppDir..."
APPDIR="$SCRIPT_DIR/build/AppDir"
rm -rf "$APPDIR"
mkdir -p "$APPDIR/usr/bin"
mkdir -p "$APPDIR/usr/lib"
mkdir -p "$APPDIR/usr/share/applications"
mkdir -p "$APPDIR/usr/share/icons/hicolor/256x256/apps"

# 3. Embarquer le Java Runtime Environment (JRE 21 minimal via jlink)
echo "[3/5] Génération du JRE 21 autonome portable (jlink)..."
if [ -n "${JAVA_HOME:-}" ] && [ -d "$JAVA_HOME/jmods" ]; then
    JAVA_HOME_DETECTED="$JAVA_HOME"
else
    JAVA_BIN="$(which java)"
    JAVA_REAL="$(readlink -f "$JAVA_BIN")"
    JAVA_HOME_DETECTED="$(dirname "$(dirname "$JAVA_REAL")")"
    if [ ! -d "$JAVA_HOME_DETECTED/jmods" ] && [ -d "/home/bryan/.sdkman/candidates/java/21-tem/jmods" ]; then
        JAVA_HOME_DETECTED="/home/bryan/.sdkman/candidates/java/21-tem"
    fi
fi

JLINK_CMD="$JAVA_HOME_DETECTED/bin/jlink"
if [ ! -x "$JLINK_CMD" ]; then
    JLINK_CMD="$(which jlink)"
fi

"$JLINK_CMD" \
    --module-path "$JAVA_HOME_DETECTED/jmods" \
    --add-modules java.base,java.desktop,java.logging,java.prefs,java.management,java.naming,jdk.unsupported \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --output "$APPDIR/usr/jre"

# Copie de l'archive principale OmeRyth.jar et des ressources
cp OmeRyth.jar "$APPDIR/usr/share/OmeRyth.jar"

# Copie des composants IA légers (scripts)
if [ -d "whisperx_engine" ]; then
    mkdir -p "$APPDIR/usr/share/whisperx_engine"
    cp -r whisperx_engine/*.py "$APPDIR/usr/share/whisperx_engine/" 2>/dev/null || true
fi

# Copie des icônes
if [ -f "src/images/logo.png" ]; then
    cp src/images/logo.png "$APPDIR/omeryth.png"
    cp src/images/logo.png "$APPDIR/usr/share/icons/hicolor/256x256/apps/omeryth.png"
fi

# Création du fichier Desktop pour Linux
cat << 'EOF' > "$APPDIR/omeryth.desktop"
[Desktop Entry]
Type=Application
Name=OmeRyth
GenericName=Bande Rythmo & Doublage Studio
Comment=Logiciel professionnel de bande rythmo, transcription IA et synchronisation labiale
Exec=omeryth %F
Icon=omeryth
Terminal=false
Categories=AudioVideo;AudioVideoEditing;Audio;Video;Java;
MimeType=application/x-omeryth;
StartupWMClass=app-Launcher
EOF
cp "$APPDIR/omeryth.desktop" "$APPDIR/usr/share/applications/omeryth.desktop"

# Création du lanceur AppRun
cat << 'EOF' > "$APPDIR/AppRun"
#!/bin/bash
set -euo pipefail

HERE="$(dirname "$(readlink -f "${0}")")"
export PATH="$HERE/usr/jre/bin:$PATH"

# Configuration des bibliothèques LibVLC et natives
LINUX_VLC_DIRS="/usr/lib/x86_64-linux-gnu:/usr/lib64:/usr/lib:/usr/local/lib"
if [ -n "${LD_LIBRARY_PATH:-}" ]; then
    export LD_LIBRARY_PATH="$HERE/usr/lib:$LINUX_VLC_DIRS:$LD_LIBRARY_PATH"
else
    export LD_LIBRARY_PATH="$HERE/usr/lib:$LINUX_VLC_DIRS"
fi

# Support de l'environnement virtuel Python local si présent à côté de l'AppImage
RUN_DIR="$(pwd)"
if [ -d "$RUN_DIR/.venv/bin" ]; then
    export PATH="$RUN_DIR/.venv/bin:$PATH"
fi

# Lancement de l'application via le JRE embarqué
exec "$HERE/usr/jre/bin/java" \
    -Dfile.encoding=UTF-8 \
    -jar "$HERE/usr/share/OmeRyth.jar" "$@"
EOF
chmod +x "$APPDIR/AppRun"

# 4. Téléchargement / Préparation de l'outil appimagetool
echo "[4/5] Préparation de l'outil appimagetool..."
APPIMAGETOOL="/tmp/appimagetool"
if [ ! -x "$APPIMAGETOOL" ]; then
    curl -sL "https://github.com/AppImage/appimagetool/releases/download/continuous/appimagetool-x86_64.AppImage" -o "$APPIMAGETOOL"
    chmod +x "$APPIMAGETOOL"
fi

# 5. Construction du paquet AppImage
echo "[5/5] Compression et assemblage de OmeRyth.AppImage..."
mkdir -p dist
OUTPUT_APPIMAGE="$SCRIPT_DIR/dist/OmeRyth-x86_64.AppImage"
rm -f "$OUTPUT_APPIMAGE"

# Exécution avec support fallback si FUSE n'est pas présent
ARCH=x86_64 "$APPIMAGETOOL" --appimage-extract-and-run "$APPDIR" "$OUTPUT_APPIMAGE"

# Créer également un lien symbolique direct à la racine
ln -sf "dist/OmeRyth-x86_64.AppImage" "OmeRyth.AppImage"

echo ""
echo "======================================================="
echo " [SUCCÈS] AppImage autonome créée avec succès !"
echo " - Fichier principal : ./dist/OmeRyth-x86_64.AppImage"
echo " - Raccourci racine  : ./OmeRyth.AppImage"
echo "======================================================="
exit 0
