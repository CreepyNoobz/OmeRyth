package app.services;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * Service de transcription vocale haute précision basé sur le pipeline noScribe
 * (faster-whisper + Silero VAD + word timestamps + diarisation).
 */
public class SpeechWorkflowService {

    private static final Set<Process> ACTIVE_PROCESSES = Collections.synchronizedSet(new HashSet<>());
    private Process currentProcess = null;
    private volatile boolean isCancelled = false;

    static {
        // Garantit qu'aucun processus ne reste en arrière-plan à la fermeture d'OmeRyth
        Runtime.getRuntime().addShutdownHook(new Thread(SpeechWorkflowService::killAllProcesses));
    }

    /** Arrête immédiatement tous les processus actifs. */
    public static void killAllProcesses() {
        synchronized (ACTIVE_PROCESSES) {
            for (Process p : ACTIVE_PROCESSES) {
                try {
                    if (p.isAlive()) {
                        p.destroyForcibly();
                    }
                } catch (Exception ignored) {}
            }
            ACTIVE_PROCESSES.clear();
        }
    }

    /**
     * Segment de transcription unitaire avec timecodes précis.
     */
    public static class TranscriptionSegment {
        public int id;
        public String speaker;
        public double startSeconds;
        public double endSeconds;
        public String text;

        public TranscriptionSegment(int id, String speaker, double startSeconds, double endSeconds, String text) {
            this.id = id;
            this.speaker = speaker;
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.text = text;
        }

        public int getId() { return id; }
        public String getSpeaker() { return speaker; }
        public double getStartSeconds() { return startSeconds; }
        public double getEndSeconds() { return endSeconds; }
        public String getText() { return text; }
        public double getDuration() { return Math.max(0, endSeconds - startSeconds); }

