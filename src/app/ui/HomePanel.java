package app.ui;

import app.MainFenetre;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Page d'accueil simplifié - le premier point de contact de l'utilisateur.
 * 3 actions principales : Nouveau projet, Ouvrir, Tutoriel
 */
public class HomePanel extends JPanel {

    public HomePanel(MainFenetre mainFenetre) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(245, 245, 245));
        setBorder(new EmptyBorder(40, 40, 40, 40));

        // Titre principal
        JLabel titleLabel = new JLabel("OmeRyth");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 48));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(new Color(70, 70, 70));
        add(titleLabel);

        add(Box.createVerticalStrut(10));

        // Sous-titre
        JLabel subtitleLabel = new JLabel("Synchroniseur de rythme et typographie");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 16));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setForeground(new Color(120, 120, 120));
        add(subtitleLabel);

        add(Box.createVerticalStrut(50));

        // Boutons principaux
        JButton newProjectBtn = createLargeButton("✚ Nouveau Projet", 
            e -> mainFenetre.nouveauProjet(null));
        
        JButton openProjectBtn = createLargeButton("📂 Ouvrir Projet", 
            e -> mainFenetre.ouvrirProjet(null));
        
        JButton tutorialBtn = createLargeButton("📚 Tutoriel Interactif", 
            e -> mainFenetre.afficherTutorial());

        add(newProjectBtn);
        add(Box.createVerticalStrut(15));
        add(openProjectBtn);
        add(Box.createVerticalStrut(15));
        add(tutorialBtn);

        add(Box.createVerticalGlue());

        // Pied de page avec astuces
        JLabel tipLabel = new JLabel("Astuce: Commencez par le tutoriel pour apprendre les bases !");
        tipLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        tipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        tipLabel.setForeground(new Color(100, 150, 200));
        add(tipLabel);
    }

    private JButton createLargeButton(String text, ActionListener listener) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Arial", Font.PLAIN, 16));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(350, 50));
        btn.setBackground(new Color(225, 235, 245));
        btn.setForeground(Color.BLACK);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createRaisedBevelBorder());
        btn.addActionListener(listener);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Hover effect
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(200, 220, 240));
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btn.setBackground(new Color(225, 235, 245));
            }
        });
        
        return btn;
    }
}
