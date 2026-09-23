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
        millis = 999
    return f"{hours:02}:{minutes:02}:{secs:02}.{millis:03}"


# ─────────────────────────────────────────────────────────
# Text cleanup, French phonetic corrections & typography
# ─────────────────────────────────────────────────────────

# Patterns for Whisper hallucination artefacts
_BRACKET_RE = re.compile(r"\[.*?\]|\(.*?\)")
_REPEATED_PHRASE_RE = re.compile(r"\b(\w{3,}(?:\s+\w+){0,4})\b(?:\s+\1\b)+", re.IGNORECASE)
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
    # Reconstruction des contractions françaises détachées
    (re.compile(r"\b([Tt])['’]\s*([aA]s?)\b"), r"t'as"),
    (re.compile(r"\b([Jj])['’]\s*([aA]i)\b"), r"j'ai"),
    (re.compile(r"\b([Cc])['’]\s*([eE]st)\b"), r"c'est"),
    (re.compile(r"\b([Cc])['’]\s*([eéEÉ]tait)\b"), r"c'était"),
    (re.compile(r"\b([Dd])['’]\s*accord\b", re.IGNORECASE), "d'accord"),
    (re.compile(r"\b([Qq]u)['’]\s*est[- ]ce\s+que\b", re.IGNORECASE), "qu'est-ce que"),
    (re.compile(r"\b([Qq]u)['’]\s*on\b", re.IGNORECASE), "qu'on"),
    (re.compile(r"\b([Dd])['’]\s*un\b", re.IGNORECASE), "d'un"),
    (re.compile(r"\b([Dd])['’]\s*une\b", re.IGNORECASE), "d'une"),
    (re.compile(r"\b([Ss])['’]\s*il\s+te\s+pla[iî]t\b", re.IGNORECASE), "s'il te plaît"),
    (re.compile(r"\b([Ss])['’]\s*il\s+vous\s+pla[iî]t\b", re.IGNORECASE), "s'il vous plaît"),
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

    # 2. Supprimer les répétitions consécutives (hallucinations Whisper)
    text = _REPEATED_PHRASE_RE.sub(r"\1", text)

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

    # 6. Guillemets
    text = text.replace('"', "« ").replace('"', " »").replace('"', "« ")

    # 7. Première lettre en majuscule
    if text and text[0].islower():
        text = text[0].upper() + text[1:]

    # 8. Nettoyer les espaces résiduels
    text = _MULTI_SPACE_RE.sub(" ", text).strip()

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


