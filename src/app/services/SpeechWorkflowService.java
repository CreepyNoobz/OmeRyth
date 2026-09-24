package app.services;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.*;

/**
 * Service orchestrant la transcription vocale haute fidélité pour le doublage.
 * <p>
 * Architecture technique :
 * <ul>
 *   <li><b>Moteur de reconnaissance :</b> Exécution asynchrone du script Python {@code whisperx_worker.py}
 *       basé sur <i>faster-whisper</i> (CTranslate2 int8) et <i>Silero VAD</i>.</li>
 *   <li><b>Protocole de communication IPC :</b> Dialogue bidirectionnel en temps réel via les flux standards (stdout/stdin)
 *       avec balises textuelles ({@code PROGRESS:step:total:msg}, {@code LIVE_SEGMENT:...}, {@code FINAL_SEGMENT:...}).</li>
 *   <li><b>Isolation & Sécurité :</b> Arbre de processus sous haute surveillance. En cas d'annulation ou de fermeture
 *       abrupte d'OmeRyth, un ShutdownHook Java déclenche une destruction récursive de l'arbre de processus
 *       via {@code ProcessHandle.descendants()} et la commande Windows {@code taskkill /F /T /PID} pour éliminer
 *       tout processus zombie en mémoire.</li>
 *   <li><b>Zéro dépendance Java :</b> Analyse syntaxique du flux JSON réalisée en interne par une machine à états finis
 *       comptabilisant la profondeur des accolades.</li>
 * </ul>
 * </p>
 */
public class SpeechWorkflowService {

    private static final Set<Process> ACTIVE_PROCESSES = Collections.synchronizedSet(new HashSet<>());
    private Process currentProcess = null;
    private volatile boolean isCancelled = false;

    static {
        // Garantit qu'aucun processus Python ou FFmpeg ne reste orphelin en arrière-plan à la fermeture d'OmeRyth
        Runtime.getRuntime().addShutdownHook(new Thread(SpeechWorkflowService::killAllProcesses));
    }

    /**
     * Arrête immédiatement et sans délai tous les processus actifs enregistrés ainsi que leurs descendants.
     */
    public static void killAllProcesses() {
        synchronized (ACTIVE_PROCESSES) {
            for (Process p : ACTIVE_PROCESSES) {
                try {
                    killProcessTree(p);
                } catch (Exception ignored) {}
            }
            ACTIVE_PROCESSES.clear();
        }
    }

