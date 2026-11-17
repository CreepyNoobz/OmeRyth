package app.ui;

import app.utils.FileUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.Arrays;

public class CustomizationWindow extends JDialog {

    public interface ApplyHandler {
        void onApply(AppCustomization customization);
    }

    private final AppCustomization customization;

    private JSpinner bandCount, bandHeight, cursorX, timerWidth, timerFontSize;
    private JCheckBox autoResizeTimerFont, showWaveform;
    private JComboBox<String> defaultProjectFormat, appLanguage, keyboardLayout;
    private ColorPreviewButton timerBgBtn, timerTextBtn, historyBgBtn, mediaBgBtn, evenBandBtn, oddBandBtn;
    private ColorPreviewButton selectedBandBtn, gridBtn, cursorBtn, sepBtn, waveformColorBtn;
    private JTextField timerImagePath, historyImagePath, mediaImagePath, globalBandImagePath;
    private JComboBox<String> bandBgMode, timelineFontFamily;
    private JTextArea perBandImages;

    private JPanel advancedPanel;

    public CustomizationWindow(JFrame parent, AppCustomization customization, ApplyHandler handler) {
        super(parent, "en".equalsIgnoreCase(customization.appLanguage) ? "Settings" : "Paramètres", true);
        this.customization = customization;
        boolean isEn = "en".equalsIgnoreCase(customization.appLanguage);

        setSize(820, 780);
        setMinimumSize(new Dimension(560, 460));
        setResizable(true);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        // En-tête éditorial OmeRyth
        JPanel headerPanel = OmeTheme.createDialogHeader(
                isEn ? "PREFERENCES" : "PRÉFÉRENCES",
                isEn ? "OmeRyth Settings" : "Paramètres OmeRyth",
                isEn ? "Customize workspace appearance, rythmo band geometry and audio preferences."
                     : "Personnalisez l'apparence du studio, la géométrie de la bande et le comportement."
        );
        add(headerPanel, BorderLayout.NORTH);

        // Conteneur principal
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(OmeTheme.bg());
        mainContent.setBorder(new EmptyBorder(18, 20, 18, 20));

        // Initialisation des champs
        initFields();

        // Barre rapide Thème OmeRyth (Sombre / Clair)
        JPanel themePresetBar = new JPanel(new BorderLayout(12, 0));
        themePresetBar.setOpaque(false);
        JLabel themePresetInfo = new JLabel(isEn ? "Apply the official OmeRyth Web palette:" : "Appliquer la palette officielle OmeRyth Web :");
        themePresetInfo.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
        themePresetInfo.setForeground(OmeTheme.textSecondary());

        JPanel themeBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        themeBtnGroup.setOpaque(false);
        OmeTheme.OmeButton darkPresetBtn = OmeTheme.createSecondaryButton(isEn ? "Dark Studio" : "Studio Sombre");
        darkPresetBtn.setMargin(new Insets(6, 12, 6, 12));
        darkPresetBtn.addActionListener(e -> applyPalettePreset(true));
        OmeTheme.OmeButton lightPresetBtn = OmeTheme.createSecondaryButton(isEn ? "Light Studio" : "Studio Clair");
        lightPresetBtn.setMargin(new Insets(6, 12, 6, 12));
        lightPresetBtn.addActionListener(e -> applyPalettePreset(false));
        themeBtnGroup.add(darkPresetBtn);
        themeBtnGroup.add(lightPresetBtn);
        themePresetBar.add(themePresetInfo, BorderLayout.CENTER);
        themePresetBar.add(themeBtnGroup, BorderLayout.EAST);

        mainContent.add(OmeTheme.createSectionCard("00", isEn ? "Studio Theme Preset" : "Thème Rapide", themePresetBar));
        mainContent.add(Box.createVerticalStrut(14));

        // 1. Paramètres basiques
        JPanel basicPanel = new JPanel(new GridBagLayout());
        basicPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(7, 8, 7, 8);
        c.weightx = 1;

        int row = 0;
        addRow(basicPanel, c, row++, isEn ? "Language & spell check:" : "Langue & correction :", appLanguage);
        addRow(basicPanel, c, row++, isEn ? "Default project format:" : "Format de projet par défaut :", defaultProjectFormat);
        addRow(basicPanel, c, row++, isEn ? "Keyboard layout:" : "Disposition du clavier :", keyboardLayout);
        addRow(basicPanel, c, row++, isEn ? "Number of bands (Lines):" : "Nombre de bandes (Lignes) :", bandCount);
        addRow(basicPanel, c, row++, isEn ? "Band height (px):" : "Hauteur des bandes (px) :", bandHeight);
        addRow(basicPanel, c, row++, isEn ? "Time cursor position (px):" : "Position curseur (px) :", cursorX);
        addRow(basicPanel, c, row++, isEn ? "Timer panel width (px):" : "Largeur zone timer (px) :", timerWidth);
        
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1;
        basicPanel.add(autoResizeTimerFont, c);
        row++;
        
        c.gridwidth = 1; c.weightx = 0;
        addRow(basicPanel, c, row++, isEn ? "Timer font size (manual):" : "Police timer (manuel) :", timerFontSize);

        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1;
        basicPanel.add(showWaveform, c);
        row++;

        mainContent.add(OmeTheme.createSectionCard("01", isEn ? "General Settings" : "Réglages Généraux", basicPanel));
        mainContent.add(Box.createVerticalStrut(14));

        // -- Section : Couleurs
        JPanel colorsPanel = new JPanel(new GridLayout(0, 2, 16, 12));
        colorsPanel.setOpaque(false);
        colorsPanel.add(createColorRow(isEn ? "Timer background:" : "Fond zone timer :", timerBgBtn));
        colorsPanel.add(createColorRow(isEn ? "Timer text:" : "Texte zone timer :", timerTextBtn));
        colorsPanel.add(createColorRow(isEn ? "History background:" : "Fond historique :", historyBgBtn));
        colorsPanel.add(createColorRow(isEn ? "Media background:" : "Fond zone média :", mediaBgBtn));
        colorsPanel.add(createColorRow(isEn ? "Even band:" : "Bande paire :", evenBandBtn));
        colorsPanel.add(createColorRow(isEn ? "Odd band:" : "Bande impaire :", oddBandBtn));
        colorsPanel.add(createColorRow(isEn ? "Selected band:" : "Bande sélectionnée :", selectedBandBtn));
        colorsPanel.add(createColorRow(isEn ? "Timeline grid:" : "Grille temporelle :", gridBtn));
        colorsPanel.add(createColorRow(isEn ? "Time cursor:" : "Curseur temporel :", cursorBtn));
        colorsPanel.add(createColorRow(isEn ? "Separators:" : "Séparateurs :", sepBtn));
        colorsPanel.add(createColorRow(isEn ? "Voice waveform:" : "Forme d'onde vocale :", waveformColorBtn));

        mainContent.add(OmeTheme.createSectionCard("02", isEn ? "Colors & Palette" : "Couleurs & Palette", colorsPanel));
        mainContent.add(Box.createVerticalStrut(14));

        // -- Section : Images et Police
        JPanel imagesPanel = new JPanel(new GridBagLayout());
        imagesPanel.setOpaque(false);
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(7, 6, 7, 6);
        c.weightx = 1;
        row = 0;
        
        addRow(imagesPanel, c, row++, isEn ? "Timeline text font:" : "Police texte timeline :", timelineFontFamily);
        addPathRow(imagesPanel, c, row++, isEn ? "Timer background image:" : "Image fond timer :", timerImagePath, createBrowseBtn(isEn ? "timer" : "timer", timerImagePath));
        addPathRow(imagesPanel, c, row++, isEn ? "History background image:" : "Image fond historique :", historyImagePath, createBrowseBtn(isEn ? "history" : "historique", historyImagePath));
        addPathRow(imagesPanel, c, row++, isEn ? "Media background image:" : "Image fond média :", mediaImagePath, createBrowseBtn(isEn ? "media" : "media", mediaImagePath));
        addRow(imagesPanel, c, row++, isEn ? "Band image mode:" : "Mode image bandes :", bandBgMode);
        addPathRow(imagesPanel, c, row++, isEn ? "Band image (Single):" : "Image bandes (Unique) :", globalBandImagePath, createBrowseBtn(isEn ? "bands" : "bandes", globalBandImagePath));
        
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel perBandLbl = new JLabel(isEn ? "Separate images (band0;band1):" : "Images séparées (bande0;bande1) :");
        perBandLbl.setForeground(OmeTheme.textSecondary());
        perBandLbl.setFont(OmeTheme.fontSans(Font.PLAIN, 12.5f));
        imagesPanel.add(perBandLbl, c);
        c.gridx = 1; c.weightx = 1;
        JScrollPane perBandScroll = new JScrollPane(perBandImages);
        OmeTheme.styleScrollPane(perBandScroll);
        imagesPanel.add(perBandScroll, c);

        mainContent.add(OmeTheme.createSectionCard("03", isEn ? "Background Images & Fonts" : "Images de fond & Polices", imagesPanel));

        JScrollPane scroll = new JScrollPane(mainContent);
        OmeTheme.styleBorderlessScrollPane(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // Actions (Boutons du bas)
        OmeTheme.OmeButton reset = OmeTheme.createSecondaryButton(isEn ? "Restore defaults" : "Rétablir par défaut");
        OmeTheme.OmeButton close = OmeTheme.createGhostButton(isEn ? "Cancel" : "Annuler");
        OmeTheme.OmeButton apply = OmeTheme.createPrimaryButton(isEn ? "Apply" : "Appliquer");

        add(OmeTheme.createDialogFooter(reset, close, apply), BorderLayout.SOUTH);

        // Listeners
        reset.addActionListener(e -> resetDefaults());
        apply.addActionListener(e -> applyChanges(handler));
        close.addActionListener(e -> dispose());
    }

    private void initFields() {
        bandCount = new JSpinner(new SpinnerNumberModel(customization.bandCount, 1, 12, 1));
        bandHeight = new JSpinner(new SpinnerNumberModel(customization.bandHeight, 20, 300, 5));
        cursorX = new JSpinner(new SpinnerNumberModel(customization.timelineCursorX, 20, 600, 5));
        timerWidth = new JSpinner(new SpinnerNumberModel(customization.timerPanelWidth, 100, 500, 10));
        timerFontSize = new JSpinner(new SpinnerNumberModel(customization.timerFontSize, 10, 200, 2));
        OmeTheme.styleSpinner(bandCount);
        OmeTheme.styleSpinner(bandHeight);
        OmeTheme.styleSpinner(cursorX);
        OmeTheme.styleSpinner(timerWidth);
        OmeTheme.styleSpinner(timerFontSize);

        boolean isEn = "en".equalsIgnoreCase(customization.appLanguage);
        autoResizeTimerFont = new JCheckBox(isEn ? "Manually set timer font size" : "Définir manuellement la taille de la police du timer");
        autoResizeTimerFont.setSelected(!customization.autoResizeTimerFont);
        OmeTheme.styleCheckBox(autoResizeTimerFont);

        timerFontSize.setEnabled(autoResizeTimerFont.isSelected());
        autoResizeTimerFont.addActionListener(e -> timerFontSize.setEnabled(autoResizeTimerFont.isSelected()));

        showWaveform = new JCheckBox(isEn ? "Show audio waveform (vocal)" : "Afficher la forme d'onde audio (vocale)");
        showWaveform.setSelected(customization.showWaveform);
        OmeTheme.styleCheckBox(showWaveform);

        appLanguage = new JComboBox<>(new String[]{"Français", "English"});
        OmeTheme.styleComboBox(appLanguage);
        if (isEn) {
            appLanguage.setSelectedIndex(1);
        } else {
            appLanguage.setSelectedIndex(0);
        }

        keyboardLayout = new JComboBox<>(isEn ? new String[]{"Auto-detect", "AZERTY", "QWERTY"} : new String[]{"Automatique", "AZERTY", "QWERTY"});
        OmeTheme.styleComboBox(keyboardLayout);
        if ("azerty".equalsIgnoreCase(customization.keyboardLayout)) keyboardLayout.setSelectedIndex(1);
        else if ("qwerty".equalsIgnoreCase(customization.keyboardLayout)) keyboardLayout.setSelectedIndex(2);
        else keyboardLayout.setSelectedIndex(0);

        defaultProjectFormat = new JComboBox<>(new String[]{"rythmo (OmeRyth)", "detx (Cappella)"});
        OmeTheme.styleComboBox(defaultProjectFormat);
        if ("detx".equalsIgnoreCase(customization.defaultProjectFormat)) {
            defaultProjectFormat.setSelectedIndex(1);
        } else {
            defaultProjectFormat.setSelectedIndex(0);
        }

        timerBgBtn = createColorButton(customization.timerBackground);
        timerTextBtn = createColorButton(customization.timerTextColor);
        historyBgBtn = createColorButton(customization.historyBackground);
        mediaBgBtn = createColorButton(customization.mediaBackground);
        evenBandBtn = createColorButton(customization.timelineEvenBand);
        oddBandBtn = createColorButton(customization.timelineOddBand);
        selectedBandBtn = createColorButton(customization.timelineSelectedBand);
        gridBtn = createColorButton(customization.timelineGrid);
        cursorBtn = createColorButton(customization.timelineCursor);
        sepBtn = createColorButton(customization.timelineSeparator);
        waveformColorBtn = createColorButton(customization.waveformColor);

        timerImagePath = new JTextField(customization.timerImagePath);
        historyImagePath = new JTextField(customization.historyImagePath);
        mediaImagePath = new JTextField(customization.mediaImagePath);
        globalBandImagePath = new JTextField(customization.globalBandImagePath);
        OmeTheme.styleTextField(timerImagePath);
        OmeTheme.styleTextField(historyImagePath);
        OmeTheme.styleTextField(mediaImagePath);
        OmeTheme.styleTextField(globalBandImagePath);

        bandBgMode = new JComboBox<>(isEn ? new String[]{"Color", "Single image", "Images per band"} : new String[]{"Couleur", "Image unique", "Images par bande"});
        OmeTheme.styleComboBox(bandBgMode);
        if (AppCustomization.BAND_BG_IMAGE_GLOBAL.equals(customization.bandBackgroundMode)) bandBgMode.setSelectedIndex(1);
        else if (AppCustomization.BAND_BG_IMAGE_PER_BAND.equals(customization.bandBackgroundMode)) bandBgMode.setSelectedIndex(2);
        else bandBgMode.setSelectedIndex(0);

        String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        Arrays.sort(fontFamilies, String.CASE_INSENSITIVE_ORDER);
        timelineFontFamily = new JComboBox<>(fontFamilies);
        OmeTheme.styleComboBox(timelineFontFamily);
        timelineFontFamily.setSelectedItem(customization.timelineFontFamily);
        timelineFontFamily.setMaximumRowCount(15);

        perBandImages = new JTextArea(customization.perBandImagePaths, 3, 20);
        perBandImages.setLineWrap(true);
        perBandImages.setWrapStyleWord(true);
        OmeTheme.styleTextArea(perBandImages);
    }

    private void applyPalettePreset(boolean dark) {
        if (dark) {
            timerBgBtn.setPreviewColor(new Color(0x101216));
            timerTextBtn.setPreviewColor(new Color(0xECEEEF));
            historyBgBtn.setPreviewColor(new Color(0x101216));
            mediaBgBtn.setPreviewColor(new Color(0x0A0B0D));
            evenBandBtn.setPreviewColor(new Color(0x15171C));
            oddBandBtn.setPreviewColor(new Color(0x1B1E24));
            selectedBandBtn.setPreviewColor(new Color(0x14261B));
            gridBtn.setPreviewColor(new Color(255, 255, 255, 26));
            cursorBtn.setPreviewColor(new Color(0xFF453A));
            sepBtn.setPreviewColor(new Color(0x2FBE55));
            waveformColorBtn.setPreviewColor(new Color(47, 190, 85, 55));
        } else {
            timerBgBtn.setPreviewColor(new Color(0xFFFFFF));
            timerTextBtn.setPreviewColor(new Color(0x14161A));
            historyBgBtn.setPreviewColor(new Color(0xFFFFFF));
            mediaBgBtn.setPreviewColor(new Color(0xFAFAF8));
            evenBandBtn.setPreviewColor(new Color(0xF2F2F0));
            oddBandBtn.setPreviewColor(new Color(0xEBEBE8));
            selectedBandBtn.setPreviewColor(new Color(0xE0F2E5));
            gridBtn.setPreviewColor(new Color(10, 12, 15, 32));
            cursorBtn.setPreviewColor(new Color(0xD93025));
            sepBtn.setPreviewColor(new Color(0x219944));
            waveformColorBtn.setPreviewColor(new Color(33, 153, 68, 55));
        }
    }

    private JPanel createColorRow(String label, JComponent colorBtn) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        JLabel lbl = new JLabel(label);
        lbl.setForeground(OmeTheme.textSecondary());
        lbl.setFont(OmeTheme.fontSans(Font.PLAIN, 12.5f));
        p.add(lbl, BorderLayout.CENTER);
        p.add(colorBtn, BorderLayout.EAST);
        return p;
    }

