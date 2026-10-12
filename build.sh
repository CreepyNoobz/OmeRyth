#!/bin/bash
set -euo pipefail

# ==============================================================================
# Script de compilation Linux pour OmeRyth
# ==============================================================================

# Se positionner à la racine du dépôt
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "======================================================="
echo "          Compilation d'OmeRyth (Linux)               "
echo "======================================================="
echo ""

# 1. Vérification des outils nécessaires
if ! command -v javac >/dev/null 2>&1; then
    echo "[ERREUR] javac est introuvable. Veuillez installer un JDK 17+ (ex: openjdk-21-jdk)." >&2
    exit 1
fi

JAVA_VERSION_STR=$(javac -version 2>&1 | awk '{print $2}' | tr -d '"')
echo "[1/4] Vérification du compilateur Java : javac $JAVA_VERSION_STR"

# 2. Vérification des bibliothèques JAR dans libs/
if [ ! -d "libs" ]; then
    echo "[ERREUR] Le dossier libs/ est introuvable." >&2
    exit 1
fi

for jar in "libs/jna-5.13.0.jar" "libs/jna-platform-5.13.0.jar" "libs/vlcj-4.7.1.jar" "libs/vlcj-natives-4.7.0.jar"; do
    if [ ! -f "$jar" ]; then
        echo "[ERREUR] Bibliothèque manquante : $jar" >&2
        exit 1
    fi
    # Vérifier que ce n'est pas un pointeur Git LFS non extrait
    if file "$jar" | grep -q "ASCII text"; then
        echo "[ERREUR] $jar semble être un pointeur Git LFS non téléchargé." >&2
        echo "Exécutez 'git lfs pull -I \"libs/*\"' pour résoudre les binaires." >&2
        exit 1
    fi
done
echo "      Bibliothèques JAR validées."

# 3. Préparation du dossier de build
echo "[2/4] Préparation du répertoire de destination bin/..."
rm -rf bin
mkdir -p bin

# 4. Compilation des sources Java
echo "[3/4] Compilation des sources Java..."
SRC_LIST_FILE=".src_files_linux.txt"
find src/app -name "*.java" > "$SRC_LIST_FILE"

if [ ! -s "$SRC_LIST_FILE" ]; then
    echo "[ERREUR] Aucun fichier source Java trouvé dans src/app." >&2
    rm -f "$SRC_LIST_FILE"
    exit 1
fi

javac -cp "libs/*:src" -d bin -encoding UTF-8 @"$SRC_LIST_FILE"
rm -f "$SRC_LIST_FILE"

# 5. Copie des ressources statiques (images, dictionnaires)
echo "[4/5] Copie des ressources (images, dictionnaires)..."
if [ -d "src/images" ]; then
    mkdir -p bin/images
    cp -r src/images/* bin/images/
fi

if [ -d "src/dictionaries" ]; then
    mkdir -p bin/dictionaries
    cp -r src/dictionaries/* bin/dictionaries/
fi

# 6. Création de l'archive autonome fat JAR (OmeRyth.jar) et de l'exécutable natif Linux (omeryth)
echo "[5/5] Création de l'exécutable universel Linux (omeryth)..."

# Résolution de l'outil jar
JAR_CMD="jar"
if ! command -v "$JAR_CMD" >/dev/null 2>&1; then
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/jar" ]; then
        JAR_CMD="$JAVA_HOME/bin/jar"
    fi
fi

TMP_JAR_DIR="$(mktemp -d)"
# Copie des classes et ressources compilées
cp -r bin/* "$TMP_JAR_DIR/"

# Extraction des bibliothèques JAR pour créer un paquet autonome (fat JAR)
for jar_dep in libs/*.jar; do
    (cd "$TMP_JAR_DIR" && "$JAR_CMD" xf "$SCRIPT_DIR/$jar_dep")
done

# Nettoyage et création du MANIFEST
rm -rf "$TMP_JAR_DIR/META-INF"
mkdir -p "$TMP_JAR_DIR/META-INF"
cat << 'EOF' > "$TMP_JAR_DIR/META-INF/MANIFEST.MF"
Manifest-Version: 1.0
Main-Class: app.Launcher

EOF

# Création du JAR
"$JAR_CMD" cfm "OmeRyth.jar" "$TMP_JAR_DIR/META-INF/MANIFEST.MF" -C "$TMP_JAR_DIR" .
rm -rf "$TMP_JAR_DIR"

# Création de l'exécutable natif Linux universel (auto-exécutable direct d'un clic ou terminal)
cat << 'EOF' > omeryth
#!/bin/bash
# ==============================================================================
# OmeRyth - Exécutable Universel Linux
# ==============================================================================
set -euo pipefail

# Résolution automatique du chemin physique du programme
SOURCE_FILE="${BASH_SOURCE[0]}"
while [ -h "$SOURCE_FILE" ]; do
    DIR="$(cd -P "$(dirname "$SOURCE_FILE")" >/dev/null 2>&1 && pwd)"
    SOURCE_FILE="$(readlink "$SOURCE_FILE")"
    [[ $SOURCE_FILE != /* ]] && SOURCE_FILE="$DIR/$SOURCE_FILE"
done
APP_DIR="$(cd -P "$(dirname "$SOURCE_FILE")" >/dev/null 2>&1 && pwd)"

# Détection et priorité de l'environnement virtuel Python local (.venv)
if [ -d "$APP_DIR/.venv/bin" ]; then
    export PATH="$APP_DIR/.venv/bin:$PATH"
fi

# Configuration LibVLC standard pour toutes les distributions Linux
LINUX_VLC_DIRS="/usr/lib/x86_64-linux-gnu:/usr/lib64:/usr/lib:/usr/local/lib"
if [ -n "${LD_LIBRARY_PATH:-}" ]; then
    export LD_LIBRARY_PATH="$LINUX_VLC_DIRS:$LD_LIBRARY_PATH"
else
    export LD_LIBRARY_PATH="$LINUX_VLC_DIRS"
fi

# Résolution de la commande Java
JAVA_CMD="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
fi

# Démarrage direct du JAR intégré
exec "$JAVA_CMD" \
    -Dfile.encoding=UTF-8 \
    -jar "$0" "$@"
exit 0
EOF

# Injection du payload JAR dans le fichier binaire exécutable
cat OmeRyth.jar >> omeryth
chmod +x omeryth

# 7. Génération de l'AppImage autonome (embarquant le JRE)
if [ -x "./package_appimage.sh" ]; then
    ./package_appimage.sh
fi

echo ""
echo "======================================================="
echo " [SUCCÈS] Construction Linux terminée avec succès !"
echo " - AppImage autonome (toutes distributions) : ./OmeRyth.AppImage"
echo " - Binaire exécutable local                : ./omeryth"
echo " - Archive JAR                             : ./OmeRyth.jar"
echo "======================================================="
exit 0
