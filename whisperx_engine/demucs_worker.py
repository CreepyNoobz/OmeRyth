"""
================================================================================
OmeRyth — Séparation Vocale Neuronale Haute Fidélité (Demucs HTDemucs)
================================================================================
Ce composant est le moteur d'isolation audio d'OmeRyth. Il s'appuie sur le
réseau de neurones de pointe Demucs développé par Facebook Research (modèle
Hybride Transformer HTDemucs).

Son rôle pour les comédiens et directeurs artistiques de doublage :
- Isoler la voix originale pour pouvoir la baisser, la muter ou l'étudier.
- Extraire une "bande témoin" ou "version internationale" (VI) contenant
  uniquement la musique et les bruitages / ambiances, sans aucune parole.
- Permettre d'enregistrer les nouvelles voix françaises sur une bande
  musicale parfaitement propre, sans réverbération ni résidu de l'ancienne voix.
================================================================================
"""

import argparse
import os
import subprocess
import sys
import tempfile
import warnings

# Forcer l'encodage UTF-8 sur les flux standards pour éviter les erreurs d'affichage
# de caractères accentués sur les consoles Windows (cp1252 par défaut)
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# On désactive les avertissements verbeux de PyTorch et TorchAudio
# afin de garder la console propre pour les messages destinés à l'application Java
warnings.filterwarnings("ignore")

# Intégration du dossier FFmpeg local au PATH système s'il est présent à la racine du projet
base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ffmpeg_dir = os.path.join(base_dir, "ffmpeg")
if os.path.exists(ffmpeg_dir):
    os.environ["PATH"] = ffmpeg_dir + os.pathsep + os.environ.get("PATH", "")

# ─────────────────────────────────────────────────────────────────────────────
# Protocole de communication textuelle avec l'application OmeRyth (Java)
# ─────────────────────────────────────────────────────────────────────────────

def print_progress(step: int, total: int, message: str):
    """
    Transmet l'avancement du traitement à la barre de progression Java.
    Format de trame : PROGRESS:<étape_courante>:<total>:<message_utilisateur>
    """
    print(f"PROGRESS:{step}:{total}:{message}", flush=True)

def print_error(message: str):
    """Signale une erreur critique à l'application Java via stdout."""
    print(f"ERROR:{message}", flush=True)

def print_info(message: str):
    """Envoie un message d'information ou de diagnostic pour le journal d'OmeRyth."""
    print(f"INFO:{message}", flush=True)

def extract_audio_if_needed(input_path: str) -> tuple[str, bool]:
    """
    Vérifie le format du média source :
    - Si le fichier est déjà un WAV PCM non compressé, on le traite directement.
    - S'il s'agit d'une vidéo (MP4, MKV, AVI, MOV...) ou d'un audio compressé (MP3, AAC, FLAC),
      on extrait la piste audio brute en WAV 44.1 kHz 16-bit stéréo via FFmpeg.
    
    Retourne :
      (chemin_du_fichier_audio_wav, est_un_fichier_temporaire_a_supprimer)
    """
    ext = os.path.splitext(input_path)[1].lower()
    if ext == ".wav":
        return input_path, False

    # Création d'un fichier temporaire unique avec suffixe explicite
    temp_wav = tempfile.NamedTemporaryFile(suffix="_demucs_extracted.wav", delete=False)
    temp_wav.close()
    temp_wav_path = temp_wav.name

    # Détection de l'exécutable FFmpeg (priorité à la version embarquée dans OmeRyth)
    ffmpeg_bin = os.path.join(ffmpeg_dir, "ffmpeg.exe") if os.path.exists(os.path.join(ffmpeg_dir, "ffmpeg.exe")) else "ffmpeg"
    
    # Commande FFmpeg : extraction audio pure sans réencodage vidéo (-vn -sn -dn)
    # à 44.1 kHz stéréo (standard studio compatible avec le modèle Demucs)
    cmd = [
        ffmpeg_bin, "-y",
        "-i", input_path,
        "-vn", "-sn", "-dn",
        "-c:a", "pcm_s16le",
        "-ar", "44100",
        temp_wav_path
    ]
    try:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if res.returncode != 0 or not os.path.exists(temp_wav_path) or os.path.getsize(temp_wav_path) == 0:
            raise RuntimeError("FFmpeg n'a pas pu extraire la piste audio de la vidéo source.")
        return temp_wav_path, True
    except Exception as e:
        # Nettoyage immédiat du fichier temporaire en cas d'échec
        if os.path.exists(temp_wav_path):
            os.remove(temp_wav_path)
        raise e

