# OmeRyth

## 🚀 Version 1.3.5 — Édition Rythmo Révolutionnée, Presets d'Export, IA Précise & Optimisation Espace (~540 Mo)

OmeRyth est un logiciel professionnel de création et de synchronisation de **bandes rythmo pour le doublage, la post-synchronisation et le karaoké**.

La version 1.3.5 apporte une refonte majeure de l'ergonomie d'édition sur la bande rythmo, un système complet de préréglages d'export vidéo, une précision chirurgicale de la transcription vocale IA et une réduction drastique de **92,5 %** de la taille de l'application (passée de 7,2 Go à ~540 Mo) :

### ✨ Nouveautés majeures :
- 💾 **Empreinte Disque Optimisée (Passage de 7,2 Go à ~540 Mo)** : Téléchargement 13× plus rapide grâce au nettoyage des installateurs redondants et à l'architecture modulaire à la demande pour les modèles IA.
- ✍️ **Édition Directe dans les Trous (Gap Typing)** : Possibilité de cliquer et taper du texte directement au sein des espaces vides entre séparateurs. Le curseur d'édition se positionne intuitivement juste après le repère d'ouverture, sans aucun artefact visuel.
- 🛡️ **Gestion Non-Destructive des Rôles & Répliques** : La suppression d'un rôle ou de la première réplique d'un personnage isole strictement le texte concerné sans plus jamais effacer le reste de la bande.
- 💾 **Gestionnaire de Préréglages d'Export Complet (Presets)** : Sauvegarde personnalisée, renommage (`✏️ Renommer...`), suppression (`🗑️ Supprimer`) et chargement instantané de presets pour le mode **Bandeau Seul** et le mode **Montage Vidéo + Bande** avec persistance automatique (`montage_presets.properties`).
- 📐 **Atelier d'Export Plein Écran & Déroulement Fluide** : Fenêtre redimensionnée (`1180×840`) avec ascenseur vertical fluide (`JScrollPane`) affichant l'intégralité des réglages sans aucune coupure. Retrait de la barre superflue "Formats Directs".
- 🎯 **Transcription IA Whisper Sans Décalage & Reconnexion Française** : Élimination des hallucinations et fuites temporelles, alignement phonétique précis au mot et reconnexion automatique des apostrophes (`d'accord`, `aujourd'hui`, `l'arraché`, `c'est`).
- 🔤 **Rendu Typographique Sans Troncature** : Prise en charge des polices personnalisées et fantaisie sans coupure des lettres aux extrémités grâce au calcul exact des métriques de glyphes (`scaleX`).
- ⌨️ **Touches & Contrôles Améliorés** : Insertion instantanée du séparateur via le pavé numérique (**Numpad 4**), pause automatique de lecture lors de la navigation aux flèches (**← / →**) comme à la molette, et sensibilité naturelle lors du déplacement des séparateurs.
- 🎨 **Personnalisation Directe & Unifiée** : Toutes les options de couleurs, d'images de fond et de polices sont directement accessibles sous **Options > Personnalisation...** sans menu déroulant masqué.

---

## 📋 Journal des Mises à Jour (Update Log)

### 2026-10-09 — Version 1.3.5 : Optimisation Massive de l'Espace Disque (7,2 Go ➔ ~540 Mo)

Grâce à l'exclusion du cache Git pour les utilisateurs finaux, au nettoyage des installateurs redondants et à l'architecture de téléchargement des modèles IA à la demande, l'empreinte disque de l'application est réduite de **92,5 %** (divisée par plus de 13) !

#### 📊 Récapitulatif Comparatif : Avant vs Après

| Catégorie | Avant (v1.2) | Après (v1.3.5) | Gain d'espace | Évolution & Solution |
| :--- | :---: | :---: | :---: | :--- |
| **Historique Git (`.git/`)** | 2 655 Mo (2,59 Go) | **0 Mo** | **-2 655 Mo** | Exclu de la distribution finale (réservé au développement). |
| **Modèles IA (`whisper/`)** | 2 139 Mo (2,09 Go) | **0 Mo** *(à la demande)* | **-2 139 Mo** | Modèle Medium téléchargé uniquement si transcription souhaitée. |
| **Installateur compilé (`dist/`)** | 2 048 Mo (2,00 Go) | **0 Mo** | **-2 048 Mo** | Nettoyage des gros paquets redondants d'installation. |
| **Dépendances autonomes (`ffmpeg`, `jre`, `vlc`)**| 489 Mo | **489 Mo** | 0 Mo | Préservées pour garantir le fonctionnement sans installation. |
| **Cœur OmeRyth (`src`, `bin`, `jar`, `exe`...)** | 54 Mo | **54 Mo** | 0 Mo | Code, exécutables, assets et dictionnaires optimisés. |
| **TOTAL GÉNÉRAL** | **~7 385 Mo (7,21 Go)** | **~543 Mo (0,53 Go)** | **🎉 -6 842 Mo (-92,5 %)** | **Téléchargement 13× plus rapide pour l'utilisateur** |