        public int getSpeakerIndex() {
            if (speaker == null || speaker.isBlank()) return 0;
            Matcher m = Pattern.compile("(\\d+)").matcher(speaker);
            if (m.find()) {
                try {
                    return Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
            return 0;
        }

        public String getSpeakerDisplayName() {
            return "Locuteur " + (getSpeakerIndex() + 1);
        }
    }

    /**
     * Résultat global de la transcription.
     */
    public static class TranscriptionResult {
        public List<TranscriptionSegment> segments;
        public File reportFile;

        public TranscriptionResult(List<TranscriptionSegment> segments, File reportFile) {
            this.segments = segments;
            this.reportFile = reportFile;
        }
    }

    /**
     * Interface de rappel pour les événements de transcription.
     */
    public interface TranscriptionCallback {
        void onProgress(int percentage, String message);
        void onSegmentFound(TranscriptionSegment segment);
        void onComplete(TranscriptionResult result);
        void onError(String errorMessage);
    }

    /** Annule la transcription en cours et libère les ressources. */
    public void cancel() {
        isCancelled = true;
        if (currentProcess != null && currentProcess.isAlive()) {
            try {
                currentProcess.destroyForcibly();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Détecte l'exécutable Python système ou virtuel.
     */
    public String findPython() {
        String[] candidates = {"python", "python3"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (InputStream is = p.getInputStream()) {
                    is.transferTo(OutputStream.nullOutputStream());
                }
                if (p.waitFor() == 0) return cmd;
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Détecte le binaire FFmpeg embarqué ou système.
     */
    public String findFfmpeg() {
        File localFfmpeg = new File("ffmpeg/ffmpeg.exe");
        if (localFfmpeg.exists()) return localFfmpeg.getAbsolutePath();

        File commonFfmpeg = new File("C:/ffmpeg/bin/ffmpeg.exe");
        if (commonFfmpeg.exists()) return commonFfmpeg.getAbsolutePath();

        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            if (p.waitFor() == 0) return "ffmpeg";
        } catch (Exception ignored) {}

        return null;
    }

    /**
     * Détecte le script worker WhisperX.
     */
    public File findWorkerScript() {
        File f = new File("whisperx_engine/whisperx_worker.py");
        if (f.exists()) return f;
        return null;
    }

    /**
     * Vérifie rapidement si un GPU compatible CUDA est disponible via ctranslate2.
     */
    public static boolean isCudaAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-c",
                    "import sys, ctranslate2; sys.exit(0 if ctranslate2.get_cuda_device_count() > 0 else 1)");
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrait l'audio de la vidéo en format WAV 16kHz mono, synchronisé précisément
     * au PTS de la vidéo via aresample async pour éviter tout décalage.
     * Utilise tous les cœurs CPU disponibles (-threads 0) pour une extraction ultra-rapide.
     */
    public boolean extractAudio(File videoFile, File outputWav) {
        String ffmpeg = findFfmpeg();
        if (ffmpeg == null) return false;

        // Normalisation dynamique intelligente (dynaudnorm) : remonte les voix chuchotées/douces
        // et articulations étalées sur plusieurs temps sans saturer les voix fortes
        String audioFilter = "aresample=async=1000:first_pts=0,highpass=f=70,lowpass=f=7600,dynaudnorm=f=150:g=15:m=10.0:r=0.9";

        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg, "-threads", "0", "-i", videoFile.getAbsolutePath(),
                "-vn", "-sn", "-dn", "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
                "-af", audioFilter,
                outputWav.getAbsolutePath(), "-y"
        );
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            registerProcess(p);
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            int exit = p.waitFor();
            unregisterProcess(p);
            if (exit == 0 && outputWav.exists() && outputWav.length() > 0) {
                return true;
            }
        } catch (Exception ignored) {}

        // Repli de sécurité si un filtre audio n'est pas supporté par une version minimale de FFmpeg
        ProcessBuilder fallbackPb = new ProcessBuilder(
                ffmpeg, "-threads", "0", "-i", videoFile.getAbsolutePath(),
                "-vn", "-sn", "-dn", "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
                "-af", "aresample=async=1000:first_pts=0,highpass=f=80,volume=1.1",
                outputWav.getAbsolutePath(), "-y"
        );
        fallbackPb.redirectErrorStream(true);
        try {
            Process p2 = fallbackPb.start();
            registerProcess(p2);
            try (InputStream is = p2.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            int exit = p2.waitFor();
            unregisterProcess(p2);
            return exit == 0 && outputWav.exists() && outputWav.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Lance la transcription vocale haute précision WhisperX en tâche de fond (compatibilité).
     */
    public void transcribe(File videoFile, int numSpeakers, String language, String modelName,
                           int threadCount, TranscriptionCallback callback) {
        transcribe(videoFile, numSpeakers, language, modelName, threadCount, "auto", callback);
    }

    /**
     * Lance la transcription vocale haute précision WhisperX avec choix d'accélération matérielle.
     */
    public void transcribe(File videoFile, int numSpeakers, String language, String modelName,
                           int threadCount, String hardwareDevice, TranscriptionCallback callback) {
        isCancelled = false;
        new Thread(() -> {
            File tempDir = new File("temp");
            if (!tempDir.exists()) tempDir.mkdirs();

            File tempWav = new File(tempDir, "audio_" + System.currentTimeMillis() + ".wav");
            File resultJson = new File(tempDir, "result_" + System.currentTimeMillis() + ".json");
            String dateStr = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File reportFile = new File(videoFile.getParentFile(), "transcription_report_" + dateStr + ".json");

            try {
                // 1. Vérification FFmpeg & Python
                String ffmpeg = findFfmpeg();
                if (ffmpeg == null) {
                    callback.onError("FFmpeg introuvable.");
                    return;
                }

                String python = findPython();
                if (python == null) {
                    callback.onError("Python introuvable sur le système.");
                    return;
                }

                File workerScript = findWorkerScript();
                if (workerScript == null) {
                    callback.onError("Script whisperx_engine/whisperx_worker.py introuvable.");
                    return;
                }

                // 2. Extraction audio
                callback.onProgress(5, "Extraction audio haute vitesse de la vidéo...");
                if (!extractAudio(videoFile, tempWav)) {
                    if (isCancelled) return;
                    callback.onError("Échec de l'extraction audio.");
                    return;
                }

                if (isCancelled) return;

                // 3. Préparation de la commande WhisperX
                String cleanModel = (modelName == null || modelName.isBlank()) ? "small" : modelName.toLowerCase().trim();
                if (cleanModel.contains("base")) cleanModel = "base";
                else if (cleanModel.contains("tiny")) cleanModel = "tiny";
                else if (cleanModel.contains("medium")) cleanModel = "medium";
                else cleanModel = "small";

                int safeThreads = threadCount > 0 ? threadCount : 4;
                String langCode = (language != null && !language.isBlank()) ? language.toLowerCase().trim() : "fr";
                if (langCode.contains("auto")) langCode = "auto";
                else if (langCode.contains("fr")) langCode = "fr";
                else if (langCode.contains("en")) langCode = "en";

                String hw = (hardwareDevice != null && !hardwareDevice.isBlank()) ? hardwareDevice.toLowerCase().trim() : "auto";
                String devLabel = hw.equals("cuda") ? "GPU NVIDIA CUDA" : "CPU Multi-cœurs";
                callback.onProgress(15, "Initialisation WhisperX (" + cleanModel.toUpperCase() + " sur " + devLabel + ")...");

                List<String> command = new ArrayList<>(Arrays.asList(
                        python, "-u", workerScript.getAbsolutePath(),
                        "--audio", tempWav.getAbsolutePath(),
                        "--num-speakers", String.valueOf(Math.max(0, numSpeakers)),
                        "--lang", langCode,
                        "--model", cleanModel,
                        "--threads", String.valueOf(safeThreads),
                        "--device", hw,
                        "--output", resultJson.getAbsolutePath(),
                        "--report", reportFile.getAbsolutePath()
                ));

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                pb.environment().put("PYTHONUTF8", "1");
                pb.redirectErrorStream(true);
                currentProcess = pb.start();
                registerProcess(currentProcess);

                List<String> outputLogs = new ArrayList<>();

                List<TranscriptionSegment> streamedSegments = new ArrayList<>();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(currentProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (isCancelled) break;
                        outputLogs.add(line);

                        if (line.startsWith("PROGRESS:")) {
                            String[] parts = line.split(":", 4);
                            if (parts.length >= 4) {
                                try {
                                    int step = Integer.parseInt(parts[1]);
                                    int total = Integer.parseInt(parts[2]);
                                    String msg = parts[3];
                                    callback.onProgress((step * 100) / total, msg);
                                } catch (NumberFormatException ignored) {}
                            }
                        } else if (line.startsWith("SEGMENT:")) {
                            String jsonStr = line.substring(8).trim();
                            TranscriptionSegment seg = parseSingleSegment(jsonStr);
                            if (seg != null) {
                                streamedSegments.add(seg);
                                callback.onSegmentFound(seg);
                            }
                        } else if (line.startsWith("INFO:")) {
                            callback.onProgress(85, line.substring(5).trim());
                        } else if (line.startsWith("ERROR:")) {
                            callback.onError(line.substring(6));
                            if (tempWav.exists()) tempWav.delete();
                            return;
                        }
                    }
                }

                int exitCode = currentProcess.waitFor();
                unregisterProcess(currentProcess);

                if (isCancelled) return;

                if (exitCode != 0 && streamedSegments.isEmpty()) {
                    String errDetail = String.join("\n", outputLogs);
                    callback.onError("Échec du processus noScribe (code " + exitCode + ") :\n" + errDetail);
                    return;
                }

                // 4. Récupération des segments finaux avec garantie anti-perte
                List<TranscriptionSegment> finalSegments = new ArrayList<>(streamedSegments);
                if (resultJson.exists()) {
                    try {
                        String jsonContent = Files.readString(resultJson.toPath(), StandardCharsets.UTF_8);
                        List<TranscriptionSegment> parsed = parseResultJson(jsonContent, streamedSegments);
                        if (parsed != null && !parsed.isEmpty()) {
                            finalSegments = parsed;
                        }
                    } catch (Exception ignored) {}
                }

                callback.onProgress(100, "Transcription terminée !");
                callback.onComplete(new TranscriptionResult(finalSegments, reportFile));

            } catch (Exception e) {
                if (!isCancelled) {
                    callback.onError("Erreur : " + e.getMessage());
                }
            } finally {
                if (tempWav.exists()) tempWav.delete();
                if (resultJson.exists()) resultJson.delete();
            }
        }).start();
    }

    private TranscriptionSegment parseSingleSegment(String jsonStr) {
        try {
            Matcher idM = Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(jsonStr);
            Matcher spkM = Pattern.compile("\"speaker\"\\s*:\\s*\"([^\"]+)\"").matcher(jsonStr);
            Matcher startM = Pattern.compile("\"start\"\\s*:\\s*([0-9.]+)").matcher(jsonStr);
            Matcher endM = Pattern.compile("\"end\"\\s*:\\s*([0-9.]+)").matcher(jsonStr);
            Matcher textM = Pattern.compile("\"text\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").matcher(jsonStr);

            int id = idM.find() ? Integer.parseInt(idM.group(1)) : 1;
            String spk = spkM.find() ? spkM.group(1) : "SPEAKER_00";
            double start = startM.find() ? Double.parseDouble(startM.group(1)) : 0.0;
            double end = endM.find() ? Double.parseDouble(endM.group(1)) : 0.0;
            String text = textM.find() ? unescape(textM.group(1)) : "";

            return new TranscriptionSegment(id, spk, start, end, text);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parseur JSON robuste avec gestion de la profondeur d'accolades (supporte les sous-objets words).
     */
    private List<TranscriptionSegment> parseResultJson(String json, List<TranscriptionSegment> fallback) {
        List<TranscriptionSegment> list = new ArrayList<>();
        try {
            int segIndex = json.indexOf("\"segments\"");
            if (segIndex != -1) {
                int depth = 0;
                int objStart = -1;
                boolean inString = false;
                for (int i = segIndex; i < json.length(); i++) {
                    char c = json.charAt(i);
                    if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) {
                        inString = !inString;
                    }
                    if (!inString) {
                        if (c == '{') {
                            depth++;
                            if (depth == 1) {
                                objStart = i;
                            }
                        } else if (c == '}') {
                            if (depth == 1 && objStart != -1) {
                                String objStr = json.substring(objStart, i + 1);
                                TranscriptionSegment s = parseSingleSegment(objStr);
                                if (s != null && !s.text.isBlank()) {
                                    s.id = list.size() + 1;
                                    list.add(s);
                                }
                                objStart = -1;
                            }
                            depth--;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (list.isEmpty() && fallback != null && !fallback.isEmpty()) {
            return new ArrayList<>(fallback);
        }
        return list;
    }

    private static String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\\"", "\"").replace("\\n", "\n").replace("\\\\", "\\");
    }

    private synchronized void registerProcess(Process p) {
        ACTIVE_PROCESSES.add(p);
    }

    private synchronized void unregisterProcess(Process p) {
        ACTIVE_PROCESSES.remove(p);
    }
}
