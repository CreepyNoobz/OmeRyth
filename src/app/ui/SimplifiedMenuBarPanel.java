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

    // Menus principaux
    private final JMenu menuFichier;
    private final JMenuItem nouveau;
    private final JMenuItem ouvrir;
    private final JMenuItem sauvegarder;
    private final JMenu menuExporter;
    private final JMenuItem exportRythmo;
    private final JMenuItem exportDetx;
    private final JMenuItem exportVideo;
    private final JMenuItem quitter;

    private final JMenu menuEdition;
    private final JMenuItem annuler;
    private final JMenuItem retablir;
    private final JMenuItem transcriptionItem;
    private final JMenuItem rolesItem;
    private final JMenuItem baisserSon;
    private final JMenuItem monterSon;
    private final JMenuItem panneauSon;

    private final JMenu menuAffichage;
    private final JCheckBoxMenuItem affichage_signes;
    private final JCheckBoxMenuItem affichage_graduations;
    private final JCheckBoxMenuItem affichage_waveform;

    private final JMenu menuOutils;
    private final JMenuItem detecterPlans;

    private final JMenu menuOptions;
    private final JMenuItem personnalisation;
    private final JMenuItem configurer;

    private final JMenu menuAide;
    private final JMenuItem tutoriel;
    private final JMenuItem raccourcis;
    private final JMenuItem info;

    public SimplifiedMenuBarPanel(MainFenetre mainFenetre, TimelinePanel timelinePanel) {
        this.mainFenetre = mainFenetre;
        this.timelinePanel = timelinePanel;

        // ===== MENU FICHIER / FOLDER =====
        menuFichier = new JMenu("Fichier");

        nouveau = new JMenuItem("Nouveau Projet");
        ouvrir = new JMenuItem("Ouvrir Projet");
        sauvegarder = new JMenuItem("Sauvegarder");
        sauvegarder.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK));

        menuExporter = new JMenu("Exporter");
        exportRythmo = new JMenuItem("Exporter en .rythmo (OmeRyth)...");
        exportDetx = new JMenuItem("Exporter en .detx (Cappella)...");
        exportVideo = new JMenuItem("Export vidéo...");
        exportRythmo.addActionListener(e -> mainFenetre.exporterRythmo());
        exportDetx.addActionListener(e -> mainFenetre.exporterDetx());
        exportVideo.addActionListener(e -> mainFenetre.exporterEnVideo());
        menuExporter.add(exportRythmo);
        menuExporter.add(exportDetx);
        menuExporter.addSeparator();
        menuExporter.add(exportVideo);

        quitter = new JMenuItem("Quitter");

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

        // ===== MENU ÉDITION / EDIT =====
        menuEdition = new JMenu("Édition");

        annuler = new JMenuItem("Annuler");
        annuler.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));

        retablir = new JMenuItem("Rétablir");
        retablir.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK));

        baisserSon = new JMenuItem("Baisser le son (-10%)");
        monterSon = new JMenuItem("Monter le son (+10%)");
        panneauSon = new JMenuItem("Panneau de son (Slider)");

        annuler.addActionListener(e -> mainFenetre.undoAction());
        retablir.addActionListener(e -> mainFenetre.redoAction());
        baisserSon.addActionListener(e -> mainFenetre.baisserSon());
        monterSon.addActionListener(e -> mainFenetre.monterSon());
        panneauSon.addActionListener(e -> mainFenetre.toggleVolumePanel());

        menuEdition.add(annuler);
        menuEdition.add(retablir);
        menuEdition.addSeparator();
        transcriptionItem = new JMenuItem("Transcription Vocale (WhisperX)...");
        transcriptionItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK));
        transcriptionItem.addActionListener(e -> mainFenetre.ouvrirTranscriptionWhisperX());
        menuEdition.add(transcriptionItem);

        rolesItem = new JMenuItem("Rôles...");
        rolesItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_R, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
        rolesItem.addActionListener(e -> mainFenetre.ouvrirRoleWindow());
        menuEdition.add(rolesItem);
        menuEdition.addSeparator();
        menuEdition.add(baisserSon);
        menuEdition.add(monterSon);
        menuEdition.add(panneauSon);

        add(menuEdition);

        // ===== MENU AFFICHAGE / VIEW =====
        menuAffichage = new JMenu("Affichage");

        affichage_signes = new JCheckBoxMenuItem("Afficher les séparateurs", timelinePanel != null ? timelinePanel.isSeparatorsVisible() : true);
        affichage_graduations = new JCheckBoxMenuItem("Afficher les graduations", timelinePanel != null ? timelinePanel.isGraduationsVisible() : true);
        affichage_waveform = new JCheckBoxMenuItem("Afficher la waveform (onde audio)", mainFenetre != null ? mainFenetre.isWaveformVisible() : (timelinePanel != null ? timelinePanel.isWaveformVisible() : true));
        affichage_waveform.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK));

        affichage_signes.addActionListener(e -> {
            if (timelinePanel != null) timelinePanel.setSeparatorsVisible(affichage_signes.isSelected());
        });
        affichage_graduations.addActionListener(e -> {
            if (timelinePanel != null) timelinePanel.setGraduationsVisible(affichage_graduations.isSelected());
        });
        affichage_waveform.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.setWaveformVisible(affichage_waveform.isSelected());
        });

        menuAffichage.add(affichage_signes);
        menuAffichage.add(affichage_graduations);
        menuAffichage.add(affichage_waveform);

        menuAffichage.addMenuListener(new javax.swing.event.MenuListener() {
            @Override
            public void menuSelected(javax.swing.event.MenuEvent e) {
                syncAffichageState();
            }
            @Override
            public void menuDeselected(javax.swing.event.MenuEvent e) {}
            @Override
            public void menuCanceled(javax.swing.event.MenuEvent e) {}
        });

        add(menuAffichage);

        // ===== MENU OUTILS / TOOLS =====
        menuOutils = new JMenu("Outils");
        detecterPlans = new JMenuItem("Détecter les plans automatiquement");
        detecterPlans.addActionListener(e -> mainFenetre.detecterPlans());
        menuOutils.add(detecterPlans);
        add(menuOutils);

        // ===== MENU OPTIONS / SETTINGS =====
        menuOptions = new JMenu("Options");

        personnalisation = new JMenuItem("Personnalisation (Paramètres)");
        configurer = new JMenuItem("Configurer les touches");

        personnalisation.addActionListener(e -> mainFenetre.ouvrirCustomizationWindow());
        configurer.addActionListener(e -> mainFenetre.ouvrirKeybindWindow());

        menuOptions.add(personnalisation);
        menuOptions.add(configurer);
        // Note : L'association des fichiers .rythmo est désormais gérée automatiquement sans option superflue dans le menu.
        add(menuOptions);

        // ===== MENU AIDE / HELP =====
        menuAide = new JMenu("Aide");

        tutoriel = new JMenuItem("Tutoriel");
        raccourcis = new JMenuItem("Voir les raccourcis");
        info = new JMenuItem("À propos");

        tutoriel.addActionListener(e -> mainFenetre.afficherTutorial());
        raccourcis.addActionListener(e -> mainFenetre.afficherKeybinds());
        info.addActionListener(e -> mainFenetre.ouvrirInfoWindow());

        menuAide.add(tutoriel);
        menuAide.add(raccourcis);
        menuAide.addSeparator();
        menuAide.add(info);

        add(menuAide);
    }

    /**
     * Met à jour dynamiquement tous les libellés de menus en fonction de la langue choisie.
     * En anglais, "Fichier" devient "Folder", "Édition" devient "Edit", etc.
     */
    public void updateLanguage(String lang) {
        boolean en = "en".equalsIgnoreCase(lang);
        if (en) {
            menuFichier.setText("Folder");
            nouveau.setText("New Project");
            ouvrir.setText("Open Project");
            sauvegarder.setText("Save");
            menuExporter.setText("Export");
            exportRythmo.setText("Export to .rythmo (OmeRyth)...");
            exportDetx.setText("Export to .detx (Cappella)...");
            exportVideo.setText("Export video...");
            quitter.setText("Exit");

            menuEdition.setText("Edit");
            annuler.setText("Undo");
            retablir.setText("Redo");
            transcriptionItem.setText("Voice Transcription (WhisperX)...");
            rolesItem.setText("Roles...");
            baisserSon.setText("Decrease volume (-10%)");
            monterSon.setText("Increase volume (+10%)");
            panneauSon.setText("Volume Panel (Slider)");

            menuAffichage.setText("View");
            affichage_signes.setText("Show separators");
            affichage_graduations.setText("Show graduations");
            affichage_waveform.setText("Show waveform (audio wave)");

            menuOutils.setText("Tools");
            detecterPlans.setText("Detect scene cuts automatically");

            menuOptions.setText("Settings");
            personnalisation.setText("Customization (Settings)");
            configurer.setText("Configure shortcuts");

            menuAide.setText("Help");
            tutoriel.setText("Tutorial");
            raccourcis.setText("View shortcuts");
            info.setText("About");
        } else {
            menuFichier.setText("Fichier");
            nouveau.setText("Nouveau Projet");
            ouvrir.setText("Ouvrir Projet");
            sauvegarder.setText("Sauvegarder");
            menuExporter.setText("Exporter");
            exportRythmo.setText("Exporter en .rythmo (OmeRyth)...");
            exportDetx.setText("Exporter en .detx (Cappella)...");
            exportVideo.setText("Export vidéo...");
            quitter.setText("Quitter");

            menuEdition.setText("Édition");
            annuler.setText("Annuler");
            retablir.setText("Rétablir");
            transcriptionItem.setText("Transcription Vocale (WhisperX)...");
            rolesItem.setText("Rôles...");
            baisserSon.setText("Baisser le son (-10%)");
            monterSon.setText("Monter le son (+10%)");
            panneauSon.setText("Panneau de son (Slider)");

            menuAffichage.setText("Affichage");
            affichage_signes.setText("Afficher les séparateurs");
            affichage_graduations.setText("Afficher les graduations");
            affichage_waveform.setText("Afficher la waveform (onde audio)");

            menuOutils.setText("Outils");
            detecterPlans.setText("Détecter les plans automatiquement");

            menuOptions.setText("Options");
            personnalisation.setText("Personnalisation (Paramètres)");
            configurer.setText("Configurer les touches");

            menuAide.setText("Aide");
            tutoriel.setText("Tutoriel");
            raccourcis.setText("Voir les raccourcis");
            info.setText("À propos");
        }
    }

    public void setWaveformChecked(boolean visible) {
        if (affichage_waveform != null && affichage_waveform.isSelected() != visible) {
            affichage_waveform.setSelected(visible);
        }
    }

    public void setSeparatorsChecked(boolean visible) {
        if (affichage_signes != null && affichage_signes.isSelected() != visible) {
            affichage_signes.setSelected(visible);
        }
    }

    public void setGraduationsChecked(boolean visible) {
        if (affichage_graduations != null && affichage_graduations.isSelected() != visible) {
            affichage_graduations.setSelected(visible);
        }
    }

    public void syncAffichageState() {
        if (affichage_waveform != null) {
            boolean visible = (mainFenetre != null) ? mainFenetre.isWaveformVisible()
                    : (timelinePanel != null && timelinePanel.isWaveformVisible());
            if (affichage_waveform.isSelected() != visible) {
                affichage_waveform.setSelected(visible);
            }
        }
        if (affichage_signes != null && timelinePanel != null) {
            boolean visible = timelinePanel.isSeparatorsVisible();
            if (affichage_signes.isSelected() != visible) {
                affichage_signes.setSelected(visible);
            }
        }
        if (affichage_graduations != null && timelinePanel != null) {
            boolean visible = timelinePanel.isGraduationsVisible();
            if (affichage_graduations.isSelected() != visible) {
                affichage_graduations.setSelected(visible);
            }
        }
    }
}
