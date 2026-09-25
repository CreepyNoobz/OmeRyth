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
    private JComboBox<String> defaultProjectFormat, appLanguage;
    private ColorPreviewButton timerBgBtn, timerTextBtn, historyBgBtn, mediaBgBtn, evenBandBtn, oddBandBtn;
    private ColorPreviewButton selectedBandBtn, gridBtn, cursorBtn, sepBtn, waveformColorBtn;
    private JTextField timerImagePath, historyImagePath, mediaImagePath, globalBandImagePath;
    private JComboBox<String> bandBgMode, timelineFontFamily;
    private JTextArea perBandImages;

    private JPanel advancedPanel;

    public CustomizationWindow(JFrame parent, AppCustomization customization, ApplyHandler handler) {
        super(parent, "Paramètres", true);
        this.customization = customization;

        setSize(800, 750);
        setMinimumSize(new Dimension(800, 750));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(245, 245, 245));

        // En-tête
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(30, 30, 30));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        JLabel headerLabel = new JLabel("Paramètres OmeRyth");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerLabel.setForeground(Color.WHITE);
        headerPanel.add(headerLabel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Conteneur principal
        JPanel mainContent = new JPanel();
        mainContent.setLayout(new BoxLayout(mainContent, BoxLayout.Y_AXIS));
        mainContent.setBackground(new Color(245, 245, 245));
        mainContent.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Initialisation des champs
        initFields();

        // 1. Paramètres basiques (Toujours visibles)
        JPanel basicPanel = new JPanel(new GridBagLayout());
        basicPanel.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL; // Keep horizontal for text fields
        c.insets = new Insets(8, 10, 8, 10);
        c.weightx = 1;

        int row = 0;
        addRow(basicPanel, c, row++, "Langue de l'application & correction :", appLanguage);
        addRow(basicPanel, c, row++, "Format de projet par défaut:", defaultProjectFormat);
        addRow(basicPanel, c, row++, "Nombre de bandes (Lignes):", bandCount);
        addRow(basicPanel, c, row++, "Hauteur de chaque bande (px):", bandHeight);
        addRow(basicPanel, c, row++, "Position du curseur temporel (px):", cursorX);
        addRow(basicPanel, c, row++, "Largeur de la zone du timer (px):", timerWidth);
        
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1;
        basicPanel.add(autoResizeTimerFont, c);
        row++;
        
        c.gridwidth = 1; c.weightx = 0;
        addRow(basicPanel, c, row++, "Taille de police du timer (manuel):", timerFontSize);

        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1;
        basicPanel.add(showWaveform, c);
        row++;

        mainContent.add(createSection("Réglages Généraux", basicPanel));
        mainContent.add(Box.createVerticalStrut(15));

        JCheckBox advancedToggle = new JCheckBox(" Paramètres avancés (Couleurs, Images, Police)");
        advancedToggle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        advancedToggle.setOpaque(false);
        advancedToggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        advancedToggle.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainContent.add(advancedToggle);
        mainContent.add(Box.createVerticalStrut(10));

        // 3. Paramètres avancés (Cachés par défaut)
        advancedPanel = new JPanel();
        advancedPanel.setLayout(new BoxLayout(advancedPanel, BoxLayout.Y_AXIS));
        advancedPanel.setOpaque(false);
        advancedPanel.setVisible(false);
        advancedPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // -- Sous-section : Couleurs
        JPanel colorsPanel = new JPanel(new GridLayout(0, 2, 15, 15));
        colorsPanel.setOpaque(false);
        colorsPanel.add(createColorRow("Fond zone timer:", timerBgBtn));
        colorsPanel.add(createColorRow("Texte zone timer:", timerTextBtn));
        colorsPanel.add(createColorRow("Fond historique:", historyBgBtn));
        colorsPanel.add(createColorRow("Fond zone média:", mediaBgBtn));
        colorsPanel.add(createColorRow("Bande paire:", evenBandBtn));
        colorsPanel.add(createColorRow("Bande impaire:", oddBandBtn));
        colorsPanel.add(createColorRow("Bande sélectionnée:", selectedBandBtn));
        colorsPanel.add(createColorRow("Grille temporel:", gridBtn));
        colorsPanel.add(createColorRow("Curseur temporel:", cursorBtn));
        colorsPanel.add(createColorRow("Séparateurs:", sepBtn));
        colorsPanel.add(createColorRow("Forme d'onde vocale:", waveformColorBtn));

        advancedPanel.add(createSection("Couleurs", colorsPanel));
        advancedPanel.add(Box.createVerticalStrut(15));

        // -- Sous-section : Images et Police
        JPanel imagesPanel = new JPanel(new GridBagLayout());
        imagesPanel.setOpaque(false);
        c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 5, 8, 5);
        c.weightx = 1;
        row = 0;
        
        addRow(imagesPanel, c, row++, "Police texte timeline:", timelineFontFamily);
        addPathRow(imagesPanel, c, row++, "Image fond timer:", timerImagePath, createBrowseBtn("timer", timerImagePath));
        addPathRow(imagesPanel, c, row++, "Image fond historique:", historyImagePath, createBrowseBtn("historique", historyImagePath));
        addPathRow(imagesPanel, c, row++, "Image fond média:", mediaImagePath, createBrowseBtn("media", mediaImagePath));
        addRow(imagesPanel, c, row++, "Mode image bandes:", bandBgMode);
        addPathRow(imagesPanel, c, row++, "Image bandes (Unique):", globalBandImagePath, createBrowseBtn("bandes", globalBandImagePath));
        
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        imagesPanel.add(new JLabel("Images séparées (bande0;bande1):"), c);
        c.gridx = 1; c.weightx = 1;
        imagesPanel.add(new JScrollPane(perBandImages), c);

        advancedPanel.add(createSection("Images de fond & Polices", imagesPanel));
        
        mainContent.add(advancedPanel);

        // Événement pour afficher/masquer les options avancées
        advancedToggle.addActionListener(e -> {
            advancedPanel.setVisible(advancedToggle.isSelected());
            revalidate();
            repaint();
        });

        JScrollPane scroll = new JScrollPane(mainContent);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Actions (Boutons du bas)
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        actions.setBackground(Color.WHITE);
        actions.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));

        JButton reset = new JButton("Rétablir par défaut");
        JButton apply = new JButton("✔ Appliquer");
        apply.setFont(new Font("Segoe UI", Font.BOLD, 12));
        apply.setBackground(new Color(0, 120, 215));
        apply.setForeground(Color.BLACK);
        apply.setFocusPainted(false);
        
        JButton close = new JButton("Annuler");

        actions.add(reset);
        actions.add(close);
        actions.add(apply);
        add(actions, BorderLayout.SOUTH);

        // Listeners
        reset.addActionListener(e -> resetDefaults());
        apply.addActionListener(e -> applyChanges(handler, advancedToggle.isSelected()));
        close.addActionListener(e -> dispose());
    }

    private void initFields() {
        bandCount = new JSpinner(new SpinnerNumberModel(customization.bandCount, 1, 12, 1));
        bandHeight = new JSpinner(new SpinnerNumberModel(customization.bandHeight, 20, 300, 5));
        cursorX = new JSpinner(new SpinnerNumberModel(customization.timelineCursorX, 20, 600, 5));
        timerWidth = new JSpinner(new SpinnerNumberModel(customization.timerPanelWidth, 100, 500, 10));
        timerFontSize = new JSpinner(new SpinnerNumberModel(customization.timerFontSize, 10, 200, 2));
        autoResizeTimerFont = new JCheckBox("Définir manuellement la taille de la police du timer");
        autoResizeTimerFont.setSelected(!customization.autoResizeTimerFont);
        
        timerFontSize.setEnabled(autoResizeTimerFont.isSelected());
        autoResizeTimerFont.addActionListener(e -> timerFontSize.setEnabled(autoResizeTimerFont.isSelected()));

        showWaveform = new JCheckBox("Afficher la forme d'onde audio (vocale)");
        showWaveform.setSelected(customization.showWaveform);

        appLanguage = new JComboBox<>(new String[]{"Français", "English"});
        if ("en".equalsIgnoreCase(customization.appLanguage)) {
            appLanguage.setSelectedIndex(1);
        } else {
            appLanguage.setSelectedIndex(0);
        }

        defaultProjectFormat = new JComboBox<>(new String[]{"rythmo (OmeRyth)", "detx (Cappella)"});
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

        bandBgMode = new JComboBox<>(new String[]{"Couleur", "Image unique", "Images par bande"});
        if (AppCustomization.BAND_BG_IMAGE_GLOBAL.equals(customization.bandBackgroundMode)) bandBgMode.setSelectedIndex(1);
        else if (AppCustomization.BAND_BG_IMAGE_PER_BAND.equals(customization.bandBackgroundMode)) bandBgMode.setSelectedIndex(2);
        else bandBgMode.setSelectedIndex(0);

        String[] fontFamilies = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        Arrays.sort(fontFamilies, String.CASE_INSENSITIVE_ORDER);
        timelineFontFamily = new JComboBox<>(fontFamilies);
        timelineFontFamily.setSelectedItem(customization.timelineFontFamily);
        timelineFontFamily.setMaximumRowCount(15);

        perBandImages = new JTextArea(customization.perBandImagePaths, 3, 20);
        perBandImages.setLineWrap(true);
        perBandImages.setWrapStyleWord(true);
    }

    private JPanel createSection(String title, JPanel content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(220, 220, 220), 1, true),
            new EmptyBorder(15, 15, 15, 15)
        ));
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(new Color(50, 50, 50));
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        
        panel.add(titleLabel, BorderLayout.NORTH);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createColorRow(String label, JComponent colorBtn) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        p.add(new JLabel(label), BorderLayout.CENTER);
        p.add(colorBtn, BorderLayout.EAST);
        return p;
    }

    private JButton createBrowseBtn(String type, JTextField field) {
        JButton btn = new JButton("...");
        btn.setToolTipText("Parcourir");
        btn.addActionListener(e -> {
            File f = FileUtils.chooseOpenFile(this, "Choisir image " + type, "png", "jpg", "jpeg", "gif", "bmp", "webp");
            if (f != null) field.setText(f.getAbsolutePath());
        });
        return btn;
    }

    private void addRow(JPanel form, GridBagConstraints c, int row, String label, JComponent value) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(label), c);
        c.gridx = 1; c.weightx = 1;
        form.add(value, c);
    }

    private void addPathRow(JPanel form, GridBagConstraints c, int row, String label, JTextField pathField, JButton browseBtn) {
        c.gridx = 0; c.gridy = row; c.weightx = 0;
        form.add(new JLabel(label), c);
        JPanel panel = new JPanel(new BorderLayout(4, 0));
        
        // Prevent JTextField from pushing the layout too wide
        pathField.setColumns(10);
        pathField.setMinimumSize(new Dimension(50, 24));
        
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

    private void applyChanges(ApplyHandler handler, boolean applyAdvanced) {
        customization.bandCount = (Integer) bandCount.getValue();
        customization.bandHeight = (Integer) bandHeight.getValue();
        customization.timelineCursorX = (Integer) cursorX.getValue();
        customization.timerPanelWidth = (Integer) timerWidth.getValue();
        customization.timerFontSize = (Integer) timerFontSize.getValue();
        customization.autoResizeTimerFont = !autoResizeTimerFont.isSelected();
        customization.showWaveform = showWaveform.isSelected();
        customization.defaultProjectFormat = defaultProjectFormat.getSelectedIndex() == 1 ? "detx" : "rythmo";
        customization.appLanguage = appLanguage.getSelectedIndex() == 1 ? "en" : "fr";

        // Only apply advanced (colors/images/font) if the section is enabled
        if (applyAdvanced) {
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
        }

        handler.onApply(customization);
    }

    private ColorPreviewButton createColorButton(Color initial) {
        ColorPreviewButton btn = new ColorPreviewButton(initial);
        btn.addActionListener(e -> {
            Color chosen = JColorChooser.showDialog(this, "Choisir une couleur", btn.getBackground());
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
            setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150)));
            setPreferredSize(new Dimension(80, 28));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }
        void setPreviewColor(Color previewColor) {
            this.previewColor = previewColor;
            repaint();
        }
        @Override public Color getBackground() { return previewColor; }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(previewColor != null ? previewColor : Color.WHITE);
            g2.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
