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
_REPEATED_WORD_LOOP_RE = re.compile(r"\b(\w{2,})\b(?:\s+\1\b){5,}", re.IGNORECASE)
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
    (re.compile(r"\bmain\s+te\s+nant\b", re.IGNORECASE), "maintenant"),
    (re.compile(r"\b([dD])\s*['’]\s*au\s*jourd['’]?\s*hui\b", re.IGNORECASE), r"\1'aujourd'hui"),
    (re.compile(r"\b([dD])\s+au\s*jourd['’]?\s*hui\b", re.IGNORECASE), r"\1'aujourd'hui"),
    (re.compile(r"['’]?\bau\s*jourd['’]?\s*hui\b", re.IGNORECASE), "aujourd'hui"),
    (re.compile(r"\baujourd\s*['’]?\s*hui\b", re.IGNORECASE), "aujourd'hui"),
    (re.compile(r"(^|\s)['’]\s*(aujourd'hui|d'aujourd'hui)\b", re.IGNORECASE), r"\1\2"),
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

_PROMPT_LEAK_WORDS = {
    "transcription", "transcription.", "transcription...", "transcription :",
    "transcription fidèle", "transcription fidele", "transcription automatique",
    "transcription whisper", "transcription whisperx", "bande rythmo", "doublage",
    "conserver impérativement", "bégaiements exacts", "sous-titres", "sous-titrage",
    "sous-titres réalisés", "amara.org"
}
_PROMPT_LEAK_PREFIX_RE = re.compile(
    r"^(?:transcription(?:\s+(?:fid[eèé]le|automatique|whisperx?))?|bande\s+rythmo|doublage|sous-titres?(?:\s+r[eé]alis[eé]s?.*)?)\s*[:\.\-–—]?\s*",
    re.IGNORECASE
)

