package app.utils;

import app.ui.AppCustomization;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.io.IOException;
import java.util.Properties;
import java.util.Locale;

import java.nio.file.Files;

/**
 * Utilitaires de fichiers et de configuration : chargement/sauvegarde des keybinds,
 * customisation d'application, gestion du premier lancement et boîtes de dialogue fichier.
 */
public class FileUtils {
    static {
        // On Windows, this asks AWT to use the modern Common Item Dialog when available.
        System.setProperty("sun.awt.windows.useCommonItemDialog", "true");
    }

    // Filtre pour JFileChooser : fichiers audio/vidéo courants
    public javax.swing.filechooser.FileFilter createFileFilter() {
        return new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f == null) return false;
                if (f.isDirectory()) return true;

                String name = f.getName().toLowerCase(Locale.ROOT);
                return name.endsWith(".mp3")
                    || name.endsWith(".wav")
                    || name.endsWith(".ogg")
                    || name.endsWith(".m4a")
                    || name.endsWith(".aac")
                    || name.endsWith(".flac")
                    || name.endsWith(".mp4")
                    || name.endsWith(".mkv")
                    || name.endsWith(".avi")
                    || name.endsWith(".mov")
                    || name.endsWith(".wmv")
                    || name.endsWith(".webm")
                    || name.endsWith(".m4v")
                    || name.endsWith(".ts")
                    || name.endsWith(".mpeg")
                    || name.endsWith(".mpg")
                    || name.endsWith(".3gp")
                    || name.endsWith(".3g2");
            }

            public String getDescription() {
                return "Fichiers audio/vidéo (MP3, MP4, WAV, OGG, AVI, MKV, WEBM, etc.)";
            }
        };
    }
    // Enregistrer un fichier
    /** Copy a file from source to destination and show an error dialog on failure. */
    public void saveFile(File sourceFile, File destFile) {
        try {
            Files.copy(sourceFile.toPath(), destFile.toPath());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Erreur lors de la sauvegarde : " + e.getMessage());
        }
    }
    // Gestion des touches
    private static final String KEYBINDS_FILE = "keybinds.properties";
    private static final String CUSTOMIZATION_FILE = "customization.properties";
    private static final String APPSTATE_FILE = "appstate.properties";

    public static Properties loadKeybinds() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(KEYBINDS_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            System.out.println("Aucun fichier keybinds trouvé, valeurs par défaut utilisées");
        }
        return props;
    }

    public static void saveKeybind(String action, String key) {
        Properties props = loadKeybinds();
        props.setProperty(action, key);
        try (FileOutputStream fos = new FileOutputStream(KEYBINDS_FILE)) {
            props.store(fos, "Keybinds");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static AppCustomization loadCustomization() {
        AppCustomization c = new AppCustomization();
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(CUSTOMIZATION_FILE)) {
            props.load(fis);
        } catch (IOException ignored) {
            return c;
        }

        c.bandCount = parseInt(props.getProperty("bandCount"), c.bandCount);
        c.bandHeight = parseInt(props.getProperty("bandHeight"), c.bandHeight);
        c.timelineCursorX = parseInt(props.getProperty("timelineCursorX"), c.timelineCursorX);
        c.timerPanelWidth = parseInt(props.getProperty("timerPanelWidth"), c.timerPanelWidth);
        c.autoResizeTimerFont = Boolean.parseBoolean(props.getProperty("autoResizeTimerFont", String.valueOf(c.autoResizeTimerFont)));
        c.timerFontSize = parseInt(props.getProperty("timerFontSize"), c.timerFontSize);

        c.timerBackground = parseColor(props.getProperty("timerBackground"), c.timerBackground);
        c.timerTextColor = parseColor(props.getProperty("timerTextColor"), c.timerTextColor);
        c.historyBackground = parseColor(props.getProperty("historyBackground"), c.historyBackground);
        c.mediaBackground = parseColor(props.getProperty("mediaBackground"), c.mediaBackground);
        c.timelineEvenBand = parseColor(props.getProperty("timelineEvenBand"), c.timelineEvenBand);
        c.timelineOddBand = parseColor(props.getProperty("timelineOddBand"), c.timelineOddBand);
        c.timelineSelectedBand = parseColor(props.getProperty("timelineSelectedBand"), c.timelineSelectedBand);
        c.timelineGrid = parseColor(props.getProperty("timelineGrid"), c.timelineGrid);
        c.timelineCursor = parseColor(props.getProperty("timelineCursor"), c.timelineCursor);
        c.timelineSeparator = parseColor(props.getProperty("timelineSeparator"), c.timelineSeparator);
        c.timerImagePath = props.getProperty("timerImagePath", c.timerImagePath);
        c.historyImagePath = props.getProperty("historyImagePath", c.historyImagePath);
        c.mediaImagePath = props.getProperty("mediaImagePath", c.mediaImagePath);

        c.bandBackgroundMode = props.getProperty("bandBackgroundMode", c.bandBackgroundMode);
        c.globalBandImagePath = props.getProperty("globalBandImagePath", c.globalBandImagePath);
        c.perBandImagePaths = props.getProperty("perBandImagePaths", c.perBandImagePaths);
        c.timelineFontFamily = props.getProperty("timelineFontFamily", c.timelineFontFamily);
        c.showWaveform = Boolean.parseBoolean(props.getProperty("showWaveform", String.valueOf(c.showWaveform)));
        c.waveformColor = parseColor(props.getProperty("waveformColor"), c.waveformColor);
        c.defaultProjectFormat = props.getProperty("defaultProjectFormat", c.defaultProjectFormat);
        return c;
    }

    public static void saveCustomization(AppCustomization c) {
        if (c == null) return;
        Properties props = new Properties();
        props.setProperty("bandCount", Integer.toString(c.bandCount));
        props.setProperty("bandHeight", Integer.toString(c.bandHeight));
        props.setProperty("timelineCursorX", Integer.toString(c.timelineCursorX));
        props.setProperty("timerPanelWidth", Integer.toString(c.timerPanelWidth));
        props.setProperty("autoResizeTimerFont", Boolean.toString(c.autoResizeTimerFont));
        props.setProperty("timerFontSize", Integer.toString(c.timerFontSize));

        props.setProperty("timerBackground", Integer.toString(c.timerBackground.getRGB()));
        props.setProperty("timerTextColor", Integer.toString(c.timerTextColor.getRGB()));
        props.setProperty("historyBackground", Integer.toString(c.historyBackground.getRGB()));
        props.setProperty("mediaBackground", Integer.toString(c.mediaBackground.getRGB()));
        props.setProperty("timelineEvenBand", Integer.toString(c.timelineEvenBand.getRGB()));
        props.setProperty("timelineOddBand", Integer.toString(c.timelineOddBand.getRGB()));
        props.setProperty("timelineSelectedBand", Integer.toString(c.timelineSelectedBand.getRGB()));
        props.setProperty("timelineGrid", Integer.toString(c.timelineGrid.getRGB()));
        props.setProperty("timelineCursor", Integer.toString(c.timelineCursor.getRGB()));
        props.setProperty("timelineSeparator", Integer.toString(c.timelineSeparator.getRGB()));

        props.setProperty("timerImagePath", safe(c.timerImagePath));
        props.setProperty("historyImagePath", safe(c.historyImagePath));
        props.setProperty("mediaImagePath", safe(c.mediaImagePath));

        props.setProperty("bandBackgroundMode", safe(c.bandBackgroundMode));
        props.setProperty("globalBandImagePath", safe(c.globalBandImagePath));
        props.setProperty("perBandImagePaths", safe(c.perBandImagePaths));
        props.setProperty("timelineFontFamily", safe(c.timelineFontFamily));
        props.setProperty("showWaveform", Boolean.toString(c.showWaveform));
        props.setProperty("waveformColor", Integer.toString(c.waveformColor.getRGB()));
        props.setProperty("defaultProjectFormat", safe(c.defaultProjectFormat));

        try (FileOutputStream fos = new FileOutputStream(CUSTOMIZATION_FILE)) {
            props.store(fos, "App customization");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns true when the application is running for the first time (no appstate file
     * or firstRunCompleted==false).
     */
    public static boolean isFirstRun() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(APPSTATE_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            // No state file -> consider first run
            return true;
        }
        return !Boolean.parseBoolean(props.getProperty("firstRunCompleted", "false"));
    }

    /**
     * Marque l'application comme ayant complété l'expérience du premier lancement.
     */
    public static void setFirstRunCompleted() {
        Properties props = new Properties();
        props.setProperty("firstRunCompleted", "true");
        try (FileOutputStream fos = new FileOutputStream(APPSTATE_FILE)) {
            props.store(fos, "App state");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Charge l'état de l'application à partir du fichier appstate.properties.
     */
    public static Properties loadAppState() {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(APPSTATE_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            System.out.println("Aucun fichier appstate trouvé, nouvel état créé");
        }
        return props;
    }

    /**
     * Sauvegarde l'état de l'application dans le fichier appstate.properties.
     */
    public static void saveAppState(Properties props) {
        if (props == null) return;
        try (FileOutputStream fos = new FileOutputStream(APPSTATE_FILE)) {
            props.store(fos, "App state");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Charge le volume sonore sauvegardé (0 à 100). Défaut: 100.
     */
    public static int loadVolume() {
        Properties props = loadAppState();
        return parseInt(props.getProperty("volume"), 100);
    }

    /**
     * Sauvegarde le volume sonore dans appstate.properties.
     */
    public static void saveVolume(int volume) {
        Properties props = loadAppState();
        props.setProperty("volume", String.valueOf(Math.max(0, Math.min(100, volume))));
        saveAppState(props);
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static java.awt.Color parseColor(String value, java.awt.Color fallback) {
        try {
            return new java.awt.Color(Integer.parseInt(value), true);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public static File chooseOpenFile(Window parent, String title, String... extensions) {
        String filter = buildOpenFilter(extensions);

        // 1. Essai via le helper natif ultra-rapide (Microsoft.Win32.OpenFileDialog moderne)
        File nativeExe = getNativeDialogExe();
        if (nativeExe != null) {
            File selected = runNativeDialog(nativeExe.getAbsolutePath(), "open", title, filter);
            if (selected != null) return selected;
        }

        // 2. Repli PowerShell (même dialogue moderne Microsoft.Win32.OpenFileDialog)
        try {
            String psScript = buildPowerShellOpenDialogScript(title, filter);
            File selected = runPowerShellDialog(psScript);
            if (selected != null) return selected;
        } catch (Throwable ignored) {}

        // 3. Repli AWT FileDialog
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                FileDialog fd;
                if (parent instanceof Frame) {
                    fd = new FileDialog((Frame) parent, title, FileDialog.LOAD);
                } else if (parent instanceof Dialog) {
                    fd = new FileDialog((Dialog) parent, title, FileDialog.LOAD);
                } else {
                    fd = new FileDialog((Frame) null, title, FileDialog.LOAD);
                }
                if (extensions != null && extensions.length > 0) {
                    fd.setFilenameFilter((dir, name) -> {
                        String lower = name.toLowerCase(Locale.ROOT);
                        for (String ext : extensions) {
                            if (lower.endsWith("." + ext.toLowerCase(Locale.ROOT))) return true;
                        }
                        return false;
                    });
                }
                fd.setVisible(true);
                String file = fd.getFile();
                String dir = fd.getDirectory();
                if (file != null && dir != null) {
                    return new File(dir, file);
                }
            } catch (Throwable ignored) {}
        }

        // 4. Repli ultime JFileChooser
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        if (extensions != null && extensions.length > 0) {
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    "Fichiers supportes", extensions));
        }
        int res = chooser.showOpenDialog(parent);
        return res == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    public static File chooseSaveFile(Window parent, String title, String defaultExtension) {
        String filter = buildSaveFilter(defaultExtension);
        String defExt = defaultExtension != null ? defaultExtension : "";

        // 1. Essai via le helper natif ultra-rapide (Microsoft.Win32.SaveFileDialog moderne)
        File nativeExe = getNativeDialogExe();
        if (nativeExe != null) {
            File selected = runNativeDialog(nativeExe.getAbsolutePath(), "save", title, filter, defExt);
            if (selected != null) {
                return ensureExtension(selected, defaultExtension);
            }
        }

        // 2. Repli PowerShell (même dialogue moderne Microsoft.Win32.SaveFileDialog)
        try {
            String psScript = buildPowerShellSaveDialogScript(title, filter);
            File selected = runPowerShellDialog(psScript);
            if (selected != null) {
                return ensureExtension(selected, defaultExtension);
            }
        } catch (Throwable ignored) {}

        // 3. Repli AWT FileDialog
        if (!GraphicsEnvironment.isHeadless()) {
            try {
                FileDialog fd;
                if (parent instanceof Frame) {
                    fd = new FileDialog((Frame) parent, title, FileDialog.SAVE);
                } else if (parent instanceof Dialog) {
                    fd = new FileDialog((Dialog) parent, title, FileDialog.SAVE);
                } else {
                    fd = new FileDialog((Frame) null, title, FileDialog.SAVE);
                }
                if (defaultExtension != null && !defaultExtension.isEmpty()) {
                    fd.setFile("*." + defaultExtension.toLowerCase(Locale.ROOT));
                }
                fd.setVisible(true);
                String file = fd.getFile();
                String dir = fd.getDirectory();
                if (file != null && dir != null) {
                    return ensureExtension(new File(dir, file), defaultExtension);
                }
            } catch (Throwable ignored) {}
        }

        // 4. Repli ultime JFileChooser
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(title);
        if (defaultExtension != null && !defaultExtension.isEmpty()) {
            chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                    defaultExtension.toUpperCase(Locale.ROOT) + " (*." + defaultExtension + ")", defaultExtension));
        }
        int res = chooser.showSaveDialog(parent);
        if (res == JFileChooser.APPROVE_OPTION) {
            return ensureExtension(chooser.getSelectedFile(), defaultExtension);
        }
        return null;
    }

    private static File ensureExtension(File file, String defaultExtension) {
        if (file == null) return null;
        if (defaultExtension != null && !defaultExtension.isEmpty()) {
            String ext = "." + defaultExtension.toLowerCase(Locale.ROOT);
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(ext)) {
                return new File(file.getAbsolutePath() + ext);
            }
        }
        return file;
    }

    private static File getNativeDialogExe() {
        File[] candidates = new File[] {
            new File("NativeDialog.exe"),
            new File("bin/NativeDialog.exe"),
            new File("OmeRyth/NativeDialog.exe")
        };
        for (File c : candidates) {
            if (c.exists() && c.isFile()) return c;
        }
        return null;
    }

    private static File runNativeDialog(String... command) {
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(false);
            Process p = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), java.nio.charset.StandardCharsets.UTF_8));
            String line;
            String result = null;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    result = line.trim();
                }
            }
            int exitCode = p.waitFor();
            if (exitCode == 0 && result != null && !result.isEmpty()) {
                return new File(result);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String buildOpenFilter(String[] extensions) {
        StringBuilder filter = new StringBuilder("Fichiers supportes|");
        if (extensions != null && extensions.length > 0) {
            for (int i = 0; i < extensions.length; i++) {
                if (i > 0) filter.append(";");
                filter.append("*.").append(extensions[i]);
            }
        } else {
            filter.append("*.*");
        }
        filter.append("|Tous les fichiers|*.*");
        return filter.toString();
    }

    private static String buildSaveFilter(String defaultExtension) {
        if (defaultExtension != null && !defaultExtension.isEmpty()) {
            String ext = defaultExtension.toLowerCase(Locale.ROOT);
            return defaultExtension.toUpperCase(Locale.ROOT) + " (*." + ext + ")|*." + ext + "|Tous les fichiers|*.*";
        }
        return "Fichiers|*.*|Tous les fichiers|*.*";
    }

    private static String buildPowerShellOpenDialogScript(String title, String filter) {
        return "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
               "Add-Type -AssemblyName PresentationFramework\n" +
               "$dlg = New-Object Microsoft.Win32.OpenFileDialog\n" +
               "$dlg.Title = '" + title.replace("'", "''") + "'\n" +
               "$dlg.Filter = '" + filter.replace("'", "''") + "'\n" +
               "$res = $dlg.ShowDialog()\n" +
               "if ($res -eq $true) { Write-Output $dlg.FileName }";
    }

    private static String buildPowerShellSaveDialogScript(String title, String filter) {
        return "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
               "Add-Type -AssemblyName PresentationFramework\n" +
               "$dlg = New-Object Microsoft.Win32.SaveFileDialog\n" +
               "$dlg.Title = '" + title.replace("'", "''") + "'\n" +
               "$dlg.Filter = '" + filter.replace("'", "''") + "'\n" +
               "$res = $dlg.ShowDialog()\n" +
               "if ($res -eq $true) { Write-Output $dlg.FileName }";
    }

    private static File runPowerShellDialog(String script) {
        try {
            String b64 = java.util.Base64.getEncoder().encodeToString(script.getBytes("UTF-16LE"));
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Sta", "-EncodedCommand", b64);
            pb.redirectErrorStream(false);
            Process p = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
            String line;
            String result = null;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty() && !line.startsWith("#< CLIXML")) {
                    result = line.trim();
                }
            }
            p.waitFor();
            if (result != null && result.contains("<Objs ")) {
                result = null;
            }
            if (result != null && !result.isEmpty()) {
                return new File(result);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
