package tests;

import javax.swing.*;
import java.awt.*;

public class TestKey {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {}

            JFrame frame = new JFrame("Exemple de Raccourci");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(300, 200);
            frame.setLocationRelativeTo(null);
            frame.getContentPane().setBackground(new Color(30, 30, 30));
            frame.setLayout(new GridBagLayout());

            JPanel shortcutPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
            shortcutPanel.setOpaque(false);
            shortcutPanel.add(new KeyUI("Ctrl"));
            
            JLabel plus = new JLabel("+");
            plus.setForeground(Color.WHITE);
            plus.setFont(new Font("Segoe UI", Font.BOLD, 14));
            shortcutPanel.add(plus);
            
            shortcutPanel.add(new KeyUI("Z"));

            frame.add(shortcutPanel);
            frame.setVisible(true);
        });
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
            g2.setColor(new Color(20, 20, 20));
            g2.fillRoundRect(0, 2, w, h - 2, 8, 8);

            // Face de la touche (dégradé ou couleur unie)
            g2.setColor(new Color(60, 60, 60));
            g2.fillRoundRect(0, 0, w, h - 3, 8, 8);
            
            // Bordure subtile
            g2.setColor(new Color(80, 80, 80));
            g2.drawRoundRect(0, 0, w - 1, h - 4, 8, 8);

            // Texte
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - 2;
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }
}
