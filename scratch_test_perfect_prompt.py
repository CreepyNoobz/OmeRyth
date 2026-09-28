from faster_whisper import WhisperModel

mdl = WhisperModel('medium', device='cpu', compute_type='int8', cpu_threads=8, download_root='whisper/cache')

prompt = (
    "Transcription fidèle pour bande rythmo et doublage. "
    "Conserver absolument toutes les répétitions mot à mot, hésitations et bégaiements exacts du comédien : "
    "qu'on va qu'on va, on a on a, comme comme, je demande pas je demande pas, et euh, voilà."
)

segs, _ = mdl.transcribe(
    'scratch_test.wav',
    language='fr',
    word_timestamps=True,
    beam_size=4,
    initial_prompt=prompt,
    condition_on_previous_text=False,
    vad_filter=False
)

for s in segs:
    print(f"[{s.start:.2f} -> {s.end:.2f}] {s.text}")
