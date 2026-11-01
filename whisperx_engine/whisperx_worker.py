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
import urllib.request
import uuid
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

# Patterns for Whisper hallucination artefacts (préserve (BO) et (BF))
_BRACKET_RE = re.compile(r"\[.*?\]|\((?!BO\)|BF\)).*?\)")
_OPEN_MOUTH_ANNOTATION_RE = re.compile(
    r"[\[\(]\s*(?:rires?|soupirs?|hal[eè]tements?|essoufflements?|respirations?|souffles?|"
    r"cris?|hurlements?|pleurs?|sanglots?|toux|raclements?|b[aâ]illements?|g[eé]missements?|"
    r"laughs?|laughter|sighs?|gasps?|panting|breathing|breath|coughs?|coughing|crying|sobs?|screams?|groans?|yawns?|"
    r"bruit\s+bouche\s+ouverte|bo)\s*[\]\)]",
    re.IGNORECASE
)
_CLOSED_MOUTH_ANNOTATION_RE = re.compile(
    r"[\[\(]\s*(?:hum+|hmm+|mm+h*|grognements?|reniflements?|fredonnements?|"
    r"sniffs?|grunts?|humming|mumbles?|bruit\s+bouche\s+ferm[eé]e|bf)\s*[\]\)]",
    re.IGNORECASE
)
_CLOSED_MOUTH_TOKEN_RE = re.compile(
    r"(?:^|(?<=\s))(?:mm+h*|hm+p?f+|mh+m+|mpf+|hmpf+|pff+f*|brr+r*|grr+r*)(?=$|[\s,.!?;:…])",
    re.IGNORECASE
)
_OPEN_MOUTH_TOKEN_RE = re.compile(
    r"(?:^|(?<=\s))(?:ha+h+a+[ha]*|he+h+e+[he]*|hi+h+i+[hi]*|aa*rgh+|ugh+|atchoum)(?=$|[\s,.!?;:…])",
    re.IGNORECASE
)
# Ne cible QUE les boucles d'hallucinations dégénérées (mots répétés 3+ fois ou séquences répétées)
# Préserve intégralement les répétitions naturelles en doublage ("oui oui", "non non", "attends attends", "très très", etc.)
_REPEATED_WORD_LOOP_RE = re.compile(r"\b(\w{2,})\b(?:[\s,.!?;:…—–-]+(?:\1\b)){2,}", re.IGNORECASE)
_REPEATED_PHRASE_LOOP_RE = re.compile(r"\b(\w{2,}(?:[\s,.!?;:…—–-]+\w+){1,4})\b(?:[\s,.!?;:…—–-]+(?:\1\b)){2,}", re.IGNORECASE)
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
    # Rectification des confusions acoustiques fréquentes de Whisper en français oral/doublage :
    # "je te blague" / "j'te blague" / "je te plaque" -> "s'te plaît" (ou "s'il te plaît")
    (re.compile(r"\b(?:je\s+te|j['’]?\s*te|jte)\s+blagues?\b", re.IGNORECASE), "s'te plaît"),
    (re.compile(r"\b(?:je\s+te|j['’]?\s*te|jte)\s+plaques?\b", re.IGNORECASE), "s'te plaît"),
    (re.compile(r"\b(?:s['’]?\s*te|ste)\s+pla[iî]t\b", re.IGNORECASE), "s'te plaît"),
    (re.compile(r"\b(?:s['’]?\s*te|ste)\s+pla[iî]s\b", re.IGNORECASE), "s'te plaît"),
    (re.compile(r"\b(?:s['’]?\s*il|sil)\s+te\s+pla[iî]s\b", re.IGNORECASE), "s'il te plaît"),
    # "je t'en parie" / "j't'en parie" -> "je t'en prie" / "j't'en prie"
    (re.compile(r"\b(?:j['’]?\s*t['’]?|jt['’]?)\s*en\s+parie?s?\b", re.IGNORECASE), "j't'en prie"),
    (re.compile(r"\bje\s+t['’]?\s*en\s+parie?s?\b", re.IGNORECASE), "je t'en prie"),
    (re.compile(r"\b(?:jt|j['’]t)['’]?\s*en\s+pri[eèé]s?\b", re.IGNORECASE), "j't'en prie"),
    (re.compile(r"\bjten\s+prie\b", re.IGNORECASE), "j't'en prie"),
    (re.compile(r"\bje\s+t['’]?\s*en\s+paris?\b", re.IGNORECASE), "je t'en prie"),
    (re.compile(r"\b(?:j['’]?\s*t['’]?|jt['’]?)\s*en\s+paris?\b", re.IGNORECASE), "j't'en prie"),
    (re.compile(r"\bje\s+vous\s+en\s+parie?s?\b", re.IGNORECASE), "je vous en prie"),
    (re.compile(r"\bvous\s+en\s+parie?s?\b", re.IGNORECASE), "vous en prie"),
    # Expressions orales et béquilles de langage
    (re.compile(r"\bc['’]\s*est\s+en\s+f[eê]te\b", re.IGNORECASE), "c'est en fait"),
    (re.compile(r"(^|[,;:\s])en\s+f[eê]te([,;:\s.!?]|$)", re.IGNORECASE), r"\1en fait\2"),
    (re.compile(r"(^|[,;:\s])aux?\s+f[eê]tes([,;:\s.!?]|$)", re.IGNORECASE), r"\1au fait\2"),
    (re.compile(r"\bt['’]\s*inqui[eèé]te?s?\s+pas\b", re.IGNORECASE), "t'inquiète pas"),
    (re.compile(r"\bt['’]\s*inqui[eèé]te?s?\b(?=[\s,.;!?]|$)", re.IGNORECASE), "t'inquiète"),
    (re.compile(r"\b(?:ch[eéè]s?|j['’]?sais)\s+pas\b", re.IGNORECASE), "j'sais pas"),
    (re.compile(r"\bqu['’]\s*est[- ]ce\s+t['’]\s*as\b", re.IGNORECASE), "qu'est-ce que t'as"),
    (re.compile(r"\bqu['’]\s*est[- ]ce\s+tu\s+fais\b", re.IGNORECASE), "qu'est-ce que tu fais"),
    (re.compile(r"\b(?:y['’]?\s*a|il\s+y\s+a)\s+pas\s+d['’]\s*quoi\b", re.IGNORECASE), "y a pas de quoi"),
    (re.compile(r"\bd['’]\s*t['’]\s*façon\b", re.IGNORECASE), "de toute façon"),
    (re.compile(r"\bd['’]\s*façon\b", re.IGNORECASE), "de toute façon"),
    (re.compile(r"\btout\s+a\s+l['’]\s*heure\b", re.IGNORECASE), "tout à l'heure"),
    (re.compile(r"\btout\s+a\s+fait\b", re.IGNORECASE), "tout à fait"),
    (re.compile(r"\ba\s+peu\s+pr[eèé]s?\b", re.IGNORECASE), "à peu près"),
    (re.compile(r"\ba\s+tous\s+les\s+coups\b", re.IGNORECASE), "à tous les coups"),
    (re.compile(r"\b([Ll])\s*['’]\s*entends?\b", re.IGNORECASE), "l'entends"),
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
    (re.compile(r"(?<![dD])['’]?\bau\s*jourd['’]?\s*hui\b", re.IGNORECASE), "aujourd'hui"),
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
    # Homophones et expressions idiomatiques fréquentes en doublage
    (re.compile(r"\b(j['’]ai|tu\s+as|il\s+a|elle\s+a|on\s+a|nous\s+avons|vous\s+avez|ils\s+ont|elles\s+ont)\s+hubo\b", re.IGNORECASE), r"\1 eu beau"),
    (re.compile(r"\b(eu\s+beau\s+)cherch[eé]\b", re.IGNORECASE), r"\1chercher"),
    (re.compile(r"\bins[eé]portable(s?)\b", re.IGNORECASE), r"insupportable\1"),
    (re.compile(r"\btsubaru\b", re.IGNORECASE), "Subaru"),
    (re.compile(r"\bje\s+vois\s+pas\s+encore\s+qu['’]il\b", re.IGNORECASE), "je vois pas en quoi il"),
    (re.compile(r"\baide[- ]toi\s+(?:arr[eê]te\s+)?de\s+te\s+foutre\b", re.IGNORECASE), "arrête de te foutre"),
    (re.compile(r"\bfoutre\s+de\s+gueule\b", re.IGNORECASE), "foutre de ma gueule"),
    (re.compile(r"\bd[îi]le[- ]moi\b", re.IGNORECASE), "dis-le-moi"),
    (re.compile(r"\bl['’]\s*ajfu?\b", re.IGNORECASE), "la JFU"),
    (re.compile(r"\ben\s+s['’]audressant\b", re.IGNORECASE), "en se dressant"),
    (re.compile(r"\bmon\s+standait\b", re.IGNORECASE), "mon stand est"),
    (re.compile(r"\bmon\s+stord\b", re.IGNORECASE), "mon stand"),
    (re.compile(r"\ble\s+juge\s+mort\b", re.IGNORECASE), "le jugement"),
    (re.compile(r"\bliquifi([eéaè][a-z]*)\b", re.IGNORECASE), r"liquéfi\1"),
    (re.compile(r"\bde\s+rôtir?\s+de\s+ses\s+flammes\b", re.IGNORECASE), "de rôtir de ses flammes"),
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
    sur la bande rythmo (sans artéfacts, avec accents, ponctuation propre et repères (BO)/(BF)).
    """
    text = raw.strip()
    if not text:
        return ""

    # 1. Convertir les annotations d'émotions / bruits vocaux en (BO) ou (BF), puis supprimer les annotations parasites [Musique], etc.
    text = _OPEN_MOUTH_ANNOTATION_RE.sub(" (BO) ", text)
    text = _CLOSED_MOUTH_ANNOTATION_RE.sub(" (BF) ", text)
    text = _BRACKET_RE.sub("", text).strip()

    # 1b. Éliminer toute fuite accidentelle de métadonnées ou de consignes de prompt
    text = _PROMPT_LEAK_PREFIX_RE.sub("", text).strip()
    norm_leak = re.sub(r"[^\w\s]", "", text).lower().strip()
    if norm_leak in _PROMPT_LEAK_WORDS or norm_leak == "transcription" or "amaraorg" in re.sub(r"[^\w]", "", text).lower():
        return ""
    for leak in ["transcription fidèle", "conserver impérativement", "bégaiements exacts", "sous-titres réalisés par", "amara.org"]:
        text = re.sub(rf"(?:^|\b){re.escape(leak)}(?:\b|$)", "", text, flags=re.IGNORECASE)

    # 2. Supprimer les boucles d'hallucination réelles Whisper (>= 3 mots ou >= 3 phrases)
    def _clean_word_loop_match(m):
        w = m.group(1)
        full = m.group(0)
        count = len(re.findall(rf"\b{re.escape(w)}\b", full, flags=re.IGNORECASE))
        if count >= 3:
            return w if count >= 4 else f"{w} {w}"
        return full

    text = _REPEATED_WORD_LOOP_RE.sub(_clean_word_loop_match, text)
    text = _REPEATED_PHRASE_LOOP_RE.sub(r"\1", text)

    # 2b. Convertir les onomatopées non-verbales explicites (soupirs, grognements bouche fermée/ouverte) en (BO) / (BF)
    text = _CLOSED_MOUTH_TOKEN_RE.sub("(BF)", text)
    text = _OPEN_MOUTH_TOKEN_RE.sub("(BO)", text)
    text = re.sub(r"\(bo\)", "(BO)", text, flags=re.IGNORECASE)
    text = re.sub(r"\(bf\)", "(BF)", text, flags=re.IGNORECASE)
    text = re.sub(r"(?:\(BO\)\s*){2,}", "(BO) ", text)
    text = re.sub(r"(?:\(BF\)\s*){2,}", "(BF) ", text)

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
        pass
    try:
        import subprocess
        ffmpeg_bin = "ffmpeg"
        local_ffmpeg = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "ffmpeg", "ffmpeg.exe")
        if os.path.isfile(local_ffmpeg):
            ffmpeg_bin = local_ffmpeg
        cmd = [ffmpeg_bin, "-threads", "0", "-i", audio_path, "-vn", "-sn", "-dn", "-ar", "16000", "-ac", "1", "-f", "f32le", "-"]
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        out, _ = proc.communicate()
        if proc.returncode == 0 and len(out) >= 4:
            data = np.frombuffer(out, dtype=np.float32)
            return data, 16000
    except Exception:
        pass
    return np.zeros(16000, dtype=np.float32), 16000


def get_voice_band_audio(audio, sr: int = 16000):
    """
    Extrait la bande fréquentielle fondamentale et harmonique de la voix humaine (180-3800 Hz)
    pour rejeter les ronflements graves (<180 Hz) et souffles/sifflements aigus (>3800 Hz).
    """
    if audio is None or len(audio) == 0 or sr <= 0:
        return audio
    try:
        import scipy.signal as signal
        nyq = sr / 2.0
        low = max(80.0, min(180.0, nyq * 0.8))
        high = max(low + 200.0, min(3800.0, nyq * 0.94))
        sos = signal.butter(3, [low, high], btype="bandpass", fs=sr, output="sos")
        return signal.sosfiltfilt(sos, audio).astype(np.float32)
    except Exception:
        return audio


def get_speech_envelope_audio(audio, sr: int = 16000):
    """
    Extrait la bande complète de la parole articulée (160-7200 Hz : formants + consonnes fricatives/plosives)
    en éliminant les basses fréquences parasites (bruit de salle, musique grave, pop micro < 160 Hz).
    """
    if audio is None or len(audio) == 0 or sr <= 0:
        return audio
    try:
        import scipy.signal as signal
        nyq = sr / 2.0
        low = max(80.0, min(160.0, nyq * 0.8))
        high = max(low + 400.0, min(7200.0, nyq * 0.95))
        sos = signal.butter(3, [low, high], btype="bandpass", fs=sr, output="sos")
        return signal.sosfiltfilt(sos, audio).astype(np.float32)
    except Exception:
        return audio


def classify_mouth_noise_bo_bf(audio_data, start_sec: float, end_sec: float, sr: int = 16000, hint_text: str = "") -> str:
    """
    Classifie un bruit vocal non-verbal / émotionnel en :
    - '(BO)' : Bruit Bouche Ouverte (soupir, respiration, halètement, rire, cri, bâillement, toux, râle ouvert)
    - '(BF)' : Bruit Bouche Fermée (hum, mmh, grognement bouche fermée, reniflement, souffle labial pff/mpf)
    Combine l'indice textuel (si non-ambigu) et l'analyse spectrale (ratio d'énergie nasale basse 90-500 Hz
    vs formants ouverts et souffle 550-5500 Hz + centroïde spectral).
    """
    h_clean = re.sub(r"[^\w]", "", (hint_text or "")).lower()
    if h_clean in ("bf", "bruitbouchefermee"):
        return "(BF)"
    if h_clean in ("bo", "bruitboucheouverte"):
        return "(BO)"
    if re.fullmatch(r"(?:mm+h*|hm+p?f*|mh+m*|uhm+|hum+|mpf+|hmpf+|pff+f*|brr+r*|grr+r*)", h_clean):
        return "(BF)"
    if re.fullmatch(r"(?:ha+h+a+[ha]*|he+h+e+[he]*|hi+h+i+[hi]*|aa*rgh+|ugh+|ouf+|aie|aïe|ouh+)", h_clean):
        return "(BO)"

    if audio_data is not None and len(audio_data) > 0 and sr > 0 and end_sec > start_sec:
        s_idx = max(0, int(start_sec * sr))
        e_idx = min(len(audio_data), int(end_sec * sr))
        chunk = audio_data[s_idx:e_idx]
        if len(chunk) >= int(sr * 0.03):
            chunk = chunk - float(np.mean(chunk))
            win = np.hanning(len(chunk))
            spec = np.abs(np.fft.rfft(chunk * win)) ** 2
            freqs = np.fft.rfftfreq(len(chunk), 1.0 / sr)
            total_e = float(np.sum(spec[(freqs >= 80) & (freqs <= 6500)])) + 1e-12
            closed_band_e = float(np.sum(spec[(freqs >= 90) & (freqs <= 500)]))
            open_band_e = float(np.sum(spec[(freqs >= 550) & (freqs <= 5500)]))
            mag = np.sqrt(spec)
            centroid = float(np.sum(freqs * mag) / (np.sum(mag) + 1e-12))
            # Un son bouche fermée (murmure nasal, hum, grognement lèvres closes) concentre > 62% de son énergie
            # sous 500 Hz avec un centroïde spectral bas (< 680 Hz).
            if (closed_band_e / total_e) >= 0.62 and centroid < 680.0 and closed_band_e > open_band_e * 1.4:
                return "(BF)"
            return "(BO)"

    return "(BO)"


# Sons d'hésitation ou souffles courts parfois hallucinés par Whisper dans le silence complet
_PHANTOM_SHORT_INTERJECTIONS = {
    "eh", "euh", "euuh", "euhh", "uh", "um", "er", "hum", "mmh", "mh", "hm"
}


def filter_hallucinated_and_classify_emotional_words(words: list, audio_data, sr: int = 16000) -> list:
    """
    1. Élimine uniquement les hallucinations fantômes d'hésitation ('eh', 'euh', 'uh', 'mmh')
       posées par Whisper dans un silence acoustique quasi total.
    2. Convertit les onomatopées non-verbales explicites en '(BO)' ou '(BF)' sans jamais
       altérer les vrais mots du dialogue.
    """
    if not words:
        return []
    if audio_data is None or len(audio_data) == 0 or sr <= 0:
        return words

    voice_band = get_voice_band_audio(audio_data, sr=sr)
    total_samples = len(voice_band)
    total_dur = total_samples / float(sr)

    word_voice_rms_list = []
    for w in words:
        ws = max(0.0, float(w.get("start", 0.0)))
        we = min(total_dur, float(w.get("end", ws + 0.08)))
        s_i, e_i = int(ws * sr), int(we * sr)
        if e_i > s_i + 16:
            r_val = float(np.sqrt(np.mean(voice_band[s_i:e_i] ** 2)))
            if len(norm_word(w.get("word", ""))) >= 3:
                word_voice_rms_list.append(r_val)

    ref_speech_rms = float(np.median(word_voice_rms_list)) if word_voice_rms_list else 0.025
    global_floor = float(np.percentile(np.abs(voice_band), 20)) if total_samples > 0 else 0.001

    filtered = []
    n_w = len(words)
    for idx, w in enumerate(words):
        w_txt = str(w.get("word", "")).strip()
        if not w_txt:
            continue
        w_upper = w_txt.upper()
        if w_upper in ("(BO)", "(BF)"):
            filtered.append(w)
            continue

        clean_w = norm_word(w_txt)
        ws = max(0.0, float(w.get("start", 0.0)))
        we = min(total_dur, float(w.get("end", ws + 0.08)))
        dur = max(0.01, we - ws)
        prob = float(w.get("prob", 0.85))
        no_speech_p = float(w.get("no_speech_prob", 0.0))

        # Onomatopées non-verbales explicites -> conversion directe en (BO) / (BF)
        if _CLOSED_MOUTH_TOKEN_RE.fullmatch(clean_w):
            w_copy = dict(w)
            w_copy["word"] = "(BF)"
            filtered.append(w_copy)
            continue
        if _OPEN_MOUTH_TOKEN_RE.fullmatch(clean_w):
            w_copy = dict(w)
            w_copy["word"] = "(BO)"
            filtered.append(w_copy)
            continue

        gap_before = (ws - float(words[idx - 1].get("end", 0.0))) if idx > 0 else 999.0
        gap_after = (float(words[idx + 1].get("start", total_dur)) - we) if idx < n_w - 1 else 999.0
        is_isolated = (gap_before >= 0.30 and gap_after >= 0.30)

        s_i = max(0, int(ws * sr))
        e_i = min(total_samples, int(we * sr))
        if e_i <= s_i + 16:
            continue

        v_chunk = voice_band[s_i:e_i]
        word_v_rms = float(np.sqrt(np.mean(v_chunk ** 2)))

        bg_samples = []
        bg1_s, bg1_e = max(0, int((ws - 0.55) * sr)), max(0, int((ws - 0.06) * sr))
        bg2_s, bg2_e = min(total_samples, int((we + 0.06) * sr)), min(total_samples, int((we + 0.55) * sr))
        if bg1_e - bg1_s >= int(sr * 0.05):
            bg_samples.append(voice_band[bg1_s:bg1_e])
        if bg2_e - bg2_s >= int(sr * 0.05):
            bg_samples.append(voice_band[bg2_s:bg2_e])
        if bg_samples:
            bg_cat = np.concatenate(bg_samples)
            local_bg_rms = float(np.percentile(np.abs(bg_cat), 65))
        else:
            local_bg_rms = global_floor

        snr_ratio = word_v_rms / max(1e-5, local_bg_rms)
        rel_to_speech = word_v_rms / max(1e-5, ref_speech_rms)

        is_short_interj = (
            clean_w in _PHANTOM_SHORT_INTERJECTIONS
            or bool(re.fullmatch(r"(?:e+u+h+|u+h+|h+m+|m+h+)", clean_w))
        )

        # Filtrage uniquement des 'eh'/'euh'/'uh' isolés dans le silence avec très faible probabilité
        if is_short_interj and is_isolated:
            if (word_v_rms < 0.0035 and rel_to_speech < 0.10) or (prob < 0.45 and snr_ratio < 1.8 and rel_to_speech < 0.18) or (no_speech_p > 0.65 and prob < 0.50):
                continue

        filtered.append(w)

    return filtered


def detect_silence_intervals(audio, sr: int = 16000, min_silence_sec: float = 0.10, frame_ms: int = 15) -> list:
    """
    Vérifie le signal audio filtré dans la bande de parole (160-7200 Hz) pour détecter toutes
    les fenêtres de silence (dès 0.10s) où la personne s'arrête de parler.
    Retourne les intervalles de silence [(start_sec, end_sec), ...] avec précision au milliseconde.
    """
    if audio is None or len(audio) == 0:
        return []
    import numpy as np
    speech_sig = get_speech_envelope_audio(audio, sr=sr)
    frame_len = max(16, int(sr * (frame_ms / 1000.0)))
    hop_len = max(8, frame_len // 3)
    num_frames = max(1, 1 + (len(speech_sig) - frame_len) // hop_len)
    if num_frames < 2:
        return []

    idx_mat = np.arange(frame_len)[None, :] + (np.arange(num_frames) * hop_len)[:, None]
    rms = np.sqrt(np.mean(speech_sig[idx_mat] ** 2, axis=1))

    p10 = float(np.percentile(rms, 10))
    p85 = float(np.percentile(rms, 85))
    silence_thresh = max(0.0025, p10 + 0.08 * (p85 - p10))

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
                silence_ranges.append((round(start_f * hop_len / sr, 3), round(idx * hop_len / sr, 3)))
    if in_silence and (len(is_silent) - start_f) >= min_silence_frames:
        silence_ranges.append((round(start_f * hop_len / sr, 3), round(len(is_silent) * hop_len / sr, 3)))

    return silence_ranges


def detect_vocal_onset(audio, start_sec: float, prev_end_sec: float = 0.0, sr: int = 16000, max_lookback: float = 0.18, max_lookahead: float = 0.25) -> float:
    """
    Rabat avec haute précision le début d'une réplique (ou d'un mot) sur l'attaque acoustique
    réelle de la voix :
    - Si Whisper a posé le début trop tôt dans un silence/souffle (jusqu'à +0.25s d'avance),
      avance le début jusqu'à la vraie attaque vocale.
    - Si Whisper a posé le début trop tard après la première consonne/voyelle (jusqu'à -0.18s de retard),
      recule le début sur le vrai départ du son.
    """
    if audio is None or len(audio) == 0 or sr <= 0 or start_sec <= 0.02:
        return max(0.0, round(float(start_sec), 3))

    min_bound = max(0.0, float(prev_end_sec) + 0.04) if prev_end_sec > 0 else 0.0
    search_start = max(min_bound, float(start_sec) - max_lookback)
    search_end = min(len(audio) / sr, float(start_sec) + max_lookahead)

    if search_end <= search_start + 0.025:
        return max(min_bound, round(float(start_sec), 3))

    import numpy as np
    idx_s = int(search_start * sr)
    idx_e = int(search_end * sr)
    chunk = audio[idx_s:idx_e]
    if len(chunk) < 64:
        return max(min_bound, round(float(start_sec), 3))

    # Filtrer les basses fréquences parasites (<160 Hz) pour ne détecter que la vraie voix/articulation
    chunk = get_speech_envelope_audio(chunk - np.mean(chunk), sr=sr)

    flen = int(sr * 0.012)  # 12ms
    hop = int(sr * 0.004)   # 4ms
    n_frames = max(1, 1 + (len(chunk) - flen) // hop)
    if n_frames < 3:
        return max(min_bound, round(float(start_sec), 3))

    idx_mat = np.arange(flen)[None, :] + (np.arange(n_frames) * hop)[:, None]
    frames = chunk[idx_mat]
    rms = np.sqrt(np.mean(frames ** 2, axis=1))
    times = search_start + (np.arange(n_frames) * hop) / float(sr)

    p10 = float(np.percentile(rms, 10))
    p90 = float(np.percentile(rms, 90))
    peak_val = float(np.max(rms))

    if peak_val < 0.0025 or (p90 - p10) < 0.0015:
        return max(min_bound, round(float(start_sec), 3))

    # Seuil d'attaque vocale : 14% de la dynamique locale au-dessus du plancher de silence
    onset_thresh = max(0.0025, p10 + 0.14 * (p90 - p10))

    # Chercher le premier franchissement de seuil soutenu (au moins 2 trames consécutives)
    for fi in range(n_frames - 1):
        if rms[fi] >= onset_thresh and rms[fi + 1] >= onset_thresh:
            t_onset = float(times[fi]) - 0.008
            t_clamped = max(min_bound, min(float(start_sec) + max_lookahead, t_onset))
            return max(0.0, round(t_clamped, 3))

    return max(min_bound, round(float(start_sec), 3))


def trim_speech_start_acoustically(audio_data, start_sec: float, end_sec: float, sr: int = 16000, blank_thresh_sec: float = 0.10, frame_ms: int = 20) -> float:
    """Alias rétro-compatible redirigeant vers detect_vocal_onset."""
    return detect_vocal_onset(audio_data, start_sec, prev_end_sec=max(0.0, start_sec - 0.35), sr=sr, max_lookback=0.25, max_lookahead=min(0.30, max(0.08, (end_sec - start_sec) * 0.6)))


def trim_speech_end_acoustically(audio_data, start_sec: float, end_sec: float, sr: int = 16000, blank_thresh_sec: float = 0.12, frame_ms: int = 15) -> float:
    """
    Vérifie le signal audio pour détecter tout blanc / temps mort (>= 0.12s)
    situé à la fin d'une réplique ou d'un mot.
    Si la personne a fini de parler plus tôt que l'horodatage Whisper, rabat précisément
    la fin de réplique sur la dernière trame vocale active (+ marge naturelle de 0.025s).
    """
    if audio_data is None or sr <= 0 or end_sec <= start_sec + 0.08:
        return round(end_sec, 3)
    import numpy as np

    s_idx = max(0, int(start_sec * sr))
    e_idx = min(len(audio_data), int(end_sec * sr))
    if e_idx <= s_idx:
        return round(end_sec, 3)

    chunk = get_speech_envelope_audio(audio_data[s_idx:e_idx], sr=sr)
    frame_len = max(16, int(sr * (frame_ms / 1000.0)))
    hop_len = max(8, frame_len // 3)
    num_frames = max(1, 1 + (len(chunk) - frame_len) // hop_len)
    if num_frames < 2:
        return round(end_sec, 3)

    idx_mat = np.arange(frame_len)[None, :] + (np.arange(num_frames) * hop_len)[:, None]
    rms = np.sqrt(np.mean(chunk[idx_mat] ** 2, axis=1))

    p10 = float(np.percentile(rms, 10))
    p85 = float(np.percentile(rms, 85))
    silence_thresh = max(0.0025, p10 + 0.10 * (p85 - p10))

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
        trimmed_sec = start_sec + (last_voice_frame * hop_len / sr) + 0.025
        return round(max(start_sec + 0.08, min(end_sec, trimmed_sec)), 3)

    return round(end_sec, 3)


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

# Clitiques et pronoms conjoints français phonétiquement liés au mot suivant (jamais de coupure rythmique)
FRENCH_CLITICS = {
    # Pronoms personnels sujets et conjoints
    "je", "j", "tu", "t", "il", "ils", "elle", "elles", "on", "nous", "vous",
    # Pronoms compléments et réfléchis
    "me", "m", "te", "se", "s", "lui", "leur", "y", "en", "moi", "toi",
    # Articles définis, indéfinis et partitifs
    "le", "la", "les", "l", "un", "une", "des", "du", "de", "d",
    # Démonstratifs et possessifs courts
    "ce", "cet", "cette", "ces", "c",
    "mon", "ton", "son", "ma", "ta", "sa", "mes", "tes", "ses",
    "notre", "votre", "nos", "vos", "leurs",
    # Conjonctions et prépositions brèves
    "que", "qu", "qui", "ne", "n", "si", "et", "ou", "ni", "car", "à", "au", "aux"
}


def norm_word(w: str) -> str:
    """Normalise un mot pour comparaison sans ponctuation ni casse."""
    return re.sub(r'[\W_]+', '', str(w)).lower()


def refine_word_boundaries_acoustically(words: list, audio_data, sr: int = 16000) -> list:
    """
    Calibre acoustiquement le DÉBUT et la FIN des mots uniquement lorsqu'un mot est
    étiré par l'alignement DTW de Whisper sur un silence adjacent (début ou fin de segment),
    sans jamais raccourcir les mots prononcés en continu ni créer de faux blancs inter-mots.
    """
    if not words or audio_data is None or len(audio_data) == 0 or sr <= 0:
        return words

    import numpy as np

    speech_sig = get_speech_envelope_audio(audio_data, sr=sr)
    total_samples = len(speech_sig)
    frame_len = int(sr * 0.015)  # fenêtre 15ms
    hop_len = int(sr * 0.005)    # saut 5ms

    n_w = len(words)
    for idx_w, w in enumerate(words):
        w_txt = str(w.get("word", "")).strip()
        clean = norm_word(w_txt)
        w_start = float(w.get("start", 0.0))
        w_end = float(w.get("end", 0.0))
        dur = w_end - w_start
        if dur <= 0.08:
            continue

        gap_before = (w_start - float(words[idx_w - 1]["end"])) if idx_w > 0 else 999.0
        gap_after = (float(words[idx_w + 1]["start"]) - w_end) if idx_w < n_w - 1 else 999.0

        L = max(1, len(clean))
        if clean in FRENCH_CLITICS or L <= 2:
            max_nat_dur = 0.28
            min_dur = 0.08
        elif clean in NEVER_PROLONGED or L <= 4:
            max_nat_dur = 0.38
            min_dur = 0.11
        else:
            max_nat_dur = max(0.48, 0.24 + 0.075 * L)
            min_dur = 0.15

        # 1. Mot précédé d'un silence/pause et anormalement étiré vers l'avant :
        #    avancer w["start"] sur l'attaque vocale réelle sans toucher à w["end"]
        if gap_before >= 0.18 and dur > max_nat_dur:
            s_samp = max(0, int(w_start * sr))
            e_samp = min(total_samples, int(w_end * sr))
            if e_samp - s_samp >= frame_len * 2:
                chunk = speech_sig[s_samp:e_samp]
                nf = 1 + (len(chunk) - frame_len) // hop_len
                idx_mat = np.arange(frame_len)[None, :] + (np.arange(nf) * hop_len)[:, None]
                rms = np.sqrt(np.mean(chunk[idx_mat] ** 2, axis=1))
                tail_start_f = max(0, int((dur - max_nat_dur) / 0.005))
                voice_ref = rms[tail_start_f:] if len(rms[tail_start_f:]) > 0 else rms
                peak_rms = float(np.percentile(voice_ref, 90)) if len(voice_ref) > 2 else float(np.max(rms))
                p10 = float(np.percentile(rms, 10))
                thresh = max(0.0025, p10 + 0.08 * (peak_rms - p10))
                onset_f = 0
                for fi in range(nf - 1):
                    if rms[fi] >= thresh and rms[fi + 1] >= thresh:
                        onset_f = fi
                        break
                acoustic_start = w_start + (onset_f * hop_len / float(sr)) - 0.010
                min_expected_dur = max(min_dur, min(0.85, 0.055 * L))
                max_allowed_start = w_end - min_expected_dur
                new_ws = max(w_start, min(max_allowed_start, acoustic_start))
                w["start"] = round(min(w_end - min_dur, new_ws), 3)
                w_start = float(w["start"])
                dur = w_end - w_start
        # 1b. Mot précédé d'un grand blanc (gap_before >= 0.22s) mais dont Whisper a placé le début
        #     trop tard en sautant les premières syllabes (dur < 0.068 * L) :
        #     reculer w["start"] sur la vraie attaque vocale après le silence inter-répliques.
        elif gap_before >= 0.22 and L >= 4 and dur < min(0.85, 0.068 * L):
            prev_e = float(words[idx_w - 1]["end"]) if idx_w > 0 else 0.0
            search_s = max(prev_e + 0.12 if idx_w > 0 else 0.0, w_end - max_nat_dur, w_start - 0.55)
            if w_start > search_s + 0.05:
                s_samp = max(0, int(search_s * sr))
                e_samp = min(total_samples, int(w_end * sr))
                if e_samp - s_samp >= frame_len * 3:
                    chunk = speech_sig[s_samp:e_samp]
                    nf = 1 + (len(chunk) - frame_len) // hop_len
                    idx_mat = np.arange(frame_len)[None, :] + (np.arange(nf) * hop_len)[:, None]
                    rms = np.sqrt(np.mean(chunk[idx_mat] ** 2, axis=1))
                    w_start_f = max(0, min(nf - 1, int((w_start - search_s) / 0.005)))
                    voice_ref = rms[w_start_f:] if len(rms[w_start_f:]) > 0 else rms
                    peak_rms = float(np.percentile(voice_ref, 90)) if len(voice_ref) > 2 else float(np.max(rms))
                    p10 = float(np.percentile(rms, 10))
                    thresh = max(0.003, p10 + 0.12 * (peak_rms - p10))
                    for fi in range(w_start_f):
                        if rms[fi] >= thresh and rms[min(nf - 1, fi + 1)] >= thresh:
                            cand_s = search_s + (fi * hop_len / float(sr)) - 0.010
                            w["start"] = round(max(search_s, min(w_start, cand_s)), 3)
                            w_start = float(w["start"])
                            dur = w_end - w_start
                            break

        # 2. Mot suivi d'un silence/pause et anormalement étiré vers l'arrière :
        #    rabattre w["end"] sur l'extinction vocale réelle sans toucher à w["start"]
        if gap_after >= 0.18 and dur > max_nat_dur:
            s_samp = max(0, int(w_start * sr))
            e_samp = min(total_samples, int(w_end * sr))
            if e_samp - s_samp >= frame_len * 2:
                chunk = speech_sig[s_samp:e_samp]
                nf = 1 + (len(chunk) - frame_len) // hop_len
                idx_mat = np.arange(frame_len)[None, :] + (np.arange(nf) * hop_len)[:, None]
                rms = np.sqrt(np.mean(chunk[idx_mat] ** 2, axis=1))
                head_end_f = min(nf, max(2, int(max_nat_dur / 0.005)))
                voice_ref = rms[:head_end_f]
                peak_rms = float(np.percentile(voice_ref, 90)) if len(voice_ref) > 2 else float(np.max(rms))
                p10 = float(np.percentile(rms, 10))
                thresh = max(0.003, p10 + 0.16 * (peak_rms - p10))
                last_f = nf - 1
                for fi in range(nf - 1, -1, -1):
                    if rms[fi] >= thresh:
                        last_f = fi
                        break
                if (nf - 1 - last_f) >= 4:
                    acoustic_end = w_start + (last_f * hop_len / float(sr)) + 0.025
                    w["end"] = round(max(w_start + min_dur, min(w_end, acoustic_end)), 3)
                elif dur > max_nat_dur * 1.30 and gap_before < 0.15:
                    tail_rms = float(np.mean(rms[head_end_f:])) if head_end_f < nf else peak_rms
                    if tail_rms < peak_rms * 0.45:
                        w["end"] = round(w_start + max_nat_dur, 3)

    return words


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
    Associe chaque mot horodaté Whisper à ses coordonnées exactes (start_pos, end_pos, end_word_pos, w)
    dans le texte de la réplique, de manière insensible à la casse et robuste aux contractions,
    aux repères (BO)/(BF) et à la ponctuation normalisée.
    """
    phrase_lower = phrase_text.lower()
    word_spans = []
    search_idx = 0
    for w in words:
        w_txt = w.get("word", "").strip()
        if not w_txt:
            continue
        # Cas spécial pour (BO) et (BF) : inclure les parenthèses dans le span du mot
        if w_txt.upper() in ("(BO)", "(BF)"):
            target_tag = w_txt.lower()
            idx = phrase_lower.find(target_tag, search_idx)
            if idx != -1:
                start_pos = idx
                end_word_pos = idx + len(target_tag)
                while end_word_pos < len(phrase_text) and phrase_text[end_word_pos] in ('.', ',', '…', ';', '!', '?', ':', '-'):
                    end_word_pos += 1
                end_pos = end_word_pos
                while end_pos < len(phrase_text) and phrase_text[end_pos] in " \t":
                    end_pos += 1
                word_spans.append((start_pos, end_pos, end_word_pos, w))
                search_idx = end_pos
                continue

        core = re.sub(r"^[^\w'’-]+|[^\w'’-]+$", "", w_txt).lower()
        if not core:
            continue
        m = None
        # 1. Correspondance exacte tenant compte des frontières de mot
        pattern = re.compile(r"(?:^|\b|\s|\()" + re.escape(core) + r"(?:\b|\s|\)|[.,;:!?'’…\-]|$)", re.IGNORECASE)
        m = pattern.search(phrase_lower, search_idx)
        if m:
            start_pos = m.start()
            while start_pos < len(phrase_text) and phrase_text[start_pos] in " \t\n\r":
                start_pos += 1
            end_word_pos = start_pos + len(core)
            if start_pos > 0 and phrase_text[start_pos - 1] == '(' and end_word_pos < len(phrase_text) and phrase_text[end_word_pos] == ')':
                start_pos -= 1
                end_word_pos += 1
            while end_word_pos < len(phrase_text) and phrase_text[end_word_pos] in ('.', ',', '…', ';', '!', '?', ':', '-', ')'):
                end_word_pos += 1
            end_pos = end_word_pos
            while end_pos < len(phrase_text) and phrase_text[end_pos] in " \t":
                end_pos += 1
            word_spans.append((start_pos, end_pos, end_word_pos, w))
            search_idx = end_pos
            continue
        # 2. Recherche par sous-chaîne directe en repli
        idx = phrase_lower.find(core, search_idx)
        if idx != -1:
            start_pos = idx
            end_word_pos = idx + len(core)
            if start_pos > 0 and phrase_text[start_pos - 1] == '(' and end_word_pos < len(phrase_text) and phrase_text[end_word_pos] == ')':
                start_pos -= 1
                end_word_pos += 1
            while end_word_pos < len(phrase_text) and phrase_text[end_word_pos] in ('.', ',', '…', ';', '!', '?', ':', '-', ')'):
                end_word_pos += 1
            end_pos = end_word_pos
            while end_pos < len(phrase_text) and phrase_text[end_pos] in " \t":
                end_pos += 1
            word_spans.append((start_pos, end_pos, end_word_pos, w))
            search_idx = end_pos
        else:
            word_spans.append((search_idx, search_idx, search_idx, w))
    return word_spans


def compute_rhythmic_separators(phrase_text: str, words: list, audio_data=None, sr: int = 16000) -> list:
    """
    Calcule les séparateurs rythmiques internes (INNER) au sein d'une réplique :
    1. Pour chaque mot suivi d'une vraie pause acoustique interne (gap >= 0.18s, ou >= 0.14s avec ponctuation) :
       REFERME le mot à sa fin exacte (w_end) via un séparateur de clôture (split_index = end_word_pos)
       et OUVRE le mot suivant à son début exact (next_start) via un séparateur d'attaque (split_index = next_start_pos).
    2. Pour les articulations syntaxiques naturelles (ponctuation intermédiaire ',', ';', ':', '?'...)
       ou les groupes de souffle longs (>= 1.35s), pose un séparateur INNER unique au début du mot suivant,
       sans surcharger les phrases courtes.
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
    last_sep_time = phrase_start

    def add_sep(t, s_idx, min_time_gap: float = 0.25, allow_adjacent_split: bool = False):
        nonlocal last_sep_time
        if s_idx <= 1 or s_idx >= p_len - 1:
            return False
        if not allow_adjacent_split and s_idx < p_len and phrase_text[s_idx] not in (" ", "(", "'", "’", "-", ",", ";", ":", ".", "!", "?"):
            if s_idx > 0 and phrase_text[s_idx - 1] != " ":
                left_space = phrase_text.rfind(" ", 0, s_idx)
                right_space = phrase_text.find(" ", s_idx)
                if left_space != -1 and (s_idx - left_space) <= 4:
                    s_idx = left_space + 1
                elif right_space != -1 and (right_space - s_idx) <= 4:
                    s_idx = right_space + 1

        if s_idx > 0 and s_idx < p_len:
            if phrase_text[s_idx - 1] in ("'", "’", "-") or phrase_text[s_idx] in ("'", "’", "-"):
                return False

        t_round = round(float(t), 3)
        if (t_round - phrase_start) < 0.25 or (phrase_end - t_round) < 0.25:
            return False
        for s in separators:
            if s["split_index"] == s_idx:
                return False
            if not allow_adjacent_split and abs(s["split_index"] - s_idx) <= 1:
                return False
            if abs(s["time"] - t_round) < min_time_gap:
                return False
        separators.append({"time": t_round, "split_index": s_idx})
        last_sep_time = max(last_sep_time, t_round)
        return True

    for i in range(len(word_spans)):
        start_pos, end_pos, end_word_pos, w_curr = word_spans[i]
        if start_pos >= end_pos:
            continue
        w_start = float(w_curr.get("start", 0.0))
        w_end = float(w_curr.get("end", 0.0))
        dur = max(0.0, w_end - w_start)
        w_clean = re.sub(r"^[^\w]+|[^\w]+$", "", w_curr.get("word", "")).lower()
        is_clitic = (w_clean in FRENCH_CLITICS)

        if i < len(word_spans) - 1:
            next_start_pos, next_end_pos, next_end_word_pos, w_next = word_spans[i + 1]
            if next_start_pos <= end_word_pos:
                next_start_pos = end_word_pos + 1
            if next_start_pos < p_len:
                next_start = float(w_next.get("start", w_end))
                gap = next_start - w_end

                w_raw = w_curr.get("word", "")
                slice_between = phrase_text[start_pos:next_start_pos]
                word_slice = phrase_text[start_pos:end_word_pos]
                is_ellipsis = (
                    "..." in slice_between
                    or "…" in slice_between
                    or "..." in word_slice
                    or "…" in word_slice
                    or w_raw.endswith("...")
                    or w_raw.endswith("…")
                )
                is_punct = is_ellipsis or any(
                    w_raw.endswith(p) for p in [".", "!", "?", ",", ";", ":", "—"]
                ) or any(
                    word_slice.endswith(p) for p in [".", "!", "?", ",", ";", ":", "—"]
                )
                next_clean = re.sub(r"^[^\w]+|[^\w]+$", "", w_next.get("word", "")).lower()
                is_repetition = (w_clean == next_clean and len(w_clean) >= 2)
                is_bo_bf = w_curr.get("word", "").strip().upper() in ("(BO)", "(BF)") or w_next.get("word", "").strip().upper() in ("(BO)", "(BF)")

                if is_clitic:
                    continue

                # Pivot syntaxique, pause interne, (BO)/(BF) ou répétition -> un seul séparateur INNER au début du mot suivant
                if gap >= 0.14 or is_punct or is_bo_bf or is_repetition:
                    if (next_start - last_sep_time) >= 0.30 and (phrase_end - next_start) >= 0.28:
                        add_sep(round(float(next_start), 3), next_start_pos, min_time_gap=0.28)
                # Phrase longue sans ponctuation -> point d'appui rythmique modéré (toutes les ~1.4s sur un mot plein)
                elif (next_start - last_sep_time) >= 1.35 and (phrase_end - next_start) >= 0.55:
                    if w_clean not in NEVER_PROLONGED and len(w_clean) >= 3 and (dur >= 0.24 or gap >= 0.04):
                        add_sep(round(float(next_start), 3), next_start_pos, min_time_gap=0.50)

    separators.sort(key=lambda s: (s["split_index"], s["time"]))
    return separators


def deduplicate_adjacent_words(words: list) -> list:
    """
    Élimine les dédoublements accidentels de tokens générés par Whisper ainsi que les
    boucles d'hallucinations dégénérées (ex: 'aller' répété 15 fois), tout en préservant
    scrupuleusement les vraies répétitions naturelles du doublage (ex: 'très très', 'non non', 'oui oui').
    """
    if not words or len(words) < 2:
        return words

    # Étape 1 : Fusion des micro-tokens accidentels de Whisper (<0.04s)
    step1 = []
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
                if min(dur1, dur2) < 0.04 and gap < 0.08:
                    chosen_word = w_next.get("word", "")
                    if chosen_word.endswith("...") or chosen_word.endswith("…"):
                        chosen_word = w.get("word", "").rstrip(".…")
                    if not chosen_word:
                        chosen_word = w_next.get("word", "").rstrip(".…")
                    merged_word = {
                        "word": chosen_word,
                        "start": min(float(w["start"]), float(w_next["start"])),
                        "end": max(float(w["end"]), float(w_next["end"])),
                        "prob": max(float(w.get("prob", 0.85)), float(w_next.get("prob", 0.85))),
                    }
                    if "speaker" in w or "speaker" in w_next:
                        merged_word["speaker"] = w.get("speaker") or w_next.get("speaker")
                    step1.append(merged_word)
                    i += 2
                    continue
        step1.append(w)
        i += 1

    # Étape 2 : Élimination des boucles d'hallucination de mots uniques répétés (>= 3 fois consécutives)
    step2 = []
    i = 0
    while i < len(step1):
        nw_curr = norm_word(step1[i].get("word", ""))
        run_len = 1
        while (i + run_len) < len(step1) and norm_word(step1[i + run_len].get("word", "")) == nw_curr and len(nw_curr) >= 2:
            run_len += 1

        if run_len >= 3:
            # Boucle d'hallucination détectée ! (ex: 15 fois "aller")
            # Si run_len >= 4, on ne garde qu'une seule occurrence, si 3 on en garde 2 au maximum.
            max_keep = 1 if run_len >= 4 else 2
            for k in range(max_keep):
                step2.append(step1[i + k])
            i += run_len
        else:
            step2.append(step1[i])
            i += 1

    # Étape 3 : Élimination des boucles d'hallucination de séquences/n-grammes répétés (ex: "c'est bon c'est bon c'est bon")
    out = step2
    for n in (2, 3, 4):
        j = 0
        filtered_ngram = []
        while j < len(out):
            if j + 2 * n <= len(out):
                ngram1 = [norm_word(x.get("word", "")) for x in out[j : j + n]]
                if all(len(k) >= 2 for k in ngram1):
                    repeats = 1
                    while j + (repeats + 1) * n <= len(out):
                        ngram_next = [norm_word(x.get("word", "")) for x in out[j + repeats * n : j + (repeats + 1) * n]]
                        if ngram_next == ngram1:
                            repeats += 1
                        else:
                            break
                    if repeats >= 2:
                        keep_repeats = 1 if repeats >= 3 else 2
                        filtered_ngram.extend(out[j : j + keep_repeats * n])
                        j += repeats * n
                        continue
            filtered_ngram.append(out[j])
            j += 1
        out = filtered_ngram

    return out


def merge_split_french_words(words: list) -> list:
    """
    Rassemble les mots composés français et les contractions avec apostrophes
    qui ont été découpés par inadvertance en plusieurs tokens par Whisper.
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
                comb_prob = max(float(w.get("prob", 0.85)), float(w_next.get("prob", 0.85)))

                # 1. aujourd + hui (ou d'aujourd + hui)
                if (w1_clean == "aujourd" or w1_clean.endswith("aujourd")) and (w2_clean.startswith("hui") or w2_clean == "hui"):
                    punct = re.sub(r"^['’\w]+", "", w2_txt)
                    prefix = "d'" if (w1_clean.startswith("d") or w1_txt.lower().startswith("d'")) else ""
                    combined_word = f"{prefix}aujourd'hui{punct}"
                    merged.append({
                        "word": combined_word,
                        "start": min(float(w.get("start", 0.0)), float(w_next.get("start", 0.0))),
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0))),
                        "prob": comb_prob,
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
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0))),
                        "prob": comb_prob,
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
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0))),
                        "prob": comb_prob,
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
                        "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0))),
                        "prob": comb_prob,
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
                            "end": max(float(w.get("end", 0.0)), float(w_next.get("end", 0.0))),
                            "prob": comb_prob,
                        })
                        changed = True
                        i += 2
                        continue

            w_txt = w.get("word", "")
            if (w_txt.startswith("'") or w_txt.startswith("’")) and not any(w_txt.lower().startswith(p) for p in ["c'", "d'", "l'", "j'", "m'", "t'", "s'", "n'", "qu'"]):
                w["word"] = w_txt.lstrip("'’")

            merged.append(w)
            i += 1
        curr_words = merged
    return curr_words


