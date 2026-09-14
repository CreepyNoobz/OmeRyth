# OmeRyth

## 🚀 Version 1.2 — Export Vidéo Ultra-Rapide, Format Mobile, IA Demucs & Association Windows

La version 1.2 d'OmeRyth apporte des performances industrielles et une intégration complète au système d'exploitation :

### ✨ Nouveautés majeures :
- ⚡ **Export Vidéo Ultra-Rapide (Streaming Mémoire Direct)** : Le moteur vidéo n'écrit plus aucun fichier temporaire sur le disque. Les frames sont transmises en temps réel via un pipe RAM directement à FFmpeg avec accélération matérielle GPU (**NVIDIA NVENC, AMD AMF, Intel QSV**) ou CPU multi-cœurs. Un export de 8 minutes passe de ~30 minutes à quelques dizaines de secondes !
- 📱 **Format Mobile Direct (1080×1920 — 9:16)** : Un seul clic pour basculer en format vertical pour **TikTok, Instagram Reels et YouTube Shorts** avec ajustement automatique de la vidéo 16:9 au centre et de la bande rythmo en dessous.
- 🎤 **Séparation Vocale IA Demucs (Facebook Research)** : Suppression chirurgicale des dialogues et voix tout en préservant la musique, les bruitages et les ambiances acoustiques grâce au modèle officiel HTDemucs.
- 🏷️ **Association Officielle des Fichiers `.rythmo`** : Les fichiers `.rythmo` arborent désormais le logo officiel OmeRyth dans l'Explorateur Windows et s'ouvrent directement au double-clic !
- 📜 **Compatibilité Professionnelle DETX (Cappella)** : Import et export fidèles des fichiers de doublage cinéma/TV avec synchronisation labiale (lipsync FVR, MPB, voyelles ouvertes, neutres).
- 🎨 **Ergonomie & Lisibilité Accrue** : Textes des boutons écrits en noir sur fond contrasté, ouverture instantanée des fichiers via `NativeDialog.exe` (dialogue natif Windows 10/11 sans latence).
- 🌊 **Waveform Audio Vocale** : Visualisation en direct de l'onde sonore sous la bande rythmo (`Ctrl+W`).

---

## 📋 Journal des Mises à Jour (Update Log)

### 2026-09-14 — Version 1.2 : Export Rapide, Format Mobile, IA Demucs & Windows Integration
- ⚡ Rendu vidéo en streaming RAM direct sans disques I/O intermédiaires
- 📱 Boutons et préréglages 1080×1920 (9:16) dans l'atelier d'export et de montage
- 🎤 Intégration du worker Demucs IA (`htdemucs`) pour l'isolation vocale doublage/karaoké
- 🏷️ Service d'association Windows automatique (`FileAssociationService`), `logo.ico` multi-tailles (16px à 256px) et `associer_fichiers_rythmo.bat`
- 📂 Gestion de l'ouverture de projet en argument de ligne de commande (double-clic Windows Explorer)
- 🎨 Textes des boutons stylisés en noir pour un contraste maximal
- 📜 Import/Export DETX complet (Cappella) avec détection et conservation des signes

### 2026-08-23 — Version 1.0 : Simplification UI/UX Complète
- 🎨 Nouvelle page d'accueil avec boutons simples
- 🧙 Assistant de création de projet (Wizard) pas-à-pas
- 📚 Tutoriel interactif amélioré (8 étapes)
- 📋 Menu simplifié (Fichier, Édition, Affichage, Aide)
- ⌨️ Raccourcis clavier réduits à 5 essentiels
- 🛠️ Barre d'outils avec boutons visuels
- 🎭 Présets de rôles prédéfinis
- 👋 Onboarding automatique au premier lancement

### 2026-08-23 — Version 0.5 : Fix de bugs
- Création d'un fichier sans vidéo
- Résolution des désynchronisations de bandes
- Stabilisation du chronomètre
- Affichage vidéo fiable

---

## 📖 Description

OmeRyth est un logiciel professionnel de création et synchronisation de **bandes rythmo pour le doublage, la post-synchronisation et le karaoké**.

Synchronisez facilement le texte, les signes de synchronisation labiale et la vidéo pour un doublage précis et confortable des comédiens.

---

