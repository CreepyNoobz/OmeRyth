package app.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Système de design centralisé d'OmeRyth, aligné sur l'identité visuelle
 * d'ometitnoobz.github.io/Web :
 * surfaces plates sombres/claires, bordures hairline 1px, typographie géométrique
 * + monospace technique, et accent vert studio (#2fbe55).
 */
public final class OmeTheme {

    private OmeTheme() {}

    private static boolean darkMode = true;

    private static final String SANS_FAMILY;
    private static final String MONO_FAMILY;
    private static BufferedImage cachedLogo;

    static {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()
        ));
        if (available.contains("Plus Jakarta Sans")) {
            SANS_FAMILY = "Plus Jakarta Sans";
        } else if (available.contains("Segoe UI")) {
            SANS_FAMILY = "Segoe UI";
        } else {
            SANS_FAMILY = Font.SANS_SERIF;
        }

        if (available.contains("JetBrains Mono")) {
            MONO_FAMILY = "JetBrains Mono";
        } else if (available.contains("Consolas")) {
            MONO_FAMILY = "Consolas";
        } else {
            MONO_FAMILY = Font.MONOSPACED;
        }
    }

    public static boolean isDark() {
        return darkMode;
    }

    public static void setDark(boolean dark) {
        darkMode = dark;
    }

    public static void syncFromCustomization(AppCustomization c) {
        if (c == null) {
            darkMode = true;
            return;
        }
        Color ref = c.mediaBackground != null ? c.mediaBackground : c.timerBackground;
        if (ref == null) {
            darkMode = true;
            return;
        }
        double luma = (0.2126 * ref.getRed() + 0.7152 * ref.getGreen() + 0.0722 * ref.getBlue()) / 255.0;
        darkMode = luma < 0.48;
    }

    // =========================================================================
    // TOKENS COULEURS (ometitnoobz.github.io/Web)
    // =========================================================================

    public static Color bg() {
        return darkMode ? new Color(0x0A0B0D) : new Color(0xFAFAF8);
    }

    public static Color bgRaised() {
        return darkMode ? new Color(0x101216) : new Color(0xFFFFFF);
    }

    public static Color surface() {
        return darkMode ? new Color(0x15171C) : new Color(0xF2F2F0);
    }

    public static Color surfaceHover() {
        return darkMode ? new Color(0x1B1E24) : new Color(0xEBEBE8);
    }

    public static Color border() {
        return darkMode ? new Color(0x23262D) : new Color(0xE1E3E6);
    }

    public static Color borderStrong() {
        return darkMode ? new Color(0x363A44) : new Color(0xC5C8CE);
    }

    public static Color gridLine() {
        return darkMode ? new Color(255, 255, 255, 9) : new Color(10, 12, 15, 11);
    }

    public static Color text() {
        return darkMode ? new Color(0xECEEEF) : new Color(0x14161A);
    }

    public static Color textSecondary() {
        return darkMode ? new Color(0x9A9FA8) : new Color(0x5A5E66);
    }

    public static Color textTertiary() {
        return darkMode ? new Color(0x62666E) : new Color(0x8B8F96);
    }

    public static Color accent() {
        return darkMode ? new Color(0x2FBE55) : new Color(0x219944);
    }

    public static Color accentStrong() {
        return darkMode ? new Color(0x26A047) : new Color(0x1A7D37);
    }

    public static Color accentTextOn() {
        return darkMode ? new Color(0x07170C) : Color.WHITE;
    }

    public static Color accentSubtle() {
        return darkMode ? new Color(47, 190, 85, 28) : new Color(33, 153, 68, 22);
    }

    public static Color accentSubtleSolid() {
        return darkMode ? new Color(0x14261B) : new Color(0xE6F5EA);
    }

    public static Color danger() {
        return darkMode ? new Color(0xFF453A) : new Color(0xD93025);
    }

    public static Color dangerSubtleSolid() {
        return darkMode ? new Color(0x2A1416) : new Color(0xFCE8E6);
    }

    public static Color warning() {
        return darkMode ? new Color(0xFF9F0A) : new Color(0xD97706);
    }

    public static Color info() {
        return darkMode ? new Color(0x38BDF8) : new Color(0x0284C7);
    }

    // =========================================================================
    // TYPOGRAPHIE
    // =========================================================================

    public static Font fontSans(int style, float size) {
        return new Font(SANS_FAMILY, style, Math.round(size));
    }

    public static Font fontMono(int style, float size) {
        return new Font(MONO_FAMILY, style, Math.round(size));
    }

    public static String getSansFamily() {
        return SANS_FAMILY;
    }

    public static String getMonoFamily() {
        return MONO_FAMILY;
    }

    // =========================================================================
    // LOGO
    // =========================================================================

    public static BufferedImage getLogoImage() {
        if (cachedLogo != null) return cachedLogo;
        try {
            java.net.URL url = OmeTheme.class.getResource("/images/logo.png");
            if (url != null) {
                cachedLogo = ImageIO.read(url);
                return cachedLogo;
            }
        } catch (Throwable ignored) {}
        try {
            File f = new File("src/images/logo.png");
            if (!f.exists()) f = new File("images/logo.png");
            if (f.exists()) {
                cachedLogo = ImageIO.read(f);
                return cachedLogo;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static ImageIcon getLogoIcon(int size) {
        BufferedImage img = getLogoImage();
        if (img == null) return null;
        return new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
    }

    // =========================================================================
    // UIMANAGER GLOBAL DEFAULTS
    // =========================================================================

    public static void installGlobalUIDefaults() {
        Color bg = bg();
        Color raised = bgRaised();
        Color surface = surface();
        Color hover = surfaceHover();
        Color text = text();
        Color textSec = textSecondary();
        Color border = border();
        Color borderStrong = borderStrong();
        Color accent = accent();
        Color accentText = accentTextOn();

        UIManager.put("Panel.background", bg);
        UIManager.put("Panel.foreground", text);
        UIManager.put("Label.foreground", text);
        UIManager.put("Label.font", fontSans(Font.PLAIN, 13));

        // Menus & Popups (délégation BasicUI pour contourner uxtheme natif Windows blanc)
        UIManager.put("MenuBarUI", "javax.swing.plaf.basic.BasicMenuBarUI");
        UIManager.put("MenuUI", "javax.swing.plaf.basic.BasicMenuUI");
        UIManager.put("MenuItemUI", "javax.swing.plaf.basic.BasicMenuItemUI");
        UIManager.put("CheckBoxMenuItemUI", "javax.swing.plaf.basic.BasicCheckBoxMenuItemUI");
        UIManager.put("PopupMenuUI", "javax.swing.plaf.basic.BasicPopupMenuUI");
        UIManager.put("PopupMenuSeparatorUI", "javax.swing.plaf.basic.BasicPopupMenuSeparatorUI");

        UIManager.put("MenuBar.background", raised);
        UIManager.put("MenuBar.foreground", text);
        UIManager.put("MenuBar.border", BorderFactory.createMatteBorder(0, 0, 1, 0, border));
        UIManager.put("Menu.background", raised);
        UIManager.put("Menu.foreground", text);
        UIManager.put("Menu.selectionBackground", hover);
        UIManager.put("Menu.selectionForeground", accent);
        UIManager.put("Menu.font", fontSans(Font.PLAIN, 13));

        UIManager.put("PopupMenu.background", raised);
        UIManager.put("PopupMenu.foreground", text);
        UIManager.put("PopupMenu.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderStrong, 1),
                new EmptyBorder(4, 4, 4, 4)
        ));

        UIManager.put("MenuItem.background", raised);
        UIManager.put("MenuItem.foreground", text);
        UIManager.put("MenuItem.selectionBackground", accentSubtleSolid());
        UIManager.put("MenuItem.selectionForeground", accent);
        UIManager.put("MenuItem.acceleratorForeground", textSec);
        UIManager.put("MenuItem.acceleratorSelectionForeground", accent);
        UIManager.put("MenuItem.font", fontSans(Font.PLAIN, 13));

        UIManager.put("CheckBoxMenuItem.background", raised);
        UIManager.put("CheckBoxMenuItem.foreground", text);
        UIManager.put("CheckBoxMenuItem.selectionBackground", accentSubtleSolid());
        UIManager.put("CheckBoxMenuItem.selectionForeground", accent);
        UIManager.put("CheckBoxMenuItem.font", fontSans(Font.PLAIN, 13));

        UIManager.put("Separator.foreground", border);
        UIManager.put("Separator.background", raised);

        // OptionPane & Dialogs
        UIManager.put("OptionPane.background", raised);
        UIManager.put("OptionPane.messageForeground", text);
        UIManager.put("OptionPane.messageFont", fontSans(Font.PLAIN, 13));
        UIManager.put("OptionPane.buttonFont", fontSans(Font.BOLD, 12));

        // Tooltips
        UIManager.put("ToolTip.background", surface);
        UIManager.put("ToolTip.foreground", text);
        UIManager.put("ToolTip.font", fontSans(Font.PLAIN, 12));
        UIManager.put("ToolTip.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderStrong, 1),
                new EmptyBorder(4, 8, 4, 8)
        ));

        // Inputs
        UIManager.put("TextField.background", surface);
        UIManager.put("TextField.foreground", text);
        UIManager.put("TextField.caretForeground", accent);
        UIManager.put("TextField.selectionBackground", accent);
        UIManager.put("TextField.selectionForeground", accentText);

        UIManager.put("TextArea.background", surface);
        UIManager.put("TextArea.foreground", text);
        UIManager.put("TextArea.caretForeground", accent);
        UIManager.put("TextArea.selectionBackground", accent);
        UIManager.put("TextArea.selectionForeground", accentText);

        UIManager.put("ComboBox.background", surface);
        UIManager.put("ComboBox.foreground", text);
        UIManager.put("ComboBox.selectionBackground", accentSubtleSolid());
        UIManager.put("ComboBox.selectionForeground", accent);

        UIManager.put("List.background", raised);
        UIManager.put("List.foreground", text);
        UIManager.put("List.selectionBackground", accentSubtleSolid());
        UIManager.put("List.selectionForeground", accent);

        UIManager.put("CheckBox.background", bg);
        UIManager.put("CheckBox.foreground", text);
        UIManager.put("RadioButton.background", bg);
        UIManager.put("RadioButton.foreground", text);

        UIManager.put("ScrollPane.background", bg);
        UIManager.put("Viewport.background", bg);

        UIManager.put("TabbedPane.background", bg);
        UIManager.put("TabbedPane.foreground", text);
        UIManager.put("TabbedPane.selected", surface);
        UIManager.put("TabbedPane.contentAreaColor", bg);
        UIManager.put("TabbedPane.focus", accent);

        UIManager.put("ProgressBar.background", surface);
        UIManager.put("ProgressBar.foreground", accent);
        UIManager.put("ProgressBar.selectionBackground", text);
        UIManager.put("ProgressBar.selectionForeground", accentText);

        UIManager.put("Table.background", raised);
        UIManager.put("Table.foreground", text);
        UIManager.put("Table.gridColor", border);
        UIManager.put("Table.selectionBackground", accentSubtleSolid());
        UIManager.put("Table.selectionForeground", text);
        UIManager.put("TableHeader.background", surface);
        UIManager.put("TableHeader.foreground", textSec);

        UIManager.put("Button.background", surface);
        UIManager.put("Button.foreground", text);
        UIManager.put("Button.focus", new Color(0, 0, 0, 0));
    }

    // =========================================================================
    // COMPOSANTS RÉUTILISABLES (Boutons, Cartes, Headers, Badges)
    // =========================================================================

    public enum ButtonVariant {
        PRIMARY,
        SECONDARY,
        GHOST,
        DANGER
    }

    public static class OmeButton extends JButton {
        private ButtonVariant variant;
        private boolean hovered = false;
        private boolean pressed = false;
        private int radius = 6;

        public OmeButton(String text, ButtonVariant variant) {
            super(text);
            this.variant = variant;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(fontSans(variant == ButtonVariant.PRIMARY ? Font.BOLD : Font.PLAIN, 13));
            setMargin(new Insets(8, 16, 8, 16));

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    if (isEnabled()) {
                        hovered = true;
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovered = false;
                    pressed = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (isEnabled() && SwingUtilities.isLeftMouseButton(e)) {
                        pressed = true;
                        repaint();
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
        }

        public void setVariant(ButtonVariant variant) {
            this.variant = variant;
            repaint();
        }

        public void setRadius(int radius) {
            this.radius = radius;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int yOff = pressed ? 1 : 0;

            Color fill = null;
            Color stroke = null;
            Color fg;

            if (!isEnabled()) {
                fill = surface();
                stroke = border();
                fg = textTertiary();
            } else {
                switch (variant) {
                    case PRIMARY -> {
                        fill = hovered ? accentStrong() : accent();
                        fg = accentTextOn();
                    }
                    case SECONDARY -> {
                        fill = hovered ? surfaceHover() : surface();
                        stroke = hovered ? accent() : borderStrong();
                        fg = hovered ? accent() : text();
                    }
                    case DANGER -> {
                        fill = hovered ? dangerSubtleSolid() : surface();
                        stroke = hovered ? danger() : borderStrong();
                        fg = danger();
                    }
                    default -> {
                        fill = hovered ? surfaceHover() : null;
                        stroke = hovered ? border() : null;
                        fg = hovered ? text() : textSecondary();
                    }
                }
            }

            if (fill != null) {
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Double(0.5, 0.5 + yOff, w - 1.0, h - 1.0 - yOff, radius, radius));
            }
            if (stroke != null) {
                g2.setColor(stroke);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Double(0.5, 0.5 + yOff, w - 1.0, h - 1.0 - yOff, radius, radius));
            }

            setForeground(fg);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static OmeButton createPrimaryButton(String text) {
        return new OmeButton(text, ButtonVariant.PRIMARY);
    }

    public static OmeButton createSecondaryButton(String text) {
        return new OmeButton(text, ButtonVariant.SECONDARY);
    }

    public static OmeButton createGhostButton(String text) {
        return new OmeButton(text, ButtonVariant.GHOST);
    }

    public static OmeButton createDangerButton(String text) {
        return new OmeButton(text, ButtonVariant.DANGER);
    }

    public static OmeButton createToolbarButton(String text, String tooltip, java.awt.event.ActionListener listener) {
        OmeButton btn = new OmeButton(text, ButtonVariant.SECONDARY);
        btn.setFocusable(false);
        btn.setFont(fontSans(Font.PLAIN, 12));
        btn.setMargin(new Insets(5, 11, 5, 11));
        btn.setRadius(5);
        if (tooltip != null) btn.setToolTipText(tooltip);
        if (listener != null) btn.addActionListener(listener);
        return btn;
    }

    /**
     * Crée un badge monospace style `.trust-icon` ou `.section-tag`.
     */
    public static JLabel createTagBadge(String text, boolean solidAccent) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(solidAccent ? accent() : accentSubtleSolid());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 4, 4);
                if (!solidAccent) {
                    g2.setColor(border());
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 4, 4);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setOpaque(false);
        label.setFont(fontMono(Font.BOLD, 10));
        label.setForeground(solidAccent ? accentTextOn() : accent());
        label.setBorder(new EmptyBorder(3, 7, 3, 7));
        return label;
    }

    /**
     * Crée un en-tête éditorial uniforme pour les boîtes de dialogue OmeRyth.
     */
    public static JPanel createDialogHeader(String tag, String title, String subtitle) {
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(bgRaised());
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, border()),
                new EmptyBorder(16, 22, 16, 22)
        ));

        JPanel stack = new JPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setOpaque(false);

        if (tag != null && !tag.isBlank()) {
            JLabel tagLbl = new JLabel("— " + tag.toUpperCase());
            tagLbl.setFont(fontMono(Font.BOLD, 11));
            tagLbl.setForeground(accent());
            tagLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(tagLbl);
            stack.add(Box.createVerticalStrut(4));
        }

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(fontSans(Font.BOLD, 20));
        titleLbl.setForeground(text());
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        stack.add(titleLbl);

        if (subtitle != null && !subtitle.isBlank()) {
            stack.add(Box.createVerticalStrut(4));
            JLabel subLbl = new JLabel("<html>" + subtitle + "</html>");
            subLbl.setFont(fontSans(Font.PLAIN, 12.5f));
            subLbl.setForeground(textSecondary());
            subLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            stack.add(subLbl);
        }

        header.add(stack, BorderLayout.CENTER);

        ImageIcon logo = getLogoIcon(28);
        if (logo != null) {
            JLabel logoLbl = new JLabel(logo);
            logoLbl.setVerticalAlignment(SwingConstants.CENTER);
            header.add(logoLbl, BorderLayout.EAST);
        }

        return header;
    }

    /**
     * Crée une carte avec bordure hairline 1px et compteur optionnel (01, 02...).
     */
    public static JPanel createSectionCard(String counterTag, String title, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setBackground(bgRaised());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border(), 1),
                new EmptyBorder(16, 18, 16, 18)
        ));

        JPanel cardHeader = new JPanel(new BorderLayout(8, 0));
        cardHeader.setOpaque(false);
        cardHeader.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, border()),
                new EmptyBorder(0, 0, 10, 0)
        ));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(fontSans(Font.BOLD, 14));
        titleLbl.setForeground(text());
        cardHeader.add(titleLbl, BorderLayout.WEST);

        if (counterTag != null && !counterTag.isBlank()) {
            JLabel counterLbl = new JLabel(counterTag);
            counterLbl.setFont(fontMono(Font.BOLD, 11));
            counterLbl.setForeground(textTertiary());
            cardHeader.add(counterLbl, BorderLayout.EAST);
        }

        card.add(cardHeader, BorderLayout.NORTH);
        if (content != null) {
            content.setOpaque(false);
            card.add(content, BorderLayout.CENTER);
        }
        return card;
    }

    public static JPanel createDialogFooter(JComponent... rightComponents) {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 14));
        footer.setBackground(bgRaised());
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, border()),
                new EmptyBorder(0, 12, 0, 12)
        ));
        for (JComponent comp : rightComponents) {
            if (comp != null) footer.add(comp);
        }
        return footer;
    }

    // =========================================================================
    // STYLISATION DE CONTRÔLES SWING
    // =========================================================================

    public static void styleTextField(JTextField tf) {
        if (tf == null) return;
        tf.setBackground(surface());
        tf.setForeground(text());
        tf.setCaretColor(accent());
        tf.setSelectionColor(accent());
        tf.setSelectedTextColor(accentTextOn());
        tf.setFont(fontSans(Font.PLAIN, 13));
        Border idle = new CompoundBorder(new LineBorder(borderStrong(), 1), new EmptyBorder(6, 10, 6, 10));
        Border focus = new CompoundBorder(new LineBorder(accent(), 1), new EmptyBorder(6, 10, 6, 10));
        tf.setBorder(idle);
        tf.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                tf.setBorder(focus);
            }

            @Override
            public void focusLost(FocusEvent e) {
                tf.setBorder(idle);
            }
        });
    }

    public static void styleTextArea(JTextArea ta) {
        if (ta == null) return;
        ta.setBackground(surface());
        ta.setForeground(text());
        ta.setCaretColor(accent());
        ta.setSelectionColor(accent());
        ta.setSelectedTextColor(accentTextOn());
        ta.setFont(fontSans(Font.PLAIN, 13));
        ta.setBorder(new EmptyBorder(8, 10, 8, 10));
    }

    public static void styleSpinner(JSpinner sp) {
        if (sp == null) return;
        sp.setBackground(surface());
        sp.setForeground(text());
        sp.setBorder(new LineBorder(borderStrong(), 1));
        JComponent editor = sp.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            JTextField tf = de.getTextField();
            tf.setBackground(surface());
            tf.setForeground(text());
            tf.setCaretColor(accent());
            tf.setFont(fontMono(Font.PLAIN, 13));
            tf.setBorder(new EmptyBorder(4, 8, 4, 8));
        }
    }

    public static <T> void styleComboBox(JComboBox<T> cb) {
        if (cb == null) return;
        cb.setBackground(surface());
        cb.setForeground(text());
        cb.setFont(fontSans(Font.PLAIN, 13));
        cb.setBorder(new LineBorder(borderStrong(), 1));
        cb.setFocusable(false);
    }

    public static void styleCheckBox(JCheckBox chk) {
        if (chk == null) return;
        chk.setOpaque(false);
        chk.setForeground(text());
        chk.setFont(fontSans(Font.PLAIN, 13));
        chk.setFocusPainted(false);
        chk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void styleProgressBar(JProgressBar pb) {
        if (pb == null) return;
        pb.setBackground(surface());
        pb.setForeground(accent());
        pb.setBorder(new LineBorder(border(), 1));
        pb.setFont(fontMono(Font.BOLD, 11));
        pb.setPreferredSize(new Dimension(pb.getPreferredSize().width, 20));
    }

    public static void styleScrollPane(JScrollPane sp) {
        if (sp == null) return;
        sp.setBorder(BorderFactory.createLineBorder(border(), 1));
        sp.getViewport().setBackground(bg());
        sp.setBackground(bg());
        if (sp.getVerticalScrollBar() != null) {
            sp.getVerticalScrollBar().setUI(new OmeScrollBarUI());
            sp.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
            sp.getVerticalScrollBar().setOpaque(false);
        }
        if (sp.getHorizontalScrollBar() != null) {
            sp.getHorizontalScrollBar().setUI(new OmeScrollBarUI());
            sp.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 8));
            sp.getHorizontalScrollBar().setOpaque(false);
        }
    }

    public static void styleBorderlessScrollPane(JScrollPane sp) {
        styleScrollPane(sp);
        sp.setBorder(null);
    }

    public static void styleTable(JTable table) {
        if (table == null) return;
        table.setBackground(bgRaised());
        table.setForeground(text());
        table.setGridColor(border());
        table.setSelectionBackground(accentSubtleSolid());
        table.setSelectionForeground(text());
        table.setFont(fontSans(Font.PLAIN, 13));
        table.setRowHeight(Math.max(table.getRowHeight(), 30));
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setBackground(surface());
            header.setForeground(textSecondary());
            header.setFont(fontMono(Font.BOLD, 11));
            header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, borderStrong()));
            header.setDefaultRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable tbl, Object val, boolean sel, boolean foc, int r, int c) {
                    JLabel lbl = (JLabel) super.getTableCellRendererComponent(tbl, val, sel, foc, r, c);
                    lbl.setBackground(surface());
                    lbl.setForeground(textSecondary());
                    lbl.setFont(fontMono(Font.BOLD, 11));
                    lbl.setBorder(new EmptyBorder(8, 10, 8, 10));
                    return lbl;
                }
            });
        }
    }

    public static void styleTabbedPane(JTabbedPane tabs) {
        if (tabs == null) return;
        tabs.setBackground(bg());
        tabs.setForeground(text());
        tabs.setFont(fontSans(Font.BOLD, 13));
        tabs.setOpaque(true);
    }

    public static String toHex(Color c) {
        if (c == null) return "#000000";
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // =========================================================================
    // CUSTOM SCROLLBAR & SLIDER UI
    // =========================================================================

    public static class OmeScrollBarUI extends BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = borderStrong();
            trackColor = bgRaised();
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return zeroButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return zeroButton();
        }

        private JButton zeroButton() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setMinimumSize(new Dimension(0, 0));
            b.setMaximumSize(new Dimension(0, 0));
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
            // Track discret transparent
        }

        @Override
        protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
            if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isDragging || isThumbRollover() ? accent() : borderStrong());
            int pad = 2;
            g2.fillRoundRect(
                    thumbBounds.x + pad,
                    thumbBounds.y + pad,
                    Math.max(2, thumbBounds.width - pad * 2),
                    Math.max(2, thumbBounds.height - pad * 2),
                    6, 6
            );
            g2.dispose();
        }
    }

    public static class OmeSliderUI extends BasicSliderUI {
        public OmeSliderUI(JSlider slider) {
            super(slider);
        }

        @Override
        public void paintTrack(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Rectangle t = trackRect;
            int cy = t.y + t.height / 2;
            int trackH = 4;

            g2.setColor(borderStrong());
            g2.fillRoundRect(t.x, cy - trackH / 2, t.width, trackH, trackH, trackH);

            int fillW = thumbRect.x + thumbRect.width / 2 - t.x;
            if (fillW > 0) {
                g2.setColor(accent());
                g2.fillRoundRect(t.x, cy - trackH / 2, Math.min(t.width, fillW), trackH, trackH, trackH);
            }
            g2.dispose();
        }

        @Override
        public void paintThumb(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int d = 12;
            int cx = thumbRect.x + thumbRect.width / 2 - d / 2;
            int cy = thumbRect.y + thumbRect.height / 2 - d / 2;

            g2.setColor(accent());
            g2.fillOval(cx, cy, d, d);
            g2.setColor(bgRaised());
            g2.fillOval(cx + 3, cy + 3, d - 6, d - 6);
            g2.dispose();
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(14, 14);
        }
    }
}
