#!/bin/bash
set -euo pipefail

# ==============================================================================
# Script de lancement natif Linux pour OmeRyth
# ==============================================================================

# Se positionner dans le répertoire du script quel que soit le dossier d'appel
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# 1. Vérification du runtime Java
if ! command -v java >/dev/null 2>&1; then
    echo "[ERREUR] La commande 'java' est introuvable." >&2
    echo "Installez un environnement Java 17+ (ex: openjdk-21-jre)." >&2
    exit 1
fi

# 2. Vérification de la compilation
if [ ! -d "bin" ] || [ ! -f "bin/app/Launcher.class" ]; then
    echo "[INFO] Le projet n'est pas encore compilé. Lancement automatique de build.sh..."
    if [ -x "./build.sh" ]; then
        ./build.sh
    else
        bash ./build.sh
    fi
fi

# 3. Vérification des bibliothèques JAR
CLASSPATH="bin:libs/*"
for jar in "libs/jna-5.13.0.jar" "libs/vlcj-4.7.1.jar"; do
    if [ ! -f "$jar" ]; then
        echo "[ERREUR] Dépendance Java manquante : $jar" >&2
        exit 1
    fi
done

# 4. Détection et activation de l'environnement virtuel Python (.venv) si présent
if [ -d ".venv" ] && [ -f ".venv/bin/python" ]; then
    VENV_BIN="$SCRIPT_DIR/.venv/bin"
    export PATH="$VENV_BIN:$PATH"
fi

# 5. Configuration des bibliothèques natives VLC sous Linux
# LibVLC sous Debian/Ubuntu se trouve généralement dans /usr/lib/x86_64-linux-gnu
LINUX_VLC_LIB_DIRS="/usr/lib/x86_64-linux-gnu:/usr/lib64:/usr/lib"
if [ -n "${LD_LIBRARY_PATH:-}" ]; then
    export LD_LIBRARY_PATH="$LINUX_VLC_LIB_DIRS:$LD_LIBRARY_PATH"
else
    export LD_LIBRARY_PATH="$LINUX_VLC_LIB_DIRS"
fi

# 6. Exécution de l'application via la classe principale avec transmission des arguments
exec java -cp "$CLASSPATH" \
    -Dfile.encoding=UTF-8 \
    app.Launcher "$@"