## 📦 Fichiers Volumineux & Dépendances Externes

> ⚠️ **Fichiers exclus du dépôt Git (> 100 Mo ou générés localement)**
>
> Pour respecter les quotas de GitHub (fichiers plafonnés à 100 Mo) et garder un dépôt Git propre et léger, les composants volumineux et binaires générés ne sont pas versionnés dans le dépôt :
>
> | Dossier / Fichier | Taille approx. | Description & Installation |
> |-------------------|----------------|----------------------------|
> | `python/` | ~800 Mo | Environnement Python portable avec PyTorch, Demucs et WhisperX |
> | `whisper/` | ~2.7 Go | Modèles de réseaux de neurones (téléchargés automatiquement à la première utilisation) |
> | `ffmpeg/ffmpeg.exe` | ~120 Mo | Moteur FFmpeg 64-bit pour l'encodage vidéo et le mixage audio |
> | `vlc/` | ~80 Mo | Bibliothèques natives libvlc et codecs vidéo |
> | `jre/` | ~150 Mo | Runtime Java portable (ou Java 17+ installé sur le système) |
> | `temp/` & `scratch/` | Variable | Fichiers de cache et de rendu temporaires |
> | `*.zip` | Variable | Archives et packages de distribution générés localement |

---

## 🛠️ Installation & Prérequis pour l'Exécution

Pour exécuter ou compiler OmeRyth depuis les sources Git :

### 1. Java 17 ou supérieur (JDK / JRE)
- Assurez-vous que Java 17+ (64 bits) est installé sur votre machine (`java -version`).

### 2. VLC Media Player (64-bit)
- OmeRyth utilise `libvlc` via VLCJ pour la lecture vidéo et le scrubbing audio fluide.
- Installez [VLC 64-bit](https://www.videolan.org/vlc/) sur votre système, ou déposez le dossier `vlc` (contenant `libvlc.dll`, `libvlccore.dll` et `plugins/`) à la racine du projet.

### 3. FFmpeg (`ffmpeg/ffmpeg.exe` et `ffmpeg/ffprobe.exe`)
- Requis pour l'export vidéo streaming, la détection audio et l'extraction de pistes.
- Téléchargez FFmpeg 64-bit et placez `ffmpeg.exe` et `ffprobe.exe` dans le sous-dossier `ffmpeg/` (ou ajoutez FFmpeg à votre variable d'environnement `PATH`).

### 4. Environnement Python & IA (Transcription WhisperX & Séparation Demucs)
Pour bénéficier de la transcription automatique et de l'isolation vocale par IA :
- Installez **Python 3.10 ou 3.11** (64-bit).
- Installez les paquets requis via pip :
  ```bash
  pip install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
  pip install soundfile demucs whisperx
  ```
- *Alternative automatique* : OmeRyth intègre un gestionnaire autonome accessible via **Outils > Configuration des dépendances IA** capable de télécharger et configurer les composants manquants.

### 5. Compilation & Lancement
```bash
# Compilation de tous les fichiers sources Java
Get-ChildItem -Recurse -Filter *.java -Path src | Select-Object -ExpandProperty FullName | Out-File -Encoding ascii .src_files.txt
javac -cp "libs/*;src" -d bin -encoding UTF-8 @.src_files.txt

# Lancement de l'application
java -cp "bin;libs/*" app.Launcher
```

---

## ⚡ Utilisation Rapide

### Raccourcis clavier essentiels
```
ESPACE          = Lecture / Arrêt
← →             = Reculer / Avancer (0.5s)
R               = Retour au début
M               = Ajouter séparateur
CTRL+Z / Y      = Annuler / Rétablir
CTRL+W          = Afficher / Masquer la Waveform
```
*(Personnalisables à tout moment dans le menu Paramètre > Touches)*

### Formats supportés
- **Projets OmeRyth** : `.rythmo`, `autosave.rythmo.json`
- **Doublage professionnel** : `.detx` (Cappella)
- **Vidéos & Audios** : MP4, MKV, MOV, AVI, MP3, WAV, FLAC, AAC

---

## 👥 Contributeurs

- **Kripy** — Développeur
- **Ometitz** — Owner et producteur

---

## 📄 Licence

OmeRyth — Tous droits réservés.
