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
        System.setProperty("sun.java2d.transaccel", "true");
        
        VlcLogFilter.install();
        app.services.FileAssociationService.ensureRythmoAssociationAsync();
        SwingUtilities.invokeLater(() -> new MainFenetre(fileToOpen));
    }
}
