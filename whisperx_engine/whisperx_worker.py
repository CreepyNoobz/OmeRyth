"""
================================================================================
OmeRyth — Moteur de Transcription Vocale Haute Précision & Calage Rythmo
================================================================================
Ce composant Python constitue le cœur de détection vocale pour la bande rythmo.
Il orchestre :
1. L'inférence rapide via `faster-whisper` (implémentation optimisée CTranslate2
   utilisant les instructions vectorielles AVX2/AVX-512 sur CPU et Tensor Cores sur GPU).
2. Le filtrage de l'activité vocale (VAD Silero) pour éliminer les silences, bruits
   de fond et respirations parasites sans jamais amputer les syllabes d'attaque.
3. L'extraction d'horodatages précis au niveau du mot unitaire (Word Timestamps).
4. Le découpage intelligent en phrases naturelles pour le doublage avec séparateurs
   début (vert ▶) et fin (rouge ◀), respectant scrupuleusement la règle des pauses
   de 0.5 seconde.
5. La diarisation vocale acoustique : séparation automatique des personnages
   par analyse du pitch fondamental (F0), du timbre spectral (MFCCs) et de la brillance.

Fonctionne 100% hors-ligne une fois les modèles téléchargés dans whisper/cache.
================================================================================
"""

import argparse
import json
import os
import re
import sys
import time
import warnings
from datetime import datetime, timezone
import numpy as np

# ─────────────────────────────────────────────────────────────────────────────
# Configuration de l'environnement d'exécution Windows
# ─────────────────────────────────────────────────────────────────────────────

# Forcer l'encodage UTF-8 sur stdout et stderr pour éviter tout plantage lié aux accents
# français sur les terminaux Windows (qui sont historiquement configurés en cp1252)
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

# Masquer les avertissements de bas niveau (PyTorch, HuggingFace, ThreadPool)
# pour garantir que seules les lignes de protocole OmeRyth transitent sur stdout
warnings.filterwarnings("ignore")

# Détection et enregistrement de FFmpeg local dans le PATH système
ffmpeg_dir = os.path.abspath("ffmpeg")
if os.path.exists(ffmpeg_dir):
    os.environ["PATH"] = ffmpeg_dir + os.pathsep + os.environ["PATH"]

# Enregistrement dynamique des bibliothèques d'exécution NVIDIA CUDA (cuBLAS, cuDNN)
# si elles sont présentes dans l'environnement Python
try:
    import site
    site_packages = site.getsitepackages() if hasattr(site, "getsitepackages") else []
    for sp in site_packages:
        nvidia_path = os.path.join(sp, "nvidia")
        if os.path.isdir(nvidia_path):
            for sub in os.listdir(nvidia_path):
                sub_dir = os.path.join(nvidia_path, sub)
                for candidate in ("bin", "lib", ""):
                    target_dir = os.path.join(sub_dir, candidate) if candidate else sub_dir
                    if os.path.isdir(target_dir) and target_dir not in os.environ["PATH"]:
                        os.environ["PATH"] = target_dir + os.pathsep + os.environ["PATH"]
                        if hasattr(os, "add_dll_directory"):
                            try:
                                os.add_dll_directory(target_dir)
                            except Exception:
                                pass
except Exception:
    pass

# Neutralisation des avertissements de privilèges de liens symboliques Windows pour HuggingFace
os.environ["HF_HUB_DISABLE_SYMLINKS_WARNING"] = "1"
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "0"
os.environ["LOKY_MAX_CPU_COUNT"] = "4"

# ─────────────────────────────────────────────────────────
# Communication protocol with Java (stdout lines)
# ─────────────────────────────────────────────────────────

def print_progress(step: int, total: int, message: str):
    """Envoie la progression vers Java via stdout."""
    print(f"PROGRESS:{step}:{total}:{message}", flush=True)

def print_error(message: str):
    """Envoie une erreur vers Java via stdout."""
    print(f"ERROR:{message}", flush=True)

def print_info(message: str):
    """Envoie un message informatif vers Java via stdout."""
    print(f"INFO:{message}", flush=True)

def print_live_segment(seg_dict: dict):
    """Envoie un segment intermédiaire détecté en temps réel vers Java."""
    line = json.dumps(seg_dict, ensure_ascii=False)
    print(f"LIVE_SEGMENT:{line}", flush=True)

def print_segment(seg_dict: dict):
    """Envoie un segment détecté en temps réel vers Java."""
    line = json.dumps(seg_dict, ensure_ascii=False)
    print(f"SEGMENT:{line}", flush=True)

def format_timecode(seconds: float) -> str:
    """Formate des secondes en timecode HH:MM:SS.mmm."""
    if seconds < 0:
        seconds = 0.0
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    millis = int(round((seconds - int(seconds)) * 1000))
    if millis >= 1000:
        millis -= 1000
        secs += 1
        if secs >= 60:
            secs -= 60
            minutes += 1
            if minutes >= 60:
                minutes -= 60
                hours += 1
    return f"{hours:02}:{minutes:02}:{secs:02}.{millis:03}"


# ─────────────────────────────────────────────────────────
# Text cleanup, French phonetic corrections & typography
# ─────────────────────────────────────────────────────────

# Patterns for Whisper hallucination artefacts
_BRACKET_RE = re.compile(r"\[.*?\]|\(.*?\)")
# Ne cible QUE les boucles d'hallucinations dégénérées (>= 4 mots identiques ou >= 3 phrases répétées)
# Préserve intégralement les répétitions naturelles en doublage ("oui oui", "non non", "attends attends", "très très", etc.)
_REPEATED_WORD_LOOP_RE = re.compile(r"\b(\w{3,})\b(?:\s+\1\b){3,}", re.IGNORECASE)
_REPEATED_PHRASE_LOOP_RE = re.compile(r"\b(\w{2,}(?:\s+\w+){1,4})\b(?:\s+\1\b){2,}", re.IGNORECASE)
_MULTI_SPACE_RE = re.compile(r"  +")

FRENCH_PHONETIC_CORRECTIONS = [
    (re.compile(r"\bla\s+tête\s+d['’]homme\b", re.IGNORECASE), "la tête de oim"),
    (re.compile(r"\bla\s+tête\s+de\s+homme\b", re.IGNORECASE), "la tête de oim"),
    (re.compile(r"\bla\s+tête\s+d['’]oim\b", re.IGNORECASE), "la tête de oim"),
    (re.compile(r"\bla\s+tête\s+d['’]oit\b", re.IGNORECASE), "la tête de oit"),
    (re.compile(r"\bla\s+tête\s+de\s+ouam\b", re.IGNORECASE), "la tête de oim"),
    # Salutations & expressions urbaines / familières
    (re.compile(r"\b[Dd]io les gens\b", re.IGNORECASE), "Yo les gens"),
    (re.compile(r"\byo les Jean\b", re.IGNORECASE), "Yo les gens"),
    (re.compile(r"\b(y['’]?a\s+)?salam\s+elle\s+est\s+comme\b", re.IGNORECASE), "salam aleykoum"),
    (re.compile(r"\bsalam\s+al[eé]y?k[ou]+m\b", re.IGNORECASE), "salam aleykoum"),
    (re.compile(r"\bal[eé]y?k[ou]+m\s+salam\b", re.IGNORECASE), "aleykoum salam"),
    (re.compile(r"\b(insulter|insulte|insulté|insultais|insultait|insulterai|insultera)\s+des\s+mers\b", re.IGNORECASE), r"\1 des mères"),
    (re.compile(r"\b(niquer|nique|niqué)\s+des\s+mers\b", re.IGNORECASE), r"\1 des mères"),
    (re.compile(r"\bbaise\s+des\s+mers\b", re.IGNORECASE), "baise des mères"),
    # Reconstruction des contractions françaises détachées (avec espaces éventuels avant/après apostrophe)
    (re.compile(r"\b([cCdDjJlLmMntTsqQ]|qu|Qu)\s+['’]\s*(\w+)", re.IGNORECASE), r"\1'\2"),
    (re.compile(r"\b([Tt])\s*['’]\s*([aA]s?)\b"), r"t'as"),
    (re.compile(r"\b([Jj])\s*['’]\s*([aA]i)\b"), r"j'ai"),
    (re.compile(r"\b([Cc])\s*['’]\s*([eE]st)\b"), r"c'est"),
    (re.compile(r"\b([Cc])\s*['’]\s*([eéEÉ]tait)\b"), r"c'était"),
    (re.compile(r"\b([Dd])\s*['’]\s*accord\b", re.IGNORECASE), "d'accord"),
    (re.compile(r"\b([Qq]u)\s*['’]\s*est[- ]ce\s+que\b", re.IGNORECASE), "qu'est-ce que"),
    (re.compile(r"\b([Qq]u)\s*['’]\s*on\b", re.IGNORECASE), "qu'on"),
    (re.compile(r"\b([Dd])\s*['’]\s*un\b", re.IGNORECASE), "d'un"),
    (re.compile(r"\b([Dd])\s*['’]\s*une\b", re.IGNORECASE), "d'une"),
    (re.compile(r"\b([Ss])\s*['’]\s*il\s+te\s+pla[iî]t\b", re.IGNORECASE), "s'il te plaît"),
    (re.compile(r"\b([Ss])\s*['’]\s*il\s+vous\s+pla[iî]t\b", re.IGNORECASE), "s'il vous plaît"),
    (re.compile(r"\b([Ss])\s*['’]\s*il\b", re.IGNORECASE), "s'il"),
    (re.compile(r"\b([Ll])\s*['’]\s*entends?\b", re.IGNORECASE), "l'entends"),
    (re.compile(r"\b([Ee]t)\s+[Ee]t\s+euh\b", re.IGNORECASE), "Et euh"),
    (re.compile(r"\b([Ee]t)\s+[Ee]t\b", re.IGNORECASE), "Et"),
    (re.compile(r"\b([Ee]t)\s+uhm\b", re.IGNORECASE), "Et euh"),
    # Mots français articulés / syllabes découpées sur plusieurs temps
    (re.compile(r"\bou\s+bli\s*[eéè](e?s?)\b", re.IGNORECASE), r"oublié\1"),
    (re.compile(r"\bou\s+bli\b", re.IGNORECASE), "oubli"),
    (re.compile(r"\bta\s+m[eèé]\s*re\b", re.IGNORECASE), "ta mère"),
    (re.compile(r"\bma\s+m[eèé]\s*re\b", re.IGNORECASE), "ma mère"),
    (re.compile(r"\bsa\s+m[eèé]\s*re\b", re.IGNORECASE), "sa mère"),
    (re.compile(r"\bton\s+p[eèé]\s*re\b", re.IGNORECASE), "ton père"),
    (re.compile(r"\bmon\s+p[eèé]\s*re\b", re.IGNORECASE), "mon père"),
    (re.compile(r"\bm[eèé]\s+re(s)?\b", re.IGNORECASE), r"mère\1"),
    (re.compile(r"\bp[eèé]\s+re(s)?\b", re.IGNORECASE), r"père\1"),
    (re.compile(r"\bfr[eèé]\s+re(s)?\b", re.IGNORECASE), r"frère\1"),
    (re.compile(r"\bat\s+tends?\b", re.IGNORECASE), "attends"),
    (re.compile(r"\bre\s+gar\s+de(r?)\b", re.IGNORECASE), r"regarde\1"),
    (re.compile(r"\bpour\s+quoi\b", re.IGNORECASE), "pourquoi"),
    (re.compile(r"\bpar\s+ce\s+que\b", re.IGNORECASE), "parce que"),
    (re.compile(r"\bau\s+jourd['’]\s*hui\b", re.IGNORECASE), "aujourd'hui"),
    (re.compile(r"\bmain\s+te\s+nant\b", re.IGNORECASE), "maintenant"),
    (re.compile(r"\btou\s+jours\b", re.IGNORECASE), "toujours"),
    (re.compile(r"\bvrai\s+ment\b", re.IGNORECASE), "vraiment"),
    (re.compile(r"\bcom\s+pren\s+dre\b", re.IGNORECASE), "comprendre"),
    (re.compile(r"\bbon\s+jour\b", re.IGNORECASE), "bonjour"),
    (re.compile(r"\bbon\s+soir\b", re.IGNORECASE), "bonsoir"),
    (re.compile(r"\bdé\s+so\s+l[eé]\b", re.IGNORECASE), "désolé"),
    (re.compile(r"\bab\s+so\s+lu\s+ment\b", re.IGNORECASE), "absolument"),
    (re.compile(r"\bquel\s+qu['’]\s*un\b", re.IGNORECASE), "quelqu'un"),
    # Réparer les '?' parasites introduits par de mauvais encodages sur les accents
    (re.compile(r"(^|\s)\?\s*tous\b", re.IGNORECASE), r"\1à tous"),
    (re.compile(r"(^|\s)\?\s*chaque\b", re.IGNORECASE), r"\1à chaque"),
    (re.compile(r"(^|\s)\?\s*moi\b", re.IGNORECASE), r"\1à moi"),
    (re.compile(r"(^|\s)\?\s*lui\b", re.IGNORECASE), r"\1à lui"),
    (re.compile(r"(^|\s)\?\s*vous\b", re.IGNORECASE), r"\1à vous"),
    (re.compile(r"(^|\s)\?\s*eux\b", re.IGNORECASE), r"\1à eux"),
]

