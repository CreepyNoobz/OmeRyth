package app.ui;

import app.MainFenetre;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicCheckBoxMenuItemUI;
import javax.swing.plaf.basic.BasicMenuBarUI;
import javax.swing.plaf.basic.BasicMenuItemUI;
import javax.swing.plaf.basic.BasicMenuUI;
import java.awt.*;
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

    private final OmeTheme.OmeButton langToggleBtn;
    private final OmeTheme.OmeButton themeToggleBtn;
    private final JLabel brandPill;

    public SimplifiedMenuBarPanel(MainFenetre mainFenetre, TimelinePanel timelinePanel) {
        this.mainFenetre = mainFenetre;
        this.timelinePanel = timelinePanel;

        setOpaque(true);
        setUI(new BasicMenuBarUI() {
            @Override
            public void paint(Graphics g, JComponent c) {
                g.setColor(c.getBackground());
                g.fillRect(0, 0, c.getWidth(), c.getHeight());
            }
        });

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
        detecterPlans = new JMenuItem("Détecter les plans automatiquement (BETA)");
        detecterPlans.addActionListener(e -> mainFenetre.detecterPlans());
        menuOutils.add(detecterPlans);
        add(menuOutils);

        // ===== MENU OPTIONS / SETTINGS =====
        menuOptions = new JMenu("Options");

        personnalisation = new JMenuItem("Personnalisation...");
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

        // ===== ZONE DROITE NAVBAR (style ometitnoobz.github.io/Web) =====
        add(Box.createHorizontalGlue());

        brandPill = new JLabel("OmeRyth");
        brandPill.setFont(OmeTheme.fontMono(Font.BOLD, 11));
        brandPill.setBorder(new EmptyBorder(0, 8, 0, 10));

        langToggleBtn = new OmeTheme.OmeButton("FR", OmeTheme.ButtonVariant.SECONDARY);
        langToggleBtn.setFocusable(false);
        langToggleBtn.setFont(OmeTheme.fontMono(Font.BOLD, 11));
        langToggleBtn.setMargin(new Insets(3, 9, 3, 9));
        langToggleBtn.setRadius(5);
        langToggleBtn.setToolTipText("FR / EN");
        langToggleBtn.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.toggleLanguage();
        });

        themeToggleBtn = new OmeTheme.OmeButton("", OmeTheme.ButtonVariant.SECONDARY) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getForeground());
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                if (OmeTheme.isDark()) {
                    // Croissant de lune (mode sombre actif)
                    g2.drawArc(cx - 5, cy - 5, 10, 10, 45, 225);
                    g2.drawArc(cx - 2, cy - 5, 8, 8, 60, 180);
                } else {
                    // Soleil (mode clair actif)
                    g2.drawOval(cx - 3, cy - 3, 6, 6);
                    for (int a = 0; a < 360; a += 45) {
                        double rad = Math.toRadians(a);
                        int x1 = cx + (int) Math.round(Math.cos(rad) * 5.0);
                        int y1 = cy + (int) Math.round(Math.sin(rad) * 5.0);
                        int x2 = cx + (int) Math.round(Math.cos(rad) * 7.0);
                        int y2 = cy + (int) Math.round(Math.sin(rad) * 7.0);
                        g2.drawLine(x1, y1, x2, y2);
                    }
                }
                g2.dispose();
            }
        };
        themeToggleBtn.setFocusable(false);
        themeToggleBtn.setPreferredSize(new Dimension(28, 24));
        themeToggleBtn.setMinimumSize(new Dimension(28, 24));
        themeToggleBtn.setMaximumSize(new Dimension(28, 24));
        themeToggleBtn.setMargin(new Insets(0, 0, 0, 0));
        themeToggleBtn.setRadius(5);
        themeToggleBtn.setToolTipText("Basculer thème sombre / clair");
        themeToggleBtn.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.toggleTheme();
        });

        add(langToggleBtn);
        add(Box.createHorizontalStrut(6));
        add(themeToggleBtn);
        add(Box.createHorizontalStrut(4));
        add(brandPill);

        applyTheme();
    }

    /**
     * Met à jour dynamiquement tous les libellés de menus en fonction de la langue choisie.
     * En anglais, "Fichier" devient "Folder", "Édition" devient "Edit", etc.
     */
    public void updateLanguage(String lang) {
        boolean en = "en".equalsIgnoreCase(lang);
        if (langToggleBtn != null) {
            langToggleBtn.setText(en ? "EN" : "FR");
        }
        if (themeToggleBtn != null) {
            themeToggleBtn.setToolTipText(en ? "Toggle Dark / Light theme" : "Basculer thème sombre / clair");
        }
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
            detecterPlans.setText("Détecter les plans automatiquement (BETA)");

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

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(OmeTheme.border());
        g2.fillRect(0, getHeight() - 1, getWidth(), 1);
        g2.dispose();
    }

    public void applyTheme() {
        setBackground(OmeTheme.bgRaised());
        setForeground(OmeTheme.text());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, OmeTheme.border()),
                new EmptyBorder(3, 6, 3, 6)
        ));
        JMenu[] menus = {menuFichier, menuEdition, menuAffichage, menuOutils, menuOptions, menuAide};
        for (JMenu m : menus) {
            if (m == null) continue;
            m.setUI(new OmeTopMenuUI());
            m.setOpaque(true);
            m.setForeground(OmeTheme.text());
            m.setBackground(OmeTheme.bgRaised());
            m.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
            m.setBorder(new EmptyBorder(4, 10, 4, 10));
            JPopupMenu popup = m.getPopupMenu();
            if (popup != null) {
                popup.setOpaque(true);
                popup.setBackground(OmeTheme.bgRaised());
                popup.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(OmeTheme.borderStrong(), 1),
                        new EmptyBorder(4, 4, 4, 4)
                ));
                stylePopupChildren(popup);
            }
        }
        if (brandPill != null) {
            brandPill.setForeground(OmeTheme.accent());
        }
        if (langToggleBtn != null) langToggleBtn.repaint();
        if (themeToggleBtn != null) themeToggleBtn.repaint();
        repaint();
    }

    private void stylePopupChildren(MenuElement element) {
        for (MenuElement child : element.getSubElements()) {
            Component comp = child.getComponent();
            if (comp instanceof JCheckBoxMenuItem cbItem) {
                cbItem.setUI(new OmeCheckBoxMenuItemUI());
                cbItem.setBackground(OmeTheme.bgRaised());
                cbItem.setForeground(OmeTheme.text());
                cbItem.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
                cbItem.setBorder(new EmptyBorder(6, 12, 6, 12));
                cbItem.setOpaque(true);
            } else if (comp instanceof JMenu subMenu) {
                subMenu.setUI(new OmeTopMenuUI());
                subMenu.setBackground(OmeTheme.bgRaised());
                subMenu.setForeground(OmeTheme.text());
                subMenu.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
                subMenu.setBorder(new EmptyBorder(6, 12, 6, 12));
                subMenu.setOpaque(true);
                JPopupMenu subPopup = subMenu.getPopupMenu();
                if (subPopup != null) {
                    subPopup.setOpaque(true);
                    subPopup.setBackground(OmeTheme.bgRaised());
                    subPopup.setBorder(BorderFactory.createCompoundBorder(
                            BorderFactory.createLineBorder(OmeTheme.borderStrong(), 1),
                            new EmptyBorder(4, 4, 4, 4)
                    ));
                    stylePopupChildren(subPopup);
                }
            } else if (comp instanceof JMenuItem item) {
                item.setUI(new OmeMenuItemUI());
                item.setBackground(OmeTheme.bgRaised());
                item.setForeground(OmeTheme.text());
                item.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
                item.setBorder(new EmptyBorder(6, 12, 6, 12));
                item.setOpaque(true);
            } else if (comp instanceof JSeparator sep) {
                sep.setForeground(OmeTheme.border());
                sep.setBackground(OmeTheme.bgRaised());
            }
            stylePopupChildren(child);
        }
    }

    private static class OmeTopMenuUI extends BasicMenuUI {
        @Override
        protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
            ButtonModel model = menuItem.getModel();
            int w = menuItem.getWidth();
            int h = menuItem.getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (model.isArmed() || (menuItem instanceof JMenu && model.isSelected())) {
                g2.setColor(OmeTheme.surfaceHover());
                g2.fillRoundRect(2, 2, w - 4, h - 4, 5, 5);
            } else if (model.isRollover()) {
                g2.setColor(OmeTheme.surface());
                g2.fillRoundRect(2, 2, w - 4, h - 4, 5, 5);
            } else {
                g2.setColor(menuItem.getParent() != null ? menuItem.getParent().getBackground() : OmeTheme.bgRaised());
                g2.fillRect(0, 0, w, h);
            }
            g2.dispose();
        }

        @Override
        protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
            ButtonModel model = menuItem.getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (model.isArmed() || (menuItem instanceof JMenu && model.isSelected()) || model.isRollover()) {
                g2.setColor(OmeTheme.accent());
            } else {
                g2.setColor(OmeTheme.text());
            }
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, textRect.x, textRect.y + fm.getAscent());
            g2.dispose();
        }
    }

    private static class OmeMenuItemUI extends BasicMenuItemUI {
        @Override
        protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
            ButtonModel model = menuItem.getModel();
            int w = menuItem.getWidth();
            int h = menuItem.getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (model.isArmed() || model.isSelected()) {
                g2.setColor(OmeTheme.accentSubtleSolid());
                g2.fillRoundRect(2, 1, w - 4, h - 2, 4, 4);
            } else {
                g2.setColor(OmeTheme.bgRaised());
                g2.fillRect(0, 0, w, h);
            }
            g2.dispose();
        }

        @Override
        protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
            ButtonModel model = menuItem.getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (model.isArmed() || model.isSelected()) {
                g2.setColor(OmeTheme.accent());
            } else {
                g2.setColor(OmeTheme.text());
            }
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, textRect.x, textRect.y + fm.getAscent());
            g2.dispose();
        }
    }

    private static class OmeCheckBoxMenuItemUI extends BasicCheckBoxMenuItemUI {
        @Override
        protected void paintBackground(Graphics g, JMenuItem menuItem, Color bgColor) {
            ButtonModel model = menuItem.getModel();
            int w = menuItem.getWidth();
            int h = menuItem.getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (model.isArmed()) {
                g2.setColor(OmeTheme.accentSubtleSolid());
                g2.fillRoundRect(2, 1, w - 4, h - 2, 4, 4);
            } else {
                g2.setColor(OmeTheme.bgRaised());
                g2.fillRect(0, 0, w, h);
            }
            g2.dispose();
        }

        @Override
        protected void paintText(Graphics g, JMenuItem menuItem, Rectangle textRect, String text) {
            ButtonModel model = menuItem.getModel();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (model.isArmed()) {
                g2.setColor(OmeTheme.accent());
            } else {
                g2.setColor(OmeTheme.text());
            }
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(text, textRect.x, textRect.y + fm.getAscent());
            g2.dispose();
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
