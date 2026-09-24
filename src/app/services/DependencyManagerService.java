package app.services;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service de diagnostic et d'installation des composants essentiels d'OmeRyth
 * (FFmpeg, LibVLC, Python / faster-whisper, JRE).
 */
public class DependencyManagerService {

    public enum ComponentType {
        FFMPEG("FFmpeg", "Moteur d'export vidéo et d'extraction audio", "~30 Mo"),
        VLC("Moteur Vidéo (LibVLC)", "Lecteur vidéo haute performance et multi-format", "~40 Mo"),
        PYTHON_WHISPER("Intelligence Artificielle (Whisper)", "Transcription automatique et détection des voix", "~150 Mo");

        private final String displayName;
        private final String description;
        private final String approximateSize;

        ComponentType(String displayName, String description, String approximateSize) {
            this.displayName = displayName;
            this.description = description;
            this.approximateSize = approximateSize;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public String getApproximateSize() { return approximateSize; }
    }

    public static class ComponentStatus {
        public final ComponentType type;
        public final boolean isInstalled;
        public final String path;
        public final String details;

        public ComponentStatus(ComponentType type, boolean isInstalled, String path, String details) {
            this.type = type;
            this.isInstalled = isInstalled;
            this.path = path;
            this.details = details;
        }
    }

    public interface DownloadProgressCallback {
        void onProgress(int percentage, String message);
        void onError(String errorMessage);
        void onComplete();
    }

    /**
     * Vérifie l'état de tous les composants essentiels.
     */
    public static List<ComponentStatus> checkAllComponents() {
        List<ComponentStatus> list = new ArrayList<>();
        list.add(checkFFmpeg());
        list.add(checkVLC());
        list.add(checkPythonWhisper());
        return list;
    }

    /**
     * Indique si tous les composants indispensables sont prêts.
     */
    public static boolean hasAllEssentialComponents() {
        List<ComponentStatus> list = checkAllComponents();
        for (ComponentStatus s : list) {
            if (!s.isInstalled) return false;
        }
        return true;
    }

    /**
     * Retourne la liste des composants manquants.
     */
    public static List<ComponentType> getMissingComponents() {
        List<ComponentType> missing = new ArrayList<>();
        for (ComponentStatus s : checkAllComponents()) {
            if (!s.isInstalled) {
                missing.add(s.type);
            }
        }
        return missing;
    }

    /**
     * Diagnostic de FFmpeg.
     */
    public static ComponentStatus checkFFmpeg() {
        File local = new File("ffmpeg/ffmpeg.exe");
        if (local.exists() && local.length() > 0) {
            return new ComponentStatus(ComponentType.FFMPEG, true, local.getAbsolutePath(), "Binaire local optimisé embarqué");
        }
        // Vérifier PATH
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            if (p.waitFor() == 0) {
                return new ComponentStatus(ComponentType.FFMPEG, true, "PATH Système", "Disponible via le système Windows");
            }
        } catch (Exception ignored) {}

        return new ComponentStatus(ComponentType.FFMPEG, false, null, "Non détecté (requis pour l'export vidéo)");
    }

    /**
     * Diagnostic de VLC.
     */
    public static ComponentStatus checkVLC() {
        File localDll = new File("vlc/libvlc.dll");
        if (localDll.exists()) {
            return new ComponentStatus(ComponentType.VLC, true, localDll.getAbsolutePath(), "Moteur LibVLC local embarqué");
        }
        // Vérification chemin standard 64-bit
        File progVlc = new File("C:/Program Files/VideoLAN/VLC/libvlc.dll");
        if (progVlc.exists()) {
            return new ComponentStatus(ComponentType.VLC, true, progVlc.getAbsolutePath(), "VLC 64-bit détecté sur le système");
        }
        return new ComponentStatus(ComponentType.VLC, false, null, "Non détecté (requis pour la lecture vidéo)");
    }