def clean_text(raw: str, lang: str = "fr") -> str:
    """
    Nettoie et formate le texte transcrit pour une lisibilité maximale
    sur la bande rythmo (sans artéfacts, avec accents et ponctuation propre).
    """
    text = raw.strip()
    if not text:
        return ""

    # 1. Supprimer les annotations parasites entre crochets/parenthèses [Musique], (Rires), etc.
    text = _BRACKET_RE.sub("", text)

    # 1b. Éliminer toute fuite accidentelle de métadonnées ou de consignes explicites
    for leak in ["transcription fidèle", "conserver impérativement", "bégaiements exacts"]:
        text = re.sub(rf"(?:^|\b){re.escape(leak)}(?:\b|$)", "", text, flags=re.IGNORECASE)

    # 2. Supprimer uniquement les boucles d'hallucination réelles Whisper (>= 4 mots ou >= 3 phrases)
    text = _REPEATED_WORD_LOOP_RE.sub(r"\1 \1", text)
    text = _REPEATED_PHRASE_LOOP_RE.sub(r"\1", text)

    # 3. Normaliser les espaces multiples
    text = _MULTI_SPACE_RE.sub(" ", text).strip()

    # 4. Corrections phonétiques et rétablissement des accents français
    if lang.startswith("fr"):
        for pattern, repl in FRENCH_PHONETIC_CORRECTIONS:
            text = pattern.sub(repl, text)

        # Typographie française : un espace propre avant ? ! : ;
        text = re.sub(r"\s*([?!:;])", r" \1", text)
        # Pas d'espace avant virgule et point
        text = re.sub(r"\s*([,.])", r"\1", text)

    # 5. Points de suspension normalisés
    text = text.replace("...", "…")
    text = re.sub(r"\.{2,}", "…", text)

    # 6. Guillemets français alternés (« ouvrants et » fermants)
    quote_parts = text.split('"')
    if len(quote_parts) > 1:
        reconstructed = []
        for q_idx, part in enumerate(quote_parts):
            reconstructed.append(part)
            if q_idx < len(quote_parts) - 1:
                reconstructed.append("« " if q_idx % 2 == 0 else " »")
        text = "".join(reconstructed)

    # 7. Suppression des symboles musicaux ou de bruit isolés (♪, ♫, *, _, #)
    text = re.sub(r"[♪♫\*_~#\^]+", " ", text)

    # 8. Première lettre en majuscule
    if text and text[0].islower():
        text = text[0].upper() + text[1:]

    # 9. Nettoyer les espaces résiduels
    text = _MULTI_SPACE_RE.sub(" ", text).strip()

    # 10. Si le texte ne contient aucune lettre ni aucun chiffre, ce n'est pas une phrase valable
    if not any(c.isalnum() for c in text):
        return ""

    return text


# ─────────────────────────────────────────────────────────
# Phrase Segmentation & Acoustic Pause Detection (0.5s Check)
# ─────────────────────────────────────────────────────────

def load_audio(audio_path: str):
    """Charge le signal audio 16kHz mono sous forme de tableau numpy float32."""
    import numpy as np
    try:
        import soundfile as sf
        data, sr = sf.read(audio_path, dtype="float32")
        if data.ndim > 1:
            data = np.mean(data, axis=1)
        return data, sr
    except Exception:
        pass
    try:
        import wave
        with wave.open(audio_path, "rb") as wf:
            sr = wf.getframerate()
            n_ch = wf.getnchannels()
            sw = wf.getsampwidth()
            raw = wf.readframes(wf.getnframes())
            if sw == 2:
                data = np.frombuffer(raw, dtype=np.int16).astype(np.float32) / 32768.0
            elif sw == 4:
                data = np.frombuffer(raw, dtype=np.int32).astype(np.float32) / 2147483648.0
            else:
                data = np.frombuffer(raw, dtype=np.uint8).astype(np.float32) / 128.0 - 1.0
            if n_ch > 1:
                data = data.reshape(-1, n_ch).mean(axis=1)
            return data, sr
    except Exception:
        return np.zeros(16000, dtype=np.float32), 16000


