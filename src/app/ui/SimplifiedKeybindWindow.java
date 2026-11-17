package app.ui;

import app.MainFenetre;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Fenêtre de paramètres simplifiée - affiche les raccourcis clavier essentiels avec un design moderne.
 */
public class SimplifiedKeybindWindow extends JDialog {

    public SimplifiedKeybindWindow(MainFenetre owner) {
        super(owner, owner != null && owner.isEnglish() ? "Keyboard Shortcuts" : "Raccourcis clavier", true);
        boolean isEn = owner != null && owner.isEnglish();
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(580, 520);
        setMinimumSize(new Dimension(500, 440));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        // En-tête
        add(OmeTheme.createDialogHeader(
                isEn ? "SHORTCUTS" : "RACCOURCIS",
                isEn ? "Essential keyboard shortcuts" : "Raccourcis clavier essentiels",
                isEn ? "Quick reference for playback, timeline sync and editing."
                     : "Référence rapide pour la lecture, le synchronisme et l'édition."
        ), BorderLayout.NORTH);

        // Contenu
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(OmeTheme.bg());
        contentPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        JPanel listCard = new JPanel();
        listCard.setLayout(new BoxLayout(listCard, BoxLayout.Y_AXIS));
        listCard.setBackground(OmeTheme.bgRaised());
        listCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OmeTheme.border(), 1),
                new EmptyBorder(6, 16, 6, 16)
        ));

        Object[][] keybinds = isEn ? new Object[][] {
            {owner.getKeyText(owner.getMarcheArretKeyCode()), "Play / Pause video"},
            {owner.getKeyText(owner.getZoomInKeyCode()), "Zoom in timeline"},
            {owner.getKeyText(owner.getZoomOutKeyCode()), "Zoom out timeline"},
            {owner.getKeyText(owner.getAvanceMSKeyCode()), "Forward 0.5s"},
            {owner.getKeyText(owner.getReculerMSKeyCode()), "Rewind 0.5s"},
            {owner.getKeyText(owner.getRetourDebutKeyCode()), "Return to video start"},
            {owner.getKeyText(owner.getSeparateurKeyCode()), "Add sync separator (blue line)"},
            {"Ctrl+Z", "Undo last action"},
            {"Ctrl+Y", "Redo undone action"}
        } : new Object[][] {
            {owner.getKeyText(owner.getMarcheArretKeyCode()), "Lecture / Arrêt de la vidéo"},
            {owner.getKeyText(owner.getZoomInKeyCode()), "Zoomer sur la timeline"},
            {owner.getKeyText(owner.getZoomOutKeyCode()), "Dézoomer la timeline"},
            {owner.getKeyText(owner.getAvanceMSKeyCode()), "Avancer de 0.5s"},
            {owner.getKeyText(owner.getReculerMSKeyCode()), "Reculer de 0.5s"},
            {owner.getKeyText(owner.getRetourDebutKeyCode()), "Retour au début de la vidéo"},
            {owner.getKeyText(owner.getSeparateurKeyCode()), "Ajouter un séparateur bleu (synchronisme)"},
            {"Ctrl+Z", "Annuler la dernière action"},
            {"Ctrl+Y", "Rétablir l'action annulée"}
        };

        for (int idx = 0; idx < keybinds.length; idx++) {
            Object[] kb = keybinds[idx];
            JPanel row = new JPanel(new BorderLayout(15, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(9, 0, 9, 0));

            JPanel shortcutPanel = createShortcutVisual((String) kb[0]);

            JLabel descLabel = new JLabel((String) kb[1]);
            descLabel.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
            descLabel.setForeground(OmeTheme.text());

            row.add(shortcutPanel, BorderLayout.WEST);
            row.add(descLabel, BorderLayout.CENTER);

            listCard.add(row);

            if (idx < keybinds.length - 1) {
                JSeparator sep = new JSeparator();
                sep.setForeground(OmeTheme.border());
                sep.setBackground(OmeTheme.border());
                sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                listCard.add(sep);
            }
        }

        contentPanel.add(listCard);
        JScrollPane scroll = new JScrollPane(contentPanel);
        OmeTheme.styleBorderlessScrollPane(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // Pied de page
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(OmeTheme.bgRaised());
        footerPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, OmeTheme.border()));

        JTextArea infoArea = new JTextArea(isEn ? "Tip: These shortcuts can be customized in Settings > Configure shortcuts." : "Astuce : Ces raccourcis sont personnalisables dans Options > Configurer les touches.");
        infoArea.setEditable(false);
        infoArea.setOpaque(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(OmeTheme.fontSans(Font.ITALIC, 12));
        infoArea.setForeground(OmeTheme.textSecondary());
        infoArea.setBorder(new EmptyBorder(14, 20, 14, 20));
        OmeTheme.OmeButton closeBtn = OmeTheme.createPrimaryButton(isEn ? "Close" : "Fermer");
        closeBtn.addActionListener(e -> dispose());

        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 12));
        btnWrapper.setOpaque(false);
        btnWrapper.add(closeBtn);

        footerPanel.add(infoArea, BorderLayout.CENTER);
        footerPanel.add(btnWrapper, BorderLayout.EAST);
        add(footerPanel, BorderLayout.SOUTH);
    }

    private JPanel createShortcutVisual(String shortcutText) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        panel.setPreferredSize(new Dimension(150, 30));

        if (shortcutText.contains("+")) {
            String[] parts = shortcutText.split("\\+");
            for (int i = 0; i < parts.length; i++) {
                panel.add(new KeyUI(parts[i].trim()));
                if (i < parts.length - 1) {
                    JLabel plus = new JLabel("+");
                    plus.setFont(new Font("Segoe UI", Font.BOLD, 14));
                    plus.setForeground(new Color(100, 100, 100));
                    panel.add(plus);
                }
            }
        } else {
            panel.add(new KeyUI(shortcutText.trim()));
        }

        return panel;
    }

    static class KeyUI extends JComponent {
        private String text;

        public KeyUI(String text) {
            this.text = text;
            int w = getFontMetrics(OmeTheme.fontMono(Font.BOLD, 11.5f)).stringWidth(text) + 22;
            setPreferredSize(new Dimension(Math.max(36, w), 26));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            g2.setColor(OmeTheme.border());
            g2.fillRoundRect(0, 2, w - 1, h - 3, 6, 6);

            g2.setColor(OmeTheme.surface());
            g2.fillRoundRect(0, 0, w - 1, h - 3, 6, 6);

            g2.setColor(OmeTheme.borderStrong());
            g2.drawRoundRect(0, 0, w - 1, h - 3, 6, 6);

            g2.setColor(OmeTheme.accent());
            g2.setFont(OmeTheme.fontMono(Font.BOLD, 11.5f));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - 2 - fm.getHeight()) / 2 + fm.getAscent();
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }
}
