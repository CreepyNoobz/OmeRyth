package app.services;

import javax.swing.JOptionPane;
import javax.swing.Timer;
import java.awt.Component;
import java.io.File;

public class AutosaveService {

    private final File autosaveFile;
    private final int intervalMs;
    private final Runnable saveAction;
    private Timer timer;

    public AutosaveService(File autosaveFile, int intervalMs, Runnable saveAction) {
        this.autosaveFile = autosaveFile;
        this.intervalMs = Math.max(1000, intervalMs);
        this.saveAction = saveAction;
    }

    /** Start the periodic autosave timer. */
    public void start() {
        stop();
        timer = new Timer(intervalMs, e -> saveNow());
        timer.start();
    }

    /** Stop the autosave timer. */
    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    /** Immediately trigger an autosave by invoking the provided save action. */
    public void saveNow() {
        try {
            saveAction.run();
        } catch (Exception ex) {
            System.err.println("Autosave failed: " + ex.getMessage());
        }
    }

    /**
     * Ask the user whether to recover the autosave.
     * Returns 1 if accepted, 0 if declined or nothing to do.
     */
    public int askRecover(Component parent) {
        if (!autosaveFile.exists() || autosaveFile.length() <= 0) {
            return 0;
        }
        // If the application shut down cleanly after this autosave was written,
        // we should not prompt. A clean shutdown writes a '.clean' marker
        // file with a timestamp at exit. Only prompt when the autosave is newer
        // than the clean marker (indicating a crash) or when the clean marker is absent.
        File cleanMarker = new File(autosaveFile.getAbsolutePath() + ".clean");
        if (cleanMarker.exists() && cleanMarker.lastModified() >= autosaveFile.lastModified()) {
            return 0;
        }

        // If the user previously declined recovery for this autosave, and the
        // declined marker is newer or equal to the autosave file, don't prompt again.
        File declined = new File(autosaveFile.getAbsolutePath() + ".declined");
        if (declined.exists() && declined.lastModified() >= autosaveFile.lastModified()) {
            return 0;
        }
        int response = JOptionPane.showConfirmDialog(
                parent,
                "Une sauvegarde automatique a ete trouvee. Voulez-vous la recuperer ?",
                "Recovery autosave",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (response == JOptionPane.YES_OPTION) {
            // If user accepted, remove any declined marker so next autosave can prompt again.
            if (declined.exists()) {
                try { declined.delete(); } catch (Throwable ignored) {}
            }
            return 1;
        } else {
            // User declined: remove the autosave so we won't ask again and
            // record the decision via the declined marker for extra safety.
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
