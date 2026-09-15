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
    private int finPhraseKeyCode = KeyEvent.VK_NUMPAD3;
    private int signeMpbKeyCode = KeyEvent.VK_NUMPAD4;
    private int signeFvrKeyCode = KeyEvent.VK_NUMPAD5;
    private int signeNeutralKeyCode = KeyEvent.VK_NUMPAD6;
    private int signeVoyelleKeyCode = KeyEvent.VK_NUMPAD7;
    private int signeRespirationKeyCode = KeyEvent.VK_NUMPAD8;
    private File currentProjectFile = null;
    private ArrayList<Role> roles = new ArrayList<>();
    private AppCustomization customization = new AppCustomization();
    private TimelinePanel timelinePanel;
    private VolumePanel volumePanel;
    private int currentVolume = 100;
    private ImageBackgroundPanel mediaPanel;
    private JPanel timerPanel;
    private ImageBackgroundPanel mediaEmptyPanel;
    private CardLayout mediaCardLayout;
    private JPanel mediaContentPanel;
    private KeyBoardListener keyBoardListener;
    private SimplifiedMenuBarPanel simplifiedMenuBar;
    private final File autosaveFile = new File("autosave.rythmo.json");
    private static final int ACTION_HISTORY_MAX_LINES = 10;
    private static final int AUTOSAVE_INTERVAL_MS = 15_000;
    private ActionHistoryService actionHistoryService;
    private AutosaveService autosaveService;
    private DropImportService dropImportService;
    private MediaWorkflowService mediaWorkflowService;
    private ProjectWorkflowService projectWorkflowService;
    private MainWindowEventBinder windowEventBinder;
    private SpeechWorkflowService speechWorkflowService = new SpeechWorkflowService();
    private AudioWaveformService audioWaveformService = new AudioWaveformService();
    // ===== Constructeur =====
    /**
     * Constructeur par défaut.
     */
    public MainFenetre() {
        this(null);
    }

    /**
     * Constructeur avec chemin d'un projet optionnel à ouvrir directement.
     */
    public MainFenetre(String initialFilePath) {
        
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
        if (keybinds.containsKey("finPhraseCode")) {
            finPhraseKeyCode = Integer.parseInt(keybinds.getProperty("finPhraseCode"));
        }
        if (keybinds.containsKey("signeMpbCode")) {
            signeMpbKeyCode = Integer.parseInt(keybinds.getProperty("signeMpbCode"));
        }
        if (keybinds.containsKey("signeFvrCode")) {
            signeFvrKeyCode = Integer.parseInt(keybinds.getProperty("signeFvrCode"));
        }
        if (keybinds.containsKey("signeNeutralCode")) {
            signeNeutralKeyCode = Integer.parseInt(keybinds.getProperty("signeNeutralCode"));
        }
        if (keybinds.containsKey("signeVoyelleCode")) {
            signeVoyelleKeyCode = Integer.parseInt(keybinds.getProperty("signeVoyelleCode"));
        }
        if (keybinds.containsKey("signeRespirationCode")) {
            signeRespirationKeyCode = Integer.parseInt(keybinds.getProperty("signeRespirationCode"));
        }
        customization = FileUtils.loadCustomization();
        currentVolume = FileUtils.loadVolume();

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
        this.volumePanel = parts.volumePanel;
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
        simplifiedMenuBar = new SimplifiedMenuBarPanel(this, timelinePanel);
        setJMenuBar(simplifiedMenuBar);

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
                finPhraseKeyCode,
                signeMpbKeyCode,
                signeFvrKeyCode,
                signeNeutralKeyCode,
                signeVoyelleKeyCode,
                signeRespirationKeyCode,
                roles,
                this
        );
        addKeyListener(keyBoardListener);
        timelinePanel.addKeyListener(keyBoardListener);

        windowEventBinder.bind(this, timelinePanel, keyBoardListener, actionHistoryService, this::adjustTimeByWheel, this::quitterApplication);

        setFocusable(true);
        timelinePanel.setFocusable(true);
        timelinePanel.requestFocusInWindow();
        setVisible(true);

        // Premier lancement : gérer via FirstRunHandler (fenêtre raccourcis + tutoriel)
        FirstRunHandler.handleFirstRun(this);

        installDragAndDrop();
        if (actionHistoryService != null) {
            actionHistoryService.setOnSeekRequested(this::seekToTime);
        }
        startHistoryRefresh();
        if (initialFilePath != null && !initialFilePath.trim().isEmpty()) {
            File f = new File(initialFilePath);
            if (f.exists() && f.isFile()) {
                SwingUtilities.invokeLater(() -> ouvrirFichierProjet(f));
            } else {
                tryRecoverAutosaveOnStartup();
            }
        } else {
            tryRecoverAutosaveOnStartup();
        }
        SingleInstanceService.setMainWindow(this);
    }

    /** Ramène la fenêtre au premier plan et lui donne le focus (ex: lors du lancement d'une seconde instance). */
    public void bringToFront() {
        SwingUtilities.invokeLater(() -> {
            try {
                if (getState() == Frame.ICONIFIED) {
                    setState(Frame.NORMAL);
                }
                setVisible(true);
                setExtendedState(getExtendedState() & ~Frame.ICONIFIED);
                setAlwaysOnTop(true);
                toFront();
                requestFocus();
                setAlwaysOnTop(false);
            } catch (Throwable ignored) {}
        });
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

    public void setFinPhraseKeyCode(int keyCode) {
        this.finPhraseKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setFinPhraseKeyCode(keyCode);
    }
    public int getFinPhraseKeyCode() { return this.finPhraseKeyCode; }

    public void setSigneMpbKeyCode(int keyCode) {
        this.signeMpbKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setSigneMpbKeyCode(keyCode);
    }
    public int getSigneMpbKeyCode() { return this.signeMpbKeyCode; }

    public void setSigneFvrKeyCode(int keyCode) {
        this.signeFvrKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setSigneFvrKeyCode(keyCode);
    }
    public int getSigneFvrKeyCode() { return this.signeFvrKeyCode; }

    public void setSigneNeutralKeyCode(int keyCode) {
        this.signeNeutralKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setSigneNeutralKeyCode(keyCode);
    }
    public int getSigneNeutralKeyCode() { return this.signeNeutralKeyCode; }

    public void setSigneVoyelleKeyCode(int keyCode) {
        this.signeVoyelleKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setSigneVoyelleKeyCode(keyCode);
    }
    public int getSigneVoyelleKeyCode() { return this.signeVoyelleKeyCode; }

    public void setSigneRespirationKeyCode(int keyCode) {
        this.signeRespirationKeyCode = keyCode;
        if (keyBoardListener != null) keyBoardListener.setSigneRespirationKeyCode(keyCode);
    }
    public int getSigneRespirationKeyCode() { return this.signeRespirationKeyCode; }

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
        String beforeHash = serializeRoles(roles);
        RoleWindow window = new RoleWindow(this, roles);
        window.setVisible(true);
        if (timelinePanel != null) {
            timelinePanel.repaint();
            if (!beforeHash.equals(serializeRoles(roles))) {
                timelinePanel.markDirty();
            }
        }
    }

    private String serializeRoles(ArrayList<Role> rList) {
        if (rList == null) return "";
        StringBuilder sb = new StringBuilder();
        for (Role r : rList) {
            if (r != null) {
                sb.append(r.name).append(':').append(r.color != null ? r.color.getRGB() : 0).append(';');
            }
        }
        return sb.toString();
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

            ProjectManager.save(autosaveFile, fichierSelectionne, timelinePanel.getTextManager(), roles, timelinePanel.getBandCount(), timelinePanel.getPixelsPerSecond(), timelinePanel.getZoomLevelIndex());
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
            if (loadedVideo != null && loadedVideo.exists()) {
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
        File video = timelinePanel.loadProject(file, roles);
        if (video != null && video.exists()) {
            fichierSelectionne = video;
            loadVideo(video);
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(currentProjectFile.getAbsolutePath());
            } catch (Throwable ignored) {}
        }
        try { timelinePanel.clearDirty(); } catch (Throwable ignored) {}
        updateTitle();
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
        loadVideo(file);
        try { timelinePanel.clearDirty(); } catch (Throwable ignored) {}
        updateTitle();
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
        if (timeline == null) timeline = this.timelinePanel;
        if (timeline != null && timeline.hasContent() && timeline.isDirty()) {
            autosaveProject();
        }

        int currentBands = (timeline != null) ? timeline.getBandCount() : customization.bandCount;
        ProjectWorkflowService.NewProjectResult result = projectWorkflowService.askNewProject(this, currentBands);
        if (result == null) return;

        File video = result.videoFile;
        int bandCount = result.bandCount;
        String presetName = result.rolePreset;

        this.fichierSelectionne = video;
        this.currentProjectFile = result.projectFile;
        updateTitle();

        if (timeline != null) {
            projectWorkflowService.applyNewProjectWithPreset(timeline, roles, video, bandCount, presetName);
        }
        loadVideo(video);

        // Save project immediately to the chosen location if a file was selected
        if (this.currentProjectFile != null && timeline != null) {
            try {
                timeline.saveProject(this.currentProjectFile, this.fichierSelectionne, roles);
                timeline.clearDirty();
                try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
                    pw.println(this.currentProjectFile.getAbsolutePath());
                } catch (Throwable ignored) {}
            } catch (Throwable ex) {
                JOptionPane.showMessageDialog(this, "Erreur lors de la sauvegarde initiale: " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        } else if (timeline != null) {
            timeline.clearDirty();
        }
        updateTitle();

        JOptionPane.showMessageDialog(this, "Projet créé !");
    }
    // ===== Sauvegarder Projet =====
    /** Save the current project to disk, prompting for a path if needed. */
    public void sauvegarderProjet(TimelinePanel timeline) {
        if (timeline == null) timeline = this.timelinePanel;
        if (timeline == null) return;

        File selected = projectWorkflowService.ensureProjectSavePath(this, currentProjectFile, customization.defaultProjectFormat);
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
        if (timeline == null) timeline = this.timelinePanel;
        if (timeline != null && timeline.hasContent() && timeline.isDirty()) {
            autosaveProject();
        }

        File selected = projectWorkflowService.askProjectToOpen(this);
        if (selected == null) return;

        currentProjectFile = selected;
        File video = timeline.loadProject(currentProjectFile, roles);
        if (video != null && video.exists()) {
            fichierSelectionne = video;
            loadVideo(video);
            // Persist last opened/saved project path so recovery knows where to reapply autosave
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(currentProjectFile.getAbsolutePath());
            } catch (Throwable ignored) {}
        }
        try { timeline.clearDirty(); } catch (Throwable ignored) {}
        updateTitle();
    }

    /** Charge directement un fichier projet sélectionné (ex: lors d'un double-clic ou drag & drop). */
    public void ouvrirFichierProjet(File selected) {
        if (selected == null || !selected.exists()) return;
        if (timelinePanel != null && timelinePanel.hasContent() && timelinePanel.isDirty()) {
            autosaveProject();
        }

        currentProjectFile = selected;
        File video = timelinePanel.loadProject(currentProjectFile, roles);
        if (video != null && video.exists()) {
            fichierSelectionne = video;
            loadVideo(video);
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(currentProjectFile.getAbsolutePath());
            } catch (Throwable ignored) {}
        }
        try { timelinePanel.clearDirty(); } catch (Throwable ignored) {}
        updateTitle();
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
                () -> {
                    refreshActionHistory();
                    setVolume(currentVolume);
                }
        );
        if (timelinePanel != null) {
            timelinePanel.setWaveformData(null);
        }

        // Extraire la forme d'onde vocale en arrière-plan si activé
        if (customization.showWaveform && videoFile != null && videoFile.exists()) {
            audioWaveformService.extractWaveform(videoFile, data -> {
                if (timelinePanel != null && data != null) {
                    timelinePanel.setWaveformData(data);
                }
            });
        }
    }

    public File getFichierSelectionne() {
        return fichierSelectionne;
    }

    public boolean isWaveformVisible() {
        return customization.showWaveform;
    }

    public void setWaveformVisible(boolean visible) {
        customization.showWaveform = visible;
        FileUtils.saveCustomization(customization);
        if (timelinePanel != null) {
            timelinePanel.setWaveformVisible(visible);
        }
        if (simplifiedMenuBar != null) {
            simplifiedMenuBar.setWaveformChecked(visible);
        }
        if (visible && timelinePanel != null && timelinePanel.getWaveformData() == null && fichierSelectionne != null && fichierSelectionne.exists()) {
            audioWaveformService.extractWaveform(fichierSelectionne, data -> {
                if (timelinePanel != null && data != null) {
                    timelinePanel.setWaveformData(data);
                }
            });
        }
    }

    public void toggleWaveformVisible() {
        setWaveformVisible(!customization.showWaveform);
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
        if (volumePanel != null) {
            volumePanel.applyCustomization(c);
        }
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

    /** Exporter la piste audio sans les voix (Karaoké / Doublage). */
    public void exporterAudioSansVoix() {
        if (fichierSelectionne == null) {
            JOptionPane.showMessageDialog(this, "Aucune vidéo chargée. Veuillez d'abord ouvrir un projet.", "Vidéo requise", JOptionPane.WARNING_MESSAGE);
            return;
        }
        mediaWorkflowService.extraireAudioSansVoix(this, fichierSelectionne);
    }

    /** Exporter le projet au format .rythmo (natif OmeRyth). */
    public void exporterRythmo() {
        File selected = FileUtils.chooseSaveFile(this, "Exporter en .rythmo", "rythmo");
        if (selected == null) return;
        ProjectManager.save(selected, fichierSelectionne, timelinePanel.getTextManager(), roles,
                timelinePanel.getBandCount(), timelinePanel.getPixelsPerSecond(), timelinePanel.getZoomLevelIndex());
        JOptionPane.showMessageDialog(this, "Projet exporté en .rythmo :\n" + selected.getName(), "Export réussi", JOptionPane.INFORMATION_MESSAGE);
    }

    /** Exporter le projet au format .detx (Cappella / Chinkel). */
    public void exporterDetx() {
        File selected = FileUtils.chooseSaveFile(this, "Exporter en .detx (Cappella)", "detx");
        if (selected == null) return;
        try {
            DetxManager.saveDetx(selected, fichierSelectionne, timelinePanel.getTextManager(), roles,
                    timelinePanel.getBandCount(), timelinePanel.getPixelsPerSecond());
            JOptionPane.showMessageDialog(this, "Projet exporté en .detx :\n" + selected.getName(), "Export réussi", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erreur lors de l'export .detx : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void detecterPlans() {
        if (fichierSelectionne == null) {
            JOptionPane.showMessageDialog(this, "Aucune vidéo chargée. Veuillez d'abord ouvrir un projet.");
            return;
        }
        mediaWorkflowService.detectSceneChanges(this, fichierSelectionne, timelinePanel);
    }

    // ===== Transcription Vocale =====
    /** Ouvre la fenêtre de transcription automatique et détection des voix. */
    public void ouvrirTranscriptionWhisperX() {
        if (fichierSelectionne == null) {
            JOptionPane.showMessageDialog(this, "Aucune vidéo chargée. Veuillez d'abord ouvrir un projet.", "Vidéo requise", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int activeBand = Math.max(0, timelinePanel.getSelectedBand());
        int bandCount = timelinePanel.getBandCount();
        AutoTranscriptionTestDialog dialog = new AutoTranscriptionTestDialog(
                this, fichierSelectionne, this::importerTranscriptionDansTimeline, activeBand, bandCount);
        dialog.setVisible(true);
    }

    /** Alias de compatibilité. */
    public void ouvrirTestTranscriptionVocale() {
        ouvrirTranscriptionWhisperX();
    }

    /**
     * Importe les segments de transcription dans la timeline avec support multi-locuteurs.
     *
     * Si plusieurs locuteurs sont détectés (ex: dialogue à 2 personnes), chaque locuteur
     * est automatiquement placé sur sa propre bande (Bande base + 0, Bande base + 1, etc.)
     * avec un rôle et une couleur personnalisée, ainsi que ses séparateurs START et END.
     *
     * La coordonnée monde est calculée avec l'offset de la tête de lecture (cursorX)
     * pour garantir une synchronisation parfaite avec la vidéo.
     */
    public void importerTranscriptionDansTimeline(java.util.List<SpeechWorkflowService.TranscriptionSegment> segments, int baseTargetBand) {
        if (segments == null || segments.isEmpty()) return;

        // Coordonnées de la timeline
        double pps = timelinePanel.getPixelsPerSecond();
        TextManager textManager = timelinePanel.getTextManager();

        // Palette de 12 couleurs distinctes et esthétiques pour chaque locuteur
        java.awt.Color[] speakerColors = new java.awt.Color[]{
                new java.awt.Color(0x4E, 0xCD, 0xC4), // Locuteur 1 : Turquoise (#4ECDC4)
                new java.awt.Color(0xFF, 0x6B, 0x6B), // Locuteur 2 : Corail / Rouge doux (#FF6B6B)
                new java.awt.Color(0xFF, 0xD1, 0x66), // Locuteur 3 : Jaune d'or (#FFD166)
                new java.awt.Color(0xA0, 0x6C, 0xD5), // Locuteur 4 : Violet (#A06CD5)
                new java.awt.Color(0x06, 0xD6, 0xA0), // Locuteur 5 : Vert émeraude (#06D6A0)
                new java.awt.Color(0x11, 0x8A, 0xB2), // Locuteur 6 : Bleu ciel (#118AB2)
                new java.awt.Color(0xF7, 0x7F, 0x00), // Locuteur 7 : Orange ambré (#F77F00)
                new java.awt.Color(0xF7, 0x25, 0x85), // Locuteur 8 : Rose fuchsia (#F72585)
                new java.awt.Color(0x70, 0xE0, 0x00), // Locuteur 9 : Vert lime (#70E000)
                new java.awt.Color(0x43, 0x61, 0xEE), // Locuteur 10 : Bleu roi (#4361EE)
                new java.awt.Color(0xD6, 0x28, 0x28), // Locuteur 11 : Rouge brique (#D62828)
                new java.awt.Color(0xB5, 0x83, 0x8D), // Locuteur 12 : Lavande poudrée (#B5838D)
        };

        // Détection de tous les locuteurs uniques présents dans les segments
        Map<Integer, Role> speakerRolesMap = new HashMap<>();
        int maxSpeakerIdx = 0;

        for (SpeechWorkflowService.TranscriptionSegment seg : segments) {
            int spkIdx = seg.getSpeakerIndex();
            maxSpeakerIdx = Math.max(maxSpeakerIdx, spkIdx);
            if (!speakerRolesMap.containsKey(spkIdx)) {
                String roleName = seg.getSpeakerDisplayName();
                Role foundRole = null;
                for (Role r : roles) {
                    if (r.name != null && r.name.equalsIgnoreCase(roleName)) {
                        foundRole = r;
                        break;
                    }
                }
                if (foundRole == null) {
                    java.awt.Color color = speakerColors[spkIdx % speakerColors.length];
                    foundRole = new Role(roleName, color);
                    roles.add(foundRole);
                }
                speakerRolesMap.put(spkIdx, foundRole);
            }
        }

        // Calcul de la bande maximale nécessaire et extension automatique
        int maxNeededBand = baseTargetBand + maxSpeakerIdx;
        if (maxNeededBand >= timelinePanel.getBandCount()) {
            timelinePanel.setBandCount(maxNeededBand + 1);
        }

        // Nettoyer les bandes de destination pour éviter tout chevauchement ou doublon lors d'un réimport
        for (int b = baseTargetBand; b <= maxNeededBand; b++) {
            final int targetB = b;
            textManager.getTexts().removeIf(t -> t.band == targetB);
            if (textManager.getBandSeparators().containsKey(targetB)) {
                textManager.getBandSeparators().get(targetB).clear();
            }
        }

        // Regrouper les segments par bande
        Map<Integer, java.util.List<SpeechWorkflowService.TranscriptionSegment>> segmentsByBand = new HashMap<>();
        for (SpeechWorkflowService.TranscriptionSegment seg : segments) {
            int spkIdx = seg.getSpeakerIndex();
            int band = baseTargetBand + spkIdx;
            segmentsByBand.computeIfAbsent(band, k -> new ArrayList<>()).add(seg);
        }

        int minStep = Math.max(10, (int) Math.round(pps * 0.1));

        for (Map.Entry<Integer, java.util.List<SpeechWorkflowService.TranscriptionSegment>> entry : segmentsByBand.entrySet()) {
            int band = entry.getKey();
            java.util.List<SpeechWorkflowService.TranscriptionSegment> bandSegments = entry.getValue();
            bandSegments.sort(Comparator.comparingDouble(SpeechWorkflowService.TranscriptionSegment::getStartSeconds));

            // Dédoublonnage défensif et garantie absolue de non-chevauchement temporel sur la même bande
            java.util.List<SpeechWorkflowService.TranscriptionSegment> cleanBandSegments = new ArrayList<>();
            for (SpeechWorkflowService.TranscriptionSegment s : bandSegments) {
                // Ignorer les segments vides
                String sTxt = (s.text != null) ? s.text.trim() : "";
                if (sTxt.isEmpty()) continue;

                if (cleanBandSegments.isEmpty()) {
                    cleanBandSegments.add(s);
                } else {
                    SpeechWorkflowService.TranscriptionSegment last = cleanBandSegments.get(cleanBandSegments.size() - 1);

                    // Doublon exact (même timecode + même texte)
                    boolean isExactDuplicate = Math.abs(s.getStartSeconds() - last.getStartSeconds()) < 0.15 &&
                                              s.getText().equalsIgnoreCase(last.getText());
                    if (isExactDuplicate) continue;

                    // Si deux segments commencent quasiment au même moment (< 0.20s) sur la même bande
                    // (ex: bégaiement ou mot parasite comme "euh") :
                    // On fusionne le texte dans le segment existant pour garantir une seule phrase sans superposition !
                    if (Math.abs(s.getStartSeconds() - last.getStartSeconds()) < 0.20) {
                        last.text = (last.text + " " + s.text).trim();
                        last.endSeconds = Math.max(last.endSeconds, s.endSeconds);
                        if (s.words != null) {
                            if (last.words == null) last.words = new ArrayList<>();
                            last.words.addAll(s.words);
                        }
                        if (s.separators != null) {
                            if (last.separators == null) last.separators = new ArrayList<>();
                            last.separators.addAll(s.separators);
                        }
                        continue;
                    }

                    // Chevauchement temporel : recaler proprement la fin du précédent et le début du suivant
                    if (last.endSeconds > s.startSeconds - 0.08) {
                        last.endSeconds = Math.max(last.startSeconds + 0.25, s.startSeconds - 0.08);
                        if (s.startSeconds < last.endSeconds + 0.08) {
                            s.startSeconds = last.endSeconds + 0.08;
                            s.endSeconds = Math.max(s.startSeconds + 0.25, s.endSeconds);
                        }
                    }
                    cleanBandSegments.add(s);
                }
            }

            // Fusion défensive des mots orphelins isolés (ex: "On" isolé dans un blanc avant "va pas...")
            Set<String> orphanTokens = Set.of("on", "et", "le", "la", "un", "une", "de", "je", "tu", "il", "mais", "donc", "car", "or", "que", "qui", "à", "en", "y");
            for (int i = 0; i < cleanBandSegments.size() - 1; i++) {
                SpeechWorkflowService.TranscriptionSegment curr = cleanBandSegments.get(i);
                SpeechWorkflowService.TranscriptionSegment nxt = cleanBandSegments.get(i + 1);
                String currTxt = curr.getText().trim();
                String[] words = currTxt.split("\\s+");
                if (words.length == 1 && (nxt.getStartSeconds() - curr.getEndSeconds()) <= 0.65) {
                    String cleanWord = words[0].toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", "");
                    if (orphanTokens.contains(cleanWord)) {
                        nxt.text = currTxt + " " + nxt.text;
                        nxt.startSeconds = curr.startSeconds;
                        if (curr.words != null && !curr.words.isEmpty()) {
                            if (nxt.words == null) nxt.words = new ArrayList<>();
                            nxt.words.addAll(0, curr.words);
                        }
                        cleanBandSegments.remove(i);
                        i--;
                    }
                }
            }

            int minGapPixels = Math.max(15, (int) Math.round(pps * 0.08));
            int lastCommittedEndX = -1;

            // Insérer chaque réplique avec son séparateur de reprise (START), ses séparateurs INNER et sa fin (END)
            for (SpeechWorkflowService.TranscriptionSegment seg : cleanBandSegments) {
                String txt = (seg.text != null) ? seg.text.trim() : "";
                if (txt.isEmpty()) continue;

                Role spkRole = speakerRolesMap.get(seg.getSpeakerIndex());

                double segStartSec = Math.round(seg.startSeconds * 100.0) / 100.0;
                double segEndSec = Math.round(seg.endSeconds * 100.0) / 100.0;
                if (segEndSec <= segStartSec) {
                    segEndSec = segStartSec + 0.3;
                }

                int segStartX = timelinePanel.snapWorldXToTenth((int) Math.round(segStartSec * pps));
                int segEndX = timelinePanel.snapWorldXToTenth((int) Math.round(segEndSec * pps));
                if (segEndX - segStartX < minStep) {
                    segEndX = segStartX + minStep;
                }

                // GARANTIE ABSOLUE ANTI-SUPERPOSITION :
                // Le début de la phrase doit être strictement après la fin de la précédente + gap de sécurité
                int shift = 0;
                if (lastCommittedEndX != -1 && segStartX < lastCommittedEndX + minGapPixels) {
                    shift = (lastCommittedEndX + minGapPixels) - segStartX;
                    segStartX += shift;
                    segEndX += shift;
                }

                TextItem item = new TextItem(txt, segStartX, band);
                item.role = spkRole;
                textManager.addTextItem(item);

                // Séparateur Reprise/Début (vert ▶)
                textManager.addSeparator(band, segStartX, SeparatorMark.Type.START);

                // Séparateurs rythmiques internes (INNER) pour caler le rythme et stretcher les mots rallongés
                int lastSepX = segStartX;

                // 1) Utilisation prioritaire des séparateurs précis calculés par le worker Python (check 0.5s + mots rallongés)
                if (seg.separators != null && !seg.separators.isEmpty()) {
                    for (SpeechWorkflowService.RhythmicSeparator sep : seg.separators) {
                        int sepX = timelinePanel.snapWorldXToTenth((int) Math.round(sep.time * pps)) + shift;
                        if (sepX >= lastSepX + minStep && sepX <= segEndX - minStep &&
                            sep.splitIndex > 0 && sep.splitIndex < txt.length()) {
                            textManager.addSeparator(band, sepX, SeparatorMark.Type.INNER, sep.splitIndex);
                            lastSepX = sepX;
                        }
                    }
                }
                // 2) Repli de sécurité via word timings si seg.separators n'était pas présent
                else if (seg.words != null && seg.words.size() > 1) {
                    int charOffset = 0;
                    double lastSepTime = segStartSec;

                    for (int wIdx = 0; wIdx < seg.words.size() - 1; wIdx++) {
                        SpeechWorkflowService.WordTiming w = seg.words.get(wIdx);
                        SpeechWorkflowService.WordTiming nextW = seg.words.get(wIdx + 1);
                        double wDur = w.end - w.start;
                        double gapToNext = Math.max(0.0, nextW.start - w.end);
                        double transitionTime = (gapToNext >= 0.05) ? Math.max(w.end, nextW.start - 0.04) : w.end;
                        double elapsedSinceLast = transitionTime - lastSepTime;

                        // Trouver la position de coupure après ce mot dans txt
                        String cleanW = w.word.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "");
                        int foundIdx = -1;
                        int searchPos = -1;
                        if (!cleanW.isEmpty()) {
                            searchPos = txt.toLowerCase().indexOf(cleanW.toLowerCase(), charOffset);
                            if (searchPos == -1) {
                                searchPos = txt.toLowerCase().indexOf(cleanW.toLowerCase());
                            }
                            if (searchPos != -1) {
                                int endPos = searchPos + cleanW.length();
                                while (endPos < txt.length() && " ,;:!?.'’…-".indexOf(txt.charAt(endPos)) != -1) {
                                    endPos++;
                                }
                                foundIdx = endPos;
                                charOffset = Math.max(charOffset, endPos);
                            }
                        }

                        // Si mot rallongé, isoler le mot en plaçant un séparateur avant lui pour préserver le texte précédent
                        if ((wDur >= 0.35 || gapToNext >= 0.15) && wIdx > 0 && searchPos > 0) {
                            int preSepX = timelinePanel.snapWorldXToTenth((int) Math.round(w.start * pps)) + shift;
                            if (preSepX >= lastSepX + minStep && preSepX <= segEndX - minStep && searchPos < foundIdx) {
                                textManager.addSeparator(band, preSepX, SeparatorMark.Type.INNER, searchPos);
                                lastSepX = preSepX;
                                lastSepTime = w.start;
                                elapsedSinceLast = transitionTime - lastSepTime;
                            }
                        }

                        // Check toutes les 0.5s ou mot rallongé (>= 0.35s) ou micro-pause
                        boolean isRallonge = (wDur >= 0.35) || (elapsedSinceLast >= 0.50) || (gapToNext >= 0.08);
                        if (isRallonge && foundIdx > 0 && foundIdx < txt.length()) {
                            int sepX = timelinePanel.snapWorldXToTenth((int) Math.round(transitionTime * pps)) + shift;
                            if (sepX >= lastSepX + minStep && sepX <= segEndX - minStep) {
                                textManager.addSeparator(band, sepX, SeparatorMark.Type.INNER, foundIdx);
                                lastSepX = sepX;
                                lastSepTime = transitionTime;
                            }
                        }
                    }
                }

                // Séparateur Fin (rouge ◀)
                textManager.addSeparator(band, segEndX, SeparatorMark.Type.END);
                lastCommittedEndX = segEndX;
            }
        }

        timelinePanel.repaint();

        int distinctSpeakers = speakerRolesMap.size();
        String message;
        if (distinctSpeakers > 1) {
            message = segments.size() + " répliques réparties sur " + distinctSpeakers + " bandes (Bandes " +
                    (baseTargetBand + 1) + " à " + (maxNeededBand + 1) + ") pour " + distinctSpeakers + " locuteurs !\n" +
                    "Chaque personne a sa propre bande, son rôle et ses séparateurs.\n" +
                    "La synchronisation est calée sur la vidéo.";
        } else {
            message = segments.size() + " répliques injectées sur la bande " + (baseTargetBand + 1) + " !\n" +
                    "Les phrases sont enchaînées avec des séparateurs internes pour un rendu fluide et esthétique.\n" +
                    "La synchronisation est calée sur la vidéo.";
        }

        if (isVisible()) {
            JOptionPane.showMessageDialog(this, message, "Transcription Importée", JOptionPane.INFORMATION_MESSAGE);
        }
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
        try { SpeechWorkflowService.killAllProcesses(); } catch (Throwable ignored) {}

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
        if (keyBoardListener != null) {
            keyBoardListener.togglePlayback();
        }
    }

    /** Avance la vidéo de 0.5 secondes. */
    public void moveForward() {
        if (keyBoardListener != null) {
            keyBoardListener.seekTime(0.5);
        } else if (timer != null) {
            timer.setTime(timer.getTime() + 0.5);
        }
    }

    /** Recule la vidéo de 0.5 secondes. */
    public void moveBackward() {
        if (keyBoardListener != null) {
            keyBoardListener.seekTime(-0.5);
        } else if (timer != null) {
            timer.setTime(timer.getTime() - 0.5);
        }
    }

    /** Retour au début de la vidéo. */
    public void returnToStart() {
        if (keyBoardListener != null) {
            keyBoardListener.seekTime(-timer.getTime());
        } else if (timer != null) {
            timer.reset();
        }
    }

    /** Positionne la tête de lecture, la timeline et la vidéo au temps cible spécifié (en secondes). */
    public void seekToTime(double targetSeconds) {
        if (timelinePanel != null && timelinePanel.isEditing()) {
            timelinePanel.stopTyping();
        }
        if (keyBoardListener != null) {
            keyBoardListener.seekToTime(targetSeconds);
        } else if (timer != null) {
            timer.setTime(targetSeconds);
        }
        if (actionHistoryService != null) {
            actionHistoryService.refreshNow();
        }
        if (timelinePanel != null) {
            timelinePanel.requestFocusInWindow();
        }
    }

    public TimelinePanel getTimelinePanel() {
        return this.timelinePanel;
    }

    /** Ajoute un séparateur à la bande sélectionnée. */
    public void addSeparator() {
        if (timelinePanel != null) {
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0; // Utiliser la première bande par défaut
            timelinePanel.addSeparatorAtCursor(band);
        }
    }

    // ===== Gestion du volume sonore =====

    /** Récupère le volume actuel (0 à 100). */
    public int getVolume() {
        return currentVolume;
    }

    /** Définit le volume sonore (0 à 100) et met à jour le lecteur et le panneau. */
    public void setVolume(int volume) {
        this.currentVolume = Math.max(0, Math.min(100, volume));
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                mediaPlayerComponent.mediaPlayer().audio().setVolume(this.currentVolume);
            } catch (Throwable ignored) {}
        }
        if (volumePanel != null) {
            volumePanel.setVolume(this.currentVolume);
        }
        FileUtils.saveVolume(this.currentVolume);
    }

    /** Baisse le son de 10%. */
    public void baisserSon() {
        baisserVolume(10);
    }

    /** Baisse le volume sonore d'un certain delta en pourcentage. */
    public void baisserVolume(int delta) {
        setVolume(this.currentVolume - delta);
    }

    /** Monte le son de 10%. */
    public void monterSon() {
        monterVolume(10);
    }

    /** Augmente le volume sonore d'un certain delta en pourcentage. */
    public void monterVolume(int delta) {
        setVolume(this.currentVolume + delta);
    }

    /** Ouvre ou bascule l'affichage du panneau de volume au-dessus du chrono. */
    public void toggleVolumePanel() {
        if (volumePanel != null) {
            boolean visible = !volumePanel.isVisible();
            volumePanel.setVisible(visible);
            if (timerPanel != null) {
                timerPanel.revalidate();
                timerPanel.repaint();
            }
        }
    }

    /** Ouvre explicitement le panneau de volume. */
    public void ouvrirVolumePanel() {
        if (volumePanel != null) {
            volumePanel.setVisible(true);
            if (timerPanel != null) {
                timerPanel.revalidate();
                timerPanel.repaint();
            }
        }
    }

    // ===== Main =====
    /**
     * Point d'entrée de l'application. Installe le filtre de logs VLC puis lance
     * l'interface Swing sur l'Event Dispatch Thread.
     */
    public static void main(String[] args) {
        final String fileToOpen = (args != null && args.length > 0) ? args[0] : null;

        // Contrôle d'instance unique (Single Instance)
        if (!SingleInstanceService.registerOrNotify(fileToOpen)) {
            System.out.println("[SingleInstance] Une autre instance d'OmeRyth est déjà en cours d'exécution. Notification envoyée.");
            System.exit(0);
            return;
        }

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
        app.services.FileAssociationService.ensureRythmoAssociationAsync();
        SwingUtilities.invokeLater(() -> new MainFenetre(fileToOpen));
    }
}