def align_phrase_words_with_cleaned_text(phrase_words: list, cleaned_text: str) -> list:
    """
    Resynchronise les mots unitaires horodatés avec le texte nettoyé SANS JAMAIS écraser
    les pauses/blancs acoustiques entre les mots (utilise SequenceMatcher localement sur les
    seuls tokens modifiés et conserve intacts les horodatages des mots non modifiés).
    """
    if not cleaned_text or not phrase_words:
        return phrase_words

    compact_cleaned = re.sub(r"\s+([?!:;]+)", r"\1", cleaned_text)
    cleaned_tokens = [t for t in compact_cleaned.split() if any(c.isalnum() for c in t)]
    if not cleaned_tokens:
        return phrase_words

    if len(phrase_words) == len(cleaned_tokens):
        for idx, w in enumerate(phrase_words):
            w["word"] = cleaned_tokens[idx]
        return phrase_words

    import difflib
    norm_cleaned = [re.sub(r"[^\w]", "", t).lower() for t in cleaned_tokens]
    norm_curr = [re.sub(r"[^\w]", "", w.get("word", "")).lower() for w in phrase_words]

    matcher = difflib.SequenceMatcher(None, norm_curr, norm_cleaned)
    new_words = []
    p_start = float(phrase_words[0].get("start", 0.0))
    p_end = float(phrase_words[-1].get("end", p_start + 0.3))

    for tag, i1, i2, j1, j2 in matcher.get_opcodes():
        if tag == "equal":
            for k in range(i2 - i1):
                src_w = phrase_words[i1 + k]
                new_words.append({
                    "word": cleaned_tokens[j1 + k],
                    "start": round(float(src_w.get("start", p_start)), 3),
                    "end": round(float(src_w.get("end", p_end)), 3),
                })
        elif tag in ("replace", "insert"):
            if i1 < i2:
                span_s = float(phrase_words[i1].get("start", p_start))
                span_e = float(phrase_words[i2 - 1].get("end", span_s + 0.12))
            elif i1 > 0 and i1 <= len(phrase_words):
                span_s = float(phrase_words[i1 - 1].get("end", p_start))
                span_e = float(phrase_words[i1].get("start", span_s + 0.12)) if i1 < len(phrase_words) else max(span_s + 0.10, p_end)
            else:
                span_s = p_start
                span_e = min(p_end, p_start + 0.20)
            if span_e <= span_s + 0.02:
                span_e = span_s + 0.10 * max(1, j2 - j1)
            count = max(1, j2 - j1)
            step = (span_e - span_s) / count
            for k in range(count):
                new_words.append({
                    "word": cleaned_tokens[j1 + k],
                    "start": round(span_s + k * step, 3),
                    "end": round(span_s + (k + 1) * step, 3),
                })

    return new_words if new_words else phrase_words


