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
        app.services.SpellGrammarService.getInstance().setLanguage(customization.appLanguage);
        currentVolume = FileUtils.loadVolume();

        // Fenêtre
        updateTitle();

        // Chargement de l'icône de l'application (supporte JAR, dev et barre des tâches Windows multi-résolutions)
        Image appIcon = null;
        try {
            java.net.URL iconUrl = getClass().getResource("/images/logo.png");
            if (iconUrl != null) {
                appIcon = javax.imageio.ImageIO.read(iconUrl);
            }
        } catch (Throwable ignored) {}
        if (appIcon == null) {
            try {
                java.io.File iconFile = new java.io.File("src/images/logo.png");
                if (iconFile.exists()) {
                    appIcon = javax.imageio.ImageIO.read(iconFile);
                } else {
                    iconFile = new java.io.File("images/logo.png");
                    if (iconFile.exists()) {
                        appIcon = javax.imageio.ImageIO.read(iconFile);
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (appIcon == null) {
            java.net.URL iconUrl = getClass().getResource("/images/logo.png");
            if (iconUrl != null) appIcon = new ImageIcon(iconUrl).getImage();
        }
        if (appIcon != null) {
            java.util.List<Image> iconList = new java.util.ArrayList<>();
            int[] sizes = {16, 24, 32, 48, 64, 128, 256};
            for (int s : sizes) {
                iconList.add(appIcon.getScaledInstance(s, s, Image.SCALE_SMOOTH));
            }
            try {
                setIconImages(iconList);
            } catch (Throwable ignored) {
                setIconImage(appIcon);
            }
            try {
                if (java.awt.Taskbar.isTaskbarSupported()) {
                    java.awt.Taskbar taskbar = java.awt.Taskbar.getTaskbar();
                    if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                        taskbar.setIconImage(appIcon);
                    }
                }
            } catch (Throwable ignored) {}
        }

        setSize(900, 650);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        // Look & Feel Windows natif
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

        // Met à jour le titre dès que l'état de modification de la timeline change et gère le minuteur d'autosave
        try {
            if (this.timelinePanel != null) this.timelinePanel.setDirtyCallback(this::onTimelineDirtyChanged);
        } catch (Throwable ignored) {}

        add(parts.mainPanel, BorderLayout.CENTER);
        
        // Utiliser le menu simplifié au lieu du menu complexe
        simplifiedMenuBar = new SimplifiedMenuBarPanel(this, timelinePanel);
        simplifiedMenuBar.updateLanguage(customization.appLanguage);
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
                SwingUtilities.invokeLater(() -> ouvrirFichierExterne(f));
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

    // Invoqué automatiquement lorsque l'état de modification (dirty state) de la timeline bascule
    private void onTimelineDirtyChanged() {
        try { updateTitle(); } catch (Throwable ignored) {}
        try {
            if (timelinePanel != null && timelinePanel.isDirty()) {
                if (autosaveService != null) autosaveService.start();
            } else {
                if (autosaveService != null) autosaveService.stop();
                // Nettoie toute sauvegarde temporaire lorsque le document est enregistré et propre
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

    public boolean isQwertyKeyboard() {
        if (customization != null && "qwerty".equalsIgnoreCase(customization.keyboardLayout)) {
            return true;
        }
        if (customization != null && "azerty".equalsIgnoreCase(customization.keyboardLayout)) {
            return false;
        }
        if (customization != null && "en".equalsIgnoreCase(customization.appLanguage)) {
            return true;
        }
        try {
            java.awt.im.InputContext ic = java.awt.im.InputContext.getInstance();
            if (ic != null && ic.getLocale() != null) {
                String lang = ic.getLocale().getLanguage();
                String country = ic.getLocale().getCountry();
                if (!"fr".equalsIgnoreCase(lang) && !"fr".equalsIgnoreCase(country)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        java.util.Locale def = java.util.Locale.getDefault();
        if (def != null && !"fr".equalsIgnoreCase(def.getLanguage())) {
            return true;
        }
        return false;
    }

    public boolean isEnglish() {
        return customization != null && "en".equalsIgnoreCase(customization.appLanguage);
    }

    public String getKeyText(int keyCode) {
        boolean en = isEnglish();
        boolean qwerty = isQwertyKeyboard();

        if (keyCode == KeyEvent.VK_SPACE) return en ? "SPACE" : "ESPACE";
        if (keyCode == KeyEvent.VK_ADD) return "NUMPAD +";
        if (keyCode == KeyEvent.VK_SUBTRACT) return "NUMPAD -";
        if (keyCode == KeyEvent.VK_UP) return en ? "UP ARROW" : "FLÈCHE HAUT";
        if (keyCode == KeyEvent.VK_DOWN) return en ? "DOWN ARROW" : "FLÈCHE BAS";
        if (keyCode == KeyEvent.VK_LEFT) return en ? "LEFT ARROW" : "FLÈCHE GAUCHE";
        if (keyCode == KeyEvent.VK_RIGHT) return en ? "RIGHT ARROW" : "FLÈCHE DROITE";

        if (qwerty) {
            if (keyCode == KeyEvent.VK_A) return "Q";
            if (keyCode == KeyEvent.VK_Q) return "A";
            if (keyCode == KeyEvent.VK_Z) return "W";
            if (keyCode == KeyEvent.VK_W) return "Z";
            if (keyCode == KeyEvent.VK_M) return ",";
            if (keyCode == KeyEvent.VK_COMMA) return "M";
        }
        
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
            // Enregistrer le chemin du fichier projet d'origine associé à cette sauvegarde automatique
            File origin = new File(autosaveFile.getAbsolutePath() + ".origin");
            try (java.io.PrintWriter pw = new java.io.PrintWriter(origin, java.nio.charset.StandardCharsets.UTF_8)) {
                pw.println(currentProjectFile == null ? "" : currentProjectFile.getAbsolutePath());
            } catch (Throwable ignored) {}

            ProjectManager.save(autosaveFile, fichierSelectionne, timelinePanel.getTextManager(), roles, timelinePanel.getBandCount(), timelinePanel.getPixelsPerSecond(), timelinePanel.getZoomLevelIndex());
        } catch (Exception ex) {
            System.err.println("Échec de la sauvegarde automatique : " + ex.getMessage());
        }
    }

    private void tryRecoverAutosaveOnStartup() {
        int decision = autosaveService.askRecover(this);
        if (decision != 1) return; // Non accepté ou aucun fichier à récupérer

        try {
            // Si l'utilisateur avait un projet enregistré avant le crash, tenter de retrouver son emplacement
            // Priorité absolue au marqueur '.origin' créé au moment précis du crash.
            // Repli sur le fichier 'lastproject.path' si l'origine est absente.
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

            // Charger l'autosave dans la timeline (restaure l'intégralité des segments et séparateurs)
            File loadedVideo = timelinePanel.loadProject(autosaveFile, roles);
            if (loadedVideo != null && loadedVideo.exists()) {
                fichierSelectionne = loadedVideo;
                loadVideo(loadedVideo);
            }

            // Si un fichier projet d'origine est identifié, écraser le fichier avec l'autosave récupérée
            if (lastProject != null) {
                try {
                    timelinePanel.saveProject(lastProject, fichierSelectionne, roles);
                    currentProjectFile = lastProject;
                } catch (Throwable ignored) {}
            } else {
                currentProjectFile = null;
            }

            // Marquer comme propre et rafraîchir l'interface graphique
            try { timelinePanel.clearDirty(); } catch (Throwable ignored) {}
            updateTitle();
            refreshActionHistory();

            // Supprimer le fichier temporaire d'autosave et ses marqueurs maintenant que le projet est restauré
            try {
                if (autosaveFile.exists()) autosaveFile.delete();
                File originFileDel = new File(autosaveFile.getAbsolutePath() + ".origin");
                if (originFileDel.exists()) originFileDel.delete();
                File declined = new File(autosaveFile.getAbsolutePath() + ".declined");
                if (declined.exists()) declined.delete();
            } catch (Throwable ignored) {}
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Impossible de récupérer la sauvegarde automatique : " + ex.getMessage(),
                    "Erreur de récupération",
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

        // Enregistre immédiatement le projet à l'emplacement choisi si un fichier a été sélectionné
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
    /**
     * Enregistre le projet actif sur le disque, en sollicitant l'utilisateur pour un emplacement si nécessaire.
     */
    public void sauvegarderProjet(TimelinePanel timeline) {
        if (timeline == null) timeline = this.timelinePanel;
        if (timeline == null) return;

        File selected = projectWorkflowService.ensureProjectSavePath(this, currentProjectFile, customization.defaultProjectFormat);
        if (selected == null) return;
        currentProjectFile = selected;
        updateTitle();

        timeline.saveProject(currentProjectFile, fichierSelectionne, roles);
        // Marque la timeline comme propre (enregistrée sans modification en attente)
        timeline.clearDirty();
        // Conserve le chemin du dernier projet enregistré pour la restauration automatique
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.File("lastproject.path"), java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println(currentProjectFile.getAbsolutePath());
        } catch (Throwable ignored) {}
    }

    // ==== Ouvrir Projet =====
    /**
     * Ouvre un projet existant depuis le disque et charge sa timeline ainsi que son média vidéo/audio associé.
     */
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
            // Conserve le chemin du dernier projet ouvert pour les futures restaurations
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

    /** Ouvre un fichier externe (projet .ryth/.detx ou directement une vidéo .mp4/.mkv etc.) */
    public void ouvrirFichierExterne(File f) {
        if (f == null || !f.exists()) return;
        String name = f.getName().toLowerCase();
        if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") ||
            name.endsWith(".mov") || name.endsWith(".webm") || name.endsWith(".flv") ||
            name.endsWith(".wmv") || name.endsWith(".m4v") || name.endsWith(".ts")) {
            openDroppedMedia(f);
        } else {
            ouvrirFichierProjet(f);
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

    public AppCustomization getCustomization() {
        return customization;
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
        app.services.SpellGrammarService.getInstance().setLanguage(c.appLanguage);
        if (simplifiedMenuBar != null) {
            simplifiedMenuBar.updateLanguage(c.appLanguage);
        }
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

        // Enregistrer un instantané Undo avant toute modification destructive de la timeline
        timelinePanel.recordUndoSnapshot();

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

            // Déduplication de segments superposés ou identiques et ajustement précis des bornes
            java.util.List<SpeechWorkflowService.TranscriptionSegment> cleanBandSegments = new ArrayList<>();
            for (SpeechWorkflowService.TranscriptionSegment seg : bandSegments) {
                if (seg.getText() == null || !SpeechWorkflowService.hasAlphanumeric(seg.getText()) || SpeechWorkflowService.isPromptLeakOrHallucination(seg.getText())) continue;
                seg.text = seg.text.replaceAll("(^|\\s)['’](\\w+)", "$1$2").trim();

                // Calage acoustique précis :
                // 1. Le début du segment ne doit pas commencer avant la première parole effective
                // 2. La fin du segment ne doit pas déborder au-delà du dernier mot prononcé
                if (seg.words != null && !seg.words.isEmpty()) {
                    SpeechWorkflowService.WordTiming firstWord = seg.words.get(0);
                    SpeechWorkflowService.WordTiming lastWord = seg.words.get(seg.words.size() - 1);
                    seg.startSeconds = firstWord.start;
                    seg.endSeconds = Math.max(seg.startSeconds + 0.15, lastWord.end);
                }

                if (!cleanBandSegments.isEmpty()) {
                    SpeechWorkflowService.TranscriptionSegment prevSeg = cleanBandSegments.get(cleanBandSegments.size() - 1);
                    if (Math.abs(seg.getStartSeconds() - prevSeg.getStartSeconds()) < 0.05 &&
                        Math.abs(seg.getEndSeconds() - prevSeg.getEndSeconds()) < 0.05) {
                        if (seg.getText().trim().length() > prevSeg.getText().trim().length()) {
                            cleanBandSegments.set(cleanBandSegments.size() - 1, seg);
                        }
                        continue;
                    }
                    if (seg.getStartSeconds() < prevSeg.getEndSeconds() - 0.1 && seg.getText().trim().equalsIgnoreCase(prevSeg.getText().trim())) {
                        continue;
                    }

                    // Sécurité anti-scission de mots composés français (ex: "aujourd" + "hui") ou contractions avec élision
                    String prevNorm = prevSeg.getText().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                    String currNorm = seg.getText().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                    String prevRaw = prevSeg.getText().trim();
                    String currRaw = seg.getText().trim();
                    boolean isAujourdHui = prevNorm.endsWith("aujourd") && (currNorm.startsWith("hui") || currNorm.equals("hui"));
                    boolean isElision = prevRaw.matches(".*\\b([cCdDjJlLmMntTsqQ]|qu|Qu)['’]$");

                    if (isAujourdHui || isElision) {
                        if (isAujourdHui) {
                            String punct = currRaw.replaceAll("^['’a-zA-Z0-9à-ÿÀ-Ý]+", "");
                            prevSeg.text = prevRaw.replaceAll("(?i)['’]?\\baujourd\\b", "aujourd'hui") + punct;
                        } else {
                            prevSeg.text = prevRaw + currRaw.replaceAll("^['’]+", "");
                        }
                        prevSeg.endSeconds = Math.max(prevSeg.endSeconds, seg.endSeconds);
                        if (seg.words != null && !seg.words.isEmpty()) {
                            if (prevSeg.words == null) prevSeg.words = new ArrayList<>();
                            prevSeg.words.addAll(seg.words);
                        }
                        continue;
                    }

                    // Éviter le débordement du segment précédent sur le début du nouveau
                    if (prevSeg.endSeconds > seg.startSeconds - 0.05) {
                        prevSeg.endSeconds = Math.max(prevSeg.startSeconds + 0.20, seg.startSeconds - 0.05);
                    }
                }
                cleanBandSegments.add(seg);
            }
            bandSegments = cleanBandSegments;

            int lastCommittedEndX = 0;

            // Insérer chaque réplique comme une phrase rythmo autonome avec START, INNER et END
            // Les silences et temps morts entre répliques restent fidèlement des zones vierges (blancs) sur la timeline
            for (int i = 0; i < bandSegments.size(); i++) {
                SpeechWorkflowService.TranscriptionSegment seg = bandSegments.get(i);
                String txt = (seg.text != null) ? seg.text.trim() : "";
                if (txt.isEmpty()) continue;

                Role spkRole = speakerRolesMap.get(seg.getSpeakerIndex());

                double segStartSec = Math.round(seg.startSeconds * 100.0) / 100.0;
                double segEndSec = Math.round(seg.endSeconds * 100.0) / 100.0;
                if (segEndSec <= segStartSec) {
                    segEndSec = segStartSec + 0.3;
                }

                int segStartX = timelinePanel.snapWorldXToTenth(segStartSec * pps);
                if (segStartX < lastCommittedEndX) {
                    segStartX = lastCommittedEndX;
                }

                int segEndX = timelinePanel.snapWorldXToTenth(segEndSec * pps);
                if (segEndX <= segStartX) {
                    segEndX = timelinePanel.snapWorldXToTenth(segStartX + pps * 0.1);
                    if (segEndX <= segStartX) segEndX = segStartX + 1;
                }

                // Si un prochain segment existe sur la même bande, borner la fin pour ne pas chevaucher
                if (i + 1 < bandSegments.size()) {
                    int nextStartX = timelinePanel.snapWorldXToTenth(bandSegments.get(i + 1).startSeconds * pps);
                    if (segEndX > nextStartX) {
                        segEndX = nextStartX;
                        if (segEndX <= segStartX) {
                            segEndX = timelinePanel.snapWorldXToTenth(segStartX + pps * 0.1);
                        }
                    }
                }
                lastCommittedEndX = segEndX;

                TextItem item = new TextItem(txt, segStartX, band);
                item.role = spkRole;
                textManager.addTextItem(item);

                // Séparateur Début (vert ▶) et Fin (rouge ◀)
                textManager.addSeparator(band, segStartX, SeparatorMark.Type.START);
                if (seg.getSeparators() != null) {
                    for (SpeechWorkflowService.RhythmicSeparator rsep : seg.getSeparators()) {
                        int rsepX = timelinePanel.snapWorldXToTenth(rsep.getTime() * pps);
                        if (rsepX > segStartX && rsepX < segEndX) {
                            textManager.addSeparator(band, rsepX, SeparatorMark.Type.INNER, rsep.getSplitIndex());
                        }
                    }
                }
                textManager.addSeparator(band, segEndX, SeparatorMark.Type.END);
            }
        }

        textManager.sortTexts();
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

    /**
     * Ferme proprement l'application : confirmation de sauvegarde si nécessaire,
     * arrêt des services d'arrière-plan, neutralisation des processus fils et libération des ressources VLCJ.
     */
    public void quitterApplication() {
        // Demande la sauvegarde uniquement si des modifications non enregistrées sont présentes
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
                // Demande à l'utilisateur où enregistrer si aucun fichier n'était assigné
                sauvegarderProjet(timelinePanel);
                if (currentProjectFile == null) {
                    // L'utilisateur a annulé la boîte de dialogue d'enregistrement
                    return;
                }
            } else {
                // L'utilisateur a choisi NON : ne pas sauvegarder. Enregistrer le refus pour ne pas proposer de récupération
                try {
                    File declined = new File(autosaveFile.getAbsolutePath() + ".declined");
                    if (!declined.exists()) declined.createNewFile();
                    declined.setLastModified(System.currentTimeMillis());
                    // Supprimer également les fichiers temporaires pour éviter toute restauration erronée
                    if (autosaveFile.exists()) autosaveFile.delete();
                    File originFile = new File(autosaveFile.getAbsolutePath() + ".origin");
                    if (originFile.exists()) originFile.delete();
                } catch (Throwable ignored) {}
            }
        }

        // Arrêter les services d'arrière-plan et les minuteurs
        try { if (autosaveService != null) autosaveService.stop(); } catch (Throwable ignored) {}
        try { if (actionHistoryService != null) actionHistoryService.stop(); } catch (Throwable ignored) {}
        try { SpeechWorkflowService.killAllProcesses(); } catch (Throwable ignored) {}

        // Écrire le marqueur '.clean' pour certifier un arrêt volontaire et propre
        try {
            if (autosaveFile.exists()) autosaveFile.delete();
            File clean = new File(autosaveFile.getAbsolutePath() + ".clean");
            if (!clean.exists()) clean.createNewFile();
            clean.setLastModified(System.currentTimeMillis());
        } catch (Throwable ignored) {}

        // Libérer les ressources matérielles et surfaces vidéo de LibVLC
        try {
            if (mediaPlayerComponent != null) {
                try { mediaPlayerComponent.mediaPlayer().controls().stop(); } catch (Throwable ignored) {}
                try { mediaPlayerComponent.release(); } catch (Throwable ignored) {}
                mediaPlayerComponent = null;
            }
        } catch (Throwable ignored) {}
        try { if (mediaPlayerFactory != null) { mediaPlayerFactory.release(); mediaPlayerFactory = null; } } catch (Throwable ignored) {}

        // Libérer l'interface graphique et terminer la JVM pour garantir l'absence de processus orphelin
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
        Launcher.main(args);
    }
}
