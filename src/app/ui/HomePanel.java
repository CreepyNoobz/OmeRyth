package app.ui;

import app.MainFenetre;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;

/**
 * Page d'accueil simplifiée - alignée sur l'identité visuelle OmeRyth.
 */
public class HomePanel extends JPanel {

    public HomePanel(MainFenetre mainFenetre) {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(OmeTheme.bg());
        setBorder(new EmptyBorder(40, 40, 40, 40));

        JLabel tagLabel = OmeTheme.createTagBadge("STUDIO BANDE RYTHMO", false);
        tagLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        add(tagLabel);
        add(Box.createVerticalStrut(14));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel omeLbl = new JLabel("Ome");
        omeLbl.setFont(OmeTheme.fontSans(Font.BOLD, 44));
        omeLbl.setForeground(OmeTheme.text());
        JLabel rythLbl = new JLabel("Ryth");
        rythLbl.setFont(OmeTheme.fontSans(Font.BOLD, 44));
        rythLbl.setForeground(OmeTheme.accent());
        brandRow.add(omeLbl);
        brandRow.add(rythLbl);
        add(brandRow);

        add(Box.createVerticalStrut(8));

        JLabel subtitleLabel = new JLabel("Synchroniseur de rythme, doublage et post-synchronisation");
        subtitleLabel.setFont(OmeTheme.fontSans(Font.PLAIN, 15));
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setForeground(OmeTheme.textSecondary());
        add(subtitleLabel);

        add(Box.createVerticalStrut(40));

        JButton newProjectBtn = createActionButton("+ Nouveau Projet", true,
                e -> mainFenetre.nouveauProjet(null));
        JButton openProjectBtn = createActionButton("Ouvrir Projet", false,
                e -> mainFenetre.ouvrirProjet(null));
        JButton tutorialBtn = createActionButton("Tutoriel Interactif", false,
                e -> mainFenetre.afficherTutorial());

        add(newProjectBtn);
        add(Box.createVerticalStrut(12));
        add(openProjectBtn);
        add(Box.createVerticalStrut(12));
        add(tutorialBtn);

        add(Box.createVerticalGlue());

        JLabel tipLabel = new JLabel("Astuce : commencez par le tutoriel interactif pour maîtriser les raccourcis !");
        tipLabel.setFont(OmeTheme.fontMono(Font.PLAIN, 11.5f));
        tipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        tipLabel.setForeground(OmeTheme.textTertiary());
        add(tipLabel);
    }

    private JButton createActionButton(String text, boolean primary, ActionListener listener) {
        OmeTheme.OmeButton btn = primary
                ? OmeTheme.createPrimaryButton(text)
                : OmeTheme.createSecondaryButton(text);
        btn.setFont(OmeTheme.fontSans(Font.BOLD, 15));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setMaximumSize(new Dimension(360, 48));
        btn.setPreferredSize(new Dimension(360, 48));
        btn.addActionListener(listener);
        return btn;
    }
}
