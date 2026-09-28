import sys, os, json
sys.path.append('whisperx_engine')
import soundfile as sf
import numpy as np
from faster_whisper import WhisperModel
from whisperx_worker import (
    load_audio,
    detect_silence_intervals,
    trim_speech_start_acoustically,
    trim_speech_end_acoustically,
    clean_text,
    compute_rhythmic_separators
)

audio_data, audio_sr = load_audio('scratch_test.wav')
silence_intervals = detect_silence_intervals(audio_data, sr=audio_sr, min_silence_sec=0.10)

prompt = (
    "Transcription fidèle pour bande rythmo et doublage. "
    "Conserver absolument toutes les répétitions mot à mot, hésitations et bégaiements exacts du comédien : "
    "qu'on va qu'on va régler, on a on a un pays, et euh, je demande pas, comme comme."
)

mdl = WhisperModel('medium', device='cpu', compute_type='int8', cpu_threads=8, download_root='whisper/cache')

segs, _ = mdl.transcribe(
    'scratch_test.wav',
    language='fr',
    word_timestamps=True,
    beam_size=4,
    initial_prompt=prompt,
    condition_on_previous_text=False,
    vad_filter=False
)

collected_words = []
for s in segs:
    for w in (s.words or []):
        w_str = w.word.strip() if w.word else ""
        if w_str and any(c.isalnum() for c in w_str):
            collected_words.append({
                "word": w_str,
                "start": float(w.start),
                "end": float(w.end)
            })

# Enhanced Recovery pass with prompt
if audio_data is not None and audio_sr > 0 and len(collected_words) > 1:
    recovered_entries = []
    for i in range(len(collected_words) - 1):
        w1 = collected_words[i]
        w2 = collected_words[i + 1]
        gap = float(w2["start"]) - float(w1["end"])
        if gap >= 0.35:
            s_idx = int(float(w1["end"]) * audio_sr)
            e_idx = int(float(w2["start"]) * audio_sr)
            chunk = audio_data[s_idx:e_idx]
            chunk_rms = np.sqrt(np.mean(chunk ** 2)) if len(chunk) > 0 else 0.0
            if chunk_rms >= 0.02 and (len(chunk) / audio_sr) >= 0.20:
                try:
                    gsegs, _ = mdl.transcribe(
                        chunk,
                        language='fr',
                        word_timestamps=True,
                        beam_size=2,
                        initial_prompt=prompt
                    )
                    for gs in gsegs:
                        for gw in (gs.words or []):
                            w_txt = gw.word.strip() if gw.word else ""
                            if w_txt and any(c.isalnum() for c in w_txt):
                                act_start = round(float(w1["end"]) + float(gw.start), 2)
                                act_end = round(float(w1["end"]) + float(gw.end), 2)
                                recovered_entries.append({
                                    "insert_after": i,
                                    "word": {
                                        "word": w_txt,
                                        "start": act_start,
                                        "end": act_end
                                    }
                                })
                except Exception as ex:
                    pass
    if recovered_entries:
        for entry in reversed(recovered_entries):
            collected_words.insert(entry["insert_after"] + 1, entry["word"])
        collected_words.sort(key=lambda w: w["start"])

