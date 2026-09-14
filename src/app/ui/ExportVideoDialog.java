package app.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Properties;

public class ExportVideoDialog extends JDialog {

    /**
     * Calcule la hauteur totale de la bande en fonction du nombre de bandes :
     * - 1 bande : hauteur unitaire de base (ex: 100 px).
     * - 2 bandes : hauteur divisée par 2 par bande (la hauteur totale reste baseSingleBandHeight, ex: 50 px chaque).
     * - Au-delà (3+ bandes) : chaque bande conserve au minimum la hauteur unitaire divisée par 2,
     *   donc la hauteur totale s'agrandit proportionnellement (ex: 3 bandes = 150 px, 4 bandes = 200 px).
     */
    public static int computeExportBandHeight(int bandCount, int baseSingleBandHeight) {
        if (bandCount <= 1) {
            int h = Math.max(20, baseSingleBandHeight);
            return (h % 2 == 0) ? h : h + 1;
        }
        int perBandH = Math.max(16, baseSingleBandHeight / 2);
        int totalH;
        if (bandCount == 2) {
            totalH = perBandH * 2;
        } else {
            totalH = bandCount * perBandH;
        }
        if (totalH % 2 != 0) totalH++;
        return totalH;
    }

    public static class ExportPreset {
        public final String name;
        public final int width;
        public final int height;
        public final double visibleSeconds;
        public final int fps;
        public final boolean isBuiltin;

        public ExportPreset(String name, int width, int height, double visibleSeconds, int fps, boolean isBuiltin) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.visibleSeconds = visibleSeconds;
            this.fps = fps;
            this.isBuiltin = isBuiltin;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class ExportConfig {
        public int width = 1920;
        public int height = 400;
        public double visibleSeconds = 5.0;
        public int fps = 60;
        public boolean includeAudio = true;
        public boolean removeVocals = false;
        public boolean antiCopyright = false;
        public int antiCopyrightOpacity = 20;
        public boolean isMontageMode = false;
        public Rectangle videoRect = new Rectangle(0, 0, 1920, 780);
        public Rectangle bandRect = new Rectangle(0, 780, 1920, 300);
        public String encoder = "auto";
        public boolean approved = false;
    }

    private static final String PRESETS_FILE = "export_presets.properties";

    private final TimelinePanel timelinePanel;
    private final BufferedImage videoSnapshot;
    private int bandCount = 1;
    private SingleBandPreviewPanel singleBandPreview;

    // Composants Onglet 1 : Bandeau Seul
    private final DefaultComboBoxModel<ExportPreset> presetModel = new DefaultComboBoxModel<>();
    private final JComboBox<ExportPreset> comboPreset;
    private final JButton btnSavePreset;
    private final JButton btnDeletePreset;
    private final JSpinner spinnerWidth;
    private final JSpinner spinnerHeight;
    private final JSpinner spinnerVisibleSeconds;
    private final JComboBox<String> comboFps;
    private final JComboBox<String> comboEncoder;

    // Composants Onglet 2 : Montage Vidéo + Bandeau
    private MontagePreviewCanvas montageCanvas;
    private JComboBox<String> comboMontageResolution;
    private JSpinner spinnerMontageWidth;
    private JSpinner spinnerMontageHeight;
    private JComboBox<String> comboMontageTemplate;
    private JToggleButton btnSelectVideo;
    private JToggleButton btnSelectBand;
    private JSpinner spinnerElemX;
    private JSpinner spinnerElemY;
    private JSpinner spinnerElemW;
    private JSpinner spinnerElemH;
    private JCheckBox checkMontageRemoveVocals;
    private JCheckBox checkAntiCopyright;
    private JSpinner spinnerAntiCopyrightOpacity;
    private JComboBox<String> comboMontageFps;
    private JComboBox<String> comboMontageEncoder;
    private JSpinner spinnerMontageVisibleSeconds;
    private boolean updatingMontageSpinners = false;

    private final JTabbedPane tabbedPane;
    private final ExportConfig config = new ExportConfig();
    private boolean updatingPreset = false;
    private final ExportPreset customPresetItem;

    public ExportVideoDialog(Frame owner, int currentScreenWidth, int currentScreenHeight) {
        this(owner, null, null, currentScreenWidth, currentScreenHeight);
    }

    public ExportVideoDialog(Frame owner, TimelinePanel timelinePanel) {
        this(owner, timelinePanel, null,
                timelinePanel != null && timelinePanel.getWidth() > 0 ? timelinePanel.getWidth() : 1920,
                timelinePanel != null && timelinePanel.getHeight() > 0 ? timelinePanel.getHeight() : 100);
    }

    public ExportVideoDialog(Frame owner, TimelinePanel timelinePanel, BufferedImage videoSnapshot) {
        this(owner, timelinePanel, videoSnapshot,
                timelinePanel != null && timelinePanel.getWidth() > 0 ? timelinePanel.getWidth() : 1920,
                timelinePanel != null && timelinePanel.getHeight() > 0 ? timelinePanel.getHeight() : 100);
    }

    public ExportVideoDialog(Frame owner, TimelinePanel timelinePanel, BufferedImage videoSnapshot, int currentScreenWidth, int currentScreenHeight) {
        super(owner, "Export vidéo", true);
        this.timelinePanel = timelinePanel;
        this.videoSnapshot = videoSnapshot;
        this.bandCount = (timelinePanel != null) ? Math.max(1, timelinePanel.getBandCount()) : 1;

        int initW = currentScreenWidth > 0 ? currentScreenWidth : 1920;
        if (initW % 2 != 0) initW++;
        int initH = computeExportBandHeight(bandCount, (currentScreenHeight > 0 && currentScreenHeight <= 300) ? currentScreenHeight : 100);
        if (initH % 2 != 0) initH++;
        this.customPresetItem = new ExportPreset("⚙️ Personnalisé (modifié)", initW, initH, 8.0, 60, true);

        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(900, 720));
        setPreferredSize(new Dimension(980, 800));
        setResizable(true);

        tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // ==========================================
        // ONGLET 1 : BANDEAU SEUL (CLASSIQUE)
        // ==========================================
        JPanel tabBandeauPanel = new JPanel(new BorderLayout(10, 10));

