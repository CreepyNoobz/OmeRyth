package app.services;

/**
 * Stocke les données de forme d'onde audio (amplitudes crêtes) pour le rendu visuel.
 * Les amplitudes sont normalisées entre 0.0 et 1.0.
 */
public class AudioWaveformData {
    private final float[] amplitudes; // amplitude crêtes normalisées [0.0 - 1.0]
    private final double durationSeconds; // durée totale de l'audio
    private final int samplesPerSecond; // résolution (ex: 100 points/seconde)

    public AudioWaveformData(float[] amplitudes, double durationSeconds, int samplesPerSecond) {
        this.amplitudes = amplitudes;
        this.durationSeconds = durationSeconds;
        this.samplesPerSecond = samplesPerSecond;
    }

    public float getAmplitudeAt(double timeSeconds) {
        if (amplitudes == null || amplitudes.length == 0 || timeSeconds < 0) return 0f;
        int index = (int)(timeSeconds * samplesPerSecond);
        if (index >= amplitudes.length) return 0f;
        return amplitudes[index];
    }

    /** Renvoie l'amplitude maximale dans la plage [startSec, endSec]. */
    public float getMaxAmplitudeInRange(double startSec, double endSec) {
        if (amplitudes == null || amplitudes.length == 0) return 0f;
        int startIdx = Math.max(0, (int)(startSec * samplesPerSecond));
        int endIdx = Math.min(amplitudes.length - 1, (int)(endSec * samplesPerSecond));
        float max = 0f;
        for (int i = startIdx; i <= endIdx; i++) {
            if (amplitudes[i] > max) max = amplitudes[i];
        }
        return max;
    }

    public float[] getAmplitudes() { return amplitudes; }
    public double getDurationSeconds() { return durationSeconds; }
    public int getSamplesPerSecond() { return samplesPerSecond; }
    public boolean isEmpty() { return amplitudes == null || amplitudes.length == 0; }
}
