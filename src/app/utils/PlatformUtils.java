package app.utils;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Utilitaire centralisant la détection du système d'exploitation et la résolution
 * multiplateforme des exécutables externes (Python, FFmpeg, ffprobe, LibVLC).
 * <p>
 * Règle d'or : Préserve 100% de la compatibilité Windows native (chemins .exe, .dll, Registre)
 * tout en activant la compatibilité transparente sous Linux.
 */
public final class PlatformUtils {

    public enum OS {
        WINDOWS,
        LINUX,
        MACOS,
        OTHER
    }

    private static final OS CURRENT_OS;

    static {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            CURRENT_OS = OS.WINDOWS;
        } else if (osName.contains("linux")) {
            CURRENT_OS = OS.LINUX;
        } else if (osName.contains("mac") || osName.contains("darwin")) {
            CURRENT_OS = OS.MACOS;
        } else {
            CURRENT_OS = OS.OTHER;
        }
    }

    private PlatformUtils() {}

    public static OS getOS() {
        return CURRENT_OS;
    }

    public static boolean isWindows() {
        return CURRENT_OS == OS.WINDOWS;
    }

    public static boolean isLinux() {
        return CURRENT_OS == OS.LINUX;
    }

    public static boolean isMac() {
        return CURRENT_OS == OS.MACOS;
    }

    /**
     * Teste si une commande peut être exécutée via ProcessBuilder et qu'elle renvoie le code 0.
     */
    public static boolean isCommandAvailable(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            return p.waitFor() == 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Recherche l'exécutable Python adapté à l'environnement.
     * <p>
     * - Sous Windows : privilégie les chemins locaux Windows (.exe) ou installés dans AppData/PATH.
     * - Sous Linux : privilégie l'environnement virtuel local (.venv/bin/python) puis le PATH (python3, python).
     */
    public static String findPython() {
        // 1. Environnements virtuels ou portables locaux
        File[] localCandidates;
        if (isWindows()) {
            localCandidates = new File[] {
                new File(".venv/Scripts/python.exe"),
                new File("python/python.exe"),
                new File("whisperx_engine/python/python.exe"),
                new File("whisperx_engine/venv/Scripts/python.exe"),
                new File("venv/Scripts/python.exe"),
                new File("env/Scripts/python.exe")
            };
        } else {
            localCandidates = new File[] {
                new File(".venv/bin/python"),
                new File("whisperx_env/bin/python"),
                new File("whisperx_engine/venv/bin/python"),
                new File("venv/bin/python"),
                new File("env/bin/python")
            };
        }

        for (File f : localCandidates) {
            if (f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }

        // 2. Chemins spécifiques Windows (AppData)
        if (isWindows()) {
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                File pyPrograms = new File(localAppData, "Programs/Python");
                if (pyPrograms.exists() && pyPrograms.isDirectory()) {
                    File[] pyDirs = pyPrograms.listFiles((dir, name) -> name.startsWith("Python"));
                    if (pyDirs != null) {
                        java.util.Arrays.sort(pyDirs, (a, b) -> b.getName().compareTo(a.getName()));
                        for (File d : pyDirs) {
                            File pyExe = new File(d, "python.exe");
                            if (pyExe.exists()) {
                                return pyExe.getAbsolutePath();
                            }
                        }
                    }
                }
            }
        }

        // 3. Commandes dans le PATH
        String[] pathCandidates = isWindows() ? new String[] {"python", "python3", "py"} : new String[] {"python3", "python"};
        for (String cmd : pathCandidates) {
            if (isCommandAvailable(cmd, "--version")) {
                return cmd;
            }
        }

        return null;
    }

    /**
     * Recherche le binaire FFmpeg embarqué ou système.
     */
    public static String findFfmpeg() {
        // 1. Candidats locaux Windows
        if (isWindows()) {
            File local = new File("ffmpeg/ffmpeg.exe");
            if (local.exists() && local.isFile()) return local.getAbsolutePath();

            File parentLocal = new File("OmeRyth/ffmpeg/ffmpeg.exe");
            if (parentLocal.exists() && parentLocal.isFile()) return parentLocal.getAbsolutePath();

            File common = new File("C:/ffmpeg/bin/ffmpeg.exe");
            if (common.exists() && common.isFile()) return common.getAbsolutePath();
        } else {
            // Linux : vérification éventuelle d'un binaire local sans extension
            File localLinux = new File("ffmpeg/ffmpeg");
            if (localLinux.exists() && localLinux.canExecute()) return localLinux.getAbsolutePath();
        }

        // 2. Vérification dans le PATH
        if (isCommandAvailable("ffmpeg", "-version")) {
            return "ffmpeg";
        }

        return null;
    }

    /**
     * Recherche le binaire ffprobe embarqué ou système.
     */
    public static String findFfprobe() {
        // 1. Candidats locaux Windows
        if (isWindows()) {
            File local = new File("ffmpeg/ffprobe.exe");
            if (local.exists() && local.isFile()) return local.getAbsolutePath();

            File parentLocal = new File("OmeRyth/ffmpeg/ffprobe.exe");
            if (parentLocal.exists() && parentLocal.isFile()) return parentLocal.getAbsolutePath();

            File common = new File("C:/ffmpeg/bin/ffprobe.exe");
            if (common.exists() && common.isFile()) return common.getAbsolutePath();
        } else {
            // Linux : vérification éventuelle d'un binaire local sans extension
            File localLinux = new File("ffmpeg/ffprobe");
            if (localLinux.exists() && localLinux.canExecute()) return localLinux.getAbsolutePath();
        }

        // 2. Vérification dans le PATH
        if (isCommandAvailable("ffprobe", "-version")) {
            return "ffprobe";
        }

        return null;
    }
}