def detect_silence_intervals(audio, sr: int = 16000, min_silence_sec: float = 0.48, frame_ms: int = 25) -> list:
    """
    Vérifie le signal audio pour détecter toutes les fenêtres d'au moins 0.48s (~0.5s)
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
    p90 = float(np.percentile(rms, 90))
    silence_thresh = max(0.003, p10 + 0.08 * (p90 - p10))

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


def segment_words_into_clean_phrases(words: list, silence_intervals: list = None, pause_threshold: float = 0.48, lang: str = "fr") -> list:
    """
    Découpe les mots en répliques naturelles et complètes en vérifiant toutes les 0.5s si la personne parle toujours :
    - Si la personne s'arrête de parler pendant >= 0.48s (~0.5s), on coupe via un séparateur END.
    - Dès qu'elle reparle, une nouvelle réplique redémarre avec un séparateur START pour espacer le tout.
    - Détecte également les silences acoustiques directs dans le signal audio.
    - Coupe proprement après ponctuation forte (?, !, ., …, :) dès qu'il y a un intervalle >= 0.18s.
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

        if not current_words:
            phrase_start = w_start
            current_words.append(w)
            continue

        prev_end = float(current_words[-1].get("end", 0.0))
        gap = w_start - prev_end
        phrase_duration = prev_end - phrase_start
        prev_word_text = current_words[-1].get("word", "").strip()

        is_punct = any(prev_word_text.endswith(p) for p in ["?", "!", ".", "…", "...", "—", ":"])

        # Check acoustique : vérifier si un temps mort de silence de >= 0.48s est présent entre les mots
        has_acoustic_silence = False
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_end > (prev_end - 0.05) and s_start < (w_start + 0.05):
                    overlap = min(w_start, s_end) - max(prev_end, s_start)
                    if overlap >= 0.38 or (s_end - s_start) >= 0.48:
                        has_acoustic_silence = True
                        break

        should_split = (gap >= pause_threshold) or \
                       has_acoustic_silence or \
                       (is_punct and gap >= 0.18) or \
                       (phrase_duration >= 4.5 and any(prev_word_text.endswith(p) for p in [",", ";"]) and gap >= 0.20) or \
                       (phrase_duration >= 5.5 and gap >= 0.22)

        if should_split:
            phrase_end = prev_end
            raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
            cleaned = clean_text(raw_text, lang)
            if cleaned:
                start_sec = round(phrase_start, 2)
                end_sec = round(max(phrase_start + 0.25, phrase_end), 2)
                if end_sec <= start_sec:
                    end_sec = round(start_sec + 0.25, 2)

                phrases.append({
                    "start": start_sec,
                    "end": end_sec,
                    "text": cleaned,
                    "words": [
                        {
                            "word": cw.get("word", "").strip(),
                            "start": round(float(cw.get("start", 0.0)), 2),
                            "end": round(float(cw.get("end", 0.0)), 2),
                        }
                        for cw in current_words
                    ],
                })
            current_words = [w]
            phrase_start = w_start
        else:
            current_words.append(w)

    # Fermer la dernière phrase
    if current_words:
        phrase_end = float(current_words[-1].get("end", 0.0))
        raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
        cleaned = clean_text(raw_text, lang)
        if cleaned:
            start_sec = round(phrase_start, 2)
            end_sec = round(max(phrase_start + 0.25, phrase_end), 2)
            if end_sec <= start_sec:
                end_sec = round(start_sec + 0.25, 2)

            phrases.append({
                "start": start_sec,
                "end": end_sec,
                "text": cleaned,
                "words": [
                    {
                        "word": cw.get("word", "").strip(),
                        "start": round(float(cw.get("start", 0.0)), 2),
                        "end": round(float(cw.get("end", 0.0)), 2),
                    }
                    for cw in current_words
                ],
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

        for p in phrases:
            s_idx = max(0, int(p["start"] * sr))
            e_idx = min(len(audio_data), int(p["end"] * sr))
            y = audio_data[s_idx:e_idx] if e_idx > s_idx else np.zeros(frame_len, dtype=np.float32)
            if len(y) < frame_len:
                y = np.pad(y, (0, frame_len - len(y)))

            # ── Estimation fine de la hauteur fondamentale (Pitch F0) ──
            # On applique le filtre passe-bande puis l'autocorrélation temporelle
            y_filt = signal.sosfilt(sos_pitch, y)
            pitches = []
            for i in range(0, len(y_filt) - frame_len, hop_len):
                frame = y_filt[i:i + frame_len]
                energy = np.sum(frame ** 2)
                if energy < 1e-4:
                    continue  # Trame quasi silencieuse (consonne sourde ou micro-pause)
                
                # Autocorrélation directe : r(tau) = sum(x(t) * x(t + tau))
                corr = signal.correlate(frame, frame, mode="full")
                corr = corr[len(corr) // 2:]  # On ne garde que les lags positifs
                
                if len(corr) > max_lag:
                    # Le premier pic significatif après min_lag correspond à la période fondamentale T0
                    pk = min_lag + np.argmax(corr[min_lag:max_lag])
                    # Validation du pic : il doit être suffisamment proéminent (voix voisée)
                    if corr[0] > 1e-5 and (corr[pk] / corr[0]) > 0.28:
                        pitches.append(target_sr / pk)

            # Pitch médian robuste de la phrase (insensible aux fausses détections ponctuelles)
            if len(pitches) >= 2:
                med_pitch = np.median(pitches)
            elif len(pitches) == 1:
                med_pitch = pitches[0]
            else:
                med_pitch = 0.0

            # Conversion en échelle logarithmique (octaves par rapport au La-1 / 55 Hz)
            # Permet une séparation linéaire entre registres vocaux (baryton, ténor, alto, soprano)
            log_pitch = np.log2(med_pitch / 55.0) if med_pitch > 50 else 0.0

            # ── MFCCs globaux de la réplique (timbre vocal de l'acteur) ──
            mfcc_mean, mfcc_std = extract_mfcc_stats(y, target_sr=target_sr, n_mfcc=13, n_fft=512, hop_len=160, n_mels=40)

            # ── Brillance et centroïde spectral (centre de gravité fréquentiel) ──
            spec = np.abs(np.fft.rfft(y))
            freqs = np.fft.rfftfreq(len(y), 1.0 / target_sr)
            spec_sum = np.sum(spec) + 1e-9
            centroid = np.sum(freqs * spec) / spec_sum
            norm_centroid = centroid / 4000.0  # Normalisation indicative

            # Vecteur de caractéristiques pondéré :
            # - log_pitch * 6.0 : poids très fort pour séparer nettement les voix masculines et féminines
            # - mfcc_mean[:8] : enveloppe des formants bas/moyens (empreinte du conduit vocal)
            # - mfcc_std[:4] : dynamique d'articulation
            # - norm_centroid * 2.0 : clarté/brillance globale de la voix
            feat = np.hstack([
                [log_pitch * 6.0],
                mfcc_mean[:8],
                mfcc_std[:4],
                [norm_centroid * 2.0]
            ])
            features.append(feat)

        X = np.array(features)
        scaler = StandardScaler()
        X_norm = scaler.fit_transform(X)

        if num_speakers > 1:
            target_k = min(num_speakers, len(phrases))
        else:
            # Mode Auto-détection non-supervisée :
            # On teste différents nombres de locuteurs k (de 2 à max_candidates)
            # et on retient le k qui maximise le score de Silhouette (compacité intra-cluster vs distance inter-cluster).
            from sklearn.metrics import silhouette_score
            max_candidates = min(12, max(2, len(phrases) // 2))
            best_k = 1
            best_score = -1.0

            for k in range(2, max_candidates + 1):
                try:
                    cl = KMeans(n_clusters=k, random_state=42, n_init=10)
                    labels = cl.fit_predict(X_norm)
                    score = float(silhouette_score(X_norm, labels))
                    if score > best_score:
                        best_score = score
                        best_k = k
                except Exception:
                    pass

            if best_score < 0.12 or best_k < 2:
                target_k = 1
            else:
                target_k = best_k

            print_info(f"Auto-détection vocale : {target_k} personnage(s) distinct(s) identifié(s) (indice de séparation: {best_score:.2f})")

        if target_k <= 1:
            for p in phrases:
                p["speaker"] = "SPEAKER_00"
            return phrases

        clusterer = KMeans(n_clusters=target_k, random_state=42, n_init=15)
        raw_labels = clusterer.fit_predict(X_norm)

        # Réassignation chronologique (premier intervenant = SPEAKER_00)
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


def merge_consecutive_same_speaker_phrases(phrases: list, max_gap: float = 0.25, max_duration: float = 5.0, max_words: int = 16) -> list:
    """
    Fusionne uniquement les micro-fragments immédiatement contigus (< 0.25s)
    appartenant au même locuteur, sans jamais avaler les pauses ni les silences de 0.5s,
    ni les répliques terminées par une ponctuation.
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

        prev_text = prev.get("text", "").strip()
        ends_with_strong_punct = any(prev_text.endswith(pt) for pt in [".", "!", "?", "…", "...", ":"])

        prev_word_count = len(prev.get("words", []))
        is_tiny_fragment = (prev_word_count <= 2) and not ends_with_strong_punct
        allowed_gap = 0.35 if is_tiny_fragment else max_gap

        if (not ends_with_strong_punct and
            prev.get("speaker") == p.get("speaker") and
            gap < allowed_gap and
            combined_dur <= max_duration and
            combined_words <= max_words):
            prev["end"] = p["end"]
            prev["text"] = (prev["text"] + " " + p["text"]).strip()
            prev["words"] = prev.get("words", []) + p.get("words", [])
        else:
            merged.append(p)

    return merged


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

    # ── Analyse acoustique des pauses de 0.5s dans le signal audio ──
    audio_data, audio_sr = load_audio(args.audio)
    silence_intervals = detect_silence_intervals(audio_data, sr=audio_sr, min_silence_sec=0.48)
    if silence_intervals:
        print_info(f"Analyse vocale : {len(silence_intervals)} temps morts/silences (>= 0.5s) détectés pour découpage.")

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

    # Prompt initial riche en français pour orienter le modèle sur le vocabulaire et les accents
    initial_prompt_text = None
    if lang_param == "fr" or lang_param is None:
        initial_prompt_text = (
            "Transcription française fidèle, rythmée et parfaitement ponctuée pour le doublage et la bande rythmo : "
            "T'as oublié que je suis ta mère ? Mais qu'est-ce que tu racontes ! Attends... Regarde-moi. "
            "Pourquoi tu fais ça ? C'est pas possible ! D'accord, je comprends, mais s'il te plaît, écoute-moi. "
            "Yo les gens, salut à tous, salam aleykoum, frères et sœurs, la tête de oim, la tête de oit, j'ai juré. "
            "Wesh, wallah, en sah, de ouf, frérot, t'inquiète, vas-y, c'est parti, oui, non, merci, absolument."
        )

    # Décodage haute précision optimisé pour le doublage et la bande rythmo
    # beam_size 2 sur CPU (très rapide, 3x plus rapide que beam 5) ou 3 sur GPU
    beam_size = 2

    transcribe_kwargs = dict(
        language=lang_param,
        beam_size=beam_size,
        best_of=beam_size,
        patience=1.0,
        word_timestamps=True,
        vad_filter=True,
        vad_parameters={
            "threshold": 0.35,
            "min_speech_duration_ms": 80,
            "max_speech_duration_s": 30,
            "min_silence_duration_ms": 350,
            "speech_pad_ms": 350,
        },
        condition_on_previous_text=False,
        initial_prompt=initial_prompt_text,
        no_speech_threshold=0.65,
        log_prob_threshold=-1.0,
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
            kwargs["beam_size"] = 3
            kwargs["best_of"] = 3
        else:
            kwargs["beam_size"] = 1 if model_name in ("tiny", "base") else 2
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
            if not seg_text:
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
                    if w_str:
                        w_start = float(w.start) if w.start is not None else float(segment.start)
                        w_end = float(w.end) if w.end is not None else float(segment.end)
                        seg_words.append({
                            "word": w_str,
                            "start": w_start,
                            "end": w_end,
                        })
            else:
                tokens = seg_text.split()
                if tokens:
                    seg_dur = max(0.1, float(segment.end) - float(segment.start))
                    step = seg_dur / len(tokens)
                    for i, token in enumerate(tokens):
                        seg_words.append({
                            "word": token,
                            "start": float(segment.start) + i * step,
                            "end": float(segment.start) + (i + 1) * step,
                        })

            collected_words.extend(seg_words)

            # Découpage direct en phrases nettes selon le check de silence de 0.5s
            live_phrases = segment_words_into_clean_phrases(
                seg_words,
                silence_intervals=silence_intervals,
                pause_threshold=0.48,
                lang=det_lang
            )
            for lp in live_phrases:
                raw_count += 1
                print_live_segment({
                    "id": raw_count,
                    "speaker": "SPEAKER_00",
                    "start": lp["start"],
                    "end": lp["end"],
                    "text": lp["text"],
                })

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

    # ── 1. Découpage en répliques naturelles et complètes (sans hachage) ──
    print_progress(70, 100, "Découpage en phrases naturelles pour la bande rythmo...")

    phrases = segment_words_into_clean_phrases(
        all_words,
        silence_intervals=silence_intervals,
        pause_threshold=0.48,
        lang=detected_lang
    )

    # ── 2. Diarisation vocale haute précision sur phrases complètes (Pitch F0 + MFCCs) ──
    num_spk = args.num_speakers
    if num_spk != 1 and len(phrases) > 1:
        spk_label = "Auto-détection de tous les personnages" if num_spk <= 0 else f"{num_spk} locuteurs"
        print_progress(80, 100, f"Diarisation des répliques ({spk_label}, analyse pitch F0 & timbre)...")
        phrases = diarize_clean_phrases(args.audio, phrases, num_spk)

        # ── 3. Fusion des répliques strictement contiguës (< 0.18s) sans jamais avaler les pauses de 0.5s ──
        phrases = merge_consecutive_same_speaker_phrases(phrases, max_gap=0.18, max_duration=3.5, max_words=6)
    else:
        for p in phrases:
            p["speaker"] = "SPEAKER_00"

    # ── 4. Construction des segments finaux et streaming direct ──
    print_progress(90, 100, "Génération des repères et envoi vers OmeRyth...")

    final_segments = []
    for idx, phrase in enumerate(phrases):
        seg_data = {
            "id": idx + 1,
            "speaker": phrase.get("speaker", "SPEAKER_00"),
            "start": phrase["start"],
            "end": phrase["end"],
            "start_timecode": format_timecode(phrase["start"]),
            "end_timecode": format_timecode(phrase["end"]),
            "duration": round(max(0.1, phrase["end"] - phrase["start"]), 3),
            "text": phrase["text"],
            "words": phrase.get("words", []),
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
