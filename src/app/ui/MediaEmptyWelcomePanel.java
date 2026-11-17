package app.ui;

import app.MainFenetre;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Écran d'accueil intégré dans la zone média lorsqu'aucune vidéo n'est chargée.
 * Inspiré directement de l'esthétique éditoriale et studio d'ometitnoobz.github.io/Web.
 */
public class MediaEmptyWelcomePanel extends JPanel {

    private final MainFenetre mainFenetre;
    private boolean hasCustomImage = false;
    private boolean isEnglish = false;

    private JLabel badgeTextLbl;
    private JLabel omePartLbl;
    private JLabel rythPartLbl;
    private JLabel subtitleLbl;
    private OmeTheme.OmeButton newProjBtn;
    private OmeTheme.OmeButton openProjBtn;
    private OmeTheme.OmeButton tutorialBtn;
    private OmeTheme.OmeButton keybindsBtn;
    private JLabel dropHintLbl;
    private JPanel trustBar;
    private JPanel badgePill;
    private JLabel badgeDot;
    private JLabel[] trustTitles;
    private JLabel[] trustTags;

    public MediaEmptyWelcomePanel(MainFenetre mainFenetre) {
        this.mainFenetre = mainFenetre;
        setOpaque(false);
        setLayout(new GridBagLayout());
        initUI();
        applyTheme();
    }

