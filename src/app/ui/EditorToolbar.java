package app.ui;

import app.MainFenetre;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Barre d'outils studio pour la bande rythmo : contrôles de transport,
 * zoom temporel, marquage rapide, outils IA et export vidéo.
 * Utilise des icônes vectorielles Java2D pour un rendu net sans dépendance de police.
 * Conserve le focus clavier sur la timeline (tous les contrôles sont non-focusables).
 */
public class EditorToolbar extends JPanel {

    private enum VectorIcon {
        NONE, PLAY, PAUSE, STEP_BACK, STEP_FWD, TO_START, UNDO, REDO
    }

    private final MainFenetre mainFenetre;
    private final TimelinePanel timelinePanel;

    private IconToolButton playPauseBtn;
    private IconToolButton backBtn;
    private IconToolButton forwardBtn;
    private IconToolButton returnBtn;
    private IconToolButton zoomOutBtn;
    private JLabel zoomBadge;
    private IconToolButton zoomInBtn;
    private IconToolButton separatorBtn;
    private IconToolButton rolesBtn;
    private IconToolButton whisperBtn;
    private IconToolButton plansBtn;
    private IconToolButton undoBtn;
    private IconToolButton redoBtn;
    private OmeTheme.OmeButton exportBtn;

    private final List<JPanel> dividers = new ArrayList<>();
    private final Timer syncTimer;
    private boolean isEnglish = false;

    public EditorToolbar(MainFenetre mainFenetre, TimelinePanel timelinePanel) {
        this.mainFenetre = mainFenetre;
        this.timelinePanel = timelinePanel;

        setLayout(new BorderLayout(8, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, OmeTheme.border()),
                new EmptyBorder(5, 10, 5, 10)
        ));

        JPanel leftFlow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftFlow.setOpaque(false);

        // Transport
        playPauseBtn = createIconBtn("Lecture", VectorIcon.PLAY, "Lecture / Pause (ESPACE)", true, e -> {
            if (mainFenetre != null) mainFenetre.togglePlayPause();
            refreshState();
        });
        backBtn = createIconBtn("0.5s", VectorIcon.STEP_BACK, "Reculer de 0.5s (←)", false, e -> {
            if (mainFenetre != null) mainFenetre.moveBackward();
        });
        forwardBtn = createIconBtn("0.5s", VectorIcon.STEP_FWD, "Avancer de 0.5s (→)", false, e -> {
            if (mainFenetre != null) mainFenetre.moveForward();
        });
        returnBtn = createIconBtn("Début", VectorIcon.TO_START, "Retour au début (R)", false, e -> {
            if (mainFenetre != null) mainFenetre.returnToStart();
        });

        leftFlow.add(playPauseBtn);
        leftFlow.add(backBtn);
        leftFlow.add(forwardBtn);
        leftFlow.add(returnBtn);
        leftFlow.add(createDivider());

        // Zoom
        zoomOutBtn = createIconBtn("−", VectorIcon.NONE, "Dézoomer la timeline", false, e -> {
            if (timelinePanel != null) {
                timelinePanel.zoomOut();
                refreshState();
                timelinePanel.requestFocusInWindow();
            }
        });
        zoomBadge = new JLabel("1.0x", SwingConstants.CENTER);
        zoomBadge.setFont(OmeTheme.fontMono(Font.BOLD, 11));
        zoomBadge.setPreferredSize(new Dimension(40, 24));

        zoomInBtn = createIconBtn("+", VectorIcon.NONE, "Zoomer sur la timeline", false, e -> {
            if (timelinePanel != null) {
                timelinePanel.zoomIn();
                refreshState();
                timelinePanel.requestFocusInWindow();
            }
        });

        leftFlow.add(zoomOutBtn);
        leftFlow.add(zoomBadge);
        leftFlow.add(zoomInBtn);
        leftFlow.add(createDivider());

        // Rythmo & Rôles
        separatorBtn = createIconBtn("+ Séparateur", VectorIcon.NONE, "Ajouter un séparateur de synchro", false, e -> {
            if (mainFenetre != null) mainFenetre.addSeparator();
        });
        rolesBtn = createIconBtn("Rôles", VectorIcon.NONE, "Gérer les rôles (Ctrl+Shift+R)", false, e -> {
            if (mainFenetre != null) mainFenetre.ouvrirRoleWindow();
        });
        leftFlow.add(separatorBtn);
        leftFlow.add(rolesBtn);
        leftFlow.add(createDivider());