    private JButton createBrowseBtn(String type, JTextField field) {
        boolean isEn = "en".equalsIgnoreCase(customization.appLanguage);
        OmeTheme.OmeButton btn = OmeTheme.createSecondaryButton("...");
        btn.setMargin(new Insets(5, 10, 5, 10));
        btn.setToolTipText(isEn ? "Browse" : "Parcourir");
        btn.addActionListener(e -> {
            File f = FileUtils.chooseOpenFile(this, (isEn ? "Choose image " : "Choisir image ") + type, "png", "jpg", "jpeg", "gif", "bmp", "webp");
            if (f != null) field.setText(f.getAbsolutePath());
        });
        return btn;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent value) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(OmeTheme.textSecondary());
        lbl.setFont(OmeTheme.fontSans(Font.PLAIN, 12.5f));
        lbl.setMinimumSize(lbl.getPreferredSize());
        form.add(lbl, c);
        c.gridx = 1; c.weightx = 1;
        form.add(value, c);
    }

    private void addPathRow(JPanel form, GridBagConstraints c, int row, String label, JTextField pathField, JButton browseBtn) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(OmeTheme.textSecondary());
        lbl.setFont(OmeTheme.fontSans(Font.PLAIN, 12.5f));
        lbl.setMinimumSize(lbl.getPreferredSize());
        form.add(lbl, c);
        JPanel panel = new JPanel(new BorderLayout(6, 0));
        panel.setOpaque(false);

        pathField.setColumns(10);
        pathField.setMinimumSize(new Dimension(50, 26));

        panel.add(pathField, BorderLayout.CENTER);
        panel.add(browseBtn, BorderLayout.EAST);
        c.gridx = 1; c.weightx = 1;
        form.add(panel, c);
    }

    private void resetDefaults() {
        AppCustomization def = new AppCustomization();
        bandCount.setValue(def.bandCount);
        bandHeight.setValue(def.bandHeight);
        cursorX.setValue(def.timelineCursorX);
        timerWidth.setValue(def.timerPanelWidth);
        timerFontSize.setValue(def.timerFontSize);
        autoResizeTimerFont.setSelected(!def.autoResizeTimerFont);
        timerFontSize.setEnabled(!def.autoResizeTimerFont);
        showWaveform.setSelected(def.showWaveform);
        defaultProjectFormat.setSelectedIndex("detx".equalsIgnoreCase(def.defaultProjectFormat) ? 1 : 0);
        appLanguage.setSelectedIndex("en".equalsIgnoreCase(def.appLanguage) ? 1 : 0);
        keyboardLayout.setSelectedIndex(0);
        timerBgBtn.setPreviewColor(def.timerBackground);
        timerTextBtn.setPreviewColor(def.timerTextColor);
        historyBgBtn.setPreviewColor(def.historyBackground);
        mediaBgBtn.setPreviewColor(def.mediaBackground);
        evenBandBtn.setPreviewColor(def.timelineEvenBand);
        oddBandBtn.setPreviewColor(def.timelineOddBand);
        selectedBandBtn.setPreviewColor(def.timelineSelectedBand);
        gridBtn.setPreviewColor(def.timelineGrid);
        cursorBtn.setPreviewColor(def.timelineCursor);
        sepBtn.setPreviewColor(def.timelineSeparator);
        waveformColorBtn.setPreviewColor(def.waveformColor);
        timerImagePath.setText(def.timerImagePath);
        historyImagePath.setText(def.historyImagePath);
        mediaImagePath.setText(def.mediaImagePath);
        bandBgMode.setSelectedIndex(0);
        globalBandImagePath.setText(def.globalBandImagePath);
        perBandImages.setText(def.perBandImagePaths);
        timelineFontFamily.setSelectedItem(def.timelineFontFamily);
    }

    private void applyChanges(ApplyHandler handler) {
        customization.bandCount = (Integer) bandCount.getValue();
        customization.bandHeight = (Integer) bandHeight.getValue();
        customization.timelineCursorX = (Integer) cursorX.getValue();
        customization.timerPanelWidth = (Integer) timerWidth.getValue();
        customization.timerFontSize = (Integer) timerFontSize.getValue();
        customization.autoResizeTimerFont = !autoResizeTimerFont.isSelected();
        customization.showWaveform = showWaveform.isSelected();
        customization.defaultProjectFormat = defaultProjectFormat.getSelectedIndex() == 1 ? "detx" : "rythmo";
        customization.appLanguage = appLanguage.getSelectedIndex() == 1 ? "en" : "fr";
        if (keyboardLayout.getSelectedIndex() == 1) customization.keyboardLayout = "azerty";
        else if (keyboardLayout.getSelectedIndex() == 2) customization.keyboardLayout = "qwerty";
        else customization.keyboardLayout = "auto";

        // Colors, images and font settings
        customization.timerBackground = timerBgBtn.getBackground();
        customization.timerTextColor = timerTextBtn.getBackground();
            customization.historyBackground = historyBgBtn.getBackground();
            customization.mediaBackground = mediaBgBtn.getBackground();
            customization.timelineEvenBand = evenBandBtn.getBackground();
            customization.timelineOddBand = oddBandBtn.getBackground();
            customization.timelineSelectedBand = selectedBandBtn.getBackground();
            customization.timelineGrid = gridBtn.getBackground();
            customization.timelineCursor = cursorBtn.getBackground();
            customization.timelineSeparator = sepBtn.getBackground();
            customization.waveformColor = waveformColorBtn.getBackground();

            customization.timerImagePath = timerImagePath.getText().trim();
            customization.historyImagePath = historyImagePath.getText().trim();
            customization.mediaImagePath = mediaImagePath.getText().trim();

            if (bandBgMode.getSelectedIndex() == 1) customization.bandBackgroundMode = AppCustomization.BAND_BG_IMAGE_GLOBAL;
            else if (bandBgMode.getSelectedIndex() == 2) customization.bandBackgroundMode = AppCustomization.BAND_BG_IMAGE_PER_BAND;
        else customization.bandBackgroundMode = AppCustomization.BAND_BG_COLOR;

        customization.globalBandImagePath = globalBandImagePath.getText().trim();
        customization.perBandImagePaths = perBandImages.getText().trim();
        customization.timelineFontFamily = String.valueOf(timelineFontFamily.getSelectedItem());

        handler.onApply(customization);
        dispose();
    }

    private ColorPreviewButton createColorButton(Color initial) {
        boolean isEn = "en".equalsIgnoreCase(customization.appLanguage);
        ColorPreviewButton btn = new ColorPreviewButton(initial);
        btn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, isEn ? "Choose a color" : "Choisir une couleur", btn.getBackground());
            if (chosen != null) btn.setPreviewColor(chosen);
        });
        return btn;
    }

    private static class ColorPreviewButton extends JButton {
        private Color previewColor;
        ColorPreviewButton(Color previewColor) {
            this.previewColor = previewColor;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorderPainted(false);
            setPreferredSize(new Dimension(104, 28));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
        void setPreviewColor(Color previewColor) {
            this.previewColor = previewColor;
            repaint();
        }
        @Override public Color getBackground() { return previewColor; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Color col = previewColor != null ? previewColor : Color.WHITE;

            g2.setColor(OmeTheme.surface());
            g2.fillRoundRect(0, 0, w - 1, h - 1, 6, 6);
            g2.setColor(OmeTheme.borderStrong());
            g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);

            // Pastille couleur à gauche
            g2.setColor(col);
            g2.fillRoundRect(4, 4, 22, h - 8, 4, 4);
            g2.setColor(OmeTheme.border());
            g2.drawRoundRect(4, 4, 22, h - 8, 4, 4);

            // Code hexadécimal monospace à droite
            g2.setColor(OmeTheme.textSecondary());
            g2.setFont(OmeTheme.fontMono(Font.BOLD, 10.5f));
            FontMetrics fm = g2.getFontMetrics();
            String hex = OmeTheme.toHex(col);
            g2.drawString(hex, 32, (h + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }
}
