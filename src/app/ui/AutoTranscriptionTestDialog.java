package app.ui;

import app.services.SpeechWorkflowService;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Boîte de dialogue pour la transcription automatique vocale.
 * Moteur faster-whisper + Silero VAD haute précision.
 *
 * Permet de choisir la bande de destination (bande active par défaut)
 * et injecte les répliques avec des séparateurs Début / Fin pour une
 * lisibilité maximale sur la bande rythmo.
 */
public class AutoTranscriptionTestDialog extends JDialog {

    private final File videoFile;
    /** Callback: (segments, targetBand) → inject into timeline. */
    private final BiConsumer<List<SpeechWorkflowService.TranscriptionSegment>, Integer> onInjectCallback;
    private final SpeechWorkflowService service = new SpeechWorkflowService();

    private JComboBox<String> comboSpeakers;
    private JComboBox<String> comboLanguage;
    private JComboBox<String> comboModel;
    private JComboBox<String> comboCpuProfile;
    private JComboBox<String> comboBandTarget;
    private JCheckBox checkMultiBandPerSpeaker;

    private JPanel progressPanel;
    private JProgressBar progressBar;
    private JLabel labelStatus;
    private JLabel labelTime;

    private JPanel resultsPanel;
    private JTable tableResults;
    private DefaultTableModel tableModel;

    private JButton btnStart;
    private JButton btnCancel;
    private JButton btnInject;
    private JButton btnClose;

    private Timer timer;
    private long startTime;
    private List<SpeechWorkflowService.TranscriptionSegment> currentSegments;
    private final List<SpeechWorkflowService.TranscriptionSegment> liveSegments = Collections.synchronizedList(new ArrayList<>());
    private JCheckBox checkAutoInject;
    private boolean isRunning = false;

    /**
     * Constructeur compatible avec l'ancien callback (sans choix de bande).
     * La bande de destination sera toujours 0.
     */
    public AutoTranscriptionTestDialog(Frame parent, File videoFile,
                                       java.util.function.Consumer<List<SpeechWorkflowService.TranscriptionSegment>> legacyCallback) {
        this(parent, videoFile, (segments, band) -> legacyCallback.accept(segments), 0, 4);
    }