def segment_words(words, silence_intervals, pause_threshold=0.28, lang="fr", audio_data=None, sr=16000):
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

        if audio_data is not None and sr > 0:
            w_start = trim_speech_start_acoustically(audio_data, w_start, w_end, sr=sr, blank_thresh_sec=0.10)
            w_end = trim_speech_end_acoustically(audio_data, w_start, w_end, sr=sr, blank_thresh_sec=0.18)
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start <= w_start and s_end > (w_start + 0.04) and s_end < (w_end - 0.06):
                    w_start = max(w_start, round(s_end - 0.02, 2))
                if s_start < w_end and s_end >= (w_end - 0.05) and s_start >= (w_start + 0.08):
                    w_end = min(w_end, round(s_start + 0.04, 2))
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

        is_pause = (gap >= pause_threshold)
        is_strong_punct = any(prev_word_text.endswith(p) for p in ["?", "!", ".", "…", "..."])
        split_strong_punct = is_strong_punct and (gap >= 0.22)
        is_comma = any(prev_word_text.endswith(p) for p in [",", ";"])
        split_comma = is_comma and (gap >= 0.22)

        has_acoustic_silence = False
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start >= (prev_end - 0.05) and s_end <= (w_start + 0.05):
                    if (s_end - s_start) >= 0.20:
                        has_acoustic_silence = True
                        break

        w_lower = w_text.lower().strip()
        is_starter = w_lower in {"et", "mais", "alors", "donc", "je", "on", "il", "elle", "ils", "comme"}
        split_starter = is_starter and (gap >= 0.22)

        split_long = (phrase_duration >= 6.5 and gap >= 0.20)

        should_split = is_pause or has_acoustic_silence or split_strong_punct or split_comma or split_starter or split_long

        if should_split:
            phrase_end = prev_end
            if audio_data is not None and sr > 0:
                phrase_start = trim_speech_start_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.10)
                phrase_end = trim_speech_end_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.18)
            raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
            cleaned = clean_text(raw_text, lang)
            if cleaned and any(c.isalnum() for c in cleaned):
                phrases.append({
                    "start": round(phrase_start, 2),
                    "end": round(phrase_end, 2),
                    "text": cleaned,
                    "words": current_words,
                    "speaker": "SPEAKER_00"
                })
            current_words = [w]
            phrase_start = w_start
        else:
            current_words.append(w)

    if current_words:
        phrase_end = float(current_words[-1].get("end", 0.0))
        if audio_data is not None and sr > 0:
            phrase_start = trim_speech_start_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.10)
            phrase_end = trim_speech_end_acoustically(audio_data, phrase_start, phrase_end, sr=sr, blank_thresh_sec=0.18)
        raw_text = " ".join(cw.get("word", "").strip() for cw in current_words)
        cleaned = clean_text(raw_text, lang)
        if cleaned and any(c.isalnum() for c in cleaned):
            phrases.append({
                "start": round(phrase_start, 2),
                "end": round(phrase_end, 2),
                "text": cleaned,
                "words": current_words,
                "speaker": "SPEAKER_00"
            })

    return phrases

phrases = segment_words(collected_words, silence_intervals, pause_threshold=0.28, audio_data=audio_data, sr=audio_sr)

def merge_phrases(phrases, silence_intervals=None, max_gap=0.22, max_duration=6.5):
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

        has_silence = False
        if silence_intervals:
            for s_start, s_end in silence_intervals:
                if s_start >= (prev["end"] - 0.05) and s_end <= (p["start"] + 0.05) and (s_end - s_start) >= 0.20:
                    has_silence = True
                    break

        prev_text = prev.get("text", "").strip()
        ends_with_strong_punct = any(prev_text.endswith(pt) for pt in [".", "!", "?", "…", "..."])

        if (not has_silence and
            not ends_with_strong_punct and
            prev.get("speaker") == p.get("speaker") and
            gap < max_gap and
            combined_dur <= max_duration):
            prev["end"] = p["end"]
            prev["text"] = (prev["text"] + " " + p["text"]).strip()
            prev["words"] = prev.get("words", []) + p.get("words", [])
        else:
            merged.append(p)
    return merged

final_phrases = merge_phrases(phrases, silence_intervals=silence_intervals)

print("\n=== FINAL TEST RESULTS ===")
for i, p in enumerate(final_phrases):
    gap_after = (final_phrases[i+1]['start'] - p['end']) if (i + 1 < len(final_phrases)) else 0
    print(f"[{i+1}] {p['start']:.2f}s -> {p['end']:.2f}s (dur={p['end']-p['start']:.2f}s): {repr(p['text'])}")
    if i + 1 < len(final_phrases):
        print(f"     [BLANK SILENCE: {gap_after:.2f}s]")