        JPanel headerPanel1 = new JPanel(new BorderLayout(5, 5));
        headerPanel1.setBackground(new Color(30, 30, 35));
        headerPanel1.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        JLabel titleLabel1 = new JLabel("🎬 Exportation Bandeau Seul");
        titleLabel1.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel1.setForeground(Color.WHITE);
        JLabel subLabel1 = new JLabel("Exporte la bande rythmo isolée (idéal pour l'incrustation directe en régie ou sous-titrage).");
        subLabel1.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subLabel1.setForeground(new Color(180, 180, 190));
        headerPanel1.add(titleLabel1, BorderLayout.NORTH);
        headerPanel1.add(subLabel1, BorderLayout.SOUTH);
        tabBandeauPanel.add(headerPanel1, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(15, 20, 10, 20));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 6, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblPreset = new JLabel("Préréglage / Format :");
        lblPreset.setFont(new Font("Segoe UI", Font.BOLD, 12));
        formPanel.add(lblPreset, gbc);

        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        JPanel presetControlPanel = new JPanel(new BorderLayout(6, 0));
        comboPreset = new JComboBox<>(presetModel);
        presetControlPanel.add(comboPreset, BorderLayout.CENTER);

        JPanel presetButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton btnBandeauMobile = new JButton("<html><span style='color:#000000; font-weight:bold;'>📱 Format Mobile (1080×1920)</span></html>");
        btnBandeauMobile.setToolTipText("Basculer immédiatement en format Mobile 1080×1920");
        btnBandeauMobile.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnBandeauMobile.setForeground(Color.BLACK);
        btnBandeauMobile.addActionListener(e -> applyMobileFormatDirect());
        btnSavePreset = new JButton("<html><span style='color:#000000;'>➕ Enregistrer...</span></html>");
        btnSavePreset.setToolTipText("Enregistrer les réglages actuels sous un nouveau nom de préréglage");
        btnSavePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSavePreset.setForeground(Color.BLACK);
        btnDeletePreset = new JButton("<html><span style='color:#000000;'>🗑️</span></html>");
        btnDeletePreset.setToolTipText("Supprimer ce préréglage personnalisé");
        btnDeletePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnDeletePreset.setForeground(Color.BLACK);
        btnDeletePreset.setEnabled(false);
        presetButtonsPanel.add(btnBandeauMobile);
        presetButtonsPanel.add(btnSavePreset);
        presetButtonsPanel.add(btnDeletePreset);
        presetControlPanel.add(presetButtonsPanel, BorderLayout.EAST);
        formPanel.add(presetControlPanel, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblWidth = new JLabel("Largeur vidéo (px) :");
        int origW = currentScreenWidth > 0 ? currentScreenWidth : 1920;
        if (origW % 2 != 0) origW++;
        int origH = computeExportBandHeight(bandCount, (currentScreenHeight > 0 && currentScreenHeight <= 300) ? currentScreenHeight : 100);
        if (origH % 2 != 0) origH++;

        formPanel.add(lblWidth, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        spinnerWidth = new JSpinner(new SpinnerNumberModel(origW, 320, 7680, 2));
        formPanel.add(spinnerWidth, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblHeight = new JLabel("Hauteur de la bande (px) :");
        formPanel.add(lblHeight, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        spinnerHeight = new JSpinner(new SpinnerNumberModel(origH, 20, 4320, 2));
        formPanel.add(spinnerHeight, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblSec = new JLabel("Vision anticipée (secondes) :");
        lblSec.setFont(new Font("Segoe UI", Font.BOLD, 12));
        formPanel.add(lblSec, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        spinnerVisibleSeconds = new JSpinner(new SpinnerNumberModel(8.0, 1.5, 20.0, 0.5));
        formPanel.add(spinnerVisibleSeconds, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblFps = new JLabel("Fluidité (FPS) :");
        formPanel.add(lblFps, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        comboFps = new JComboBox<>(new String[]{
                "60 FPS — Ultra fluide (Recommandé)",
                "30 FPS — Standard",
                "24 FPS — Cinéma / Doublage"
        });
        comboFps.setSelectedIndex(0);
        formPanel.add(comboFps, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblEnc = new JLabel("Accélération :");
        formPanel.add(lblEnc, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        comboEncoder = new JComboBox<>(new String[]{
                "🚀 Auto (GPU Détecté / Recommandé)",
                "⚡ NVIDIA NVENC (Ultra-Rapide)",
                "💻 CPU Multi-cœurs (x264 Rapide)"
        });
        comboEncoder.setSelectedIndex(0);
        formPanel.add(comboEncoder, gbc);

        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("💡 Vous pouvez créer, nommer et sauvegarder vos configurations personnalisées.");
        infoLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        infoLabel.setForeground(new Color(130, 185, 235));
        formPanel.add(infoLabel, gbc);

        singleBandPreview = new SingleBandPreviewPanel(timelinePanel, bandCount, origW, origH, 8.0);

        JPanel centerBandeauPanel = new JPanel(new BorderLayout(8, 8));
        centerBandeauPanel.add(formPanel, BorderLayout.NORTH);
        centerBandeauPanel.add(singleBandPreview, BorderLayout.CENTER);
        tabBandeauPanel.add(centerBandeauPanel, BorderLayout.CENTER);

        // ==========================================
        // BARRE D'ACCÈS RAPIDE AUX FORMATS
        // ==========================================
        JPanel topFormatBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        topFormatBar.setBackground(new Color(24, 24, 28));
        topFormatBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(45, 45, 52)));

        JLabel lblQuick = new JLabel("⚡ Formats Directs :");
        lblQuick.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblQuick.setForeground(new Color(220, 220, 225));
        topFormatBar.add(lblQuick);

        JButton btnQuickMobile = new JButton("<html><span style='color:#000000; font-weight:bold;'>📱 Format Mobile 9:16 (1080×1920)</span></html>");
        btnQuickMobile.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnQuickMobile.setBackground(new Color(245, 158, 11)); // Amber / Gold
        btnQuickMobile.setForeground(Color.BLACK);
        btnQuickMobile.setOpaque(true);
        btnQuickMobile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnQuickMobile.setToolTipText("Basculer instantanément en résolution verticale 1080×1920 pour TikTok, Reels et Shorts");
        btnQuickMobile.addActionListener(e -> applyMobileFormatDirect());
        topFormatBar.add(btnQuickMobile);

        JButton btnQuick1080p = new JButton("<html><span style='color:#000000;'>🖥️ Format Paysage 16:9 (1920×1080)</span></html>");
        btnQuick1080p.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnQuick1080p.setBackground(new Color(225, 225, 230));
        btnQuick1080p.setForeground(Color.BLACK);
        btnQuick1080p.setOpaque(true);
        btnQuick1080p.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnQuick1080p.setToolTipText("Format horizontal classique 1920×1080 Full HD");
        btnQuick1080p.addActionListener(e -> applyStandard1080pFormatDirect());
        topFormatBar.add(btnQuick1080p);

        JButton btnQuickOrigin = new JButton("<html><span style='color:#000000;'>🎯 Format d'origine OmeRyth</span></html>");
        btnQuickOrigin.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnQuickOrigin.setBackground(new Color(225, 225, 230));
        btnQuickOrigin.setForeground(Color.BLACK);
        btnQuickOrigin.setOpaque(true);
        btnQuickOrigin.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnQuickOrigin.setToolTipText("Rétablir les dimensions et proportions d'origine de la session");
        btnQuickOrigin.addActionListener(e -> applyOriginalFormatDirect(currentScreenWidth, currentScreenHeight));
        topFormatBar.add(btnQuickOrigin);

        add(topFormatBar, BorderLayout.NORTH);

        // ==========================================
        // ONGLET 2 : MONTAGE VIDÉO + BANDEAU
        // ==========================================
        JPanel tabMontagePanel = createMontageTabPanel();

        tabbedPane.addTab("📦 Bandeau Seul (Format d'origine OmeRyth)", tabBandeauPanel);
        tabbedPane.addTab("🎬 Montage Vidéo + Bande (Format d'origine OmeRyth)", tabMontagePanel);
        tabbedPane.setSelectedIndex(0); // Sélectionne le format d'origine de base par défaut

        add(tabbedPane, BorderLayout.CENTER);

        // Populate Presets pour l'onglet classique
        populatePresets(currentScreenWidth, currentScreenHeight);

        comboPreset.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED && !updatingPreset) {
                ExportPreset selected = (ExportPreset) comboPreset.getSelectedItem();
                if (selected != null && selected != customPresetItem) {
                    applyPreset(selected);
                }
                updateButtonStates();
            }
        });

        spinnerWidth.addChangeListener(e -> onSpinnerChanged());
        spinnerHeight.addChangeListener(e -> onSpinnerChanged());
        spinnerVisibleSeconds.addChangeListener(e -> onSpinnerChanged());
        comboFps.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) onSpinnerChanged();
        });

        btnSavePreset.addActionListener(e -> saveCurrentAsPreset());
        btnDeletePreset.addActionListener(e -> deleteSelectedPreset());

        // ==========================================
        // BOUTONS GLOBAUX DU BAS (Annuler / Exporter)
        // ==========================================
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 15, 10, 15));

