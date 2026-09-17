package app;

import javax.swing.SwingUtilities;
import app.services.SingleInstanceService;
import app.services.VlcLogFilter;

/**
 * Lanceur de l'application.
 * Indépendant de JFrame pour garantir que les propriétés système (ex: FileDialog)
 * sont appliquées AVANT le chargement d'AWT/Swing.
 */
public class Launcher {
    public static void main(String[] args) {
        final String fileToOpen = (args != null && args.length > 0) ? args[0] : null;

        // Contrôle d'instance unique (Single Instance)
        if (!SingleInstanceService.registerOrNotify(fileToOpen)) {
            System.out.println("[SingleInstance] Une autre instance d'OmeRyth est déjà en cours d'exécution. Notification envoyée.");
            System.exit(0);
            return;
        }

        // Force l'utilisation du dialogue natif Windows moderne (IFileDialog)
        // Doit être exécuté avant tout chargement de classe AWT (donc avant JFrame)
        System.setProperty("sun.awt.windows.useCommonItemDialog", "true");

        // Accélération matérielle Direct3D sous Windows pour un défilement ultra-fluide 60+ FPS sans saccades
        System.setProperty("sun.java2d.d3d", "true");
        System.setProperty("sun.java2d.ddforcevram", "true");
        // Configuration des chemins LibVLC (embarqué pour .exe et distribution)
        try {
            java.io.File vlcDir = new java.io.File("vlc").getAbsoluteFile();
            if (vlcDir.exists()) {
                String vlcPath = vlcDir.getAbsolutePath();
                String pluginsPath = new java.io.File(vlcDir, "plugins").getAbsolutePath();
                System.setProperty("jna.library.path", vlcPath);
                System.setProperty("VLC_PLUGIN_PATH", pluginsPath);
                String existingLibPath = System.getProperty("java.library.path", "");
                System.setProperty("java.library.path", existingLibPath.isEmpty() ? vlcPath : (existingLibPath + ";" + vlcPath));
            }
        } catch (Throwable ignored) {}

        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("crash.log", true));
                pw.println("=== CRASH in thread " + t.getName() + " ===");
                e.printStackTrace(pw);
                pw.close();
            } catch (Exception ignored) {}
        });

        VlcLogFilter.install();
        app.services.FileAssociationService.ensureRythmoAssociationAsync();
        SwingUtilities.invokeLater(() -> {
            try {
                new MainFenetre(fileToOpen);
            } catch (Throwable t) {
                try {
                    java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("crash.log", true));
                    pw.println("=== ERROR IN MainFenetre CREATION ===");
                    t.printStackTrace(pw);
                    pw.close();
                } catch (Exception ignored) {}
            }
        });
    }
}
