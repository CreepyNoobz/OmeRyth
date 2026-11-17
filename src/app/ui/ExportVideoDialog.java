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
        public final int roleLabelScalePercent;
        public final boolean isBuiltin;

        public ExportPreset(String name, int width, int height, double visibleSeconds, int fps, boolean isBuiltin) {
            this(name, width, height, visibleSeconds, fps, 100, isBuiltin);
        }

        public ExportPreset(String name, int width, int height, double visibleSeconds, int fps, int roleLabelScalePercent, boolean isBuiltin) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.visibleSeconds = visibleSeconds;
            this.fps = fps;
            this.roleLabelScalePercent = Math.max(10, Math.min(200, roleLabelScalePercent));
            this.isBuiltin = isBuiltin;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class TextOverlayConfig {
        public boolean enabled = false;
        public String text = "© OmeRyth";
        public String fontFamily = "Segoe UI";
        public int fontSize = 42;
        public boolean bold = true;
        public boolean italic = false;
        public Color color = Color.WHITE;
        public int opacity = 80; // 0 à 100%
        public double angle = 0.0; // degrés (-180° à +180°)
        public int posX = 50; // % largeur (0 à 100%)
        public int posY = 50; // % hauteur (0 à 100%)
        public double exactPosX = 50.0;
        public double exactPosY = 50.0;
        private boolean hasExactPos = false;
        public boolean outline = true;
        public Color outlineColor = new Color(0, 0, 0, 220);
        public int outlineWidth = 3;

        public double getEffectivePosX() {
            if (hasExactPos && Math.abs(exactPosX - posX) <= 1.0) {
                return exactPosX;
            }
            return posX;
        }

        public double getEffectivePosY() {
            if (hasExactPos && Math.abs(exactPosY - posY) <= 1.0) {
                return exactPosY;
            }
            return posY;
        }

        public void setExactPosition(double xPct, double yPct) {
            this.exactPosX = Math.max(0.0, Math.min(100.0, xPct));
            this.exactPosY = Math.max(0.0, Math.min(100.0, yPct));
            this.posX = (int) Math.round(this.exactPosX);
            this.posY = (int) Math.round(this.exactPosY);
            this.hasExactPos = true;
        }

        public TextOverlayConfig copy() {
            TextOverlayConfig c = new TextOverlayConfig();
            c.enabled = this.enabled;
            c.text = this.text;
            c.fontFamily = this.fontFamily;
            c.fontSize = this.fontSize;
            c.bold = this.bold;
            c.italic = this.italic;
            c.color = this.color;
            c.opacity = this.opacity;
            c.angle = this.angle;
            c.posX = this.posX;
            c.posY = this.posY;
            c.exactPosX = this.exactPosX;
            c.exactPosY = this.exactPosY;
            c.hasExactPos = this.hasExactPos;
            c.outline = this.outline;
            c.outlineColor = this.outlineColor;
            c.outlineWidth = this.outlineWidth;
            return c;
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
        public int cpuThreads = Runtime.getRuntime().availableProcessors();
        public boolean blurBackgroundVideo = false;
        public int blurRadius = 25;
        public int blurOpacity = 85;
        public java.util.List<String> layerOrder = new java.util.ArrayList<>(java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND"));
        public java.util.List<double[]> voiceKeepZones = new java.util.ArrayList<>();
        public boolean voiceCutMode = true;
        public boolean mirrorVideo = false;
        public int roleLabelScalePercent = 100; // 10% à 200%
        public TextOverlayConfig textOverlay = new TextOverlayConfig();
        public boolean approved = false;
    }

    public static class MontagePreset {
        public String name;
        public int width;
        public int height;
        public Rectangle videoRect;
        public Rectangle bandRect;
        public String templateName;
        public boolean removeVocals;
        public boolean antiCopyright;
        public int antiCopyrightOpacity;
        public boolean blurBackground;
        public int blurRadius;
        public int blurOpacity;
        public int fps;
        public double visibleSeconds;
        public String encoder;
        public java.util.List<String> layerOrder;
        public boolean mirrorVideo;
        public int roleLabelScalePercent = 100;
        public TextOverlayConfig textOverlay = new TextOverlayConfig();
        public boolean isBuiltin;

        public MontagePreset(String name, int width, int height, Rectangle videoRect, Rectangle bandRect,
                             String templateName, boolean removeVocals, boolean antiCopyright,
                             int antiCopyrightOpacity, boolean blurBackground, int blurRadius,
                             int blurOpacity, int fps, double visibleSeconds, String encoder,
                             java.util.List<String> layerOrder, boolean isBuiltin) {
            this(name, width, height, videoRect, bandRect, templateName, removeVocals, antiCopyright,
                    antiCopyrightOpacity, blurBackground, blurRadius, blurOpacity, fps, visibleSeconds, encoder,
                    layerOrder, null, false, isBuiltin);
        }

        public MontagePreset(String name, int width, int height, Rectangle videoRect, Rectangle bandRect,
                             String templateName, boolean removeVocals, boolean antiCopyright,
                             int antiCopyrightOpacity, boolean blurBackground, int blurRadius,
                             int blurOpacity, int fps, double visibleSeconds, String encoder,
                             java.util.List<String> layerOrder, TextOverlayConfig textOverlay, boolean isBuiltin) {
            this(name, width, height, videoRect, bandRect, templateName, removeVocals, antiCopyright,
                    antiCopyrightOpacity, blurBackground, blurRadius, blurOpacity, fps, visibleSeconds, encoder,
                    layerOrder, textOverlay, false, isBuiltin);
        }

        public MontagePreset(String name, int width, int height, Rectangle videoRect, Rectangle bandRect,
                             String templateName, boolean removeVocals, boolean antiCopyright,
                             int antiCopyrightOpacity, boolean blurBackground, int blurRadius,
                             int blurOpacity, int fps, double visibleSeconds, String encoder,
                             java.util.List<String> layerOrder, TextOverlayConfig textOverlay, boolean mirrorVideo, boolean isBuiltin) {
            this.name = name;
            this.width = width;
            this.height = height;
            this.videoRect = (videoRect != null) ? new Rectangle(videoRect) : new Rectangle(0, 0, width, (int)(height*0.75));
            this.bandRect = (bandRect != null) ? new Rectangle(bandRect) : new Rectangle(0, (int)(height*0.75), width, (int)(height*0.25));
            this.templateName = templateName != null ? templateName : "OMERYTH_ORIGINAL";
            this.removeVocals = removeVocals;
            this.antiCopyright = antiCopyright;
            this.antiCopyrightOpacity = antiCopyrightOpacity;
            this.blurBackground = blurBackground;
            this.blurRadius = blurRadius;
            this.blurOpacity = blurOpacity;
            this.fps = fps;
            this.visibleSeconds = visibleSeconds;
            this.encoder = encoder != null ? encoder : "auto";
            this.layerOrder = (layerOrder != null) ? new java.util.ArrayList<>(layerOrder) : new java.util.ArrayList<>(java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND"));
            if (textOverlay != null) {
                this.textOverlay = textOverlay.copy();
            }
            this.mirrorVideo = mirrorVideo;
            this.isBuiltin = isBuiltin;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static String[] getAvailableWatermarkFonts() {
        java.util.LinkedHashSet<String> fonts = new java.util.LinkedHashSet<>();
        String[] priority = new String[]{"Segoe UI", "Arial", "Impact", "Montserrat", "Verdana", "Tahoma", "Trebuchet MS", "Georgia", "Times New Roman", "Consolas", "Courier New"};
        for (String p : priority) fonts.add(p);
        try {
            String[] sys = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            for (String s : sys) fonts.add(s);
        } catch (Throwable ignored) {}
        return fonts.toArray(new String[0]);
    }

    public static Dimension computeTextOverlayExportSize(TextOverlayConfig cfg) {
        if (cfg == null || cfg.text == null || cfg.text.trim().isEmpty()) {
            return new Dimension(120, 48);
        }
        int style = Font.PLAIN;
        if (cfg.bold && cfg.italic) style = Font.BOLD | Font.ITALIC;
        else if (cfg.bold) style = Font.BOLD;
        else if (cfg.italic) style = Font.ITALIC;
        Font font = new Font(cfg.fontFamily != null ? cfg.fontFamily : "Segoe UI", style, Math.max(6, cfg.fontSize));
        java.awt.font.FontRenderContext frc = new java.awt.font.FontRenderContext(null, true, true);
        String[] lines = cfg.text.split("\r?\n");
        int maxW = 40;
        int lineH = Math.max(12, cfg.fontSize);
        for (String line : lines) {
            java.awt.geom.Rectangle2D b = font.getStringBounds(line, frc);
            maxW = Math.max(maxW, (int) Math.ceil(b.getWidth()));
            lineH = Math.max(lineH, (int) Math.ceil(b.getHeight()));
        }
        int pad = (cfg.outline ? cfg.outlineWidth * 2 : 0) + 16;
        return new Dimension(maxW + pad, lines.length * lineH + pad);
    }

    public static void drawTextOverlay(Graphics2D g2Original, TextOverlayConfig cfg, int canvasW, int canvasH) {
        if (cfg == null || !cfg.enabled || cfg.text == null || cfg.text.trim().isEmpty()) {
            return;
        }
        float alpha = Math.max(0.0f, Math.min(1.0f, cfg.opacity / 100.0f));
        if (alpha <= 0.001f) return;

        Graphics2D g2 = (Graphics2D) g2Original.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            int style = Font.PLAIN;
            if (cfg.bold && cfg.italic) style = Font.BOLD | Font.ITALIC;
            else if (cfg.bold) style = Font.BOLD;
            else if (cfg.italic) style = Font.ITALIC;

            Font font = new Font(cfg.fontFamily != null ? cfg.fontFamily : "Segoe UI", style, Math.max(6, cfg.fontSize));
            g2.setFont(font);
            FontMetrics fm = g2.getFontMetrics(font);

            double cx = (canvasW * cfg.getEffectivePosX()) / 100.0;
            double cy = (canvasH * cfg.getEffectivePosY()) / 100.0;

            java.awt.geom.AffineTransform origXform = g2.getTransform();
            g2.translate(cx, cy);
            if (Math.abs(cfg.angle) > 0.001) {
                g2.rotate(Math.toRadians(cfg.angle));
            }

            String[] lines = cfg.text.split("\r?\n");
            int lineHeight = fm.getHeight();
            int totalHeight = lines.length * lineHeight;
            int startY = -totalHeight / 2 + fm.getAscent();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int textW = fm.stringWidth(line);
                int drawX = -textW / 2;
                int drawY = startY + i * lineHeight;

                if (cfg.outline && cfg.outlineWidth > 0) {
                    try {
                        java.awt.font.FontRenderContext frc = g2.getFontRenderContext();
                        java.awt.font.GlyphVector gv = font.createGlyphVector(frc, line);
                        Shape glyphShape = gv.getOutline(drawX, drawY);

                        float outlineAlpha = alpha * Math.min(1.0f, (cfg.outlineColor != null ? cfg.outlineColor.getAlpha() / 255.0f : 1.0f));
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, outlineAlpha));
                        Color baseOutline = cfg.outlineColor != null ? cfg.outlineColor : Color.BLACK;
                        g2.setColor(new Color(baseOutline.getRed(), baseOutline.getGreen(), baseOutline.getBlue()));
                        g2.setStroke(new BasicStroke(cfg.outlineWidth * 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                        g2.draw(glyphShape);
                    } catch (Throwable ignored) {
                        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha * 0.7f));
                        g2.setColor(cfg.outlineColor != null ? cfg.outlineColor : Color.BLACK);
                        g2.drawString(line, drawX + cfg.outlineWidth, drawY + cfg.outlineWidth);
                    }
                }

                float fillAlpha = alpha * Math.min(1.0f, (cfg.color != null ? cfg.color.getAlpha() / 255.0f : 1.0f));
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fillAlpha));
                Color baseColor = cfg.color != null ? cfg.color : Color.WHITE;
                g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue()));
                g2.drawString(line, drawX, drawY);
            }

            g2.setTransform(origXform);
        } finally {
            g2.dispose();
        }
    }

    private static String serializeTextOverlay(TextOverlayConfig c) {
        if (c == null) return "";
        String b64Text = (c.text != null && !c.text.isEmpty())
                ? java.util.Base64.getEncoder().encodeToString(c.text.getBytes(StandardCharsets.UTF_8))
                : "";
        int colRgb = (c.color != null) ? c.color.getRGB() : Color.WHITE.getRGB();
        int outColRgb = (c.outlineColor != null) ? c.outlineColor.getRGB() : new Color(0, 0, 0, 220).getRGB();
        return c.enabled + ";" + b64Text + ";" + (c.fontFamily != null ? c.fontFamily : "Segoe UI") + ";" +
               c.fontSize + ";" + c.bold + ";" + c.italic + ";" + colRgb + ";" + c.opacity + ";" +
               c.angle + ";" + c.posX + ";" + c.posY + ";" + c.outline + ";" + outColRgb + ";" + c.outlineWidth;
    }

    private static TextOverlayConfig deserializeTextOverlay(String raw) {
        TextOverlayConfig c = new TextOverlayConfig();
        if (raw == null || raw.trim().isEmpty()) return c;
        try {
            String[] parts = raw.split(";", -1);
            if (parts.length >= 14) {
                c.enabled = Boolean.parseBoolean(parts[0]);
                if (!parts[1].isEmpty()) {
                    c.text = new String(java.util.Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                }
                c.fontFamily = parts[2];
                c.fontSize = Integer.parseInt(parts[3]);
                c.bold = Boolean.parseBoolean(parts[4]);
                c.italic = Boolean.parseBoolean(parts[5]);
                c.color = new Color(Integer.parseInt(parts[6]), true);
                c.opacity = Integer.parseInt(parts[7]);
                c.angle = Double.parseDouble(parts[8]);
                c.posX = Integer.parseInt(parts[9]);
                c.posY = Integer.parseInt(parts[10]);
                c.outline = Boolean.parseBoolean(parts[11]);
                c.outlineColor = new Color(Integer.parseInt(parts[12]), true);
                c.outlineWidth = Integer.parseInt(parts[13]);
            }
        } catch (Exception ex) {
            System.err.println("Erreur chargement watermark: " + ex.getMessage());
        }
        return c;
    }

    public class WatermarkControlGroup {
        public final JCheckBox checkEnabled;
        public final JTextField textInput;
        public final JComboBox<String> comboFont;
        public final JSpinner spinnerFontSize;
        public final JCheckBox checkBold;
        public final JCheckBox checkItalic;
        public final JButton btnTextColor;
        public final JCheckBox checkOutline;
        public final JButton btnOutlineColor;
        public final JSpinner spinnerOutlineWidth;
        public final JComboBox<String> comboPosition;
        public final JSpinner spinnerPosX;
        public final JSpinner spinnerPosY;
        public final JSpinner spinnerAngle;
        public final JSlider sliderOpacity;
        public final JLabel lblOpacityVal;

        private Color currentColor = Color.WHITE;
        private Color currentOutlineColor = new Color(0, 0, 0, 220);
        private boolean isUpdating = false;
        private boolean isUpdatingPos = false;
        private final Runnable changeCallback;
        private JPanel contentPanel;

        public WatermarkControlGroup(Runnable changeCallback) {
            this.changeCallback = changeCallback;

            checkEnabled = new JCheckBox("Activer l'incrustation de texte / filigrane", false);
            checkEnabled.setFont(new Font("Segoe UI", Font.BOLD, 11));
            checkEnabled.setForeground(new Color(56, 189, 248)); // Sky Blue

            textInput = new JTextField("© OmeRyth");
            textInput.setFont(new Font("Segoe UI", Font.PLAIN, 11));

            comboFont = new JComboBox<>(getAvailableWatermarkFonts());
            comboFont.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            comboFont.setSelectedItem("Segoe UI");

            spinnerFontSize = new JSpinner(new SpinnerNumberModel(42, 8, 300, 2));
            checkBold = new JCheckBox("Gras", true);
            checkBold.setFont(new Font("Segoe UI", Font.BOLD, 10));
            checkItalic = new JCheckBox("Italique", false);
            checkItalic.setFont(new Font("Segoe UI", Font.ITALIC, 10));

            btnTextColor = new JButton("Couleur...");
            btnTextColor.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            btnTextColor.setForeground(Color.BLACK);
            btnTextColor.setIcon(createColorIcon(currentColor));

            checkOutline = new JCheckBox("Contour", true);
            checkOutline.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            checkOutline.setForeground(new Color(212, 212, 216));

            btnOutlineColor = new JButton("Noir");
            btnOutlineColor.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            btnOutlineColor.setForeground(Color.BLACK);
            btnOutlineColor.setIcon(createColorIcon(currentOutlineColor));

            spinnerOutlineWidth = new JSpinner(new SpinnerNumberModel(3, 1, 15, 1));

            String[] posOptions = new String[]{
                    "Centre (50%, 50%)",
                    "En haut à gauche (10%, 8%)",
                    "En haut au centre (50%, 8%)",
                    "En haut à droite (90%, 8%)",
                    "En bas à gauche (10%, 92%)",
                    "En bas au centre (50%, 92%)",
                    "En bas à droite (90%, 92%)",
                    "Personnalisée"
            };
            comboPosition = new JComboBox<>(posOptions);
            comboPosition.setFont(new Font("Segoe UI", Font.PLAIN, 11));

            spinnerPosX = new JSpinner(new SpinnerNumberModel(50, 0, 100, 1));
            spinnerPosY = new JSpinner(new SpinnerNumberModel(50, 0, 100, 1));

            spinnerAngle = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 5.0));

            sliderOpacity = new JSlider(0, 100, 80);
            sliderOpacity.setOpaque(false);
            lblOpacityVal = new JLabel("80%");
            lblOpacityVal.setFont(new Font("Consolas", Font.BOLD, 11));
            lblOpacityVal.setForeground(new Color(56, 189, 248));

            setupListeners();
        }

        private void setupListeners() {
            checkEnabled.addActionListener(e -> {
                updateContentEnabledState();
                triggerChange();
            });

            textInput.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { if (!isUpdating) triggerChange(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { if (!isUpdating) triggerChange(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { if (!isUpdating) triggerChange(); }
            });

            comboFont.addActionListener(e -> { if (!isUpdating) triggerChange(); });
            spinnerFontSize.addChangeListener(e -> { if (!isUpdating) triggerChange(); });
            checkBold.addActionListener(e -> { if (!isUpdating) triggerChange(); });
            checkItalic.addActionListener(e -> { if (!isUpdating) triggerChange(); });

            btnTextColor.addActionListener(e -> {
                Color c = JColorChooser.showDialog(ExportVideoDialog.this, "Couleur du texte", currentColor);
                if (c != null) {
                    currentColor = c;
                    btnTextColor.setIcon(createColorIcon(currentColor));
                    triggerChange();
                }
            });

            checkOutline.addActionListener(e -> {
                boolean en = checkOutline.isSelected();
                btnOutlineColor.setEnabled(en);
                spinnerOutlineWidth.setEnabled(en);
                triggerChange();
            });

            btnOutlineColor.addActionListener(e -> {
                Color c = JColorChooser.showDialog(ExportVideoDialog.this, "Couleur du contour", currentOutlineColor);
                if (c != null) {
                    currentOutlineColor = c;
                    btnOutlineColor.setIcon(createColorIcon(currentOutlineColor));
                    triggerChange();
                }
            });

            spinnerOutlineWidth.addChangeListener(e -> { if (!isUpdating) triggerChange(); });

            comboPosition.addActionListener(e -> {
                if (isUpdating || isUpdatingPos) return;
                int idx = comboPosition.getSelectedIndex();
                isUpdatingPos = true;
                switch (idx) {
                    case 0 -> { spinnerPosX.setValue(50); spinnerPosY.setValue(50); }
                    case 1 -> { spinnerPosX.setValue(10); spinnerPosY.setValue(8); }
                    case 2 -> { spinnerPosX.setValue(50); spinnerPosY.setValue(8); }
                    case 3 -> { spinnerPosX.setValue(90); spinnerPosY.setValue(8); }
                    case 4 -> { spinnerPosX.setValue(10); spinnerPosY.setValue(92); }
                    case 5 -> { spinnerPosX.setValue(50); spinnerPosY.setValue(92); }
                    case 6 -> { spinnerPosX.setValue(90); spinnerPosY.setValue(92); }
                }
                isUpdatingPos = false;
                triggerChange();
            });

            javax.swing.event.ChangeListener posListener = e -> {
                if (isUpdating || isUpdatingPos) return;
                int px = ((Number) spinnerPosX.getValue()).intValue();
                int py = ((Number) spinnerPosY.getValue()).intValue();
                isUpdatingPos = true;
                if (px == 50 && py == 50) comboPosition.setSelectedIndex(0);
                else if (px == 10 && py == 8) comboPosition.setSelectedIndex(1);
                else if (px == 50 && py == 8) comboPosition.setSelectedIndex(2);
                else if (px == 90 && py == 8) comboPosition.setSelectedIndex(3);
                else if (px == 10 && py == 92) comboPosition.setSelectedIndex(4);
                else if (px == 50 && py == 92) comboPosition.setSelectedIndex(5);
                else if (px == 90 && py == 92) comboPosition.setSelectedIndex(6);
                else comboPosition.setSelectedIndex(7);
                isUpdatingPos = false;
                triggerChange();
            };
            spinnerPosX.addChangeListener(posListener);
            spinnerPosY.addChangeListener(posListener);

            spinnerAngle.addChangeListener(e -> { if (!isUpdating) triggerChange(); });

            sliderOpacity.addChangeListener(e -> {
                lblOpacityVal.setText(sliderOpacity.getValue() + "%");
                if (!isUpdating) triggerChange();
            });
        }

        public JPanel createPanel(String title) {
            JPanel panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
            panel.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(63, 63, 70)),
                    title,
                    TitledBorder.LEFT, TitledBorder.TOP,
                    new Font("Segoe UI", Font.PLAIN, 11),
                    new Color(212, 212, 216)
            ));

            JPanel topToggle = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));
            topToggle.setOpaque(false);
            topToggle.add(checkEnabled);
            panel.add(topToggle);

            contentPanel = new JPanel();
            contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
            contentPanel.setOpaque(false);
            contentPanel.setBorder(new EmptyBorder(2, 4, 4, 4));

            // 1. Texte
            JPanel rowText = new JPanel(new BorderLayout(6, 0));
            rowText.setOpaque(false);
            JLabel lblTxt = new JLabel("Texte :");
            lblTxt.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblTxt.setPreferredSize(new Dimension(65, 22));
            rowText.add(lblTxt, BorderLayout.WEST);
            rowText.add(textInput, BorderLayout.CENTER);
            contentPanel.add(rowText);
            contentPanel.add(Box.createVerticalStrut(4));

            // 2. Position
            JPanel rowPos = new JPanel(new GridBagLayout());
            rowPos.setOpaque(false);
            GridBagConstraints gbcP = new GridBagConstraints();
            gbcP.fill = GridBagConstraints.HORIZONTAL;
            gbcP.insets = new Insets(1, 2, 1, 2);

            gbcP.gridx = 0; gbcP.weightx = 0;
            JLabel lblPos = new JLabel("Position :");
            lblPos.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblPos.setPreferredSize(new Dimension(65, 22));
            rowPos.add(lblPos, gbcP);

            gbcP.gridx = 1; gbcP.weightx = 0.55;
            rowPos.add(comboPosition, gbcP);

            gbcP.gridx = 2; gbcP.weightx = 0;
            JLabel lblX = new JLabel("X:");
            lblX.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            rowPos.add(lblX, gbcP);

            gbcP.gridx = 3; gbcP.weightx = 0.22;
            spinnerPosX.setPreferredSize(new Dimension(46, 22));
            rowPos.add(spinnerPosX, gbcP);

            gbcP.gridx = 4; gbcP.weightx = 0;
            JLabel lblY = new JLabel("Y:");
            lblY.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            rowPos.add(lblY, gbcP);

            gbcP.gridx = 5; gbcP.weightx = 0.22;
            spinnerPosY.setPreferredSize(new Dimension(46, 22));
            rowPos.add(spinnerPosY, gbcP);

            gbcP.gridx = 6; gbcP.weightx = 0;
            JLabel lblPct = new JLabel("%");
            lblPct.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            rowPos.add(lblPct, gbcP);
            contentPanel.add(rowPos);
            contentPanel.add(Box.createVerticalStrut(4));

            // 3. Inclinaison
            JPanel rowAngle = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            rowAngle.setOpaque(false);
            JLabel lblAng = new JLabel("Inclinaison :");
            lblAng.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblAng.setPreferredSize(new Dimension(65, 22));
            rowAngle.add(lblAng);

            spinnerAngle.setPreferredSize(new Dimension(54, 22));
            rowAngle.add(spinnerAngle);
            JLabel lblDeg = new JLabel("°");
            lblDeg.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            rowAngle.add(lblDeg);

            rowAngle.add(Box.createHorizontalStrut(6));
            JButton btn0 = createAngleBtn("0°", 0.0);
            JButton btnM45 = createAngleBtn("-45°", -45.0);
            JButton btnP45 = createAngleBtn("+45°", 45.0);
            JButton btn90 = createAngleBtn("90°", 90.0);
            rowAngle.add(btn0);
            rowAngle.add(btnM45);
            rowAngle.add(btnP45);
            rowAngle.add(btn90);
            contentPanel.add(rowAngle);
            contentPanel.add(Box.createVerticalStrut(4));

            // 4. Police & Style
            JPanel rowFont = new JPanel(new GridBagLayout());
            rowFont.setOpaque(false);
            GridBagConstraints gbcF = new GridBagConstraints();
            gbcF.fill = GridBagConstraints.HORIZONTAL;
            gbcF.insets = new Insets(1, 2, 1, 2);

            gbcF.gridx = 0; gbcF.weightx = 0;
            JLabel lblF = new JLabel("Police :");
            lblF.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblF.setPreferredSize(new Dimension(65, 22));
            rowFont.add(lblF, gbcF);

            gbcF.gridx = 1; gbcF.weightx = 0.45;
            rowFont.add(comboFont, gbcF);

            gbcF.gridx = 2; gbcF.weightx = 0;
            JLabel lblSz = new JLabel("Taille :");
            lblSz.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            rowFont.add(lblSz, gbcF);

            gbcF.gridx = 3; gbcF.weightx = 0.25;
            spinnerFontSize.setPreferredSize(new Dimension(48, 22));
            rowFont.add(spinnerFontSize, gbcF);

            gbcF.gridx = 4; gbcF.weightx = 0;
            JLabel lblPx = new JLabel("px");
            lblPx.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            rowFont.add(lblPx, gbcF);

            gbcF.gridx = 5; gbcF.weightx = 0;
            rowFont.add(checkBold, gbcF);

            gbcF.gridx = 6; gbcF.weightx = 0;
            rowFont.add(checkItalic, gbcF);
            contentPanel.add(rowFont);
            contentPanel.add(Box.createVerticalStrut(4));

            // 5. Couleur & Contour
            JPanel rowCol = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 1));
            rowCol.setOpaque(false);
            JLabel lblC = new JLabel("Couleur :");
            lblC.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblC.setPreferredSize(new Dimension(65, 22));
            rowCol.add(lblC);

            rowCol.add(btnTextColor);
            rowCol.add(createPaletteBtn("Blanc", Color.WHITE));
            rowCol.add(createPaletteBtn("Or / Doré", new Color(245, 158, 11)));
            rowCol.add(createPaletteBtn("Ciel", new Color(56, 189, 248)));
            rowCol.add(createPaletteBtn("Rouge", new Color(239, 68, 68)));
            rowCol.add(createPaletteBtn("Noir", Color.BLACK));

            rowCol.add(Box.createHorizontalStrut(6));
            rowCol.add(checkOutline);
            spinnerOutlineWidth.setPreferredSize(new Dimension(40, 22));
            rowCol.add(spinnerOutlineWidth);
            rowCol.add(btnOutlineColor);
            contentPanel.add(rowCol);
            contentPanel.add(Box.createVerticalStrut(4));

            // 6. Opacité
            JPanel rowOp = new JPanel(new BorderLayout(6, 0));
            rowOp.setOpaque(false);
            JLabel lblOp = new JLabel("Opacité :");
            lblOp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblOp.setPreferredSize(new Dimension(65, 22));
            rowOp.add(lblOp, BorderLayout.WEST);
            sliderOpacity.setPreferredSize(new Dimension(160, 22));
            rowOp.add(sliderOpacity, BorderLayout.CENTER);
            rowOp.add(lblOpacityVal, BorderLayout.EAST);
            contentPanel.add(rowOp);

            panel.add(contentPanel);
            updateContentEnabledState();
            return panel;
        }

        private JButton createAngleBtn(String label, double angle) {
            JButton b = new JButton(label);
            b.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            b.setForeground(Color.BLACK);
            b.setMargin(new Insets(1, 4, 1, 4));
            b.addActionListener(e -> {
                spinnerAngle.setValue(angle);
                triggerChange();
            });
            return b;
        }

        private JButton createPaletteBtn(String tip, Color c) {
            JButton b = new JButton();
            b.setToolTipText(tip);
            b.setPreferredSize(new Dimension(18, 18));
            b.setMargin(new Insets(0, 0, 0, 0));
            b.setIcon(createColorIcon(c));
            b.addActionListener(e -> {
                currentColor = c;
                btnTextColor.setIcon(createColorIcon(currentColor));
                triggerChange();
            });
            return b;
        }

        private Icon createColorIcon(Color c) {
            return new Icon() {
                @Override
                public void paintIcon(Component cmp, Graphics g, int x, int y) {
                    g.setColor(c);
                    g.fillRect(x + 1, y + 1, 12, 12);
                    g.setColor(new Color(80, 80, 80));
                    g.drawRect(x + 1, y + 1, 12, 12);
                }
                @Override public int getIconWidth() { return 14; }
                @Override public int getIconHeight() { return 14; }
            };
        }

        private void triggerChange() {
            if (!isUpdating && changeCallback != null) {
                changeCallback.run();
            }
        }

        private void updateContentEnabledState() {
            boolean en = checkEnabled.isSelected();
            setContainerEnabled(contentPanel, en);
            if (en) {
                boolean outEn = checkOutline.isSelected();
                btnOutlineColor.setEnabled(outEn);
                spinnerOutlineWidth.setEnabled(outEn);
            }
        }

        private void setContainerEnabled(Container c, boolean en) {
            if (c == null) return;
            for (Component comp : c.getComponents()) {
                comp.setEnabled(en);
                if (comp instanceof Container child) {
                    setContainerEnabled(child, en);
                }
            }
        }

        public void syncFromConfig(TextOverlayConfig cfg) {
            if (cfg == null) return;
            isUpdating = true;
            try {
                checkEnabled.setSelected(cfg.enabled);
                textInput.setText(cfg.text != null ? cfg.text : "");
                if (cfg.fontFamily != null) {
                    comboFont.setSelectedItem(cfg.fontFamily);
                }
                spinnerFontSize.setValue(Math.max(8, cfg.fontSize));
                checkBold.setSelected(cfg.bold);
                checkItalic.setSelected(cfg.italic);
                currentColor = cfg.color != null ? cfg.color : Color.WHITE;
                btnTextColor.setIcon(createColorIcon(currentColor));
                sliderOpacity.setValue(Math.max(0, Math.min(100, cfg.opacity)));
                lblOpacityVal.setText(sliderOpacity.getValue() + "%");
                spinnerAngle.setValue(cfg.angle);
                spinnerPosX.setValue(Math.max(0, Math.min(100, cfg.posX)));
                spinnerPosY.setValue(Math.max(0, Math.min(100, cfg.posY)));
                checkOutline.setSelected(cfg.outline);
                currentOutlineColor = cfg.outlineColor != null ? cfg.outlineColor : new Color(0, 0, 0, 220);
                btnOutlineColor.setIcon(createColorIcon(currentOutlineColor));
                spinnerOutlineWidth.setValue(Math.max(1, Math.min(15, cfg.outlineWidth)));

                int px = cfg.posX;
                int py = cfg.posY;
                if (px == 50 && py == 50) comboPosition.setSelectedIndex(0);
                else if (px == 10 && py == 8) comboPosition.setSelectedIndex(1);
                else if (px == 50 && py == 8) comboPosition.setSelectedIndex(2);
                else if (px == 90 && py == 8) comboPosition.setSelectedIndex(3);
                else if (px == 10 && py == 92) comboPosition.setSelectedIndex(4);
                else if (px == 50 && py == 92) comboPosition.setSelectedIndex(5);
                else if (px == 90 && py == 92) comboPosition.setSelectedIndex(6);
                else comboPosition.setSelectedIndex(7);

                updateContentEnabledState();
            } finally {
                isUpdating = false;
            }
        }

        public void syncToConfig(TextOverlayConfig cfg) {
            if (cfg == null) return;
            cfg.enabled = checkEnabled.isSelected();
            cfg.text = textInput.getText();
            cfg.fontFamily = (String) comboFont.getSelectedItem();
            cfg.fontSize = ((Number) spinnerFontSize.getValue()).intValue();
            cfg.bold = checkBold.isSelected();
            cfg.italic = checkItalic.isSelected();
            cfg.color = currentColor;
            cfg.opacity = sliderOpacity.getValue();
            cfg.angle = ((Number) spinnerAngle.getValue()).doubleValue();
            cfg.posX = ((Number) spinnerPosX.getValue()).intValue();
            cfg.posY = ((Number) spinnerPosY.getValue()).intValue();
            cfg.outline = checkOutline.isSelected();
            cfg.outlineColor = currentOutlineColor;
            cfg.outlineWidth = ((Number) spinnerOutlineWidth.getValue()).intValue();
        }
    }

    private static final String PRESETS_FILE = "export_presets.properties";
    private static final String MONTAGE_PRESETS_FILE = "montage_presets.properties";

    private final TimelinePanel timelinePanel;
    private BufferedImage videoSnapshot;
    private int bandCount = 1;
    private SingleBandPreviewPanel singleBandPreview;

    // Composants Onglet 1 : Bandeau Seul
    private final DefaultComboBoxModel<ExportPreset> presetModel = new DefaultComboBoxModel<>();
    private final JComboBox<ExportPreset> comboPreset;
    private final JButton btnSavePreset;
    private final JButton btnRenamePreset;
    private final JButton btnDeletePreset;
    private final JSpinner spinnerWidth;
    private final JSpinner spinnerHeight;
    private final JSpinner spinnerVisibleSeconds;
    private final JSlider sliderRoleLabelScale;
    private final JLabel lblRoleLabelScaleVal;
    private final JComboBox<String> comboFps;
    private final JComboBox<String> comboEncoder;
    private final JComboBox<String> comboCpuCores;

    public static String[] buildCpuCoreOptions() {
        int totalCores = Runtime.getRuntime().availableProcessors();
        int balancedCores = Math.max(1, (int) Math.round(totalCores * 0.75));
        int ecoCores = Math.max(1, Math.min(4, Math.max(2, totalCores / 4)));
        return new String[]{
                "Performance Maximale (" + totalCores + " cœurs / 100% de puissance)",
                "Équilibré (" + balancedCores + " cœurs / 75% de puissance)",
                "Économe / Silencieux (" + ecoCores + " cœurs)"
        };
    }

    public static int getThreadsFromCpuIndex(int index) {
        int totalCores = Runtime.getRuntime().availableProcessors();
        int balancedCores = Math.max(1, (int) Math.round(totalCores * 0.75));
        int ecoCores = Math.max(1, Math.min(4, Math.max(2, totalCores / 4)));
        return switch (index) {
            case 1 -> balancedCores;
            case 2 -> ecoCores;
            default -> totalCores;
        };
    }

    // Composants Onglet 2 : Montage Vidéo + Bandeau
    private final DefaultComboBoxModel<MontagePreset> montagePresetModel = new DefaultComboBoxModel<>();
    private JComboBox<MontagePreset> comboMontagePreset;
    private JButton btnSaveMontagePreset;
    private JButton btnRenameMontagePreset;
    private JButton btnDeleteMontagePreset;
    private MontagePreset customMontagePresetItem;
    private boolean updatingMontagePreset = false;

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
    private JCheckBox checkMirrorVideo;
    private JComboBox<String> comboMontageFps;
    private JComboBox<String> comboMontageEncoder;
    private JComboBox<String> comboMontageCpuCores;
    private JSpinner spinnerMontageVisibleSeconds;
    private JSlider sliderMontageRoleLabelScale;
    private JLabel lblMontageRoleLabelScaleVal;
    private boolean isSyncingRoleLabelScale = false;
    private boolean updatingMontageSpinners = false;

    // Contrôles du Fond Flouté et de l'Ordre des Calques
    private JCheckBox checkBlurBackground;
    private JSpinner spinnerBlurRadius;
    private JSpinner spinnerBlurOpacity;
    public static class LayerItem {
        public final String id;
        public final String label;
        public LayerItem(String id, String label) {
            this.id = id;
            this.label = label;
        }
        @Override
        public String toString() {
            return label;
        }
    }
    private DefaultListModel<LayerItem> layerListModel;
    private JList<LayerItem> layerList;
    private JButton btnLayerUp;
    private JButton btnLayerDown;

    private final JTabbedPane tabbedPane;
    private final ExportConfig config = new ExportConfig();
    private WatermarkControlGroup watermarkGroupMontage;
    private WatermarkControlGroup watermarkGroupBandeau;
    private boolean isSyncingWatermark = false;

    private void onWatermarkChanged(WatermarkControlGroup source) {
        if (isSyncingWatermark) return;
        isSyncingWatermark = true;
        try {
            source.syncToConfig(config.textOverlay);
            if (source == watermarkGroupMontage && watermarkGroupBandeau != null) {
                watermarkGroupBandeau.syncFromConfig(config.textOverlay);
            } else if (source == watermarkGroupBandeau && watermarkGroupMontage != null) {
                watermarkGroupMontage.syncFromConfig(config.textOverlay);
            }
            if (montageCanvas != null) {
                montageCanvas.setTextOverlay(config.textOverlay);
            }
            if (singleBandPreview != null) {
                singleBandPreview.setTextOverlay(config.textOverlay);
            }
        } finally {
            isSyncingWatermark = false;
        }
    }

    private boolean updatingPreset = false;
    private final ExportPreset customPresetItem;

    private File videoFile;
    private double videoDuration = 60.0;
    private uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private VoiceExclusionTimelinePanel voiceExclusionTimeline;
    private javax.swing.Timer previewPlayTimer;
    private boolean isPreviewPlaying = false;
    private long previewPlayStartSysNs = 0L;
    private double previewPlayStartVidTime = 0.0;
    private final app.services.MediaWorkflowService mediaWorkflowService = new app.services.MediaWorkflowService();
    private volatile long lastSnapshotRequestTime = 0;

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

    public ExportVideoDialog(Frame owner, TimelinePanel timelinePanel, BufferedImage videoSnapshot, File videoFile, double durationSeconds, uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent mediaPlayerComponent) {
        this(owner, timelinePanel, videoSnapshot,
                timelinePanel != null && timelinePanel.getWidth() > 0 ? timelinePanel.getWidth() : 1920,
                timelinePanel != null && timelinePanel.getHeight() > 0 ? timelinePanel.getHeight() : 100,
                videoFile, durationSeconds, mediaPlayerComponent);
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel() {
            super();
        }
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }
        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 22;
        }
        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 66;
        }
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }
        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    public ExportVideoDialog(Frame owner, TimelinePanel timelinePanel, BufferedImage videoSnapshot, int currentScreenWidth, int currentScreenHeight) {
        this(owner, timelinePanel, videoSnapshot, currentScreenWidth, currentScreenHeight, null, 0.0, null);
    }

    public ExportVideoDialog(Frame owner, TimelinePanel timelinePanel, BufferedImage videoSnapshot, int currentScreenWidth, int currentScreenHeight, File videoFile, double durationSeconds, uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent mediaPlayerComponent) {
        super(owner, "Exportation Vidéo & Bande Rythmo", true);
        this.timelinePanel = timelinePanel;
        this.videoSnapshot = videoSnapshot;
        this.videoFile = videoFile;
        this.mediaPlayerComponent = mediaPlayerComponent;
        if (durationSeconds > 0) {
            this.videoDuration = durationSeconds;
        } else if (timelinePanel != null) {
            this.videoDuration = Math.max(10.0, timelinePanel.getCurrentTime() + 60.0);
        } else {
            this.videoDuration = 60.0;
        }
        this.bandCount = (timelinePanel != null) ? Math.max(1, timelinePanel.getBandCount()) : 1;

        int initW = currentScreenWidth > 0 ? currentScreenWidth : 1920;
        if (initW % 2 != 0) initW++;
        int initH = computeExportBandHeight(bandCount, (currentScreenHeight > 0 && currentScreenHeight <= 300) ? currentScreenHeight : 100);
        if (initH % 2 != 0) initH++;
        this.customPresetItem = new ExportPreset("Personnalisé (modifié)", initW, initH, 8.0, 60, true);

        setLayout(new BorderLayout());
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int dialogW = Math.min(1340, Math.max(1050, screenSize.width - 60));
        int dialogH = Math.min(880, Math.max(740, screenSize.height - 70));
        setPreferredSize(new Dimension(dialogW, dialogH));
        setSize(new Dimension(dialogW, dialogH));
        setMinimumSize(new Dimension(980, 680));
        setResizable(true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopPreviewPlayback();
                config.approved = false;
                dispose();
            }
        });

        getContentPane().setBackground(OmeTheme.bg());
        tabbedPane = new JTabbedPane();
        OmeTheme.styleTabbedPane(tabbedPane);

        // ==========================================
        // ONGLET 1 : BANDEAU SEUL (CLASSIQUE)
        // ==========================================
        JPanel tabBandeauPanel = new JPanel(new BorderLayout(10, 10));
        tabBandeauPanel.setBackground(OmeTheme.bg());

        JPanel headerPanel1 = OmeTheme.createDialogHeader(
                "EXPORT BANDEAU",
                "Exportation Bandeau Seul",
                "Exporte la bande rythmo isolée (idéal pour l'incrustation directe en régie ou sous-titrage)."
        );
        tabBandeauPanel.add(headerPanel1, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 8, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblPreset = new JLabel("Préréglage / Format :");
        lblPreset.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        formPanel.add(lblPreset, gbc);

        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        JPanel presetControlPanel = new JPanel(new BorderLayout(6, 0));
        comboPreset = new JComboBox<>(presetModel);
        presetControlPanel.add(comboPreset, BorderLayout.CENTER);

        JPanel presetButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        JButton btnBandeauMobile = new JButton("Mobile 9:16");
        btnBandeauMobile.setToolTipText("Basculer immédiatement en format Mobile 1080×1920 (TikTok, Shorts)");
        btnBandeauMobile.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnBandeauMobile.setForeground(Color.BLACK);
        btnBandeauMobile.addActionListener(e -> applyMobileFormatDirect());
        btnSavePreset = new JButton("Enregistrer...");
        btnSavePreset.setToolTipText("Enregistrer les réglages actuels sous un nouveau nom de préréglage");
        btnSavePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSavePreset.setForeground(Color.BLACK);
        btnRenamePreset = new JButton("Renommer...");
        btnRenamePreset.setToolTipText("Renommer ce préréglage personnalisé");
        btnRenamePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnRenamePreset.setForeground(Color.BLACK);
        btnRenamePreset.setEnabled(false);
        btnRenamePreset.addActionListener(e -> renameSelectedPreset());
        btnDeletePreset = new JButton("Supprimer");
        btnDeletePreset.setToolTipText("Supprimer ce préréglage personnalisé");
        btnDeletePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnDeletePreset.setForeground(Color.BLACK);
        btnDeletePreset.setEnabled(false);
        presetButtonsPanel.add(btnBandeauMobile);
        presetButtonsPanel.add(btnSavePreset);
        presetButtonsPanel.add(btnRenamePreset);
        presetButtonsPanel.add(btnDeletePreset);
        presetControlPanel.add(presetButtonsPanel, BorderLayout.EAST);
        formPanel.add(presetControlPanel, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblWidth = new JLabel("Largeur vidéo (px) :");
        lblWidth.setFont(new Font("Segoe UI", Font.PLAIN, 11));
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
        lblHeight.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        formPanel.add(lblHeight, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        spinnerHeight = new JSpinner(new SpinnerNumberModel(origH, 20, 4320, 2));
        formPanel.add(spinnerHeight, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblSec = new JLabel("Vision anticipée (secondes) :");
        lblSec.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        formPanel.add(lblSec, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        spinnerVisibleSeconds = new JSpinner(new SpinnerNumberModel(8.0, 1.5, 20.0, 0.5));
        formPanel.add(spinnerVisibleSeconds, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblRoleScale = new JLabel("Taille étiquettes des rôles (10% - 200%) :");
        lblRoleScale.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        formPanel.add(lblRoleScale, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        JPanel roleScalePanel = new JPanel(new BorderLayout(6, 0));
        roleScalePanel.setOpaque(false);
        sliderRoleLabelScale = new JSlider(10, 200, 100);
        sliderRoleLabelScale.setOpaque(false);
        sliderRoleLabelScale.setToolTipText("Ajuste la taille des étiquettes des rôles de 10% à 200% (devient translucide en cas de chevauchement sur une phrase)");
        lblRoleLabelScaleVal = new JLabel("100%");
        lblRoleLabelScaleVal.setFont(OmeTheme.fontMono(Font.BOLD, 11));
        lblRoleLabelScaleVal.setForeground(OmeTheme.accent());
        roleScalePanel.add(sliderRoleLabelScale, BorderLayout.CENTER);
        roleScalePanel.add(lblRoleLabelScaleVal, BorderLayout.EAST);
        formPanel.add(roleScalePanel, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblFps = new JLabel("Fluidité (FPS) :");
        lblFps.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        formPanel.add(lblFps, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        comboFps = new JComboBox<>(new String[]{
                "60 FPS — Ultra fluide (Recommandé)",
                "30 FPS — Standard",
                "24 FPS — Cinéma / Doublage"
        });
        comboFps.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboFps.setSelectedIndex(0);
        formPanel.add(comboFps, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblEnc = new JLabel("Accélération :");
        lblEnc.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        formPanel.add(lblEnc, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        comboEncoder = new JComboBox<>(new String[]{
                "Auto (Matériel Détecté / Recommandé)",
                "GPU NVIDIA NVENC (NVIDIA GeForce/RTX)",
                "GPU AMD AMF (AMD Radeon)",
                "GPU Intel QSV (Intel Core & Intel Arc)",
                "CPU Multi-cœurs (Universel — x264 Rapide)"
        });
        comboEncoder.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboEncoder.setSelectedIndex(0);
        formPanel.add(comboEncoder, gbc);

        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.3;
        JLabel lblCpuCores = new JLabel("Cœurs & Puissance :");
        lblCpuCores.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        formPanel.add(lblCpuCores, gbc);
        gbc.gridx = 1; gbc.gridy = row++; gbc.weightx = 0.7;
        comboCpuCores = new JComboBox<>(buildCpuCoreOptions());
        comboCpuCores.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboCpuCores.setSelectedIndex(0);
        formPanel.add(comboCpuCores, gbc);

        gbc.gridx = 0; gbc.gridy = row++; gbc.gridwidth = 2;
        JLabel infoLabel = new JLabel("Vous pouvez créer, nommer et sauvegarder vos configurations personnalisées.");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        infoLabel.setForeground(new Color(130, 185, 235));
        formPanel.add(infoLabel, gbc);

        singleBandPreview = new SingleBandPreviewPanel(timelinePanel, bandCount, origW, origH, 8.0);
        singleBandPreview.setOnWatermarkChanged(() -> {
            if (isSyncingWatermark) return;
            isSyncingWatermark = true;
            try {
                if (watermarkGroupBandeau != null) watermarkGroupBandeau.syncFromConfig(config.textOverlay);
                if (watermarkGroupMontage != null) watermarkGroupMontage.syncFromConfig(config.textOverlay);
                if (montageCanvas != null) montageCanvas.repaint();
            } finally {
                isSyncingWatermark = false;
            }
        });

        watermarkGroupBandeau = new WatermarkControlGroup(() -> onWatermarkChanged(watermarkGroupBandeau));
        JPanel watermarkPanelTab1 = watermarkGroupBandeau.createPanel("Texte / Filigrane Superposé (Watermark)");

        ScrollablePanel bandeauTopPanel = new ScrollablePanel();
        bandeauTopPanel.setLayout(new BoxLayout(bandeauTopPanel, BoxLayout.Y_AXIS));
        bandeauTopPanel.add(formPanel);
        bandeauTopPanel.add(Box.createVerticalStrut(4));
        bandeauTopPanel.add(watermarkPanelTab1);

        JScrollPane scrollBandeauTop = new JScrollPane(bandeauTopPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollBandeauTop.setBorder(null);
        scrollBandeauTop.getVerticalScrollBar().setUnitIncrement(22);

        JPanel centerBandeauPanel = new JPanel(new BorderLayout(8, 8));
        centerBandeauPanel.add(scrollBandeauTop, BorderLayout.CENTER);
        centerBandeauPanel.add(singleBandPreview, BorderLayout.SOUTH);
        tabBandeauPanel.add(centerBandeauPanel, BorderLayout.CENTER);

        // ==========================================
        // ONGLET 2 : MONTAGE VIDÉO + BANDEAU
        // ==========================================
        JPanel tabMontagePanel = createMontageTabPanel();

        tabbedPane.addTab("Bandeau Seul (Format d'origine OmeRyth)", tabBandeauPanel);
        tabbedPane.addTab("Montage Vidéo + Bande (Format d'origine OmeRyth)", tabMontagePanel);
        add(tabbedPane, BorderLayout.CENTER);

        // Initialisation des préréglages
        populatePresets(currentScreenWidth, currentScreenHeight);
        populateMontagePresets();

        if (watermarkGroupMontage != null) watermarkGroupMontage.syncFromConfig(config.textOverlay);
        if (watermarkGroupBandeau != null) watermarkGroupBandeau.syncFromConfig(config.textOverlay);
        if (montageCanvas != null) montageCanvas.setTextOverlay(config.textOverlay);
        if (singleBandPreview != null) singleBandPreview.setTextOverlay(config.textOverlay);

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
        sliderRoleLabelScale.addChangeListener(e -> {
            int val = sliderRoleLabelScale.getValue();
            lblRoleLabelScaleVal.setText(val + "%");
            config.roleLabelScalePercent = val;
            if (singleBandPreview != null) {
                singleBandPreview.setRoleLabelScale(val / 100.0);
            }
            if (!isSyncingRoleLabelScale) {
                isSyncingRoleLabelScale = true;
                try {
                    if (sliderMontageRoleLabelScale != null) {
                        sliderMontageRoleLabelScale.setValue(val);
                    }
                    if (lblMontageRoleLabelScaleVal != null) {
                        lblMontageRoleLabelScaleVal.setText(val + "%");
                    }
                    if (montageCanvas != null) {
                        montageCanvas.setRoleLabelScale(val / 100.0);
                    }
                    onSpinnerChanged();
                } finally {
                    isSyncingRoleLabelScale = false;
                }
            }
        });
        comboFps.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) onSpinnerChanged();
        });

        btnSavePreset.addActionListener(e -> saveCurrentAsPreset());
        btnDeletePreset.addActionListener(e -> deleteSelectedPreset());

        // ==========================================
        // BOUTONS GLOBAUX DU BAS (Annuler / Exporter)
        // ==========================================
        OmeTheme.OmeButton btnCancel = OmeTheme.createGhostButton("Annuler");
        btnCancel.addActionListener(e -> {
            config.approved = false;
            dispose();
        });

        OmeTheme.OmeButton btnExport = OmeTheme.createPrimaryButton("Lancer l'exportation vidéo...");
        btnExport.setFont(OmeTheme.fontSans(Font.BOLD, 13));
        btnExport.addActionListener(e -> onExportConfirmed());

        add(OmeTheme.createDialogFooter(btnCancel, btnExport), BorderLayout.SOUTH);

        applyOmeThemeTree(this);
        pack();
        int finalW = Math.max(dialogW, getWidth());
        int finalH = Math.max(dialogH, getHeight());
        finalW = Math.min(finalW, screenSize.width - 40);
        finalH = Math.min(finalH, screenSize.height - 40);
        setSize(finalW, finalH);
        setLocationRelativeTo(owner);
        updateVoiceRemovalTimelineVisibility();
        applyOmeThemeTree(this);
    }

    /**
     * Applique de manière uniforme le thème OmeRyth aux composants Swing générés dans le dialogue d'export.
     */
    private static void applyOmeThemeTree(Component comp) {
        if (comp == null) return;
        if (comp instanceof SingleBandPreviewPanel || comp instanceof MontagePreviewCanvas
                || comp instanceof VoiceExclusionTimelinePanel) {
            return;
        }
        if (comp instanceof OmeTheme.OmeButton) {
            return;
        }
        if (comp instanceof JPanel panel) {
            if (panel.getBorder() instanceof TitledBorder tb) {
                tb.setTitleFont(OmeTheme.fontMono(Font.BOLD, 11));
                tb.setTitleColor(OmeTheme.accent());
                tb.setBorder(BorderFactory.createLineBorder(OmeTheme.border(), 1));
                panel.setBackground(OmeTheme.bgRaised());
                panel.setOpaque(true);
            } else if (panel.isOpaque() && panel.getBackground() != null) {
                Color bg = panel.getBackground();
                if (bg.equals(new Color(30, 30, 35)) || bg.equals(new Color(24, 24, 27))) {
                    panel.setBackground(OmeTheme.bgRaised());
                } else if (bg.getRed() > 210 && bg.getGreen() > 210 && bg.getBlue() > 210) {
                    panel.setBackground(OmeTheme.bg());
                }
            }
        } else if (comp instanceof JLabel lbl) {
            Color fg = lbl.getForeground();
            if (fg != null) {
                if (fg.equals(new Color(56, 189, 248)) || fg.equals(new Color(130, 185, 235))) {
                    lbl.setForeground(OmeTheme.accent());
                } else if (fg.equals(Color.BLACK) || fg.equals(new Color(51, 51, 51))
                        || (fg.getRed() < 80 && fg.getGreen() < 80 && fg.getBlue() < 80)) {
                    lbl.setForeground(OmeTheme.text());
                } else if (fg.equals(new Color(180, 180, 190)) || fg.equals(new Color(161, 161, 170))
                        || fg.equals(new Color(212, 212, 216))) {
                    lbl.setForeground(OmeTheme.textSecondary());
                }
            }
        } else if (comp instanceof JCheckBox chk) {
            OmeTheme.styleCheckBox(chk);
        } else if (comp instanceof JTextField tf) {
            OmeTheme.styleTextField(tf);
        } else if (comp instanceof JSpinner sp) {
            OmeTheme.styleSpinner(sp);
        } else if (comp instanceof JComboBox<?> cb) {
            OmeTheme.styleComboBox(cb);
        } else if (comp instanceof JSlider sl) {
            sl.setOpaque(false);
            sl.setUI(new OmeTheme.OmeSliderUI(sl));
        } else if (comp instanceof JScrollPane sp) {
            OmeTheme.styleScrollPane(sp);
        } else if (comp instanceof JList<?> list) {
            list.setBackground(OmeTheme.surface());
            list.setForeground(OmeTheme.text());
            list.setSelectionBackground(OmeTheme.accentSubtleSolid());
            list.setSelectionForeground(OmeTheme.accent());
        } else if (comp instanceof JViewport vp) {
            vp.setBackground(OmeTheme.bg());
        } else if (comp instanceof AbstractButton btn) {
            // Préserver les pastilles de couleur (petits carrés avec texte vide ou couleur perso)
            if (btn.getText() != null && !btn.getText().isEmpty()) {
                btn.setFont(OmeTheme.fontSans(Font.BOLD, 11.5f));
                btn.setFocusPainted(false);
                btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                Color bg = btn.getBackground();
                if (bg != null && bg.equals(new Color(245, 158, 11))) {
                    btn.setBackground(OmeTheme.accent());
                    btn.setForeground(OmeTheme.accentTextOn());
                } else {
                    btn.setBackground(OmeTheme.surface());
                    btn.setForeground(OmeTheme.text());
                    btn.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(OmeTheme.borderStrong(), 1),
                            BorderFactory.createEmptyBorder(4, 10, 4, 10)
                    ));
                }
                btn.setOpaque(true);
            }
        }

        if (comp instanceof Container cont) {
            for (Component child : cont.getComponents()) {
                applyOmeThemeTree(child);
            }
        }
    }

    /**
     * Conservé pour compatibilité ; délègue au thème OmeRyth.
     */
    public static void applyBlackButtonText(Component comp) {
        applyOmeThemeTree(comp);
    }

    @Override
    public void setVisible(boolean b) {
        if (b) {
            applyOmeThemeTree(this);
        }
        super.setVisible(b);
    }

    public void setVideoSnapshot(BufferedImage snapshot) {
        this.videoSnapshot = snapshot;
        if (montageCanvas != null) {
            montageCanvas.setVideoSnapshot(snapshot);
        }
    }

    public BufferedImage getVideoSnapshot() {
        return videoSnapshot;
    }

    private JPanel createMontageTabPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(OmeTheme.bg());
        panel.setBorder(new EmptyBorder(0, 0, 8, 0));

        // En-tête
        JPanel headerPanel = OmeTheme.createDialogHeader(
                "ATELIER MONTAGE",
                "Atelier de Composition Vidéo & Bande Rythmo",
                "Déplacez et redimensionnez la Vidéo et la Bande directement à la souris sur la maquette ci-dessous."
        );
        panel.add(headerPanel, BorderLayout.NORTH);

        // Canvas interactif au centre avec barre temporelle d'exclusion vocale en dessous
        JPanel centerContainer = new JPanel(new BorderLayout(0, 4));
        centerContainer.setOpaque(false);

        montageCanvas = new MontagePreviewCanvas();
        montageCanvas.setTimelinePanel(timelinePanel);
        montageCanvas.setBandCount(bandCount);
        montageCanvas.setVideoSnapshot(videoSnapshot);
        if (timelinePanel != null) {
            montageCanvas.setPreviewTime(timelinePanel.getCurrentTime());
        }
        centerContainer.add(montageCanvas, BorderLayout.CENTER);

        voiceExclusionTimeline = new VoiceExclusionTimelinePanel(videoDuration, this::seekPreview, this::togglePreviewPlayback);
        if (timelinePanel != null) {
            voiceExclusionTimeline.setTimelinePanel(timelinePanel);
            voiceExclusionTimeline.setCurrentTime(timelinePanel.getCurrentTime());
        }
        voiceExclusionTimeline.setVisible(false);
        centerContainer.add(voiceExclusionTimeline, BorderLayout.SOUTH);

        panel.add(centerContainer, BorderLayout.CENTER);

        // Panneau latéral droit : Contrôles & Inspecteur (avec défilement fluide et largeur confortable)
        ScrollablePanel controlsPanel = new ScrollablePanel();
        controlsPanel.setLayout(new BoxLayout(controlsPanel, BoxLayout.Y_AXIS));
        controlsPanel.setBorder(new EmptyBorder(0, 4, 8, 4));

        // 0. Préréglages de Montage (Sauvegarde, Renommage, Suppression)
        JPanel montagePresetPanel = new JPanel(new BorderLayout(4, 4));
        montagePresetPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "Préréglages de Montage",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.PLAIN, 11),
                new Color(212, 212, 216)
        ));
        comboMontagePreset = new JComboBox<>(montagePresetModel);
        comboMontagePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboMontagePreset.addActionListener(e -> {
            if (!updatingMontagePreset) {
                MontagePreset sel = (MontagePreset) comboMontagePreset.getSelectedItem();
                if (sel != null && sel != customMontagePresetItem) {
                    applyMontagePreset(sel);
                } else {
                    updateMontagePresetButtonStates();
                }
            }
        });
        montagePresetPanel.add(comboMontagePreset, BorderLayout.CENTER);

        JPanel montagePresetBtns = new JPanel(new GridLayout(1, 3, 4, 0));
        btnSaveMontagePreset = new JButton("Enregistrer...");
        btnSaveMontagePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSaveMontagePreset.setForeground(Color.BLACK);
        btnSaveMontagePreset.setToolTipText("Enregistrer tous les réglages de ce montage sous un nouveau nom");
        btnSaveMontagePreset.addActionListener(e -> saveCurrentMontageAsPreset());

        btnRenameMontagePreset = new JButton("Renommer...");
        btnRenameMontagePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnRenameMontagePreset.setForeground(Color.BLACK);
        btnRenameMontagePreset.setToolTipText("Renommer le préréglage de montage sélectionné");
        btnRenameMontagePreset.setEnabled(false);
        btnRenameMontagePreset.addActionListener(e -> renameSelectedMontagePreset());

        btnDeleteMontagePreset = new JButton("Supprimer");
        btnDeleteMontagePreset.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnDeleteMontagePreset.setForeground(Color.BLACK);
        btnDeleteMontagePreset.setToolTipText("Supprimer ce préréglage de montage");
        btnDeleteMontagePreset.setEnabled(false);
        btnDeleteMontagePreset.addActionListener(e -> deleteSelectedMontagePreset());

        montagePresetBtns.add(btnSaveMontagePreset);
        montagePresetBtns.add(btnRenameMontagePreset);
        montagePresetBtns.add(btnDeleteMontagePreset);
        montagePresetPanel.add(montagePresetBtns, BorderLayout.SOUTH);
        controlsPanel.add(montagePresetPanel);
        controlsPanel.add(Box.createVerticalStrut(4));

        // 1. Résolution d'export
        JPanel resPanel = new JPanel(new GridLayout(0, 1, 3, 3));
        resPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "1. Format & Résolution d'Export",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.PLAIN, 11),
                new Color(212, 212, 216)
        ));

        comboMontageResolution = new JComboBox<>(new String[]{
                "1920 × 1080 (16:9 Paysage Full HD — YouTube)",
                "1080 × 1920 (9:16 Vertical — TikTok, Shorts)",
                "1280 × 720 (16:9 Paysage HD)",
                "1080 × 1080 (1:1 Carré — Instagram)",
                "Personnalisé..."
        });
        comboMontageResolution.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        resPanel.add(comboMontageResolution);

        JPanel dimSpinners = new JPanel(new GridBagLayout());
        GridBagConstraints gbcD = new GridBagConstraints();
        gbcD.insets = new Insets(2, 2, 2, 2);
        gbcD.fill = GridBagConstraints.HORIZONTAL;

        gbcD.gridx = 0; gbcD.weightx = 0;
        JLabel lblL = new JLabel("L :");
        lblL.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dimSpinners.add(lblL, gbcD);

        gbcD.gridx = 1; gbcD.weightx = 0.25;
        spinnerMontageWidth = new JSpinner(new SpinnerNumberModel(1920, 320, 7680, 10));
        dimSpinners.add(spinnerMontageWidth, gbcD);

        gbcD.gridx = 2; gbcD.weightx = 0;
        JLabel lblH = new JLabel("H :");
        lblH.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dimSpinners.add(lblH, gbcD);

        gbcD.gridx = 3; gbcD.weightx = 0.25;
        spinnerMontageHeight = new JSpinner(new SpinnerNumberModel(1080, 240, 4320, 10));
        dimSpinners.add(spinnerMontageHeight, gbcD);

        gbcD.gridx = 4; gbcD.weightx = 0.5;
        JButton btnMontageMobile = new JButton("Mobile 9:16");
        btnMontageMobile.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnMontageMobile.setBackground(new Color(245, 158, 11));
        btnMontageMobile.setForeground(Color.BLACK);
        btnMontageMobile.setOpaque(true);
        btnMontageMobile.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnMontageMobile.setToolTipText("Appliquer le format vertical 1080×1920 pour TikTok / Shorts");
        btnMontageMobile.addActionListener(e -> applyMobileFormatDirect());
        dimSpinners.add(btnMontageMobile, gbcD);

        resPanel.add(dimSpinners);
        controlsPanel.add(resPanel);
        controlsPanel.add(Box.createVerticalStrut(4));

        // 2. Modèles de disposition prédéfinis
        JPanel templatePanel = new JPanel(new GridLayout(0, 1, 3, 3));
        templatePanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "2. Disposition Rapide (Modèles)",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.PLAIN, 11),
                new Color(212, 212, 216)
        ));

        comboMontageTemplate = new JComboBox<>(new String[]{
                "Format d'origine OmeRyth (Vidéo haut, Bande bas)",
                "Plein écran (Vidéo 16:9 intégrale + Bande incrustée)",
                "Grand Bandeau Studio (Vidéo 75% / Bande 25%)",
                "TikTok / Shorts (Vidéo 16:9 centrée + Bande dessous)",
                "Égalitaire (50% Vidéo / 50% Bande)"
        });
        comboMontageTemplate.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        templatePanel.add(comboMontageTemplate);

        JButton btnApplyTemplate = new JButton("Appliquer la disposition");
        btnApplyTemplate.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnApplyTemplate.setForeground(Color.BLACK);
        btnApplyTemplate.addActionListener(e -> applyCurrentTemplate());
        templatePanel.add(btnApplyTemplate);

        controlsPanel.add(templatePanel);
        controlsPanel.add(Box.createVerticalStrut(4));

        // 3. Inspecteur de l'élément sélectionné
        JPanel inspectorPanel = new JPanel(new GridLayout(0, 1, 3, 3));
        inspectorPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "3. Position & Taille de l'Élément Sélectionné",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.PLAIN, 11),
                new Color(212, 212, 216)
        ));

        JPanel togglePanel = new JPanel(new GridLayout(1, 2, 6, 0));
        btnSelectVideo = new JToggleButton("Vidéo source");
        btnSelectVideo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnSelectVideo.setForeground(Color.BLACK);
        btnSelectBand = new JToggleButton("Bande Rythmo");
        btnSelectBand.setFont(new Font("Segoe UI", Font.PLAIN, 11));
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

        JPanel coordPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcC = new GridBagConstraints();
        gbcC.insets = new Insets(2, 3, 2, 3);
        gbcC.fill = GridBagConstraints.HORIZONTAL;

        gbcC.gridy = 0;
        gbcC.gridx = 0; gbcC.weightx = 0;
        JLabel lblX = new JLabel("X :");
        lblX.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        coordPanel.add(lblX, gbcC);

        gbcC.gridx = 1; gbcC.weightx = 0.5;
        spinnerElemX = new JSpinner(new SpinnerNumberModel(0, -2000, 7680, 10));
        coordPanel.add(spinnerElemX, gbcC);

        gbcC.gridx = 2; gbcC.weightx = 0;
        JLabel lblY = new JLabel("Y :");
        lblY.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        coordPanel.add(lblY, gbcC);

        gbcC.gridx = 3; gbcC.weightx = 0.5;
        spinnerElemY = new JSpinner(new SpinnerNumberModel(0, -2000, 4320, 10));
        coordPanel.add(spinnerElemY, gbcC);

        gbcC.gridy = 1;
        gbcC.gridx = 0; gbcC.weightx = 0;
        JLabel lblW = new JLabel("Larg :");
        lblW.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        coordPanel.add(lblW, gbcC);

        gbcC.gridx = 1; gbcC.weightx = 0.5;
        spinnerElemW = new JSpinner(new SpinnerNumberModel(1920, 30, 7680, 10));
        coordPanel.add(spinnerElemW, gbcC);

        gbcC.gridx = 2; gbcC.weightx = 0;
        JLabel lblH2 = new JLabel("Haut :");
        lblH2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        coordPanel.add(lblH2, gbcC);

        gbcC.gridx = 3; gbcC.weightx = 0.5;
        spinnerElemH = new JSpinner(new SpinnerNumberModel(300, 30, 4320, 10));
        coordPanel.add(spinnerElemH, gbcC);

        inspectorPanel.add(coordPanel);

        JPanel alignPanel = new JPanel(new GridLayout(1, 3, 4, 0));
        JButton btnCenterH = new JButton("Centrer Horiz.");
        btnCenterH.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnCenterH.setForeground(Color.BLACK);
        btnCenterH.addActionListener(e -> montageCanvas.centerSelectedHorizontally());

        JButton btnCenterV = new JButton("Centrer Vert.");
        btnCenterV.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnCenterV.setForeground(Color.BLACK);
        btnCenterV.addActionListener(e -> montageCanvas.centerSelectedVertically());

        JButton btnFullW = new JButton("Pleine Largeur");
        btnFullW.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnFullW.setForeground(Color.BLACK);
        btnFullW.addActionListener(e -> montageCanvas.setSelectedFullWidth());

        alignPanel.add(btnCenterH);
        alignPanel.add(btnCenterV);
        alignPanel.add(btnFullW);
        inspectorPanel.add(alignPanel);

        controlsPanel.add(inspectorPanel);
        controlsPanel.add(Box.createVerticalStrut(4));

        // 4. Fond Vidéo Flouté & Ordre des Calques
        JPanel layerPanel = new JPanel();
        layerPanel.setLayout(new BoxLayout(layerPanel, BoxLayout.Y_AXIS));
        layerPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "4. Arrière-Plan Flouté & Calques",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.PLAIN, 11),
                new Color(212, 212, 216)
        ));

        checkBlurBackground = new JCheckBox("Vidéo source floutée en arrière-plan", false);
        checkBlurBackground.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        checkBlurBackground.setForeground(new Color(56, 189, 248));
        checkBlurBackground.setToolTipText("Remplit l'arrière-plan de la vidéo exportée avec une version floutée de la vidéo source.");
        layerPanel.add(checkBlurBackground);

        JPanel blurParamsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel lblFlou = new JLabel("Flou :");
        lblFlou.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        blurParamsPanel.add(lblFlou);
        spinnerBlurRadius = new JSpinner(new SpinnerNumberModel(25, 1, 60, 2));
        spinnerBlurRadius.setPreferredSize(new Dimension(50, 22));
        spinnerBlurRadius.setEnabled(false);
        blurParamsPanel.add(spinnerBlurRadius);
        JLabel lblPx = new JLabel("px");
        lblPx.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        blurParamsPanel.add(lblPx);

        blurParamsPanel.add(Box.createHorizontalStrut(8));
        JLabel lblOp = new JLabel("Opacité :");
        lblOp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        blurParamsPanel.add(lblOp);
        spinnerBlurOpacity = new JSpinner(new SpinnerNumberModel(85, 10, 100, 5));
        spinnerBlurOpacity.setPreferredSize(new Dimension(50, 22));
        spinnerBlurOpacity.setEnabled(false);
        blurParamsPanel.add(spinnerBlurOpacity);
        JLabel lblPct = new JLabel("%");
        lblPct.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        blurParamsPanel.add(lblPct);
        layerPanel.add(blurParamsPanel);

        layerPanel.add(Box.createVerticalStrut(2));
        JLabel lblLayerOrder = new JLabel("Ordre des calques (Haut = Devant / Bas = Derrière) :");
        lblLayerOrder.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblLayerOrder.setForeground(new Color(180, 180, 190));
        layerPanel.add(lblLayerOrder);

        layerListModel = new DefaultListModel<>();
        LayerItem itemWatermark = new LayerItem("WATERMARK", "Filigrane / Texte");
        LayerItem itemBand = new LayerItem("BAND", "Bande Rythmo");
        LayerItem itemVideo = new LayerItem("VIDEO", "Vidéo source");
        LayerItem itemBlur = new LayerItem("BACKGROUND_BLUR", "Fond Vidéo Flouté");
        layerListModel.addElement(itemWatermark);
        layerListModel.addElement(itemBand);
        layerListModel.addElement(itemVideo);
        layerListModel.addElement(itemBlur);

        layerList = new JList<>(layerListModel);
        layerList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        layerList.setSelectedIndex(0);
        layerList.setVisibleRowCount(4);
        layerList.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        JScrollPane layerScroll = new JScrollPane(layerList);
        layerScroll.setPreferredSize(new Dimension(220, 80));

        JPanel layerListContainer = new JPanel(new GridBagLayout());
        GridBagConstraints gbcL = new GridBagConstraints();
        gbcL.fill = GridBagConstraints.BOTH;
        gbcL.insets = new Insets(0, 0, 0, 6);
        gbcL.gridx = 0; gbcL.weightx = 0.72;
        layerListContainer.add(layerScroll, gbcL);

        JPanel layerBtnCol = new JPanel(new GridLayout(2, 1, 0, 3));
        btnLayerUp = new JButton("Monter");
        btnLayerUp.setToolTipText("Avancer ce calque vers le premier plan (au-dessus)");
        btnLayerUp.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLayerUp.setForeground(Color.BLACK);
        btnLayerDown = new JButton("Descendre");
        btnLayerDown.setToolTipText("Reculer ce calque vers l'arrière-plan (en-dessous)");
        btnLayerDown.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnLayerDown.setForeground(Color.BLACK);

        Runnable updateLayerBtnStates = () -> {
            int idx = layerList.getSelectedIndex();
            btnLayerUp.setEnabled(idx > 0);
            btnLayerDown.setEnabled(idx >= 0 && idx < layerListModel.getSize() - 1);
        };

        btnLayerUp.addActionListener(e -> {
            int idx = layerList.getSelectedIndex();
            if (idx > 0) {
                LayerItem item = layerListModel.remove(idx);
                layerListModel.add(idx - 1, item);
                layerList.setSelectedIndex(idx - 1);
                applyLayerOrderToCanvas();
                onMontageParamChanged();
                updateLayerBtnStates.run();
            }
        });

        btnLayerDown.addActionListener(e -> {
            int idx = layerList.getSelectedIndex();
            if (idx >= 0 && idx < layerListModel.getSize() - 1) {
                LayerItem item = layerListModel.remove(idx);
                layerListModel.add(idx + 1, item);
                layerList.setSelectedIndex(idx + 1);
                applyLayerOrderToCanvas();
                onMontageParamChanged();
                updateLayerBtnStates.run();
            }
        });

        layerList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateLayerBtnStates.run();
                LayerItem sel = layerList.getSelectedValue();
                if (sel != null && montageCanvas != null) {
                    if ("VIDEO".equals(sel.id)) {
                        montageCanvas.setSelectedElement(MontagePreviewCanvas.ElementType.VIDEO);
                    } else if ("BAND".equals(sel.id)) {
                        montageCanvas.setSelectedElement(MontagePreviewCanvas.ElementType.BAND);
                    } else if ("WATERMARK".equals(sel.id)) {
                        montageCanvas.setSelectedElement(MontagePreviewCanvas.ElementType.WATERMARK);
                    }
                }
            }
        });

        updateLayerBtnStates.run();

        layerBtnCol.add(btnLayerUp);
        layerBtnCol.add(btnLayerDown);

        gbcL.gridx = 1; gbcL.weightx = 0.28;
        gbcL.insets = new Insets(0, 0, 0, 0);
        layerListContainer.add(layerBtnCol, gbcL);
        layerPanel.add(layerListContainer);

        Runnable updateBlurState = () -> {
            boolean active = checkBlurBackground.isSelected();
            spinnerBlurRadius.setEnabled(active);
            spinnerBlurOpacity.setEnabled(active);
            montageCanvas.setBlurBackgroundVideo(active);
            int r = ((Number) spinnerBlurRadius.getValue()).intValue();
            int op = ((Number) spinnerBlurOpacity.getValue()).intValue();
            montageCanvas.setBlurRadius(r);
            montageCanvas.setBlurOpacity(op);
        };
        checkBlurBackground.addActionListener(e -> updateBlurState.run());
        spinnerBlurRadius.addChangeListener(e -> updateBlurState.run());
        spinnerBlurOpacity.addChangeListener(e -> updateBlurState.run());

        controlsPanel.add(layerPanel);
        controlsPanel.add(Box.createVerticalStrut(4));

        // 5. Options d'encodage et suppression vocale
        JPanel optionsPanel = new JPanel(new GridLayout(0, 1, 2, 2));
        optionsPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(63, 63, 70)),
                "5. Audio, Doublage & Qualité",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.PLAIN, 11),
                new Color(212, 212, 216)
        ));

        checkMontageRemoveVocals = new JCheckBox("Retirer les voix (IA Demucs - Conserver musique)", false);
        checkMontageRemoveVocals.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        checkMontageRemoveVocals.setForeground(new Color(245, 158, 11));
        checkMontageRemoveVocals.setToolTipText("Supprime les dialogues via le réseau de neurones IA Demucs pour permettre aux comédiens de doubler par-dessus.");
        checkMontageRemoveVocals.addActionListener(e -> {
            updateVoiceRemovalTimelineVisibility();
            onMontageParamChanged();
        });
        optionsPanel.add(checkMontageRemoveVocals);

        JPanel antiCopyrightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        checkAntiCopyright = new JCheckBox("Filtre Anti-Copyright :", false);
        checkAntiCopyright.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        checkAntiCopyright.setForeground(new Color(56, 189, 248));
        checkAntiCopyright.setToolTipText("Applique un léger filtre blanc semi-transparent sur la vidéo source.");

        spinnerAntiCopyrightOpacity = new JSpinner(new SpinnerNumberModel(20, 0, 100, 5));
        spinnerAntiCopyrightOpacity.setPreferredSize(new Dimension(50, 22));
        spinnerAntiCopyrightOpacity.setEnabled(false);

        JLabel lblPercent = new JLabel("% d'opacité (voile)");
        lblPercent.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        antiCopyrightPanel.add(checkAntiCopyright);
        antiCopyrightPanel.add(spinnerAntiCopyrightOpacity);
        antiCopyrightPanel.add(lblPercent);
        optionsPanel.add(antiCopyrightPanel);

        checkMirrorVideo = new JCheckBox("Inverser l'image vidéo (Miroir horizontal)", false);
        checkMirrorVideo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        checkMirrorVideo.setForeground(new Color(56, 189, 248));
        checkMirrorVideo.setToolTipText("Inverse horizontalement la vidéo et le fond flouté (effet miroir anti-copyright), sans toucher à la bande rythmo.");
        checkMirrorVideo.addActionListener(e -> {
            montageCanvas.setMirrorVideo(checkMirrorVideo.isSelected());
            onMontageParamChanged();
        });
        optionsPanel.add(checkMirrorVideo);

        Runnable updateAntiCopyright = () -> {
            boolean active = checkAntiCopyright.isSelected();
            spinnerAntiCopyrightOpacity.setEnabled(active);
            int op = ((Number) spinnerAntiCopyrightOpacity.getValue()).intValue();
            montageCanvas.setAntiCopyright(active, op);
        };
        checkAntiCopyright.addActionListener(e -> updateAntiCopyright.run());
        spinnerAntiCopyrightOpacity.addChangeListener(e -> updateAntiCopyright.run());

        JPanel optRow = new JPanel(new GridBagLayout());
        GridBagConstraints gbcO = new GridBagConstraints();
        gbcO.insets = new Insets(1, 2, 1, 4);
        gbcO.fill = GridBagConstraints.HORIZONTAL;

        gbcO.gridx = 0; gbcO.weightx = 0;
        JLabel lblFpsM = new JLabel("FPS :");
        lblFpsM.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        optRow.add(lblFpsM, gbcO);

        gbcO.gridx = 1; gbcO.weightx = 0.45;
        comboMontageFps = new JComboBox<>(new String[]{"60 FPS (Fluide)", "30 FPS", "24 FPS"});
        comboMontageFps.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        optRow.add(comboMontageFps, gbcO);

        gbcO.gridx = 2; gbcO.weightx = 0;
        JLabel lblVis = new JLabel("Vision :");
        lblVis.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        optRow.add(lblVis, gbcO);

        gbcO.gridx = 3; gbcO.weightx = 0.35;
        spinnerMontageVisibleSeconds = new JSpinner(new SpinnerNumberModel(8.0, 1.5, 20.0, 0.5));
        optRow.add(spinnerMontageVisibleSeconds, gbcO);

        gbcO.gridx = 4; gbcO.weightx = 0;
        JLabel lblS = new JLabel("s");
        lblS.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        optRow.add(lblS, gbcO);
        optionsPanel.add(optRow);

        JPanel roleScaleMontageRow = new JPanel(new BorderLayout(6, 0));
        JLabel lblRoleScaleM = new JLabel("Taille étiquettes rôles :");
        lblRoleScaleM.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        roleScaleMontageRow.add(lblRoleScaleM, BorderLayout.WEST);
        sliderMontageRoleLabelScale = new JSlider(10, 200, 100);
        sliderMontageRoleLabelScale.setOpaque(false);
        sliderMontageRoleLabelScale.setToolTipText("Ajuste la taille des étiquettes des rôles de 10% à 200% (devient translucide en cas de chevauchement sur une phrase)");
        lblMontageRoleLabelScaleVal = new JLabel("100%");
        lblMontageRoleLabelScaleVal.setFont(new Font("Consolas", Font.BOLD, 11));
        lblMontageRoleLabelScaleVal.setForeground(new Color(56, 189, 248));
        roleScaleMontageRow.add(sliderMontageRoleLabelScale, BorderLayout.CENTER);
        roleScaleMontageRow.add(lblMontageRoleLabelScaleVal, BorderLayout.EAST);
        sliderMontageRoleLabelScale.addChangeListener(e -> {
            int val = sliderMontageRoleLabelScale.getValue();
            lblMontageRoleLabelScaleVal.setText(val + "%");
            config.roleLabelScalePercent = val;
            if (montageCanvas != null) {
                montageCanvas.setRoleLabelScale(val / 100.0);
            }
            if (!isSyncingRoleLabelScale) {
                isSyncingRoleLabelScale = true;
                try {
                    if (sliderRoleLabelScale != null) {
                        sliderRoleLabelScale.setValue(val);
                    }
                    if (lblRoleLabelScaleVal != null) {
                        lblRoleLabelScaleVal.setText(val + "%");
                    }
                    if (singleBandPreview != null) {
                        singleBandPreview.setRoleLabelScale(val / 100.0);
                    }
                    onMontageParamChanged();
                } finally {
                    isSyncingRoleLabelScale = false;
                }
            }
        });
        optionsPanel.add(roleScaleMontageRow);

        JPanel encRow = new JPanel(new BorderLayout(6, 0));
        JLabel lblEncM = new JLabel("Accélération :");
        lblEncM.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        encRow.add(lblEncM, BorderLayout.WEST);
        comboMontageEncoder = new JComboBox<>(new String[]{
                "Auto (Matériel Détecté / Recommandé)",
                "GPU NVIDIA NVENC (NVIDIA GeForce/RTX)",
                "GPU AMD AMF (AMD Radeon)",
                "GPU Intel QSV (Intel Core & Arc)",
                "CPU Multi-cœurs (Universel — x264)"
        });
        comboMontageEncoder.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboMontageEncoder.setSelectedIndex(0);
        encRow.add(comboMontageEncoder, BorderLayout.CENTER);
        optionsPanel.add(encRow);

        JPanel cpuRow = new JPanel(new BorderLayout(6, 0));
        JLabel lblCpuM = new JLabel("Cœurs CPU :");
        lblCpuM.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cpuRow.add(lblCpuM, BorderLayout.WEST);
        comboMontageCpuCores = new JComboBox<>(buildCpuCoreOptions());
        comboMontageCpuCores.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        comboMontageCpuCores.setSelectedIndex(0);
        cpuRow.add(comboMontageCpuCores, BorderLayout.CENTER);
        optionsPanel.add(cpuRow);

        controlsPanel.add(optionsPanel);
        controlsPanel.add(Box.createVerticalStrut(4));

        watermarkGroupMontage = new WatermarkControlGroup(() -> onWatermarkChanged(watermarkGroupMontage));
        JPanel watermarkPanelTab2 = watermarkGroupMontage.createPanel("6. Texte / Filigrane Superposé (Watermark)");
        controlsPanel.add(watermarkPanelTab2);
        controlsPanel.add(Box.createVerticalStrut(4));
        JScrollPane scrollControls = new JScrollPane(controlsPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollControls.setBorder(null);
        scrollControls.setPreferredSize(new Dimension(520, 720));
        scrollControls.setMinimumSize(new Dimension(460, 300));
        scrollControls.getVerticalScrollBar().setUnitIncrement(22);
        SwingUtilities.invokeLater(() -> scrollControls.getVerticalScrollBar().setValue(0));
        panel.add(scrollControls, BorderLayout.EAST);

        // Écouteurs pour synchroniser le canevas et les contrôles
        montageCanvas.setOnLayoutChanged(this::syncMontageSpinnersFromCanvas);
        montageCanvas.setOnWatermarkChanged(() -> {
            if (isSyncingWatermark) return;
            isSyncingWatermark = true;
            try {
                if (watermarkGroupMontage != null) watermarkGroupMontage.syncFromConfig(config.textOverlay);
                if (watermarkGroupBandeau != null) watermarkGroupBandeau.syncFromConfig(config.textOverlay);
                if (singleBandPreview != null) singleBandPreview.repaint();
                onMontageParamChanged();
            } finally {
                isSyncingWatermark = false;
            }
        });

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
            onMontageParamChanged();
        });

        spinnerMontageHeight.addChangeListener(e -> {
            int w = (Integer) spinnerMontageWidth.getValue();
            int h = (Integer) spinnerMontageHeight.getValue();
            montageCanvas.setExportResolution(w, h);
            onMontageParamChanged();
        });

        spinnerElemX.addChangeListener(e -> onElementSpinnerChanged());
        spinnerElemY.addChangeListener(e -> onElementSpinnerChanged());
        spinnerElemW.addChangeListener(e -> onElementSpinnerChanged());
        spinnerElemH.addChangeListener(e -> onElementSpinnerChanged());

        spinnerMontageVisibleSeconds.addChangeListener(e -> {
            double sec = ((Number) spinnerMontageVisibleSeconds.getValue()).doubleValue();
            montageCanvas.setVisibleSeconds(sec);
            onMontageParamChanged();
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

    private void applyLayerOrderToCanvas() {
        if (montageCanvas == null || layerListModel == null) return;
        java.util.List<String> order = new java.util.ArrayList<>();
        // L'UI affiche du haut (devant) vers le bas (derrière).
        // Le moteur de rendu dessine de l'index 0 (fond) au dernier index (premier plan).
        // On inverse donc l'ordre pour le rendu.
        for (int i = layerListModel.getSize() - 1; i >= 0; i--) {
            order.add(layerListModel.getElementAt(i).id);
        }
        montageCanvas.setLayerOrder(order);
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
            if (layerList != null && layerListModel != null) {
                for (int i = 0; i < layerListModel.getSize(); i++) {
                    if ("VIDEO".equals(layerListModel.get(i).id)) {
                        layerList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } else if (sel == MontagePreviewCanvas.ElementType.WATERMARK) {
            if (layerList != null && layerListModel != null) {
                for (int i = 0; i < layerListModel.getSize(); i++) {
                    if ("WATERMARK".equals(layerListModel.get(i).id)) {
                        layerList.setSelectedIndex(i);
                        break;
                    }
                }
            }
        } else {
            btnSelectBand.setSelected(true);
            Rectangle r = montageCanvas.getBandRect();
            spinnerElemX.setValue(r.x);
            spinnerElemY.setValue(r.y);
            spinnerElemW.setValue(r.width);
            spinnerElemH.setValue(r.height);
            if (layerList != null && layerListModel != null) {
                for (int i = 0; i < layerListModel.getSize(); i++) {
                    if ("BAND".equals(layerListModel.get(i).id)) {
                        layerList.setSelectedIndex(i);
                        break;
                    }
                }
            }
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
        onMontageParamChanged();
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
            if (config.removeVocals && voiceExclusionTimeline != null) {
                config.voiceKeepZones = voiceExclusionTimeline.getNormalizedZones();
                config.voiceCutMode = voiceExclusionTimeline.isVoiceCutMode();
            } else {
                config.voiceKeepZones = new java.util.ArrayList<>();
                config.voiceCutMode = true;
            }
            config.antiCopyright = (checkAntiCopyright != null && checkAntiCopyright.isSelected());
            config.antiCopyrightOpacity = (spinnerAntiCopyrightOpacity != null)
                    ? ((Number) spinnerAntiCopyrightOpacity.getValue()).intValue()
                    : 20;
            config.encoder = switch (comboMontageEncoder.getSelectedIndex()) {
                case 1 -> "nvenc";
                case 2 -> "amf";
                case 3 -> "qsv";
                case 4 -> "cpu";
                default -> "auto";
            };
            config.cpuThreads = getThreadsFromCpuIndex(comboMontageCpuCores.getSelectedIndex());
            config.blurBackgroundVideo = (checkBlurBackground != null && checkBlurBackground.isSelected());
            config.blurRadius = (spinnerBlurRadius != null) ? ((Number) spinnerBlurRadius.getValue()).intValue() : 25;
            config.blurOpacity = (spinnerBlurOpacity != null) ? ((Number) spinnerBlurOpacity.getValue()).intValue() : 85;
            config.mirrorVideo = (checkMirrorVideo != null && checkMirrorVideo.isSelected());
            config.roleLabelScalePercent = (sliderMontageRoleLabelScale != null) ? sliderMontageRoleLabelScale.getValue() : 100;
            java.util.List<String> order = new java.util.ArrayList<>();
            if (layerListModel != null) {
                for (int i = layerListModel.getSize() - 1; i >= 0; i--) {
                    order.add(layerListModel.getElementAt(i).id);
                }
            } else {
                order.addAll(java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND"));
            }
            config.layerOrder = order;
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
            config.voiceKeepZones = new java.util.ArrayList<>();
            config.voiceCutMode = true;
            config.antiCopyright = false;
            config.antiCopyrightOpacity = 0;
            config.mirrorVideo = false;
            config.roleLabelScalePercent = (sliderRoleLabelScale != null) ? sliderRoleLabelScale.getValue() : 100;
            config.encoder = switch (comboEncoder.getSelectedIndex()) {
                case 1 -> "nvenc";
                case 2 -> "amf";
                case 3 -> "qsv";
                case 4 -> "cpu";
                default -> "auto";
            };
            config.cpuThreads = getThreadsFromCpuIndex(comboCpuCores.getSelectedIndex());
        }

        if (isMontage) {
            if (watermarkGroupMontage != null) {
                watermarkGroupMontage.syncToConfig(config.textOverlay);
            }
        } else {
            if (watermarkGroupBandeau != null) {
                watermarkGroupBandeau.syncToConfig(config.textOverlay);
            }
        }

        config.approved = true;
        dispose();
    }

    @Override
    public void dispose() {
        stopPreviewPlayback();
        super.dispose();
    }

    private void updateVoiceRemovalTimelineVisibility() {
        boolean active = (checkMontageRemoveVocals != null && checkMontageRemoveVocals.isSelected());
        if (voiceExclusionTimeline != null) {
            voiceExclusionTimeline.setVisible(active);
            if (!active && isPreviewPlaying) {
                stopPreviewPlayback();
            }
            Container parent = voiceExclusionTimeline.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }
    }

    private void togglePreviewPlayback() {
        if (isPreviewPlaying) {
            stopPreviewPlayback();
        } else {
            startPreviewPlayback();
        }
    }

    private void startPreviewPlayback() {
        if (isPreviewPlaying) return;
        isPreviewPlaying = true;
        if (voiceExclusionTimeline != null) {
            voiceExclusionTimeline.setPlaying(true);
        }

        double curT = (voiceExclusionTimeline != null) ? voiceExclusionTimeline.getCurrentTime() : 0.0;
        if (curT >= videoDuration - 0.1) {
            curT = 0.0;
            if (voiceExclusionTimeline != null) {
                voiceExclusionTimeline.setCurrentTime(0.0);
            }
            seekPreview(0.0);
        }

        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                var mp = mediaPlayerComponent.mediaPlayer();
                long targetMs = (long) Math.round(curT * 1000.0);
                var state = mp.status().state();
                if (state == uk.co.caprica.vlcj.player.base.State.ENDED || state == uk.co.caprica.vlcj.player.base.State.STOPPED) {
                    if (videoFile != null) {
                        mp.media().play(videoFile.getAbsolutePath(), ":start-time=" + curT, ":file-caching=150", ":clock-jitter=0");
                    } else {
                        mp.controls().play();
                        mp.controls().setTime(targetMs);
                    }
                } else {
                    long vlcTime = mp.status().time();
                    long diffMs = (vlcTime >= 0) ? Math.abs(vlcTime - targetMs) : Long.MAX_VALUE;
                    if (diffMs > 650 || vlcTime < 0) {
                        mp.controls().setTime(targetMs);
                    }
                    if (state == uk.co.caprica.vlcj.player.base.State.PAUSED) {
                        mp.controls().setPause(false);
                    } else {
                        mp.controls().play();
                    }
                    javax.swing.Timer ensurePlayTimer = new javax.swing.Timer(45, ev -> {
                        if (isPreviewPlaying && mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
                            try {
                                var m = mediaPlayerComponent.mediaPlayer();
                                if (!m.status().isPlaying()) {
                                    m.controls().setPause(false);
                                    m.controls().play();
                                }
                            } catch (Throwable ignored2) {}
                        }
                    });
                    ensurePlayTimer.setRepeats(false);
                    ensurePlayTimer.start();
                }
            } catch (Throwable ignored) {}
        }

        if (previewPlayTimer != null) {
            previewPlayTimer.stop();
        }
        previewPlayStartSysNs = System.nanoTime();
        previewPlayStartVidTime = curT;

        previewPlayTimer = new javax.swing.Timer(16, evt -> {
            if (!isPreviewPlaying) return;
            long nowNs = System.nanoTime();
            double elapsed = (nowNs - previewPlayStartSysNs) / 1_000_000_000.0;
            double nowT = previewPlayStartVidTime + elapsed;

            if (nowT >= videoDuration) {
                nowT = videoDuration;
                stopPreviewPlayback();
            }

            if (voiceExclusionTimeline != null) {
                voiceExclusionTimeline.setCurrentTime(nowT);
            }
            if (montageCanvas != null) {
                montageCanvas.setPreviewTime(nowT);
            }
            if (singleBandPreview != null && singleBandPreview.isShowing()) {
                singleBandPreview.setPreviewTime(nowT);
            }
        });
        previewPlayTimer.setCoalesce(true);
        previewPlayTimer.start();
    }

    private void stopPreviewPlayback() {
        isPreviewPlaying = false;
        if (voiceExclusionTimeline != null) {
            double cur = voiceExclusionTimeline.getCurrentTime();
            double snapped = Math.round(cur * 10.0) / 10.0;
            voiceExclusionTimeline.setCurrentTime(snapped);
            if (montageCanvas != null) {
                montageCanvas.setPreviewTime(snapped);
            }
            if (singleBandPreview != null && singleBandPreview.isShowing()) {
                singleBandPreview.setPreviewTime(snapped);
            }
            voiceExclusionTimeline.setPlaying(false);
        }
        if (previewPlayTimer != null) {
            previewPlayTimer.stop();
            previewPlayTimer = null;
        }
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                mediaPlayerComponent.mediaPlayer().controls().setPause(true);
            } catch (Throwable ignored) {}
        }
    }

    private void seekPreview(double targetSec) {
        targetSec = Math.max(0.0, Math.min(videoDuration, targetSec));
        if (isPreviewPlaying) {
            previewPlayStartSysNs = System.nanoTime();
            previewPlayStartVidTime = targetSec;
        }
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                mediaPlayerComponent.mediaPlayer().controls().setTime((long) Math.round(targetSec * 1000.0));
            } catch (Throwable ignored) {}
        }
        if (montageCanvas != null) {
            montageCanvas.setPreviewTime(targetSec);
        }
        if (singleBandPreview != null && singleBandPreview.isShowing()) {
            singleBandPreview.setPreviewTime(targetSec);
        }
        if (!isPreviewPlaying && videoFile != null) {
            requestPreviewSnapshot(targetSec);
        }
    }

    private void requestPreviewSnapshot(double targetSec) {
        if (videoFile == null || !videoFile.exists()) return;
        long reqId = System.currentTimeMillis();
        lastSnapshotRequestTime = reqId;
        javax.swing.Timer debounce = new javax.swing.Timer(120, e -> {
            if (lastSnapshotRequestTime == reqId) {
                Thread worker = new Thread(() -> {
                    try {
                        BufferedImage snap = mediaWorkflowService.capturePreviewFrame(videoFile, targetSec, mediaPlayerComponent);
                        if (snap != null && lastSnapshotRequestTime == reqId) {
                            SwingUtilities.invokeLater(() -> {
                                if (montageCanvas != null) {
                                    montageCanvas.setVideoSnapshot(snap);
                                }
                            });
                        }
                    } catch (Throwable ignored) {}
                }, "ExportPreviewScrubWorker");
                worker.setDaemon(true);
                worker.start();
            }
        });
        debounce.setRepeats(false);
        debounce.start();
    }

    private void populatePresets(int currentScreenWidth, int currentScreenHeight) {
        updatingPreset = true;
        presetModel.removeAllElements();

        int origW = currentScreenWidth > 0 ? currentScreenWidth : 1920;
        if (origW % 2 != 0) origW++;
        int origH = computeExportBandHeight(bandCount, (currentScreenHeight > 0 && currentScreenHeight <= 300) ? currentScreenHeight : 100);
        if (origH % 2 != 0) origH++;

        ExportPreset defaultPreset = new ExportPreset(
                "Format adapté OmeRyth (" + origW + " × " + origH + " — " + bandCount + " bande" + (bandCount > 1 ? "s" : "") + ")",
                origW, origH, 8.0, 60, true
        );
        presetModel.addElement(defaultPreset);

        int stdH = computeExportBandHeight(bandCount, 100);
        presetModel.addElement(new ExportPreset("Bandeau Standard (1920 × " + stdH + " — Vision 8s)", 1920, stdH, 8.0, 60, true));
        presetModel.addElement(new ExportPreset("Format Mobile Plein Écran (1080 × 1920 — 9:16)", 1080, 1920, 6.0, 60, true));
        int mobH = computeExportBandHeight(bandCount, 180);
        presetModel.addElement(new ExportPreset("Bandeau Mobile Réseaux (1080 × " + mobH + " — Vision 6s)", 1080, mobH, 6.0, 60, true));
        int stuH = computeExportBandHeight(bandCount, 200);
        presetModel.addElement(new ExportPreset("Grand Bandeau Studio (1920 × " + stuH + " — Vision 6s)", 1920, stuH, 6.0, 60, true));
        presetModel.addElement(new ExportPreset("Full HD 1080p Plein écran (1920 × 1080 — Vision 8s)", 1920, 1080, 8.0, 60, true));
        presetModel.addElement(new ExportPreset("HD 720p (1280 × 720 — Vision 6s)", 1280, 720, 6.0, 60, true));

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
        isSyncingRoleLabelScale = true;
        try {
            spinnerWidth.setValue(p.width);
            spinnerHeight.setValue(p.height);
            spinnerVisibleSeconds.setValue(p.visibleSeconds);
            comboFps.setSelectedIndex(p.fps == 24 ? 2 : (p.fps == 30 ? 1 : 0));
            int scalePct = Math.max(10, Math.min(200, p.roleLabelScalePercent));
            config.roleLabelScalePercent = scalePct;
            if (sliderRoleLabelScale != null) sliderRoleLabelScale.setValue(scalePct);
            if (lblRoleLabelScaleVal != null) lblRoleLabelScaleVal.setText(scalePct + "%");
            if (sliderMontageRoleLabelScale != null) sliderMontageRoleLabelScale.setValue(scalePct);
            if (lblMontageRoleLabelScaleVal != null) lblMontageRoleLabelScaleVal.setText(scalePct + "%");
            if (montageCanvas != null) montageCanvas.setRoleLabelScale(scalePct / 100.0);
        } finally {
            isSyncingRoleLabelScale = false;
            updatingPreset = false;
        }
        if (singleBandPreview != null) {
            singleBandPreview.setRoleLabelScale(p.roleLabelScalePercent / 100.0);
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
        btnRenamePreset.setEnabled(isCustomUserPreset);
    }

    private void renameSelectedPreset() {
        ExportPreset sel = (ExportPreset) comboPreset.getSelectedItem();
        if (sel == null || sel.isBuiltin || sel == customPresetItem) return;

        String oldName = sel.name.replaceFirst("^(\\u2B50|\\[Perso\\]|Custom - )\\s*", "");
        String newName = (String) JOptionPane.showInputDialog(
                this,
                "Entrez un nouveau nom pour ce préréglage :",
                "Renommer le préréglage",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                oldName
        );

        if (newName == null || newName.trim().isEmpty() || newName.trim().equals(oldName)) {
            return;
        }

        String finalName = "[Perso] " + newName.trim();
        ExportPreset renamed = new ExportPreset(finalName, sel.width, sel.height, sel.visibleSeconds, sel.fps, sel.roleLabelScalePercent, false);
        int idx = presetModel.getIndexOf(sel);
        if (idx >= 0) {
            presetModel.removeElementAt(idx);
            presetModel.insertElementAt(renamed, idx);
            saveAllCustomPresetsToFile();
            updatingPreset = true;
            comboPreset.setSelectedItem(renamed);
            updatingPreset = false;
            updateButtonStates();
        }
    }

    private void populateMontagePresets() {
        updatingMontagePreset = true;
        montagePresetModel.removeAllElements();

        MontagePreset pOrig = new MontagePreset("Format d'origine OmeRyth", 1920, 1080,
                new Rectangle(0, 0, 1920, 780), new Rectangle(0, 780, 1920, 300),
                "OMERYTH_ORIGINAL", false, false, 20, false, 25, 85, 60, 8.0, "auto",
                java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND", "WATERMARK"), true);
        montagePresetModel.addElement(pOrig);

        MontagePreset pTiktok = new MontagePreset("TikTok / Shorts 9:16 (Flou + Bande)", 1080, 1920,
                new Rectangle(0, 420, 1080, 608), new Rectangle(0, 1040, 1080, 240),
                "TIKTOK_CENTER_9_16", false, false, 20, true, 25, 85, 60, 6.0, "auto",
                java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND", "WATERMARK"), true);
        montagePresetModel.addElement(pTiktok);

        MontagePreset pYoutube = new MontagePreset("YouTube / Cinéma 16:9 (Plein écran)", 1920, 1080,
                new Rectangle(0, 0, 1920, 1080), new Rectangle(0, 840, 1920, 240),
                "FULLSCREEN_16_9", false, false, 20, false, 25, 85, 60, 8.0, "auto",
                java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND", "WATERMARK"), true);
        montagePresetModel.addElement(pYoutube);

        MontagePreset pStudio = new MontagePreset("Grand Bandeau Studio (75% Vidéo / 25% Bande)", 1920, 1080,
                new Rectangle(0, 0, 1920, 810), new Rectangle(0, 810, 1920, 270),
                "STUDIO_LARGE_BAND", false, false, 20, false, 25, 85, 60, 8.0, "auto",
                java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND", "WATERMARK"), true);
        montagePresetModel.addElement(pStudio);

        MontagePreset pCarre = new MontagePreset("Format Carré 1:1 (Réseaux)", 1080, 1080,
                new Rectangle(0, 0, 1080, 540), new Rectangle(0, 540, 1080, 540),
                "STACKED_TOP_BOTTOM", false, false, 20, false, 25, 85, 60, 6.0, "auto",
                java.util.Arrays.asList("BACKGROUND_BLUR", "VIDEO", "BAND", "WATERMARK"), true);
        montagePresetModel.addElement(pCarre);

        for (MontagePreset custom : loadCustomMontagePresetsFromFile()) {
            montagePresetModel.addElement(custom);
        }

        this.customMontagePresetItem = new MontagePreset("Personnalisé (modifié)", 1920, 1080,
                null, null, "CUSTOM", false, false, 20, false, 25, 85, 60, 8.0, "auto", null, true);
        montagePresetModel.addElement(customMontagePresetItem);

        comboMontagePreset.setSelectedIndex(0);
        updatingMontagePreset = false;
        updateMontagePresetButtonStates();
    }

    private void applyMontagePreset(MontagePreset p) {
        if (p == null) return;
        updatingMontagePreset = true;
        updatingMontageSpinners = true;

        spinnerMontageWidth.setValue(p.width);
        spinnerMontageHeight.setValue(p.height);
        montageCanvas.setExportResolution(p.width, p.height);

        if (p.videoRect != null) montageCanvas.setVideoRect(p.videoRect.x, p.videoRect.y, p.videoRect.width, p.videoRect.height);
        if (p.bandRect != null) montageCanvas.setBandRect(p.bandRect.x, p.bandRect.y, p.bandRect.width, p.bandRect.height);

        checkMontageRemoveVocals.setSelected(p.removeVocals);
        updateVoiceRemovalTimelineVisibility();
        checkAntiCopyright.setSelected(p.antiCopyright);
        spinnerAntiCopyrightOpacity.setEnabled(p.antiCopyright);
        spinnerAntiCopyrightOpacity.setValue(p.antiCopyrightOpacity);

        if (checkMirrorVideo != null) {
            checkMirrorVideo.setSelected(p.mirrorVideo);
        }
        if (montageCanvas != null) {
            montageCanvas.setMirrorVideo(p.mirrorVideo);
        }

        checkBlurBackground.setSelected(p.blurBackground);
        spinnerBlurRadius.setEnabled(p.blurBackground);
        spinnerBlurRadius.setValue(p.blurRadius);
        spinnerBlurOpacity.setEnabled(p.blurBackground);
        spinnerBlurOpacity.setValue(p.blurOpacity);
        montageCanvas.setBlurBackgroundVideo(p.blurBackground);
        montageCanvas.setBlurRadius(p.blurRadius);
        montageCanvas.setBlurOpacity(p.blurOpacity);

        comboMontageFps.setSelectedIndex(p.fps == 24 ? 2 : (p.fps == 30 ? 1 : 0));
        spinnerMontageVisibleSeconds.setValue(p.visibleSeconds);

        if (p.encoder != null) {
            switch (p.encoder.toLowerCase()) {
                case "nvenc" -> comboMontageEncoder.setSelectedIndex(1);
                case "amf" -> comboMontageEncoder.setSelectedIndex(2);
                case "qsv" -> comboMontageEncoder.setSelectedIndex(3);
                case "cpu" -> comboMontageEncoder.setSelectedIndex(4);
                default -> comboMontageEncoder.setSelectedIndex(0);
            }
        }

        if (p.layerOrder != null && !p.layerOrder.isEmpty()) {
            layerListModel.clear();
            boolean hasWatermarkLayer = false;
            // Dans p.layerOrder, les calques sont stockés de l'arrière (index 0) vers l'avant (dernier index).
            // L'UI affiche du haut (devant) vers le bas (derrière). On insère donc de l'avant vers l'arrière.
            for (int i = p.layerOrder.size() - 1; i >= 0; i--) {
                String lid = p.layerOrder.get(i);
                if ("WATERMARK".equals(lid)) {
                    layerListModel.addElement(new LayerItem("WATERMARK", "Filigrane / Texte"));
                    hasWatermarkLayer = true;
                } else if ("BAND".equals(lid)) {
                    layerListModel.addElement(new LayerItem("BAND", "Bande Rythmo"));
                } else if ("VIDEO".equals(lid)) {
                    layerListModel.addElement(new LayerItem("VIDEO", "Vidéo source"));
                } else if ("BACKGROUND_BLUR".equals(lid)) {
                    layerListModel.addElement(new LayerItem("BACKGROUND_BLUR", "Fond Vidéo Flouté"));
                }
            }
            if (!hasWatermarkLayer) {
                layerListModel.add(0, new LayerItem("WATERMARK", "Filigrane / Texte"));
            }
            applyLayerOrderToCanvas();
        }

        if (p.textOverlay != null) {
            config.textOverlay = p.textOverlay.copy();
            if (watermarkGroupMontage != null) watermarkGroupMontage.syncFromConfig(config.textOverlay);
            if (watermarkGroupBandeau != null) watermarkGroupBandeau.syncFromConfig(config.textOverlay);
            if (montageCanvas != null) montageCanvas.setTextOverlay(config.textOverlay);
            if (singleBandPreview != null) singleBandPreview.setTextOverlay(config.textOverlay);
        }

        int scalePct = Math.max(10, Math.min(200, p.roleLabelScalePercent > 0 ? p.roleLabelScalePercent : 100));
        isSyncingRoleLabelScale = true;
        try {
            config.roleLabelScalePercent = scalePct;
            if (sliderMontageRoleLabelScale != null) sliderMontageRoleLabelScale.setValue(scalePct);
            if (lblMontageRoleLabelScaleVal != null) lblMontageRoleLabelScaleVal.setText(scalePct + "%");
            if (sliderRoleLabelScale != null) sliderRoleLabelScale.setValue(scalePct);
            if (lblRoleLabelScaleVal != null) lblRoleLabelScaleVal.setText(scalePct + "%");
            if (montageCanvas != null) montageCanvas.setRoleLabelScale(scalePct / 100.0);
            if (singleBandPreview != null) singleBandPreview.setRoleLabelScale(scalePct / 100.0);
        } finally {
            isSyncingRoleLabelScale = false;
        }

        syncMontageSpinnersFromCanvas();
        updatingMontageSpinners = false;
        updatingMontagePreset = false;
        updateMontagePresetButtonStates();
    }

    private void onMontageParamChanged() {
        if (!updatingMontagePreset) {
            updatingMontagePreset = true;
            if (customMontagePresetItem != null) {
                comboMontagePreset.setSelectedItem(customMontagePresetItem);
            }
            updatingMontagePreset = false;
            updateMontagePresetButtonStates();
        }
    }

    private void saveCurrentMontageAsPreset() {
        int w = (Integer) spinnerMontageWidth.getValue();
        int h = (Integer) spinnerMontageHeight.getValue();
        Rectangle vr = montageCanvas.getVideoRect();
        Rectangle br = montageCanvas.getBandRect();
        String tName = (String) comboMontageTemplate.getSelectedItem();
        boolean remVoc = checkMontageRemoveVocals.isSelected();
        boolean antiC = checkAntiCopyright.isSelected();
        int antiCopac = ((Number) spinnerAntiCopyrightOpacity.getValue()).intValue();
        boolean blur = checkBlurBackground.isSelected();
        int blurR = ((Number) spinnerBlurRadius.getValue()).intValue();
        int blurOp = ((Number) spinnerBlurOpacity.getValue()).intValue();
        int fps = switch (comboMontageFps.getSelectedIndex()) {
            case 1 -> 30;
            case 2 -> 24;
            default -> 60;
        };
        double sec = ((Number) spinnerMontageVisibleSeconds.getValue()).doubleValue();
        String enc = switch (comboMontageEncoder.getSelectedIndex()) {
            case 1 -> "nvenc";
            case 2 -> "amf";
            case 3 -> "qsv";
            case 4 -> "cpu";
            default -> "auto";
        };
        java.util.List<String> layers = new ArrayList<>();
        for (int i = layerListModel.getSize() - 1; i >= 0; i--) layers.add(layerListModel.get(i).id);

        String defaultName = w + "x" + h + " (" + sec + "s, " + fps + "fps)";
        String name = (String) JOptionPane.showInputDialog(
                this,
                "Entrez un nom pour votre préréglage de montage :",
                "Enregistrer un nouveau préréglage de montage",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                defaultName
        );

        if (name == null || name.trim().isEmpty()) return;

        String finalName = "[Perso] " + name.trim();
        TextOverlayConfig wmCfg = (config.textOverlay != null) ? config.textOverlay.copy() : new TextOverlayConfig();
        boolean mirror = (checkMirrorVideo != null && checkMirrorVideo.isSelected());
        MontagePreset newPreset = new MontagePreset(finalName, w, h, vr, br, tName, remVoc, antiC, antiCopac, blur, blurR, blurOp, fps, sec, enc, layers, wmCfg, mirror, false);
        newPreset.roleLabelScalePercent = (sliderMontageRoleLabelScale != null) ? sliderMontageRoleLabelScale.getValue() : 100;

        int insertIdx = montagePresetModel.getSize() - 1;
        montagePresetModel.insertElementAt(newPreset, Math.max(0, insertIdx));
        saveAllCustomMontagePresetsToFile();

        updatingMontagePreset = true;
        comboMontagePreset.setSelectedItem(newPreset);
        updatingMontagePreset = false;
        updateMontagePresetButtonStates();

        JOptionPane.showMessageDialog(this,
                "Préréglage de montage \"" + finalName + "\" enregistré avec succès !",
                "Préréglage Enregistré",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void renameSelectedMontagePreset() {
        MontagePreset sel = (MontagePreset) comboMontagePreset.getSelectedItem();
        if (sel == null || sel.isBuiltin || sel == customMontagePresetItem) return;

        String oldName = sel.name.replaceFirst("^(\\u2B50|\\[Perso\\]|Custom - )\\s*", "");
        String newName = (String) JOptionPane.showInputDialog(
                this,
                "Entrez un nouveau nom pour ce préréglage de montage :",
                "Renommer le préréglage de montage",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                oldName
        );

        if (newName == null || newName.trim().isEmpty() || newName.trim().equals(oldName)) return;

        String finalName = "[Perso] " + newName.trim();
        sel.name = finalName;
        saveAllCustomMontagePresetsToFile();
        comboMontagePreset.repaint();
        updateMontagePresetButtonStates();
    }

    private void deleteSelectedMontagePreset() {
        MontagePreset sel = (MontagePreset) comboMontagePreset.getSelectedItem();
        if (sel == null || sel.isBuiltin || sel == customMontagePresetItem) return;

        int response = JOptionPane.showConfirmDialog(
                this,
                "Voulez-vous vraiment supprimer le préréglage de montage \"" + sel.name + "\" ?",
                "Supprimer le préréglage de montage",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
        );

        if (response == JOptionPane.YES_OPTION) {
            montagePresetModel.removeElement(sel);
            saveAllCustomMontagePresetsToFile();
            comboMontagePreset.setSelectedIndex(0);
            updateMontagePresetButtonStates();
        }
    }

    private void updateMontagePresetButtonStates() {
        MontagePreset sel = (MontagePreset) comboMontagePreset.getSelectedItem();
        boolean isCustomUser = (sel != null && !sel.isBuiltin && sel != customMontagePresetItem);
        btnDeleteMontagePreset.setEnabled(isCustomUser);
        btnRenameMontagePreset.setEnabled(isCustomUser);
    }

    private ArrayList<MontagePreset> loadCustomMontagePresetsFromFile() {
        ArrayList<MontagePreset> list = new ArrayList<>();
        File file = new File(MONTAGE_PRESETS_FILE);
        if (!file.exists()) return list;

        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            props.load(reader);
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith("montage_preset.")) {
                    String raw = props.getProperty(key, "");
                    String[] parts = raw.split("\\|", -1);
                    if (parts.length >= 15) {
                        String name = parts[0];
                        int w = Integer.parseInt(parts[1]);
                        int h = Integer.parseInt(parts[2]);
                        String[] vrParts = parts[3].split(",");
                        Rectangle vr = new Rectangle(Integer.parseInt(vrParts[0]), Integer.parseInt(vrParts[1]), Integer.parseInt(vrParts[2]), Integer.parseInt(vrParts[3]));
                        String[] brParts = parts[4].split(",");
                        Rectangle br = new Rectangle(Integer.parseInt(brParts[0]), Integer.parseInt(brParts[1]), Integer.parseInt(brParts[2]), Integer.parseInt(brParts[3]));
                        String template = parts[5];
                        boolean remVoc = Boolean.parseBoolean(parts[6]);
                        boolean antiC = Boolean.parseBoolean(parts[7]);
                        int antiCopac = Integer.parseInt(parts[8]);
                        boolean blur = Boolean.parseBoolean(parts[9]);
                        int blurR = Integer.parseInt(parts[10]);
                        int blurOp = Integer.parseInt(parts[11]);
                        int fps = Integer.parseInt(parts[12]);
                        double sec = Double.parseDouble(parts[13]);
                        String enc = parts[14];
                        java.util.List<String> layers = new ArrayList<>();
                        if (parts.length > 15 && !parts[15].isEmpty()) {
                            layers.addAll(java.util.Arrays.asList(parts[15].split(",")));
                        }
                        TextOverlayConfig wmCfg = new TextOverlayConfig();
                        if (parts.length > 16 && !parts[16].trim().isEmpty()) {
                            wmCfg = deserializeTextOverlay(parts[16]);
                        }
                        boolean mirror = false;
                        if (parts.length > 17 && !parts[17].trim().isEmpty()) {
                            mirror = Boolean.parseBoolean(parts[17].trim());
                        }
                        MontagePreset mp = new MontagePreset(name, w, h, vr, br, template, remVoc, antiC, antiCopac, blur, blurR, blurOp, fps, sec, enc, layers, wmCfg, mirror, false);
                        if (parts.length > 18 && !parts[18].trim().isEmpty()) {
                            try {
                                mp.roleLabelScalePercent = Math.max(10, Math.min(200, Integer.parseInt(parts[18].trim())));
                            } catch (NumberFormatException ignored) {}
                        }
                        list.add(mp);
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Failed to load custom montage presets: " + ex.getMessage());
        }
        return list;
    }

    private void saveAllCustomMontagePresetsToFile() {
        Properties props = new Properties();
        int count = 0;
        for (int i = 0; i < montagePresetModel.getSize(); i++) {
            MontagePreset p = montagePresetModel.getElementAt(i);
            if (p != null && !p.isBuiltin && p != customMontagePresetItem) {
                String vrStr = p.videoRect.x + "," + p.videoRect.y + "," + p.videoRect.width + "," + p.videoRect.height;
                String brStr = p.bandRect.x + "," + p.bandRect.y + "," + p.bandRect.width + "," + p.bandRect.height;
                String layersStr = (p.layerOrder != null) ? String.join(",", p.layerOrder) : "";
                String wmStr = serializeTextOverlay(p.textOverlay);
                String val = p.name + "|" + p.width + "|" + p.height + "|" + vrStr + "|" + brStr + "|" +
                        p.templateName + "|" + p.removeVocals + "|" + p.antiCopyright + "|" + p.antiCopyrightOpacity + "|" +
                        p.blurBackground + "|" + p.blurRadius + "|" + p.blurOpacity + "|" + p.fps + "|" +
                        p.visibleSeconds + "|" + p.encoder + "|" + layersStr + "|" + wmStr + "|" + p.mirrorVideo + "|" + p.roleLabelScalePercent;
                props.setProperty("montage_preset." + (count++), val);
            }
        }

        File file = new File(MONTAGE_PRESETS_FILE);
        try (OutputStream out = new FileOutputStream(file);
             Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            props.store(writer, "OmeRyth Custom Montage Export Presets");
        } catch (Exception ex) {
            System.err.println("Failed to save custom montage presets: " + ex.getMessage());
        }
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

        String finalName = "[Perso] " + name.trim();
        int roleScalePct = (sliderRoleLabelScale != null) ? sliderRoleLabelScale.getValue() : 100;
        ExportPreset newPreset = new ExportPreset(finalName, w, h, sec, fps, roleScalePct, false);

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
                        int roleScalePct = 100;
                        if (parts.length >= 6 && !parts[5].trim().isEmpty()) {
                            try {
                                roleScalePct = Math.max(10, Math.min(200, Integer.parseInt(parts[5].trim())));
                            } catch (NumberFormatException ignored) {}
                        }
                        list.add(new ExportPreset(name, w, h, sec, fps, roleScalePct, false));
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
                String val = p.name + "|" + p.width + "|" + p.height + "|" + p.visibleSeconds + "|" + p.fps + "|" + p.roleLabelScalePercent;
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

    public JSlider getSliderRoleLabelScale() {
        return sliderRoleLabelScale;
    }

    public JSlider getSliderMontageRoleLabelScale() {
        return sliderMontageRoleLabelScale;
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
        private double roleLabelScale = 1.0;
        private double previewTime = 0.0;
        private TimelinePanel.ExportSession cachedSession = null;
        private int cachedSessionW = -1;
        private int cachedSessionH = -1;
        private double cachedSessionSec = -1.0;
        private BufferedImage cachedImage = null;
        private int cachedW = -1;
        private int cachedH = -1;
        private double cachedSec = -1;
        private double cachedTime = -1;
        private TextOverlayConfig textOverlayConfig;
        private Runnable onWatermarkChanged;
        private boolean isDraggingWm = false;
        private Point wmDragStart = new Point();
        private double wmStartPosX = 50.0;
        private double wmStartPosY = 50.0;
        private int lastDrawX = 0, lastDrawY = 0, lastDrawW = 100, lastDrawH = 40;

        private final JSlider timeSlider;
        private final JLabel timeLabel;

        public void setOnWatermarkChanged(Runnable callback) {
            this.onWatermarkChanged = callback;
        }

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
                    "Aperçu Graphique Réel de la Bande Rythmo",
                    TitledBorder.LEFT, TitledBorder.TOP,
                    new Font("Segoe UI", Font.PLAIN, 11),
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
                    lastDrawX = drawX;
                    lastDrawY = drawY;
                    lastDrawW = drawW;
                    lastDrawH = drawH;

                    // Ombre portée / Fond du conteneur
                    g2.setColor(new Color(15, 15, 18));
                    g2.fillRoundRect(drawX - 2, drawY - 2, drawW + 4, drawH + 4, 6, 6);

                    // Rendu ou cache de la bande réelle
                    boolean drawnReal = false;
                    if (timelinePanel != null && drawW > 20 && drawH > 10) {
                        if (cachedSession == null || cachedSessionW != drawW || cachedSessionH != drawH ||
                                Math.abs(cachedSessionSec - visibleSeconds) > 0.05) {
                            cachedSessionW = drawW;
                            cachedSessionH = drawH;
                            cachedSessionSec = visibleSeconds;
                            try {
                                cachedSession = timelinePanel.createExportSession(drawW, drawH, visibleSeconds, roleLabelScale);
                            } catch (Exception ex) {
                                cachedSession = null;
                            }
                        }
                        if (cachedSession != null) {
                            cachedSession.setRoleLabelScale(roleLabelScale);
                            Graphics2D g2Sub = (Graphics2D) g2.create(drawX, drawY, drawW, drawH);
                            try {
                                g2Sub.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                                g2Sub.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                                cachedSession.renderFrameDirect(g2Sub, previewTime);
                                drawnReal = true;
                            } catch (Exception ex) {
                                drawnReal = false;
                            } finally {
                                g2Sub.dispose();
                            }
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

                    // Rendu du texte / filigrane superposé si activé
                    if (textOverlayConfig != null && textOverlayConfig.enabled) {
                        Graphics2D g2Overlay = (Graphics2D) g2.create(drawX, drawY, drawW, drawH);
                        try {
                            g2Overlay.scale(scale, scale);
                            drawTextOverlay(g2Overlay, textOverlayConfig, targetW, targetH);
                        } finally {
                            g2Overlay.dispose();
                        }
                    }

                    // Bordure or / dorée stylisée
                    g2.setColor(new Color(245, 158, 11, 200));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawRoundRect(drawX, drawY, drawW, drawH, 2, 2);

                    // Badge info en haut à gauche
                    String info = targetW + " × " + targetH + " px (" + bandCount + " bande" + (bandCount > 1 ? "s" : "") + ") — Vision : " + visibleSeconds + "s";
                    g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
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
            java.awt.event.MouseAdapter wmMouse = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    if (textOverlayConfig != null && textOverlayConfig.enabled) {
                        isDraggingWm = true;
                        wmDragStart = e.getPoint();
                        wmStartPosX = textOverlayConfig.getEffectivePosX();
                        wmStartPosY = textOverlayConfig.getEffectivePosY();
                        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    }
                }
                @Override
                public void mouseReleased(java.awt.event.MouseEvent e) {
                    isDraggingWm = false;
                    canvas.setCursor(Cursor.getDefaultCursor());
                }
                @Override
                public void mouseDragged(java.awt.event.MouseEvent e) {
                    if (isDraggingWm && textOverlayConfig != null && textOverlayConfig.enabled) {
                        double dxPct = ((e.getX() - wmDragStart.x) / (double) Math.max(1, lastDrawW)) * 100.0;
                        double dyPct = ((e.getY() - wmDragStart.y) / (double) Math.max(1, lastDrawH)) * 100.0;
                        textOverlayConfig.setExactPosition(wmStartPosX + dxPct, wmStartPosY + dyPct);
                        canvas.repaint();
                        if (onWatermarkChanged != null) onWatermarkChanged.run();
                    }
                }
            };
            canvas.addMouseListener(wmMouse);
            canvas.addMouseMotionListener(wmMouse);
            canvas.addMouseWheelListener(e -> {
                if (textOverlayConfig != null && textOverlayConfig.enabled) {
                    double step = e.isShiftDown() ? 1.0 : 5.0;
                    double newAngle = textOverlayConfig.angle + (e.getWheelRotation() > 0 ? step : -step);
                    while (newAngle > 180.0) newAngle -= 360.0;
                    while (newAngle < -180.0) newAngle += 360.0;
                    textOverlayConfig.angle = Math.round(newAngle * 10.0) / 10.0;
                    canvas.repaint();
                    if (onWatermarkChanged != null) onWatermarkChanged.run();
                }
            });
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
            timeLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
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
            this.cachedSession = null;
            this.cachedImage = null;
            repaint();
        }

        public void setBandCount(int count) {
            this.bandCount = Math.max(1, count);
            this.cachedSession = null;
            this.cachedImage = null;
            repaint();
        }

        public void setPreviewTime(double time) {
            this.previewTime = Math.max(0.0, time);
            this.timeLabel.setText(formatTime(previewTime));
            repaint();
        }

        public void setRoleLabelScale(double scale) {
            this.roleLabelScale = Math.max(0.10, Math.min(2.00, scale));
            if (cachedSession != null) {
                cachedSession.setRoleLabelScale(this.roleLabelScale);
            }
            repaint();
        }

        public double getRoleLabelScale() {
            return roleLabelScale;
        }

        public void setTextOverlay(TextOverlayConfig cfg) {
            this.textOverlayConfig = cfg;
            repaint();
        }

        public TextOverlayConfig getTextOverlay() {
            return textOverlayConfig;
        }
    }
}
