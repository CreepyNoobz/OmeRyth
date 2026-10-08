package app.services;

import javax.swing.JOptionPane;
import javax.swing.Timer;
import java.awt.Component;
import java.io.File;

/**
 * Service de sauvegarde automatique périodique et de récupération après crash.
 * <p>
 * Protocole de sécurité anti-perte de données :
 * <ul>
 *   <li><b>Enregistrement cyclique :</b> Un chronomètre Swing ({@link Timer}) déclenche à intervalle régulier
 *       l'action de sauvegarde sans bloquer l'interface graphique.</li>
 *   <li><b>Détection de crash vs fermeture propre :</b>
 *       Lors d'une fermeture normale de l'application, un fichier témoin {@code .clean} est écrit avec l'horodatage courant.
 *       Au démarrage, si le fichier de sauvegarde automatique est plus récent que le marqueur {@code .clean} (ou si ce dernier
 *       est absent suite à une coupure de courant ou un arrêt brutal du système), OmeRyth propose la restauration.</li>
 *   <li><b>Respect du choix de l'utilisateur :</b> Si l'utilisateur refuse la récupération, un marqueur {@code .declined}
 *       est créé pour éviter toute invite redondante lors des démarrages ultérieurs.</li>
 * </ul>
 * </p>
 */
public class AutosaveService {

    private final File autosaveFile;
    private final int intervalMs;
    private final Runnable saveAction;
    private Timer timer;

    /**
     * Initialise le service de sauvegarde automatique.
     *
     * @param autosaveFile  Fichier de destination de la sauvegarde temporaire.
     * @param intervalMs    Période en millisecondes entre deux sauvegardes (minimum 1000 ms).
     * @param saveAction    Délégation exécutant l'écriture de l'état du projet.
     */
    public AutosaveService(File autosaveFile, int intervalMs, Runnable saveAction) {
        this.autosaveFile = autosaveFile;
        this.intervalMs = Math.max(1000, intervalMs);
        this.saveAction = saveAction;
    }

    /**
     * Démarre le minuteur de sauvegarde périodique.
     */
    public void start() {
        stop();
        timer = new Timer(intervalMs, e -> saveNow());
        timer.start();
    }

    /**
     * Arrête le minuteur de sauvegarde automatique.
     */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /**
     * Déclenche immédiatement une sauvegarde de sécurité sur le fil d'exécution courant.
     */
    public void saveNow() {
        try {
            saveAction.run();
        } catch (Exception ex) {
            System.err.println("Échec de la sauvegarde automatique : " + ex.getMessage());
        }
    }

    /**
     * Vérifie la présence d'une sauvegarde de secours valide et demande confirmation à l'utilisateur.
     *
     * @param parent Composant parent pour centrer la boîte de dialogue.
     * @return 1 si l'utilisateur accepte la restauration, 0 s'il la décline ou s'il n'y a rien à restaurer.
     */
    public int askRecover(Component parent) {
        if (!autosaveFile.exists() || autosaveFile.length() <= 0) {
            return 0;
        }

        // Si l'application a été fermée proprement après l'écriture de cette sauvegarde automatique,
        // le marqueur '.clean' possède un timestamp supérieur ou égal : aucune invite n'est requise.
        File cleanMarker = new File(autosaveFile.getAbsolutePath() + ".clean");
        if (cleanMarker.exists() && cleanMarker.lastModified() >= autosaveFile.lastModified()) {
            return 0;
        }

        // Si l'utilisateur a déjà décliné la récupération pour ce fichier, ne pas le solliciter à nouveau.
        File declined = new File(autosaveFile.getAbsolutePath() + ".declined");
        if (declined.exists() && declined.lastModified() >= autosaveFile.lastModified()) {
            return 0;
        }

        int response = JOptionPane.showConfirmDialog(
                parent,
                "Une sauvegarde automatique suite à une interruption inattendue a été trouvée.\nVoulez-vous restaurer votre session de travail ?",
                "Récupération de projet",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            // L'utilisateur accepte : on purge le marqueur de refus
            if (declined.exists()) {
                try { declined.delete(); } catch (Throwable ignored) {}
            }
            return 1;
        } else {
            // L'utilisateur refuse : suppression du fichier d'autosave et création du témoin '.declined'
            try {
                if (autosaveFile.exists()) autosaveFile.delete();
                File origin = new File(autosaveFile.getAbsolutePath() + ".origin");
                if (origin.exists()) origin.delete();
                if (!declined.exists()) declined.createNewFile();
                declined.setLastModified(System.currentTimeMillis());
            } catch (Throwable ignored) {}
            return 0;
        }
    }

    public File getAutosaveFile() {
        return autosaveFile;
    }
}

