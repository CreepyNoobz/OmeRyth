package app.ui;

import app.MainFenetre;
import javax.swing.*;
import java.awt.*;

/**
 * Barre d'outils simplifiée pour l'édition.
 * Affiche les actions principales sous forme de boutons visuels et intuitifs.
 */
public class EditorToolbar extends JPanel {

    public EditorToolbar(MainFenetre mainFenetre, TimelinePanel timelinePanel) {
        setLayout(new FlowLayout(FlowLayout.LEFT, 8, 5));
        setBackground(new Color(220, 220, 220));
        setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));

        // Bouton Lecture/Pause
        JButton playPauseBtn = createToolButton("▶ Jouer / ⏸ Pause", 
            "Appuyez sur ESPACE", e -> mainFenetre.togglePlayPause());
        add(playPauseBtn);

        add(new JSeparator(JSeparator.VERTICAL));

        // Bouton Reculer
        JButton backBtn = createToolButton("◀ Reculer", 
            "Appuyez sur ← (0.5s)", e -> mainFenetre.moveBackward());
        add(backBtn);

        // Bouton Avancer
        JButton forwardBtn = createToolButton("Avancer ▶", 
            "Appuyez sur → (0.5s)", e -> mainFenetre.moveForward());
        add(forwardBtn);

        // Bouton Retour au début
        JButton returnBtn = createToolButton("⏮ Début", 
            "Appuyez sur R", e -> mainFenetre.returnToStart());
        add(returnBtn);

        add(new JSeparator(JSeparator.VERTICAL));

        // Bouton Ajouter séparateur
        JButton separatorBtn = createToolButton("➕ Séparateur", 
            "Appuyez sur M", e -> mainFenetre.addSeparator());
        separatorBtn.setForeground(new Color(200, 0, 0));
        add(separatorBtn);

        add(new JSeparator(JSeparator.VERTICAL));

        // Bouton Annuler
        JButton undoBtn = createToolButton("↶ Annuler", 
            "Appuyez sur CTRL+Z", e -> mainFenetre.undoAction());
        add(undoBtn);

        // Bouton Rétablir
        JButton redoBtn = createToolButton("↷ Rétablir", 
            "Appuyez sur CTRL+Y", e -> mainFenetre.redoAction());
        add(redoBtn);

        add(Box.createHorizontalGlue());
    }

    private JButton createToolButton(String text, String tooltip, java.awt.event.ActionListener listener) {
        JButton btn = new JButton(text);
        btn.setToolTipText(tooltip);
        btn.setFont(new Font("Arial", Font.PLAIN, 11));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 150), 1));
        btn.setBackground(new Color(240, 240, 240));
        btn.addActionListener(listener);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Hover effect
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(200, 220, 240));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(240, 240, 240));
            }
        });

        return btn;
    }
}