    /**
     * Détruit de manière récursive et immédiate un processus et tous ses sous-processus descendants.
     * <p>
     * Sous Windows, la création de sous-processus par Python (ex: workers PyTorch ou FFmpeg) échappe souvent
     * au simple {@link Process#destroy()}. Cette méthode combine donc l'API ProcessHandle de Java et l'utilitaire
     * système {@code taskkill /F /T} pour une terminaison garantie et sans résidu.
     * </p>
     *
     * @param p Le processus parent à neutraliser.
     */
    public static void killProcessTree(Process p) {
        if (p == null) return;
        try {
            long pid = -1;
            try {
                pid = p.pid();
            } catch (Throwable ignored) {}

            // 1. Tuer tous les sous-processus descendants via l'API ProcessHandle (Java 9+)
            try {
                p.descendants().forEach(h -> {
                    try { h.destroyForcibly(); } catch (Throwable ignored) {}
                });
            } catch (Throwable ignored) {}

            // 2. Sur Windows, taskkill /F /T /PID garantit l'élimination de tout arbre de processus
            if (pid > 0 && System.getProperty("os.name", "").toLowerCase().contains("win")) {
                try {
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid))
                            .start()
                            .waitFor(2, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Throwable ignored) {}
            }

            // 3. Forcer l'arrêt du processus lui-même
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Timing précis d'un mot unitaire dans la réplique.
     */
    public static class WordTiming {
        public String word;
        public double start;
        public double end;

        public WordTiming(String word, double start, double end) {
            this.word = word;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * Séparateur rythmique interne (INNER) pour caler les mots rallongés et variations de débit.
     */
    public static class RhythmicSeparator {
        public double time;
        public int splitIndex;

        public RhythmicSeparator(double time, int splitIndex) {
            this.time = time;
            this.splitIndex = splitIndex;
        }

        public double getTime() { return time; }
        public int getSplitIndex() { return splitIndex; }
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
        public List<WordTiming> words = new ArrayList<>();
        public List<RhythmicSeparator> separators = new ArrayList<>();
        public String customRoleName = null;

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
        public List<WordTiming> getWords() { return words; }
        public List<RhythmicSeparator> getSeparators() { return separators; }

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
            if (customRoleName != null && !customRoleName.isBlank()) {
                return customRoleName;
            }
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

    /** Annule la transcription en cours et libère immédiatement toutes les ressources et processus. */
    public void cancel() {
        isCancelled = true;
        if (currentProcess != null) {
            killProcessTree(currentProcess);
            unregisterProcess(currentProcess);
            currentProcess = null;
        }
    }

    /**
     * Détecte l'exécutable Python embarqué, virtuel ou système.
     */
    private static volatile String cachedPython = null;
    private static volatile String cachedFfmpeg = null;

    public String findPython() {
        if (cachedPython != null && (new File(cachedPython).exists() || cachedPython.equals("python") || cachedPython.equals("python3") || cachedPython.equals("py"))) {
            return cachedPython;
        }

        // 1. Chercher d'abord un Python portable embarqué dans l'application
        File[] localCandidates = {
            new File("python/python.exe"),
            new File("whisperx_engine/python/python.exe"),
            new File("whisperx_engine/venv/Scripts/python.exe"),
            new File("venv/Scripts/python.exe"),
            new File("env/Scripts/python.exe")
        };
        for (File f : localCandidates) {
            if (f.exists() && f.isFile()) {
                cachedPython = f.getAbsolutePath();
                return cachedPython;
            }
        }

        // 2. Chercher dans les chemins d'installation standards Windows (AppData)
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            File pyPrograms = new File(localAppData, "Programs/Python");
            if (pyPrograms.exists() && pyPrograms.isDirectory()) {
                File[] pyDirs = pyPrograms.listFiles((dir, name) -> name.startsWith("Python"));
                if (pyDirs != null) {
                    // Trier par version la plus récente en premier
                    Arrays.sort(pyDirs, (a, b) -> b.getName().compareTo(a.getName()));
                    for (File d : pyDirs) {
                        File pyExe = new File(d, "python.exe");
                        if (pyExe.exists()) {
                            cachedPython = pyExe.getAbsolutePath();
                            return cachedPython;
                        }
                    }
                }
            }
        }

        // 3. Chercher dans le PATH système
        String[] candidates = {"python", "python3", "py"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (InputStream is = p.getInputStream()) {
                    is.transferTo(OutputStream.nullOutputStream());
                }
                if (p.waitFor() == 0) {
                    cachedPython = cmd;
                    return cachedPython;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Détecte le binaire FFmpeg embarqué ou système.
     */
    public String findFfmpeg() {
        if (cachedFfmpeg != null && (new File(cachedFfmpeg).exists() || cachedFfmpeg.equals("ffmpeg"))) {
            return cachedFfmpeg;
        }

        File localFfmpeg = new File("ffmpeg/ffmpeg.exe");
        if (localFfmpeg.exists()) {
            cachedFfmpeg = localFfmpeg.getAbsolutePath();
            return cachedFfmpeg;
        }

        File commonFfmpeg = new File("C:/ffmpeg/bin/ffmpeg.exe");
        if (commonFfmpeg.exists()) {
            cachedFfmpeg = commonFfmpeg.getAbsolutePath();
            return cachedFfmpeg;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            if (p.waitFor() == 0) {
                cachedFfmpeg = "ffmpeg";
                return cachedFfmpeg;
            }
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

    private static volatile Boolean cachedCudaAvailable = null;

    /**
     * Vérifie de façon rigoureuse et rapide si un GPU compatible CUDA est présent
     * avec ctranslate2 sans bloquer l'application. Le résultat est mis en cache.
     */
    public static boolean isCudaAvailable() {
        if (cachedCudaAvailable != null) {
            return cachedCudaAvailable;
        }
        try {
            SpeechWorkflowService service = new SpeechWorkflowService();
            String py = service.findPython();
            if (py == null) {
                cachedCudaAvailable = false;
                return false;
            }
            ProcessBuilder pb = new ProcessBuilder(py, "-c",
                    "import ctranslate2; assert ctranslate2.get_cuda_device_count() > 0");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            boolean ok = (p.waitFor() == 0);
            cachedCudaAvailable = ok;
            return ok;
        } catch (Exception e) {
            cachedCudaAvailable = false;
            return false;
        }
    }

    /**
     * Extrait l'audio de la vidéo en format WAV 16kHz mono, synchronisé précisément 1:1
     * avec la vidéo sans altération temporelle.
     * Utilise tous les cœurs CPU disponibles (-threads 0) pour une extraction ultra-rapide.
     */
    public boolean extractAudio(File videoFile, File outputWav) {
        String ffmpeg = findFfmpeg();
        if (ffmpeg == null) return false;

        // Extraction 1:1 propre et sans latence (aucun étirement d'échantillons)
        ProcessBuilder pb = new ProcessBuilder(
                ffmpeg, "-threads", "0", "-i", videoFile.getAbsolutePath(),
                "-vn", "-sn", "-dn", "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
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

        // Repli avec normalisation douce de volume
        ProcessBuilder fallbackPb = new ProcessBuilder(
                ffmpeg, "-threads", "0", "-i", videoFile.getAbsolutePath(),
                "-vn", "-sn", "-dn", "-ar", "16000", "-ac", "1", "-c:a", "pcm_s16le",
                "-af", "volume=1.1",
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
                List<TranscriptionSegment> liveSegments = new ArrayList<>();
                List<TranscriptionSegment> finalStreamedSegments = new ArrayList<>();

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
                        } else if (line.startsWith("LIVE_SEGMENT:")) {
                            String jsonStr = line.substring(13).trim();
                            TranscriptionSegment seg = parseSingleSegment(jsonStr);
                            if (seg != null) {
                                liveSegments.add(seg);
                                callback.onSegmentFound(seg);
                            }
                        } else if (line.startsWith("FINAL_SEGMENT:")) {
                            String jsonStr = line.substring(14).trim();
                            TranscriptionSegment seg = parseSingleSegment(jsonStr);
                            if (seg != null) {
                                finalStreamedSegments.add(seg);
                            }
                        } else if (line.startsWith("SEGMENT:")) {
                            String jsonStr = line.substring(8).trim();
                            TranscriptionSegment seg = parseSingleSegment(jsonStr);
                            if (seg != null) {
                                finalStreamedSegments.add(seg);
                                callback.onSegmentFound(seg);
                            }
                        } else if (line.startsWith("INFO:")) {
                            callback.onProgress(-1, line.substring(5).trim());
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

                if (exitCode != 0 && liveSegments.isEmpty() && finalStreamedSegments.isEmpty()) {
                    String errDetail = String.join("\n", outputLogs);
                    callback.onError("Échec du processus noScribe (code " + exitCode + ") :\n" + errDetail);
                    return;
                }

                // 4. Récupération des segments finaux avec garantie anti-doublon
                List<TranscriptionSegment> finalSegments = new ArrayList<>();
                if (resultJson.exists()) {
                    try {
                        String jsonContent = Files.readString(resultJson.toPath(), StandardCharsets.UTF_8);
                        List<TranscriptionSegment> parsed = parseResultJson(jsonContent, finalStreamedSegments);
                        if (parsed != null && !parsed.isEmpty()) {
                            finalSegments = parsed;
                        }
                    } catch (Exception ignored) {}
                }

                if (finalSegments.isEmpty()) {
                    if (!finalStreamedSegments.isEmpty()) {
                        finalSegments = new ArrayList<>(finalStreamedSegments);
                    } else if (!liveSegments.isEmpty()) {
                        finalSegments = new ArrayList<>(liveSegments);
                    }
                }

                callback.onProgress(100, "Transcription terminée !");
                callback.onComplete(new TranscriptionResult(finalSegments, reportFile));

            } catch (Exception e) {
                if (!isCancelled) {
                    callback.onError("Erreur : " + e.getMessage());
                }
            } finally {
                if (currentProcess != null) {
                    unregisterProcess(currentProcess);
                    killProcessTree(currentProcess);
                    currentProcess = null;
                }
                if (tempWav != null && tempWav.exists()) tempWav.delete();
                if (resultJson != null && resultJson.exists()) resultJson.delete();
            }
        }).start();
    }

    public static boolean hasAlphanumeric(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLetterOrDigit(c)) return true;
        }
        return false;
    }

    public static TranscriptionSegment parseSingleSegment(String jsonStr) {
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
            String text = textM.find() ? unescape(textM.group(1)).trim() : "";

            if (!hasAlphanumeric(text)) {
                return null;
            }

            TranscriptionSegment seg = new TranscriptionSegment(id, spk, start, end, text);

            // Extraction des mots unitaires (word timestamps)
            int wordsIdx = jsonStr.indexOf("\"words\"");
            if (wordsIdx != -1) {
                int arrStart = jsonStr.indexOf('[', wordsIdx);
                if (arrStart != -1) {
                    int arrEnd = jsonStr.indexOf(']', arrStart);
                    if (arrEnd != -1) {
                        String wordsArrayStr = jsonStr.substring(arrStart, arrEnd + 1);
                        Matcher objM = Pattern.compile("\\{([^}]+)\\}").matcher(wordsArrayStr);
                        while (objM.find()) {
                            String objBody = objM.group(1);
                            Matcher wM = Pattern.compile("\"word\"\\s*:\\s*\"((?:\\\\\"|[^\"])*)\"").matcher(objBody);
                            Matcher startM2 = Pattern.compile("\"start\"\\s*:\\s*([0-9.]+)").matcher(objBody);
                            Matcher endM2 = Pattern.compile("\"end\"\\s*:\\s*([0-9.]+)").matcher(objBody);
                            if (wM.find() && startM2.find() && endM2.find()) {
                                String wStr = unescape(wM.group(1)).trim();
                                if (hasAlphanumeric(wStr)) {
                                    double wStart = Double.parseDouble(startM2.group(1));
                                    double wEnd = Double.parseDouble(endM2.group(1));
                                    seg.words.add(new WordTiming(wStr, wStart, wEnd));
                                }
                            }
                        }
                    }
                }
            }

            // Si la liste des mots est vide, découper automatiquement le texte pour garantir la présence des mots
            if (seg.words.isEmpty() && hasAlphanumeric(text)) {
                String[] tokens = text.split("\\s+");
                double dur = Math.max(0.1, end - start);
                double step = dur / Math.max(1, tokens.length);
                for (int i = 0; i < tokens.length; i++) {
                    String tok = tokens[i].trim();
                    if (hasAlphanumeric(tok)) {
                        double wStart = Math.round((start + i * step) * 100.0) / 100.0;
                        double wEnd = Math.round((start + (i + 1) * step) * 100.0) / 100.0;
                        seg.words.add(new WordTiming(tok, wStart, wEnd));
                    }
                }
            }

            // Extraction des séparateurs rythmiques internes (INNER)
            int sepsIdx = jsonStr.indexOf("\"separators\"");
            if (sepsIdx != -1) {
                int arrStart = jsonStr.indexOf('[', sepsIdx);
                if (arrStart != -1) {
                    int arrEnd = jsonStr.indexOf(']', arrStart);
                    if (arrEnd != -1) {
                        String sepsArrayStr = jsonStr.substring(arrStart, arrEnd + 1);
                        Matcher objM = Pattern.compile("\\{([^}]+)\\}").matcher(sepsArrayStr);
                        while (objM.find()) {
                            String objBody = objM.group(1);
                            Matcher tM = Pattern.compile("\"time\"\\s*:\\s*([0-9.]+)").matcher(objBody);
                            Matcher sM = Pattern.compile("\"split_index\"\\s*:\\s*(\\d+)").matcher(objBody);
                            if (tM.find() && sM.find()) {
                                double sTime = Double.parseDouble(tM.group(1));
                                int sIdx = Integer.parseInt(sM.group(1));
                                seg.separators.add(new RhythmicSeparator(sTime, sIdx));
                            }
                        }
                    }
                }
            }

            return seg;
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
                                if (s != null && hasAlphanumeric(s.text)) {
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
