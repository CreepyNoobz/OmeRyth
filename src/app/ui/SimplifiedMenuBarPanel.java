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

        // Sous-menu Exporter
        JMenu menuExporter = new JMenu("📤 Exporter");
        JMenuItem exportRythmo = new JMenuItem("📦 Exporter en .rythmo (OmeRyth)...");
        JMenuItem exportDetx = new JMenuItem("📜 Exporter en .detx (Cappella)...");
        JMenuItem exportVideo = new JMenuItem("🎬 Export vidéo...");
        exportRythmo.addActionListener(e -> mainFenetre.exporterRythmo());
        exportDetx.addActionListener(e -> mainFenetre.exporterDetx());
        exportVideo.addActionListener(e -> mainFenetre.exporterEnVideo());
        menuExporter.add(exportRythmo);
        menuExporter.add(exportDetx);
        menuExporter.addSeparator();
        menuExporter.add(exportVideo);

        JMenuItem quitter = new JMenuItem("❌ Quitter");

        nouveau.addActionListener(e -> mainFenetre.nouveauProjet(timelinePanel));
        ouvrir.addActionListener(e -> mainFenetre.ouvrirProjet(timelinePanel));
        sauvegarder.addActionListener(e -> mainFenetre.sauvegarderProjet(timelinePanel));
        quitter.addActionListener(e -> mainFenetre.quitterApplication());

        menuFichier.add(nouveau);
        menuFichier.add(ouvrir);
        menuFichier.addSeparator();
        menuFichier.add(sauvegarder);
        menuFichier.addSeparator();
        menuFichier.add(menuExporter);
        menuFichier.addSeparator();
        menuFichier.add(quitter);

        add(menuFichier);

        // ===== MENU ÉDITION =====
        JMenu menuEdition = new JMenu("Édition");
        
        JMenuItem annuler = new JMenuItem("↶ Annuler");
        annuler.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
        
        JMenuItem retablir = new JMenuItem("↷ Rétablir");
        retablir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));

        JMenuItem baisserSon = new JMenuItem("🔉 Baisser le son (-10%)");
        JMenuItem monterSon = new JMenuItem("🔊 Monter le son (+10%)");
        JMenuItem panneauSon = new JMenuItem("🎚️ Panneau de son (Slider)");

        annuler.addActionListener(e -> mainFenetre.undoAction());
        retablir.addActionListener(e -> mainFenetre.redoAction());
        baisserSon.addActionListener(e -> mainFenetre.baisserSon());
        monterSon.addActionListener(e -> mainFenetre.monterSon());
        panneauSon.addActionListener(e -> mainFenetre.toggleVolumePanel());

        menuEdition.add(annuler);
        menuEdition.add(retablir);
        menuEdition.addSeparator();
        JMenuItem transcriptionItem = new JMenuItem("🎙️ Transcription Vocale (WhisperX)...");
        transcriptionItem.setAccelerator(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_T, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        transcriptionItem.addActionListener(e -> mainFenetre.ouvrirTranscriptionWhisperX());
        menuEdition.add(transcriptionItem);
        JMenuItem rolesItem = new JMenuItem("🎭 Rôles...");
        rolesItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        rolesItem.addActionListener(e -> mainFenetre.ouvrirRoleWindow());
        menuEdition.add(rolesItem);
        menuEdition.addSeparator();
        menuEdition.add(baisserSon);
        menuEdition.add(monterSon);
        menuEdition.add(panneauSon);

        add(menuEdition);

        // ===== MENU AFFICHAGE =====
        JMenu menuAffichage = new JMenu("Affichage");
        
        JCheckBoxMenuItem affichage_signes = new JCheckBoxMenuItem("Afficher les séparateurs", true);
        JCheckBoxMenuItem affichage_graduations = new JCheckBoxMenuItem("Afficher les graduations", true);
        JCheckBoxMenuItem affichage_waveform = new JCheckBoxMenuItem("Afficher la waveform (onde audio)", timelinePanel.isWaveformVisible());
        affichage_waveform.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));

        affichage_signes.addActionListener(e ->
            timelinePanel.setSeparatorsVisible(affichage_signes.isSelected()));
        affichage_graduations.addActionListener(e ->
            timelinePanel.setGraduationsVisible(affichage_graduations.isSelected()));
        affichage_waveform.addActionListener(e ->
            mainFenetre.setWaveformVisible(affichage_waveform.isSelected()));

        menuAffichage.add(affichage_signes);
        menuAffichage.add(affichage_graduations);
        menuAffichage.add(affichage_waveform);

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
        menuOptions.addSeparator();
        JMenuItem associerRythmo = new JMenuItem("🔗 Associer les fichiers .rythmo au logo...");
        associerRythmo.addActionListener(e -> app.services.FileAssociationService.associateNow(mainFenetre));
        menuOptions.add(associerRythmo);
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