def detect_silence_intervals(audio, sr: int = 16000, min_silence_sec: float = 0.18, frame_ms: int = 20) -> list:
    """
    Vérifie le signal audio pour détecter toutes les fenêtres de silence (dès 0.18-0.20s)
    où la personne s'arrête de parler (énergie RMS en dessous du seuil de voix).
    Retourne les intervalles de silence [(start_sec, end_sec), ...] avec haute précision.
    """
    if audio is None or len(audio) == 0:
        return []
    import numpy as np
    frame_len = int(sr * (frame_ms / 1000.0))
    hop_len = frame_len // 2
    num_frames = max(1, 1 + (len(audio) - frame_len) // hop_len)

    rms = np.zeros(num_frames, dtype=np.float32)
    for i in range(num_frames):
        start = i * hop_len
        end = start + frame_len
        chunk = audio[start:end]
        rms[i] = np.sqrt(np.mean(chunk ** 2)) if len(chunk) > 0 else 0.0

    p10 = float(np.percentile(rms, 10))
    p85 = float(np.percentile(rms, 85))
    silence_thresh = max(0.003, p10 + 0.08 * (p85 - p10))

    min_silence_frames = max(1, int(min_silence_sec / (hop_len / sr)))
    is_silent = (rms < silence_thresh)

    silence_ranges = []
    in_silence = False
    start_f = 0
    for idx, s in enumerate(is_silent):
        if s and not in_silence:
            in_silence = True
            start_f = idx
        elif not s and in_silence:
            in_silence = False
            if (idx - start_f) >= min_silence_frames:
                silence_ranges.append((round(start_f * hop_len / sr, 2), round(idx * hop_len / sr, 2)))
    if in_silence and (len(is_silent) - start_f) >= min_silence_frames:
        silence_ranges.append((round(start_f * hop_len / sr, 2), round(len(is_silent) * hop_len / sr, 2)))

    return silence_ranges


def trim_speech_start_acoustically(audio_data, start_sec: float, end_sec: float, sr: int = 16000, blank_thresh_sec: float = 0.10, frame_ms: int = 20) -> float:
    """
    Vérifie le signal audio pour détecter tout blanc / silence au début d'une réplique ou d'un mot.
    Si la voix commence plus tard que l'horodatage Whisper (ex: latence attentionnelle ou silence pré-vocal),
    rabat précisément le début de réplique sur la première trame vocale active (- marge naturelle de 0.04s).
    """
    if audio_data is None or sr <= 0 or end_sec <= start_sec + 0.10:
        return round(start_sec, 2)
    import numpy as np

    s_idx = max(0, int(start_sec * sr))
    e_idx = min(len(audio_data), int(end_sec * sr))
    if e_idx <= s_idx:
        return round(start_sec, 2)

    chunk = audio_data[s_idx:e_idx]
    frame_len = int(sr * (frame_ms / 1000.0))
    hop_len = frame_len // 2
    num_frames = max(1, 1 + (len(chunk) - frame_len) // hop_len)

    rms = np.zeros(num_frames, dtype=np.float32)
    for i in range(num_frames):
        st = i * hop_len
        en = st + frame_len
        c = chunk[st:en]
        rms[i] = np.sqrt(np.mean(c ** 2)) if len(c) > 0 else 0.0

    p10 = float(np.percentile(rms, 10))
    p85 = float(np.percentile(rms, 85))
    silence_thresh = max(0.003, p10 + 0.08 * (p85 - p10))

    blank_frames = max(1, int(blank_thresh_sec / (hop_len / sr)))

    silent_count = 0
    first_voice_frame = 0
    for f in range(num_frames):
        if rms[f] < silence_thresh:
            silent_count += 1
        else:
            first_voice_frame = f
            break

    if silent_count >= blank_frames and first_voice_frame > 0:
        trimmed_sec = start_sec + (first_voice_frame * hop_len / sr) - 0.04
        return round(max(start_sec, min(end_sec - 0.10, trimmed_sec)), 2)

    return round(start_sec, 2)


def trim_speech_end_acoustically(audio_data, start_sec: float, end_sec: float, sr: int = 16000, blank_thresh_sec: float = 0.18, frame_ms: int = 20) -> float:
    """
    Vérifie le signal audio pour détecter tout blanc / temps mort (>= 0.18-0.20s)
    situé à la fin d'une réplique ou d'un mot.
    Si la personne a fini de parler plus tôt que l'horodatage Whisper, rabat précisément
    la fin de réplique sur la dernière trame vocale active (+ marge naturelle de 0.04s).
    """
    if audio_data is None or sr <= 0 or end_sec <= start_sec + 0.10:
        return round(end_sec, 2)
    import numpy as np

    s_idx = max(0, int(start_sec * sr))
    e_idx = min(len(audio_data), int(end_sec * sr))
    if e_idx <= s_idx:
        return round(end_sec, 2)

    chunk = audio_data[s_idx:e_idx]
    frame_len = int(sr * (frame_ms / 1000.0))
    hop_len = frame_len // 2
    num_frames = max(1, 1 + (len(chunk) - frame_len) // hop_len)

    rms = np.zeros(num_frames, dtype=np.float32)
    for i in range(num_frames):
        st = i * hop_len
        en = st + frame_len
        c = chunk[st:en]
        rms[i] = np.sqrt(np.mean(c ** 2)) if len(c) > 0 else 0.0

    p10 = float(np.percentile(rms, 10))
    p85 = float(np.percentile(rms, 85))
    silence_thresh = max(0.003, p10 + 0.08 * (p85 - p10))

    blank_frames = max(1, int(blank_thresh_sec / (hop_len / sr)))

    silent_count = 0
    last_voice_frame = num_frames - 1
    for f in range(num_frames - 1, -1, -1):
        if rms[f] < silence_thresh:
            silent_count += 1
        else:
            last_voice_frame = f
            break

    if silent_count >= blank_frames:
        trimmed_sec = start_sec + (last_voice_frame * hop_len / sr) + 0.04
        return round(max(start_sec + 0.15, min(end_sec, trimmed_sec)), 2)

    return round(end_sec, 2)


DIALOGUE_STARTERS = {
    "mais", "oui", "non", "ouais", "nan", "ah", "oh", "bah", "ben", "hein",
    "euh", "euuuh", "euhh", "euhhh", "hum", "mmh", "mh", "et",
    "attends", "regarde", "écoute", "pourquoi", "comment", "alors", "donc",
    "c'est", "ce", "tu", "je", "il", "elle", "on", "nous", "vous", "ils", "elles",
    "d'accord", "exactement", "absolument", "jamais", "toujours", "bien", "voilà",
    "merci", "pardon", "s'il", "salut", "bonjour", "bonsoir"
}

FILLER_WORDS = {
    "euh", "euuuh", "euhh", "euhhh", "euuh",
    "et", "ettt", "etttt", "ett",
    "hum", "mmh", "mh", "euhm", "hem",
    "bah", "ben", "ba",
    "ah", "ahh", "ahhh",
    "oh", "ohh", "ohhh",
    "ouais", "nan", "hein",
    "mais", "maiiis", "maiiiseuhhh", "maiseuh", "maiis",
    "mouais", "bof",
    "uh", "um", "er"
}

# Mots-outils et mots de liaison courts qui ne doivent JAMAIS être étirés sur la bande rythmo
# même si Whisper leur assigne une longue durée (artéfact de bégaiement ou timing imprécis)
NEVER_PROLONGED = {
    # prépositions
    "sur", "de", "du", "des", "en", "à", "au", "aux", "par", "pour",
    "dans", "avec", "sans", "sous", "vers", "chez", "entre", "contre",
    # articles et déterminants
    "le", "la", "les", "un", "une", "ce", "cet", "cette", "ces",
    "mon", "ton", "son", "ma", "ta", "sa", "mes", "tes", "ses",
    "notre", "votre", "leur", "nos", "vos", "leurs",
    # pronoms courants
    "il", "elle", "ils", "elles", "je", "tu", "on", "nous", "vous",
    "se", "y", "lui", "me", "te",
    # conjonctions
    "et", "ou", "ni", "que", "qui", "si", "car",
    # adverbes courts
    "ne", "pas", "plus", "très", "bien",
}

def extract_local_pitch(audio_data, start_sec: float, end_sec: float, sr: int = 16000) -> float:
    """Estime rapidement le pitch F0 médian local sur une fenêtre temporelle restreinte."""
    if audio_data is None or sr <= 0 or end_sec <= start_sec:
        return 0.0
    import numpy as np
    import scipy.signal as signal
    s_idx = max(0, int(start_sec * sr))
    e_idx = min(len(audio_data), int(end_sec * sr))
    if e_idx - s_idx < int(sr * 0.04):
        return 0.0
    chunk = audio_data[s_idx:e_idx]
    try:
        sos = signal.butter(3, [65, 450], btype="bandpass", fs=sr, output="sos")
        filt = signal.sosfilt(sos, chunk)
        min_lag = int(sr / 450)
        max_lag = int(sr / 65)
        corr = signal.correlate(filt, filt, mode="full")
        corr = corr[len(corr) // 2:]
        if len(corr) > max_lag:
            pk = min_lag + int(np.argmax(corr[min_lag:max_lag]))
            if corr[0] > 1e-5 and (corr[pk] / corr[0]) > 0.25:
                return float(sr / pk)
    except Exception:
        pass
    return 0.0


def align_words_to_text(phrase_text: str, words: list) -> list:
    """
    Associe chaque mot horodaté Whisper à ses coordonnées exactes (start_pos, end_pos)
    dans le texte de la réplique, de manière insensible à la casse et robuste à la ponctuation normalisée.
    """
    phrase_lower = phrase_text.lower()
    word_spans = []
    search_idx = 0
    for w in words:
        w_txt = w.get("word", "").strip()
        core = re.sub(r"^[^\w]+|[^\w]+$", "", w_txt).lower()
        if not core:
            continue
        pattern = re.compile(r"\b" + re.escape(core) + r"\b", re.IGNORECASE)
        m = pattern.search(phrase_lower, search_idx)
        if m:
            start_pos = m.start()
            end_pos = m.end()
            while end_pos < len(phrase_text) and phrase_text[end_pos] in ('.', ',', '…', ';', '!', '?', ':', '-'):
                end_pos += 1
            if end_pos < len(phrase_text) and phrase_text[end_pos] == " ":
                end_pos += 1
            word_spans.append((start_pos, end_pos, w))
            search_idx = end_pos
        else:
            word_spans.append((search_idx, search_idx, w))
    return word_spans


def compute_rhythmic_separators(phrase_text: str, words: list) -> list:
    """
    Calcule les séparateurs rythmiques internes (INNER) au sein d'une réplique :
    1. Pour les mots parasites/hésitations prolongés (ex: 'maiiiseuhhh', 'ettt', 'euh...' dur >= 0.35s).
       Pose un séparateur au début ET à la fin du mot pour qu'il soit étendu fidèlement.
    2. Pour les pauses internes nettes (gap >= 0.22s) ou après virgule avec pause >= 0.16s.
    Garantit que la coupe tombe toujours sur une frontière de mot propre, sans jamais déchirer un mot en deux.
    """
    if not words or len(words) < 2 or not phrase_text:
        return []

    word_spans = align_words_to_text(phrase_text, words)
    valid_spans = [s for s in word_spans if s[0] < s[1]]
    if len(valid_spans) < len(words) // 2:
        return []

    separators = []

    def add_sep(t, s_idx):
        if s_idx <= 2 or s_idx >= len(phrase_text) - 2:
            return
        # Garantir que s_idx est sur une frontière de mot (espace ou ponctuation)
        if s_idx < len(phrase_text) and phrase_text[s_idx] not in (" ", "'", "’", "-", ",", ";", ":", ".", "!", "?"):
            left_space = phrase_text.rfind(" ", 0, s_idx)
            right_space = phrase_text.find(" ", s_idx)
            if left_space != -1 and (s_idx - left_space) <= 3:
                s_idx = left_space + 1
            elif right_space != -1 and (right_space - s_idx) <= 3:
                s_idx = right_space + 1

        t_round = round(float(t), 3)
        for s in separators:
            if s["split_index"] == s_idx or abs(s["time"] - t_round) < 0.08:
                return
        separators.append({"time": t_round, "split_index": s_idx})

    for i in range(len(word_spans)):
        start_pos, end_pos, w_curr = word_spans[i]
        if start_pos >= end_pos:
            continue
        w_start = float(w_curr.get("start", 0.0))
        w_end = float(w_curr.get("end", 0.0))
        dur = max(0.0, w_end - w_start)
        w_clean = re.sub(r"^[^\w]+|[^\w]+$", "", w_curr.get("word", "")).lower()

        is_filler = (
            w_clean in FILLER_WORDS
            or bool(re.match(r"^(euh+|et+|hum+|ah+|oh+|mais+|ouais+|ben+|bah+)", w_clean))
            or w_clean in {"comme", "commme", "commmme", "mais", "maiiis", "maiis", "donc", "genre", "voilà", "enfin", "alors"}
            or bool(re.search(r"[.…]{2,}$", w_curr.get("word", "")))
        )
        # Seul un vrai mot parasite ou étiré est prolongé dès 0.35s.
        # Les mots-outils (prépositions, articles, pronoms) ne sont JAMAIS étirés
        # même si Whisper leur assigne une longue durée (artéfact de bégaiement).
        # Un mot lexical ordinaire n'est étiré que s'il dure vraiment très longtemps (>= 1.3s).
        is_never_prolonged = w_clean in NEVER_PROLONGED
        is_prolonged = (not is_never_prolonged) and (
            (is_filler and dur >= 0.35) or (dur >= 1.3)
        )

        if is_prolonged:
            if i > 0 and word_spans[i - 1][0] < word_spans[i - 1][1]:
                prev_end = float(word_spans[i - 1][2].get("end", w_start))
                t_before = round((prev_end + w_start) / 2.0, 3) if w_start > prev_end else w_start
                add_sep(t_before, start_pos)
            if i < len(word_spans) - 1 and word_spans[i + 1][0] < word_spans[i + 1][1]:
                next_start = float(word_spans[i + 1][2].get("start", w_end))
                t_after = round((w_end + next_start) / 2.0, 3) if next_start > w_end else w_end
                add_sep(t_after, end_pos)
        else:
            if i < len(word_spans) - 1 and word_spans[i + 1][0] < word_spans[i + 1][1]:
                next_start = float(word_spans[i + 1][2].get("start", w_end))
                gap = next_start - w_end
                # Pause nette entre mots (gap >= 0.20s), après ponctuation faible/suspension, ou répétition mot à mot (ex: comme | comme)
                w_raw = w_curr.get("word", "")
                is_punct = any(w_raw.endswith(p) for p in [",", ";", "…", "...", ":"])
                next_clean = re.sub(r"^[^\w]+|[^\w]+$", "", word_spans[i + 1][2].get("word", "")).lower()
                is_repetition = (w_clean == next_clean and len(w_clean) >= 2)
                if gap >= 0.20 or (is_punct and gap >= 0.06) or is_repetition:
                    t_mid = round((w_end + next_start) / 2.0, 3)
                    add_sep(t_mid, end_pos)

    separators.sort(key=lambda s: (s["split_index"], s["time"]))
    return separators


def norm_word(w: str) -> str:
    """Normalise un mot pour comparaison sans ponctuation ni casse."""
    return re.sub(r'[\W_]+', '', str(w)).lower()


def deduplicate_adjacent_words(words: list) -> list:
    """
    Élimine les dédoublements accidentels de tokens générés par Whisper
    (ex: 'grand...' de 0.04s suivi de 'grand' de 0.12s), tout en préservant
    scrupuleusement les vraies répétitions mot à mot délibérées (ex: 'très très', 'comme comme', 'on a, on a').
    """
    if not words or len(words) < 2:
        return words
    cleaned = []
    i = 0
    while i < len(words):
        w = words[i]
        if i < len(words) - 1:
            w_next = words[i + 1]
            t1 = norm_word(w.get("word", ""))
            t2 = norm_word(w_next.get("word", ""))
            dur1 = float(w.get("end", 0.0)) - float(w.get("start", 0.0))
            dur2 = float(w_next.get("end", 0.0)) - float(w_next.get("start", 0.0))
            gap = float(w_next.get("start", 0.0)) - float(w.get("end", 0.0))

            if t1 and t2 and t1 == t2:
                # Si l'un des deux mots consécutifs identiques est un micro-fragment (< 0.09s)
                # et qu'ils sont contigus (< 0.15s), c'est une fausse répétition (artéfact acoustique/token)
                if min(dur1, dur2) < 0.09 and gap < 0.15:
                    chosen_word = w_next.get("word", "")
                    if chosen_word.endswith("...") or chosen_word.endswith("…"):
                        chosen_word = w.get("word", "").rstrip(".…")
                    if not chosen_word:
                        chosen_word = w_next.get("word", "").rstrip(".…")
                    merged_word = {
                        "word": chosen_word,
                        "start": min(float(w["start"]), float(w_next["start"])),
                        "end": max(float(w["end"]), float(w_next["end"]))
                    }
                    cleaned.append(merged_word)
                    i += 2
                    continue
        cleaned.append(w)
        i += 1
    return cleaned


def segment_words_into_clean_phrases(words: list, silence_intervals: list = None, pause_threshold: float = 0.28, lang: str = "fr", audio_data=None, sr: int = 16000) -> list:
    """
    Découpe les mots en répliques naturelles et complètes pour la bande rythmo :
    - Pause de respiration naturelle (>= 0.28s).
    - Silences acoustiques détectés dans le signal audio (>= 0.20s).
    - Ponctuation forte (?, !, ., …, ...) ou virgules avec pause réelle (>= 0.22s).
    - Mots déclencheurs (et, mais, on, je, etc.) après pause (>= 0.22s).
    - Les micro-pauses internes restent DANS la même phrase avec des séparateurs INNER.
    """
    if not words:
        return []

    phrases = []
    current_words = []
    phrase_start = None

    for w in words:
        w_text = w.get("word", "").strip()
        if not w_text:
            continue

        w_start = float(w.get("start", 0.0))
        w_end = float(w.get("end", w_start + 0.05))

        # Vérification acoustique du mot individuel : ne pas anticiper avant la voix ni déborder sur un blanc
        if audio_data is not None and sr > 0:
            w_start = trim_speech_start_acoustically(audio_data, w_start, w_end, sr=sr, blank_thresh_sec=0.10)
            w_end = trim_speech_end_acoustically(audio_data, w_start, w_end, sr=sr, blank_thresh_sec=0.18)
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start <= (w_start + 0.05) and s_end > (w_start + 0.04) and s_end < (w_end - 0.06):
                    w_start = max(w_start, round(s_end - 0.02, 2))
                if s_start < w_end and s_end >= (w_end - 0.05) and s_start >= (w_start + 0.08):
                    w_end = min(w_end, round(s_start + 0.04, 2))
                # Si un mot enjambe un silence net (>= 0.15s), couper la fin du mot avant le début du silence
                if s_start >= (w_start + 0.08) and (s_end - s_start) >= 0.15 and w_end > s_start:
                    w_end = min(w_end, round(s_start + 0.04, 2))
        w["start"] = w_start
        w["end"] = w_end

        if not current_words:
            phrase_start = w_start
            current_words.append(w)
            continue

        prev_end = float(current_words[-1].get("end", 0.0))
        gap = w_start - prev_end
        phrase_duration = prev_end - phrase_start
        prev_word_text = current_words[-1].get("word", "").strip()

        # 1. Seuil de pause conversationnel (>= 0.28s est une vraie pause / respiration entre répliques)
        is_pause = (gap >= pause_threshold)

        # 2. Ponctuation forte (?, !, ., …, ...) avec pause réelle (>= 0.22s)
        is_strong_punct = any(prev_word_text.endswith(p) for p in ["?", "!", ".", "…", "..."])
        split_strong_punct = is_strong_punct and (gap >= 0.22)

        # 3. Ponctuation intermédiaire (virgule, point-virgule) avec pause nette (>= 0.22s)
        is_comma = any(prev_word_text.endswith(p) for p in [",", ";"])
        split_comma = is_comma and (gap >= 0.22)

        # 4. Détection acoustique de changement de locuteur (saut net de pitch F0 entre répliques)
        has_acoustic_pitch_jump = False
        if audio_data is not None and gap >= 0.18 and sr > 0:
            try:
                p_prev = extract_local_pitch(audio_data, max(0.0, prev_end - 0.40), prev_end, sr)
                p_curr = extract_local_pitch(audio_data, w_start, min(len(audio_data) / sr, w_start + 0.40), sr)
                if p_prev > 50 and p_curr > 50:
                    delta_f0 = abs(p_prev - p_curr)
                    ratio_f0 = max(p_prev, p_curr) / min(p_prev, p_curr)
                    if delta_f0 >= 28.0 and ratio_f0 >= 1.25:
                        has_acoustic_pitch_jump = True
            except Exception:
                pass

        # 5. Check acoustique : vérifier si un temps mort ou silence long >= 0.20s est présent entre les mots
        has_acoustic_silence = False
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start >= (prev_end - 0.05) and s_end <= (w_start + 0.05):
                    if (s_end - s_start) >= 0.20:
                        has_acoustic_silence = True
                        break

        # 6. Mots déclencheurs après pause nette (>= 0.22s)
        w_lower = w_text.lower().strip()
        is_starter = w_lower in {"et", "mais", "alors", "donc", "je", "on", "il", "elle", "ils", "comme"}
        split_starter = is_starter and (gap >= 0.22)

        # 7. Limites de durée de phrase pour le doublage (éviter les répliques démesurées > 6.5s)
        split_long_phrase = (phrase_duration >= 6.5 and gap >= 0.20)

        should_split = is_pause or \
                       has_acoustic_silence or \
                       split_strong_punct or \
                       split_comma or \
                       split_starter or \
                       has_acoustic_pitch_jump or \
                       split_long_phrase

        # Répétition mot à mot (ex: comme... comme, on a, on a, qu'on va, qu'on va) : ne jamais couper en deux répliques distinctes
        is_word_repetition = (norm_word(w_text) == norm_word(prev_word_text) and len(norm_word(w_text)) >= 2)
        if is_word_repetition and gap < 0.65 and (phrase_duration + gap) <= 6.5:
            should_split = False

        if should_split:

            phrase_end = prev_end
            if current_words:
                phrase_start = float(current_words[0].get("start", phrase_start))
            if audio_data is not None and sr > 0:
                phrase_start = trim_speech_start_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.10)
                phrase_end = trim_speech_end_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.18)
            current_words[0]["start"] = max(float(current_words[0].get("start", phrase_start)), phrase_start)
            current_words[-1]["end"] = min(float(current_words[-1].get("end", phrase_end)), phrase_end)

            raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
            cleaned = clean_text(raw_text, lang)
            if cleaned and any(c.isalnum() for c in cleaned):
                start_sec = round(phrase_start, 2)
                end_sec = round(max(phrase_start + 0.15, phrase_end), 2)
                if end_sec <= start_sec:
                    end_sec = round(start_sec + 0.20, 2)

                phrase_words = [
                    {
                        "word": cw.get("word", "").strip(),
                        "start": round(float(cw.get("start", 0.0)), 2),
                        "end": round(min(end_sec, float(cw.get("end", 0.0))), 2),
                    }
                    for cw in current_words
                    if cw.get("word", "").strip() and any(c.isalnum() for c in cw.get("word", ""))
                ]
                if phrase_words:
                    phrase_words[-1]["end"] = min(phrase_words[-1]["end"], end_sec)
                else:
                    tokens = [t for t in cleaned.split() if any(c.isalnum() for c in t)]
                    if tokens:
                        dur = max(0.1, end_sec - start_sec)
                        step = dur / len(tokens)
                        for ti, tok in enumerate(tokens):
                            phrase_words.append({
                                "word": tok,
                                "start": round(start_sec + ti * step, 2),
                                "end": round(start_sec + (ti + 1) * step, 2),
                            })

                if phrase_words:
                    phrases.append({
                        "start": start_sec,
                        "end": end_sec,
                        "text": cleaned,
                        "words": phrase_words,
                    })
            current_words = [w]
            phrase_start = w_start
        else:
            current_words.append(w)

    # Fermer la dernière phrase
    if current_words:
        phrase_end = float(current_words[-1].get("end", 0.0))
        phrase_start = float(current_words[0].get("start", phrase_start))
        if audio_data is not None and sr > 0:
            phrase_start = trim_speech_start_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.10)
            phrase_end = trim_speech_end_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.18)
        current_words[0]["start"] = max(float(current_words[0].get("start", phrase_start)), phrase_start)
        current_words[-1]["end"] = min(float(current_words[-1].get("end", phrase_end)), phrase_end)

        raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
        cleaned = clean_text(raw_text, lang)
        if cleaned and any(c.isalnum() for c in cleaned):
            start_sec = round(phrase_start, 2)
            end_sec = round(max(phrase_start + 0.15, phrase_end), 2)
            if end_sec <= start_sec:
                end_sec = round(start_sec + 0.20, 2)

            phrase_words = [
                {
                    "word": cw.get("word", "").strip(),
                    "start": round(float(cw.get("start", 0.0)), 2),
                    "end": round(min(end_sec, float(cw.get("end", 0.0))), 2),
                }
                for cw in current_words
                if cw.get("word", "").strip() and any(c.isalnum() for c in cw.get("word", ""))
            ]
            if phrase_words:
                phrase_words[-1]["end"] = min(phrase_words[-1]["end"], end_sec)
            else:
                tokens = [t for t in cleaned.split() if any(c.isalnum() for c in t)]
                if tokens:
                    dur = max(0.1, end_sec - start_sec)
                    step = dur / len(tokens)
                    for ti, tok in enumerate(tokens):
                        phrase_words.append({
                            "word": tok,
                            "start": round(start_sec + ti * step, 2),
                            "end": round(start_sec + (ti + 1) * step, 2),
                        })

            if phrase_words:
                phrases.append({
                    "start": start_sec,
                    "end": end_sec,
                    "text": cleaned,
                    "words": phrase_words,
                })

    return phrases


