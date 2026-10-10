package app.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Barre de contrôle temporelle interactive pour l'exclusion ciblée de la suppression vocale.
 * <p>
 * Affiche un slider représentant la longévité totale de la vidéo, des commandes de lecture
 * pour écouter l'audio en direct dans la fenêtre d'exportation, et permet d'insérer des balises
 * (intervalles par défaut de 3 secondes) pour préserver les voix originales aux endroits choisis.
 * </p>
 */
public class VoiceExclusionTimelinePanel extends JPanel {

    /**
     * Représente une zone temporelle où les voix originales doivent être conservées.
     */
    public static class VoiceZone {
        public double startSec;
        public double endSec;

        public VoiceZone(double startSec, double endSec) {
            this.startSec = Math.min(startSec, endSec);
            this.endSec = Math.max(startSec, endSec);
        }

        public double getDuration() {
            return Math.max(0.0, endSec - startSec);
        }

        public boolean contains(double time) {
            return time >= startSec && time <= endSec;
        }
    }

    private double totalDuration = 60.0;
    private double currentTime = 0.0;
    private final List<VoiceZone> zones = new ArrayList<>();
    private int selectedZoneIndex = -1;
    private boolean isPlaying = false;

    private final Consumer<Double> onSeek;
    private final Runnable onTogglePlay;
    private Runnable onZonesChanged;

    private JButton btnPlayPause;
    private JLabel lblTime;
    private JButton btnAddZone;
    private JButton btnDeleteZone;
    private JButton btnClearAll;
    private JLabel lblSummary;
    private TrackCanvas trackCanvas;

