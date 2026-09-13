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
> Pour respecter les limites de GitHub (fichiers plafonnés à 100 Mo) et garder un dépôt Git léger, les composants suivants ne sont pas versionnés dans le dépôt :
>
> | Dossier / Fichier | Taille approx. | Raison & Emplacement |
> |-------------------|----------------|----------------------|
> | `OmeRyth_Portable.zip` | ~208 Mo | Archive portable complète générée via `creer_zip_portable.bat` |
> | `whisper/` | ~2.7 Go | Cache et poids neuronaux IA (téléchargés automatiquement par Demucs / WhisperX à la première extraction) |
> | `ffmpeg/` | ~120 Mo | Binaires FFmpeg pour l'encodage vidéo |
> | `vlc/` | ~80 Mo | Bibliothèques libvlc et codecs |
> | `jre/` | ~150 Mo | Runtime Java 17 portable |
> | `temp/` & `scratch/` | Variable | Fichiers de travail et de rendu temporaires |

Une fois les dépendances nécessaires installées ou récupérées :
```
OmeRyth/
├── src/                      ← Code source Java (dans le repo Git)
├── whisperx_engine/          ← Workers Python IA Demucs / WhisperX (dans le repo Git)
├── logo.ico                  ← Icône Windows officielle du logo (dans le repo Git)
├── NativeDialog.exe          ← Helper natif dialogue Windows (dans le repo Git)
├── associer_fichiers_rythmo.bat ← Script d'association d'icône Windows (dans le repo Git)
├── creer_zip_portable.bat    ← Script de création de l'archive portable (dans le repo Git)
├── libs/                     ← Bibliothèques JAR (vlcj, etc.)
├── ffmpeg/                   ← Binaires ffmpeg.exe, ffprobe.exe
├── vlc/                      ← Dossier plugins et bibliothèques VLC
└── jre/                      ← Runtime Java
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
