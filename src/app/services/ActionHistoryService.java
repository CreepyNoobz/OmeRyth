package app.services;

import app.ui.TimelinePanel;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Point;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Service gérant l'historique textuel interactif des répliques et actions de la bande rythmo.
 * <p>
 * Fonctionnalités interactives :
 * <ul>
 *   <li><b>Visualisation chronologique :</b> Affiche en continu la liste ordonnée des répliques
 *       (timecodes de début et de fin, comédien/rôle, contenu textuel).</li>
 *   <li><b>Navigation bidirectionnelle au clic :</b> L'utilisateur peut cliquer directement sur n'importe quelle ligne
 *       de texte dans la zone d'historique pour téléporter instantanément la tête de lecture et la timeline
 *       au timecode précis de la réplique ({@code onSeekRequested}).</li>
 *   <li><b>Détection géométrique précise :</b> Utilise {@code viewToModel2D} et {@code modelToView2D} pour mapper
 *       les coordonnées physiques de la souris vers la ligne de document correspondante, adaptant le curseur
 *       en main interactive ({@link Cursor#HAND_CURSOR}).</li>
 *   <li><b>Rafraîchissement non-bloquant :</b> Met à jour l'affichage périodiquement sans altérer la position
 *       de défilement de l'utilisateur si le texte n'a pas changé.</li>
 * </ul>
 * </p>
 */
public class ActionHistoryService {

    private final int maxLines;
    private final int refreshMs;
    private final JTextArea historyArea;
    private final List<TimelinePanel.ActionHistoryEntry> currentEntries = new ArrayList<>();
    private Consumer<Double> onSeekRequested;
    private Timer refreshTimer;
    private TimelinePanel timelinePanel;
    private app.ui.ImageBackgroundPanel containerPanel;
    private JScrollPane scrollPane;
    private String lastRenderedText = "";

    public ActionHistoryService(int maxLines, int refreshMs) {
        this.maxLines = Math.max(1, maxLines);
        this.refreshMs = Math.max(100, refreshMs);
        this.historyArea = new JTextArea();
        initArea();
    }

    private void initArea() {
        historyArea.setEditable(false);
        historyArea.setFocusable(false);
        historyArea.setLineWrap(true);
        historyArea.setWrapStyleWord(true);
        historyArea.setOpaque(false);
        historyArea.setBackground(new Color(0, 0, 0, 0));
        historyArea.setForeground(new Color(180, 180, 180));
        historyArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        historyArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        historyArea.setText("Historique en attente...");
        historyArea.setToolTipText("Cliquez sur une phrase pour vous y rendre directement.");

        historyArea.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    handleHistoryClick(e.getPoint());
                }
            }
        });

        historyArea.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int line = getEntryIndexAtPoint(e.getPoint());
                if (line >= 0) {
                    historyArea.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    historyArea.setCursor(Cursor.getDefaultCursor());
                }
            }
        });
    }

    public void setOnSeekRequested(Consumer<Double> onSeekRequested) {
        this.onSeekRequested = onSeekRequested;
    }

    public List<TimelinePanel.ActionHistoryEntry> getCurrentEntries() {
        return Collections.unmodifiableList(currentEntries);
    }

    public boolean isHistoryComponent(Component c) {
        if (c == null) return false;
        if (c == historyArea || c == scrollPane || c == containerPanel) return true;
        if (scrollPane != null && (c == scrollPane.getViewport() || c == scrollPane.getVerticalScrollBar() || c == scrollPane.getHorizontalScrollBar())) return true;
        if (containerPanel != null && SwingUtilities.isDescendingFrom(c, containerPanel)) return true;
        if (scrollPane != null && SwingUtilities.isDescendingFrom(c, scrollPane)) return true;
        return false;
    }

    public int getEntryIndexAtPoint(Point pt) {
        if (currentEntries.isEmpty() || pt == null) return -1;
        try {
            int offset = historyArea.viewToModel2D(pt);
            if (offset >= 0 && offset < historyArea.getDocument().getLength()) {
                Rectangle2D r = historyArea.modelToView2D(offset);
                if (r != null && pt.y >= r.getY() - 4 && pt.y <= r.getY() + r.getHeight() + 6) {
                    int line = historyArea.getLineOfOffset(offset);
                    if (line >= 0 && line < currentEntries.size()) {
                        return line;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    public void handleHistoryClick(Point pt) {
        int index = getEntryIndexAtPoint(pt);
        if (index >= 0) {
            triggerEntryClick(index);
        }
    }

    public void triggerEntryClick(int index) {
        if (index >= 0 && index < currentEntries.size()) {
            TimelinePanel.ActionHistoryEntry entry = currentEntries.get(index);
            if (entry != null && onSeekRequested != null) {
                onSeekRequested.accept(entry.startTimeSeconds);
            }
        }
    }

    public JPanel createPanel() {
        scrollPane = new JScrollPane(historyArea);
        scrollPane.setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        scrollPane.setBackground(new Color(0, 0, 0, 0));
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);

        java.awt.event.MouseWheelListener wheelListener = e -> {
            if (scrollPane != null) {
                javax.swing.JScrollBar bar = scrollPane.getVerticalScrollBar();
                if (bar != null) {
                    int rotation = e.getWheelRotation();
                    int amount = e.getScrollAmount();
                    int delta = rotation * bar.getUnitIncrement() * amount;
                    bar.setValue(bar.getValue() + delta);
                    e.consume();
                }
            }
        };

        historyArea.addMouseWheelListener(wheelListener);
        scrollPane.addMouseWheelListener(wheelListener);
        scrollPane.getViewport().addMouseWheelListener(wheelListener);

        containerPanel = new app.ui.ImageBackgroundPanel(new BorderLayout());
        containerPanel.addMouseWheelListener(wheelListener);
        containerPanel.add(scrollPane, BorderLayout.CENTER);
        return containerPanel;
    }

    public void setTheme(Color background, String imagePath) {
        if (containerPanel != null) {
            containerPanel.setBackgroundStyle(background, imagePath);
        }
    }

    /**
     * Associe ce service au {@link TimelinePanel} actif et déclenche un rafraîchissement immédiat des entrées.
     */
    public void bind(TimelinePanel timelinePanel) {
        this.timelinePanel = timelinePanel;
        refreshNow();
    }

    /**
     * Démarre la boucle de mise à jour périodique de l'historique visuel.
     */
    public void start() {
        stop();
        refreshTimer = new Timer(refreshMs, e -> refreshNow());
        refreshTimer.start();
    }

    /**
     * Arrête le minuteur de rafraîchissement périodique.
     */
    public void stop() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    /**
     * Rafraîchit immédiatement le panneau d'historique en synchronisant les données depuis la timeline.
     */
    public void refreshNow() {
        if (timelinePanel == null) return;

        List<TimelinePanel.ActionHistoryEntry> entries = timelinePanel.getAllActionHistory();
        currentEntries.clear();

        if (entries.isEmpty()) {
            if (!"Aucune action pour l'instant.".equals(lastRenderedText)) {
                lastRenderedText = "Aucune action pour l'instant.";
                historyArea.setText(lastRenderedText);
            }
            return;
        }

        currentEntries.addAll(entries);

        StringBuilder sb = new StringBuilder(512);
        for (TimelinePanel.ActionHistoryEntry entry : entries) {
            String start = formatTenths(entry.startTimeSeconds);
            String end = entry.endTimeSeconds == null ? "..." : formatTenths(entry.endTimeSeconds);
            sb.append(entry.active ? "▶ " : "  ")
                    .append(start)
                    .append(" → ")
                    .append(end)
                    .append(" | ")
                    .append(entry.roleName)
                    .append(" : ")
                    .append(entry.text == null ? "" : entry.text)
                    .append('\n');
        }

        String newText = sb.toString();
        if (!newText.equals(lastRenderedText)) {
            // Conserver la position de défilement actuelle de l'utilisateur
            int scrollVal = (scrollPane != null && scrollPane.getVerticalScrollBar() != null)
                    ? scrollPane.getVerticalScrollBar().getValue()
                    : -1;

            lastRenderedText = newText;
            historyArea.setText(newText);

            if (scrollVal >= 0 && scrollPane != null && scrollPane.getVerticalScrollBar() != null) {
                SwingUtilities.invokeLater(() -> {
                    if (scrollPane != null && scrollPane.getVerticalScrollBar() != null) {
                        scrollPane.getVerticalScrollBar().setValue(scrollVal);
                    }
                });
            }
        }
    }

    private static String formatTenths(double seconds) {
        long tenths = Math.max(0L, Math.round(seconds * 10.0));
        long min = tenths / 600L;
        long sec = (tenths / 10L) % 60L;
        long tenth = tenths % 10L;
        return String.format("%02d;%02d.%d", min, sec, tenth);
    }
}

