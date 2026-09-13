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
    private final JLabel iconLabel;
    private final JLabel valueLabel;
    private final JButton closeBtn;
    private boolean adjusting = false;

    public VolumePanel(MainFenetre mainFenetre, AppCustomization customization) {
        this.mainFenetre = mainFenetre;

        setLayout(new BorderLayout(6, 0));
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(55, 55, 55)),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)
        ));

        int initialVol = (mainFenetre != null) ? mainFenetre.getVolume() : 100;

        // Icône de haut-parleur
        iconLabel = new JLabel("🔊");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 14));
        iconLabel.setToolTipText("Volume");

        // Slider horizontal de 0 à 100
        slider = new JSlider(SwingConstants.HORIZONTAL, 0, 100, initialVol);
        slider.setFocusable(false);
        slider.setOpaque(false);
        slider.setToolTipText("Régler le volume sonore (0 à 100%)");

        // Affichage textuel du pourcentage
        valueLabel = new JLabel(initialVol + "%", SwingConstants.RIGHT);
        valueLabel.setPreferredSize(new Dimension(38, 20));
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        updateLabels(initialVol);

        // Bouton de fermeture discret
        closeBtn = new JButton("×");
        closeBtn.setFocusable(false);
        closeBtn.setMargin(new Insets(0, 3, 0, 3));
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
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

        add(iconLabel, BorderLayout.WEST);
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
        valueLabel.setText(volume + "%");
        if (volume == 0) {
            iconLabel.setText("🔇");
        } else if (volume < 50) {
            iconLabel.setText("🔉");
        } else {
            iconLabel.setText("🔊");
        }
    }

    /**
     * Applique les couleurs du thème (fond et texte).
     */
    public void applyCustomization(AppCustomization c) {
        if (c == null) return;
        setBackground(c.timerBackground);
        Color fg = c.timerTextColor != null ? c.timerTextColor : Color.WHITE;
        iconLabel.setForeground(fg);
        valueLabel.setForeground(fg);
        slider.setForeground(fg);
        closeBtn.setForeground(fg);
        repaint();
    }
}