        JButton btnCancel = new JButton("<html><span style='color:#000000; font-weight:500;'>Annuler</span></html>");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnCancel.setForeground(Color.BLACK);
        btnCancel.addActionListener(e -> {
            config.approved = false;
            dispose();
        });

        JButton btnExport = new JButton("<html><span style='color:#000000; font-weight:normal; font-family:Segoe UI, sans-serif;'>Lancer l'exportation vidéo...</span></html>");
        btnExport.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnExport.setBackground(new Color(56, 189, 248)); // Sky Blue lumineux
        btnExport.setForeground(Color.BLACK); // Noir forcé
        btnExport.setOpaque(true);
        btnExport.addActionListener(e -> onExportConfirmed());

        buttonPanel.add(btnCancel);
        buttonPanel.add(btnExport);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(owner);
    }

    private JPanel createMontageTabPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new EmptyBorder(8, 12, 8, 12));

        // En-tête
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        headerPanel.setBackground(new Color(24, 24, 27));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel title = new JLabel("🎬 Atelier de Composition Vidéo & Bande Rythmo");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(Color.WHITE);

        JLabel sub = new JLabel("Déplacez et redimensionnez la Vidéo et la Bande directement à la souris sur la maquette ci-dessous.");
        sub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        sub.setForeground(new Color(161, 161, 170));

        headerPanel.add(title, BorderLayout.NORTH);
        headerPanel.add(sub, BorderLayout.SOUTH);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Canvas interactif au centre
        montageCanvas = new MontagePreviewCanvas();
        montageCanvas.setTimelinePanel(timelinePanel);
        montageCanvas.setBandCount(bandCount);
        montageCanvas.setVideoSnapshot(videoSnapshot);
        if (timelinePanel != null) {
            montageCanvas.setPreviewTime(timelinePanel.getCurrentTime());
        }
        panel.add(montageCanvas, BorderLayout.CENTER);

        // Panneau latéral droit : Contrôles & Inspecteur
        JPanel controlsPanel = new JPanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setPreferredSize(new Dimension(360, 480));
        controlsPanel.setBorder(new EmptyBorder(0, 8, 0, 0));

        // 1. Résolution d'export
        JPanel resPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        resPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "1. Format & Résolution d'Export",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11),
                new Color(212, 212, 216)
        ));

        comboMontageResolution = new JComboBox<>(new String[]{
                "1920 × 1080 (16:9 Paysage Full HD — YouTube, Cinéma)",
                "1080 × 1920 (9:16 Vertical — TikTok, Shorts, Reels)",
                "1280 × 720 (16:9 Paysage HD)",
                "1080 × 1080 (1:1 Carré — Instagram)",
                "Personnalisé..."
        });
        resPanel.add(comboMontageResolution);

        JPanel dimSpinners = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        dimSpinners.add(new JLabel("L :"));
        spinnerMontageWidth = new JSpinner(new SpinnerNumberModel(1920, 320, 7680, 10));
        dimSpinners.add(spinnerMontageWidth);
        dimSpinners.add(new JLabel("H :"));
        spinnerMontageHeight = new JSpinner(new SpinnerNumberModel(1080, 240, 4320, 10));
        dimSpinners.add(spinnerMontageHeight);
        resPanel.add(dimSpinners);

        JPanel quickMontageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton btnMontageMobile = new JButton("<html><span style='color:#000000; font-weight:bold;'>📱 Format Mobile 9:16 (1080×1920)</span></html>");
        btnMontageMobile.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnMontageMobile.setBackground(new Color(245, 158, 11));
        btnMontageMobile.setForeground(Color.BLACK);
        btnMontageMobile.setOpaque(true);
        btnMontageMobile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMontageMobile.addActionListener(e -> applyMobileFormatDirect());
        quickMontageRow.add(btnMontageMobile);
        resPanel.add(quickMontageRow);

        controlsPanel.add(resPanel);
        controlsPanel.add(Box.createVerticalStrut(8));

        // 2. Modèles de disposition prédéfinis
        JPanel templatePanel = new JPanel(new GridLayout(0, 1, 4, 4));
        templatePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "2. Disposition Rapide (Modèles)",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11),
                new Color(212, 212, 216)
        ));

        comboMontageTemplate = new JComboBox<>(new String[]{
                "📐 Format d'origine OmeRyth (Vidéo en haut, Bandeau fin en bas comme à l'écran)",
                "📐 Plein écran (Vidéo 16:9 intégrale + Bandeau incrusté en bas)",
                "📐 Grand Bandeau Studio (Vidéo 75%, Bandeau 25%)",
                "📐 TikTok / Shorts (Vidéo 16:9 centrée + Bandeau dessous)",
                "📐 Égalitaire (50% Vidéo / 50% Bandeau)"
        });
        templatePanel.add(comboMontageTemplate);

        JButton btnApplyTemplate = new JButton("<html><span style='color:#000000;'>Appliquer la disposition</span></html>");
        btnApplyTemplate.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnApplyTemplate.setForeground(Color.BLACK);
        btnApplyTemplate.addActionListener(e -> applyCurrentTemplate());
        templatePanel.add(btnApplyTemplate);

        controlsPanel.add(templatePanel);
        controlsPanel.add(Box.createVerticalStrut(8));

        // 3. Inspecteur de l'élément sélectionné
        JPanel inspectorPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        inspectorPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "3. Position & Taille de l'Élément Sélectionné",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11),
                new Color(212, 212, 216)
        ));

        JPanel togglePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 2));
        btnSelectVideo = new JToggleButton("🎬 Vidéo source");
        btnSelectVideo.setForeground(Color.BLACK);
        btnSelectBand = new JToggleButton("🎵 Bande Rythmo");
        btnSelectBand.setForeground(Color.BLACK);
        btnSelectBand.setSelected(true);
        ButtonGroup group = new ButtonGroup();
        group.add(btnSelectVideo);
        group.add(btnSelectBand);

        btnSelectVideo.addActionListener(e -> montageCanvas.setSelectedElement(MontagePreviewCanvas.ElementType.VIDEO));
        btnSelectBand.addActionListener(e -> montageCanvas.setSelectedElement(MontagePreviewCanvas.ElementType.BAND));

        togglePanel.add(btnSelectVideo);
        togglePanel.add(btnSelectBand);
        inspectorPanel.add(togglePanel);

        JPanel coordPanel = new JPanel(new GridLayout(2, 4, 4, 4));
        coordPanel.add(new JLabel("X :", SwingConstants.RIGHT));
        spinnerElemX = new JSpinner(new SpinnerNumberModel(0, -2000, 7680, 10));
        coordPanel.add(spinnerElemX);
        coordPanel.add(new JLabel("Y :", SwingConstants.RIGHT));
        spinnerElemY = new JSpinner(new SpinnerNumberModel(0, -2000, 4320, 10));
        coordPanel.add(spinnerElemY);

        coordPanel.add(new JLabel("Larg :", SwingConstants.RIGHT));
        spinnerElemW = new JSpinner(new SpinnerNumberModel(1920, 30, 7680, 10));
        coordPanel.add(spinnerElemW);
        coordPanel.add(new JLabel("Haut :", SwingConstants.RIGHT));
        spinnerElemH = new JSpinner(new SpinnerNumberModel(300, 30, 4320, 10));
        coordPanel.add(spinnerElemH);
        inspectorPanel.add(coordPanel);

        JPanel alignPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 2));
        JButton btnCenterH = new JButton("<html><span style='color:#000000;'>↔ Centrer</span></html>");
        btnCenterH.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnCenterH.setForeground(Color.BLACK);
        btnCenterH.addActionListener(e -> montageCanvas.centerSelectedHorizontally());
        JButton btnCenterV = new JButton("<html><span style='color:#000000;'>↕ Centrer</span></html>");
        btnCenterV.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnCenterV.setForeground(Color.BLACK);
        btnCenterV.addActionListener(e -> montageCanvas.centerSelectedVertically());
        JButton btnFullW = new JButton("<html><span style='color:#000000;'>⬛ Pleine Largeur</span></html>");
        btnFullW.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnFullW.setForeground(Color.BLACK);
        btnFullW.addActionListener(e -> montageCanvas.setSelectedFullWidth());

        alignPanel.add(btnCenterH);
        alignPanel.add(btnCenterV);
        alignPanel.add(btnFullW);
        inspectorPanel.add(alignPanel);

        controlsPanel.add(inspectorPanel);
        controlsPanel.add(Box.createVerticalStrut(8));

        // 4. Options d'encodage et suppression vocale
        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 4, 4));
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "4. Audio, Doublage & Qualité",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 11),
                new Color(212, 212, 216)
        ));

        checkMontageRemoveVocals = new JCheckBox("🎤 Retirer les voix (IA Demucs - Conserver musique & ambiance)", false);
        checkMontageRemoveVocals.setFont(new Font("Segoe UI", Font.BOLD, 11));
        checkMontageRemoveVocals.setForeground(new Color(245, 158, 11));
        checkMontageRemoveVocals.setToolTipText("Supprime les dialogues via le réseau de neurones IA Demucs pour permettre aux comédiens de doubler par-dessus (conserve l'audio de base si décoché).");
        optionsPanel.add(checkMontageRemoveVocals);

        JPanel antiCopyrightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        checkAntiCopyright = new JCheckBox("🛡️ Filtre Anti-Copyright (Voile blanc) :", false);
        checkAntiCopyright.setFont(new Font("Segoe UI", Font.BOLD, 11));
        checkAntiCopyright.setForeground(new Color(56, 189, 248));
        checkAntiCopyright.setToolTipText("Applique un léger filtre blanc semi-transparent (de 0 à 100% d'opacité) sur la vidéo source pour contourner la détection automatique.");

        spinnerAntiCopyrightOpacity = new JSpinner(new SpinnerNumberModel(20, 0, 100, 5));
        spinnerAntiCopyrightOpacity.setPreferredSize(new Dimension(55, 22));
        spinnerAntiCopyrightOpacity.setEnabled(false);
        spinnerAntiCopyrightOpacity.setToolTipText("Opacité du filtre blanc (0% à 100%)");

        JLabel lblPercent = new JLabel("% d'opacité");
        lblPercent.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        antiCopyrightPanel.add(checkAntiCopyright);
        antiCopyrightPanel.add(spinnerAntiCopyrightOpacity);
        antiCopyrightPanel.add(lblPercent);

        Runnable updateAntiCopyright = () -> {
            boolean active = checkAntiCopyright.isSelected();
            spinnerAntiCopyrightOpacity.setEnabled(active);
            int op = ((Number) spinnerAntiCopyrightOpacity.getValue()).intValue();
            montageCanvas.setAntiCopyright(active, op);
        };

        checkAntiCopyright.addActionListener(e -> updateAntiCopyright.run());
        spinnerAntiCopyrightOpacity.addChangeListener(e -> updateAntiCopyright.run());

        optionsPanel.add(antiCopyrightPanel);

        JPanel optRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        optRow.add(new JLabel("FPS :"));
        comboMontageFps = new JComboBox<>(new String[]{"60 FPS (Fluide)", "30 FPS (Standard)", "24 FPS (Cinéma)"});
        comboMontageFps.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        optRow.add(comboMontageFps);

        optRow.add(new JLabel("Vision :"));
        spinnerMontageVisibleSeconds = new JSpinner(new SpinnerNumberModel(8.0, 1.5, 20.0, 0.5));
        optRow.add(spinnerMontageVisibleSeconds);
        optRow.add(new JLabel("s"));
        optionsPanel.add(optRow);

        JPanel encRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        encRow.add(new JLabel("Accélération :"));
        comboMontageEncoder = new JComboBox<>(new String[]{
                "🚀 Auto (GPU Détecté / Recommandé)",
                "⚡ NVIDIA NVENC (Ultra-Rapide)",
                "💻 CPU Multi-cœurs (x264 Rapide)"
        });
        comboMontageEncoder.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboMontageEncoder.setSelectedIndex(0);
        encRow.add(comboMontageEncoder);
        optionsPanel.add(encRow);

        controlsPanel.add(optionsPanel);
        panel.add(controlsPanel, BorderLayout.EAST);

        // Écouteurs pour synchroniser le canevas et les contrôles
        montageCanvas.setOnLayoutChanged(this::syncMontageSpinnersFromCanvas);

        comboMontageResolution.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                int idx = comboMontageResolution.getSelectedIndex();
                if (idx == 0) { // 1920x1080
                    spinnerMontageWidth.setValue(1920);
                    spinnerMontageHeight.setValue(1080);
                    montageCanvas.setExportResolution(1920, 1080);
                    montageCanvas.applyLayoutTemplate("CLASSIC_16_9");
                } else if (idx == 1) { // 1080x1920
                    spinnerMontageWidth.setValue(1080);
                    spinnerMontageHeight.setValue(1920);
                    montageCanvas.setExportResolution(1080, 1920);
                    montageCanvas.applyLayoutTemplate("TIKTOK_CENTER_9_16");
                } else if (idx == 2) { // 1280x720
                    spinnerMontageWidth.setValue(1280);
                    spinnerMontageHeight.setValue(720);
                    montageCanvas.setExportResolution(1280, 720);
                    montageCanvas.applyLayoutTemplate("CLASSIC_16_9");
                } else if (idx == 3) { // 1080x1080
                    spinnerMontageWidth.setValue(1080);
                    spinnerMontageHeight.setValue(1080);
                    montageCanvas.setExportResolution(1080, 1080);
                    montageCanvas.applyLayoutTemplate("STACKED_TOP_BOTTOM");
                }
            }
        });

        spinnerMontageWidth.addChangeListener(e -> {
            int w = (Integer) spinnerMontageWidth.getValue();
            int h = (Integer) spinnerMontageHeight.getValue();
            montageCanvas.setExportResolution(w, h);
        });

        spinnerMontageHeight.addChangeListener(e -> {
            int w = (Integer) spinnerMontageWidth.getValue();
            int h = (Integer) spinnerMontageHeight.getValue();
            montageCanvas.setExportResolution(w, h);
        });

        spinnerElemX.addChangeListener(e -> onElementSpinnerChanged());
        spinnerElemY.addChangeListener(e -> onElementSpinnerChanged());
        spinnerElemW.addChangeListener(e -> onElementSpinnerChanged());
        spinnerElemH.addChangeListener(e -> onElementSpinnerChanged());

        spinnerMontageVisibleSeconds.addChangeListener(e -> {
            double sec = ((Number) spinnerMontageVisibleSeconds.getValue()).doubleValue();
            montageCanvas.setVisibleSeconds(sec);
        });

        syncMontageSpinnersFromCanvas();
        return panel;
    }

    private void applyCurrentTemplate() {
        int idx = comboMontageTemplate.getSelectedIndex();
        switch (idx) {
            case 0 -> montageCanvas.applyLayoutTemplate("OMERYTH_ORIGINAL");
            case 1 -> montageCanvas.applyLayoutTemplate("OVERLAY_BOTTOM");
            case 2 -> montageCanvas.applyLayoutTemplate("LARGE_BAND_25");
            case 3 -> montageCanvas.applyLayoutTemplate("TIKTOK_CENTER_9_16");
            case 4 -> montageCanvas.applyLayoutTemplate("STACKED_TOP_BOTTOM");
        }
    }

    private void syncMontageSpinnersFromCanvas() {
        updatingMontageSpinners = true;
        MontagePreviewCanvas.ElementType sel = montageCanvas.getSelectedElement();
        if (sel == MontagePreviewCanvas.ElementType.VIDEO) {
            btnSelectVideo.setSelected(true);
            Rectangle r = montageCanvas.getVideoRect();
            spinnerElemX.setValue(r.x);
            spinnerElemY.setValue(r.y);
            spinnerElemW.setValue(r.width);
            spinnerElemH.setValue(r.height);
        } else {
            btnSelectBand.setSelected(true);
            Rectangle r = montageCanvas.getBandRect();
            spinnerElemX.setValue(r.x);
            spinnerElemY.setValue(r.y);
            spinnerElemW.setValue(r.width);
            spinnerElemH.setValue(r.height);
        }
        updatingMontageSpinners = false;
    }

    private void onElementSpinnerChanged() {
        if (updatingMontageSpinners) return;
        int x = (Integer) spinnerElemX.getValue();
        int y = (Integer) spinnerElemY.getValue();
        int w = (Integer) spinnerElemW.getValue();
        int h = (Integer) spinnerElemH.getValue();

        if (btnSelectVideo.isSelected()) {
            montageCanvas.setVideoRect(x, y, w, h);
        } else {
            montageCanvas.setBandRect(x, y, w, h);
        }
    }

    private void onExportConfirmed() {
        boolean isMontage = (tabbedPane.getSelectedIndex() == 1);
        config.isMontageMode = isMontage;

        if (isMontage) {
            int w = (Integer) spinnerMontageWidth.getValue();
            int h = (Integer) spinnerMontageHeight.getValue();
            if (w % 2 != 0) w++;
            if (h % 2 != 0) h++;

            config.width = w;
            config.height = h;

            Rectangle vr = montageCanvas.getVideoRect();
            Rectangle br = montageCanvas.getBandRect();
            if (vr.width % 2 != 0) vr.width++;
            if (vr.height % 2 != 0) vr.height++;
            if (br.width % 2 != 0) br.width++;
            if (br.height % 2 != 0) br.height++;

            config.videoRect = vr;
            config.bandRect = br;
            config.visibleSeconds = ((Number) spinnerMontageVisibleSeconds.getValue()).doubleValue();
            config.fps = switch (comboMontageFps.getSelectedIndex()) {
                case 1 -> 30;
                case 2 -> 24;
                default -> 60;
            };
            config.includeAudio = true;
            config.removeVocals = checkMontageRemoveVocals.isSelected();
            config.antiCopyright = (checkAntiCopyright != null && checkAntiCopyright.isSelected());
            config.antiCopyrightOpacity = (spinnerAntiCopyrightOpacity != null)
                    ? ((Number) spinnerAntiCopyrightOpacity.getValue()).intValue()
                    : 20;
            config.encoder = switch (comboMontageEncoder.getSelectedIndex()) {
                case 1 -> "nvenc";
                case 2 -> "cpu";
                default -> "auto";
            };
        } else {
            int w = (Integer) spinnerWidth.getValue();
            int h = (Integer) spinnerHeight.getValue();
            if (w % 2 != 0) w++;
            if (h % 2 != 0) h++;

            config.width = w;
            config.height = h;
            config.visibleSeconds = ((Number) spinnerVisibleSeconds.getValue()).doubleValue();
            config.fps = getSelectedFps();
            config.includeAudio = true;
            config.removeVocals = false;
            config.antiCopyright = false;
            config.antiCopyrightOpacity = 0;
            config.encoder = switch (comboEncoder.getSelectedIndex()) {
                case 1 -> "nvenc";
                case 2 -> "cpu";
                default -> "auto";
            };
        }

        config.approved = true;
        dispose();
    }

    private void populatePresets(int currentScreenWidth, int currentScreenHeight) {
        updatingPreset = true;
        presetModel.removeAllElements();

        int origW = currentScreenWidth > 0 ? currentScreenWidth : 1920;
        if (origW % 2 != 0) origW++;
        int origH = computeExportBandHeight(bandCount, (currentScreenHeight > 0 && currentScreenHeight <= 300) ? currentScreenHeight : 100);
        if (origH % 2 != 0) origH++;

        ExportPreset defaultPreset = new ExportPreset(
                "🎯 Format adapté OmeRyth (" + origW + " × " + origH + " — " + bandCount + " bande" + (bandCount > 1 ? "s" : "") + ")",
                origW, origH, 8.0, 60, true
        );
        presetModel.addElement(defaultPreset);

        int stdH = computeExportBandHeight(bandCount, 100);
        presetModel.addElement(new ExportPreset("⚡ Bandeau Standard (1920 × " + stdH + " — Vision 8s)", 1920, stdH, 8.0, 60, true));
        presetModel.addElement(new ExportPreset("📱 Format Mobile Plein Écran (1080 × 1920 — 9:16)", 1080, 1920, 6.0, 60, true));
        int mobH = computeExportBandHeight(bandCount, 180);
        presetModel.addElement(new ExportPreset("📱 Bandeau Mobile Réseaux (1080 × " + mobH + " — Vision 6s)", 1080, mobH, 6.0, 60, true));
        int stuH = computeExportBandHeight(bandCount, 200);
        presetModel.addElement(new ExportPreset("🌟 Grand Bandeau Studio (1920 × " + stuH + " — Vision 6s)", 1920, stuH, 6.0, 60, true));
        presetModel.addElement(new ExportPreset("🎬 Full HD 1080p Plein écran (1920 × 1080 — Vision 8s)", 1920, 1080, 8.0, 60, true));
        presetModel.addElement(new ExportPreset("📺 HD 720p (1280 × 720 — Vision 6s)", 1280, 720, 6.0, 60, true));

        for (ExportPreset custom : loadCustomPresetsFromFile()) {
            presetModel.addElement(custom);
        }

        presetModel.addElement(customPresetItem);
        comboPreset.setSelectedIndex(0);
        updatingPreset = false;

        applyPreset(defaultPreset);
        updateButtonStates();
    }

    private void applyPreset(ExportPreset p) {
        updatingPreset = true;
        spinnerWidth.setValue(p.width);
        spinnerHeight.setValue(p.height);
        spinnerVisibleSeconds.setValue(p.visibleSeconds);
        comboFps.setSelectedIndex(p.fps == 24 ? 2 : (p.fps == 30 ? 1 : 0));
        updatingPreset = false;
        if (singleBandPreview != null) {
            singleBandPreview.updateParams(p.width, p.height, p.visibleSeconds);
        }
    }

    public void applyMobileFormatDirect() {
        if (tabbedPane.getSelectedIndex() == 0) {
            spinnerWidth.setValue(1080);
            spinnerHeight.setValue(1920);
            spinnerVisibleSeconds.setValue(6.0);
            comboFps.setSelectedIndex(0);
            onSpinnerChanged();
        } else {
            comboMontageResolution.setSelectedIndex(1);
            comboMontageTemplate.setSelectedIndex(3);
            spinnerMontageWidth.setValue(1080);
            spinnerMontageHeight.setValue(1920);
            montageCanvas.setExportResolution(1080, 1920);
            montageCanvas.applyLayoutTemplate("TIKTOK_CENTER_9_16");
            syncMontageSpinnersFromCanvas();
        }
    }

    public void applyStandard1080pFormatDirect() {
        if (tabbedPane.getSelectedIndex() == 0) {
            spinnerWidth.setValue(1920);
            spinnerHeight.setValue(1080);
            spinnerVisibleSeconds.setValue(8.0);
            comboFps.setSelectedIndex(0);
            onSpinnerChanged();
        } else {
            comboMontageResolution.setSelectedIndex(0);
            comboMontageTemplate.setSelectedIndex(0);
            spinnerMontageWidth.setValue(1920);
            spinnerMontageHeight.setValue(1080);
            montageCanvas.setExportResolution(1920, 1080);
            montageCanvas.applyLayoutTemplate("OMERYTH_ORIGINAL");
            syncMontageSpinnersFromCanvas();
        }
    }

    public void applyOriginalFormatDirect(int currentScreenWidth, int currentScreenHeight) {
        int origW = currentScreenWidth > 0 ? currentScreenWidth : 1920;
        if (origW % 2 != 0) origW++;
        int origH = computeExportBandHeight(bandCount, (currentScreenHeight > 0 && currentScreenHeight <= 300) ? currentScreenHeight : 100);
        if (origH % 2 != 0) origH++;

        if (tabbedPane.getSelectedIndex() == 0) {
            comboPreset.setSelectedIndex(0);
        } else {
            comboMontageResolution.setSelectedIndex(0);
            comboMontageTemplate.setSelectedIndex(0);
            spinnerMontageWidth.setValue(1920);
            spinnerMontageHeight.setValue(1080);
            montageCanvas.setExportResolution(1920, 1080);
            montageCanvas.applyLayoutTemplate("OMERYTH_ORIGINAL");
            syncMontageSpinnersFromCanvas();
        }
    }

    private void onSpinnerChanged() {
        if (!updatingPreset) {
            updatingPreset = true;
            comboPreset.setSelectedItem(customPresetItem);
            updatingPreset = false;
            updateButtonStates();
        }
        if (singleBandPreview != null) {
            int w = (Integer) spinnerWidth.getValue();
            int h = (Integer) spinnerHeight.getValue();
            double sec = ((Number) spinnerVisibleSeconds.getValue()).doubleValue();
            singleBandPreview.updateParams(w, h, sec);
        }
    }

    private void updateButtonStates() {
        ExportPreset sel = (ExportPreset) comboPreset.getSelectedItem();
        boolean isCustomUserPreset = (sel != null && !sel.isBuiltin && sel != customPresetItem);
        btnDeletePreset.setEnabled(isCustomUserPreset);
    }

    private int getSelectedFps() {
        return switch (comboFps.getSelectedIndex()) {
            case 1 -> 30;
            case 2 -> 24;
            default -> 60;
        };
    }

    private void saveCurrentAsPreset() {
        int w = (Integer) spinnerWidth.getValue();
        int h = (Integer) spinnerHeight.getValue();
        double sec = ((Number) spinnerVisibleSeconds.getValue()).doubleValue();
        int fps = getSelectedFps();

        String defaultName = w + "x" + h + " (" + sec + "s, " + fps + "fps)";
        String name = (String) JOptionPane.showInputDialog(
                this,
                "Entrez un nom pour votre configuration d'export :",
                "Enregistrer un nouveau préréglage",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                defaultName
        );

        if (name == null || name.trim().isEmpty()) {
            return;
        }

        String finalName = "⭐ " + name.trim();
        ExportPreset newPreset = new ExportPreset(finalName, w, h, sec, fps, false);

        int insertIdx = presetModel.getSize() - 1;
        presetModel.insertElementAt(newPreset, Math.max(0, insertIdx));
        saveAllCustomPresetsToFile();

        updatingPreset = true;
        comboPreset.setSelectedItem(newPreset);
        updatingPreset = false;
        updateButtonStates();

        JOptionPane.showMessageDialog(this,
                "Préréglage \"" + finalName + "\" enregistré avec succès !",
                "Préréglage Enregistré",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void deleteSelectedPreset() {
        ExportPreset sel = (ExportPreset) comboPreset.getSelectedItem();
        if (sel == null || sel.isBuiltin || sel == customPresetItem) return;

        int response = JOptionPane.showConfirmDialog(
                this,
                "Voulez-vous vraiment supprimer le préréglage \"" + sel.name + "\" ?",
                "Supprimer le préréglage",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            presetModel.removeElement(sel);
            saveAllCustomPresetsToFile();
            comboPreset.setSelectedIndex(0);
            updateButtonStates();
        }
    }

    private ArrayList<ExportPreset> loadCustomPresetsFromFile() {
        ArrayList<ExportPreset> list = new ArrayList<>();
        File file = new File(PRESETS_FILE);
        if (!file.exists()) return list;

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("preset.")) {
                    String raw = props.getProperty(key, "");
                    String[] parts = raw.split("\\|", -1);
                    if (parts.length >= 5) {
                        String name = parts[0];
                        int w = Integer.parseInt(parts[1]);
                        int h = Integer.parseInt(parts[2]);
                        double sec = Double.parseDouble(parts[3]);
                        int fps = Integer.parseInt(parts[4]);
                        list.add(new ExportPreset(name, w, h, sec, fps, false));
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Failed to load custom export presets: " + ex.getMessage());
        }
        return list;
    }

    private void saveAllCustomPresetsToFile() {
        Properties props = new Properties();
        int count = 0;
        for (int i = 0; i < presetModel.getSize(); i++) {
            ExportPreset p = presetModel.getElementAt(i);
            if (p != null && !p.isBuiltin && p != customPresetItem) {
                String val = p.name + "|" + p.width + "|" + p.height + "|" + p.visibleSeconds + "|" + p.fps;
                props.setProperty("preset." + (count++), val);
            }
        }

        File file = new File(PRESETS_FILE);
        try (OutputStream out = new FileOutputStream(file);
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            props.store(writer, "OmeRyth Custom Video Export Presets");
        } catch (Exception ex) {
            System.err.println("Failed to save custom export presets: " + ex.getMessage());
        }
    }

    public ExportConfig getExportConfig() {
        return config;
    }

    /**
     * Panneau d'aperçu graphique réel de la bande rythmo pour l'onglet "Bandeau Seul".
     * Affiche fidèlement la vraie bande du projet (textes, rôles, séparateurs, repère de lecture rouge)
     * au lieu d'une simple simulation textuelle.
     */
    public static class SingleBandPreviewPanel extends JPanel {
        private final TimelinePanel timelinePanel;
        private int bandCount = 1;
        private int targetW = 1920;
        private int targetH = 100;
        private double visibleSeconds = 8.0;
        private double previewTime = 0.0;
        private BufferedImage cachedImage = null;
        private int cachedW = -1;
        private int cachedH = -1;
        private double cachedSec = -1;
        private double cachedTime = -1;

        private final JSlider timeSlider;
        private final JLabel timeLabel;

        public SingleBandPreviewPanel(TimelinePanel timelinePanel, int bandCount, int initialW, int initialH, double initialSec) {
            this.timelinePanel = timelinePanel;
            this.bandCount = Math.max(1, bandCount);
            this.targetW = Math.max(100, initialW);
            this.targetH = Math.max(20, initialH);
            this.visibleSeconds = Math.max(1.0, initialSec);
            if (timelinePanel != null) {
                this.previewTime = timelinePanel.getCurrentTime();
            }

            setLayout(new BorderLayout(4, 4));
            setBackground(new Color(24, 24, 27));
            setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(63, 63, 70)),
                    "👁️ Aperçu Graphique Réel de la Bande Rythmo",
                    TitledBorder.LEFT, TitledBorder.TOP,
                    new Font("Segoe UI", Font.BOLD, 11),
                    new Color(245, 158, 11) // Gold
            ));
            setPreferredSize(new Dimension(800, 180));
            setMinimumSize(new Dimension(400, 140));

            // Canevas de rendu au centre
            JPanel canvas = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                    int availW = getWidth() - 16;
                    int availH = getHeight() - 16;
                    if (availW <= 10 || availH <= 10) {
                        g2.dispose();
                        return;
                    }

                    // Calcul de l'échelle pour afficher la bande avec ses vraies proportions
                    double scaleX = (double) availW / targetW;
                    double scaleY = (double) availH / targetH;
                    double scale = Math.min(scaleX, scaleY);
                    int drawW = Math.max(20, (int) Math.round(targetW * scale));
                    int drawH = Math.max(10, (int) Math.round(targetH * scale));
                    int drawX = (getWidth() - drawW) / 2;
                    int drawY = (getHeight() - drawH) / 2;

                    // Ombre portée / Fond du conteneur
                    g2.setColor(new Color(15, 15, 18));
                    g2.fillRoundRect(drawX - 2, drawY - 2, drawW + 4, drawH + 4, 6, 6);

                    // Rendu ou cache de la bande réelle
                    boolean drawnReal = false;
                    if (timelinePanel != null && targetW > 20 && targetH > 10) {
                        if (cachedImage == null || cachedW != targetW || cachedH != targetH ||
                                Math.abs(cachedSec - visibleSeconds) > 0.05 || Math.abs(cachedTime - previewTime) > 0.02) {
                            cachedW = targetW;
                            cachedH = targetH;
                            cachedSec = visibleSeconds;
                            cachedTime = previewTime;
                            try {
                                cachedImage = timelinePanel.renderFrame(targetW, targetH, previewTime, visibleSeconds);
                            } catch (Exception ex) {
                                cachedImage = null;
                            }
                        }
                        if (cachedImage != null) {
                            g2.drawImage(cachedImage, drawX, drawY, drawW, drawH, null);
                            drawnReal = true;
                        }
                    }

                    if (!drawnReal) {
                        // Rendu de secours épuré si timelinePanel non connecté
                        g2.setColor(new Color(28, 25, 23));
                        g2.fillRect(drawX, drawY, drawW, drawH);
                        // Lignes de séparation de pistes
                        if (bandCount > 1) {
                            g2.setColor(new Color(60, 60, 65));
                            for (int b = 1; b < bandCount; b++) {
                                int by = drawY + (drawH * b) / bandCount;
                                g2.drawLine(drawX, by, drawX + drawW, by);
                            }
                        }
                        // Curseur rouge
                        int cx = drawX + drawW / 2;
                        g2.setColor(new Color(239, 68, 68, 220));
                        g2.setStroke(new BasicStroke(2.0f));
                        g2.drawLine(cx, drawY, cx, drawY + drawH);
                    }

                    // Bordure or / dorée stylisée
                    g2.setColor(new Color(245, 158, 11, 200));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(drawX, drawY, drawW, drawH, 2, 2);

                    // Badge info en haut à gauche
                    String info = targetW + " × " + targetH + " px (" + bandCount + " bande" + (bandCount > 1 ? "s" : "") + ") — Vision : " + visibleSeconds + "s";
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    FontMetrics fm = g2.getFontMetrics();
                    int tw = fm.stringWidth(info);
                    g2.setColor(new Color(15, 15, 18, 210));
                    g2.fillRoundRect(drawX + 4, drawY + 4, tw + 8, 16, 4, 4);
                    g2.setColor(new Color(245, 158, 11));
                    g2.drawString(info, drawX + 8, drawY + 16);

                    g2.dispose();
                }
            };
            canvas.setBackground(new Color(20, 20, 24));
            add(canvas, BorderLayout.CENTER);

            // Barre de scrubbing temporel en bas
            JPanel scrubPanel = new JPanel(new BorderLayout(6, 0));
            scrubPanel.setOpaque(false);
            scrubPanel.setBorder(new EmptyBorder(2, 6, 2, 6));

            JLabel lblScrub = new JLabel("Curseur temps :");
            lblScrub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            lblScrub.setForeground(new Color(180, 180, 190));
            scrubPanel.add(lblScrub, BorderLayout.WEST);

            int maxSec = 120;
            if (timelinePanel != null) {
                maxSec = Math.max(30, (int) Math.ceil(timelinePanel.getCurrentTime() + 60.0));
            }
            timeSlider = new JSlider(0, maxSec * 10, (int) (previewTime * 10));
            timeSlider.setOpaque(false);
            timeSlider.addChangeListener(e -> {
                double t = timeSlider.getValue() / 10.0;
                setPreviewTime(t);
            });
            scrubPanel.add(timeSlider, BorderLayout.CENTER);

            timeLabel = new JLabel(formatTime(previewTime));
            timeLabel.setFont(new Font("Consolas", Font.BOLD, 11));
            timeLabel.setForeground(new Color(245, 158, 11));
            scrubPanel.add(timeLabel, BorderLayout.EAST);

            add(scrubPanel, BorderLayout.SOUTH);
        }

        private static String formatTime(double sec) {
            int mins = (int) (sec / 60);
            double rem = sec - mins * 60;
            return String.format("%02d:%05.2f", mins, rem);
        }

        public void updateParams(int w, int h, double visibleSec) {
            this.targetW = Math.max(100, w);
            this.targetH = Math.max(20, h);
            this.visibleSeconds = Math.max(1.0, visibleSec);
            this.cachedImage = null;
            repaint();
        }

        public void setBandCount(int count) {
            this.bandCount = Math.max(1, count);
            this.cachedImage = null;
            repaint();
        }

        public void setPreviewTime(double time) {
            this.previewTime = Math.max(0.0, time);
            this.timeLabel.setText(formatTime(previewTime));
            this.cachedImage = null;
            repaint();
        }
    }
}
