import sys, os
sys.path.append('whisperx_engine')
from faster_whisper import WhisperModel

prompt = (
    "Transcription fidèle pour bande rythmo et doublage. "
    "Conserver absolument toutes les répétitions mot à mot, hésitations et bégaiements exacts du comédien : "
    "qu'on va qu'on va régler, on a on a, et euh, je demande pas, comme comme."
)

mdl = WhisperModel('medium', device='cpu', compute_type='int8', cpu_threads=8, download_root='whisper/cache')
segs, _ = mdl.transcribe('scratch_test.wav', language='fr', word_timestamps=True, initial_prompt=prompt, condition_on_previous_text=False, beam_size=4, vad_filter=False)

print('=== SEGMENTS ===')
for s in segs:
    print(f'[{s.start:.2f} -> {s.end:.2f}] {s.text}')
    words_str = ' '.join(f'{w.word}({w.start:.2f}-{w.end:.2f})' for w in (s.words or []))
    print(f'   WORDS: {words_str}')