def main():
    """
    Point d'entrée principal du worker de séparation vocale.
    Reçoit les arguments en ligne de commande depuis l'interface Java d'OmeRyth,
    instancie le réseau de neurones HTDemucs, sépare les pistes et sauvegarde le résultat.
    """
    parser = argparse.ArgumentParser(description="OmeRyth — Séparation Vocale IA par Facebook Demucs")
    parser.add_argument("--input", required=True, help="Chemin d'accès au fichier vidéo ou audio source")
    parser.add_argument("--output", required=True, help="Chemin où enregistrer le fichier audio séparé (WAV)")
    parser.add_argument("--stem", default="no_vocals", choices=["no_vocals", "vocals"],
                        help="Piste souhaitée : 'no_vocals' (instrumental/bruitages) ou 'vocals' (voix seules)")
    parser.add_argument("--device", default="auto", choices=["auto", "cuda", "cpu"],
                        help="Périphérique de calcul : 'auto' (détection GPU), 'cuda' (GPU NVIDIA) ou 'cpu' (processeur)")
    args = parser.parse_args()

    input_file = os.path.abspath(args.input)
    output_file = os.path.abspath(args.output)

    if not os.path.exists(input_file):
        print_error(f"Fichier source introuvable sur le disque : {input_file}")
        sys.exit(1)

    print_progress(5, 100, "Initialisation de Demucs et détection des composants matériels...")

    import torch
    import demucs.api

    # Détection automatique du moteur d'accélération (GPU CUDA vs CPU)
    if args.device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    else:
        device = args.device

    dev_label = "GPU NVIDIA (CUDA)" if device == "cuda" else "CPU Multi-cœurs"
    print_info(f"Moteur de calcul sélectionné : {dev_label}")

    temp_audio_path = None
    is_temp = False
    try:
        print_progress(10, 100, "Extraction et conversion du flux audio en format studio...")
        temp_audio_path, is_temp = extract_audio_if_needed(input_file)

        print_progress(20, 100, f"Chargement des poids du modèle HTDemucs sur {dev_label}...")

        # Suivi fin de progression renvoyé à la barre de progression d'OmeRyth
        last_percent = [20]
        def progress_callback(data: dict):
            audio_len = data.get("audio_length", 1)
            offset = data.get("segment_offset", 0)
            if audio_len > 0:
                pct = 20 + int((offset / audio_len) * 75)
                pct = min(95, max(20, pct))
                if pct > last_percent[0]:
                    last_percent[0] = pct
                    print_progress(pct, 100, f"Séparation vocale neuronale en cours ({pct}%)...")

        # Configuration du séparateur HTDemucs :
        # - shifts=1 : 1 seul décalage temporel (excellent compromis rapidité / absence d'artefacts)
        # - overlap=0.25 : recouvrement de 25% entre segments contigus pour éviter tout "clic" ou coupure
        separator = demucs.api.Separator(
            model="htdemucs",
            device=device,
            shifts=1,
            overlap=0.25,
            callback=progress_callback
        )

        print_progress(25, 100, "Séparation neuronale en cours (décomposition en 4 canaux spectraux)...")
        origin, separated = separator.separate_audio_file(temp_audio_path)

        # HTDemucs décompose l'audio en 4 pistes distinctes :
        # 'drums' (batterie/percussions), 'bass' (basses), 'other' (instruments/effets/ambiances) et 'vocals' (voix).
        print_progress(96, 100, "Recombinaison acoustique et mixage final...")
        if args.stem == "no_vocals":
            # Pour obtenir la bande internationale (sans voix), on additionne mathématiquement
            # tous les tenseurs audio qui ne sont PAS la piste 'vocals'
            stems_to_sum = [v for k, v in separated.items() if k != "vocals"]
            if not stems_to_sum:
                raise RuntimeError("Aucune piste instrumentale n'a pu être isolée par le modèle.")
            target_wav = stems_to_sum[0]
            for s in stems_to_sum[1:]:
                target_wav = target_wav + s
        else:
            # Mode comédien témoin : on isole uniquement la voix originale
            target_wav = separated.get("vocals")
            if target_wav is None:
                raise RuntimeError("La piste vocale isolée est introuvable dans la sortie du modèle.")

        os.makedirs(os.path.dirname(output_file), exist_ok=True)

        print_progress(98, 100, "Sauvegarde du master audio final...")
        demucs.api.save_audio(
            target_wav,
            output_file,
            samplerate=separator.samplerate,
            bitrate=320,
            clip="rescale",
            bits_per_sample=16
        )

        print_progress(100, 100, "Séparation vocale terminée avec succès !")
        print(f"SUCCESS:{output_file}", flush=True)

    except Exception as e:
        print_error(str(e))
        sys.exit(1)
    finally:
        if is_temp and temp_audio_path and os.path.exists(temp_audio_path):
            try:
                os.remove(temp_audio_path)
            except Exception:
                pass

if __name__ == "__main__":
    main()
