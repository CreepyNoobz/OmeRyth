package app.ui;

import app.MainFenetre;
import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Menu bar SIMPLIFIÉ - réduit aux actions essentielles seulement.
 * Priorise l'utilisation simple et directe.
 */
public class SimplifiedMenuBarPanel extends JMenuBar {

    private MainFenetre mainFenetre;
    private TimelinePanel timelinePanel;

    public SimplifiedMenuBarPanel(MainFenetre mainFenetre, TimelinePanel timelinePanel) {
        this.mainFenetre = mainFenetre;
        this.timelinePanel = timelinePanel;

        // ===== MENU FICHIER =====
        JMenu menuFichier = new JMenu("Fichier");
        
        JMenuItem nouveau = new JMenuItem("➕ Nouveau Projet");
        JMenuItem ouvrir = new JMenuItem("📂 Ouvrir Projet");
        JMenuItem sauvegarder = new JMenuItem("💾 Sauvegarder");
        sauvegarder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));
        
        JMenuItem exporter = new JMenuItem("🎬 Exporter en vidéo");
        JMenuItem quitter = new JMenuItem("❌ Quitter");

        nouveau.addActionListener(e -> mainFenetre.nouveauProjet(timelinePanel));
        ouvrir.addActionListener(e -> mainFenetre.ouvrirProjet(timelinePanel));
        sauvegarder.addActionListener(e -> mainFenetre.sauvegarderProjet(timelinePanel));
        exporter.addActionListener(e -> mainFenetre.exporterEnVideo());
        quitter.addActionListener(e -> mainFenetre.quitterApplication());

        menuFichier.add(nouveau);
        menuFichier.add(ouvrir);
        menuFichier.addSeparator();
        menuFichier.add(sauvegarder);
        menuFichier.addSeparator();
        menuFichier.add(exporter);
        menuFichier.addSeparator();
        menuFichier.add(quitter);

        add(menuFichier);

        // ===== MENU ÉDITION =====
        JMenu menuEdition = new JMenu("Édition");
        
        JMenuItem annuler = new JMenuItem("↶ Annuler");
        annuler.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        
        JMenuItem retablir = new JMenuItem("↷ Rétablir");
        retablir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));

        annuler.addActionListener(e -> mainFenetre.undoAction());
        retablir.addActionListener(e -> mainFenetre.redoAction());

        menuEdition.add(annuler);
        menuEdition.add(retablir);

        add(menuEdition);

        // ===== MENU AFFICHAGE =====
        JMenu menuAffichage = new JMenu("Affichage");
        
        JCheckBoxMenuItem affichage_signes = new JCheckBoxMenuItem("Afficher les séparateurs", true);
        JCheckBoxMenuItem affichage_graduations = new JCheckBoxMenuItem("Afficher les graduations", true);

        affichage_signes.addActionListener(e ->
            timelinePanel.setSeparatorsVisible(affichage_signes.isSelected()));
        affichage_graduations.addActionListener(e ->
            timelinePanel.setGraduationsVisible(affichage_graduations.isSelected()));

        menuAffichage.add(affichage_signes);
        menuAffichage.add(affichage_graduations);

        add(menuAffichage);

        // ===== MENU OUTILS =====
        JMenu menuOutils = new JMenu("Outils");
        JMenuItem detecterPlans = new JMenuItem("🎥 Détecter les plans automatiquement");
        detecterPlans.addActionListener(e -> mainFenetre.detecterPlans());
        menuOutils.add(detecterPlans);
        add(menuOutils);

        // ===== MENU OPTIONS =====
        JMenu menuOptions = new JMenu("Options");
        
        JMenuItem personnalisation = new JMenuItem("🎨 Personnalisation (Paramètres)");
        JMenuItem configurer = new JMenuItem("⚙️ Configurer les touches");
        
        personnalisation.addActionListener(e -> mainFenetre.ouvrirCustomizationWindow());
        configurer.addActionListener(e -> mainFenetre.ouvrirKeybindWindow());
        
        menuOptions.add(personnalisation);
        menuOptions.add(configurer);
        add(menuOptions);

        // ===== MENU AIDE =====
        JMenu menuAide = new JMenu("Aide");
        
        JMenuItem tutoriel = new JMenuItem("📚 Tutoriel");
        JMenuItem raccourcis = new JMenuItem("⌨️ Voir les raccourcis");
        JMenuItem info = new JMenuItem("ℹ️ À propos");

        tutoriel.addActionListener(e -> mainFenetre.afficherTutorial());
        raccourcis.addActionListener(e -> mainFenetre.afficherKeybinds());
        info.addActionListener(e -> mainFenetre.ouvrirInfoWindow());

        menuAide.add(tutoriel);
        menuAide.add(raccourcis);
        menuAide.addSeparator();
        menuAide.add(info);

        add(menuAide);
    }
}