    /**
     * Constructeur principal avec choix de bande.
     *
     * @param parent        Fenêtre parente.
     * @param videoFile     Vidéo chargée.
     * @param onInject      Callback (segments, targetBand).
     * @param activeBand    Bande actuellement sélectionnée dans la timeline.
     * @param bandCount     Nombre total de bandes disponibles.
     */
    public AutoTranscriptionTestDialog(Frame parent, File videoFile,
                                       BiConsumer<List<SpeechWorkflowService.TranscriptionSegment>, Integer> onInject,
                                       int activeBand, int bandCount) {
        super(parent, "🎙️ Transcription Vocale – Haute Précision & Multi-Locuteurs", true);
        this.videoFile = videoFile;
        this.onInjectCallback = onInject;

        initComponents(activeBand, bandCount);
        layoutComponents();
        setupListeners();

        setSize(780, 670);
        setLocationRelativeTo(parent);
        setResizable(true);

        // Anti-processus fantômes : Arrêter le moteur si on ferme la fenêtre
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelAnalysis();
            }
        });
    }

    private void initComponents(int activeBand, int bandCount) {
        // Paramètres
        comboSpeakers = new JComboBox<>(new String[]{
                "Auto-détection (Trouver tous les personnages différents)",
                "1 personne (Solo — 1 bande)",
                "2 personnes (Dialogue — 2 bandes)",
                "3 personnes (3 bandes)",
                "4 personnes (4 bandes)",
                "5 personnes (5 bandes)",
                "6 personnes (6 bandes)",
                "8 personnes (8 bandes)"
        });
        comboSpeakers.setSelectedIndex(0);

        checkMultiBandPerSpeaker = new JCheckBox("👥 Répartir chaque personne sur sa propre bande (1 bande par locuteur)", true);
        checkMultiBandPerSpeaker.setFont(new Font("Segoe UI", Font.BOLD, 12));

        comboLanguage = new JComboBox<>(new String[]{
                "Français (fr)", "Anglais (en)", "Espagnol (es)",
                "Allemand (de)", "Italien (it)", "Auto-détection (auto)"
        });

        comboModel = new JComboBox<>(new String[]{
                "⚡ Base — Ultra-Rapide (Vitesse Maximale)",
                "🎯 Small — Haute Précision (Recommandé)",
                "🌟 Medium — Précision Maximale (Plus lent)",
                "⚡ Tiny — Éclair"
        });
        comboModel.setSelectedIndex(1); // Small sélectionné par défaut

        int totalCores = Runtime.getRuntime().availableProcessors();
        int balancedThreads = Math.max(4, Math.min(8, totalCores));
        int maxThreads = Math.max(2, totalCores);

        boolean hasCuda = SpeechWorkflowService.isCudaAvailable();
        if (hasCuda) {
            comboCpuProfile = new JComboBox<>(new String[]{
                    "🚀 GPU NVIDIA (Accélération CUDA Tensor Cores — Ultra-Rapide ~1 min pour 1h)",
                    "⚡ CPU Multi-cœurs Turbo (" + maxThreads + " cœurs)",
                    "🤫 CPU Économe / Silencieux (2 cœurs)"
            });
        } else {
            comboCpuProfile = new JComboBox<>(new String[]{
                    "⚡ CPU Turbo / Équilibré (" + balancedThreads + " cœurs — Recommandé)",
                    "🚀 CPU Maximum (" + maxThreads + " cœurs — Pleine Puissance)",
                    "🤫 CPU Économe / Silencieux (2 cœurs)"
            });
        }
        comboCpuProfile.setSelectedIndex(0);

        // Destination band selector
        int safeBandCount = Math.max(1, bandCount);
        String[] bandOptions = new String[safeBandCount];
        for (int i = 0; i < safeBandCount; i++) {
            String label = "Bande " + (i + 1);
            if (i == activeBand) label += " (active)";
            bandOptions[i] = label;
        }
        comboBandTarget = new JComboBox<>(bandOptions);
        comboBandTarget.setSelectedIndex(Math.max(0, Math.min(activeBand, safeBandCount - 1)));

        checkAutoInject = new JCheckBox("⚡ Injection directe dans la timeline dès que la transcription est terminée", true);
        checkAutoInject.setFont(new Font("Segoe UI", Font.BOLD, 12));

        // Progression
        progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setBorder(BorderFactory.createTitledBorder("Progression en direct"));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        labelStatus = new JLabel("En attente...", SwingConstants.CENTER);
        labelTime = new JLabel("00:00", SwingConstants.CENTER);
        progressPanel.add(progressBar, BorderLayout.CENTER);
        JPanel statusTimePanel = new JPanel(new GridLayout(2, 1));
        statusTimePanel.add(labelStatus);
        statusTimePanel.add(labelTime);
        progressPanel.add(statusTimePanel, BorderLayout.SOUTH);
        progressPanel.setVisible(false);

        // Résultats — avec colonnes Locuteur et Bande
        resultsPanel = new JPanel(new BorderLayout(5, 5));
        resultsPanel.setBorder(BorderFactory.createTitledBorder("Répliques détectées"));
        tableModel = new DefaultTableModel(new String[]{"#", "Locuteur", "Bande", "Début", "Fin", "Durée", "Texte"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableResults = new JTable(tableModel);
        tableResults.getColumnModel().getColumn(0).setPreferredWidth(30);
        tableResults.getColumnModel().getColumn(1).setPreferredWidth(85);
        tableResults.getColumnModel().getColumn(2).setPreferredWidth(65);
        tableResults.getColumnModel().getColumn(3).setPreferredWidth(85);
        tableResults.getColumnModel().getColumn(4).setPreferredWidth(85);
        tableResults.getColumnModel().getColumn(5).setPreferredWidth(55);
        tableResults.getColumnModel().getColumn(6).setPreferredWidth(340);
        tableResults.setDefaultRenderer(Object.class, new AlternateRowRenderer());

        JScrollPane scrollPane = new JScrollPane(tableResults);
        scrollPane.setPreferredSize(new Dimension(720, 200));
        resultsPanel.add(scrollPane, BorderLayout.CENTER);
        resultsPanel.setVisible(false);

        // Boutons
        btnStart = new JButton("▶ Lancer l'analyse");
        btnStart.setFont(btnStart.getFont().deriveFont(Font.BOLD));
        btnStart.setForeground(new Color(0, 120, 0));

        btnCancel = new JButton("⏹ Annuler");
        btnCancel.setEnabled(false);

        btnInject = new JButton("📥 Injecter dans la bande rythmo");
        btnInject.setEnabled(false);

        btnClose = new JButton("❌ Fermer");

        timer = new Timer(1000, e -> updateTime());
    }

    private void layoutComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // Header
        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        JLabel titleLabel = new JLabel("<html><b>🧪 Transcription Vocale Haute Précision & Multi-Locuteurs</b></html>");
        titleLabel.setFont(titleLabel.getFont().deriveFont(17f));
        JLabel subtitleLabel = new JLabel("Moteur faster-whisper + VAD Silero + Diarisation — Chaque interlocuteur sur sa propre bande.");
        headerPanel.add(titleLabel);
        headerPanel.add(subtitleLabel);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Centre
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

        JPanel paramsPanel = new JPanel(new GridBagLayout());
        paramsPanel.setBorder(BorderFactory.createTitledBorder("Paramètres d'analyse"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; paramsPanel.add(new JLabel("Nombre d'interlocuteurs :"), gbc);
        gbc.gridx = 1; gbc.gridy = 0; paramsPanel.add(comboSpeakers, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        paramsPanel.add(checkMultiBandPerSpeaker, gbc);
        gbc.gridwidth = 1;

        gbc.gridx = 0; gbc.gridy = 2; paramsPanel.add(new JLabel("Bande de départ :"), gbc);
        gbc.gridx = 1; gbc.gridy = 2; paramsPanel.add(comboBandTarget, gbc);

        gbc.gridx = 0; gbc.gridy = 3; paramsPanel.add(new JLabel("Langue :"), gbc);
        gbc.gridx = 1; gbc.gridy = 3; paramsPanel.add(comboLanguage, gbc);

        gbc.gridx = 0; gbc.gridy = 4; paramsPanel.add(new JLabel("Modèle IA :"), gbc);
        gbc.gridx = 1; gbc.gridy = 4; paramsPanel.add(comboModel, gbc);

        gbc.gridx = 0; gbc.gridy = 5; paramsPanel.add(new JLabel("Accélération matérielle :"), gbc);
        gbc.gridx = 1; gbc.gridy = 5; paramsPanel.add(comboCpuProfile, gbc);

        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        paramsPanel.add(checkAutoInject, gbc);

        centerPanel.add(paramsPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(progressPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(resultsPanel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Boutons
        JPanel buttonsPanel = new JPanel(new BorderLayout());
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftButtons.add(btnCancel);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        rightButtons.add(btnStart);
        rightButtons.add(btnInject);
        rightButtons.add(btnClose);

        buttonsPanel.add(leftButtons, BorderLayout.WEST);
        buttonsPanel.add(rightButtons, BorderLayout.EAST);

        mainPanel.add(buttonsPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void setupListeners() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelAnalysis();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                cancelAnalysis();
            }
        });

        btnClose.addActionListener(e -> {
            cancelAnalysis();
            dispose();
        });

        btnCancel.addActionListener(e -> cancelAnalysis());

        btnStart.addActionListener(e -> startAnalysis());

        btnInject.addActionListener(e -> {
            List<SpeechWorkflowService.TranscriptionSegment> toInject =
                    (currentSegments != null && !currentSegments.isEmpty()) ? currentSegments : liveSegments;
            if (toInject != null && !toInject.isEmpty()) {
                int targetBand = comboBandTarget.getSelectedIndex();
                onInjectCallback.accept(new ArrayList<>(toInject), targetBand);
                dispose();
            } else {
                JOptionPane.showMessageDialog(this, "Aucune réplique disponible à injecter.", "Information", JOptionPane.WARNING_MESSAGE);
            }
        });
    }

    private void startAnalysis() {
        if (videoFile == null || !videoFile.exists()) {
            JOptionPane.showMessageDialog(this, "Aucune vidéo chargée.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        isRunning = true;
        btnStart.setEnabled(false);
        btnCancel.setEnabled(true);
        btnInject.setEnabled(false);
        progressPanel.setVisible(true);
        resultsPanel.setVisible(true);
        tableModel.setRowCount(0);
        liveSegments.clear();
        currentSegments = null;

        startTime = System.currentTimeMillis();
        timer.start();

        int spkSelection = comboSpeakers.getSelectedIndex();
        int numSpeakers = switch (spkSelection) {
            case 0 -> 0; // Auto-détection (Trouver tous les personnages différents)
            case 1 -> 1; // 1 personne (Solo)
            case 2 -> 2; // 2 personnes (Dialogue)
            case 3 -> 3; // 3 personnes
            case 4 -> 4; // 4 personnes
            case 5 -> 5; // 5 personnes
            case 6 -> 6; // 6 personnes
            case 7 -> 8; // 8 personnes
            default -> 0; // Auto-détection
        };

        String langDisplay = comboLanguage.getSelectedItem().toString();
        String language = langDisplay.replaceAll(".*\\((.+)\\)", "$1");

        String modelDisplay = comboModel.getSelectedItem().toString().toLowerCase();
        String model = modelDisplay.contains("medium") ? "medium" :
                       modelDisplay.contains("small") ? "small" :
                       modelDisplay.contains("tiny") ? "tiny" : "base";

        String hwSelection = comboCpuProfile.getSelectedItem().toString();
        String device = hwSelection.contains("GPU") ? "cuda" : "cpu";

        int cpuIdx = comboCpuProfile.getSelectedIndex();
        int totalCores = Runtime.getRuntime().availableProcessors();
        int threads = switch (cpuIdx) {
            case 0 -> hwSelection.contains("GPU") ? 4 : Math.max(4, Math.min(8, totalCores));
            case 1 -> Math.max(2, totalCores);
            default -> 2;
        };

        service.transcribe(videoFile, numSpeakers, language, model, threads, device, new SpeechWorkflowService.TranscriptionCallback() {
            @Override
            public void onProgress(int percentage, String message) {
                SwingUtilities.invokeLater(() -> {
                    if (percentage >= 0) {
                        progressBar.setValue(percentage);
                    }
                    progressBar.setString(message);
                    labelStatus.setText(message);
                });
            }

            @Override
            public void onSegmentFound(SpeechWorkflowService.TranscriptionSegment segment) {
                liveSegments.add(segment);
                SwingUtilities.invokeLater(() -> {
                    String durationStr = String.format("%.2f", segment.getDuration());
                    int baseBand = comboBandTarget.getSelectedIndex();
                    boolean multiBand = checkMultiBandPerSpeaker.isSelected();
                    int targetBand = multiBand ? (baseBand + segment.getSpeakerIndex()) : baseBand;
                    tableModel.addRow(new Object[]{
                            segment.getId(),
                            segment.getSpeakerDisplayName(),
                            "Bande " + (targetBand + 1),
                            formatTimecode(segment.getStartSeconds()),
                            formatTimecode(segment.getEndSeconds()),
                            durationStr,
                            segment.getText()
                    });
                    // Auto-scroll vers le dernier élément détecté
                    int lastRow = tableModel.getRowCount() - 1;
                    if (lastRow >= 0) {
                        tableResults.scrollRectToVisible(tableResults.getCellRect(lastRow, 0, true));
                    }
                });
            }

            @Override
            public void onComplete(SpeechWorkflowService.TranscriptionResult result) {
                SwingUtilities.invokeLater(() -> {
                    isRunning = false;
                    timer.stop();
                    if (result.segments != null && !result.segments.isEmpty()) {
                        currentSegments = result.segments;
                    } else if (!liveSegments.isEmpty()) {
                        currentSegments = new ArrayList<>(liveSegments);
                    }
                    populateTable(currentSegments);
                    btnInject.setEnabled(currentSegments != null && !currentSegments.isEmpty());
                    btnStart.setEnabled(true);
                    btnCancel.setEnabled(false);
                    progressBar.setValue(100);
                    labelStatus.setText("Terminé ! Rapport généré à côté de la vidéo.");

                    int count = currentSegments != null ? currentSegments.size() : 0;

                    if (checkAutoInject.isSelected() && count > 0) {
                        int targetBand = comboBandTarget.getSelectedIndex();
                        onInjectCallback.accept(new ArrayList<>(currentSegments), targetBand);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(AutoTranscriptionTestDialog.this,
                                count + " répliques détectées avec succès !\n" +
                                "Cliquez sur 'Injecter dans la bande rythmo' pour les ajouter à la timeline.\n" +
                                "Rapport détaillé : " + (result.reportFile != null ? result.reportFile.getName() : ""),
                                "Transcription terminée", JOptionPane.INFORMATION_MESSAGE);
                    }
                });
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    isRunning = false;
                    timer.stop();
                    btnStart.setEnabled(true);
                    btnCancel.setEnabled(false);
                    labelStatus.setText("Erreur");
                    JOptionPane.showMessageDialog(AutoTranscriptionTestDialog.this,
                            message, "Erreur d'analyse", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private void cancelAnalysis() {
        try {
            service.cancel();
        } catch (Throwable ignored) {}
        if (isRunning) {
            isRunning = false;
            timer.stop();
            btnStart.setEnabled(true);
            btnCancel.setEnabled(false);
            labelStatus.setText("Analyse annulée.");
            progressBar.setValue(0);
        }
    }

    private void populateTable(List<SpeechWorkflowService.TranscriptionSegment> segments) {
        tableModel.setRowCount(0);
        if (segments == null) return;
        int baseBand = comboBandTarget.getSelectedIndex();
        boolean multiBand = checkMultiBandPerSpeaker.isSelected();
        for (SpeechWorkflowService.TranscriptionSegment seg : segments) {
            String durationStr = String.format("%.2f", seg.getDuration());
            int targetBand = multiBand ? (baseBand + seg.getSpeakerIndex()) : baseBand;
            tableModel.addRow(new Object[]{
                    seg.getId(),
                    seg.getSpeakerDisplayName(),
                    "Bande " + (targetBand + 1),
                    formatTimecode(seg.getStartSeconds()),
                    formatTimecode(seg.getEndSeconds()),
                    durationStr,
                    seg.getText()
            });
        }
    }

    private void updateTime() {
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        long m = elapsed / 60;
        long s = elapsed % 60;
        labelTime.setText(String.format("Temps écoulé : %02d:%02d", m, s));
    }

    private static String formatTimecode(double seconds) {
        int h = (int) (seconds / 3600);
        int m = (int) ((seconds % 3600) / 60);
        int s = (int) (seconds % 60);
        int ms = (int) ((seconds * 1000) % 1000);
        return String.format("%02d:%02d:%02d.%03d", h, m, s, ms);
    }

    /** Simple alternating row renderer for readability. */
    private static class AlternateRowRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                c.setBackground(row % 2 == 0 ? new Color(0xF7, 0xF7, 0xF7) : Color.WHITE);
                c.setForeground(Color.BLACK);
            }
            return c;
        }
    }
}
