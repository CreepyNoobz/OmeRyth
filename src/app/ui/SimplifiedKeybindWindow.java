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
        super(owner, "Raccourcis clavier", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(550, 480);
        setMinimumSize(new Dimension(550, 480));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(250, 250, 250));

        // En-tête
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(30, 30, 30));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        JLabel titleLabel = new JLabel("⌨️ Raccourcis clavier essentiels");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Contenu
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(250, 250, 250));
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        Object[][] keybinds = new Object[][] {
            {owner.getKeyText(owner.getMarcheArretKeyCode()), "Lecture / Arrêt de la vidéo"},
            {owner.getKeyText(owner.getZoomInKeyCode()), "Zoomer sur la timeline"},
            {owner.getKeyText(owner.getZoomOutKeyCode()), "Dézoomer la timeline"},
            {owner.getKeyText(owner.getAvanceMSKeyCode()), "Avancer de 0.5s"},
            {owner.getKeyText(owner.getReculerMSKeyCode()), "Reculer de 0.5s"},
            {owner.getKeyText(owner.getRetourDebutKeyCode()), "Retour au début de la vidéo"},
            {owner.getKeyText(owner.getSeparateurKeyCode()), "Ajouter un séparateur de plan"},
            {"Ctrl+Z", "Annuler la dernière action"},
            {"Ctrl+Y", "Rétablir l'action annulée"}
        };

        for (Object[] kb : keybinds) {
            JPanel row = new JPanel(new BorderLayout(15, 0));
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(8, 0, 8, 0));
            
            // Partie gauche (Raccourci)
            JPanel shortcutPanel = createShortcutVisual((String) kb[0]);
            
            // Partie droite (Description)
            JLabel descLabel = new JLabel((String) kb[1]);
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            descLabel.setForeground(new Color(50, 50, 50));
            
            row.add(shortcutPanel, BorderLayout.WEST);
            row.add(descLabel, BorderLayout.CENTER);
            
            contentPanel.add(row);
            
            // Séparateur fin
            JSeparator sep = new JSeparator();
            sep.setForeground(new Color(230, 230, 230));
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            contentPanel.add(sep);
        }

        JScrollPane scroll = new JScrollPane(contentPanel);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // Pied de page
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBackground(Color.WHITE);
        footerPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        
        JTextArea infoArea = new JTextArea("💡 Astuce : Ces raccourcis sont personnalisables dans Options > Configurer les touches.");
        infoArea.setEditable(false);
        infoArea.setOpaque(false);
        infoArea.setLineWrap(true);
        infoArea.setWrapStyleWord(true);
        infoArea.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        infoArea.setForeground(new Color(100, 100, 100));
        infoArea.setBorder(new EmptyBorder(15, 20, 15, 20));
        JButton closeBtn = new JButton("Fermer");
        closeBtn.setFocusPainted(false);
        closeBtn.addActionListener(e -> dispose());
        
        JPanel btnWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 20, 15));
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
            setPreferredSize(new Dimension(getFontMetrics(new Font("Segoe UI", Font.BOLD, 12)).stringWidth(text) + 20, 26));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            // Ombre / Bordure du bas pour l'effet 3D
            g2.setColor(new Color(200, 200, 200));
            g2.fillRoundRect(0, 2, w, h - 2, 6, 6);

            // Face de la touche
            g2.setColor(new Color(245, 245, 245));
            g2.fillRoundRect(0, 0, w, h - 3, 6, 6);
            
            // Bordure
            g2.setColor(new Color(210, 210, 210));
            g2.drawRoundRect(0, 0, w - 1, h - 4, 6, 6);

            // Texte
            g2.setColor(new Color(50, 50, 50));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - 2;
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }
}