---

#### 🔴 Tableau Détaillé AVANT (7,21 Go)

| Élément | Type | Taille (Mo / Go) | % du total | Description |
| :--- | :---: | :---: | :---: | :--- |
| `.git/` | Dossier | 2 655 Mo (2,59 Go) | 36,0 % | Historique Git complet (commits, blobs, branches). |
| `whisper/` | Dossier | 2 139 Mo (2,09 Go) | 29,0 % | Cache local des 4 modèles Faster-Whisper (Large, Medium, Small, Tiny). |
| `dist/` | Dossier | 2 048 Mo (2,00 Go) | 27,7 % | Installateur Inno Setup complet (`OmeRyth_Setup_v1.2.exe`). |
| `ffmpeg/` | Dossier | 208 Mo (0,21 Go) | 2,8 % | Moteur d'exportation vidéo (`ffmpeg.exe`, `ffprobe.exe`). |
| `jre/` | Dossier | 145 Mo (0,15 Go) | 2,0 % | Runtime Java 21 portable autonome. |
| `vlc/` | Dossier | 136 Mo (0,14 Go) | 1,8 % | Moteur vidéo LibVLC 64-bit (codecs et lecture fluide). |
| `bin/` | Dossier | 14,8 Mo | 0,2 % | Classes Java compilées (`.class`). |
| `web/` | Dossier | 13,2 Mo | 0,2 % | Ressources d'affichage web / documentation. |
| `src/` | Dossier | 6,4 Mo | 0,1 % | Code source Java et scripts Python. |
| `OmeRyth.exe` & `OmeRyth.jar` | Fichiers | 10,4 Mo | 0,1 % | Exécutable Windows et archive JAR de l'application. |
| Reste *(dictionnaire, libs, icons, configs)* | Fichiers | ~9,5 Mo | 0,1 % | Dépendances tierces, dictionnaire français, icônes. |
| **Total** | | **~7 210 Mo (7,21 Go)** | **100 %** | |

---

#### 🟢 Tableau Détaillé APRÈS (~543 Mo)

