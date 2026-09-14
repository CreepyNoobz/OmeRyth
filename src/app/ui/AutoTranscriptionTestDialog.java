package app.ui;

import app.services.SpeechWorkflowService;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private JLabel labelScanSummary;
    private JButton btnSwapSpeakers;
    private JButton btnRenameRoles;
    private JButton btnAddSpeaker;
    private JComboBox<String> speakerComboEditor;
    private final Map<Integer, String> customSpeakerNames = new HashMap<>();
    private boolean isUpdatingTable = false;
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

        checkAutoInject = new JCheckBox("⚡ Injection directe sans validation dès la fin du scan", false);
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

        // Résultats — avec colonnes Locuteur interactive et Bande
        resultsPanel = new JPanel(new BorderLayout(5, 5));
        resultsPanel.setBorder(BorderFactory.createTitledBorder("Répliques & Personnages"));

        labelScanSummary = new JLabel("Scan non démarré.");
        labelScanSummary.setFont(new Font("Segoe UI", Font.PLAIN, 12));

        btnSwapSpeakers = new JButton("🔄 Inverser (1 ↔ 2)");
        btnSwapSpeakers.setToolTipText("Inverser toutes les répliques entre Locuteur 1 et Locuteur 2 en 1 clic");
        btnSwapSpeakers.setEnabled(false);

        btnRenameRoles = new JButton("🏷️ Renommer les rôles...");
        btnRenameRoles.setToolTipText("Donner un vrai nom aux personnages (ex: Mbappé, Journaliste...)");
        btnRenameRoles.setEnabled(false);

        btnAddSpeaker = new JButton("➕ Ajouter personnage");
        btnAddSpeaker.setToolTipText("Créer un nouveau rôle personnage pour lui assigner des répliques");
        btnAddSpeaker.setEnabled(false);

        JPanel scanHeaderPanel = new JPanel(new BorderLayout(5, 5));
        scanHeaderPanel.setBorder(BorderFactory.createEmptyBorder(2, 4, 6, 4));
        scanHeaderPanel.add(labelScanSummary, BorderLayout.CENTER);

        JPanel speakerTools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        speakerTools.add(btnSwapSpeakers);
        speakerTools.add(btnRenameRoles);
        speakerTools.add(btnAddSpeaker);
        scanHeaderPanel.add(speakerTools, BorderLayout.EAST);

        resultsPanel.add(scanHeaderPanel, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(new String[]{"#", "Locuteur", "Bande", "Début", "Fin", "Durée", "Texte"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 1; // La colonne Locuteur est modifiable manuellement !
            }
        };
        tableResults = new JTable(tableModel);
        tableResults.getColumnModel().getColumn(0).setPreferredWidth(30);
        tableResults.getColumnModel().getColumn(1).setPreferredWidth(125);
        tableResults.getColumnModel().getColumn(2).setPreferredWidth(65);
        tableResults.getColumnModel().getColumn(3).setPreferredWidth(85);
        tableResults.getColumnModel().getColumn(4).setPreferredWidth(85);
        tableResults.getColumnModel().getColumn(5).setPreferredWidth(55);
        tableResults.getColumnModel().getColumn(6).setPreferredWidth(320);
        tableResults.setDefaultRenderer(Object.class, new AlternateRowRenderer());

        speakerComboEditor = new JComboBox<>();
        tableResults.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(speakerComboEditor));

        tableModel.addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 1) {
                int row = e.getFirstRow();
                if (row >= 0 && currentSegments != null && row < currentSegments.size()) {
                    Object val = tableModel.getValueAt(row, 1);
                    if (val != null) {
                        handleSpeakerCellEdited(row, val.toString());
                    }
                }
            }
        });

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

        btnSwapSpeakers.addActionListener(e -> swapSpeakers());
        btnRenameRoles.addActionListener(e -> renameRolesDialog());
        btnAddSpeaker.addActionListener(e -> addSpeakerDialog());

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
        customSpeakerNames.clear();
        labelScanSummary.setText("Analyse en cours... Recherche des répliques et des locuteurs...");
        btnSwapSpeakers.setEnabled(false);
        btnRenameRoles.setEnabled(false);
        btnAddSpeaker.setEnabled(false);

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
                    isUpdatingTable = true;
                    try {
                        tableModel.addRow(new Object[]{
                                segment.getId(),
                                segment.getSpeakerDisplayName(),
                                "Bande " + (targetBand + 1),
                                formatTimecode(segment.getStartSeconds()),
                                formatTimecode(segment.getEndSeconds()),
                                durationStr,
                                segment.getText()
                        });
                    } finally {
                        isUpdatingTable = false;
                    }
                    updateScanSummary();
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
                    refreshSpeakerNamesAndTable();
                    btnInject.setEnabled(currentSegments != null && !currentSegments.isEmpty());
                    btnStart.setEnabled(true);
                    btnCancel.setEnabled(false);
                    progressBar.setValue(100);
                    labelStatus.setText("Terminé ! Scan complet prêt pour révision.");

                    int count = currentSegments != null ? currentSegments.size() : 0;

                    if (checkAutoInject.isSelected() && count > 0) {
                        int targetBand = comboBandTarget.getSelectedIndex();
                        onInjectCallback.accept(new ArrayList<>(currentSegments), targetBand);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(AutoTranscriptionTestDialog.this,
                                count + " répliques analysées !\n\n" +
                                "👉 Vous pouvez modifier le locuteur de chaque réplique directement dans le tableau ci-dessous,\n" +
                                "   inverser les rôles ou les renommer avant injection.\n\n" +
                                "Cliquez sur '📥 Injecter dans la bande rythmo' pour valider.",
                                "Scan terminé - Révision disponible", JOptionPane.INFORMATION_MESSAGE);
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

    private void updateScanSummary() {
        List<SpeechWorkflowService.TranscriptionSegment> segs =
                (currentSegments != null && !currentSegments.isEmpty()) ? currentSegments : liveSegments;
        if (segs == null || segs.isEmpty()) {
            labelScanSummary.setText("Scan : En attente de répliques...");
            btnSwapSpeakers.setEnabled(false);
            btnRenameRoles.setEnabled(false);
            btnAddSpeaker.setEnabled(false);
            return;
        }

        Map<Integer, Integer> countPerSpeaker = new TreeMap<>();
        for (SpeechWorkflowService.TranscriptionSegment s : segs) {
            countPerSpeaker.put(s.getSpeakerIndex(), countPerSpeaker.getOrDefault(s.getSpeakerIndex(), 0) + 1);
        }

        StringBuilder sb = new StringBuilder("<html><b>Scan :</b> ");
        sb.append(segs.size()).append(" réplique(s) — ");
        sb.append(countPerSpeaker.size()).append(" personnage(s) : ");
        boolean first = true;
        for (Map.Entry<Integer, Integer> entry : countPerSpeaker.entrySet()) {
            if (!first) sb.append(", ");
            int idx = entry.getKey();
            String name = customSpeakerNames.getOrDefault(idx, "Locuteur " + (idx + 1));
            sb.append("<b>").append(name).append("</b> (").append(entry.getValue()).append(")");
            first = false;
        }
        sb.append("</html>");
        labelScanSummary.setText(sb.toString());

        btnSwapSpeakers.setEnabled(countPerSpeaker.size() >= 2);
        btnRenameRoles.setEnabled(!countPerSpeaker.isEmpty());
        btnAddSpeaker.setEnabled(true);
    }

    private void refreshSpeakerNamesAndTable() {
        isUpdatingTable = true;
        try {
            Set<Integer> allIndices = new TreeSet<>();
            List<SpeechWorkflowService.TranscriptionSegment> segs =
                    (currentSegments != null && !currentSegments.isEmpty()) ? currentSegments : liveSegments;
            if (segs != null) {
                for (SpeechWorkflowService.TranscriptionSegment s : segs) {
                    allIndices.add(s.getSpeakerIndex());
                }
            }
            allIndices.addAll(customSpeakerNames.keySet());
            if (allIndices.isEmpty()) {
                allIndices.add(0);
                allIndices.add(1);
            } else if (allIndices.size() == 1) {
                allIndices.add(1);
            }

            speakerComboEditor.removeAllItems();
            for (int idx : allIndices) {
                String custom = customSpeakerNames.get(idx);
                if (custom != null && !custom.isBlank()) {
                    speakerComboEditor.addItem(custom + " (Locuteur " + (idx + 1) + ")");
                } else {
                    speakerComboEditor.addItem("Locuteur " + (idx + 1));
                }
            }

            if (segs != null) {
                for (SpeechWorkflowService.TranscriptionSegment s : segs) {
                    if (customSpeakerNames.containsKey(s.getSpeakerIndex())) {
                        s.customRoleName = customSpeakerNames.get(s.getSpeakerIndex());
                    }
                }
            }

            populateTable(segs);
            updateScanSummary();
        } finally {
            isUpdatingTable = false;
        }
    }

    private void handleSpeakerCellEdited(int row, String selectedValue) {
        if (isUpdatingTable || selectedValue == null) return;
        List<SpeechWorkflowService.TranscriptionSegment> segs =
                (currentSegments != null && !currentSegments.isEmpty()) ? currentSegments : liveSegments;
        if (segs == null || row < 0 || row >= segs.size()) return;

        SpeechWorkflowService.TranscriptionSegment seg = segs.get(row);
        int targetIdx = -1;

        Matcher m = Pattern.compile("Locuteur\\s*(\\d+)").matcher(selectedValue);
        if (m.find()) {
            try {
                targetIdx = Integer.parseInt(m.group(1)) - 1;
            } catch (Exception ignored) {}
        } else {
            for (Map.Entry<Integer, String> entry : customSpeakerNames.entrySet()) {
                if (selectedValue.trim().equalsIgnoreCase(entry.getValue().trim())) {
                    targetIdx = entry.getKey();
                    break;
                }
            }
        }

        if (targetIdx >= 0) {
            seg.speaker = String.format("SPEAKER_%02d", targetIdx);
            seg.customRoleName = customSpeakerNames.get(targetIdx);

            int baseBand = comboBandTarget.getSelectedIndex();
            boolean multiBand = checkMultiBandPerSpeaker.isSelected();
            int targetBand = multiBand ? (baseBand + seg.getSpeakerIndex()) : baseBand;

            isUpdatingTable = true;
            try {
                tableModel.setValueAt("Bande " + (targetBand + 1), row, 2);
            } finally {
                isUpdatingTable = false;
            }

            updateScanSummary();
        }
    }

    private void swapSpeakers() {
        List<SpeechWorkflowService.TranscriptionSegment> segs =
                (currentSegments != null && !currentSegments.isEmpty()) ? currentSegments : liveSegments;
        if (segs == null || segs.isEmpty()) return;

        for (SpeechWorkflowService.TranscriptionSegment seg : segs) {
            int idx = seg.getSpeakerIndex();
            if (idx == 0) {
                seg.speaker = "SPEAKER_01";
                seg.customRoleName = customSpeakerNames.get(1);
            } else if (idx == 1) {
                seg.speaker = "SPEAKER_00";
                seg.customRoleName = customSpeakerNames.get(0);
            }
        }

        String name0 = customSpeakerNames.get(0);
        String name1 = customSpeakerNames.get(1);
        if (name0 != null || name1 != null) {
            if (name1 != null) customSpeakerNames.put(0, name1); else customSpeakerNames.remove(0);
            if (name0 != null) customSpeakerNames.put(1, name0); else customSpeakerNames.remove(1);
        }

        refreshSpeakerNamesAndTable();
        JOptionPane.showMessageDialog(this,
                "Les répliques du Locuteur 1 et du Locuteur 2 ont été inversées avec succès !",
                "Inversion effectuée", JOptionPane.INFORMATION_MESSAGE);
    }

    private void renameRolesDialog() {
        List<SpeechWorkflowService.TranscriptionSegment> segs =
                (currentSegments != null && !currentSegments.isEmpty()) ? currentSegments : liveSegments;
        if (segs == null || segs.isEmpty()) return;

        Set<Integer> activeSpeakers = new TreeSet<>();
        for (SpeechWorkflowService.TranscriptionSegment s : segs) {
            activeSpeakers.add(s.getSpeakerIndex());
        }
        activeSpeakers.addAll(customSpeakerNames.keySet());

        JPanel panel = new JPanel(new GridLayout(0, 2, 8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        Map<Integer, JTextField> fields = new HashMap<>();
        for (int idx : activeSpeakers) {
            String currentName = customSpeakerNames.getOrDefault(idx, "Locuteur " + (idx + 1));
            panel.add(new JLabel("Locuteur " + (idx + 1) + " :"));
            JTextField tf = new JTextField(currentName, 15);
            fields.put(idx, tf);
            panel.add(tf);
        }

        int res = JOptionPane.showConfirmDialog(this, panel,
                "Renommer les personnages / rôles", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            for (Map.Entry<Integer, JTextField> entry : fields.entrySet()) {
                String val = entry.getValue().getText().trim();
                if (!val.isBlank()) {
                    customSpeakerNames.put(entry.getKey(), val);
                }
            }
            refreshSpeakerNamesAndTable();
        }
    }

    private void addSpeakerDialog() {
        String name = JOptionPane.showInputDialog(this,
                "Entrez le nom du nouveau personnage à ajouter :\n(Il sera immédiatement disponible dans la liste déroulante)",
                "Nouveau Personnage", JOptionPane.QUESTION_MESSAGE);
        if (name != null && !name.trim().isBlank()) {
            int nextIdx = getNextSpeakerIndex();
            customSpeakerNames.put(nextIdx, name.trim());
            refreshSpeakerNamesAndTable();
            JOptionPane.showMessageDialog(this,
                    "Personnage '" + name.trim() + "' ajouté (assigné au Locuteur " + (nextIdx + 1) + ").\n" +
                    "Vous pouvez maintenant l'attribuer aux répliques dans la colonne 'Locuteur'.",
                    "Personnage ajouté", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private int getNextSpeakerIndex() {
        int max = -1;
        List<SpeechWorkflowService.TranscriptionSegment> segs =
                (currentSegments != null && !currentSegments.isEmpty()) ? currentSegments : liveSegments;
        if (segs != null) {
            for (SpeechWorkflowService.TranscriptionSegment s : segs) {
                max = Math.max(max, s.getSpeakerIndex());
            }
        }
        for (int k : customSpeakerNames.keySet()) {
            max = Math.max(max, k);
        }
        return max + 1;
    }

    private void populateTable(List<SpeechWorkflowService.TranscriptionSegment> segments) {
        isUpdatingTable = true;
        try {
            tableModel.setRowCount(0);
            if (segments == null) return;
            int baseBand = comboBandTarget.getSelectedIndex();
            boolean multiBand = checkMultiBandPerSpeaker.isSelected();
            for (SpeechWorkflowService.TranscriptionSegment seg : segments) {
                String durationStr = String.format("%.2f", seg.getDuration());
                int targetBand = multiBand ? (baseBand + seg.getSpeakerIndex()) : baseBand;
                String speakerDisp = seg.getSpeakerDisplayName();
                String custom = customSpeakerNames.get(seg.getSpeakerIndex());
                if (custom != null && !custom.isBlank()) {
                    speakerDisp = custom + " (Locuteur " + (seg.getSpeakerIndex() + 1) + ")";
                }
                tableModel.addRow(new Object[]{
                        seg.getId(),
                        speakerDisp,
                        "Bande " + (targetBand + 1),
                        formatTimecode(seg.getStartSeconds()),
                        formatTimecode(seg.getEndSeconds()),
                        durationStr,
                        seg.getText()
                });
            }
        } finally {
            isUpdatingTable = false;
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