        // IA & Plans & Historique
        whisperBtn = createIconBtn("WhisperX", VectorIcon.NONE, "Transcription Vocale IA (Ctrl+T)", false, e -> {
            if (mainFenetre != null) mainFenetre.ouvrirTranscriptionWhisperX();
        });
        plansBtn = createIconBtn("Plans", VectorIcon.NONE, "Détecter les changements de plan", false, e -> {
            if (mainFenetre != null) mainFenetre.detecterPlans();
        });
        undoBtn = createIconBtn("", VectorIcon.UNDO, "Annuler (Ctrl+Z)", false, e -> {
            if (mainFenetre != null) mainFenetre.undoAction();
        });
        redoBtn = createIconBtn("", VectorIcon.REDO, "Rétablir (Ctrl+Y)", false, e -> {
            if (mainFenetre != null) mainFenetre.redoAction();
        });

        leftFlow.add(whisperBtn);
        leftFlow.add(plansBtn);
        leftFlow.add(undoBtn);
        leftFlow.add(redoBtn);

        // Right primary Export CTA
        JPanel rightFlow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightFlow.setOpaque(false);
        exportBtn = OmeTheme.createPrimaryButton("Export Vidéo");
        exportBtn.setFocusable(false);
        exportBtn.setFont(OmeTheme.fontSans(Font.BOLD, 12));
        exportBtn.setMargin(new Insets(5, 13, 5, 13));
        exportBtn.setRadius(5);
        exportBtn.setToolTipText("Exporter la bande rythmo en vidéo");
        exportBtn.addActionListener(e -> {
            if (mainFenetre != null) mainFenetre.exporterEnVideo();
        });
        rightFlow.add(exportBtn);

        add(leftFlow, BorderLayout.CENTER);
        add(rightFlow, BorderLayout.EAST);

        applyTheme();
        refreshState();

