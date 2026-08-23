package app;

import javax.swing.SwingUtilities;
import app.services.VlcLogFilter;

/**
 * Lanceur de l'application.
 * Indépendant de JFrame pour garantir que les propriétés système (ex: FileDialog)
 * sont appliquées AVANT le chargement d'AWT/Swing.
 */
public class Launcher {
    public static void main(String[] args) {
        // Force l'utilisation du dialogue natif Windows moderne (IFileDialog)
        // Doit être exécuté avant tout chargement de classe AWT (donc avant JFrame)
        System.setProperty("sun.awt.windows.useCommonItemDialog", "true");
        
        VlcLogFilter.install();
        SwingUtilities.invokeLater(MainFenetre::new);
    }
}
