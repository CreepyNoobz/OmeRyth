import sys, os
sys.path.append('whisperx_engine')
from scratch_test_worker_e2e import collected_words, silence_intervals

print("WORDS:")
for i, w in enumerate(collected_words):
    print(f"[{i}] {repr(w['word'])}: {w['start']:.2f} -> {w['end']:.2f}")