def clean_text(raw: str, lang: str = "fr") -> str:
    """
    Nettoie et formate le texte transcrit pour une lisibilité maximale
    sur la bande rythmo (sans artéfacts, avec accents et ponctuation propre).
    """
    text = raw.strip()
    if not text:
        return ""

    # 1. Supprimer les annotations parasites entre crochets/parenthèses [Musique], (Rires), etc.
    text = _BRACKET_RE.sub("", text).strip()

    # 1b. Éliminer toute fuite accidentelle de métadonnées ou de consignes de prompt
    text = _PROMPT_LEAK_PREFIX_RE.sub("", text).strip()
    norm_leak = re.sub(r"[^\w\s]", "", text).lower().strip()
    if norm_leak in _PROMPT_LEAK_WORDS or norm_leak == "transcription" or "amaraorg" in re.sub(r"[^\w]", "", text).lower():
        return ""
    for leak in ["transcription fidèle", "conserver impérativement", "bégaiements exacts", "sous-titres réalisés par", "amara.org"]:
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

        # Raccordement des traits d'union français (qu'est-ce, est-ce, dis-moi, peut-être...)
        text = re.sub(r"\s+-\s*(ce|tu|il|elle|ils|elles|on|vous|nous|je|moi|toi|lui|leur|y|en)\b", r"-\1", text, flags=re.IGNORECASE)
        text = re.sub(r"\b(qu['’]est|est)\s*-\s*ce\b", r"\1-ce", text, flags=re.IGNORECASE)
        text = re.sub(r"\b(peut)\s*-\s*(être)\b", r"\1-\2", text, flags=re.IGNORECASE)
        text = re.sub(r"\b(rendez)\s*-\s*(vous)\b", r"\1-\2", text, flags=re.IGNORECASE)
        text = re.sub(r"\b(celui|celle|ceux|celles)\s*-\s*(ci|là)\b", r"\1-\2", text, flags=re.IGNORECASE)
        text = re.sub(r"\b(dis|dites)\s*-\s*(moi|lui|leur|nous)\b", r"\1-\2", text, flags=re.IGNORECASE)
        text = re.sub(r"\b(vas|allez)\s*-\s*(y)\b", r"\1-\2", text, flags=re.IGNORECASE)

        # Typographie française : un espace propre avant ? ! : ;
        text = re.sub(r"\s*([?!:;])", r" \1", text)
        # Pas d'espace avant virgule et point
        text = re.sub(r"\s*([,.])", r"\1", text)
        # Nettoyage des apostrophes isolées en début de mot (ex: 'aujourd ou 'hui)
        text = re.sub(r"(^|\s)['’](\w+)", r"\1\2", text)

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
    dans le texte de la réplique, de manière insensible à la casse et robuste aux contractions
    et à la ponctuation normalisée.
    """
    phrase_lower = phrase_text.lower()
    word_spans = []
    search_idx = 0
    for w in words:
        w_txt = w.get("word", "").strip()
        core = re.sub(r"^[^\w'’-]+|[^\w'’-]+$", "", w_txt).lower()
        if not core:
            continue
        m = None
        # 1. Correspondance exacte tenant compte des frontières de mot
        pattern = re.compile(r"(?:^|\b|\s)" + re.escape(core) + r"(?:\b|\s|[.,;:!?'’…\-]|$)", re.IGNORECASE)
        m = pattern.search(phrase_lower, search_idx)
        if m:
            start_pos = m.start()
            while start_pos < len(phrase_text) and phrase_text[start_pos] in " \t\n\r":
                start_pos += 1
            end_pos = start_pos + len(core)
            while end_pos < len(phrase_text) and phrase_text[end_pos] in ('.', ',', '…', ';', '!', '?', ':', '-'):
                end_pos += 1
            if end_pos < len(phrase_text) and phrase_text[end_pos] == " ":
                end_pos += 1
            word_spans.append((start_pos, end_pos, w))
            search_idx = end_pos
            continue
        # 2. Recherche par sous-chaîne directe en repli
        idx = phrase_lower.find(core, search_idx)
        if idx != -1:
            start_pos = idx
            end_pos = idx + len(core)
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
    1. Pour les mots parasites/hésitations prolongés (dur >= 0.30s) ou mots étirés (dur >= 0.50s).
       Pose un séparateur au début ET à la fin du mot pour qu'il soit étendu fidèlement.
    2. Pour les pauses internes audibles (gap >= 0.12s) ou après ponctuation (virgule, points de suspension, tiret).
    3. Pour les variations de débit (tempo drift >= 0.15s) afin que chaque mot soit calé précisément sur la tête de lecture.
    Garantit que la coupe tombe toujours sur une frontière de mot propre, sans jamais déchirer un mot ou une contraction.
    """
    if not words or len(words) < 2 or not phrase_text:
        return []

    word_spans = align_words_to_text(phrase_text, words)
    valid_spans = [s for s in word_spans if s[0] < s[1]]
    if len(valid_spans) < max(1, len(words) // 2):
        return []

    separators = []
    p_len = len(phrase_text)
    phrase_start = float(words[0].get("start", 0.0))
    phrase_end = float(words[-1].get("end", phrase_start + 1.0))
    phrase_dur = max(0.1, phrase_end - phrase_start)

    def add_sep(t, s_idx):
        if s_idx <= 2 or s_idx >= p_len - 2:
            return
        # Garantir que s_idx est sur une frontière de mot (après un espace ou une ponctuation)
        if s_idx < p_len and phrase_text[s_idx] not in (" ", "'", "’", "-", ",", ";", ":", ".", "!", "?"):
            left_space = phrase_text.rfind(" ", 0, s_idx)
            right_space = phrase_text.find(" ", s_idx)
            if left_space != -1 and (s_idx - left_space) <= 4:
                s_idx = left_space + 1
            elif right_space != -1 and (right_space - s_idx) <= 4:
                s_idx = right_space + 1

        # Ne jamais scinder à l'intérieur d'une contraction avec apostrophe ou d'un trait d'union
        if s_idx > 0 and s_idx < p_len:
            if phrase_text[s_idx - 1] in ("'", "’", "-") or phrase_text[s_idx] in ("'", "’", "-"):
                return

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
            or w_clean in {"comme", "commme", "mais", "maiiis", "genre", "voilà", "enfin", "alors"}
            or bool(re.search(r"[.…]{2,}$", w_curr.get("word", "")))
        )
        is_never_prolonged = w_clean in NEVER_PROLONGED
        is_prolonged = (not is_never_prolonged) and (
            (is_filler and dur >= 0.30) or (dur >= 0.50)
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
                w_raw = w_curr.get("word", "")
                is_punct = any(w_raw.endswith(p) for p in [",", ";", "…", "...", ":", "—"])
                next_clean = re.sub(r"^[^\w]+|[^\w]+$", "", word_spans[i + 1][2].get("word", "")).lower()
                is_repetition = (w_clean == next_clean and len(w_clean) >= 2)

                # Écart de débit : si l'interpolation linéaire de caractères dévie de plus de 150ms du timing réel
                linear_time = phrase_start + (end_pos / max(1, p_len)) * phrase_dur
                tempo_drift = abs(next_start - linear_time)

                if gap >= 0.12 or (is_punct and gap >= 0.04) or is_repetition or (gap >= 0.02 and tempo_drift >= 0.15):
                    t_mid = round((w_end + next_start) / 2.0, 3) if gap > 0 else next_start
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
                # Si l'un des deux mots consécutifs identiques est un micro-fragment (< 0.04s)
                # et qu'ils sont contigus (< 0.08s), c'est une fausse répétition (artéfact acoustique/token)
                if min(dur1, dur2) < 0.04 and gap < 0.08:
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


def merge_split_french_words(words: list) -> list:
    """
    Rassemble les mots composés français et les contractions avec apostrophes
    qui ont été découpés par inadvertance en plusieurs tokens par Whisper.
    Exemples :
    - 'aujourd' + ''hui,' -> 'aujourd'hui,'
    - 'd'' + 'accord' -> 'd'accord'
    - 'c'' + 'est' -> 'c'est'
    - 'l'' + 'on' -> 'l'on'
    - 'qu'' + 'il' -> 'qu'il'
    - 'j'' + 'ai' -> 'j'ai'
    """
    if not words or len(words) < 2:
        return words
    changed = True
    passes = 0
    curr_words = words
    while changed and passes < 3:
        changed = False
        passes += 1
        merged = []
        i = 0
        while i < len(curr_words):
            w = dict(curr_words[i])
            if i < len(curr_words) - 1:
                w_next = dict(curr_words[i + 1])
                w1_txt = w.get("word", "").strip()
                w2_txt = w_next.get("word", "").strip()
                w1_clean = re.sub(r"[^\w]", "", w1_txt).lower()
                w2_clean = re.sub(r"[^\w]", "", w2_txt).lower()

                # 1. aujourd + hui (ou d'aujourd + hui)
                if (w1_clean == "aujourd" or w1_clean.endswith("aujourd")) and (w2_clean.startswith("hui") or w2_clean == "hui"):
                    punct = re.sub(r"^['’\w]+", "", w2_txt)
                    prefix = "d'" if (w1_clean.startswith("d") or w1_txt.lower().startswith("d'")) else ""
                    combined_word = f"{prefix}aujourd'hui{punct}"
                    merged.append({
                        "word": combined_word,
                        "start": min(float(w.get("start", 0.0)), float(w_next.get("start", 0.0))),
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0)))
                    })
                    changed = True
                    i += 2
                    continue

                # 2. Contractions françaises avec élision (c', d', l', j', m', t', s', n', qu', etc.)
                elision_match = re.match(r"^([cCdDjJlLmMntTsqQ]|qu|Qu|jusqu|lorsqu|puisqu|presqu|quelqu)['’]$", w1_txt)
                if elision_match:
                    combined_word = f"{w1_txt}{w2_txt.lstrip('\'’')}"
                    merged.append({
                        "word": combined_word,
                        "start": min(float(w.get("start", 0.0)), float(w_next.get("start", 0.0))),
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0)))
                    })
                    changed = True
                    i += 2
                    continue

                # 3. Mot débutant par une apostrophe collée au mot précédent (ex: "d" + "'accord" ou "c" + "'est")
                if (w2_txt.startswith("'") or w2_txt.startswith("’")) and w1_clean in {"c", "d", "l", "j", "m", "t", "s", "n", "qu"}:
                    combined_word = f"{w1_clean}'{w2_txt.lstrip('\'’')}"
                    merged.append({
                        "word": combined_word,
                        "start": min(float(w.get("start", 0.0)), float(w_next.get("start", 0.0))),
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0)))
                    })
                    changed = True
                    i += 2
                    continue

                # 4. Trait d'union coupé en deux tokens (ex: "est-" + "ce", "peut-" + "être", "rendez-" + "vous", etc.)
                if w1_txt.endswith("-") or w2_txt.startswith("-"):
                    combined_word = f"{w1_txt.rstrip('-')}-{w2_txt.lstrip('-')}"
                    merged.append({
                        "word": combined_word,
                        "start": min(float(w.get("start", 0.0)), float(w_next.get("start", 0.0))),
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0)))
                    })
                    changed = True
                    i += 2
                    continue

                # 5. Inversion interrogative / mots composés fréquents sans trait d'union explicite dans Whisper
                gap = float(w_next.get("start", 0.0)) - float(w.get("end", 0.0))
                if gap < 0.20:
                    if (w1_clean in {"est", "quest"} and w2_clean == "ce") or \
                       (w1_clean == "peut" and w2_clean in {"être", "etre"}) or \
                       (w1_clean == "rendez" and w2_clean == "vous") or \
                       (w1_clean in {"vas", "va"} and w2_clean == "y"):
                        punct = re.sub(r"^['’\w]+", "", w2_txt)
                        combined_word = f"{w1_txt}-{w2_clean}{punct}"
                        merged.append({
                            "word": combined_word,
                            "start": min(float(w.get("start", 0.0)), float(w_next.get("start", 0.0))),
                            "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0)))
                        })
                        changed = True
                        i += 2
                        continue

            # Nettoyer une apostrophe isolée en tête d'un mot qui n'est pas une élision (ex: "'aujourd" -> "aujourd")
            w_txt = w.get("word", "")
            if (w_txt.startswith("'") or w_txt.startswith("’")) and not any(w_txt.lower().startswith(p) for p in ["c'", "d'", "l'", "j'", "m'", "t'", "s'", "n'", "qu'"]):
                w["word"] = w_txt.lstrip("'’")

            merged.append(w)
            i += 1
        curr_words = merged
    return curr_words