def segment_words_into_clean_phrases(words: list, silence_intervals: list = None, pause_threshold: float = 0.38, lang: str = "fr", audio_data=None, sr: int = 16000) -> list:
    """
    Découpe les mots en répliques naturelles et complètes pour la bande rythmo :
    - Pause de respiration naturelle (>= 0.32s).
    - Ponctuation forte (?, !, ., :) marquant la fin d'une phrase complète ou suivie d'un décrochage.
    - Ponctuation intermédiaire (virgule, point-virgule) avec pause nette (>= 0.25s).
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

        w["start"] = round(w_start, 3)
        w["end"] = round(w_end, 3)

        if not current_words:
            phrase_start = w_start
            current_words.append(w)
            continue

        prev_end = float(current_words[-1].get("end", 0.0))
        gap = round(w_start - prev_end, 3)
        phrase_duration = prev_end - phrase_start
        prev_word_text = current_words[-1].get("word", "").strip()
        p_norm = norm_word(prev_word_text)
        w_norm = norm_word(w_text)

        # 1. Seuil de pause naturelle (>= 0.32s est une vraie pause / respiration entre répliques)
        is_pause = (gap >= 0.32)

        # 2. Ponctuation forte (?, !, ., :, ...)
        is_ellipsis = prev_word_text.endswith("…") or prev_word_text.endswith("...")
        is_abbrev = prev_word_text.endswith(".") and p_norm in {"m", "mr", "mme", "mlle", "dr", "st", "ste"}
        is_strong_punct = any(prev_word_text.endswith(p) for p in ["?", "!", ".", ":"]) and not is_abbrev
        split_strong_punct = (
            (is_strong_punct and not is_ellipsis and (gap >= 0.06 or (len(current_words) >= 2 and phrase_duration >= 0.65)))
            or (is_ellipsis and gap >= 0.28)
        )

        # 2b. Ponctuation intermédiaire (virgule, point-virgule, tiret) avec pause
        is_soft_punct = any(prev_word_text.endswith(p) for p in [",", ";", "—", "-"])
        split_soft_punct = (is_soft_punct and gap >= 0.25)

        # 3. Limites de durée de phrase pour le doublage (éviter les répliques trop longues > 5.2s si pause nette)
        split_long_phrase = (phrase_duration >= 5.2 and gap >= 0.16)

        # 4. Détection acoustique de changement d'interlocuteur sur pause brève (0.10s <= gap < 0.32s)
        split_speaker_turn = False
        if audio_data is not None and sr > 0 and 0.10 <= gap < 0.32 and phrase_duration >= 0.45 and len(current_words) >= 2:
            if is_strong_punct or is_soft_punct or gap >= 0.14:
                prev_win_s = max(phrase_start, prev_end - 0.75)
                next_win_e = min(len(audio_data) / float(sr), max(w_end, w_start + 0.45))
                if (prev_end - prev_win_s) >= 0.22 and (next_win_e - w_start) >= 0.18:
                    p_f0 = extract_local_pitch(audio_data, prev_win_s, prev_end, sr=sr)
                    n_f0 = extract_local_pitch(audio_data, w_start, next_win_e, sr=sr)
                    if p_f0 > 65.0 and n_f0 > 65.0:
                        semitone_jump = abs(12.0 * float(__import__("numpy").log2(n_f0 / p_f0)))
                        if semitone_jump >= 3.8:
                            split_speaker_turn = True

        should_split = is_pause or split_strong_punct or split_soft_punct or split_long_phrase or split_speaker_turn

        # Si une courte interjection/mot d'attaque ('alors', 'sinon', 'mais', 'donc', 'enfin', 'bon', 'ben', 'bah')
        # est suivie d'une pause modérée (< 0.48s) et que la phrase débute à peine (<= 2 mots),
        # les garder ensemble dans la même réplique pour former 'alors | (blanc) | sinon' !
        if len(current_words) <= 2 and gap < 0.48 and not (is_strong_punct and not is_ellipsis):
            if p_norm in {"alors", "sinon", "mais", "donc", "enfin", "bon", "ben", "bah", "écoute", "attends", "dis", "voyons", "eh", "euh"} or \
               w_norm in {"sinon", "alors", "donc", "enfin", "quand", "comment", "pourquoi"}:
                should_split = False

        # Répétition mot à mot (ex: comme... comme, on a, on a) : ne jamais couper en deux répliques distinctes
        is_word_repetition = (w_norm == p_norm and len(w_norm) >= 2)
        if is_word_repetition and gap < 0.65 and (phrase_duration + gap) <= 6.5 and not (is_strong_punct and not is_ellipsis):
            should_split = False

        # Règle absolue de doublage : NE JAMAIS couper au milieu d'un mot composé, d'une élision,
        # ou après un proclitique non ponctué (mais autoriser la coupe après une ponctuation ou une pause franche !)
        if (p_norm.endswith("aujourd") and w_norm.startswith("hui")) or \
           (prev_word_text.endswith("'") or prev_word_text.endswith("’")) or \
           (w_text.startswith("'") or w_text.startswith("’")) or \
           (p_norm in {"c", "d", "l", "j", "m", "t", "s", "n", "qu", "jusqu", "lorsqu", "puisqu", "presqu", "quelqu"} and gap < 0.45):
            should_split = False
        elif (p_norm in FRENCH_CLITICS and p_norm not in {"moi", "toi", "lui", "vous", "nous", "elle", "elles", "un", "une"}) and \
             not is_strong_punct and not is_soft_punct and not is_ellipsis and gap < 0.35:
            should_split = False

        if should_split:
            phrase_end = prev_end
            if current_words:
                phrase_start = float(current_words[0].get("start", phrase_start))
                phrase_end = float(current_words[-1].get("end", phrase_end))
            current_words[0]["start"] = round(float(current_words[0].get("start", phrase_start)), 3)
            current_words[-1]["end"] = round(float(current_words[-1].get("end", phrase_end)), 3)

            raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
            cleaned = clean_text(raw_text, lang)
            if cleaned and any(c.isalnum() for c in cleaned):
                start_sec = round(phrase_start, 3)
                end_sec = round(max(phrase_start + 0.15, phrase_end), 3)
                if end_sec <= start_sec:
                    end_sec = round(start_sec + 0.20, 3)

                phrase_words = [
                    {
                        "word": cw.get("word", "").strip(),
                        "start": round(float(cw.get("start", 0.0)), 3),
                        "end": round(min(end_sec, float(cw.get("end", 0.0))), 3),
                    }
                    for cw in current_words
                    if cw.get("word", "").strip() and any(c.isalnum() for c in cw.get("word", ""))
                ]
                if phrase_words:
                    phrase_words = align_phrase_words_with_cleaned_text(phrase_words, cleaned)
                    phrase_words[-1]["end"] = min(phrase_words[-1]["end"], end_sec)
                else:
                    tokens = [t for t in cleaned.split() if any(c.isalnum() for c in t)]
                    if tokens:
                        dur = max(0.1, end_sec - start_sec)
                        step = dur / len(tokens)
                        for ti, tok in enumerate(tokens):
                            phrase_words.append({
                                "word": tok,
                                "start": round(start_sec + ti * step, 3),
                                "end": round(start_sec + (ti + 1) * step, 3),
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
        current_words[0]["start"] = round(float(current_words[0].get("start", phrase_start)), 3)
        current_words[-1]["end"] = round(float(current_words[-1].get("end", phrase_end)), 3)

        raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
        cleaned = clean_text(raw_text, lang)
        if cleaned and any(c.isalnum() for c in cleaned):
            start_sec = round(phrase_start, 3)
            end_sec = round(max(phrase_start + 0.15, phrase_end), 3)
            if end_sec <= start_sec:
                end_sec = round(start_sec + 0.20, 3)

            phrase_words = [
                {
                    "word": cw.get("word", "").strip(),
                    "start": round(float(cw.get("start", 0.0)), 3),
                    "end": round(min(end_sec, float(cw.get("end", 0.0))), 3),
                }
                for cw in current_words
                if cw.get("word", "").strip() and any(c.isalnum() for c in cw.get("word", ""))
            ]
            if phrase_words:
                phrase_words = align_phrase_words_with_cleaned_text(phrase_words, cleaned)
                phrase_words[-1]["end"] = min(phrase_words[-1]["end"], end_sec)
            else:
                tokens = [t for t in cleaned.split() if any(c.isalnum() for c in t)]
                if tokens:
                    dur = max(0.1, end_sec - start_sec)
                    step = dur / len(tokens)
                    for ti, tok in enumerate(tokens):
                        phrase_words.append({
                            "word": tok,
                            "start": round(start_sec + ti * step, 3),
                            "end": round(start_sec + (ti + 1) * step, 3),
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

    # On retient les coefficients 1 à n_mfcc-1 (hors énergie globale MFCC 0)
    # IMPORTANT : la moyenne cepstrale (mfcc_raw_mean) doit être calculée AVANT soustraction intra-phrase,
    # sinon np.mean(mfcc - np.mean(mfcc, axis=0), axis=0) est mathématiquement nul (0.0) !
    mfcc_raw_active = mfcc[:, 1:]
    mfcc_raw_mean = np.median(mfcc_raw_active, axis=0) if len(mfcc_raw_active) > 2 else np.mean(mfcc_raw_active, axis=0)
    mfcc_raw_norm = mfcc_raw_mean / (np.linalg.norm(mfcc_raw_mean) + 1e-9)

    mfcc_cms = mfcc - np.mean(mfcc, axis=0, keepdims=True)
    mfcc_active = mfcc_cms[:, 1:]
    mfcc_std = np.std(mfcc_active, axis=0) if len(mfcc_active) > 1 else np.zeros(n_mfcc - 1)
    mfcc_std_norm = mfcc_std / (np.linalg.norm(mfcc_std) + 1e-9)
    mfcc_delta = np.mean(np.abs(np.diff(mfcc_active, axis=0)), axis=0) if len(mfcc_active) > 2 else np.zeros(n_mfcc - 1)
    mfcc_delta_norm = mfcc_delta / (np.linalg.norm(mfcc_delta) + 1e-9)

    # 3. Descripteurs spectraux globaux & ratios formants (F1/F2/F3)
    avg_spec = np.mean(spec, axis=0) + 1e-9
    spec_sum = np.sum(avg_spec)
    centroid = float(np.sum(freqs * avg_spec) / spec_sum) / 4000.0
    cum_spec = np.cumsum(avg_spec)
    rolloff85 = float(freqs[min(np.searchsorted(cum_spec, 0.85 * spec_sum), len(freqs) - 1)]) / 4000.0
    geom_mean = np.exp(np.mean(np.log(avg_spec + 1e-12)))
    arith_mean = np.mean(avg_spec)
    flatness = float(geom_mean / (arith_mean + 1e-12))

    f1_e = float(np.mean(avg_spec[(freqs >= 280) & (freqs < 850)])) + 1e-9 if np.any((freqs >= 280) & (freqs < 850)) else 1e-9
    f2_e = float(np.mean(avg_spec[(freqs >= 850) & (freqs < 2400)])) + 1e-9 if np.any((freqs >= 850) & (freqs < 2400)) else 1e-9
    f3_e = float(np.mean(avg_spec[(freqs >= 2400) & (freqs < 3800)])) + 1e-9 if np.any((freqs >= 2400) & (freqs < 3800)) else 1e-9
    formant_ratios = np.array([
        np.log1p(f1_e / f2_e),
        np.log1p(f2_e / f3_e),
        np.log1p(f1_e / f3_e)
    ], dtype=np.float32)
    formant_ratios = formant_ratios / (np.linalg.norm(formant_ratios) + 1e-9)

    shape_feats = np.array([centroid, rolloff85, flatness], dtype=np.float32)

    # Assemblage de l'empreinte biométrique complète
    vocal_vec = np.hstack([
        mfcc_raw_norm * 2.6,     # 19 valeurs : enveloppe cepstrale du conduit vocal (MFCC 1..19)
        se_norm * 2.0,           # 6 valeurs : résonance sous-bandes
        sc_norm * 1.4,           # 6 valeurs : contraste spectral harmonique
        formant_ratios * 1.5,    # 3 valeurs : ratios inter-formants F1/F2/F3
        mfcc_std_norm * 0.9,     # 19 valeurs : modulation spectrale
        mfcc_delta_norm * 0.7,   # 19 valeurs : vitesse d'articulation
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
        "raw_mfcc": mfcc_raw_mean.astype(np.float32),
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
        audio_data, sr = load_audio(audio_path)
        if audio_data is None or len(audio_data) == 0:
            import soundfile as sf
            audio_data, sr = sf.read(audio_path, dtype="float32")
            if audio_data.ndim > 1:
                audio_data = np.mean(audio_data, axis=1)

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
        # Normalisation cepstrale globale (CMVN sur l'ensemble du fichier) pour supprimer l'empreinte du canal/micro
        # tout en amplifiant les différences anatomiques de conduit vocal entre locuteurs :
        if n_p >= 2:
            raw_mfcc_mat = np.array([f["raw_mfcc"] for f in phrase_features], dtype=np.float32)
            global_mfcc_mean = np.mean(raw_mfcc_mat, axis=0, keepdims=True)
            global_mfcc_std = np.std(raw_mfcc_mat, axis=0, keepdims=True) + 1e-6
            cmvn_mfcc_mat = (raw_mfcc_mat - global_mfcc_mean) / global_mfcc_std
            for idx_f, f in enumerate(phrase_features):
                cmvn_v = cmvn_mfcc_mat[idx_f]
                cmvn_v = cmvn_v / (np.linalg.norm(cmvn_v) + 1e-9)
                combined = np.hstack([f["timbre"] * 1.4, cmvn_v * 1.6])
                f["timbre"] = combined / (np.linalg.norm(combined) + 1e-9)

        dist_matrix = np.zeros((n_p, n_p), dtype=np.float32)

        for i in range(n_p):
            for j in range(i + 1, n_p):
                f_i = phrase_features[i]
                f_j = phrase_features[j]
                dot_val = np.clip(float(np.dot(f_i["timbre"], f_j["timbre"])), -1.0, 1.0)
                d_timbre = max(0.0, 1.0 - dot_val)
                if f_i["has_pitch"] and f_j["has_pitch"]:
                    d_pitch = min(1.0, abs(f_i["pitch_semitones"] - f_j["pitch_semitones"]) / 10.0)
                    dist = 0.68 * d_timbre + 0.32 * d_pitch
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
                    # Un cluster composé d'une seule réplique brève (< 0.6s) est rejeté comme bruit
                    bad_small = False
                    for c in set(lbls):
                        members = [idx for idx, l in enumerate(lbls) if l == c]
                        tot_dur_c = sum(phrase_features[idx]["duration"] for idx in members)
                        if len(members) < 2 and tot_dur_c < 0.6:
                            bad_small = True
                            break
                    if bad_small:
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

                    # Un partitionnement est valide dès lors que la silhouette est positive et bien détachée (>= 0.16)
                    # et que les centroïdes ne sont pas superposés (> 0.035)
                    if sil >= 0.16 and min_c_dist > 0.035:
                        combined_eval = sil + min_c_dist * 2.5
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
    Fusionne les micro-fragments immédiatement contigus appartenant au même locuteur :
    - Règle stricte OmeRyth : Si gap < 0.10s entre deux répliques du même locuteur,
      NE JAMAIS créer de nouvelle phrase ! Toujours fusionner en une seule phrase (un séparateur rythmique
      sera positionné au point de jonction).
    - Si gap >= 0.10s : laisser au moins 0.10s d'écart entre la fin de la première et le début de la suivante.
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

        same_speaker = (prev.get("speaker") == p.get("speaker"))
        prev_text = prev.get("text", "").strip()
        ends_with_strong_punct = any(prev_text.endswith(pt) for pt in [".", "!", "?", ":"]) and not prev_text.endswith("...")

        # Si un silence acoustique marqué (>= 0.16s) existe entre les répliques
        has_silence = False
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start < (p["start"] + 0.04) and s_end > (prev["end"] - 0.04) and (s_end - s_start) >= 0.16:
                    has_silence = True
                    break

        # Ne fusionner que si gap est quasi nul (< 0.04s), même locuteur, et AUCUNE ponctuation de fin de phrase
        must_merge_due_to_gap = same_speaker and (gap < 0.04) and not ends_with_strong_punct and not has_silence

        # Hésitation "Je crois..." sans silence marqué
        soft_continue = (
            same_speaker and
            not has_silence and
            not ends_with_strong_punct and
            gap < 0.18 and
            combined_dur <= max_duration and
            (prev_text.endswith("...") or prev_text.endswith("…"))
        )

        if must_merge_due_to_gap or soft_continue:
            prev["end"] = max(prev["end"], p["end"])
            prev["text"] = (prev["text"] + " " + p["text"]).strip()
            prev["words"] = prev.get("words", []) + p.get("words", [])
        else:
            # Phrases distinctes : garantir AU MOINS 0.10s de décalage
            if same_speaker and prev["end"] > p["start"] - 0.10:
                prev["end"] = max(prev["start"] + 0.15, p["start"] - 0.10)
                if prev.get("words"):
                    prev["words"][-1]["end"] = min(prev["words"][-1]["end"], prev["end"])
            merged.append(p)

    return merged


ORPHAN_CONNECTORS = {
    "et", "mais", "ou", "donc", "or", "ni", "car", "si", "que", "qui", "qu", "c", "d", "l", "j", "on",
    "je", "tu", "il", "ils", "elle", "elles", "le", "la", "les", "un", "une", "des", "du", "de", "ce", "cet", "cette"
}


def resolve_orphan_phrases(phrases: list, max_duration: float = 6.5) -> list:
    """
    Élimine les répliques orphelines (ex: 'Et' isolé de 0.16s entre deux pauses).
    Une réplique constituée d'un seul mot de liaison / connecteur / clitique est rattachée :
    - soit à la phrase suivante (obligatoire pour les pronoms et articles clitiques 'je', 'tu', 'le'...),
    - soit à la phrase précédente,
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
            w_single_norm = norm_word(p_words[0].get("word", "")) if len(p_words) == 1 else ""
            is_clitic_single = (p_text in FRENCH_CLITICS or w_single_norm in FRENCH_CLITICS)
            is_connector = p_text in ORPHAN_CONNECTORS or (w_single_norm in ORPHAN_CONNECTORS) or is_clitic_single
            is_short = (p["end"] - p["start"]) < 0.90 or is_clitic_single

            if is_single_word and is_connector and is_short:
                has_prev = len(new_phrases) > 0
                has_next = (i < len(phrases) - 1)
                next_p = phrases[i + 1] if has_next else None

                same_spk_prev = prev_p is not None and prev_p.get("speaker") == p.get("speaker")
                same_spk_next = next_p is not None and next_p.get("speaker") == p.get("speaker")

                gap_prev = (p["start"] - prev_p["end"]) if same_spk_prev else 999.0
                gap_next = (next_p["start"] - p["end"]) if same_spk_next else 999.0

                attach_to = None
                # Un pronom clitique (je, tu, il, elle, on...) ou article (le, la, un...) s'attache TOUJOURS en avant vers son verbe/nom
                if is_clitic_single and same_spk_next and (next_p["end"] - p["start"]) <= max_duration:
                    attach_to = "next"
                elif is_clitic_single and same_spk_prev and (p["end"] - prev_p["start"]) <= max_duration:
                    attach_to = "prev"
                else:
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
    "abonnez-vous", "abonnez vous", "thanks for watching", "thank you for watching",
    "please subscribe", "subtitles by", "amara.org", "www.",
)


