package app.ui;

import app.MainFenetre;

import javax.swing.*;
import java.awt.*;

/**
 * Panneau horizontal de contrôle du volume (0 à 100%).
 * Placé directement au-dessus du chronomètre (TimerClass).
 */
public class VolumePanel extends JPanel {

    private final MainFenetre mainFenetre;
    private final JSlider slider;
    private final JComponent speakerIcon;
    private final JLabel valueLabel;
    private final JButton closeBtn;
    private boolean adjusting = false;
    private int currentVolume = 100;

    public VolumePanel(MainFenetre mainFenetre, AppCustomization customization) {
        this.mainFenetre = mainFenetre;

        setLayout(new BorderLayout(8, 0));
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, OmeTheme.border()),
                BorderFactory.createEmptyBorder(6, 10, 6, 8)
        ));

        int initialVol = (mainFenetre != null) ? mainFenetre.getVolume() : 100;
        this.currentVolume = Math.max(0, Math.min(100, initialVol));

        // Icône vectorielle haut-parleur
        speakerIcon = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(currentVolume == 0 ? OmeTheme.textTertiary() : OmeTheme.accent());
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cy = getHeight() / 2;
                int[] px = {2, 6, 10, 10, 6, 2};
                int[] py = {cy - 2, cy - 2, cy - 5, cy + 5, cy + 2, cy + 2};
                g2.fillPolygon(px, py, 6);
                if (currentVolume == 0) {
                    g2.setColor(OmeTheme.danger());
                    g2.drawLine(12, cy - 3, 16, cy + 3);
                    g2.drawLine(16, cy - 3, 12, cy + 3);
                } else {
                    g2.drawArc(9, cy - 3, 5, 6, -55, 110);
                    if (currentVolume >= 50) {
                        g2.drawArc(10, cy - 5, 8, 10, -55, 110);
                    }
                }
                g2.dispose();
            }
        };
        speakerIcon.setPreferredSize(new Dimension(20, 18));
        speakerIcon.setToolTipText("Volume");

        // Slider horizontal de 0 à 100
        slider = new JSlider(SwingConstants.HORIZONTAL, 0, 100, initialVol);
        slider.setFocusable(false);
        slider.setOpaque(false);
        slider.setUI(new OmeTheme.OmeSliderUI(slider));
        slider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        slider.setToolTipText("Régler le volume sonore (0 à 100%)");

        // Affichage textuel monospace du pourcentage
        valueLabel = new JLabel(initialVol + "%", SwingConstants.RIGHT);
        valueLabel.setPreferredSize(new Dimension(38, 20));
        valueLabel.setFont(OmeTheme.fontMono(Font.BOLD, 11));
        updateLabels(initialVol);

        // Bouton de fermeture discret
        closeBtn = new JButton("×");
        closeBtn.setFocusable(false);
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setFont(OmeTheme.fontMono(Font.BOLD, 13));
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setToolTipText("Fermer le panneau de son");
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> {
            setVisible(false);
            if (getParent() != null) {
                getParent().revalidate();
                getParent().repaint();
            }
        });

        // Écouteur de changement du slider
        slider.addChangeListener(e -> {
            int val = slider.getValue();
            updateLabels(val);
            if (!adjusting && mainFenetre != null) {
                mainFenetre.setVolume(val);
            }
        });

        JPanel rightPanel = new JPanel(new BorderLayout(2, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(valueLabel, BorderLayout.CENTER);
        rightPanel.add(closeBtn, BorderLayout.EAST);

        add(speakerIcon, BorderLayout.WEST);
        add(slider, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);

        applyCustomization(customization);
    }

    /**
     * Met à jour la position du curseur et les labels de volume sans propager
     * d'événements récursifs.
     */
    public void setVolume(int volume) {
        adjusting = true;
        try {
            int clamped = Math.max(0, Math.min(100, volume));
            slider.setValue(clamped);
            updateLabels(clamped);
        } finally {
            adjusting = false;
        }
    }

    public int getVolume() {
        return slider.getValue();
    }

    private void updateLabels(int volume) {
        this.currentVolume = volume;
        valueLabel.setText(volume + "%");
        speakerIcon.repaint();
    }

    /**
     * Applique les couleurs du thème (fond et texte).
     */
    public void applyCustomization(AppCustomization c) {
        if (c != null && c.timerBackground != null) {
            setBackground(c.timerBackground);
        } else {
            setBackground(OmeTheme.bgRaised());
        }
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, OmeTheme.border()),
                BorderFactory.createEmptyBorder(6, 10, 6, 8)
        ));
        Color fg = (c != null && c.timerTextColor != null) ? c.timerTextColor : OmeTheme.text();
        valueLabel.setForeground(fg);
        closeBtn.setForeground(OmeTheme.textTertiary());
        slider.repaint();
        speakerIcon.repaint();
        repaint();
    }
}