def segment_words_into_clean_phrases(words: list, silence_intervals: list = None, pause_threshold: float = 0.38, lang: str = "fr", audio_data=None, sr: int = 16000) -> list:
    """
    Découpe les mots en répliques naturelles et complètes pour la bande rythmo :
    - Pause de respiration naturelle (>= 0.38s).
    - Silences acoustiques détectés dans le signal audio (>= 0.20s).
    - Ponctuation forte (?, !, ., …, ...) avec pause réelle (>= 0.22s).
    - Ponctuation intermédiaire (virgule, point-virgule) avec pause nette (>= 0.25s).
    - Mots déclencheurs (et, mais, on, je, etc.) après pause nette (>= 0.25s).
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

        w["start"] = round(w_start, 2)
        w["end"] = round(w_end, 2)

        if not current_words:
            phrase_start = w_start
            current_words.append(w)
            continue

        prev_end = float(current_words[-1].get("end", 0.0))
        gap = w_start - prev_end
        phrase_duration = prev_end - phrase_start
        prev_word_text = current_words[-1].get("word", "").strip()

        # 1. Seuil de pause conversationnel (>= 0.38s est une vraie pause / respiration entre répliques)
        is_pause = (gap >= pause_threshold)

        # 2. Ponctuation forte (?, !, ., …, ...) avec pause réelle (>= 0.22s)
        is_strong_punct = any(prev_word_text.endswith(p) for p in ["?", "!", ".", "…", "..."])
        split_strong_punct = is_strong_punct and (gap >= 0.22)

        # 3. Ponctuation intermédiaire (virgule, point-virgule) avec pause nette (>= 0.25s)
        is_comma = any(prev_word_text.endswith(p) for p in [",", ";"])
        split_comma = is_comma and (gap >= 0.25)

        # 4. Check acoustique : vérifier si un temps mort ou silence net >= 0.20s est présent entre les mots
        has_acoustic_silence = False
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start >= (prev_end - 0.05) and s_end <= (w_start + 0.05):
                    if (s_end - s_start) >= 0.20:
                        has_acoustic_silence = True
                        break

        # 5. Mots déclencheurs après pause nette (>= 0.25s)
        w_lower = w_text.lower().strip()
        is_starter = w_lower in {"et", "mais", "alors", "donc", "je", "on", "il", "elle", "ils", "comme"}
        split_starter = is_starter and (gap >= 0.25)

        # 6. Limites de durée de phrase pour le doublage (éviter les répliques démesurées > 6.5s)
        split_long_phrase = (phrase_duration >= 6.5 and gap >= 0.20)

        should_split = is_pause or \
                       has_acoustic_silence or \
                       split_strong_punct or \
                       split_comma or \
                       split_starter or \
                       split_long_phrase

        # Répétition mot à mot (ex: comme... comme, on a, on a, qu'on va, qu'on va) : ne jamais couper en deux répliques distinctes
        is_word_repetition = (norm_word(w_text) == norm_word(prev_word_text) and len(norm_word(w_text)) >= 2)
        if is_word_repetition and gap < 0.65 and (phrase_duration + gap) <= 6.5:
            should_split = False

        # Règle absolue de doublage : NE JAMAIS couper au milieu d'un mot composé, d'une apostrophe ou d'une contraction française !
        # Ex: "aujourd'hui" ("aujourd" / "hui"), "d'accord" ("d'" / "accord"), "c'est", "l'on", "qu'il", etc.
        p_norm = norm_word(prev_word_text)
        w_norm = norm_word(w_text)
        if (p_norm.endswith("aujourd") and w_norm.startswith("hui")) or \
           (prev_word_text.endswith("'") or prev_word_text.endswith("’")) or \
           (w_text.startswith("'") or w_text.startswith("’")) or \
           (p_norm in {"c", "d", "l", "j", "m", "t", "s", "n", "qu", "jusqu", "lorsqu", "puisqu", "presqu", "quelqu"}):
            should_split = False

        if should_split:

            phrase_end = prev_end
            if current_words:
                phrase_start = float(current_words[0].get("start", phrase_start))
                phrase_end = float(current_words[-1].get("end", phrase_end))
            current_words[0]["start"] = round(float(current_words[0].get("start", phrase_start)), 2)
            current_words[-1]["end"] = round(float(current_words[-1].get("end", phrase_end)), 2)

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
        current_words[0]["start"] = round(float(current_words[0].get("start", phrase_start)), 2)
        current_words[-1]["end"] = round(float(current_words[-1].get("end", phrase_end)), 2)

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

def extract_advanced_vocal_profile(y, target_sr=16000, n_fft=512, hop_len=160, n_mels=40, n_mfcc=20):
    """
    Extrait l'empreinte vocale biométrique haute fidélité (indépendante du pitch F0) :
    1. Résonances fréquentielles sous-bandes (6 bandes critiques du conduit vocal)
    2. Contraste spectral harmonique (qualité de vibration des cordes vocales)
    3. 19 coefficients MFCCs avec CMS (Cepstral Mean Subtraction, invariant au microphone)
    4. Dynamique spectrale temporelle (écart-type et delta-MFCCs d'articulation)
    5. Descripteurs spectraux globaux (centroïde, rolloff 85%, platitude spectrale de Wiener)
    6. Hauteur F0 auxiliaire douce (ne servant qu'à 12% max du score global).
    """
    import numpy as np
    import scipy.signal as signal
    try:
        from scipy.fft import dct
    except Exception:
        dct = None

    if len(y) < n_fft:
        y = np.pad(y, (0, n_fft - len(y)))

    num_frames = max(1, 1 + (len(y) - n_fft) // hop_len)
    frames = np.lib.stride_tricks.as_strided(
        y,
        shape=(num_frames, n_fft),
        strides=(y.strides[0] * hop_len, y.strides[0])
    )

    # Filtrage strict des trames silencieuses pour ne garder que la voix active
    frame_energies = np.sum(frames ** 2, axis=1)
    max_e = float(np.max(frame_energies)) if len(frame_energies) > 0 else 0.0
    active_mask = frame_energies > max(1e-5, 0.04 * max_e)
    if np.sum(active_mask) >= 2:
        frames = frames[active_mask]

    window = np.hanning(n_fft)
    spec = np.abs(np.fft.rfft(frames * window, n=n_fft))
    power_spec = spec ** 2

    # 1. Énergies et contrastes sous-bandes (conduit vocal & résonances anatomiques)
    freqs = np.fft.rfftfreq(n_fft, 1.0 / target_sr)
    band_edges = [50, 300, 800, 1800, 3200, 5500, 8000]
    subband_energies = []
    subband_contrasts = []
    for b in range(len(band_edges) - 1):
        idx = np.where((freqs >= band_edges[b]) & (freqs < band_edges[b + 1]))[0]
        if len(idx) > 0:
            b_spec = spec[:, idx]
            b_mean = np.mean(b_spec, axis=1) + 1e-9
            b_peak = np.percentile(b_spec, 90, axis=1) + 1e-9
            b_valley = np.percentile(b_spec, 10, axis=1) + 1e-9
            subband_energies.append(float(np.mean(np.log1p(b_mean))))
            subband_contrasts.append(float(np.mean(np.log1p(b_peak / b_valley))))
        else:
            subband_energies.append(0.0)
            subband_contrasts.append(0.0)

    subband_energies = np.array(subband_energies, dtype=np.float32)
    se_norm = subband_energies / (np.linalg.norm(subband_energies) + 1e-9)
    subband_contrasts = np.array(subband_contrasts, dtype=np.float32)
    sc_norm = subband_contrasts / (np.linalg.norm(subband_contrasts) + 1e-9)

    # 2. MFCCs avec CMS (Cepstral Mean Subtraction pour éliminer l'effet du microphone)
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

    mel_energies = np.dot(power_spec, fbank.T)
    mel_energies = np.maximum(mel_energies, 1e-10)
    log_mel = np.log(mel_energies)

    if dct is not None:
        mfcc = dct(log_mel, type=2, axis=-1, norm="ortho")[:, :n_mfcc]
    else:
        k_idx = np.arange(n_mfcc)[:, None]
        n_idx = np.arange(n_mels)
        dct_mat = np.cos(np.pi / n_mels * (n_idx + 0.5) * k_idx)
        mfcc = np.dot(log_mel, dct_mat.T)

    # CMS : suppression de l'empreinte du micro
    mfcc_cms = mfcc - np.mean(mfcc, axis=0, keepdims=True)
    # On retient les coefficients 1 à n_mfcc-1 (hors énergie globale MFCC 0)
    mfcc_active = mfcc_cms[:, 1:]
    mfcc_mean = np.mean(mfcc_active, axis=0)
    mfcc_std = np.std(mfcc_active, axis=0) if len(mfcc_active) > 1 else np.zeros(n_mfcc - 1)
    mfcc_delta = np.mean(np.abs(np.diff(mfcc_active, axis=0)), axis=0) if len(mfcc_active) > 2 else np.zeros(n_mfcc - 1)

    # 3. Descripteurs spectraux globaux
    avg_spec = np.mean(spec, axis=0) + 1e-9
    spec_sum = np.sum(avg_spec)
    centroid = float(np.sum(freqs * avg_spec) / spec_sum) / 4000.0
    cum_spec = np.cumsum(avg_spec)
    rolloff85 = float(freqs[min(np.searchsorted(cum_spec, 0.85 * spec_sum), len(freqs) - 1)]) / 4000.0
    geom_mean = np.exp(np.mean(np.log(avg_spec + 1e-12)))
    arith_mean = np.mean(avg_spec)
    flatness = float(geom_mean / (arith_mean + 1e-12))
    shape_feats = np.array([centroid, rolloff85, flatness], dtype=np.float32)

    # Assemblage de l'empreinte biométrique complète
    vocal_vec = np.hstack([
        se_norm * 2.0,           # 6 valeurs : résonance sous-bandes
        sc_norm * 1.5,           # 6 valeurs : contraste spectral
        mfcc_mean * 1.5,         # 19 valeurs : enveloppe spectrale CMS
        mfcc_std * 0.8,          # 19 valeurs : modulation spectrale
        mfcc_delta * 0.8,        # 19 valeurs : vitesse d'articulation
        shape_feats * 1.2        # 3 valeurs : brillance & platitude
    ])
    vocal_vec_norm = vocal_vec / (np.linalg.norm(vocal_vec) + 1e-9)

    # 4. Extraction pitch F0 (auxiliaire)
    sos_pitch = signal.butter(4, [65, 450], btype="bandpass", fs=target_sr, output="sos")
    y_filt = signal.sosfilt(sos_pitch, y)
    min_lag = int(target_sr / 450)
    max_lag = int(target_sr / 65)
    pitches = []
    for i in range(0, len(y_filt) - int(target_sr * 0.03), int(target_sr * 0.015)):
        fr = y_filt[i:i + int(target_sr * 0.03)]
        corr = signal.correlate(fr, fr, mode="full")[len(fr) - 1:]
        if len(corr) > max_lag:
            pk = min_lag + int(np.argmax(corr[min_lag:max_lag]))
            if corr[0] > 1e-5 and (corr[pk] / corr[0]) > 0.38:
                f0 = target_sr / max(1.0, float(pk))
                if 65 <= f0 <= 450:
                    pitches.append(f0)
    has_pitch = len(pitches) >= 3
    med_pitch = float(np.median(pitches)) if has_pitch else 0.0

    return {
        "timbre": vocal_vec_norm,
        "pitch": med_pitch,
        "has_pitch": has_pitch,
        "pitch_semitones": (12.0 * np.log2(med_pitch / 65.0)) if has_pitch else 0.0
    }



# =========================================================================
# DIARISATION VOCALE SUR PHRASES COMPLÈTES (PITCH F0 + MFCCs + CLUSTERING)
# =========================================================================

def diarize_clean_phrases(audio_path: str, phrases: list, num_speakers: int = 0) -> list:
    """
    Attribue un locuteur distinct (SPEAKER_00, SPEAKER_01...) à chaque phrase
    complète par analyse acoustique avancée :
    - Hauteur fondamentale de la voix (Pitch F0 via corrélation croisée robuste 65-450 Hz)
    - Empreinte spectrale active (MFCCs normalisés + centroïde + brillance sans biais de silence)
    - Matrice de distance combinée (timbre cosinus + hauteur musicale sans pénaliser les phrases sourdes)
    - Classification hiérarchique agglomérative (AHC) avec fusion des clusters acoustiquement proches
    - Re-rattachement des répliques courtes/ambiguës au personnage le plus proche.
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
        from sklearn.cluster import AgglomerativeClustering

        target_sr = 16000
        if sr != target_sr and len(audio_data) > 0:
            num_samples = int(len(audio_data) * target_sr / sr)
            audio_data = signal.resample(audio_data, num_samples).astype(np.float32)
            sr = target_sr

        frame_len = int(target_sr * 0.030)  # 30 ms
        phrase_features = []

        for p in phrases:
            s_idx = max(0, int(p["start"] * sr))
            e_idx = min(len(audio_data), int(p["end"] * sr))
            y = audio_data[s_idx:e_idx] if e_idx > s_idx else np.zeros(frame_len, dtype=np.float32)
            if len(y) < frame_len:
                y = np.pad(y, (0, frame_len - len(y)))

            prof = extract_advanced_vocal_profile(y, target_sr=target_sr)
            prof["duration"] = p["end"] - p["start"]
            phrase_features.append(prof)

        n_p = len(phrases)
        dist_matrix = np.zeros((n_p, n_p), dtype=np.float32)

        for i in range(n_p):
            for j in range(i + 1, n_p):
                f_i = phrase_features[i]
                f_j = phrase_features[j]
                dot_val = np.clip(float(np.dot(f_i["timbre"], f_j["timbre"])), -1.0, 1.0)
                d_timbre = max(0.0, 1.0 - dot_val)
                if f_i["has_pitch"] and f_j["has_pitch"]:
                    d_pitch = min(1.0, abs(f_i["pitch_semitones"] - f_j["pitch_semitones"]) / 12.0)
                    dist = 0.88 * d_timbre + 0.12 * d_pitch
                else:
                    dist = d_timbre
                dist_matrix[i, j] = dist
                dist_matrix[j, i] = dist

        # Détermination du nombre de locuteurs
        if num_speakers > 1:
            target_k = min(num_speakers, n_p)
            clusterer = AgglomerativeClustering(n_clusters=target_k, metric="precomputed", linkage="average")
            raw_labels = list(clusterer.fit_predict(dist_matrix))
            print_info(f"Diarisation vocale : {target_k} personnage(s) assigné(s) selon consigne utilisateur.")
        else:
            from sklearn.metrics import silhouette_score
            best_k = 1
            best_labels = [0] * n_p
            best_score = -1.0

            if n_p >= 2:
                max_k = min(5, max(2, n_p // 2))
                for k in range(2, max_k + 1):
                    cl = AgglomerativeClustering(n_clusters=k, metric="precomputed", linkage="average")
                    lbls = list(cl.fit_predict(dist_matrix))

                    counts = [lbls.count(c) for c in set(lbls)]
                    min_cluster_size = min(counts)
                    if min_cluster_size < 1 or (n_p >= 8 and min_cluster_size < 2 and (min_cluster_size / n_p) < 0.05):
                        continue

                    centroids = []
                    for c in set(lbls):
                        c_idx = [idx for idx, l in enumerate(lbls) if l == c]
                        c_mean = np.mean([phrase_features[idx]["timbre"] for idx in c_idx], axis=0)
                        c_mean /= (np.linalg.norm(c_mean) + 1e-9)
                        centroids.append(c_mean)

                    min_c_dist = 999.0
                    for ca_idx in range(len(centroids)):
                        for cb_idx in range(ca_idx + 1, len(centroids)):
                            c_dist = max(0.0, float(1.0 - np.dot(centroids[ca_idx], centroids[cb_idx])))
                            min_c_dist = min(min_c_dist, c_dist)

                    try:
                        sil = float(silhouette_score(dist_matrix, lbls, metric="precomputed"))
                    except Exception:
                        sil = 0.0

                    # Un partitionnement est valide dès lors que la silhouette est positive et bien détachée (>= 0.15)
                    # et que les centroïdes ne sont pas superposés (> 0.008)
                    if sil >= 0.15 and min_c_dist > 0.008:
                        combined_eval = sil + min_c_dist * 3.0
                        if combined_eval > best_score:
                            best_score = combined_eval
                            best_k = k
                            best_labels = lbls

            if best_k <= 1:
                for p in phrases:
                    p["speaker"] = "SPEAKER_00"
                print_info("Auto-détection vocale : 1 personnage détecté.")
                return phrases

            raw_labels = best_labels
            print_info(f"Auto-détection vocale : {best_k} personnages distincts identifiés (séparation biométrique validée).")

        # Re-rattachement des micro-répliques très courtes (< 0.4s) au centroïde de timbre le plus proche
        active_clusters = sorted(list(set(raw_labels)))
        cluster_centroids = {}
        for c in active_clusters:
            c_idxes = [i for i, l in enumerate(raw_labels) if l == c]
            c_t = np.mean([phrase_features[i]["timbre"] for i in c_idxes], axis=0)
            c_t /= (np.linalg.norm(c_t) + 1e-9)
            cluster_centroids[c] = c_t

        for i in range(n_p):
            if phrase_features[i]["duration"] < 0.4:
                best_c = raw_labels[i]
                best_sim = -1.0
                for c, c_vec in cluster_centroids.items():
                    sim = float(np.dot(phrase_features[i]["timbre"], c_vec))
                    if sim > best_sim:
                        best_sim = sim
                        best_c = c
                raw_labels[i] = best_c

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

            prev_p = new_phrases[-1] if len(new_phrases) > 0 else None
            if prev_p is not None:
                prev_text_raw = prev_p.get("text", "").strip()
                p_text_raw = p.get("text", "").strip()
                prev_norm = norm_word(prev_text_raw)
                p_norm = norm_word(p_text_raw)

                # Règle absolue 1 : Reconnexion de 'aujourd'hui' fragmenté accidentellement
                # (ex: '... mauvaises aujourd' + 'hui,' ou '... mauvaises \'aujourd' + '\'hui,')
                if prev_norm.endswith("aujourd") and (p_norm.startswith("hui") or p_norm == "hui"):
                    punct = re.sub(r"^['’\w]+", "", p_text_raw)
                    prev_p["text"] = re.sub(r"['’]?\baujourd\b", "aujourd'hui", prev_p["text"], flags=re.IGNORECASE)
                    if punct and not prev_p["text"].endswith(punct):
                        prev_p["text"] += punct
                    prev_p["end"] = max(prev_p["end"], p["end"])
                    prev_p["words"] = prev_p.get("words", []) + p_words
                    changed = True
                    i += 1
                    continue

                # Règle absolue 2 : Reconnexion des contractions avec élision (c', d', l', j', etc.)
                if re.search(r"\b([cCdDjJlLmMntTsqQ]|qu|Qu)['’]$", prev_text_raw):
                    clean_curr = p_text_raw.lstrip("'’")
                    prev_p["text"] = f"{prev_text_raw}{clean_curr}"
                    prev_p["end"] = max(prev_p["end"], p["end"])
                    prev_p["words"] = prev_p.get("words", []) + p_words
                    changed = True
                    i += 1
                    continue

            is_single_word = len(p_words) == 1
            is_connector = p_text in ORPHAN_CONNECTORS or (len(p_words) == 1 and norm_word(p_words[0].get("word", "")) in ORPHAN_CONNECTORS)
            is_short = (p["end"] - p["start"]) < 0.60

            if is_single_word and is_connector and is_short:
                has_prev = len(new_phrases) > 0
                has_next = (i < len(phrases) - 1)
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

_KNOWN_HALLUCINATIONS = (
    "merci d'avoir regardé", "merci davoir regardé", "sous-titrage", "sous-titres", "sous titres",
    "abonnez-vous", "abonnez vous", "à la prochaine", "thanks for watching", "thank you for watching",
    "please subscribe", "subtitles by", "amara.org", "www.",
)


def is_known_hallucination(text: str, prompt: str = None) -> bool:
    """Détecte les hallucinations typiques de Whisper (outros YouTube, sous-titrage, fuite du prompt)."""
    t = (text or "").strip().lower()
    if not t:
        return True
    if any(h in t for h in _KNOWN_HALLUCINATIONS):
        return True
    if prompt:
        norm_t = re.sub(r"[^\w]", "", t)
        norm_p = re.sub(r"[^\w]", "", prompt.lower())
        if len(norm_t) >= 12 and norm_t in norm_p:
            return True
    return False


def find_uncovered_speech_regions(audio, sr: int, words: list, min_len: float = 0.25, margin: float = 0.12) -> list:
    """
    Repère les zones où il y a clairement du signal vocal (énergie nettement au-dessus du bruit de fond)
    mais où aucun mot n'a été transcrit : typiquement rires, interjections ('nannn'), mots déformés
    que le VAD ou Whisper ont ignorés. Retourne [(start_sec, end_sec), ...].
    """
    if audio is None or len(audio) < sr or sr <= 0:
        return []
    frame = int(sr * 0.03)
    hop = int(sr * 0.015)
    n = 1 + (len(audio) - frame) // hop
    if n < 4:
        return []
    # RMS vectorisé
    idx = np.arange(frame)[None, :] + (np.arange(n) * hop)[:, None]
    rms = np.sqrt(np.mean(audio[idx] ** 2, axis=1))
    floor = float(np.percentile(rms, 20))
    peak = float(np.percentile(rms, 95))
    if peak < 0.01:
        return []
    thresh = max(0.012, floor + 0.20 * (peak - floor))
    active = rms > thresh

    # Couverture par les mots déjà transcrits
    covered = np.zeros(n, dtype=bool)
    for w in words:
        s = int(max(0.0, float(w["start"]) - margin) * sr / hop)
        e = int((float(w["end"]) + margin) * sr / hop) + 1
        covered[max(0, s):min(n, e)] = True

    todo = active & ~covered
    regions = []
    start = None
    gap_allow = 0
    for i in range(n):
        if todo[i]:
            if start is None:
                start = i
            gap_allow = 0
        elif start is not None:
            gap_allow += 1
            if gap_allow > 8:  # ~120 ms de trou toléré dans une même zone
                end = i - gap_allow
                if (end - start) * hop / sr >= min_len:
                    regions.append((start * hop / sr, (end + 1) * hop / sr))
                start = None
                gap_allow = 0
    if start is not None:
        end = n - 1
        if (end - start) * hop / sr >= min_len:
            regions.append((start * hop / sr, (end + 1) * hop / sr))
    return regions


def recover_missed_speech(model, audio, sr: int, words: list, lang, prompt: str = None, lang_hint: str = "fr") -> list:
    """
    Seconde passe sans VAD sur les zones vocales non transcrites pour capter les mots
    peu intelligibles (rires parlés, 'nannn', onomatopées). Retourne la liste de nouveaux mots.
    """
    regions = find_uncovered_speech_regions(audio, sr, words)
    if not regions:
        return []
    # Whisper veut du 16 kHz
    if sr != 16000:
        return []
    new_words = []
    total = len(audio) / sr
    for r_start, r_end in regions[:60]:
        pad = 0.25
        s = max(0.0, r_start - pad)
        e = min(total, r_end + pad)
        chunk = np.ascontiguousarray(audio[int(s * sr):int(e * sr)], dtype=np.float32)
        if len(chunk) < int(0.3 * sr):
            continue
        try:
            segs, _info = model.transcribe(
                chunk,
                language=lang,
                beam_size=3,
                best_of=3,
                word_timestamps=True,
                vad_filter=False,
                condition_on_previous_text=False,
                initial_prompt=prompt,
                no_speech_threshold=0.95,
                log_prob_threshold=-2.0,
                compression_ratio_threshold=2.6,
                temperature=[0.0, 0.3, 0.6],
            )
            for seg in segs:
                txt = (seg.text or "").strip()
                if getattr(seg, "no_speech_prob", 0.0) > 0.92:
                    continue
                if getattr(seg, "avg_logprob", 0.0) < -1.8:
                    continue
                cleaned = clean_text(txt, lang_hint)
                if not cleaned or is_known_hallucination(cleaned, prompt):
                    continue
                for w in (seg.words or []):
                    w_str = (w.word or "").strip()
                    if not w_str or not any(c.isalnum() for c in w_str):
                        continue
                    ws = s + float(w.start if w.start is not None else seg.start)
                    we = s + float(w.end if w.end is not None else seg.end)
                    if we <= ws + 0.001:
                        we = ws + 0.08
                    # Ne pas empiéter sur des mots déjà transcrits
                    if any(float(x["start"]) < we - 0.05 and float(x["end"]) > ws + 0.05 for x in words):
                        continue
                    new_words.append({"word": w_str, "start": ws, "end": we})
        except Exception:
            continue
    return new_words


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

    # Paramètres Silero VAD pour couper net le bruit de fond et le silence pré-vocal sans jamais tronquer les amorces douces
    vad_parameters = dict(
        threshold=0.20,
        min_speech_duration_ms=30,
        min_silence_duration_ms=400,
        speech_pad_ms=400,
    )

    # Décodage haute précision optimisé pour le doublage et la bande rythmo :
    # vad_filter=True avec Silero VAD élimine toute hallucination sur les silences ou bruits de fond
    beam_size = 4

    # Prompt informel : pousse Whisper à écrire les interjections, mots déformés, rires parlés
    # et le langage familier au lieu de les ignorer. Les fuites éventuelles sont filtrées ensuite.
    if lang_param == "fr" or lang_param is None:
        informal_prompt = "Euh, nan ! Nannnn, mais arrête, hein ? Ouais, bah, genre, han, ah ouais, mdr, wesh, putain, trop bien."
    elif lang_param == "en":
        informal_prompt = "Um, nah! Nooo, come on, huh? Yeah, well, like, ha, oh yeah, dude, damn."
    else:
        informal_prompt = None

    transcribe_kwargs = dict(
        language=lang_param,
        beam_size=beam_size,
        best_of=beam_size,
        patience=1.0,
        word_timestamps=True,
        vad_filter=True,
        vad_parameters=vad_parameters,
        condition_on_previous_text=False,
        initial_prompt=informal_prompt,
        no_speech_threshold=0.85,
        log_prob_threshold=-1.6,
        compression_ratio_threshold=2.6,
        hallucination_silence_threshold=2.0,
        temperature=[0.0, 0.2, 0.4, 0.6],
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

            # Nettoyer un éventuel préfixe parasite résiduel (ex: "Transcription : ...")
            seg_text = clean_text(seg_text, det_lang)
            if not seg_text or not any(c.isalnum() for c in seg_text):
                continue

            norm_seg = re.sub(r"[^\w]", "", seg_text).lower()
            if norm_seg in _PROMPT_LEAK_WORDS or norm_seg == "transcription" or "amaraorg" in norm_seg:
                continue
            if is_known_hallucination(seg_text, informal_prompt):
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
                        clean_w = re.sub(r"[^\w]", "", w_str).lower()
                        if clean_w in ("transcription", "transcriptionfidele", "transcriptionfidèle") or clean_w in _PROMPT_LEAK_WORDS:
                            continue
                        w_start = float(w.start) if w.start is not None else float(segment.start)
                        w_end = float(w.end) if w.end is not None else float(segment.end)
                        if w_end <= w_start + 0.001:
                            # Interjection très brève (nan, ah, hein...) : on la garde avec une durée minimale
                            w_end = w_start + 0.08
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

            seg_words = merge_split_french_words(seg_words)
            collected_words.extend(seg_words)

            # Découpage direct en phrases nettes avec détection des alternances de répliques
            live_phrases = segment_words_into_clean_phrases(
                seg_words,
                silence_intervals=silence_intervals,
                pause_threshold=0.38,
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

        # Passe de rattrapage : zones vocales ignorées (rires parlés, 'nannn', interjections)
        try:
            print_progress(88, 100, "Rattrapage des mots peu intelligibles (rires, interjections)...")
            recovered = recover_missed_speech(mdl, audio_data, audio_sr, collected_words, lang_param or det_lang,
                                              informal_prompt, det_lang)
            if recovered:
                print_info(f"Rattrapage : {len(recovered)} mot(s) supplémentaire(s) capté(s).")
                collected_words.extend(recovered)
                collected_words.sort(key=lambda x: (float(x["start"]), float(x["end"])))
        except Exception as rec_err:
            print_info(f"Rattrapage ignoré : {rec_err}")

        # Élimination des micro-dédoublements de tokens accidentels (< 0.09s)
        collected_words = deduplicate_adjacent_words(collected_words)
        collected_words = merge_split_french_words(collected_words)
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

    all_words = merge_split_french_words(all_words)
    phrases = segment_words_into_clean_phrases(
        all_words,
        silence_intervals=silence_intervals,
        pause_threshold=0.38,
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
        p_words = phrase.get("words", [])
        if p_words:
            p_start = round(float(p_words[0]["start"]), 2)
            p_end = round(max(p_start + 0.15, float(p_words[-1]["end"])), 2)
            for w in p_words:
                w["start"] = round(float(w["start"]), 2)
                w["end"] = round(float(w["end"]), 2)
        else:
            p_start = round(float(phrase["start"]), 2)
            p_end = round(max(p_start + 0.15, float(phrase["end"])), 2)

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