    private void initUI() {
        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBorder(new EmptyBorder(20, 28, 20, 28));
        container.setMaximumSize(new Dimension(680, 500));

        // 1. Hero status badge (● STUDIO BANDE RYTHMO)
        badgePill = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(OmeTheme.bgRaised());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(OmeTheme.border());
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.dispose();
            }
        };
        badgePill.setOpaque(false);
        badgePill.setAlignmentX(Component.CENTER_ALIGNMENT);
        badgePill.setMaximumSize(new Dimension(520, 30));

        badgeDot = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(OmeTheme.accentSubtle());
                g2.fillOval(0, 0, 12, 12);
                g2.setColor(OmeTheme.accent());
                g2.fillOval(3, 3, 6, 6);
                g2.dispose();
            }
        };
        badgeDot.setPreferredSize(new Dimension(12, 12));

        badgeTextLbl = new JLabel("STUDIO BANDE RYTHMO  •  DOUBLAGE & POST-SYNCHRO");
        badgeTextLbl.setFont(OmeTheme.fontMono(Font.BOLD, 10));

        badgePill.add(badgeDot);
        badgePill.add(badgeTextLbl);
        container.add(badgePill);
        container.add(Box.createVerticalStrut(16));

        // 2. Wordmark OmeRyth
        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        brandRow.setOpaque(false);
        brandRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        ImageIcon logo = OmeTheme.getLogoIcon(34);
        if (logo != null) {
            JLabel logoLbl = new JLabel(logo);
            logoLbl.setBorder(new EmptyBorder(0, 0, 0, 12));
            brandRow.add(logoLbl);
        }

        omePartLbl = new JLabel("Ome");
        omePartLbl.setFont(OmeTheme.fontSans(Font.BOLD, 34));

        rythPartLbl = new JLabel("Ryth");
        rythPartLbl.setFont(OmeTheme.fontSans(Font.BOLD, 34));

        brandRow.add(omePartLbl);
        brandRow.add(rythPartLbl);
        container.add(brandRow);
        container.add(Box.createVerticalStrut(8));

        // 3. Subtitle
        subtitleLbl = new JLabel("Synchronisation labiale, transcription IA WhisperX et export vidéo GPU.", SwingConstants.CENTER);
        subtitleLbl.setFont(OmeTheme.fontSans(Font.PLAIN, 13.5f));
        subtitleLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(subtitleLbl);
        container.add(Box.createVerticalStrut(22));

        // 4. CTA Buttons
        JPanel ctaRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4));
        ctaRow.setOpaque(false);
        ctaRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        newProjBtn = OmeTheme.createPrimaryButton("+ Nouveau Projet");
        newProjBtn.setFocusable(false);
        newProjBtn.setMargin(new Insets(10, 18, 10, 18));
        newProjBtn.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.nouveauProjet(mainFenetre.getTimelinePanel());
        });

        openProjBtn = OmeTheme.createSecondaryButton("Ouvrir un Projet");
        openProjBtn.setFocusable(false);
        openProjBtn.setMargin(new Insets(10, 18, 10, 18));
        openProjBtn.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.ouvrirProjet(mainFenetre.getTimelinePanel());
        });

        tutorialBtn = OmeTheme.createGhostButton("Tutoriel");
        tutorialBtn.setFocusable(false);
        tutorialBtn.setMargin(new Insets(10, 14, 10, 14));
        tutorialBtn.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.afficherTutorial();
        });

        keybindsBtn = OmeTheme.createGhostButton("Raccourcis");
        keybindsBtn.setFocusable(false);
        keybindsBtn.setMargin(new Insets(10, 14, 10, 14));
        keybindsBtn.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.afficherKeybinds();
        });

        ctaRow.add(newProjBtn);
        ctaRow.add(openProjBtn);
        ctaRow.add(tutorialBtn);
        ctaRow.add(keybindsBtn);
        container.add(ctaRow);
        container.add(Box.createVerticalStrut(12));

        // 5. Drag & drop hint
        dropHintLbl = new JLabel("Glissez-déposez une vidéo (.mp4, .mkv, .avi) ou un projet (.rythmo, .detx)", SwingConstants.CENTER);
        dropHintLbl.setFont(OmeTheme.fontMono(Font.PLAIN, 11));
        dropHintLbl.setAlignmentX(Component.CENTER_ALIGNMENT);
        container.add(dropHintLbl);
        container.add(Box.createVerticalStrut(22));

        // 6. 4-column Trust Bar (matching .hero-trust-bar)
        trustBar = new JPanel(new GridLayout(1, 4, 0, 0));
        trustBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        trustBar.setMaximumSize(new Dimension(640, 68));

        String[] codes = {"SYN", "VOX", "WAV", "9:16"};
        String[] labelsFr = {"Synchro 60 FPS", "WhisperX IA", "Waveform Live", "Export Studio"};
        trustTags = new JLabel[4];
        trustTitles = new JLabel[4];

        for (int i = 0; i < 4; i++) {
            JPanel cell = new JPanel();
            cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
            cell.setOpaque(false);
            cell.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, i == 0 ? 0 : 1, 0, 0, OmeTheme.border()),
                    new EmptyBorder(10, 12, 10, 12)
            ));

            JLabel tag = OmeTheme.createTagBadge(codes[i], true);
            tag.setAlignmentX(Component.LEFT_ALIGNMENT);
            trustTags[i] = tag;

            JLabel title = new JLabel(labelsFr[i]);
            title.setFont(OmeTheme.fontSans(Font.PLAIN, 11.5f));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            trustTitles[i] = title;

            cell.add(tag);
            cell.add(Box.createVerticalStrut(6));
            cell.add(title);
            trustBar.add(cell);
        }

        container.add(trustBar);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        add(container, gbc);
    }

    public void setHasCustomImage(boolean hasCustomImage) {
        this.hasCustomImage = hasCustomImage;
        repaint();
    }

    public void updateLanguage(String lang) {
        this.isEnglish = "en".equalsIgnoreCase(lang);
        if (isEnglish) {
            badgeTextLbl.setText("RYTHMO BAND STUDIO  •  DUBBING & LIP-SYNC");
            subtitleLbl.setText("Lip synchronization, WhisperX AI transcription, and GPU video export.");
            newProjBtn.setText("+ New Project");
            openProjBtn.setText("Open Project");
            tutorialBtn.setText("Tutorial");
            keybindsBtn.setText("Shortcuts");
            dropHintLbl.setText("Drag & drop a video (.mp4, .mkv, .avi) or a project (.rythmo, .detx)");
            String[] labelsEn = {"60 FPS Sync", "WhisperX AI", "Live Waveform", "Studio Export"};
            for (int i = 0; i < trustTitles.length; i++) {
                trustTitles[i].setText(labelsEn[i]);
            }
        } else {
            badgeTextLbl.setText("STUDIO BANDE RYTHMO  •  DOUBLAGE & POST-SYNCHRO");
            subtitleLbl.setText("Synchronisation labiale, transcription IA WhisperX et export vidéo GPU.");
            newProjBtn.setText("+ Nouveau Projet");
            openProjBtn.setText("Ouvrir un Projet");
            tutorialBtn.setText("Tutoriel");
            keybindsBtn.setText("Raccourcis");
            dropHintLbl.setText("Glissez-déposez une vidéo (.mp4, .mkv, .avi) ou un projet (.rythmo, .detx)");
            String[] labelsFr = {"Synchro 60 FPS", "WhisperX IA", "Waveform Live", "Export Studio"};
            for (int i = 0; i < trustTitles.length; i++) {
                trustTitles[i].setText(labelsFr[i]);
            }
        }
    }

    public void applyTheme() {
        badgeTextLbl.setForeground(OmeTheme.textSecondary());
        omePartLbl.setForeground(OmeTheme.text());
        rythPartLbl.setForeground(OmeTheme.accent());
        subtitleLbl.setForeground(OmeTheme.textSecondary());
        dropHintLbl.setForeground(OmeTheme.textTertiary());

        trustBar.setBackground(OmeTheme.bgRaised());
        trustBar.setOpaque(true);
        trustBar.setBorder(BorderFactory.createLineBorder(OmeTheme.border(), 1));

        for (int i = 0; i < trustBar.getComponentCount(); i++) {
            Component comp = trustBar.getComponent(i);
            if (comp instanceof JPanel cell) {
                cell.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, i == 0 ? 0 : 1, 0, 0, OmeTheme.border()),
                        new EmptyBorder(10, 12, 10, 12)
                ));
            }
        }
        for (JLabel tag : trustTags) {
            if (tag != null) tag.setForeground(OmeTheme.accentTextOn());
        }
        for (JLabel t : trustTitles) {
            if (t != null) t.setForeground(OmeTheme.textSecondary());
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!hasCustomImage) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            // Subtle architectural grid lines
            g2.setColor(OmeTheme.gridLine());
            int step = 40;
            for (int x = step; x < w; x += step) {
                g2.drawLine(x, 0, x, h);
            }
            for (int y = step; y < h; y += step) {
                g2.drawLine(0, y, w, y);
            }

            // Ambient emerald radial glow matching website `.glow-1`
            if (w > 100 && h > 100) {
                Color c1 = new Color(OmeTheme.accent().getRed(), OmeTheme.accent().getGreen(), OmeTheme.accent().getBlue(), OmeTheme.isDark() ? 22 : 14);
                Color c2 = new Color(OmeTheme.accent().getRed(), OmeTheme.accent().getGreen(), OmeTheme.accent().getBlue(), 0);
                RadialGradientPaint glow = new RadialGradientPaint(
                        new Point(w / 2, h / 3),
                        Math.max(w, h) * 0.55f,
                        new float[]{0f, 1f},
                        new Color[]{c1, c2}
                );
                g2.setPaint(glow);
                g2.fillRect(0, 0, w, h);
            }
            g2.dispose();
        }
        super.paintComponent(g);
    }
}
