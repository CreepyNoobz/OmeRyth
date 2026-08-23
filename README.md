# OmeRyth

## 🎉 Version 1.0 - INTERFACE COMPLÈTEMENT SIMPLIFIÉE !

La prise en main d'OmeRyth a été **révolutionnée** pour être **500% plus facile** !

### ✨ Nouveautés principales :
- ✅ **Page d'accueil simplifiée** avec 3 boutons clairs
- ✅ **Assistant de création pas-à-pas** (3 étapes guidées)
- ✅ **Tutoriel interactif complet** en 8 étapes
- ✅ **Menu drastiquement réduit** (80% moins d'options)
- ✅ **5 raccourcis clavier essentiels** seulement (au lieu de 15+)
- ✅ **Barre d'outils visuelle** pour les actions principales
- ✅ **Présets de rôles** (Chanteur, Danseur, Narrateur, Duo, Trio, Quartet)
- ✅ **Onboarding automatique** au premier lancement

📖 **Lire le guide complet** : [SIMPLIFICATION_GUIDE.md](SIMPLIFICATION_GUIDE.md)

---

## Update Log

### 2026-08-23 — Version 1.0 : Simplification UI/UX Complète
- 🎨 Nouvelle page d'accueil avec boutons simples
- 🧙 Assistant de création de projet (Wizard) pas-à-pas
- 📚 Tutoriel interactif amélioré (8 étapes)
- 📋 Menu simplifié (Fichier, Édition, Affichage, Aide)
- ⌨️ Raccourcis clavier réduits à 5 essentiels
- 🛠️ Barre d'outils avec boutons visuels
- 🎭 Présets de rôles prédéfinis
- 👋 Onboarding automatique au premier lancement

### 2026-08-23 - Version 0.5 : Fix de bug
- Liste bugs fix
	- Création d'un fichier sans vidéo
	- Désynchronisation des bandes
	- Chronomètre non fixe
	- Vidéo non affichée

### 2026-05-31 — Ajout du tutoriel
- Ajout d'un tutoriel interactif (fenêtre Next/Back) couvrant :
	- Fichiers (ouvrir/sauvegarder, glisser-déposer)
	- Édition (édition de phrases, séparateurs internes)
	- Paramètres (personnalisation des bandes, police)
	- Raccourcis (configuration des touches)
	- Astuces (autosave, annuler/rétablir)

---

## Description

OmeRyth est un outil professionnel de synchronisation de **rythme et typographie**.

Synchronisez votre texte avec la musique ou la vidéo pour créer des contenus visuels impressionnants !

## Fonctionnalités principales

✅ **Synchronisation facile**
- Importez une vidéo/audio
- Divisez en bandes (lignes de texte)
- Synchronisez avec la musique

✅ **Gestion des rôles**
- Assignez des rôles aux bandes (Chanteur, Danseur, etc.)
- Personnalisez les couleurs
- Utilisez des présets prédéfinis

✅ **Interface intuitive**
- Page d'accueil claire
- Menu simplifié
- Barre d'outils visuelle
- Raccourcis clavier essentiels

✅ **Sauvegarde et export**
- Autosave intégré
- Annuler/Rétablir
- Export en vidéo

## Installation

> ⚠️ **Dépendances non incluses dans le repo (trop volumineuses)**
>
> Les dossiers suivants **ne sont pas sur GitHub** et doivent être obtenus séparément puis placés à la racine du projet :
>
> | Dossier | Description | Source |
> |---------|-------------|--------|
> | `jre/`  | Java Runtime Environment (JRE 17+) | [Adoptium](https://adoptium.net/) — extraire dans `jre/` |
> | `vlc/`  | Bibliothèques VLC (libvlc) | [VideoLAN](https://www.videolan.org/vlc/) — copier le contenu de VLC dans `vlc/` |
> | `libs/` | JARs de dépendances (vlcj, etc.) | Demander au contributeur ou voir `run.bat` |
> | `ffmpeg/` | Binaires FFmpeg | [ffmpeg.org](https://ffmpeg.org/download.html) — placer `ffmpeg.exe`, `ffprobe.exe` dans `ffmpeg/` |
>
> Une fois ces dossiers en place, la structure racine doit ressembler à :
> ```
> OmeRyth/
> ├── src/         ← code source (dans le repo)
> ├── jre/         ← à ajouter manuellement
> ├── vlc/         ← à ajouter manuellement
> ├── libs/        ← à ajouter manuellement
> ├── ffmpeg/      ← à ajouter manuellement
> ├── run.bat      ← script de lancement
> └── ...
> ```

### Étapes

1. Clonez le repository : `git clone <url>`
2. Ajoutez les dossiers manquants (voir tableau ci-dessus)
3. Compilez le projet Java avec votre IDE (IntelliJ, Eclipse…) ou via `run.bat`
4. Exécutez `MainFenetre.main()`
5. Le tutoriel s'affichera automatiquement au premier lancement !

## Utilisation Rapide

### Première utilisation
1. Lancez l'application
2. Cliquez sur **"Tutoriel Interactif"** (recommandé)
3. Cliquez sur **"Nouveau Projet"**
4. Suivez les 3 étapes simples

### Raccourcis clavier essentiels
```
ESPACE          = Lecture / Arrêt
← →             = Reculer / Avancer (0.5s)
R               = Retour au début
M               = Ajouter séparateur
CTRL+Z / Y      = Annuler / Rétablir
```
(possibilité de les changer sur l'application)

### Actions principales
- **Fichier** : Nouveau, Ouvrir, Sauvegarder, Exporter
- **Édition** : Annuler, Rétablir
- **Affichage** : Afficher/masquer séparateurs et graduations
- **Aide** : Tutoriel, Raccourcis, À propos

## Contributeurs

- **Kripy** - Développeur (v2.0 : Simplification UI/UX)
- **Ometitz** - Owner et producteur

## Licence

OmeRyth - Tous droits réservés

---

## 🚀 Prêt à démarrer ?

Lancez l'application et profitez de l'expérience simplifiée !

💡 **Astuce** : Le tutoriel s'ouvre automatiquement au premier lancement. Profitez-en ! 📚