# =========================================================================
# EXTRACTION DES CARACTÉRISTIQUES ACOUSTIQUES (MFCC & TIMBRE VOCAL)
# =========================================================================

def extract_mfcc_stats(y, target_sr=16000, n_mfcc=13, n_fft=512, hop_len=160, n_mels=40):
    """
    Calcule les coefficients cepstraux sur l'échelle de Mel (MFCCs) ainsi que leurs
    statistiques temporelles (moyenne et écart-type) pour caractériser l'empreinte
    vocale (le timbre du comédien / personnage).

    Principe mathématique & acoustique :
    1. Découpage en trames courtes (32 ms avec pas de 10 ms) pour garantir la quasi-stationnarité du signal vocal.
    2. Fenêtrage de Hanning pour éliminer les discontinuités aux bords de chaque fenêtre.
    3. RFFT (Transformée de Fourier Rapide réelle) pour obtenir le spectre de puissance.
    4. Banc de filtres triangulaires espacés sur l'échelle psychocousique de Mel (perception humaine de la hauteur).
    5. Logarithme des énergies de Mel pour compresser la dynamique (loi de Weber-Fechner).
    6. DCT (Transformée en Cosinus Discrète) pour décorréler les canaux et capturer l'enveloppe spectrale.
    """
    import numpy as np
    try:
        from scipy.fft import dct
    except Exception:
        dct = None

    # Si le signal est trop court pour une seule fenêtre FFT, on applique un padding de zéros
    if len(y) < n_fft:
        y = np.pad(y, (0, n_fft - len(y)))

    # Découpage vectorisé en trames glissantes via stride_tricks (très rapide, sans copie mémoire)
    num_frames = max(1, 1 + (len(y) - n_fft) // hop_len)
    frames = np.lib.stride_tricks.as_strided(
        y,
        shape=(num_frames, n_fft),
        strides=(y.strides[0] * hop_len, y.strides[0])
    )
    # Fenêtre de Hanning : atténue les fuites spectrales (spectral leakage)
    window = np.hanning(n_fft)
    # Spectre de puissance (|FFT|^2)
    spec = np.abs(np.fft.rfft(frames * window, n=n_fft)) ** 2

    # Construction du banc de filtres de Mel (triangle filterbank)
    low_mel = 0.0
    high_mel = 2595.0 * np.log10(1.0 + (target_sr / 2.0) / 700.0)
    mel_pts = np.linspace(low_mel, high_mel, n_mels + 2)
    hz_pts = 700.0 * (10.0 ** (mel_pts / 2595.0) - 1.0)
    bin_pts = np.floor((n_fft + 1) * hz_pts / target_sr).astype(int)

    fbank = np.zeros((n_mels, int(n_fft // 2 + 1)))
    for m in range(1, n_mels + 1):
        for k in range(bin_pts[m - 1], bin_pts[m]):
            fbank[m - 1, k] = (k - bin_pts[m - 1]) / max(1, bin_pts[m] - bin_pts[m - 1])
        for k in range(bin_pts[m], bin_pts[m + 1]):
            fbank[m - 1, k] = (bin_pts[m + 1] - k) / max(1, bin_pts[m + 1] - bin_pts[m])

    # Multiplication matricielle pour projeter le spectre sur les bandes de Mel
    mel_energies = np.dot(spec, fbank.T)
    mel_energies = np.maximum(mel_energies, 1e-10)  # Évite le log(0)
    log_mel = np.log(mel_energies)

    # DCT de type II orthogonale pour obtenir les coefficients cepstraux
    if dct is not None:
        mfcc = dct(log_mel, type=2, axis=-1, norm="ortho")[:, :n_mfcc].T
    else:
        # Repli pur numpy si scipy.fft n'est pas disponible
        k = np.arange(n_mfcc)[:, None]
        n = np.arange(n_mels)
        dct_mat = np.cos(np.pi / n_mels * (n + 0.5) * k)
        mfcc = np.dot(log_mel, dct_mat.T).T

    # Statistiques temporelles : moyenne et dispersion sur toute la réplique
    mfcc_mean = np.mean(mfcc, axis=1)
    mfcc_std = np.std(mfcc, axis=1) if mfcc.shape[1] > 1 else np.zeros(n_mfcc)
    return mfcc_mean, mfcc_std


# =========================================================================
# DIARISATION VOCALE SUR PHRASES COMPLÈTES (PITCH F0 + MFCCs + CLUSTERING)
# =========================================================================

def diarize_clean_phrases(audio_path: str, phrases: list, num_speakers: int = 0) -> list:
    """
    Attribue un locuteur distinct (SPEAKER_00, SPEAKER_01...) à chaque phrase
    complète par analyse acoustique du signal audio :
    - Hauteur fondamentale de la voix (Pitch F0 via corrélation croisée après filtrage Butterworth 65-450 Hz)
    - Empreinte spectrale (MFCCs moyenne + écart-type)
    - Brillance et centroïde spectral
    - Classification non-supervisée par K-Means avec sélection optimale du nombre de comédiens (Silhouette Score).
    
    Si num_speakers <= 0, détecte automatiquement le nombre optimal d'intervenants.
    """
    if not phrases:
        return []

    if num_speakers == 1 or len(phrases) < 2:
        for p in phrases:
            p["speaker"] = "SPEAKER_00"
        return phrases

    try:
        import numpy as np
        try:
            import soundfile as sf
            audio_data, sr = sf.read(audio_path, dtype="float32")
            if audio_data.ndim > 1:
                audio_data = np.mean(audio_data, axis=1)
        except Exception:
            import wave
            with wave.open(audio_path, "rb") as wf:
                sr = wf.getframerate()
                n_channels = wf.getnchannels()
                sampwidth = wf.getsampwidth()
                raw_bytes = wf.readframes(wf.getnframes())
                if sampwidth == 2:
                    audio_data = np.frombuffer(raw_bytes, dtype=np.int16).astype(np.float32) / 32768.0
                elif sampwidth == 4:
                    audio_data = np.frombuffer(raw_bytes, dtype=np.int32).astype(np.float32) / 2147483648.0
                else:
                    audio_data = np.frombuffer(raw_bytes, dtype=np.uint8).astype(np.float32) / 128.0 - 1.0
                if n_channels > 1:
                    audio_data = audio_data.reshape(-1, n_channels).mean(axis=1)

        import scipy.signal as signal
        from sklearn.preprocessing import StandardScaler
        from sklearn.cluster import KMeans

        target_sr = 16000
        frame_len = int(target_sr * 0.030)  # Fenêtre d'analyse de 30 ms (environ 480 échantillons à 16kHz)
        hop_len = int(target_sr * 0.020)    # Pas d'avance de 20 ms (recouvrement de 10 ms pour la continuité)
        
        # Bornes de recherche de la fréquence fondamentale humaine :
        # - Voix d'homme très grave : jusqu'à ~65 Hz (période max_lag = 16000 / 65 ≈ 246 échantillons)
        # - Voix d'enfant/femme aiguë : jusqu'à ~450 Hz (période min_lag = 16000 / 450 ≈ 35 échantillons)
        min_lag = int(target_sr / 450)
        max_lag = int(target_sr / 65)

        # Filtre passe-bande de Butterworth d'ordre 4 (SOS : Second-Order Sections pour la stabilité numérique)
        # Il supprime les ronflements secteur (< 50 Hz) et les bruits d'harmoniques aiguës (> 450 Hz)
        sos_pitch = signal.butter(4, [65, 450], btype="bandpass", fs=target_sr, output="sos")

        features = []
        raw_phrase_pitches = []
        all_voiced_pitches = []

        # Premier passage : extraction fine du pitch F0 et collecte des hauteurs de voix
        for p in phrases:
            s_idx = max(0, int(p["start"] * sr))
            e_idx = min(len(audio_data), int(p["end"] * sr))
            y = audio_data[s_idx:e_idx] if e_idx > s_idx else np.zeros(frame_len, dtype=np.float32)
            if len(y) < frame_len:
                y = np.pad(y, (0, frame_len - len(y)))

            y_filt = signal.sosfilt(sos_pitch, y)
            pitches = []
            for i in range(0, len(y_filt) - frame_len, hop_len):
                frame = y_filt[i:i + frame_len]
                energy = np.sum(frame ** 2)
                if energy < 1e-4:
                    continue

                corr = signal.correlate(frame, frame, mode="full")
                corr = corr[len(corr) // 2:]

                if len(corr) > max_lag:
                    pk = min_lag + int(np.argmax(corr[min_lag:max_lag]))
                    if corr[0] > 1e-5 and (corr[pk] / corr[0]) > 0.28:
                        # Interpolation parabolique fine sub-échantillon
                        if 0 < pk < len(corr) - 1:
                            y0, y1, y2 = corr[pk - 1], corr[pk], corr[pk + 1]
                            denom = y0 - 2 * y1 + y2
                            pk_fine = pk if abs(denom) < 1e-12 else pk + 0.5 * (y0 - y2) / denom
                        else:
                            pk_fine = float(pk)
                        f0 = target_sr / max(1.0, pk_fine)
                        if 65 <= f0 <= 450:
                            pitches.append(f0)
                            all_voiced_pitches.append(f0)

            raw_phrase_pitches.append(pitches)

        # Médiane vocale globale de l'audio pour imputer sans biais les phrases courtes/sourdes
        global_median_pitch = float(np.median(all_voiced_pitches)) if all_voiced_pitches else 150.0

        p_med_list = []
        has_pitch_list = []

        # Deuxième passage : construction du vecteur de caractéristiques acoustiques
        for idx, p in enumerate(phrases):
            s_idx = max(0, int(p["start"] * sr))
            e_idx = min(len(audio_data), int(p["end"] * sr))
            y = audio_data[s_idx:e_idx] if e_idx > s_idx else np.zeros(frame_len, dtype=np.float32)
            if len(y) < frame_len:
                y = np.pad(y, (0, frame_len - len(y)))

            pitches = raw_phrase_pitches[idx]
            if len(pitches) >= 2:
                med_pitch = float(np.median(pitches))
                iqr_pitch = float(np.percentile(pitches, 75) - np.percentile(pitches, 25))
                has_voiced = 1.0
            elif len(pitches) == 1:
                med_pitch = float(pitches[0])
                iqr_pitch = 5.0
                has_voiced = 1.0
            else:
                # Phrase sourde ou trop courte : imputation sur la médiane globale pour éviter
                # la création d'un cluster parasite avec pitch = 0.0 !
                med_pitch = global_median_pitch
                iqr_pitch = 0.0
                has_voiced = 0.0

            p_med_list.append(med_pitch)
            has_pitch_list.append(has_voiced)

            log_pitch = np.log2(max(50.0, med_pitch) / 55.0)
            log_iqr = np.log2(max(1.0, iqr_pitch) / 10.0)

            # MFCCs complets (enveloppe du conduit vocal)
            mfcc_mean, mfcc_std = extract_mfcc_stats(y, target_sr=target_sr, n_mfcc=13, n_fft=512, hop_len=160, n_mels=40)

            # Brillance, roll-off et centroïde spectral
            spec = np.abs(np.fft.rfft(y))
            freqs = np.fft.rfftfreq(len(y), 1.0 / target_sr)
            spec_sum = np.sum(spec) + 1e-9
            centroid = np.sum(freqs * spec) / spec_sum
            norm_centroid = centroid / 4000.0

            # Spectral roll-off (85% de l'énergie cumulée)
            cum_spec = np.cumsum(spec)
            rolloff_idx = np.searchsorted(cum_spec, 0.85 * spec_sum)
            norm_rolloff = float(freqs[min(rolloff_idx, len(freqs) - 1)]) / 4000.0

            # Ratio d'énergie bas/haut (résonance trachéale/buccale distincte entre individus)
            low_energy = np.sum(spec[(freqs >= 100.0) & (freqs < 1000.0)]) + 1e-9
            mid_energy = np.sum(spec[(freqs >= 1000.0) & (freqs < 4000.0)]) + 1e-9
            energy_ratio = float(np.clip(np.log2(low_energy / mid_energy), -3.0, 3.0))

            feat = np.hstack([
                [log_pitch],
                [log_iqr],
                [norm_centroid],
                [norm_rolloff],
                [energy_ratio],
                mfcc_mean,
                mfcc_std[:6]
            ])
            features.append(feat)

        X = np.array(features)
        scaler = StandardScaler()
        X_norm = scaler.fit_transform(X)

        # Pondération ciblée : pitch (hauteur), résonance (low/mid ratio) et conduit vocal (MFCC 0-3)
        # sont prioritaires sur les variations phonétiques (MFCC 4+)
        # - col 0 (log_pitch): 3.5
        # - col 1 (log_iqr): 1.2
        # - col 2 (norm_centroid): 1.5
        # - col 3 (norm_rolloff): 1.5
        # - col 4 (energy_ratio): 2.2
        # - col 5-8 (mfcc_mean 0-3): 1.8 chacun (conduit vocal)
        # - col 9-13 (mfcc_mean 4-8): 0.8 chacun
        # - col 14-17 (mfcc_mean 9-12): 0.4 chacun
        # - col 18-23 (mfcc_std 0-5): 0.3 chacun
        weights = np.array([3.5, 1.2, 1.5, 1.5, 2.2] + [1.8] * 4 + [0.8] * 5 + [0.4] * 4 + [0.3] * 6)
        X_weighted = X_norm * weights

        if num_speakers > 1:
            target_k = min(num_speakers, len(phrases))
        else:
            # Mode Auto-détection : sélection optimale et robuste du nombre de personnages
            from sklearn.metrics import silhouette_score
            max_candidates = min(6, max(2, len(phrases) // 2))
            best_k = 1
            best_score = -1.0
            best_labels = None

            for k in range(2, max_candidates + 1):
                try:
                    cl = KMeans(n_clusters=k, random_state=42, n_init=15)
                    labels = cl.fit_predict(X_weighted)

                    # Validation des tailles de cluster : un vrai personnage a au moins 2 répliques
                    # et au moins 10% des interventions du fichier
                    unique, counts = np.unique(labels, return_counts=True)
                    if min(counts) < 2 or (min(counts) / len(phrases)) < 0.10:
                        continue

                    score = float(silhouette_score(X_weighted, labels))
                    if score > best_score:
                        best_score = score
                        best_k = k
                        best_labels = labels
                except Exception:
                    pass

            # Validation stricte multi-locuteurs :
            # Différencie à la fois les voix de hauteur différente (homme/femme) ET les voix de même hauteur (deux hommes ou deux femmes)
            if best_k >= 2 and best_score >= 0.30 and best_labels is not None:
                # Écart de hauteur vocale (pitch)
                cl0_p = [p_med_list[i] for i in range(len(phrases)) if best_labels[i] == 0 and has_pitch_list[i] > 0]
                cl1_p = [p_med_list[i] for i in range(len(phrases)) if best_labels[i] == 1 and has_pitch_list[i] > 0]

                med_p0 = float(np.median(cl0_p)) if cl0_p else global_median_pitch
                med_p1 = float(np.median(cl1_p)) if cl1_p else global_median_pitch
                delta_pitch = abs(med_p0 - med_p1)

                c0 = np.mean(X_weighted[best_labels == 0], axis=0)
                c1 = np.mean(X_weighted[best_labels == 1], axis=0)
                centroid_dist = float(np.linalg.norm(c0 - c1))
                timbre_dist = float(np.linalg.norm(c0[2:] - c1[2:]))

                # Confirmation multi-personnages :
                # Condition 1: Séparation de registre vocal (delta_pitch >= 24 Hz, indice >= 0.30)
                # Condition 2: Séparation de timbre marqué même sans écart de pitch (timbre_dist >= 3.6, indice >= 0.38, centroid_dist >= 3.2)
                if (delta_pitch >= 24.0 and best_score >= 0.30 and centroid_dist >= 2.5) or \
                   (timbre_dist >= 3.6 and best_score >= 0.38 and centroid_dist >= 3.2):
                    target_k = best_k
                else:
                    target_k = 1
            else:
                target_k = 1

            print_info(f"Auto-détection vocale : {target_k} personnage(s) identifié(s) (indice de séparation: {max(0.0, best_score):.2f})")

        if target_k <= 1:
            for p in phrases:
                p["speaker"] = "SPEAKER_00"
            return phrases

        clusterer = KMeans(n_clusters=target_k, random_state=42, n_init=20)
        raw_labels = list(clusterer.fit_predict(X_weighted))

        # Lissage temporel : élimine les faux micro-sauts isolés (< 1.2s) au milieu d'un même intervenant
        for i in range(1, len(phrases) - 1):
            if raw_labels[i] != raw_labels[i - 1] and raw_labels[i - 1] == raw_labels[i + 1]:
                dur = phrases[i]["end"] - phrases[i]["start"]
                gap = phrases[i]["start"] - phrases[i - 1]["end"]
                if dur < 1.2 and gap < 0.4:
                    raw_labels[i] = raw_labels[i - 1]

        # Réassignation chronologique (premier intervenant entendu = SPEAKER_00)
        label_map = {}
        next_speaker_id = 0
        for lbl in raw_labels:
            if lbl not in label_map:
                label_map[lbl] = next_speaker_id
                next_speaker_id += 1

        for i, p in enumerate(phrases):
            spk_num = label_map.get(raw_labels[i], 0)
            p["speaker"] = f"SPEAKER_{spk_num:02d}"

        return phrases

    except Exception as e:
        print_info(f"Diarisation vocale : mode locuteur par défaut ({e})")
        for p in phrases:
            if "speaker" not in p:
                p["speaker"] = "SPEAKER_00"
        return phrases


def merge_consecutive_same_speaker_phrases(phrases: list, silence_intervals: list = None, max_gap: float = 0.15, max_duration: float = 6.5, max_words: int = 25) -> list:
    """
    Fusionne uniquement les micro-fragments immédiatement contigus (< 0.15s) appartenant au même locuteur,
    sans jamais fusionner par-dessus un silence acoustique ou une ponctuation.
    """
    if not phrases:
        return []

    merged = []
    for p in phrases:
        if not merged:
            merged.append(p)
            continue

        prev = merged[-1]
        gap = p["start"] - prev["end"]
        combined_dur = p["end"] - prev["start"]
        combined_words = len(prev.get("words", [])) + len(p.get("words", []))

        # Si un silence acoustique existe entre les répliques, ne JAMAIS fusionner
        has_silence = False
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start < (p["start"] + 0.04) and s_end > (prev["end"] - 0.04) and (s_end - s_start) >= 0.15:
                    has_silence = True
                    break

        prev_text = prev.get("text", "").strip()
        ends_with_strong_punct = any(prev_text.endswith(pt) for pt in [".", "!", "?", "…", "...", ",", ";"])

        if (not has_silence and
            not ends_with_strong_punct and
            prev.get("speaker") == p.get("speaker") and
            gap < max_gap and
            combined_dur <= max_duration and
            combined_words <= max_words):
            prev["end"] = p["end"]
            prev["text"] = (prev["text"] + " " + p["text"]).strip()
            prev["words"] = prev.get("words", []) + p.get("words", [])
        else:
            merged.append(p)

    return merged


ORPHAN_CONNECTORS = {
    "et", "mais", "ou", "donc", "or", "ni", "car", "si", "que", "qui", "qu", "c", "d", "l", "j", "on"
}


def resolve_orphan_phrases(phrases: list, max_duration: float = 6.5) -> list:
    """
    Élimine les répliques orphelines (ex: 'Et' isolé de 0.16s entre deux pauses).
    Une réplique constituée d'un seul mot de liaison / connecteur est rattachée :
    - soit à la phrase précédente (si le gap précédent est plus court ou égal au gap suivant),
    - soit à la phrase suivante (si le gap suivant est plus court),
    à condition d'appartenir au même locuteur et de ne pas dépasser max_duration.
    """
    if not phrases or len(phrases) < 2:
        return phrases

    changed = True
    iteration = 0
    while changed and iteration < 5:
        changed = False
        iteration += 1
        new_phrases = []
        i = 0
        while i < len(phrases):
            p = phrases[i]
            p_words = p.get("words", [])
            p_text = norm_word(p.get("text", ""))
            is_single_word = len(p_words) == 1
            is_connector = p_text in ORPHAN_CONNECTORS or (len(p_words) == 1 and norm_word(p_words[0].get("word", "")) in ORPHAN_CONNECTORS)
            is_short = (p["end"] - p["start"]) < 0.60

            if is_single_word and is_connector and is_short:
                has_prev = len(new_phrases) > 0
                has_next = (i < len(phrases) - 1)
                prev_p = new_phrases[-1] if has_prev else None
                next_p = phrases[i + 1] if has_next else None

                same_spk_prev = prev_p is not None and prev_p.get("speaker") == p.get("speaker")
                same_spk_next = next_p is not None and next_p.get("speaker") == p.get("speaker")

                gap_prev = (p["start"] - prev_p["end"]) if same_spk_prev else 999.0
                gap_next = (next_p["start"] - p["end"]) if same_spk_next else 999.0

                attach_to = None
                if same_spk_prev and (p["end"] - prev_p["start"]) <= max_duration:
                    if not same_spk_next or gap_prev <= gap_next:
                        attach_to = "prev"
                if attach_to is None and same_spk_next:
                    if (next_p["end"] - p["start"]) <= max_duration:
                        attach_to = "next"

                if attach_to == "prev":
                    p_text_raw = p.get("text", "").strip()
                    word_clean = p_text_raw.lower() if p_text_raw.lower() in ORPHAN_CONNECTORS else p_text_raw
                    prev_text = prev_p["text"].rstrip()
                    if not prev_text.endswith(",") and not prev_text.endswith(";"):
                        prev_p["text"] = f"{prev_text}, {word_clean}"
                    else:
                        prev_p["text"] = f"{prev_text} {word_clean}"
                    prev_p["end"] = p["end"]
                    prev_p["words"] = prev_p.get("words", []) + p_words
                    changed = True
                    i += 1
                    continue
                elif attach_to == "next":
                    p_text_raw = p.get("text", "").strip()
                    word_clean = p_text_raw.capitalize()
                    next_p["start"] = p["start"]
                    next_p["text"] = (word_clean + " " + next_p["text"]).strip()
                    next_p["words"] = p_words + next_p.get("words", [])
                    changed = True
                    i += 1
                    continue

            new_phrases.append(p)
            i += 1
        phrases = new_phrases

    return phrases


# ─────────────────────────────────────────────────────────

# Main transcription pipeline
# ─────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="OmeRyth STT Worker – Haute Précision")
    parser.add_argument("--audio", required=True, help="Chemin du fichier audio WAV")
    parser.add_argument("--num-speakers", type=int, default=0, help="Nombre de locuteurs attendus (0 = Auto-détection de tous les personnages)")
    parser.add_argument("--lang", default="fr", help="Code langue (fr, en, auto)")
    parser.add_argument("--model", default="small", help="Modèle faster-whisper (tiny, base, small, medium)")
    parser.add_argument("--threads", type=int, default=4, help="Nombre de threads CPU")
    parser.add_argument("--device", default="auto", help="Périphérique de calcul (auto, cuda, cpu)")
    parser.add_argument("--compute-type", default="auto", help="Type de calcul (auto, float16, int8_float16, int8)")
    parser.add_argument("--batch-size", type=int, default=16, help="Taille des batchs pour GPU Tensor Cores (1-32)")
    parser.add_argument("--output", required=True, help="Fichier JSON de sortie principal")
    parser.add_argument("--report", required=True, help="Fichier JSON de rapport complet")
    args = parser.parse_args()

    start_time = time.time()
    print_progress(5, 100, "Initialisation du moteur STT...")

    # ── Import de faster-whisper ──
    try:
        from faster_whisper import WhisperModel
    except ImportError as e:
        print_error(f"Bibliothèque faster-whisper manquante : {e}")
        sys.exit(1)

    # ── Configuration ──
    model_name = args.model.lower().strip()
    if model_name not in ("tiny", "base", "small", "medium", "large-v2", "large-v3"):
        model_name = "small"

    cpu_threads = max(1, min(args.threads, 16))
    lang_param = None if args.lang.lower() == "auto" else args.lang.lower()

    # ── Vérification du fichier audio ──
    if not os.path.exists(args.audio):
        print_error(f"Fichier audio introuvable : {args.audio}")
        sys.exit(1)

    # ── Analyse acoustique des pauses et silences (>= 0.10s) dans le signal audio ──
    audio_data, audio_sr = load_audio(args.audio)
    silence_intervals = detect_silence_intervals(audio_data, sr=audio_sr, min_silence_sec=0.10)
    if silence_intervals:
        print_info(f"Analyse vocale : {len(silence_intervals)} temps morts/silences (>= 0.10s) détectés pour découpage.")

    # ── Détection automatique de l'accélération GPU NVIDIA CUDA ──
    req_device = args.device.lower().strip()
    req_compute = args.compute_type.lower().strip()
    device = "cpu"
    compute_type = "int8"

    if req_device in ("auto", "cuda"):
        try:
            import ctranslate2
            if ctranslate2.get_cuda_device_count() > 0:
                device = "cuda"
                supported = ctranslate2.get_supported_compute_types("cuda")
                if req_compute in supported:
                    compute_type = req_compute
                elif "float16" in supported:
                    compute_type = "float16"
                elif "int8_float16" in supported:
                    compute_type = "int8_float16"
                elif "int8" in supported:
                    compute_type = "int8"
                else:
                    compute_type = "float32"
        except Exception:
            device = "cpu"
            compute_type = "int8"
    else:
        device = "cpu"
        compute_type = "int8" if req_compute == "auto" else req_compute

    # ── Inférence & transcription haute résilience avec repli automatique CPU ──
    batch_size = max(1, min(args.batch_size, 32))

    # Prompt initial naturel optimisé pour le doublage et la bande rythmo :
    # Guide le style sans métadonnées pour préserver fidèlement toutes les répétitions et hésitations réelles sans induire de faux bégaiements
    initial_prompt_text = "Transcription fidèle pour bande rythmo et doublage. Conserver absolument toutes les répétitions mot à mot, hésitations et bégaiements réels du comédien : qu'on va, qu'on va régler, on a, on a un pays, et euh... comme... comme ça me correspond."

    # Décodage haute précision optimisé pour le doublage et la bande rythmo :
    # vad_filter=False : transmission intégrale du flux audio sans suppression de chunks Silero VAD,
    # garantissant qu'aucun mot parasite, hésitation ou mot prolongé ("maiiiseuhhh") n'est coupé en amont.
    # no_speech_threshold assoupli à 0.85 et log_prob_threshold désactivé pour ne pas jeter les répliques hésitantes.
    beam_size = 4

    transcribe_kwargs = dict(
        language=lang_param,
        beam_size=beam_size,
        best_of=beam_size,
        patience=1.0,
        word_timestamps=True,
        vad_filter=False,
        condition_on_previous_text=False,
        initial_prompt=initial_prompt_text,
        no_speech_threshold=0.85,
        log_prob_threshold=None,
        compression_ratio_threshold=2.8,
        temperature=0.0,
    )

    def perform_transcription(dev, comp_type):
        local_cache = os.path.abspath("whisper/cache")
        download_root = local_cache if os.path.isdir(local_cache) else None

        print_progress(10, 100, f"Chargement du modèle {model_name.upper()} sur {dev.upper()} ({comp_type})...")

        mdl = WhisperModel(
            model_name,
            device=dev,
            compute_type=comp_type,
            cpu_threads=cpu_threads,
            download_root=download_root,
        )

        pipe = mdl
        use_batch = False
        if dev == "cuda":
            try:
                from faster_whisper import BatchedInferencePipeline
                pipe = BatchedInferencePipeline(model=mdl)
                use_batch = True
                print_progress(15, 100, f"Accélération Tensor Cores active (Batch size {batch_size}, {comp_type})...")
            except Exception:
                pipe = mdl
                use_batch = False

        kwargs = dict(transcribe_kwargs)
        if dev == "cuda":
            kwargs["beam_size"] = 4
            kwargs["best_of"] = 4
        else:
            kwargs["beam_size"] = 2 if model_name in ("tiny", "base") else 3
            kwargs["best_of"] = kwargs["beam_size"]
        if use_batch:
            kwargs["batch_size"] = batch_size

        print_progress(20, 100, f"Transcription en cours ({model_name.upper()} sur {dev.upper()})...")

        seg_iter, inf = pipe.transcribe(args.audio, **kwargs)
        det_lang = inf.language if inf.language else (lang_param or "fr")
        tot_dur = inf.duration if hasattr(inf, "duration") and inf.duration else 0.0

        collected_words = []
        raw_count = 0
        last_pct = 20

        for segment in seg_iter:
            raw_count += 1
            seg_text = segment.text.strip() if segment.text else ""
            if not seg_text or not any(c.isalnum() for c in seg_text):
                continue

            if tot_dur > 0:
                cur_sec = min(segment.end, tot_dur)
                calc_pct = int(20 + (cur_sec / tot_dur) * 70)
                if calc_pct > last_pct:
                    last_pct = calc_pct
                    cur_str = format_timecode(cur_sec)[:8]
                    tot_str = format_timecode(tot_dur)[:8]
                    print_progress(calc_pct, 100, f"Transcription en cours ({cur_str} / {tot_str})...")

            seg_words = []
            if segment.words and len(segment.words) > 0:
                for w in segment.words:
                    w_str = w.word.strip() if w.word else ""
                    if w_str and any(c.isalnum() for c in w_str):
                        w_start = float(w.start) if w.start is not None else float(segment.start)
                        w_end = float(w.end) if w.end is not None else float(segment.end)
                        dur = w_end - w_start
                        # Éliminer les tokens fantômes à durée quasi nulle situés en plein silence
                        if dur < 0.08 and audio_data is not None and audio_sr > 0:
                            s_idx = max(0, int((w_start - 0.03) * audio_sr))
                            e_idx = min(len(audio_data), int((w_end + 0.03) * audio_sr))
                            if e_idx > s_idx:
                                chunk_rms = np.sqrt(np.mean(audio_data[s_idx:e_idx] ** 2))
                                if chunk_rms < 0.015:
                                    continue
                        seg_words.append({
                            "word": w_str,
                            "start": w_start,
                            "end": w_end,
                        })

            # Repli systématique si segment.words n'a donné aucun mot valide : découper seg_text
            if not seg_words and seg_text and any(c.isalnum() for c in seg_text):
                tokens = [t for t in seg_text.split() if any(c.isalnum() for c in t)]
                if tokens:
                    seg_dur = max(0.1, float(segment.end) - float(segment.start))
                    step = seg_dur / len(tokens)
                    for i, token in enumerate(tokens):
                        seg_words.append({
                            "word": token,
                            "start": float(segment.start) + i * step,
                            "end": float(segment.start) + (i + 1) * step,
                        })

            if not seg_words:
                continue

            collected_words.extend(seg_words)

            # Découpage direct en phrases nettes avec détection des alternances de répliques
            live_phrases = segment_words_into_clean_phrases(
                seg_words,
                silence_intervals=silence_intervals,
                pause_threshold=0.22,
                lang=det_lang,
                audio_data=audio_data,
                sr=audio_sr
            )
            for lp in live_phrases:
                raw_count += 1
                print_live_segment({
                    "id": raw_count,
                    "speaker": "SPEAKER_00",
                    "start": lp["start"],
                    "end": lp["end"],
                    "text": lp["text"],
                    "words": lp.get("words", []),
                })

        # Passe de récupération acoustique des poches de parole oubliées (ex: mot répété "comme" ou hésitation courte)
        if audio_data is not None and audio_sr > 0 and len(collected_words) > 1:
            recovered_entries = []
            for i in range(len(collected_words) - 1):
                w1 = collected_words[i]
                w2 = collected_words[i + 1]
                gap = float(w2["start"]) - float(w1["end"])
                # Seuil plus élevé (0.50s) pour éviter les hallucinations sur les courts silences inter-mots
                # Un gap de 0.35s-0.49s est souvent un bégaiement ou une hésitation naturelle, pas un mot manquant
                if gap >= 0.50:
                    s_idx = int(float(w1["end"]) * audio_sr)
                    e_idx = int(float(w2["start"]) * audio_sr)
                    chunk = audio_data[s_idx:e_idx]
                    chunk_rms = np.sqrt(np.mean(chunk ** 2)) if len(chunk) > 0 else 0.0
                    if chunk_rms >= 0.025 and (len(chunk) / audio_sr) >= 0.25:
                        try:
                            gap_segs, _ = mdl.transcribe(
                                chunk,
                                language=det_lang,
                                word_timestamps=True,
                                beam_size=2,
                                initial_prompt=initial_prompt_text
                            )
                            for gs in gap_segs:
                                for gw in (gs.words or []):
                                    w_txt = gw.word.strip() if gw.word else ""
                                    if w_txt and any(c.isalnum() for c in w_txt):
                                        act_start = round(float(w1["end"]) + float(gw.start), 2)
                                        act_end = round(float(w1["end"]) + float(gw.end), 2)
                                        w_dur = act_end - act_start
                                        if w_dur < 0.08:
                                            continue
                                        # Éviter de dupliquer un mot identique si l'horodatage chevauche déjà le mot précédent ou suivant (uniquement si micro-fragment < 0.15s)
                                        n_txt = norm_word(w_txt)
                                        if n_txt == norm_word(w1.get("word", "")) and abs(act_start - float(w1["end"])) < 0.18 and w_dur < 0.15:
                                            continue
                                        if n_txt == norm_word(w2.get("word", "")) and abs(act_end - float(w2["start"])) < 0.18 and w_dur < 0.15:
                                            continue
                                        recovered_entries.append({
                                            "insert_after": i,
                                            "word": {
                                                "word": w_txt,
                                                "start": act_start,
                                                "end": act_end
                                            }
                                        })
                        except Exception:
                            pass
            if recovered_entries:
                for entry in reversed(recovered_entries):
                    collected_words.insert(entry["insert_after"] + 1, entry["word"])
                collected_words.sort(key=lambda w: w["start"])

        # Élimination des micro-dédoublements de tokens accidentels (< 0.09s)
        collected_words = deduplicate_adjacent_words(collected_words)
        return collected_words, det_lang, raw_count


    all_words = []
    detected_lang = lang_param or "fr"
    raw_segment_count = 0
    used_device = "cpu"
    used_compute_type = compute_type

    if device == "cuda":
        try:
            all_words, detected_lang, raw_segment_count = perform_transcription("cuda", compute_type)
            used_device = "cuda"
            used_compute_type = compute_type
        except Exception as e:
            print_info(f"Notification CUDA ({e}) : bascule automatique transparente sur CPU Multi-cœurs...")
            try:
                all_words, detected_lang, raw_segment_count = perform_transcription("cpu", "int8")
                used_device = "cpu"
                used_compute_type = "int8"
            except Exception as e2:
                print_error(f"Échec de la transcription sur CPU : {e2}")
                sys.exit(1)
    else:
        try:
            all_words, detected_lang, raw_segment_count = perform_transcription("cpu", compute_type)
            used_device = "cpu"
            used_compute_type = compute_type
        except Exception as e:
            print_error(f"Échec de la transcription : {e}")
            sys.exit(1)

    if not all_words:
        print_progress(100, 100, "Aucune parole détectée dans le fichier.")
        os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump({"segments": []}, f, ensure_ascii=False, indent=2)
        os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump({
                "metadata": {
                    "engine": "OmeRyth STT (faster-whisper + Silero VAD)",
                    "audio_file": os.path.abspath(args.audio),
                    "model": model_name,
                    "language": detected_lang,
                    "total_segments": 0,
                },
                "segments": [],
            }, f, ensure_ascii=False, indent=2)
        sys.exit(0)

    # ── 1. Découpage en répliques naturelles et complètes avec détection des dialogues ──
    print_progress(70, 100, "Découpage en phrases naturelles pour la bande rythmo...")

    phrases = segment_words_into_clean_phrases(
        all_words,
        silence_intervals=silence_intervals,
        pause_threshold=0.28,
        lang=detected_lang,
        audio_data=audio_data,
        sr=audio_sr
    )

    # ── 2. Diarisation vocale haute précision sur phrases complètes (Pitch F0 + MFCCs) ──
    num_spk = args.num_speakers
    if num_spk != 1 and len(phrases) > 1:
        spk_label = "Auto-détection de tous les personnages" if num_spk <= 0 else f"{num_spk} locuteurs"
        print_progress(80, 100, f"Diarisation des répliques ({spk_label}, analyse pitch F0 & timbre)...")
        phrases = diarize_clean_phrases(args.audio, phrases, num_spk)
    else:
        for p in phrases:
            p["speaker"] = "SPEAKER_00"

    # ── 3. Fusion des micro-fragments contigus d'un même personnage (< 0.15s sans silence) ──
    phrases = merge_consecutive_same_speaker_phrases(phrases, silence_intervals=silence_intervals, max_gap=0.15, max_duration=6.5, max_words=25)

    # ── 3b. Rattachement des mots de liaison orphelins (ex: 'Et' isolé de 0.16s entre deux répliques) ──
    phrases = resolve_orphan_phrases(phrases, max_duration=6.5)


    # Filtrer rigoureusement toute réplique vide ou ne contenant aucun caractère alphanumérique
    phrases = [p for p in phrases if p.get("text") and any(c.isalnum() for c in p["text"])]
    for p in phrases:
        p_words = [w for w in p.get("words", []) if w.get("word") and any(c.isalnum() for c in w["word"])]
        if not p_words:
            toks = [t for t in p["text"].split() if any(c.isalnum() for c in t)]
            if toks:
                dur = max(0.1, p["end"] - p["start"])
                step = dur / len(toks)
                p_words = [
                    {
                        "word": t,
                        "start": round(p["start"] + i * step, 2),
                        "end": round(p["start"] + (i + 1) * step, 2)
                    }
                    for i, t in enumerate(toks)
                ]
        p["words"] = p_words

    # Éliminer les éventuelles répliques sans mots après validation
    phrases = [p for p in phrases if p.get("words")]

    # ── 4. Construction des segments finaux et streaming direct ──
    print_progress(90, 100, "Génération des repères et envoi vers OmeRyth...")

    final_segments = []
    for idx, phrase in enumerate(phrases):
        p_start = phrase["start"]
        p_end = phrase["end"]
        p_words = phrase.get("words", [])

        if p_words:
            first_w_start = float(p_words[0].get("start", p_start))
            if first_w_start > p_start:
                p_start = max(p_start, round(first_w_start - 0.04, 2))

        # Rognage acoustique bilatéral : début sans latence, fin sans traînée
        if audio_data is not None and audio_sr > 0:
            p_start = trim_speech_start_acoustically(audio_data, p_start, p_end, sr=audio_sr, blank_thresh_sec=0.10)
            p_end = trim_speech_end_acoustically(audio_data, p_start, p_end, sr=audio_sr, blank_thresh_sec=0.18)

        if p_words:
            p_words[0]["start"] = max(float(p_words[0].get("start", p_start)), p_start)
            p_words[-1]["end"] = min(float(p_words[-1].get("end", p_end)), p_end)
            last_w_end = float(p_words[-1].get("end", p_end))
            if last_w_end > p_start and last_w_end < p_end:
                p_end = round(last_w_end + 0.04, 2)

        seg_data = {
            "id": idx + 1,
            "speaker": phrase.get("speaker", "SPEAKER_00"),
            "start": p_start,
            "end": p_end,
            "start_timecode": format_timecode(p_start),
            "end_timecode": format_timecode(p_end),
            "duration": round(max(0.1, p_end - p_start), 3),
            "text": phrase["text"],
            "words": p_words,
            "separators": compute_rhythmic_separators(phrase["text"], p_words),
        }
        final_segments.append(seg_data)

        # Envoi en direct vers Java pour affichage dans le tableau
        print_segment(seg_data)

    print_progress(98, 100, "Sauvegarde du rapport final...")

    # ── Écriture du JSON principal pour injection OmeRyth ──
    output_payload = {"segments": final_segments}
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump(output_payload, f, ensure_ascii=False, indent=2)

    # ── Écriture du rapport détaillé ──
    processing_time = round(time.time() - start_time, 2)
    report_payload = {
        "metadata": {
            "engine": "OmeRyth STT (faster-whisper + Silero VAD + Word Timestamps)",
            "audio_file": os.path.abspath(args.audio),
            "model": model_name,
            "language": detected_lang,
            "device": used_device,
            "compute_type": used_compute_type,
            "threads": cpu_threads,
            "total_segments": len(final_segments),
            "total_words": len(all_words),
            "raw_whisper_segments": raw_segment_count,
            "processing_time_seconds": processing_time,
            "timestamp": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        },
        "full_transcript": " ".join(s["text"] for s in final_segments),
        "segments": final_segments,
    }

    os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)
    with open(args.report, "w", encoding="utf-8") as f:
        json.dump(report_payload, f, ensure_ascii=False, indent=2)

    print_progress(100, 100, f"Transcription terminée ! {len(final_segments)} répliques détectées en {processing_time}s")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        import traceback
        traceback.print_exc()
        print_error(f"Erreur interne STT : {e}")
        sys.exit(1)