    /**
     * Diagnostic de Python et faster-whisper.
     */
    public static ComponentStatus checkPythonWhisper() {
        SpeechWorkflowService service = new SpeechWorkflowService();
        String pyCmd = service.findPython();
        if (pyCmd == null) {
            return new ComponentStatus(ComponentType.PYTHON_WHISPER, false, null, "Python non installé");
        }

        File workerScript = new File("whisperx_engine/whisperx_worker.py");
        boolean isLocal = pyCmd.toLowerCase().contains("omeryth") || pyCmd.contains("python") || pyCmd.startsWith(".\\python");

        // Si le script worker local existe et que python est détecté, le composant est immédiatement prêt
        if (workerScript.exists()) {
            return new ComponentStatus(ComponentType.PYTHON_WHISPER, true, pyCmd,
                    isLocal ? "Python portable OmeRyth avec IA active" : "Python prêt avec module IA OmeRyth");
        }

        // Test de sécurité via subprocess uniquement si nécessaire
        try {
            ProcessBuilder pb = new ProcessBuilder(pyCmd, "-c", "import faster_whisper; print('OK')");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                output = r.readLine();
            }
            if (p.waitFor() == 0 && "OK".equals(output)) {
                return new ComponentStatus(ComponentType.PYTHON_WHISPER, true, pyCmd,
                        isLocal ? "Python portable OmeRyth avec IA active" : "Python système avec faster-whisper");
            } else {
                return new ComponentStatus(ComponentType.PYTHON_WHISPER, false, pyCmd,
                        "Python présent mais bibliothèque faster-whisper manquante");
            }
        } catch (Exception e) {
            return new ComponentStatus(ComponentType.PYTHON_WHISPER, false, pyCmd, "Erreur test Python : " + e.getMessage());
        }
    }

    /**
     * Télécharge une archive ZIP et l'extrait dans le répertoire cible avec callback de progression.
     */
    public static void downloadAndExtractZip(String fileUrl, File targetDirectory, DownloadProgressCallback callback) {
        new Thread(() -> {
            File tempZip = null;
            try {
                if (!targetDirectory.exists()) targetDirectory.mkdirs();

                callback.onProgress(5, "Connexion au serveur...");
                URL url = new URL(fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "OmeRyth-Setup/1.0");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    callback.onError("Erreur serveur HTTP " + responseCode);
                    return;
                }

                int contentLength = conn.getContentLength();
                tempZip = File.createTempFile("omeryth_pkg_", ".zip");

                try (InputStream is = conn.getInputStream();
                     FileOutputStream fos = new FileOutputStream(tempZip)) {
                    byte[] buffer = new byte[8192];
                    long totalRead = 0;
                    int read;
                    long lastReportTime = System.currentTimeMillis();

                    while ((read = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                        totalRead += read;
                        long now = System.currentTimeMillis();
                        if (contentLength > 0 && now - lastReportTime > 200) {
                            lastReportTime = now;
                            int pct = (int) (10 + (totalRead * 70) / contentLength);
                            String mb = String.format("%.1f Mo / %.1f Mo", totalRead / (1024.0 * 1024.0), contentLength / (1024.0 * 1024.0));
                            callback.onProgress(pct, "Téléchargement en cours (" + mb + ")...");
                        }
                    }
                }

                callback.onProgress(85, "Extraction des fichiers dans " + targetDirectory.getName() + "...");
                unzip(tempZip, targetDirectory);

                callback.onProgress(100, "Installation terminée avec succès !");
                callback.onComplete();

            } catch (Exception e) {
                callback.onError("Échec du téléchargement/extraction : " + e.getMessage());
            } finally {
                if (tempZip != null && tempZip.exists()) {
                    tempZip.delete();
                }
            }
        }).start();
    }

    private static void unzip(File zipFile, File destDir) throws IOException {
        byte[] buffer = new byte[8192];
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry zipEntry = zis.getNextEntry();
            while (zipEntry != null) {
                File newFile = newFile(destDir, zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Impossible de créer le dossier " + newFile);
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Impossible de créer le dossier " + parent);
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zipEntry = zis.getNextEntry();
            }
            zis.closeEntry();
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());
        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entrée ZIP invalide en dehors du répertoire cible: " + zipEntry.getName());
        }
        return destFile;
    }
}
