from faster_whisper import WhisperModel
import sys, os
sys.path.append(os.path.abspath('whisperx_engine'))
from whisperx_worker import load_audio, detect_silence_intervals, segment_words_into_clean_phrases, compute_rhythmic_separators

audio_data, audio_sr = load_audio('scratch_test.wav')
silence_intervals = detect_silence_intervals(audio_data, sr=audio_sr, min_silence_sec=0.10)

prompt = (
    "Transcription fidèle pour bande rythmo et doublage. "
    "Conserver absolument toutes les répétitions mot à mot, hésitations et bégaiements exacts du comédien : "
    "qu'on va qu'on va, on a on a, comme comme, je demande pas je demande pas, et euh, voilà."
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

raw_words = []
for s in segs:
    for w in (s.words or []):
        w_str = w.word.strip() if w.word else ""
        if w_str and any(c.isalnum() for c in w_str):
            raw_words.append({'word': w_str, 'start': float(w.start), 'end': float(w.end)})

print(f"Total raw words: {len(raw_words)}")

# Gap recovery
for i in range(len(raw_words) - 1):
    w1 = raw_words[i]
    w2 = raw_words[i + 1]
    gap = w2['start'] - w1['end']
    if gap >= 0.35:
        s_idx = int(w1['end'] * audio_sr)
        e_idx = int(w2['start'] * audio_sr)
        chunk = audio_data[s_idx:e_idx]
        rms = (chunk ** 2).mean() ** 0.5 if len(chunk) > 0 else 0
        if rms >= 0.02 and len(chunk) / audio_sr >= 0.20:
            print(f"Gap between {w1['word']} ({w1['end']}) and {w2['word']} ({w2['start']}): dur={gap:.2f}s, rms={rms:.4f}")
            gsegs, _ = mdl.transcribe(chunk, language='fr', word_timestamps=True, beam_size=2)
            for gs in gsegs:
                for gw in (gs.words or []):
                    gtxt = gw.word.strip()
                    if gtxt and any(c.isalnum() for c in gtxt):
                        print(f"   -> RECOVERED: {repr(gtxt)} [{w1['end']+gw.start:.2f} - {w1['end']+gw.end:.2f}]")

# Let's test segment_words_into_clean_phrases with pause_threshold=0.30
phrases = segment_words_into_clean_phrases(
    raw_words,
    silence_intervals=silence_intervals,
    pause_threshold=0.30,
    lang="fr",
    audio_data=audio_data,
    sr=audio_sr
)

print("\n=== CLEAN PHRASES (pause_threshold=0.30) ===")
for idx, p in enumerate(phrases):
    print(f"[{idx+1}] {p['start']:.2f} -> {p['end']:.2f} (dur={p['end']-p['start']:.2f}s): {repr(p['text'])}")