| Élément | Type | Taille (Mo) | % du total | Rôle dans l'application finale |
| :--- | :---: | :---: | :---: | :--- |
| `ffmpeg/` | Dossier | 207,8 Mo | 38,3 % | Moteur d'export vidéo MP4 et extraction audio. |
| `jre/` | Dossier | 144,5 Mo | 26,6 % | Java portable (lancement d'OmeRyth sans prérequis système). |
| `vlc/` | Dossier | 135,6 Mo | 25,0 % | Moteur de lecture vidéo 60 FPS LibVLC. |
| `bin/` | Dossier | 14,8 Mo | 2,7 % | Fichiers compilés nécessaires à l'exécution. |
| `web/` | Dossier | 13,2 Mo | 2,4 % | Interface et assets web embarqués. |
| `src/` | Dossier | 6,4 Mo | 1,2 % | Code source de référence. |
| `OmeRyth.exe` | Fichier | 5,2 Mo | 1,0 % | Exécutable Windows officiel. |
| `OmeRyth.jar` | Fichier | 5,1 Mo | 0,9 % | Archive Java exécutable principale. |
| `test_dict.txt` | Fichier | 3,9 Mo | 0,7 % | Dictionnaire orthographique français. |
| `libs/` | Dossier | 3,6 Mo | 0,7 % | Bibliothèques Java (VLCJ, JNA). |
| Reste *(scripts, icons, configs)* | Fichiers | ~2,8 Mo | 0,5 % | `whisperx_engine` (~0,2 Mo), icône, configuration. |
| **Total** | | **~542,8 Mo (0,53 Go)**| **100 %** | **Application légère, portable et autonome** |

---

### 2026-10-08 — Version 1.3.0 : Édition Rythmo Avancée, Presets d'Export, Stabilisation IA & Personnalisation

#### ✍️ Édition Rythmo & Ergonomie
- **Écriture directe dans les trous (Gap Typing)** : Possibilité de cliquer et taper du texte directement entre deux séparateurs avec calage précis du curseur.
- **Suppression non-destructive des phrases avec rôles** : La suppression d'un rôle ou de sa première phrase n'efface plus le reste de la bande.
- **Déplacement fluide des séparateurs** : Glissement précis et direction naturelle des lettres au maintien du clic gauche.
- **Raccourci Numpad 4** : Insertion réactive de séparateurs rythmiques via le pavé numérique.
- **Pause automatique à la navigation** : Utilisation des flèches directionnelles mettant immédiatement la lecture en pause pour éviter toute désynchronisation.
- **Rendu typographique intégral** : Prise en charge des polices personnalisées et cursives sans rognage ou troncature de caractères.

#### 🎬 Export Vidéo & Presets
- **Gestionnaire complet de presets d'export** : Sauvegarde, renommage et suppression de préréglages pour le Bandeau et pour le Montage Vidéo.
- **Interface déroulante fluide** : Fenêtre d'export agrandie avec ascenseur scrollable sans coupure des options.
- **Nettoyage UI** : Retrait de la barre "Formats Directs".

#### 🎙️ Transcription IA & Vocal
- **Suppression des hallucinations Whisper** : Élimination des décalages temporels et des répétitions en boucle.
- **Reconnexion intelligente des mots français** : Fusion des élisions et apostrophes (`d'`, `l'`, `c'`, `qu'`).

#### 🎨 Personnalisation
- **Menu unifié sous Options > Personnalisation** : Accès direct à toutes les couleurs, images de fond et polices sans volet déroulant masqué.

---

### 2026-09-14 — Version 1.2.0 : Export Rapide, Format Mobile, IA Demucs & Windows Integration
- ⚡ Rendu vidéo en streaming RAM direct sans disques I/O intermédiaires
- 📱 Boutons et préréglages 1080×1920 (9:16) dans l'atelier d'export et de montage
- 🎤 Intégration du worker Demucs IA (`htdemucs`) pour l'isolation vocale doublage/karaoké
- 🏷️ Service d'association Windows automatique (`FileAssociationService`), `logo.ico` multi-tailles (16px à 256px) et `associer_fichiers_rythmo.bat`
- 📂 Gestion de l'ouverture de projet en argument de ligne de commande (double-clic Windows Explorer)
- 🎨 Textes des boutons stylisés en noir pour un contraste maximal
- 📜 Import/Export DETX complet (Cappella) avec détection et conservation des signes
- 🌊 Waveform Audio Vocale (`Ctrl+W`)

---

### 2026-08-23 — Version 1.0.0 : Simplification UI/UX Complète

#### 🎨 Interface Utilisateur
- **Nouvelle Page d'Accueil (`HomePanel.java`)** : Page de bienvenue épurée avec boutons clairs ("✚ Nouveau Projet", "📂 Ouvrir Projet", "📚 Tutoriel").
- **Assistant de Création pas-à-pas (`ProjectWizard.java`)** : 3 étapes guidées (choix du média, nombre de bandes, sélection des rôles).
- **Tutoriel Interactif Amélioré (`EnhancedTutorialDialog.java`)** : 8 étapes progressives avec barre de progression.
- **Menu Simplifié (`SimplifiedMenuBarPanel.java`)** : Passage de 5 à 4 menus clairs (Fichier, Édition, Affichage, Aide).
- **Raccourcis Clavier Essentiels (`SimplifiedKeybinds.java`)** : 5 raccourcis clés faciles à mémoriser.
- **Barre d'Outils Visuelle (`EditorToolbar.java`)** : Accès direct aux boutons de contrôle avec tooltips.
- **Onboarding Automatique (`OnboardingManager.java`)** : Détection du premier lancement.

#### 📊 Résumé des Améliorations v1.0

| Métrique | Avant | Après | Amélioration |
| :--- | :---: | :---: | :---: |
| Options de menu visibles | 15+ | 8 | 73% ↓ |
| Raccourcis clavier | 15+ | 5 | 67% ↓ |
| Étapes création projet | Confuses | 3 guidées | 300% ↑ |
| Page d'accueil | ✗ | ✓ | Nouveau ! |
| Tutoriel | 5 pages | 8 pages | 60% ↑ |
| Présets de rôles | ✗ | 6 | Nouveau ! |
| Barre d'outils | ✗ | ✓ | Nouveau ! |
| Onboarding | Nul | Complet | Nouveau ! |

---

### 2026-08-23 — Version 0.5.0 : Fixes de Base
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
> | `whisper/` | ~1.4 Go | Modèle Medium de réseaux de neurones (téléchargé à la demande pour la transcription) |
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
← →             = Reculer / Avancer (0.5s - pause automatique en lecture)
R               = Retour au début
Numpad 4   = Ajouter séparateur
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