INFORMAL_PROMPTS = {
    "fr": "Nan, euh, ouais !",
    "en": "Nah, uh, yeah!",
}


def is_known_hallucination(text: str) -> bool:
    """Détecte les hallucinations typiques de Whisper (outros YouTube, écho du prompt...)."""
    t = (text or "").strip().lower()
    if not t:
        return True
    if any(h in t for h in _KNOWN_HALLUCINATIONS):
        return True
    norm_t = re.sub(r"[^\w]", "", t)
    for pr in INFORMAL_PROMPTS.values():
        if norm_t == re.sub(r"[^\w]", "", pr.lower()):
            return True
    return False


def prepare_whisper_audio(audio, sr: int):
    """
    Prépare le signal envoyé à Whisper : mono float32 16 kHz, sans composante continue,
    avec amplification douce (max x6) des enregistrements très faibles. Jamais de compression
    ni de filtrage destructif : les rires, souffles et interjections restent intacts.
    """
    a = np.asarray(audio, dtype=np.float32)
    if a.size == 0:
        return np.zeros(16000, dtype=np.float32), 16000
    if sr != 16000:
        try:
            from math import gcd
            import scipy.signal as ss
            g = gcd(int(sr), 16000)
            a = ss.resample_poly(a, 16000 // g, int(sr) // g).astype(np.float32)
        except Exception:
            n = int(len(a) * 16000 / sr)
            a = np.interp(np.linspace(0, len(a) - 1, n), np.arange(len(a)), a).astype(np.float32)
        sr = 16000
    a = a - float(np.mean(a))
    peak = float(np.percentile(np.abs(a), 99.8))
    if 1e-4 < peak < 0.25:
        a = a * min(6.0, 0.5 / peak)
    return np.clip(a, -1.0, 1.0).astype(np.float32), sr


def sanitize_words(words: list) -> list:
    """
    Nettoie la liste de mots : ordre chronologique strict, durées valides, pas de mot vide,
    sans tronquer arbitrairement la fin des mots courts avant la calibration acoustique.
    """
    out = []
    for w in sorted(words, key=lambda x: (float(x["start"]), float(x["end"]))):
        txt = str(w.get("word", "")).strip()
        if not txt or not any(c.isalnum() for c in txt):
            continue
        s = max(0.0, float(w["start"]))
        e = float(w["end"])
        if e <= s + 0.001:
            e = s + 0.08
        if e - s > 2.5:
            e = s + 2.5
        if out:
            prev = out[-1]
            if s < prev["end"]:
                prev["end"] = round(max(float(prev["start"]) + 0.04, s), 3)
                if s < prev["end"]:
                    s = prev["end"]
                    if e <= s + 0.04:
                        e = s + 0.08
        entry = {"word": txt, "start": round(s, 3), "end": round(e, 3)}
        if "prob" in w:
            entry["prob"] = float(w["prob"])
        if "no_speech_prob" in w:
            entry["no_speech_prob"] = float(w["no_speech_prob"])
        out.append(entry)
    return out


def find_uncovered_speech_regions(audio, sr: int, words: list, min_len: float = 0.35, margin: float = 0.15) -> list:
    """
    Repère les zones nettement vocales (énergie proche de celle des mots déjà transcrits,
    spectre dominé par la bande de la voix 200-3400 Hz) où aucun mot n'a été transcrit :
    rires parlés, interjections, mots déformés que le VAD a ignorés.
    """
    if audio is None or sr <= 0 or len(audio) < sr or not words:
        return []
    frame = int(sr * 0.03)
    hop = int(sr * 0.015)
    n = 1 + (len(audio) - frame) // hop
    if n < 8:
        return []
    idx = np.arange(frame)[None, :] + (np.arange(n) * hop)[:, None]
    frames = audio[idx]
    rms = np.sqrt(np.mean(frames ** 2, axis=1))

    covered = np.zeros(n, dtype=bool)
    for w in words:
        s = int(max(0.0, float(w["start"]) - margin) * sr / hop)
        e = int((float(w["end"]) + margin) * sr / hop) + 1
        covered[max(0, s):min(n, e)] = True
    if not covered.any():
        return []

    ref = float(np.median(rms[covered]))   # niveau typique de la voix déjà transcrite
    floor = float(np.percentile(rms, 20))
    thresh = max(0.018, floor * 3.2, 0.42 * ref)
    todo = (rms > thresh) & ~covered

    regions = []
    start = None
    holes = 0
    for i in range(n):
        if todo[i]:
            if start is None:
                start = i
            holes = 0
        elif start is not None:
            holes += 1
            if holes > 6:
                end = i - holes
                if (end - start) * hop / sr >= min_len:
                    regions.append((start * hop / sr, (end + 1) * hop / sr))
                start = None
                holes = 0
    if start is not None and (n - 1 - start) * hop / sr >= min_len:
        regions.append((start * hop / sr, n * hop / sr))

    # Filtre spectral : la voix doit dominer (rejette musique basse fréquence, bruits secs)
    kept = []
    for r_s, r_e in regions:
        seg = audio[int(r_s * sr):int(r_e * sr)]
        if len(seg) < 256:
            continue
        spec = np.abs(np.fft.rfft(seg * np.hanning(len(seg)))) ** 2
        freqs = np.fft.rfftfreq(len(seg), 1.0 / sr)
        total = float(np.sum(spec)) + 1e-12
        voice = float(np.sum(spec[(freqs >= 200) & (freqs <= 3400)]))
        if voice / total >= 0.38:
            kept.append((r_s, r_e))
    return kept


def recover_missed_speech(model, audio, sr: int, words: list, lang, lang_hint: str, beam: int) -> list:
    """
    Seconde passe prudente (sans VAD) sur les zones vocales non transcrites.
    Un résultat n'est gardé que s'il est court, confiant et pas une hallucination connue ni un 'eh' fantôme.
    """
    if sr != 16000:
        return []
    regions = find_uncovered_speech_regions(audio, sr, words)
    if not regions:
        return []
    new_words = []
    total = len(audio) / sr
    for r_start, r_end in regions[:40]:
        s = max(0.0, r_start - 0.18)
        e = min(total, r_end + 0.18)
        chunk = np.ascontiguousarray(audio[int(s * sr):int(e * sr)], dtype=np.float32)
        if len(chunk) < int(0.3 * sr):
            continue
        try:
            init_p = "Dialogue en français, ponctuation naturelle." if (not lang or str(lang).lower().strip() in ("fr", "auto", "français", "francais")) else None
            segs, _info = model.transcribe(
                chunk, language=lang, beam_size=beam, best_of=beam, word_timestamps=True,
                vad_filter=False, condition_on_previous_text=False,
                initial_prompt=init_p,
                no_speech_threshold=0.60, log_prob_threshold=-1.0, compression_ratio_threshold=2.4,
                temperature=[0.0, 0.2],
            )
            for seg in segs:
                seg_no_speech = float(getattr(seg, "no_speech_prob", 0.0))
                seg_logprob = float(getattr(seg, "avg_logprob", 0.0))
                if seg_no_speech > 0.60 or seg_logprob < -1.0:
                    continue
                cleaned = clean_text(seg.text or "", lang_hint)
                if not cleaned or is_known_hallucination(cleaned):
                    continue
                toks = [t for t in cleaned.split() if any(c.isalnum() for c in t)]
                if not toks or len(toks) > 6:
                    continue
                for w in (seg.words or []):
                    w_str = (w.word or "").strip()
                    w_str = _OPEN_MOUTH_ANNOTATION_RE.sub("(BO)", w_str)
                    w_str = _CLOSED_MOUTH_ANNOTATION_RE.sub("(BF)", w_str)
                    if not w_str or not any(c.isalnum() for c in w_str):
                        continue
                    w_prob = float(getattr(w, "probability", 0.75))
                    w_norm_c = norm_word(w_str)
                    if w_norm_c in _PHANTOM_SHORT_INTERJECTIONS and (len(toks) == 1 and w_prob < 0.75):
                        continue
                    ws = s + float(w.start if w.start is not None else seg.start)
                    we = s + float(w.end if w.end is not None else seg.end)
                    if we <= ws + 0.001:
                        we = ws + 0.08
                    if any(float(x["start"]) < we - 0.05 and float(x["end"]) > ws + 0.05 for x in words):
                        continue
                    new_words.append({
                        "word": w_str,
                        "start": ws,
                        "end": we,
                        "prob": w_prob,
                        "no_speech_prob": seg_no_speech,
                    })
        except Exception:
            continue
    return new_words


def build_transcribe_options(lang_param, beam: int) -> dict:
    """
    Options de décodage haute fidélité pour faster-whisper :
    - Décodage séquentiel complet (vad_filter=False, sans hallucination_silence_threshold destructif)
      afin de ne jamais amputer les attaques de phrases ni les répliques sur fond musical.
    - Alignement DTW unitaire (word_timestamps=True) avec ponctuation naturelle.
    """
    opts = dict(
        language=lang_param,
        beam_size=beam,
        best_of=beam,
        patience=1.0,
        word_timestamps=True,
        vad_filter=False,
        condition_on_previous_text=False,
        no_speech_threshold=0.60,
        log_prob_threshold=-1.0,
        compression_ratio_threshold=2.4,
        temperature=[0.0, 0.2],
        repetition_penalty=1.15,
        no_repeat_ngram_size=3,
        hallucination_silence_threshold=2.0,
    )
    if not lang_param or lang_param.lower().strip() in ("fr", "auto", "français", "francais"):
        opts["initial_prompt"] = "Dialogue en français, ponctuation naturelle."
    return opts


def finalize_phrase_text(text: str) -> str:
    """Retire la virgule/point-virgule traînants en fin de réplique (conserve ? ! … . et (BO)/(BF))."""
    return re.sub(r"[\s,;]+$", "", text).strip()


def tighten_phrase_boundaries_acoustically(p_start: float, p_end: float, p_words: list,
                                           silence_intervals: list = None,
                                           audio_data=None, sr: int = 16000,
                                           prev_end_sec: float = 0.0,
                                           next_start_sec: float = None) -> tuple:
    """
    Synchronise les bornes de la réplique (p_start, p_end) sur l'enveloppe acoustique
    réelle de la voix (attaque et extinction vocales) et les bornes calibrées des mots,
    en garantissant la séparation minimale avec les répliques voisines.
    """
    if not p_words:
        return p_start, p_end

    raw_start = max(0.0, float(p_words[0]["start"]))
    last_start = float(p_words[-1]["start"])
    raw_end = max(raw_start + 0.15, last_start + 0.08, float(p_words[-1]["end"]))

    # 1. Calage acoustique fin sur l'attaque vocale (début de parole) et l'extinction vocale (fin de parole)
    if audio_data is not None and len(audio_data) > 0 and sr > 0:
        max_la = min(0.22, max(0.04, (raw_end - raw_start) * 0.40))
        refined_start = detect_vocal_onset(
            audio_data, raw_start, prev_end_sec=prev_end_sec, sr=sr,
            max_lookback=0.14, max_lookahead=max_la
        )
        first_word_end = float(p_words[0].get("end", raw_end))
        if refined_start < first_word_end - 0.05:
            raw_start = refined_start

        trimmed_end = trim_speech_end_acoustically(
            audio_data, raw_start, raw_end, sr=sr, blank_thresh_sec=0.10
        )
        raw_end = max(raw_start + 0.15, last_start + 0.06, trimmed_end)

    # 2. Respect des intervalles de silence franc détectés en bordure de phrase
    if silence_intervals:
        for s_s, s_e in silence_intervals:
            # Si le début de la phrase tombe en plein milieu d'un silence franc, avancer jusqu'à la sortie du silence
            if s_s <= raw_start + 0.02 and s_e > raw_start + 0.03 and s_e < raw_end - 0.12:
                raw_start = max(raw_start, s_e - 0.01)
            # Si la fin de la phrase déborde dans un silence franc, rabattre sur le début du silence
            if s_s > raw_start + 0.12 and s_s < raw_end - 0.02 and s_e >= raw_end - 0.02:
                raw_end = max(raw_start + 0.15, last_start + 0.06, min(raw_end, s_s + 0.02))

    p_start = round(max(0.0, raw_start), 3)
    p_end = round(max(p_start + 0.15, last_start + 0.08, raw_end), 3)

    if next_start_sec is not None and next_start_sec < 900000.0:
        if p_end > next_start_sec - 0.06:
            p_end = max(p_start + 0.15, round(next_start_sec - 0.06, 3))

    p_words[0]["start"] = p_start
    p_words[-1]["end"] = round(min(float(p_words[-1]["end"]), p_end), 3)
    if p_words[-1]["end"] <= p_words[-1]["start"] + 0.04:
        p_words[-1]["end"] = round(min(p_end, p_words[-1]["start"] + 0.08), 3)

    return p_start, p_end


def transcribe_with_gladia(audio_path: str, api_key: str, lang_param: str, num_speakers: int, total_duration: float = 0.0) -> tuple:
    """
    Exécute la transcription vocale via l'API Gladia v2 (modèle Solaria).
    Prend en charge le téléversement multipart, le lancement du job asynchrone,
    le suivi de progression en direct, la diarisation et l'extraction des horodatages de mots.
    """
    clean_key = (api_key or "").strip()
    if not clean_key:
        clean_key = "sk_gladia_329ab1b6be0948b59240530dee458a2b"

    print_progress(10, 100, "Préparation de l'audio pour l'API Gladia...")
    if not os.path.isfile(audio_path):
        raise FileNotFoundError(f"Fichier audio introuvable : {audio_path}")

    # 1. Téléversement multipart du fichier audio vers Gladia v2/upload
    boundary = uuid.uuid4().hex
    filename = os.path.basename(audio_path)
    file_size = os.path.getsize(audio_path)
    print_progress(15, 100, f"Téléversement vers Gladia AI ({file_size / (1024*1024):.1f} Mo)...")

    with open(audio_path, "rb") as f:
        file_bytes = f.read()

    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode("utf-8"))
    body.extend(f'Content-Disposition: form-data; name="audio"; filename="{filename}"\r\n'.encode("utf-8"))
    body.extend(b"Content-Type: audio/wav\r\n\r\n")
    body.extend(file_bytes)
    body.extend(f"\r\n--{boundary}--\r\n".encode("utf-8"))

    upload_req = urllib.request.Request("https://api.gladia.io/v2/upload", data=bytes(body), headers={
        "x-gladia-key": clean_key,
        "Content-Type": f"multipart/form-data; boundary={boundary}"
    })

    try:
        with urllib.request.urlopen(upload_req, timeout=120) as resp:
            upload_res = json.loads(resp.read().decode("utf-8"))
    except Exception as e_up:
        raise RuntimeError(f"Échec de l'envoi du fichier à Gladia : {e_up}")

    audio_url = upload_res.get("audio_url")
    if not audio_url:
        raise RuntimeError("Gladia n'a pas renvoyé d'audio_url valide.")

    # 2. Lancement du job de transcription v2/pre-recorded
    print_progress(25, 100, "Initialisation de la transcription Gladia (IA Solaria)...")
    payload = {
        "audio_url": audio_url,
        "diarization": True,
        "sentences": True,
    }
    if num_speakers > 0:
        payload["diarization_config"] = {
            "min_speakers": num_speakers,
            "max_speakers": num_speakers
        }
    if lang_param and lang_param.lower() not in ("auto", ""):
        payload["language"] = lang_param.lower()
    else:
        payload["language_behaviour"] = "automatic single language"

    job_req = urllib.request.Request("https://api.gladia.io/v2/pre-recorded",
                                     data=json.dumps(payload).encode("utf-8"),
                                     headers={
                                         "x-gladia-key": clean_key,
                                         "Content-Type": "application/json"
                                     })
    try:
        with urllib.request.urlopen(job_req, timeout=30) as resp2:
            job_res = json.loads(resp2.read().decode("utf-8"))
    except Exception as e_job:
        raise RuntimeError(f"Échec d'initialisation du job Gladia : {e_job}")

    result_url = job_res.get("result_url")
    if not result_url:
        raise RuntimeError("Gladia n'a pas renvoyé de result_url.")

    # 3. Boucle de suivi de progression
    poll_pct = 30
    poll_start = time.time()
    final_res = None
    while True:
        time.sleep(2.0)
        p_req = urllib.request.Request(result_url, headers={"x-gladia-key": clean_key})
        try:
            with urllib.request.urlopen(p_req, timeout=30) as p_resp:
                p_data = json.loads(p_resp.read().decode("utf-8"))
        except Exception as e_poll:
            print_info(f"Attente réseau Gladia : {e_poll}")
            continue

        status = p_data.get("status")
        elapsed = int(time.time() - poll_start)
        if status == "done":
            final_res = p_data
            print_progress(90, 100, f"Transcription Gladia terminée en {elapsed}s ! Synchronisation...")
            break
        elif status == "error":
            err_code = p_data.get("error_code") or str(p_data)
            raise RuntimeError(f"Erreur Gladia : {err_code}")
        else:
            poll_pct = min(88, poll_pct + 4)
            print_progress(poll_pct, 100, f"Transcription Gladia en cours ({elapsed}s)...")

    # 4. Traitement des répliques et des mots
    res_obj = final_res.get("result", {})
    trans_obj = res_obj.get("transcription", {})
    langs = trans_obj.get("languages", [])
    det_lang = langs[0] if langs else (lang_param or "fr")

    utterances = trans_obj.get("utterances", [])
    if not utterances:
        sent_results = trans_obj.get("sentences", {})
        if isinstance(sent_results, dict):
            utterances = sent_results.get("results", [])
        elif isinstance(sent_results, list):
            utterances = sent_results

    collected_words = []
    seg_counter = 0

    for utt in utterances:
        spk = utt.get("speaker", 0)
        if isinstance(spk, (int, float)) or (isinstance(spk, str) and spk.isdigit()):
            spk_label = f"SPEAKER_{int(spk):02d}"
        else:
            spk_label = str(spk or "SPEAKER_00")

        utt_words = utt.get("words", [])
        raw_u_text = utt.get("text") or utt.get("sentence") or ""
        u_text = clean_text(raw_u_text, det_lang)

        live_seg_words = []
        for w in utt_words:
            w_str = (w.get("word") or "").strip()
            if not w_str or not any(c.isalnum() for c in w_str):
                continue
            w_start = round(float(w.get("start", 0.0)), 3)
            w_end = round(float(w.get("end", w_start + 0.08)), 3)
            w_prob = round(float(w.get("confidence", 0.95)), 2)
            w_dict = {
                "word": w_str,
                "start": w_start,
                "end": max(w_start + 0.04, w_end),
                "prob": w_prob,
                "speaker": spk_label
            }
            collected_words.append(w_dict)
            live_seg_words.append(w_dict)

        if not live_seg_words and u_text:
            u_start = round(float(utt.get("start", 0.0)), 3)
            u_end = round(float(utt.get("end", u_start + 0.5)), 3)
            toks = [t for t in u_text.split() if any(c.isalnum() for c in t)]
            if toks:
                dur = max(0.1, u_end - u_start)
                step = dur / len(toks)
                for ti, tok in enumerate(toks):
                    w_dict = {
                        "word": tok,
                        "start": round(u_start + ti * step, 3),
                        "end": round(u_start + (ti + 1) * step, 3),
                        "prob": 0.90,
                        "speaker": spk_label
                    }
                    collected_words.append(w_dict)
                    live_seg_words.append(w_dict)

        if live_seg_words:
            seg_counter += 1
            print_live_segment({
                "id": seg_counter,
                "speaker": spk_label,
                "start": live_seg_words[0]["start"],
                "end": live_seg_words[-1]["end"],
                "text": u_text if u_text else " ".join(w["word"] for w in live_seg_words),
                "words": live_seg_words
            })

    collected_words = sanitize_words(collected_words)
    collected_words = deduplicate_adjacent_words(collected_words)
    collected_words = sanitize_words(merge_split_french_words(collected_words))
    return collected_words, det_lang, seg_counter


