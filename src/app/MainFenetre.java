package app;

import app.ui.*;
import app.services.*;
import app.utils.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import uk.co.caprica.vlcj.factory.*;
import uk.co.caprica.vlcj.player.component.*;

/**
 * Fenêtre principale de l'application Omeryth.
 *
 * Initialise l'interface utilisateur, configure et lie les services (autosave, historique, media),
 * et fournit des points d'extension utilisés par les builders/services (drag & drop, autosave, etc.).
 */
public class MainFenetre extends JFrame {

    private static boolean vlcErrFilterInstalled = false;

    // ===== Champs =====
    private File fichierSelectionne = null;
    private int marcheArretKeyCode = KeyEvent.VK_SPACE; // touche par défaut
    private TimerClass timer;
    private EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private MediaPlayerFactory mediaPlayerFactory;
    private int avanceMSKeyCode = KeyEvent.VK_RIGHT;
    private int reculerMSKeyCode = KeyEvent.VK_LEFT;
    private int retourDebutKeyCode = KeyEvent.VK_R; // R par défaut
    private int separateurKeyCode = KeyEvent.VK_M; // M par défaut
    private int zoomInKeyCode = KeyEvent.VK_EQUALS;
    private int zoomOutKeyCode = KeyEvent.VK_MINUS;
    private File currentProjectFile = null;
    private ArrayList<Role> roles = new ArrayList<>();
    private AppCustomization customization = new AppCustomization();
    private TimelinePanel timelinePanel;
    private ImageBackgroundPanel mediaPanel;
    private JPanel timerPanel;
    private ImageBackgroundPanel mediaEmptyPanel;
    private CardLayout mediaCardLayout;
    private JPanel mediaContentPanel;
    private KeyBoardListener keyBoardListener;
    private final File autosaveFile = new File("autosave.rythmo.json");
    private static final int ACTION_HISTORY_MAX_LINES = 10;
    private static final int AUTOSAVE_INTERVAL_MS = 15_000;
    private ActionHistoryService actionHistoryService;
    private AutosaveService autosaveService;
    private DropImportService dropImportService;
    private MediaWorkflowService mediaWorkflowService;
    private ProjectWorkflowService projectWorkflowService;
    private MainWindowEventBinder windowEventBinder;
    // ===== Constructeur =====
    /**
     * Constructeur : charge les préférences (keybinds, customisation),
     * construit l'UI via `MainWindowBuilder`, initialise les écouteurs clavier et
     * démarre les services (autosave, historique).
     */
    public MainFenetre() {
        
        // Charger keybinds
        Properties keybinds = FileUtils.loadKeybinds();
        if (keybinds.containsKey("marcheArretCode")) {
            marcheArretKeyCode = Integer.parseInt(keybinds.getProperty("marcheArretCode"));
        }
        if (keybinds.containsKey("avanceMSCode")) {
            avanceMSKeyCode = Integer.parseInt(keybinds.getProperty("avanceMSCode"));
        }
        if (keybinds.containsKey("reculerMSCode")) {
            reculerMSKeyCode = Integer.parseInt(keybinds.getProperty("reculerMSCode"));
        }
        if (keybinds.containsKey("retourDebutCode")) {
            retourDebutKeyCode = Integer.parseInt(keybinds.getProperty("retourDebutCode"));
        }
        if (keybinds.containsKey("separateurKeyCode")) {
            separateurKeyCode = Integer.parseInt(keybinds.getProperty("separateurKeyCode"));
        }
        if (keybinds.containsKey("zoomInCode")) {
            zoomInKeyCode = Integer.parseInt(keybinds.getProperty("zoomInCode"));
        }
        if (keybinds.containsKey("zoomOutCode")) {
            zoomOutKeyCode = Integer.parseInt(keybinds.getProperty("zoomOutCode"));
        }
        customization = FileUtils.loadCustomization();

        // Fenêtre
        updateTitle();

        // Load icon - works both from JAR (resource) and from filesystem
        java.net.URL iconUrl = getClass().getResource("/images/logo.png");
        if (iconUrl != null) {
            setIconImage(new ImageIcon(iconUrl).getImage());
        } else {
            java.io.File iconFile = new java.io.File("src/images/logo.png");
            if (iconFile.exists()) setIconImage(new ImageIcon(iconFile.getAbsolutePath()).getImage());
        }

        setSize(900, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        // Look & Feel Windows
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // ===== Construire l'UI et les services via MainWindowBuilder =====
        MainWindowBuilder.Parts parts = MainWindowBuilder.build(
                this,
                customization,
                roles,
                autosaveFile,
                ACTION_HISTORY_MAX_LINES,
                AUTOSAVE_INTERVAL_MS
        );

        // Assigner les parties construites
        this.timelinePanel = parts.timelinePanel;
        this.timer = parts.timer;
        this.mediaPanel = parts.mediaPanel;
        this.timerPanel = parts.timerPanel;
        this.mediaEmptyPanel = parts.mediaEmptyPanel;
        this.mediaCardLayout = parts.mediaCardLayout;
        this.mediaContentPanel = parts.mediaContentPanel;
        this.mediaPlayerComponent = parts.mediaPlayerComponent;
        this.mediaPlayerFactory = parts.mediaPlayerFactory;
        this.actionHistoryService = parts.actionHistoryService;
        this.autosaveService = parts.autosaveService;
        this.dropImportService = parts.dropImportService;
        this.mediaWorkflowService = parts.mediaWorkflowService;
        this.projectWorkflowService = parts.projectWorkflowService;
        this.windowEventBinder = parts.windowEventBinder;

        // Update title whenever timeline dirty state changes and manage autosave timer
        try {
            if (this.timelinePanel != null) this.timelinePanel.setDirtyCallback(this::onTimelineDirtyChanged);
        } catch (Throwable ignored) {}

        add(parts.mainPanel, BorderLayout.CENTER);
        
        // Utiliser le menu simplifié au lieu du menu complexe
        setJMenuBar(new SimplifiedMenuBarPanel(this, timelinePanel));

        // Listener clavier global
        keyBoardListener = new KeyBoardListener(
                timelinePanel,
                timer,
                mediaPlayerComponent,
                marcheArretKeyCode,
                avanceMSKeyCode,
                reculerMSKeyCode,
                retourDebutKeyCode,
                separateurKeyCode,
            zoomInKeyCode,
            zoomOutKeyCode,
                roles,
                this
        );
        addKeyListener(keyBoardListener);
        timelinePanel.addKeyListener(keyBoardListener);

        windowEventBinder.bind(this, timelinePanel, keyBoardListener, this::adjustTimeByWheel, this::quitterApplication);

        setFocusable(true);
        timelinePanel.setFocusable(true);
        timelinePanel.requestFocusInWindow();
        setVisible(true);

        // Premier lancement : gérer via FirstRunHandler (fenêtre raccourcis + tutoriel)
        FirstRunHandler.handleFirstRun(this);

        installDragAndDrop();
        startHistoryRefresh();
        tryRecoverAutosaveOnStartup();
    }

    // Called when TimelinePanel dirty state toggles.
    private void onTimelineDirtyChanged() {
        try { updateTitle(); } catch (Throwable ignored) {}
        try {
            if (timelinePanel != null && timelinePanel.isDirty()) {
                if (autosaveService != null) autosaveService.start();
            } else {
                if (autosaveService != null) autosaveService.stop();
                // remove any existing autosave when document is clean
                try { if (autosaveFile.exists()) autosaveFile.delete(); } catch (Throwable ignored) {}
                try { File origin = new File(autosaveFile.getAbsolutePath() + ".origin"); if (origin.exists()) origin.delete(); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    // ===== Gestion de la touche =====
    /** Set the play/pause key code and update the keyboard listener. */
    public void setMarcheArretKeyCode(int keyCode) {
        this.marcheArretKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setMarcheArretKeyCode(keyCode);
    }
    public int getMarcheArretKeyCode() {
        return this.marcheArretKeyCode;
    }
    /** Set the forward-seek key code and update the keyboard listener. */
    public void setAvanceMSKeyCode(int keyCode) {
        this.avanceMSKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setAvanceMSKeyCode(keyCode);
    }
    public int getAvanceMSKeyCode() {
        return this.avanceMSKeyCode;
    }
    /** Set the rewind-to-start key code and update the keyboard listener. */
    public void setRetourDebutKeyCode(int keyCode) {
        this.retourDebutKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setRetourDebutKeyCode(keyCode);
    }
    public int getRetourDebutKeyCode() {
        return this.retourDebutKeyCode;
    }
    /** Set the backward-seek key code and update the keyboard listener. */
    public void setReculerMSKeyCode(int keyCode) {
        this.reculerMSKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setReculerMSKeyCode(keyCode);
    }
    public int getReculerMSKeyCode() {
        return this.reculerMSKeyCode;
    }
    /** Set the separator key code and update the keyboard listener. */
    public void setSeparateurKeyCode(int keyCode) {
        this.separateurKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setSeparateurKeyCode(keyCode);
    }
    public int getSeparateurKeyCode() {
        return this.separateurKeyCode;
    }

    /** Set the zoom-in key code and update the keyboard listener. */
    public void setZoomInKeyCode(int keyCode) {
        this.zoomInKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setZoomInKeyCode(keyCode);
    }
    public int getZoomInKeyCode() {
        return this.zoomInKeyCode;
    }

    /** Set the zoom-out key code and update the keyboard listener. */
    public void setZoomOutKeyCode(int keyCode) {
        this.zoomOutKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setZoomOutKeyCode(keyCode);
    }
    public int getZoomOutKeyCode() {
        return this.zoomOutKeyCode;
    }

    public String getKeyText(int keyCode) {
        if (keyCode == KeyEvent.VK_SPACE) return "ESPACE";
        if (keyCode == KeyEvent.VK_ADD) return "NUMPAD +";
        if (keyCode == KeyEvent.VK_SUBTRACT) return "NUMPAD -";
        if (keyCode == KeyEvent.VK_UP) return "FLÈCHE HAUT";
        if (keyCode == KeyEvent.VK_DOWN) return "FLÈCHE BAS";
        if (keyCode == KeyEvent.VK_LEFT) return "FLÈCHE GAUCHE";
        if (keyCode == KeyEvent.VK_RIGHT) return "FLÈCHE DROITE";
        
        String text = KeyEvent.getKeyText(keyCode);
        return text.toUpperCase();
    }

    public TimerClass getTimer() {
        return timer;
    }
    public File getCurrentVideoFile() {
        return this.fichierSelectionne; // File correspondant à la vidéo chargée
    }

    private void updateTitle() {
        String base = "Omeryth";
        String prefix = "";
        if (currentProjectFile != null) {
            String name = currentProjectFile.getName();
            if (name.toLowerCase().endsWith(".rythmo")) {
                name = name.substring(0, name.length() - ".rythmo".length());
            }
            base = name + " - Omeryth";
        }

        boolean dirty = false;
        try {
            if (timelinePanel != null) dirty = timelinePanel.isDirty();
        } catch (Throwable ignored) {}

        if (dirty) prefix = "* ";
        setTitle(prefix + base);
    }

    /** Open the role management window. */
    public void ouvrirRoleWindow() {
        RoleWindow window = new RoleWindow(this, roles);
        window.setVisible(true);
    }

    /** Open the customization window to edit appearance and band settings. */
    public void ouvrirCustomizationWindow() {
        CustomizationWindow window = new CustomizationWindow(this, customization, this::applyCustomization);
        window.setVisible(true);
    }

    // ===== Ouvrir la fenêtre d'info =====
    /** Open the keybind configuration window. */
    public void ouvrirKeybindWindow() {
        KeybindWindow window = new KeybindWindow(this);
        window.setVisible(true);
    }

    /** Open the informational window showing app tips and credits. */
    public void ouvrirInfoWindow() {
        InfoWindow infoWindow = new InfoWindow(this, timer);
        infoWindow.setVisible(true);
    }

    /** Undo the last timeline action and refresh history display. */
    public void undoAction() {
        timelinePanel.undo();
        refreshActionHistory();
    }

    /** Redo the next timeline action and refresh history display. */
    public void redoAction() {
        timelinePanel.redo();
        refreshActionHistory();
    }

    /** Apply a dark theme to the UI by updating customization colors. */
    public void appliquerThemeSombre() {
        AppCustomization c = customization;
        c.timerBackground = new Color(18, 24, 32);
        c.mediaBackground = new Color(20, 24, 28);
        c.timelineEvenBand = new Color(36, 42, 48);
        c.timelineOddBand = new Color(48, 54, 62);
        c.timelineSelectedBand = new Color(66, 88, 110);
        c.timelineGrid = new Color(255, 255, 255, 35);
        c.timelineCursor = new Color(255, 140, 80);
        c.timelineSeparator = new Color(255, 110, 90);
        applyCustomization(c);
    }

    /** Apply a light theme to the UI by updating customization colors. */
    public void appliquerThemeClair() {
        AppCustomization c = customization;
        c.timerBackground = new Color(234, 239, 245);
        c.mediaBackground = new Color(245, 247, 251);
        c.timelineEvenBand = new Color(230, 235, 240);
        c.timelineOddBand = new Color(216, 224, 233);
        c.timelineSelectedBand = new Color(188, 212, 237);
        c.timelineGrid = new Color(40, 40, 40, 35);
        c.timelineCursor = new Color(212, 74, 0);
        c.timelineSeparator = new Color(196, 52, 52);
        applyCustomization(c);
    }

    private void startHistoryRefresh() {
        actionHistoryService.start();
        refreshActionHistory();
    }

    private void refreshActionHistory() {
        actionHistoryService.refreshNow();
    }

    private void startAutosave() {
        autosaveService.start();
    }

    /**
     * Sauvegarde automatique : demandé par `AutosaveService`.
     * Sauvegarde le projet courant (timeline + vidéo) dans `autosave.rythmo.json`.
     */
    public void autosaveProject() {
        try {
            if (timelinePanel == null) return;
            if (fichierSelectionne == null && !timelinePanel.hasContent()) return;
            // Record the project file this autosave reflects so recovery can reapply
            File origin = new File(autosaveFile.getAbsolutePath() + ".origin");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(origin, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(currentProjectFile == null ? "" : currentProjectFile.getAbsolutePath());
            } catch (Throwable ignored) {}

            ProjectManager.save(autosaveFile, fichierSelectionne, timelinePanel.getTextManager(), roles, timelinePanel.getBandCount());
        } catch (Exception ex) {
            System.err.println("Autosave failed: " + ex.getMessage());
        }
    }

    private void tryRecoverAutosaveOnStartup() {
        int decision = autosaveService.askRecover(this);
        if (decision != 1) return; // not accepted or nothing to do

        try {
            // If user had a previously saved project path, load it first so we can
            // reapply/overwrite it with the autosave contents.
            // Prefer the origin saved with the autosave (the project active when the autosave
            // was made). Fallback to lastproject.path if no origin exists.
            File lastProject = null;
            File originFile = new File(autosaveFile.getAbsolutePath() + ".origin");
            if (originFile.exists()) {
                try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(originFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                    String p = br.readLine();
                    if (p != null && !p.isBlank()) {
                        File candidate = new File(p.trim());
                        if (candidate.exists()) lastProject = candidate;
                    }
                } catch (Throwable ignored) {}
            }
            if (lastProject == null) {
                File lastPathFile = new File("lastproject.path");
                if (lastPathFile.exists()) {
                    try (java.io.BufferedReader br = java.nio.file.Files.newBufferedReader(lastPathFile.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                        String p = br.readLine();
                        if (p != null && !p.isBlank()) {
                            File candidate = new File(p.trim());
                            if (candidate.exists()) lastProject = candidate;
                        }
                    } catch (Throwable ignored) {}
                }
            }

            // Load the autosave into the timeline (this will populate timeline state)
            File loadedVideo = timelinePanel.loadProject(autosaveFile, roles);
            if (loadedVideo != null) {
                fichierSelectionne = loadedVideo;
                loadVideo(loadedVideo);
            }

            // If we have an original project file, persist the autosave over it
            if (lastProject != null) {
                try {
                    timelinePanel.saveProject(lastProject, fichierSelectionne, roles);
                    currentProjectFile = lastProject;
                } catch (Throwable ignored) {}
            } else {
                currentProjectFile = null;
            }

            // Mark as clean and refresh UI
            try { timelinePanel.clearDirty(); } catch (Throwable ignored) {}
            updateTitle();
            refreshActionHistory();
            // Remove the autosave and origin now that it has been applied so we don't prompt again
            try {
                if (autosaveFile.exists()) autosaveFile.delete();
                File originFileDel = new File(autosaveFile.getAbsolutePath() + ".origin");
                if (originFileDel.exists()) originFileDel.delete();
                File declined = new File(autosaveFile.getAbsolutePath() + ".declined");
                if (declined.exists()) declined.delete();
            } catch (Throwable ignored) {}
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Impossible de recuperer l'autosave : " + ex.getMessage(),
                    "Recovery error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void installDragAndDrop() {
        setTransferHandler(dropImportService.createTransferHandler());
    }

    /**
     * Ouvre un projet glissé-déposé sur la fenêtre : charge le projet et la vidéo associée.
     */
    public void openDroppedProject(File file) {
        if (timelinePanel.hasContent() && timelinePanel.isDirty()) {
            autosaveProject();
        }

        currentProjectFile = file;
        updateTitle();
        File video = timelinePanel.loadProject(file, roles);
        if (video != null) {
            fichierSelectionne = video;
            loadVideo(video);
            // Loaded from a saved project - mark as clean
            try { timelinePanel.clearDirty(); } catch (Throwable ignored) {}
            updateTitle();
        }
    }

    /**
     * Ouvre un média glissé-déposé (vidéo) et le charge dans le lecteur.
     */
    public void openDroppedMedia(File file) {
        if (timelinePanel.hasContent() && timelinePanel.isDirty()) {
            autosaveProject();
        }

        fichierSelectionne = file;
        currentProjectFile = null;
        updateTitle();
        loadVideo(file);
    }

    /**
     * Affiche une alerte de type drag-and-drop avec le message fourni.
     */
    public void showDropWarning(String message) {
        JOptionPane.showMessageDialog(this,
                message,
                "Drag and drop",
                JOptionPane.WARNING_MESSAGE
        );
    }
    // ===== Nouveau Projet =====
    /** Create a new project with user-specified band count and optional video. */
    public void nouveauProjet(TimelinePanel timeline) {
        if (timeline.hasContent() && timeline.isDirty()) {
            autosaveProject();
        }

        ProjectWorkflowService.NewProjectResult result = projectWorkflowService.askNewProject(this, timeline.getBandCount());
        if (result == null) return;

        File video = result.videoFile;
        int bandCount = result.bandCount;
        String presetName = result.rolePreset;

        this.fichierSelectionne = video;
        this.currentProjectFile = result.projectFile;
        updateTitle();

        projectWorkflowService.applyNewProjectWithPreset(timeline, roles, video, bandCount, presetName);
        loadVideo(video);

        // Save project immediately to the chosen location
        if (this.currentProjectFile != null) {
            try {
                timeline.saveProject(this.currentProjectFile, this.fichierSelectionne, roles);
                timeline.clearDirty();
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
                    pw.println(this.currentProjectFile.getAbsolutePath());
                } catch (Throwable ignored) {}
            } catch (Throwable ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de la sauvegarde initiale: " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        }

        JOptionPane.showMessageDialog(this, "Projet créé !");
    }
    // ===== Sauvegarder Projet =====
    /** Save the current project to disk, prompting for a path if needed. */
    public void sauvegarderProjet(TimelinePanel timeline) {
        File selected = projectWorkflowService.ensureProjectSavePath(this, currentProjectFile);
        if (selected == null) return;
        currentProjectFile = selected;
        updateTitle();

        timeline.saveProject(currentProjectFile, fichierSelectionne, roles);
        // Mark timeline as saved / clean
        timeline.clearDirty();
        // Persist last saved project path for future autosave recovery
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println(currentProjectFile.getAbsolutePath());
        } catch (Throwable ignored) {}
    }

    // ==== Ouvrir Projet =====
    /** Open an existing project file and load its timeline and media. */
    public void ouvrirProjet(TimelinePanel timeline) {
        if (timeline.hasContent() && timeline.isDirty()) {
            autosaveProject();
        }

        File selected = projectWorkflowService.askProjectToOpen(this);
        if (selected == null) return;

        currentProjectFile = selected;
        updateTitle();

        File video = timeline.loadProject(currentProjectFile, roles);

        if (video != null) {
            fichierSelectionne = video;
            loadVideo(video);
            // Loaded from a saved project - mark as clean
            try { timeline.clearDirty(); } catch (Throwable ignored) {}
            updateTitle();
            // Persist last opened/saved project path so recovery knows where to reapply autosave
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(currentProjectFile.getAbsolutePath());
            } catch (Throwable ignored) {}
        }
    }
    // ===== Lecture vidéo =====
    /** Load and prepare a video file into the media player and timeline. */
    public void loadVideo(File videoFile) {
        mediaWorkflowService.loadVideo(
                this,
                videoFile,
                mediaPlayerComponent,
                mediaCardLayout,
                mediaContentPanel,
                timer,
                this::refreshActionHistory
        );
    }

    private void adjustTimeByWheel(MouseWheelEvent event) {
        mediaWorkflowService.adjustTimeByWheel(event, timer, mediaPlayerComponent);
    }

    private void applyCustomization(AppCustomization c) {
        if (c.bandCount < timelinePanel.getBandCount()) {
            String warning = timelinePanel.getHiddenBandWarning(c.bandCount);
            if (warning != null) {
                int response = JOptionPane.showConfirmDialog(
                        this, warning, "Contenu masqué",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (response != JOptionPane.YES_OPTION) return;
            }
        }
        this.customization = c;
        FileUtils.saveCustomization(c);
        timerPanel.setOpaque(false);
        timerPanel.setPreferredSize(new Dimension(c.timerPanelWidth, 220));
        timerPanel.setMinimumSize(new Dimension(c.timerPanelWidth, 220));
        timer.setTheme(c.timerBackground, c.timerImagePath);
        timer.applyCustomization(c);
        actionHistoryService.setTheme(c.historyBackground, c.historyImagePath);
        mediaPanel.setBackgroundStyle(c.mediaBackground, c.mediaImagePath);
        if (mediaEmptyPanel != null) {
            mediaEmptyPanel.setBackgroundStyle(c.mediaBackground, c.mediaImagePath);
        }
        timelinePanel.applyCustomization(c);
        revalidate();
        repaint();
    }

    // ===== Export vidéo bande rythmo =====
    /** Export the current timeline as a video file using the MediaWorkflowService. */
    public void exporterEnVideo() {
        mediaWorkflowService.exportVideo(this, fichierSelectionne, mediaPlayerComponent, timelinePanel);
    }

    public void detecterPlans() {
        if (fichierSelectionne == null) {
            JOptionPane.showMessageDialog(this, "Aucune vidéo chargée. Veuillez d'abord ouvrir un projet.");
            return;
        }
        mediaWorkflowService.detectSceneChanges(this, fichierSelectionne, timelinePanel);
    }

    /** Quit the application after autosave and resource cleanup. */
    public void quitterApplication() {
        // Only ask to save if the timeline has unsaved changes.
        if (timelinePanel.hasContent() && timelinePanel.isDirty()) {
            int response = JOptionPane.showConfirmDialog(this,
                    "Voulez-vous sauvegarder le projet avant de quitter ?",
                    "Sauvegarder le projet",
                    JOptionPane.YES_NO_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE);

            if (response == JOptionPane.CANCEL_OPTION || response == JOptionPane.CLOSED_OPTION) {
                return;
            }

            if (response == JOptionPane.YES_OPTION) {
                // Ask user for a save path if needed
                sauvegarderProjet(timelinePanel);
                if (currentProjectFile == null) {
                    // User cancelled save dialog
                    return;
                }
                // saved to currentProjectFile by sauvegarderProjet
            } else {
                // User chose NO: do NOT save. Record the decision so we don't offer autosave recovery.
                try {
                    File declined = new File(autosaveFile.getAbsolutePath() + ".declined");
                    if (!declined.exists()) declined.createNewFile();
                    declined.setLastModified(System.currentTimeMillis());
                    // Also remove any autosave/origin to avoid accidental restores
                    if (autosaveFile.exists()) autosaveFile.delete();
                    File originFile = new File(autosaveFile.getAbsolutePath() + ".origin");
                    if (originFile.exists()) originFile.delete();
                } catch (Throwable ignored) {}
            }
        }

        // Stop background services and timers
        try { if (autosaveService != null) autosaveService.stop(); } catch (Throwable ignored) {}
        try { if (actionHistoryService != null) actionHistoryService.stop(); } catch (Throwable ignored) {}

        // Mark a clean shutdown so we don't prompt to recover autosave on next start.
        // Also delete any stale autosave file since shutdown was intentional.
        try {
            if (autosaveFile.exists()) autosaveFile.delete();
            File clean = new File(autosaveFile.getAbsolutePath() + ".clean");
            if (!clean.exists()) clean.createNewFile();
            clean.setLastModified(System.currentTimeMillis());
        } catch (Throwable ignored) {}

        // Release media player resources
        try {
            if (mediaPlayerComponent != null) {
                try { mediaPlayerComponent.mediaPlayer().controls().stop(); } catch (Throwable ignored) {}
                try { mediaPlayerComponent.release(); } catch (Throwable ignored) {}
                mediaPlayerComponent = null;
            }
        } catch (Throwable ignored) {}
        try { if (mediaPlayerFactory != null) { mediaPlayerFactory.release(); mediaPlayerFactory = null; } } catch (Throwable ignored) {}

        // Dispose UI and exit JVM to ensure process termination
        try { dispose(); } catch (Throwable ignored) {}
        System.exit(0);
    }

    // ===== Nouvelles méthodes simplifiées pour UX améliorée =====

    /** Affiche le tutoriel interactif amélioré. */
    public void afficherTutorial() {
        EnhancedTutorialDialog dialog = new EnhancedTutorialDialog(this);
        dialog.setVisible(true);
    }

    /** Affiche la fenêtre des raccourcis clavier simplifiés. */
    public void afficherKeybinds() {
        SimplifiedKeybindWindow window = new SimplifiedKeybindWindow(this);
        window.setVisible(true);
    }

    /** Toggle la lecture/pause de la vidéo. */
    public void togglePlayPause() {
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            if (mediaPlayerComponent.mediaPlayer().status().isPlayable()) {
                if (mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
                    mediaPlayerComponent.mediaPlayer().controls().pause();
                } else {
                    mediaPlayerComponent.mediaPlayer().controls().play();
                }
            }
        }
    }

    /** Avance la vidéo de 0.5 secondes. */
    public void moveForward() {
        if (timer != null) {
            timer.setTime(timer.getTime() + 0.5);
        }
    }

    /** Recule la vidéo de 0.5 secondes. */
    public void moveBackward() {
        if (timer != null) {
            timer.setTime(timer.getTime() - 0.5);
        }
    }

    /** Retour au début de la vidéo. */
    public void returnToStart() {
        if (timer != null) {
            timer.reset();
        }
    }

    /** Ajoute un séparateur à la bande sélectionnée. */
    public void addSeparator() {
        if (timelinePanel != null) {
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0; // Utiliser la première bande par défaut
            timelinePanel.addSeparatorAtCursor(band);
        }
    }
    // ===== Main =====
    /**
     * Point d'entrée de l'application. Installe le filtre de logs VLC puis lance
     * l'interface Swing sur l'Event Dispatch Thread.
     */
    public static void main(String[] args) {
        // Must be set BEFORE any AWT/Swing classes are loaded to enable modern Windows 10/11 native dialogs
        System.setProperty("sun.awt.windows.useCommonItemDialog", "true");

        // Point VLC to the bundled vlc/ folder (for .exe distribution)
        // Works both when running from project folder and from installed location
        String vlcDir = new java.io.File("vlc").getAbsolutePath();
        String pluginsDir = new java.io.File("vlc/plugins").getAbsolutePath();
        System.setProperty("jna.library.path", vlcDir);
        System.setProperty("VLC_PLUGIN_PATH", pluginsDir);
        // Also extend java.library.path at runtime
        String existingLibPath = System.getProperty("java.library.path", "");
        System.setProperty("java.library.path", existingLibPath + ";" + vlcDir);

        VlcLogFilter.install();
        SwingUtilities.invokeLater(MainFenetre::new);
    }
}
