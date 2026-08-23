package app.ui;

import app.MainFenetre;
import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;

/**
 * Menu bar principal de l'application. Regroupe les menus "Fichier", "Gestion",
 * "Edition", "Rôle" et "Paramètre" et délègue les actions à `MainFenetre`.
 */
public class MenuBarPanel extends JMenuBar {

    private MainFenetre mainFenetre;
    private TimelinePanel timelinePanel;

    public MenuBarPanel(MainFenetre mainFenetre, TimelinePanel timelinePanel) {
        this.mainFenetre = mainFenetre;
        this.timelinePanel = timelinePanel;

        // Création du menu Fichier
        JMenu menuFichier = new JMenu("Fichier");
            JMenuItem nouveau = new JMenuItem("Nouveau");
            JMenuItem ouvrir = new JMenuItem("Ouvrir");
            JMenuItem sauvegarder = new JMenuItem("Sauvegarder");
            JMenuItem prendreEnVideo = new JMenuItem("Prendre en vidéo");
            JMenuItem quitter = new JMenuItem("Quitter");

        // Création du menu de gestion
        JMenu menuGestion = new JMenu("Gestion");
            JCheckBoxMenuItem affichage_signes = new JCheckBoxMenuItem("Affichage des signes", true);
            JCheckBoxMenuItem affichage_graduations = new JCheckBoxMenuItem("Affichage des graduations", true);
        JMenu menuEdition = new JMenu("Edition");
            JMenuItem annuler = new JMenuItem("Annuler");
            JMenuItem retablir = new JMenuItem("Retablir");
        // Création du menu Rôle
        JMenu menuRole = new JMenu("Rôle");
            JMenuItem gererRoles = new JMenuItem("Gérer les rôles...");

        // Création du menu Paramètre
        JMenu menuParam = new JMenu("Paramètre");
            JMenuItem touches = new JMenuItem("Touches");
            JMenuItem info = new JMenuItem("Info");
            JMenuItem customisation = new JMenuItem("Customisation");
            JMenuItem themeSombre = new JMenuItem("Theme sombre");
            JMenuItem themeClair = new JMenuItem("Theme clair");

        // ===== Écouteurs =====
        nouveau.addActionListener(e -> mainFenetre.nouveauProjet(timelinePanel));
        ouvrir.addActionListener(e -> mainFenetre.ouvrirProjet(timelinePanel));
        sauvegarder.addActionListener(e -> mainFenetre.sauvegarderProjet(timelinePanel));
        prendreEnVideo.addActionListener(e -> mainFenetre.exporterEnVideo());
        quitter.addActionListener(e -> mainFenetre.quitterApplication());


        gererRoles.addActionListener(e -> mainFenetre.ouvrirRoleWindow());

        touches.addActionListener(e -> mainFenetre.ouvrirKeybindWindow());
        info.addActionListener(e -> mainFenetre.ouvrirInfoWindow());
        customisation.addActionListener(e -> mainFenetre.ouvrirCustomizationWindow());
        themeSombre.addActionListener(e -> mainFenetre.appliquerThemeSombre());
        themeClair.addActionListener(e -> mainFenetre.appliquerThemeClair());
        affichage_signes.addActionListener(e ->
            timelinePanel.setSeparatorsVisible(affichage_signes.isSelected()));
        affichage_graduations.addActionListener(e ->
            timelinePanel.setGraduationsVisible(affichage_graduations.isSelected()));
        annuler.addActionListener(e -> mainFenetre.undoAction());
        retablir.addActionListener(e -> mainFenetre.redoAction());

        annuler.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        retablir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));

        // ===== Ajout au menu =====
        menuFichier.add(nouveau);
        menuFichier.add(ouvrir);
        menuFichier.add(sauvegarder);
        menuFichier.addSeparator();
        menuFichier.add(prendreEnVideo);
        menuFichier.addSeparator();
        menuFichier.add(quitter);


        menuRole.add(gererRoles);

        menuGestion.add(affichage_signes);
        menuGestion.add(affichage_graduations);

        menuEdition.add(annuler);
        menuEdition.add(retablir);

        menuParam.add(touches);
        menuParam.add(info);
        menuParam.add(customisation);
        menuParam.addSeparator();
        menuParam.add(themeSombre);
        menuParam.add(themeClair);

        add(menuFichier);
        add(menuGestion);
        add(menuEdition);
        add(menuRole);
        add(menuParam);
    }
    
}