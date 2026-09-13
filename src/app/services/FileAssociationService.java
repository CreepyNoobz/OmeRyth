package app.services;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Service gérant l'association des fichiers .rythmo sous Windows,
 * attribuant l'icône officielle du logo OmeRyth et permettant l'ouverture
 * automatique de projets au double-clic.
 */
public class FileAssociationService {

    private static boolean alreadyAttempted = false;

    /**
     * Tente d'enregistrer l'association silencieusement en arrière-plan au lancement
     * sur les systèmes Windows.
     */
    public static void ensureRythmoAssociationAsync() {
        if (alreadyAttempted) return;
        alreadyAttempted = true;

        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) return;

        new Thread(() -> {
            try {
                registerAssociation(false);
            } catch (Throwable ignored) {}
        }, "RythmoFileAssociationThread").start();
    }

    /**
     * Déclenche l'association manuellement (par exemple depuis le menu) et affiche un message.
     */
    public static boolean associateNow(Component parent) {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (!os.contains("win")) {
            if (parent != null) {
                JOptionPane.showMessageDialog(parent,
                        "L'association de fichiers intégrée est uniquement disponible sous Windows.",
                        "Système non supporté",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            return false;
        }

        boolean success = registerAssociation(true);
        if (parent != null) {
            if (success) {
                JOptionPane.showMessageDialog(parent,
                        "Les fichiers .rythmo ont été associés avec succès au logo OmeRyth !\n" +
                                "Vos projets s'affichent désormais avec l'icône officielle.",
                        "Association réussie",
                        JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(parent,
                        "Impossible de configurer automatiquement l'association.\n" +
                                "Vous pouvez exécuter 'associer_fichiers_rythmo.bat' dans le dossier d'OmeRyth.",
                        "Association non effectuée",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
        return success;
    }

    private static boolean registerAssociation(boolean verbose) {
        try {
            File appDir = new File(".").getAbsoluteFile().getParentFile();
            File icoFile = new File("logo.ico");

            // Si logo.ico n'est pas à la racine, vérifier dans src/images ou extraire depuis les ressources
            if (!icoFile.exists()) {
                File srcIco = new File("src/images/logo.ico");
                if (srcIco.exists()) {
                    Files.copy(srcIco.toPath(), icoFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // Extraire depuis le classpath
                    try (InputStream in = FileAssociationService.class.getResourceAsStream("/images/logo.ico")) {
                        if (in != null) {
                            try (FileOutputStream out = new FileOutputStream(icoFile)) {
                                in.transferTo(out);
                            }
                        }
                    }
                }
            }

            if (!icoFile.exists()) {
                return false;
            }

            String icoPath = icoFile.getAbsolutePath();

            // Déterminer la commande de lancement
            File exeFile = new File("OmeRyth.exe");
            String openCmd;
            if (exeFile.exists()) {
                openCmd = "\"" + exeFile.getAbsolutePath() + "\" \"%1\"";
            } else {
                File jarFile = new File("OmeRyth.jar");
                if (jarFile.exists()) {
                    openCmd = "javaw.exe -jar \"" + jarFile.getAbsolutePath() + "\" \"%1\"";
                } else {
                    openCmd = "javaw.exe -cp \"" + new File("bin").getAbsolutePath() + ";" + new File("libs/*").getAbsolutePath() + "\" app.Launcher \"%1\"";
                }
            }

            // Exécution des commandes reg.exe dans HKCU (ne requiert pas de privilèges administrateur)
            runRegAdd("HKCU\\Software\\Classes\\.rythmo", "", "OmeRyth.Project");
            runRegAdd("HKCU\\Software\\Classes\\.rythmo", "Content Type", "application/x-omeryth");
            runRegAdd("HKCU\\Software\\Classes\\OmeRyth.Project", "", "Projet Bande Rythmo OmeRyth");
            runRegAdd("HKCU\\Software\\Classes\\OmeRyth.Project\\DefaultIcon", "", "\"" + icoPath + "\",0");
            runRegAdd("HKCU\\Software\\Classes\\OmeRyth.Project\\shell\\open\\command", "", openCmd);
            runRegAddNone("HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\FileExts\\.rythmo\\OpenWithProgids", "OmeRyth.Project");

            // Notifier le Shell Windows de l'actualisation des icônes
            notifyWindowsShell();

            return true;
        } catch (Throwable t) {
            if (verbose) t.printStackTrace();
            return false;
        }
    }

    private static void runRegAdd(String key, String valueName, String data) {
        try {
            ProcessBuilder pb;
            if (valueName == null || valueName.isEmpty()) {
                pb = new ProcessBuilder("reg", "add", key, "/ve", "/d", data, "/f");
            } else {
                pb = new ProcessBuilder("reg", "add", key, "/v", valueName, "/d", data, "/f");
            }
            Process p = pb.start();
            p.waitFor();
        } catch (Throwable ignored) {}
    }

    private static void runRegAddNone(String key, String valueName) {
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "add", key, "/v", valueName, "/t", "REG_NONE", "/f");
            Process p = pb.start();
            p.waitFor();
        } catch (Throwable ignored) {}
    }

    private static void notifyWindowsShell() {
        try {
            String psCommand = "Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class S { [DllImport(\"shell32.dll\")] public static extern void SHChangeNotify(int w, int u, IntPtr d1, IntPtr d2); }'; [S]::SHChangeNotify(0x08000000, 0, [IntPtr]::Zero, [IntPtr]::Zero)";
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand);
            Process p = pb.start();
            p.waitFor();
        } catch (Throwable ignored) {}
    }
}
