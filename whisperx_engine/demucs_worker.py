"""
OmeRyth Demucs Worker — Séparation Vocale IA Haute Fidélité
Basé sur Facebook Research Demucs (HTDemucs).
Extrait la bande instrumentale / ambiance propre sans les voix pour le doublage et karaoké.
"""

import argparse
import os
import subprocess
import sys
import tempfile
import warnings

# Forcer UTF-8 sur stdout et stderr pour Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

warnings.filterwarnings("ignore")

# Ajouter ffmpeg local au PATH
base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ffmpeg_dir = os.path.join(base_dir, "ffmpeg")
if os.path.exists(ffmpeg_dir):
    os.environ["PATH"] = ffmpeg_dir + os.pathsep + os.environ.get("PATH", "")

def print_progress(step: int, total: int, message: str):
    print(f"PROGRESS:{step}:{total}:{message}", flush=True)

def print_error(message: str):
    print(f"ERROR:{message}", flush=True)

def print_info(message: str):
    print(f"INFO:{message}", flush=True)

def extract_audio_if_needed(input_path: str) -> tuple[str, bool]:
    """Si le fichier est une vidéo ou non-wav, extrait la piste audio en WAV via FFmpeg."""
    ext = os.path.splitext(input_path)[1].lower()
    if ext == ".wav":
        return input_path, False

    temp_wav = tempfile.NamedTemporaryFile(suffix="_demucs_extracted.wav", delete=False)
    temp_wav.close()
    temp_wav_path = temp_wav.name

    ffmpeg_bin = os.path.join(ffmpeg_dir, "ffmpeg.exe") if os.path.exists(os.path.join(ffmpeg_dir, "ffmpeg.exe")) else "ffmpeg"
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
            raise RuntimeError("FFmpeg a échoué à extraire l'audio de la vidéo.")
        return temp_wav_path, True
    except Exception as e:
        if os.path.exists(temp_wav_path):
            os.remove(temp_wav_path)
        raise e

def main():
    parser = argparse.ArgumentParser(description="OmeRyth Demucs Audio Separation")
    parser.add_argument("--input", required=True, help="Chemin du fichier audio ou vidéo source")
    parser.add_argument("--output", required=True, help="Chemin de sauvegarde du fichier séparé")
    parser.add_argument("--stem", default="no_vocals", choices=["no_vocals", "vocals"], help="Piste à conserver")
    parser.add_argument("--device", default="auto", choices=["auto", "cuda", "cpu"], help="Dispositif d'exécution")
    args = parser.parse_args()

    input_file = os.path.abspath(args.input)
    output_file = os.path.abspath(args.output)

    if not os.path.exists(input_file):
        print_error(f"Fichier introuvable : {input_file}")
        sys.exit(1)

    print_progress(5, 100, "Initialisation de Demucs et vérification du matériel...")

    import torch
    import demucs.api

    if args.device == "auto":
        device = "cuda" if torch.cuda.is_available() else "cpu"
    else:
        device = args.device

    dev_label = "GPU NVIDIA (CUDA)" if device == "cuda" else "CPU Multi-cœurs"
    print_info(f"Accélération : {dev_label}")

    temp_audio_path = None
    is_temp = False
    try:
        print_progress(10, 100, "Préparation du flux audio...")
        temp_audio_path, is_temp = extract_audio_if_needed(input_file)

        print_progress(20, 100, f"Chargement du modèle Demucs ({dev_label})...")

        last_percent = [20]
        def progress_callback(data: dict):
            audio_len = data.get("audio_length", 1)
            offset = data.get("segment_offset", 0)
            if audio_len > 0:
                pct = 20 + int((offset / audio_len) * 75)
                pct = min(95, max(20, pct))
                if pct > last_percent[0]:
                    last_percent[0] = pct
                    print_progress(pct, 100, f"Séparation vocale IA en cours ({pct}%)...")

        separator = demucs.api.Separator(
            model="htdemucs",
            device=device,
            shifts=1,
            overlap=0.25,
            callback=progress_callback
        )

        print_progress(25, 100, "Séparation neuronale des sources sonores...")
        origin, separated = separator.separate_audio_file(temp_audio_path)

        print_progress(96, 100, "Reconstruction de la piste sans voix...")
        if args.stem == "no_vocals":
            stems_to_sum = [v for k, v in separated.items() if k != "vocals"]
            if not stems_to_sum:
                raise RuntimeError("Aucune piste instrumentale trouvée.")
            target_wav = stems_to_sum[0]
            for s in stems_to_sum[1:]:
                target_wav = target_wav + s
        else:
            target_wav = separated.get("vocals")
            if target_wav is None:
                raise RuntimeError("Piste voix introuvable.")

        os.makedirs(os.path.dirname(output_file), exist_ok=True)

        print_progress(98, 100, "Finalisation du fichier audio...")
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