    public VoiceExclusionTimelinePanel(double durationSeconds, Consumer<Double> onSeek, Runnable onTogglePlay) {
        this.totalDuration = Math.max(1.0, durationSeconds);
        this.onSeek = onSeek;
        this.onTogglePlay = onTogglePlay;

        setLayout(new BorderLayout(0, 6));
        setBackground(new Color(24, 24, 27));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(45, 45, 52)),
                new EmptyBorder(8, 12, 10, 12)
        ));

        initUI();
    }

    public void setOnZonesChanged(Runnable callback) {
        this.onZonesChanged = callback;
    }

    public void setTotalDuration(double duration) {
        this.totalDuration = Math.max(1.0, duration);
        if (this.currentTime > this.totalDuration) {
            this.currentTime = this.totalDuration;
        }
        updateTimeDisplay();
        if (trackCanvas != null) trackCanvas.repaint();
    }

    public double getTotalDuration() {
        return totalDuration;
    }

    public double getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(double time) {
        this.currentTime = Math.max(0.0, Math.min(totalDuration, time));
        updateTimeDisplay();
        if (trackCanvas != null) trackCanvas.repaint();
    }

    public void setPlaying(boolean playing) {
        this.isPlaying = playing;
        if (btnPlayPause != null) {
            btnPlayPause.setText(playing ? "Pause" : "Lecture");
            btnPlayPause.setBackground(playing ? new Color(245, 158, 11) : new Color(240, 240, 245));
            btnPlayPause.setForeground(Color.BLACK);
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public List<VoiceZone> getZones() {
        return new ArrayList<>(zones);
    }

    /**
     * Retourne les zones triées et fusionnées sous forme de couples [startSec, endSec]
     * prêts pour l'injection dans les filtres FFmpeg.
     */
    public List<double[]> getNormalizedZones() {
        if (zones.isEmpty()) return new ArrayList<>();
        List<VoiceZone> sorted = new ArrayList<>(zones);
        sorted.sort(Comparator.comparingDouble(z -> z.startSec));

        List<double[]> merged = new ArrayList<>();
        double curStart = Math.max(0.0, sorted.get(0).startSec);
        double curEnd = Math.min(totalDuration, sorted.get(0).endSec);

        for (int i = 1; i < sorted.size(); i++) {
            VoiceZone z = sorted.get(i);
            double s = Math.max(0.0, z.startSec);
            double e = Math.min(totalDuration, z.endSec);
            if (s <= curEnd + 0.05) { // Zones qui se chevauchent ou se touchent
                curEnd = Math.max(curEnd, e);
            } else {
                if (curEnd > curStart) {
                    merged.add(new double[]{ Math.round(curStart * 1000.0) / 1000.0, Math.round(curEnd * 1000.0) / 1000.0 });
                }
                curStart = s;
                curEnd = e;
            }
        }
        if (curEnd > curStart) {
            merged.add(new double[]{ Math.round(curStart * 1000.0) / 1000.0, Math.round(curEnd * 1000.0) / 1000.0 });
        }
        return merged;
    }

    public void addZone(double startSec, double endSec) {
        double s = Math.max(0.0, Math.min(totalDuration, startSec));
        double e = Math.max(0.0, Math.min(totalDuration, endSec));
        if (Math.abs(e - s) < 0.1) {
            e = Math.min(totalDuration, s + 3.0);
        }
        zones.add(new VoiceZone(s, e));
        selectedZoneIndex = zones.size() - 1;
        updateSummary();
        updateButtonStates();
        if (trackCanvas != null) trackCanvas.repaint();
        if (onZonesChanged != null) onZonesChanged.run();
    }

    public void addZoneAtCursor(double durationSec) {
        double s = currentTime;
        double e = Math.min(totalDuration, s + durationSec);
        if (e - s < durationSec && totalDuration >= durationSec) {
            s = Math.max(0.0, e - durationSec);
        }
        addZone(s, e);
    }

    public void deleteSelectedZone() {
        if (selectedZoneIndex >= 0 && selectedZoneIndex < zones.size()) {
            zones.remove(selectedZoneIndex);
            selectedZoneIndex = -1;
            updateSummary();
            updateButtonStates();
            if (trackCanvas != null) trackCanvas.repaint();
            if (onZonesChanged != null) onZonesChanged.run();
        }
    }

    public void clearAllZones() {
        if (!zones.isEmpty()) {
            zones.clear();
            selectedZoneIndex = -1;
            updateSummary();
            updateButtonStates();
            if (trackCanvas != null) trackCanvas.repaint();
            if (onZonesChanged != null) onZonesChanged.run();
        }
    }

    private void updateTimeDisplay() {
        if (lblTime != null) {
            String text = formatTime(currentTime) + " / " + formatTime(totalDuration);
            if (!text.equals(lblTime.getText())) {
                lblTime.setText(text);
            }
        }
    }

    private void updateButtonStates() {
        if (btnDeleteZone != null) {
            boolean enabled = selectedZoneIndex >= 0 && selectedZoneIndex < zones.size();
            btnDeleteZone.setEnabled(enabled);
            btnDeleteZone.setForeground(enabled ? Color.BLACK : new Color(140, 140, 150));
        }
    }

    private void updateSummary() {
        // Aucun texte résiduel affiché derrière les boutons
    }

    private static String formatTime(double sec) {
        int totalSec = (int) Math.floor(sec);
        int m = totalSec / 60;
        int s = totalSec % 60;
        int tenths = (int) Math.floor((sec - totalSec) * 10.0);
        return String.format(Locale.US, "%02d:%02d.%d", m, s, tenths);
    }

    public JButton getBtnPlayPause() { return btnPlayPause; }
    public JButton getBtnAddZone() { return btnAddZone; }
    public JButton getBtnDeleteZone() { return btnDeleteZone; }
    public JButton getBtnClearAll() { return btnClearAll; }

    private void initUI() {
        // --- Barre d'outils supérieure ---
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        topBar.setOpaque(false);

        btnPlayPause = new JButton("Lecture");
        btnPlayPause.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnPlayPause.setForeground(Color.BLACK);
        btnPlayPause.setBackground(new Color(240, 240, 245));
        btnPlayPause.setFocusPainted(false);
        btnPlayPause.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnPlayPause.setToolTipText("Lire / Mettre en pause la prévisualisation sonore pour placer précisément les balises (Espace)");
        btnPlayPause.addActionListener(e -> {
            if (onTogglePlay != null) onTogglePlay.run();
        });

        lblTime = new JLabel(formatTime(currentTime) + " / " + formatTime(totalDuration));
        lblTime.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblTime.setForeground(new Color(56, 189, 248)); // Sky blue

        btnAddZone = new JButton("Balise voix (3s)");
        btnAddZone.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnAddZone.setForeground(Color.BLACK);
        btnAddZone.setBackground(new Color(245, 158, 11)); // Amber
        btnAddZone.setFocusPainted(false);
        btnAddZone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAddZone.setToolTipText("Insère deux balises espacées de 3 secondes à la position du curseur pour conserver la voix");
        btnAddZone.addActionListener(e -> addZoneAtCursor(3.0));

        btnDeleteZone = new JButton("Supprimer balise");
        btnDeleteZone.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnDeleteZone.setForeground(new Color(140, 140, 150));
        btnDeleteZone.setBackground(new Color(240, 240, 245));
        btnDeleteZone.setFocusPainted(false);
        btnDeleteZone.setEnabled(false);
        btnDeleteZone.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDeleteZone.setToolTipText("Supprimer la balise sélectionnée");
        btnDeleteZone.addActionListener(e -> deleteSelectedZone());

        btnClearAll = new JButton("Tout effacer");
        btnClearAll.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btnClearAll.setForeground(Color.BLACK);
        btnClearAll.setBackground(new Color(240, 240, 245));
        btnClearAll.setFocusPainted(false);
        btnClearAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClearAll.setToolTipText("Supprimer toutes les balises de voix");
        btnClearAll.addActionListener(e -> clearAllZones());

        topBar.add(btnPlayPause);
        topBar.add(lblTime);
        topBar.add(Box.createHorizontalStrut(6));
        topBar.add(btnAddZone);
        topBar.add(btnDeleteZone);
        topBar.add(btnClearAll);

        add(topBar, BorderLayout.NORTH);

        // --- Piste Timeline interactive ---
        trackCanvas = new TrackCanvas();
        trackCanvas.setPreferredSize(new Dimension(600, 50));
        trackCanvas.setMinimumSize(new Dimension(300, 44));
        add(trackCanvas, BorderLayout.CENTER);

        // Raccourci clavier local
        trackCanvas.setFocusable(true);
        trackCanvas.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    if (onTogglePlay != null) onTogglePlay.run();
                } else if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    deleteSelectedZone();
                }
            }
        });
    }

    /**
     * Canevas personnalisé pour le rendu graphique de la timeline et des balises.
     */
    private class TrackCanvas extends JComponent {

        private static final int MARGIN_X = 14;
        private static final int HANDLE_WIDTH = 8;
        private static final int HANDLE_TOLERANCE = 7;

        private static final int DRAG_NONE = 0;
        private static final int DRAG_SCRUB = 1;
        private static final int DRAG_START_HANDLE = 2;
        private static final int DRAG_END_HANDLE = 3;
        private static final int DRAG_ZONE_BODY = 4;

        private int activeDragMode = DRAG_NONE;
        private int activeZoneIdx = -1;
        private double dragAnchorTime = 0.0;
        private double origZoneStart = 0.0;
        private double origZoneEnd = 0.0;

        public TrackCanvas() {
            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    handleMousePressed(e);
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    handleMouseReleased(e);
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    handleMouseDragged(e);
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    handleMouseMoved(e);
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        private int getTrackWidth() {
            return Math.max(10, getWidth() - 2 * MARGIN_X);
        }

        private int timeToX(double t) {
            double ratio = Math.max(0.0, Math.min(1.0, t / totalDuration));
            return MARGIN_X + (int) Math.round(ratio * getTrackWidth());
        }

        private double xToTime(int x) {
            double ratio = (double) (x - MARGIN_X) / getTrackWidth();
            return Math.max(0.0, Math.min(totalDuration, ratio * totalDuration));
        }

        private void handleMouseMoved(MouseEvent e) {
            if (activeDragMode != DRAG_NONE) return;
            int x = e.getX();
            int hitZone = -1;
            int hitHandle = DRAG_NONE;

            for (int i = zones.size() - 1; i >= 0; i--) {
                VoiceZone z = zones.get(i);
                int x1 = timeToX(z.startSec);
                int x2 = timeToX(z.endSec);

                if (Math.abs(x - x1) <= HANDLE_TOLERANCE) {
                    hitZone = i;
                    hitHandle = DRAG_START_HANDLE;
                    break;
                } else if (Math.abs(x - x2) <= HANDLE_TOLERANCE) {
                    hitZone = i;
                    hitHandle = DRAG_END_HANDLE;
                    break;
                } else if (x >= x1 && x <= x2) {
                    hitZone = i;
                    hitHandle = DRAG_ZONE_BODY;
                    break;
                }
            }

            if (hitHandle == DRAG_START_HANDLE) {
                setCursor(Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR));
                setToolTipText("Balise début (glisser pour ajuster le timecode de départ)");
            } else if (hitHandle == DRAG_END_HANDLE) {
                setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                setToolTipText("Balise fin (glisser pour ajuster le timecode de fin)");
            } else if (hitHandle == DRAG_ZONE_BODY) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                setToolTipText("Zone de voix conservée (glisser pour déplacer la zone complète)");
            } else {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Cliquer pour déplacer le curseur de lecture");
            }
        }

        private void handleMousePressed(MouseEvent e) {
            int x = e.getX();
            activeDragMode = DRAG_NONE;
            activeZoneIdx = -1;

            // Détection des poignées de balises ou corps de zone
            for (int i = zones.size() - 1; i >= 0; i--) {
                VoiceZone z = zones.get(i);
                int x1 = timeToX(z.startSec);
                int x2 = timeToX(z.endSec);

                if (Math.abs(x - x1) <= HANDLE_TOLERANCE) {
                    activeDragMode = DRAG_START_HANDLE;
                    activeZoneIdx = i;
                    selectedZoneIndex = i;
                    break;
                } else if (Math.abs(x - x2) <= HANDLE_TOLERANCE) {
                    activeDragMode = DRAG_END_HANDLE;
                    activeZoneIdx = i;
                    selectedZoneIndex = i;
                    break;
                } else if (x >= x1 && x <= x2) {
                    activeDragMode = DRAG_ZONE_BODY;
                    activeZoneIdx = i;
                    selectedZoneIndex = i;
                    dragAnchorTime = xToTime(x);
                    origZoneStart = z.startSec;
                    origZoneEnd = z.endSec;
                    break;
                }
            }

            if (activeDragMode == DRAG_NONE) {
                // Clic dans la piste vide -> scrub de la tête de lecture
                activeDragMode = DRAG_SCRUB;
                selectedZoneIndex = -1;
                currentTime = xToTime(x);
                updateTimeDisplay();
                if (onSeek != null) onSeek.accept(currentTime);
            }

            updateButtonStates();
            repaint();
        }

        private void handleMouseDragged(MouseEvent e) {
            int x = e.getX();
            if (activeDragMode == DRAG_SCRUB) {
                currentTime = xToTime(x);
                updateTimeDisplay();
                if (onSeek != null) onSeek.accept(currentTime);
                repaint();
            } else if (activeZoneIdx >= 0 && activeZoneIdx < zones.size()) {
                VoiceZone z = zones.get(activeZoneIdx);
                double t = xToTime(x);

                if (activeDragMode == DRAG_START_HANDLE) {
                    z.startSec = Math.max(0.0, Math.min(z.endSec - 0.2, t));
                    updateSummary();
                    repaint();
                } else if (activeDragMode == DRAG_END_HANDLE) {
                    z.endSec = Math.min(totalDuration, Math.max(z.startSec + 0.2, t));
                    updateSummary();
                    repaint();
                } else if (activeDragMode == DRAG_ZONE_BODY) {
                    double dur = origZoneEnd - origZoneStart;
                    double delta = t - dragAnchorTime;
                    double newStart = Math.max(0.0, Math.min(totalDuration - dur, origZoneStart + delta));
                    z.startSec = newStart;
                    z.endSec = newStart + dur;
                    updateSummary();
                    repaint();
                }
            }
        }

        private void handleMouseReleased(MouseEvent e) {
            if (activeDragMode != DRAG_NONE) {
                activeDragMode = DRAG_NONE;
                updateSummary();
                if (onZonesChanged != null) onZonesChanged.run();
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int trackH = h - 14;
            int trackY = 7;

            // 1. Fond de la glissière / piste
            g2.setColor(new Color(15, 15, 18));
            g2.fillRoundRect(MARGIN_X, trackY, getTrackWidth(), trackH, 8, 8);

            g2.setColor(new Color(45, 45, 52));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(MARGIN_X, trackY, getTrackWidth(), trackH, 8, 8);

            // 2. Graduations temporelles
            drawGraduations(g2, trackY, trackH);

            // 3. Dessin des zones d'exclusion (balises voix)
            drawZones(g2, trackY, trackH);

            // 4. Curseur de lecture (Playhead)
            drawPlayhead(g2, trackY, trackH);

            g2.dispose();
        }

        private void drawGraduations(Graphics2D g2, int trackY, int trackH) {
            double step = computeTickStep(totalDuration);
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            FontMetrics fm = g2.getFontMetrics();

            for (double t = step; t < totalDuration; t += step) {
                int tx = timeToX(t);
                boolean isMajor = (Math.round(t) % Math.round(step * 2) == 0);

                g2.setColor(isMajor ? new Color(75, 75, 85) : new Color(45, 45, 52));
                int tickLen = isMajor ? 6 : 3;
                g2.drawLine(tx, trackY + 1, tx, trackY + 1 + tickLen);
                g2.drawLine(tx, trackY + trackH - 1 - tickLen, tx, trackY + trackH - 1);

                if (isMajor && getTrackWidth() > 400) {
                    String s = formatTimeShort(t);
                    int tw = fm.stringWidth(s);
                    g2.setColor(new Color(113, 113, 122));
                    g2.drawString(s, tx - tw / 2, trackY + 14);
                }
            }
        }

        private double computeTickStep(double dur) {
            if (dur <= 15) return 1.0;
            if (dur <= 60) return 5.0;
            if (dur <= 180) return 10.0;
            if (dur <= 600) return 30.0;
            return 60.0;
        }

        private String formatTimeShort(double sec) {
            int s = (int) Math.round(sec);
            int m = s / 60;
            int rem = s % 60;
            return String.format(Locale.US, "%d:%02d", m, rem);
        }

        private void drawZones(Graphics2D g2, int trackY, int trackH) {
            int zoneH = trackH - 6;
            int zoneY = trackY + 3;

            for (int i = 0; i < zones.size(); i++) {
                VoiceZone z = zones.get(i);
                int x1 = timeToX(z.startSec);
                int x2 = timeToX(z.endSec);
                int zw = Math.max(HANDLE_WIDTH * 2, x2 - x1);
                boolean isSel = (i == selectedZoneIndex);

                // Remplissage de la zone
                g2.setColor(isSel ? new Color(245, 158, 11, 140) : new Color(217, 119, 6, 85));
                g2.fillRoundRect(x1, zoneY, zw, zoneH, 6, 6);

                // Bordure de la zone
                g2.setColor(isSel ? new Color(251, 191, 36) : new Color(245, 158, 11, 200));
                g2.setStroke(new BasicStroke(isSel ? 2.0f : 1.2f));
                g2.drawRoundRect(x1, zoneY, zw, zoneH, 6, 6);

                // Balise Gauche (handle début)
                drawBaliseHandle(g2, x1, zoneY, zoneH, isSel, true);

                // Balise Droite (handle fin)
                drawBaliseHandle(g2, x2, zoneY, zoneH, isSel, false);

                // Libellé de durée si la largeur le permet
                if (zw >= 54) {
                    g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                    FontMetrics fm = g2.getFontMetrics();
                    String text = String.format(Locale.US, "Voix %.1fs", z.getDuration());
                    int tw = fm.stringWidth(text);
                    int tx = x1 + (zw - tw) / 2;
                    int ty = zoneY + (zoneH + fm.getAscent() - fm.getDescent()) / 2;

                    // Ombre douce pour lisibilité parfaite
                    g2.setColor(new Color(0, 0, 0, 160));
                    g2.drawString(text, tx + 1, ty + 1);
                    g2.setColor(Color.WHITE);
                    g2.drawString(text, tx, ty);
                }
            }
        }

        private void drawBaliseHandle(Graphics2D g2, int hx, int hy, int hh, boolean isSelected, boolean isLeft) {
            int hw = HANDLE_WIDTH;
            int rx = hx - hw / 2;

            g2.setColor(isSelected ? new Color(251, 191, 36) : new Color(245, 158, 11));
            g2.fillRoundRect(rx, hy, hw, hh, 4, 4);

            g2.setColor(new Color(30, 20, 5));
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawRoundRect(rx, hy, hw, hh, 4, 4);

            // Trait central d'adhérence
            g2.setColor(new Color(0, 0, 0, 150));
            g2.drawLine(hx, hy + 5, hx, hy + hh - 5);
        }

        private void drawPlayhead(Graphics2D g2, int trackY, int trackH) {
            int cx = timeToX(currentTime);

            // Trait vertical cyan
            g2.setColor(new Color(56, 189, 248));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawLine(cx, trackY, cx, trackY + trackH);

            // Tête de lecture en losange / pointeur supérieur
            Polygon p = new Polygon();
            p.addPoint(cx - 5, trackY - 2);
            p.addPoint(cx + 5, trackY - 2);
            p.addPoint(cx, trackY + 6);

            g2.setColor(new Color(56, 189, 248));
            g2.fillPolygon(p);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.0f));
            g2.drawPolygon(p);
        }
    }
}
