package app.services;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Service de traitement du signal audio (DSP) dédié à la génération de la forme d'onde (waveform).
 * <p>
 * Pipeline d'extraction haute performance :
 * <ul>
 *   <li><b>Filtrage acoustique vocal :</b> Décodage audio via FFmpeg avec un filtre passe-bande
 *       (passe-haut à 180 Hz et passe-bas à 3400 Hz) et amplification de volume (x2.0). Ce filtrage supprime
 *       les grondements de sub-basses et les sifflements aigus parasites afin de faire ressortir avec netteté
 *       les attaques de consonnes et les voyelles parlées des comédiens.</li>
 *   <li><b>Streaming binaire direct (Zero Disk I/O) :</b> L'audio est extrait en flux brut PCM 16-bit mono à 16 000 Hz
 *       via le descripteur de tube standard {@code pipe:1}, évitant l'écriture de fichiers temporaires volumineux.</li>
 *   <li><b>Décimation et calcul de crête (Peak Pooling) :</b> Le flux d'échantillons est agrégé par fenêtres
 *       de 160 échantillons (soit exactement 100 crêtes par seconde d'audio à 16 kHz).</li>
 *   <li><b>Gestion de l'alignement mémoire :</b> Reconstitution 16-bit Little-Endian {@code (b0 | (b1 << 8))}
 *       avec conservation de l'octet résiduel ({@code leftoverByte}) en cas de coupure de buffer sur un indice impair.</li>
 *   <li><b>Optimisation mémoire sans garbage collection :</b> Utilisation exclusive de tableaux de types primitifs
 *       ({@code float[]}) avec redimensionnement géométrique par doublement, éliminant tout boxing d'objets {@link Float}.</li>
 * </ul>
 * </p>
 */
public class AudioWaveformService {

    /**
     * Interface de rappel notifiée lorsque l'extraction et la normalisation de la forme d'onde sont terminées.
     */
    public interface WaveformCallback {
        void onWaveformReady(AudioWaveformData data);
    }
    
    private AudioWaveformData cachedData;
    private String cachedFilePath;
    private Thread currentWorker;

    /**
     * Lance l'extraction asynchrone de la forme d'onde dans un fil d'exécution démon d'arrière-plan.
     * En cas de requêtes successives, interrompt immédiatement le calcul précédent.
     *
     * @param mediaFile  Fichier source vidéo ou audio.
     * @param callback   Rappel exécuté sur le fil d'événements Swing (EDT).
     */
    public void extractWaveform(File mediaFile, WaveformCallback callback) {
        if (mediaFile == null || !mediaFile.exists()) {
            if (callback != null) {
                SwingUtilities.invokeLater(() -> callback.onWaveformReady(null));
            }
            return;
        }

        String filePath = mediaFile.getAbsolutePath();
        if (filePath.equals(cachedFilePath) && cachedData != null) {
            if (callback != null) {
                SwingUtilities.invokeLater(() -> callback.onWaveformReady(cachedData));
            }
            return;
        }

        if (currentWorker != null && currentWorker.isAlive()) {
            currentWorker.interrupt();
        }

        currentWorker = new Thread(() -> {
            AudioWaveformData data = performExtraction(mediaFile);
            if (Thread.currentThread().isInterrupted()) return;

            if (data != null) {
                cachedFilePath = filePath;
                cachedData = data;
            }

            if (callback != null) {
                SwingUtilities.invokeLater(() -> callback.onWaveformReady(data));
            }
        });
        currentWorker.setDaemon(true);
        currentWorker.start();
    }
    
    /**
     * Décode le flux PCM binaire 16-bit et calcule les amplitudes crêtes normalisées.
     */
    private AudioWaveformData performExtraction(File mediaFile) {
        String ffmpeg = findFfmpeg();
        if (ffmpeg == null) return null;

        // Décodage PCM 16kHz mono filtré sur la bande vocale (180 Hz - 3400 Hz)
        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg, "-threads", "0", "-i", mediaFile.getAbsolutePath(),
                "-vn", "-sn", "-dn", "-ar", "16000", "-ac", "1", "-f", "s16le", "-c:a", "pcm_s16le",
                "-af", "highpass=f=180,lowpass=f=3400,volume=2.0",
                "pipe:1"
        );
        pb.redirectError(ProcessBuilder.Redirect.DISCARD);

        float[] peaksBuffer = new float[131072]; // Tampon primitif extensible (zéro boxing de Float)
        int peakCount = 0;
        try {
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                byte[] readBuffer = new byte[65536];
                int bytesRead;
                float maxGlobal = 0f;
                int sampleCountInPeak = 0;
                short currentPeakMax = 0;
                long totalSamples = 0;
                int leftoverByte = -1; // Pour gérer les coupures sur un octet impair

                while ((bytesRead = is.read(readBuffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) {
                        p.destroyForcibly();
                        return null;
                    }
                    if (bytesRead == 0) continue;

                    int offset = 0;
                    if (leftoverByte != -1) {
                        int b0 = leftoverByte;
                        int b1 = readBuffer[0] & 0xFF;
                        offset = 1;
                        leftoverByte = -1;

                        short sample = (short) (b0 | (b1 << 8));
                        short abs = (short) Math.abs(sample);
                        if (abs > currentPeakMax) currentPeakMax = abs;
                        sampleCountInPeak++;
                        totalSamples++;

                        if (sampleCountInPeak == 160) {
                            if (peakCount >= peaksBuffer.length) {
                                peaksBuffer = java.util.Arrays.copyOf(peaksBuffer, peaksBuffer.length * 2);
                            }
                            peaksBuffer[peakCount++] = (float) currentPeakMax;
                            if (currentPeakMax > maxGlobal) maxGlobal = currentPeakMax;
                            currentPeakMax = 0;
                            sampleCountInPeak = 0;
                        }
                    }

                    int availableBytes = bytesRead - offset;
                    int completeSamples = availableBytes / 2;

                    for (int i = 0; i < completeSamples; i++) {
                        int b0 = readBuffer[offset + i * 2] & 0xFF;
                        int b1 = readBuffer[offset + i * 2 + 1] & 0xFF;
                        short sample = (short) (b0 | (b1 << 8));
                        short abs = (short) Math.abs(sample);
                        if (abs > currentPeakMax) currentPeakMax = abs;
                        sampleCountInPeak++;
                        totalSamples++;

                        if (sampleCountInPeak == 160) {
                            if (peakCount >= peaksBuffer.length) {
                                peaksBuffer = java.util.Arrays.copyOf(peaksBuffer, peaksBuffer.length * 2);
                            }
                            peaksBuffer[peakCount++] = (float) currentPeakMax;
                            if (currentPeakMax > maxGlobal) maxGlobal = currentPeakMax;
                            currentPeakMax = 0;
                            sampleCountInPeak = 0;
                        }
                    }

                    if ((availableBytes % 2) != 0) {
                        leftoverByte = readBuffer[bytesRead - 1] & 0xFF;
                    }
                }

                if (sampleCountInPeak > 0) {
                    if (peakCount >= peaksBuffer.length) {
                        peaksBuffer = java.util.Arrays.copyOf(peaksBuffer, peaksBuffer.length * 2);
                    }
                    peaksBuffer[peakCount++] = (float) currentPeakMax;
                    if (currentPeakMax > maxGlobal) maxGlobal = currentPeakMax;
                }

                p.waitFor();

                float[] normalizedPeaks = new float[peakCount];
                if (maxGlobal > 0) {
                    for (int i = 0; i < peakCount; i++) {
                        normalizedPeaks[i] = peaksBuffer[i] / maxGlobal;
                    }
                }

                double durationSec = totalSamples / 16000.0;
                return new AudioWaveformData(normalizedPeaks, durationSec, 100);

            } catch (Exception e) {
                p.destroyForcibly();
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private String findFfmpeg() {
        File localFfmpeg = new File("ffmpeg/ffmpeg.exe");
        if (localFfmpeg.exists()) return localFfmpeg.getAbsolutePath();

        File commonFfmpeg = new File("C:/ffmpeg/bin/ffmpeg.exe");
        if (commonFfmpeg.exists()) return commonFfmpeg.getAbsolutePath();

        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(java.io.OutputStream.nullOutputStream());
            }
            if (p.waitFor() == 0) return "ffmpeg";
        } catch (Exception ignored) {}

        return null;
    }

    public AudioWaveformData getCachedData() { return cachedData; }
    
    public void clearCache() {
        cachedData = null;
        cachedFilePath = null;
    }
}
