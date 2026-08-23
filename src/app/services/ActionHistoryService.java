package app.services;

import app.ui.TimelinePanel;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Font;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JPanel;

public class ActionHistoryService {

    private final int maxLines;
    private final int refreshMs;
    private final JTextArea historyArea;
    private Timer refreshTimer;
    private TimelinePanel timelinePanel;

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
    }

    private app.ui.ImageBackgroundPanel containerPanel;

    public JPanel createPanel() {
        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        scrollPane.setBackground(new Color(0, 0, 0, 0));
        scrollPane.getViewport().setOpaque(false);
        scrollPane.getViewport().setBackground(new Color(0, 0, 0, 0));
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        
        containerPanel = new app.ui.ImageBackgroundPanel(new BorderLayout());
        containerPanel.add(scrollPane, BorderLayout.CENTER);
        return containerPanel;
    }

    public void setTheme(Color background, String imagePath) {
        if (containerPanel != null) {
            containerPanel.setBackgroundStyle(background, imagePath);
        }
    }

    /** Bind this service to a TimelinePanel and perform an immediate refresh. */
    public void bind(TimelinePanel timelinePanel) {
        this.timelinePanel = timelinePanel;
        refreshNow();
    }

    /** Start periodic refresh of the action history display. */
    public void start() {
        stop();
        refreshTimer = new Timer(refreshMs, e -> refreshNow());
        refreshTimer.start();
    }

    /** Stop the periodic refresh timer if running. */
    public void stop() {
        if (refreshTimer != null) {
            refreshTimer.stop();
            refreshTimer = null;
        }
    }

    /** Immediately refresh the action history panel using the timeline's data. */
    public void refreshNow() {
        if (timelinePanel == null) return;

        List<TimelinePanel.ActionHistoryEntry> entries = timelinePanel.getActionHistory(maxLines);
        if (entries.isEmpty()) {
            historyArea.setText("Aucune action pour l'instant.");
            return;
        }

        StringBuilder sb = new StringBuilder(512);
        for (TimelinePanel.ActionHistoryEntry entry : entries) {
            String start = formatTenths(entry.startTimeSeconds);
            String end = entry.endTimeSeconds == null ? "..." : formatTenths(entry.endTimeSeconds);
            sb.append(entry.active ? "> " : "  ")
                    .append(start)
                    .append(" -> ")
                    .append(end)
                    .append(" | ")
                    .append(entry.roleName)
                    .append(" : ")
                    .append(entry.text == null ? "" : entry.text)
                    .append('\n');
        }
        historyArea.setText(sb.toString());
        historyArea.setCaretPosition(historyArea.getDocument().getLength());
    }

    private static String formatTenths(double seconds) {
        long tenths = Math.max(0L, Math.round(seconds * 10.0));
        long min = tenths / 600L;
        long sec = (tenths / 10L) % 60L;
        long tenth = tenths % 10L;
        return String.format("%02d;%02d.%d", min, sec, tenth);
    }
}