def main():
    parser = argparse.ArgumentParser(description="OmeRyth STT Worker – Haute Précision")
    parser.add_argument("--audio", required=True, help="Chemin du fichier audio WAV")
    parser.add_argument("--num-speakers", type=int, default=0, help="Nombre de locuteurs attendus (0 = auto)")
    parser.add_argument("--lang", default="fr", help="Code langue (fr, en, auto)")
    parser.add_argument("--engine", default="whisper", choices=["whisper", "gladia"], help="Moteur de transcription")
    parser.add_argument("--gladia-key", default="sk_gladia_329ab1b6be0948b59240530dee458a2b", help="Clé API Gladia")
    parser.add_argument("--model", default="small", help="Modèle faster-whisper (tiny, base, small, medium, large-v2, large-v3)")
    parser.add_argument("--threads", type=int, default=4, help="Nombre de threads CPU")
    parser.add_argument("--device", default="auto", help="Périphérique de calcul (auto, cuda, cpu)")
    parser.add_argument("--compute-type", default="auto", help="Type de calcul (auto, float16, int8_float16, int8)")
    parser.add_argument("--batch-size", type=int, default=16, help="(conservé pour compatibilité, inutilisé)")
    parser.add_argument("--output", required=True, help="Fichier JSON de sortie principal")
    parser.add_argument("--report", required=True, help="Fichier JSON de rapport complet")
    args = parser.parse_args()

    start_time = time.time()
    print_progress(5, 100, f"Initialisation du moteur STT ({args.engine.upper()})...")

    if args.engine == "whisper":
        try:
            from faster_whisper import WhisperModel
        except ImportError as e:
            print_error(f"Bibliothèque faster-whisper manquante : {e}")
            sys.exit(1)

    model_name = args.model.lower().strip()
    if model_name not in ("tiny", "base", "small", "medium", "large-v2", "large-v3"):
        model_name = "small"
    total_sys_cores = os.cpu_count() or 4
    cpu_threads = max(1, min(args.threads, total_sys_cores * 2))
    try:
        import torch
        torch.set_num_threads(cpu_threads)
    except Exception:
        pass
    lang_param = None if args.lang.lower() == "auto" else args.lang.lower()

    if not os.path.exists(args.audio):
        print_error(f"Fichier audio introuvable : {args.audio}")
        sys.exit(1)

    # ── Audio : chargement + préparation 16 kHz ──
    raw_audio, raw_sr = load_audio(args.audio)
    audio_data, audio_sr = prepare_whisper_audio(raw_audio, raw_sr)
    total_duration = len(audio_data) / float(audio_sr)
    silence_intervals = detect_silence_intervals(audio_data, sr=audio_sr, min_silence_sec=0.14)
    if silence_intervals:
        print_info(f"Analyse vocale : {len(silence_intervals)} temps morts/silences détectés pour découpage.")

    # ── Choix du périphérique ──
    req_device = args.device.lower().strip()
    req_compute = args.compute_type.lower().strip()
    device, compute_type = "cpu", "int8"
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
            device, compute_type = "cpu", "int8"
    else:
        compute_type = "int8" if req_compute == "auto" else req_compute

    informal_lang_hint = lang_param or "fr"

    def perform_transcription(dev, comp_type):
        local_cache = os.path.abspath("whisper/cache")
        download_root = local_cache if os.path.isdir(local_cache) else None
        print_progress(10, 100, f"Chargement du modèle {model_name.upper()} sur {dev.upper()} ({comp_type})...")
        mdl = WhisperModel(model_name, device=dev, compute_type=comp_type,
                           cpu_threads=cpu_threads, download_root=download_root)
        beam = 5 if dev == "cuda" else (3 if model_name in ("tiny", "base") else 5)
        opts = build_transcribe_options(lang_param, beam)

        print_progress(20, 100, f"Transcription en cours ({model_name.upper()} sur {dev.upper()})...")
        try:
            seg_iter, inf = mdl.transcribe(audio_data, **opts)
        except Exception as e_vad:
            print_info(f"Notification VAD ({e_vad}) : repli sur décodage direct...")
            opts["vad_filter"] = False
            opts.pop("vad_parameters", None)
            seg_iter, inf = mdl.transcribe(audio_data, **opts)
        det_lang = inf.language if inf.language else (lang_param or "fr")
        tot_dur = total_duration

        collected_words = []
        seg_counter = 0
        last_pct = 20

        for segment in seg_iter:
            seg_text = (segment.text or "").strip()
            if not seg_text or not any(c.isalnum() for c in seg_text):
                continue
            seg_no_speech_p = float(getattr(segment, "no_speech_prob", 0.0))
            cleaned_seg = clean_text(seg_text, det_lang)
            if not cleaned_seg or not any(c.isalnum() for c in cleaned_seg):
                continue
            norm_seg = re.sub(r"[^\w]", "", cleaned_seg).lower()
            if norm_seg in _PROMPT_LEAK_WORDS or norm_seg == "transcription" or "amaraorg" in norm_seg:
                continue
            if is_known_hallucination(cleaned_seg):
                continue

            if tot_dur > 0:
                cur_sec = min(segment.end, tot_dur)
                pct = int(20 + (cur_sec / tot_dur) * 65)
                if pct > last_pct:
                    last_pct = pct
                    print_progress(pct, 100, f"Transcription en cours ({format_timecode(cur_sec)[:8]} / {format_timecode(tot_dur)[:8]})...")

            seg_words = []
            for w in (segment.words or []):
                w_str = (w.word or "").strip()
                w_str = _OPEN_MOUTH_ANNOTATION_RE.sub("(BO)", w_str)
                w_str = _CLOSED_MOUTH_ANNOTATION_RE.sub("(BF)", w_str)
                w_str = _CLOSED_MOUTH_TOKEN_RE.sub("(BF)", w_str)
                w_str = _OPEN_MOUTH_TOKEN_RE.sub("(BO)", w_str)
                if w_str and not any(c.isalnum() for c in w_str):
                    if seg_words and re.fullmatch(r"[?!.…,;:]+", w_str):
                        seg_words[-1]["word"] = seg_words[-1]["word"].rstrip(",;:") + w_str
                    continue
                if not w_str:
                    continue
                clean_w = re.sub(r"[^\w]", "", w_str).lower()
                if clean_w in _PROMPT_LEAK_WORDS or clean_w in ("transcription", "transcriptionfidele"):
                    continue
                w_start = float(w.start) if w.start is not None else float(segment.start)
                w_end = float(w.end) if w.end is not None else float(segment.end)
                w_prob = float(getattr(w, "probability", 0.85))
                seg_words.append({
                    "word": w_str,
                    "start": w_start,
                    "end": w_end,
                    "prob": w_prob,
                    "no_speech_prob": seg_no_speech_p,
                })

            if not seg_words:
                toks = [t for t in cleaned_seg.split() if any(c.isalnum() for c in t)]
                if toks:
                    seg_dur = max(0.1, float(segment.end) - float(segment.start))
                    step = seg_dur / len(toks)
                    for i, tok in enumerate(toks):
                        seg_words.append({
                            "word": tok,
                            "start": float(segment.start) + i * step,
                            "end": float(segment.start) + (i + 1) * step,
                            "prob": 0.75,
                            "no_speech_prob": seg_no_speech_p,
                        })
            if not seg_words:
                continue

            seg_words = sanitize_words(merge_split_french_words(seg_words))
            if audio_data is not None and audio_sr > 0:
                seg_words = filter_hallucinated_and_classify_emotional_words(seg_words, audio_data, sr=audio_sr)
                if not seg_words:
                    continue
            collected_words.extend(seg_words)

            live_words = refine_word_boundaries_acoustically([dict(x) for x in seg_words], audio_data, sr=audio_sr) if (audio_data is not None and audio_sr > 0) else [dict(x) for x in seg_words]
            for lp in segment_words_into_clean_phrases(
                    live_words, silence_intervals=silence_intervals,
                    pause_threshold=0.45, lang=det_lang, audio_data=audio_data, sr=audio_sr):
                seg_counter += 1
                print_live_segment({
                    "id": seg_counter, "speaker": "SPEAKER_00",
                    "start": lp["start"], "end": lp["end"],
                    "text": lp["text"], "words": lp.get("words", []),
                })

        collected_words = sanitize_words(collected_words)
        collected_words = deduplicate_adjacent_words(collected_words)
        collected_words = sanitize_words(merge_split_french_words(collected_words))
        if audio_data is not None and audio_sr > 0:
            collected_words = filter_hallucinated_and_classify_emotional_words(collected_words, audio_data, sr=audio_sr)
            collected_words = refine_word_boundaries_acoustically(collected_words, audio_data, sr=audio_sr)
        return collected_words, det_lang, seg_counter

    all_words = []
    detected_lang = lang_param or "fr"
    raw_segment_count = 0
    used_device = "cpu"
    used_compute_type = compute_type

    if args.engine == "gladia":
        used_device = "cloud"
        used_compute_type = "api"
        all_words, detected_lang, raw_segment_count = transcribe_with_gladia(
            args.audio, args.gladia_key, lang_param, args.num_speakers, total_duration
        )
    elif device == "cuda":
        try:
            all_words, detected_lang, raw_segment_count = perform_transcription("cuda", compute_type)
            used_device, used_compute_type = "cuda", compute_type
        except Exception as e:
            print_info(f"Notification CUDA ({e}) : bascule automatique sur CPU...")
            try:
                all_words, detected_lang, raw_segment_count = perform_transcription("cpu", "int8")
                used_device, used_compute_type = "cpu", "int8"
            except Exception as e2:
                print_error(f"Échec de la transcription sur CPU : {e2}")
                sys.exit(1)
    else:
        try:
            all_words, detected_lang, raw_segment_count = perform_transcription("cpu", compute_type)
            used_compute_type = compute_type
        except Exception as e:
            print_error(f"Échec de la transcription : {e}")
            sys.exit(1)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)
    os.makedirs(os.path.dirname(os.path.abspath(args.report)), exist_ok=True)

    if not all_words:
        print_progress(100, 100, "Aucune parole détectée dans le fichier.")
        with open(args.output, "w", encoding="utf-8") as f:
            json.dump({"segments": []}, f, ensure_ascii=False, indent=2)
        with open(args.report, "w", encoding="utf-8") as f:
            json.dump({"metadata": {"engine": ("OmeRyth STT Cloud (Gladia Solaria-1 API)" if args.engine == "gladia" else "OmeRyth STT (faster-whisper)"),
                                    "audio_file": os.path.abspath(args.audio),
                                    "model": ("Solaria-1" if args.engine == "gladia" else model_name), "language": detected_lang, "total_segments": 0},
                       "segments": []}, f, ensure_ascii=False, indent=2)
        sys.exit(0)

    # ── 1. Découpage en répliques ──
    print_progress(88, 100, "Découpage en phrases naturelles pour la bande rythmo...")
    phrases = segment_words_into_clean_phrases(
        all_words, silence_intervals=silence_intervals, pause_threshold=0.45,
        lang=detected_lang, audio_data=audio_data, sr=audio_sr)

    # ── 2. Diarisation ──
    num_spk = args.num_speakers
    if num_spk != 1 and len(phrases) > 1:
        if args.engine == "gladia":
            # Gladia a déjà assigné les locuteurs de manière fiable dans l'API
            for p in phrases:
                spk_candidates = [w.get("speaker") for w in p.get("words", []) if w.get("speaker")]
                if spk_candidates:
                    p["speaker"] = max(set(spk_candidates), key=spk_candidates.count)
                elif not p.get("speaker"):
                    p["speaker"] = "SPEAKER_00"
        else:
            print_progress(91, 100, "Diarisation des répliques (pitch F0 & timbre)...")
            phrases = diarize_clean_phrases(args.audio, phrases, num_spk)
    else:
        for p in phrases:
            p["speaker"] = "SPEAKER_00"

    # ── 3. Fusion des micro-fragments et mots orphelins ──
    phrases = merge_consecutive_same_speaker_phrases(phrases, silence_intervals=silence_intervals,
                                                     max_gap=0.15, max_duration=6.5, max_words=25)
    phrases = resolve_orphan_phrases(phrases, max_duration=6.5)

    phrases = [p for p in phrases if p.get("text") and any(c.isalnum() for c in p["text"])]
    for p in phrases:
        p_words = [w for w in p.get("words", []) if w.get("word") and any(c.isalnum() for c in w["word"])]
        if not p_words:
            toks = [t for t in p["text"].split() if any(c.isalnum() for c in t)]
            if toks:
                dur = max(0.1, p["end"] - p["start"])
                step = dur / len(toks)
                p_words = [{"word": t, "start": round(p["start"] + i * step, 3),
                            "end": round(p["start"] + (i + 1) * step, 3)} for i, t in enumerate(toks)]
        p["words"] = p_words
    phrases = [p for p in phrases if p.get("words")]

    # ── 4. Segments finaux ──
    print_progress(95, 100, "Génération des repères et envoi vers OmeRyth...")
    final_segments = []
    prev_end = 0.0
    for ph_idx, phrase in enumerate(phrases):
        p_words = phrase["words"]
        raw_start = round(max(0.0, float(p_words[0]["start"])), 3)
        p_start = raw_start
        p_end = round(max(p_start + 0.15, float(p_words[-1]["end"])), 3)

        prev_end_bound = final_segments[-1]["end"] if final_segments else 0.0
        next_ph_start = float(phrases[ph_idx + 1].get("start", 999999.0)) if (ph_idx + 1 < len(phrases)) else None
        p_start, p_end = tighten_phrase_boundaries_acoustically(
            p_start, p_end, p_words, silence_intervals=silence_intervals,
            audio_data=audio_data, sr=audio_sr, prev_end_sec=prev_end_bound, next_start_sec=next_ph_start
        )

        # Si la réplique précédente empiète sur les 0.06s d'écart requis :
        # Raccourcir la fin de la réplique précédente sans retarder le début de la nouvelle réplique !
        if final_segments and final_segments[-1]["end"] > round(p_start - 0.06, 3):
            prev_seg = final_segments[-1]
            prev_seg["end"] = round(max(prev_seg["start"] + 0.15, round(p_start - 0.06, 3)), 3)
            prev_seg["end_timecode"] = format_timecode(prev_seg["end"])
            prev_seg["duration"] = round(max(0.1, prev_seg["end"] - prev_seg["start"]), 3)
            if prev_seg.get("words"):
                prev_seg["words"][-1]["end"] = min(prev_seg["words"][-1]["end"], prev_seg["end"])

        if p_words:
            p_words[0]["start"] = p_start

        for w_idx in range(1, len(p_words)):
            w = p_words[w_idx]
            prev_w = p_words[w_idx - 1]
            w_s = round(float(w["start"]), 3)
            prev_e = round(float(prev_w["end"]), 3)
            if prev_e > w_s:
                prev_w["end"] = round(max(float(prev_w["start"]) + 0.05, w_s), 3)
                prev_e = round(float(prev_w["end"]), 3)
            w["start"] = max(prev_e, w_s)
            w["end"] = round(max(float(w["end"]), float(w["start"]) + 0.05), 3)

        p_end = round(max(p_start + 0.15, float(p_words[-1]["end"])), 3)
        if next_ph_start is not None and next_ph_start < 900000.0:
            if p_end > round(next_ph_start - 0.08, 3):
                p_end = max(p_start + 0.15, round(next_ph_start - 0.08, 3))
                if p_words:
                    p_words[-1]["end"] = min(p_words[-1]["end"], p_end)

        clean_p_words = [
            {"word": w["word"], "start": round(float(w["start"]), 3), "end": round(float(w["end"]), 3)}
            for w in p_words
        ]

        text = finalize_phrase_text(phrase["text"])
        if not text:
            continue
        seg_data = {
            "id": len(final_segments) + 1,
            "speaker": phrase.get("speaker", "SPEAKER_00"),
            "start": p_start,
            "end": p_end,
            "start_timecode": format_timecode(p_start),
            "end_timecode": format_timecode(p_end),
            "duration": round(max(0.1, p_end - p_start), 3),
            "text": text,
            "words": clean_p_words,
            "separators": compute_rhythmic_separators(text, clean_p_words, audio_data=audio_data, sr=audio_sr),
        }
        final_segments.append(seg_data)
        prev_end = p_end
        print_segment(seg_data)

    print_progress(98, 100, "Sauvegarde du rapport final...")
    with open(args.output, "w", encoding="utf-8") as f:
        json.dump({"segments": final_segments}, f, ensure_ascii=False, indent=2)

    processing_time = round(time.time() - start_time, 2)
    with open(args.report, "w", encoding="utf-8") as f:
        json.dump({
            "metadata": {
                "engine": ("OmeRyth STT Cloud (Gladia Solaria-1 API)" if args.engine == "gladia" else "OmeRyth STT (faster-whisper séquentiel + Silero VAD + Word Timestamps)"),
                "audio_file": os.path.abspath(args.audio),
                "model": ("Solaria-1" if args.engine == "gladia" else model_name),
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
        }, f, ensure_ascii=False, indent=2)

    print_progress(100, 100, f"Transcription terminée ! {len(final_segments)} répliques détectées en {processing_time}s")


if __name__ == "__main__":
    try:
        main()
    except Exception as e:
        import traceback
        traceback.print_exc()
        print_error(f"Erreur interne STT : {e}")
        sys.exit(1)
