/* ==========================================================================
   OmeRyth Web Presentation — Interactive Logic
   ========================================================================== */

document.addEventListener('DOMContentLoaded', () => {

  // ========================================================================
  // 1. Download Modal Trigger (Bouton de téléchargement)
  // ========================================================================
  const modal = document.getElementById('downloadModal');
  const modalClose = document.getElementById('modalClose');
  const modalOk = document.getElementById('modalOk');
  const downloadButtons = document.querySelectorAll('.btn-download-trigger');

  function openModal() {
    if (modal) {
      modal.classList.add('active');
    }
  }

  function closeModal() {
    if (modal) {
      modal.classList.remove('active');
    }
  }

  downloadButtons.forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      openModal();
    });
  });

  if (modalClose) modalClose.addEventListener('click', closeModal);
  if (modalOk) modalOk.addEventListener('click', closeModal);

  if (modal) {
    modal.addEventListener('click', (e) => {
      if (e.target === modal) {
        closeModal();
      }
    });
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && modal && modal.classList.contains('active')) {
      closeModal();
    }
  });


  // ========================================================================
  // 2. Interactive Rythmo Band Simulator
  // ========================================================================
  const rythmoTrack = document.getElementById('rythmoTrack');
  const waveformWave = document.getElementById('waveformWave');
  const timecodeDisplay = document.getElementById('timecodeDisplay');
  const btnPlayPause = document.getElementById('btnPlayPause');
  const playIcon = document.getElementById('playIcon');
  const btnReset = document.getElementById('btnReset');

  let isPlaying = false;
  let currentPosition = 0; // in pixels
  let startTimeMs = 84400; // start at 00:01:24.400
  let currentTimeMs = startTimeMs;
  let lastTimestamp = null;
  const speedPxPerSec = 140; // 140 px/s

  function formatTimecode(ms) {
    const totalSeconds = Math.floor(ms / 1000);
    const milliseconds = Math.floor(ms % 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;
    const hours = Math.floor(minutes / 60);

    const pad = (n, len = 2) => String(n).padStart(len, '0');
    return `${pad(hours)}:${pad(minutes % 60)}:${pad(seconds)}.${pad(milliseconds, 3)}`;
  }

  function updateVisuals() {
    if (rythmoTrack) {
      rythmoTrack.style.transform = `translateX(${-currentPosition}px)`;
    }
    if (waveformWave) {
      waveformWave.style.transform = `translateX(${(-currentPosition * 0.8) % 300}px)`;
    }
    if (timecodeDisplay) {
      timecodeDisplay.textContent = formatTimecode(currentTimeMs);
    }
  }

  function animationLoop(timestamp) {
    if (!isPlaying) return;

    if (!lastTimestamp) lastTimestamp = timestamp;
    const deltaMs = timestamp - lastTimestamp;
    lastTimestamp = timestamp;

    const deltaSec = deltaMs / 1000;
    currentPosition += speedPxPerSec * deltaSec;
    currentTimeMs += deltaMs;

    // Loop after 1900px
    if (currentPosition > 1900) {
      currentPosition = 0;
      currentTimeMs = startTimeMs;
    }

    updateVisuals();
    requestAnimationFrame(animationLoop);
  }

  function togglePlay() {
    isPlaying = !isPlaying;
    if (isPlaying) {
      playIcon.textContent = '⏸';
      lastTimestamp = null;
      requestAnimationFrame(animationLoop);
    } else {
      playIcon.textContent = '▶';
    }
  }

  function resetTrack() {
    isPlaying = false;
    if (playIcon) playIcon.textContent = '▶';
    currentPosition = 0;
    currentTimeMs = startTimeMs;
    updateVisuals();
  }

  if (btnPlayPause) {
    btnPlayPause.addEventListener('click', togglePlay);
  }

  if (btnReset) {
    btnReset.addEventListener('click', resetTrack);
  }

  // Keyboard shortcut: Spacebar inside demo area or anywhere if not in input
  document.addEventListener('keydown', (e) => {
    if (e.code === 'Space' && !['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) {
      e.preventDefault();
      togglePlay();
    } else if (e.code === 'KeyR' && !['INPUT', 'TEXTAREA'].includes(document.activeElement.tagName)) {
      e.preventDefault();
      resetTrack();
    }
  });

  // Initial draw
  updateVisuals();
});