        syncTimer = new Timer(180, e -> refreshState());
        syncTimer.start();
    }

    private IconToolButton createIconBtn(String text, VectorIcon icon, String tooltip, boolean secondary, java.awt.event.ActionListener listener) {
        IconToolButton btn = new IconToolButton(text, icon,
                secondary ? OmeTheme.ButtonVariant.SECONDARY : OmeTheme.ButtonVariant.GHOST);
        btn.setFocusable(false);
        btn.setFont(OmeTheme.fontSans(Font.BOLD, 11.5f));
        btn.setRadius(5);
        btn.setToolTipText(tooltip);
        btn.addActionListener(listener);
        return btn;
    }

    private JPanel createDivider() {
        JPanel div = new JPanel();
        div.setPreferredSize(new Dimension(1, 20));
        div.setBackground(OmeTheme.border());
        dividers.add(div);
        return div;
    }

    public void refreshState() {
        if (timelinePanel != null) {
            String z = String.format("%.1fx", timelinePanel.getZoomLevel()).replace(",", ".");
            if (!z.equals(zoomBadge.getText())) {
                zoomBadge.setText(z);
            }
        }
        if (mainFenetre != null && mainFenetre.getTimer() != null) {
            boolean running = mainFenetre.getTimer().isRunning();
            String target = running ? "Pause" : (isEnglish ? "Play" : "Lecture");
            if (!target.equals(playPauseBtn.getText())) {
                playPauseBtn.setText(target);
            }
            playPauseBtn.setVectorIcon(running ? VectorIcon.PAUSE : VectorIcon.PLAY);
            playPauseBtn.setVariant(running ? OmeTheme.ButtonVariant.PRIMARY : OmeTheme.ButtonVariant.SECONDARY);
        }
    }

    public void updateLanguage(String lang) {
        this.isEnglish = "en".equalsIgnoreCase(lang);
        if (isEnglish) {
            playPauseBtn.setToolTipText("Play / Pause (SPACE)");
            backBtn.setToolTipText("Step back 0.5s (←)");
            forwardBtn.setToolTipText("Step forward 0.5s (→)");
            returnBtn.setText("Start");
            returnBtn.setToolTipText("Return to start (R)");
            zoomOutBtn.setToolTipText("Zoom out timeline");
            zoomInBtn.setToolTipText("Zoom in timeline");
            separatorBtn.setText("+ Separator");
            separatorBtn.setToolTipText("Add sync separator");
            rolesBtn.setText("Roles");
            rolesBtn.setToolTipText("Manage roles (Ctrl+Shift+R)");
            whisperBtn.setToolTipText("AI Voice Transcription (Ctrl+T)");
            plansBtn.setText("Cuts");
            plansBtn.setToolTipText("Detect scene cuts automatically");
            undoBtn.setToolTipText("Undo (Ctrl+Z)");
            redoBtn.setToolTipText("Redo (Ctrl+Y)");
            exportBtn.setText("Export Video");
            exportBtn.setToolTipText("Export rythmo band as video");
        } else {
            playPauseBtn.setToolTipText("Lecture / Pause (ESPACE)");
            backBtn.setToolTipText("Reculer de 0.5s (←)");
            forwardBtn.setToolTipText("Avancer de 0.5s (→)");
            returnBtn.setText("Début");
            returnBtn.setToolTipText("Retour au début (R)");
            zoomOutBtn.setToolTipText("Dézoomer la timeline");
            zoomInBtn.setToolTipText("Zoomer sur la timeline");
            separatorBtn.setText("+ Séparateur");
            separatorBtn.setToolTipText("Ajouter un séparateur de synchro");
            rolesBtn.setText("Rôles");
            rolesBtn.setToolTipText("Gérer les rôles (Ctrl+Shift+R)");
            whisperBtn.setToolTipText("Transcription Vocale IA (Ctrl+T)");
            plansBtn.setText("Plans");
            plansBtn.setToolTipText("Détecter les changements de plan");
            undoBtn.setToolTipText("Annuler (Ctrl+Z)");
            redoBtn.setToolTipText("Rétablir (Ctrl+Y)");
            exportBtn.setText("Export Vidéo");
            exportBtn.setToolTipText("Exporter la bande rythmo en vidéo");
        }
        refreshState();
    }

    public void applyTheme() {
        setBackground(OmeTheme.bgRaised());
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, OmeTheme.border()),
                new EmptyBorder(5, 10, 5, 10)
        ));
        zoomBadge.setForeground(OmeTheme.accent());
        for (JPanel d : dividers) {
            d.setBackground(OmeTheme.border());
        }
        repaint();
    }

    private static class IconToolButton extends OmeTheme.OmeButton {
        private VectorIcon vectorIcon;

        IconToolButton(String text, VectorIcon icon, OmeTheme.ButtonVariant variant) {
            super(text, variant);
            this.vectorIcon = icon;
            updateMargins();
        }

        void setVectorIcon(VectorIcon icon) {
            this.vectorIcon = icon;
            updateMargins();
            repaint();
        }

        private void updateMargins() {
            if (vectorIcon == VectorIcon.NONE) {
                setMargin(new Insets(5, 9, 5, 9));
            } else if (getText() == null || getText().isEmpty()) {
                setMargin(new Insets(5, 12, 5, 12));
                setPreferredSize(new Dimension(28, 26));
            } else {
                setMargin(new Insets(5, 22, 5, 10));
            }
        }

        @Override
        public void setText(String text) {
            super.setText(text);
            if (vectorIcon != null) {
                updateMargins();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (vectorIcon == null || vectorIcon == VectorIcon.NONE) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getForeground());
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            int h = getHeight();
            int cy = h / 2;
            boolean iconOnly = (getText() == null || getText().isEmpty());
            int ix = iconOnly ? (getWidth() / 2 - 5) : 8;

            switch (vectorIcon) {
                case PLAY -> {
                    int[] px = {ix + 1, ix + 9, ix + 1};
                    int[] py = {cy - 4, cy, cy + 4};
                    g2.fillPolygon(px, py, 3);
                }
                case PAUSE -> {
                    g2.fillRect(ix + 1, cy - 4, 2, 8);
                    g2.fillRect(ix + 6, cy - 4, 2, 8);
                }
                case STEP_BACK -> {
                    int[] px = {ix + 7, ix + 2, ix + 7};
                    int[] py = {cy - 4, cy, cy + 4};
                    g2.fillPolygon(px, py, 3);
                }
                case STEP_FWD -> {
                    int[] px = {ix + 2, ix + 7, ix + 2};
                    int[] py = {cy - 4, cy, cy + 4};
                    g2.fillPolygon(px, py, 3);
                }
                case TO_START -> {
                    g2.fillRect(ix + 1, cy - 4, 2, 8);
                    int[] px = {ix + 9, ix + 4, ix + 9};
                    int[] py = {cy - 4, cy, cy + 4};
                    g2.fillPolygon(px, py, 3);
                }
                case UNDO -> {
                    Path2D p = new Path2D.Float();
                    p.moveTo(ix + 1, cy - 1);
                    p.lineTo(ix + 4, cy - 4);
                    p.moveTo(ix + 1, cy - 1);
                    p.lineTo(ix + 4, cy + 2);
                    g2.draw(p);
                    g2.drawArc(ix + 1, cy - 1, 8, 7, 15, 165);
                }
                case REDO -> {
                    Path2D p = new Path2D.Float();
                    p.moveTo(ix + 9, cy - 1);
                    p.lineTo(ix + 6, cy - 4);
                    p.moveTo(ix + 9, cy - 1);
                    p.lineTo(ix + 6, cy + 2);
                    g2.draw(p);
                    g2.drawArc(ix + 1, cy - 1, 8, 7, 0, 165);
                }
                default -> {}
            }
            g2.dispose();
        }
    }
}
