package app.ui;

import app.services.DependencyManagerService;
import app.services.DependencyManagerService.ComponentStatus;
import app.services.DependencyManagerService.ComponentType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

/**
 * Boîte de dialogue affichée au démarrage si un ou plusieurs composants
 * essentiels (FFmpeg, LibVLC, Python IA) sont absents.
 */
public class DependencySetupDialog extends JDialog {

    private final JPanel componentsListPanel;
    private final JButton actionButton;
    private final JButton skipButton;
    private final JProgressBar progressBar;
    private final JLabel progressLabel;
    private boolean completed = false;

    public DependencySetupDialog(Frame parent) {
        super(parent, "OmeRyth — Composants Essentiels", true);
        setSize(650, 480);
        setMinimumSize(new Dimension(500, 360));
        setLocationRelativeTo(parent);
        setResizable(true);
        setLayout(new BorderLayout(15, 15));

        // Fond OmeRyth
        getContentPane().setBackground(OmeTheme.bg());

        // ── En-tête ──
        add(OmeTheme.createDialogHeader(
                "SYSTÈME",
                "Initialisation des Composants",
                "OmeRyth intègre des modules autonomes (FFmpeg, LibVLC, IA) sans configuration complexe."
        ), BorderLayout.NORTH);

        // ── Liste des composants ──
        componentsListPanel = new JPanel();
        componentsListPanel.setLayout(new BoxLayout(componentsListPanel, BoxLayout.Y_AXIS));
        componentsListPanel.setBackground(OmeTheme.bg());
        componentsListPanel.setBorder(new EmptyBorder(16, 22, 12, 22));

        JScrollPane scrollPane = new JScrollPane(componentsListPanel);
        OmeTheme.styleBorderlessScrollPane(scrollPane);
        add(scrollPane, BorderLayout.CENTER);

        // ── Pied de page & Actions ──
        JPanel footerPanel = new JPanel(new BorderLayout(10, 10));
        footerPanel.setBackground(OmeTheme.bgRaised());
        footerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, OmeTheme.border()),
                new EmptyBorder(12, 22, 16, 22)
        ));

        // Zone de progression
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setOpaque(false);

        progressLabel = new JLabel("Prêt pour la configuration");
        progressLabel.setFont(OmeTheme.fontMono(Font.PLAIN, 11));
        progressLabel.setForeground(OmeTheme.textSecondary());
        progressLabel.setVisible(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        OmeTheme.styleProgressBar(progressBar);
        progressBar.setVisible(false);

        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        // Boutons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonsPanel.setOpaque(false);

        skipButton = OmeTheme.createGhostButton("Passer cette étape");
        skipButton.addActionListener(e -> dispose());

        actionButton = OmeTheme.createPrimaryButton("Installer les composants manquants");
        actionButton.addActionListener(e -> handleActionButton());

        buttonsPanel.add(skipButton);
        buttonsPanel.add(actionButton);

        footerPanel.add(progressPanel, BorderLayout.CENTER);
        footerPanel.add(buttonsPanel, BorderLayout.SOUTH);
        add(footerPanel, BorderLayout.SOUTH);

        refreshComponentStatusList();
    }

    private void refreshComponentStatusList() {
        componentsListPanel.removeAll();
        List<ComponentStatus> statuses = DependencyManagerService.checkAllComponents();
        boolean allReady = true;

        for (ComponentStatus status : statuses) {
            if (!status.isInstalled) allReady = false;
            componentsListPanel.add(createComponentCard(status));
            componentsListPanel.add(Box.createVerticalStrut(10));
        }

        if (allReady) {
            actionButton.setText("✔ Tous les composants sont prêts • Continuer");
            skipButton.setVisible(false);
        } else {
            actionButton.setText("Configurer les composants manquants");
            skipButton.setVisible(true);
        }

        componentsListPanel.revalidate();
        componentsListPanel.repaint();
    }

    private JPanel createComponentCard(ComponentStatus status) {
        JPanel card = new JPanel(new BorderLayout(12, 8));
        card.setBackground(OmeTheme.bgRaised());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(status.isInstalled ? OmeTheme.accent() : OmeTheme.warning(), 1),
                new EmptyBorder(12, 16, 12, 16)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

        // Info gauche
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 0, 3));
        leftPanel.setOpaque(false);

        JLabel nameLabel = new JLabel(status.type.getDisplayName() + " (" + status.type.getApproximateSize() + ")");
        nameLabel.setFont(OmeTheme.fontSans(Font.BOLD, 13.5f));
        nameLabel.setForeground(OmeTheme.text());

        JLabel descLabel = new JLabel(status.type.getDescription() + " — " + status.details);
        descLabel.setFont(OmeTheme.fontSans(Font.PLAIN, 11.5f));
        descLabel.setForeground(OmeTheme.textSecondary());

        leftPanel.add(nameLabel);
        leftPanel.add(descLabel);

        // Badge droite
        JLabel badgeLabel = OmeTheme.createTagBadge(status.isInstalled ? "PRÊT" : "MANQUANT", status.isInstalled);
        if (!status.isInstalled) {
            badgeLabel.setForeground(OmeTheme.warning());
        }

        card.add(leftPanel, BorderLayout.CENTER);
        card.add(badgeLabel, BorderLayout.EAST);

        return card;
    }

    private void handleActionButton() {
        if (DependencyManagerService.hasAllEssentialComponents()) {
            completed = true;
            dispose();
            return;
        }

        // Lancer la configuration/téléchargement
        actionButton.setEnabled(false);
        skipButton.setEnabled(false);
        progressBar.setVisible(true);
        progressLabel.setVisible(true);
        progressBar.setIndeterminate(true);
        progressLabel.setText("Vérification et initialisation des répertoires autonomes...");

        new Thread(() -> {
            try {
                // Si Python ou Whisper est manquant
                ComponentStatus pyStatus = DependencyManagerService.checkPythonWhisper();
                if (!pyStatus.isInstalled) {
                    SwingUtilities.invokeLater(() -> {
                        progressBar.setIndeterminate(false);
                        progressBar.setValue(30);
                        progressLabel.setText("Configuration du module d'IA (faster-whisper)...");
                    });

                    // Si python est présent sans faster-whisper, on installe via pip (support universel CPU/GPU)
                    String pyCmd = new app.services.SpeechWorkflowService().findPython();
                    if (pyCmd != null) {
                        ProcessBuilder pb = new ProcessBuilder(pyCmd, "-m", "pip", "install", "--upgrade", "faster-whisper");
                        pb.redirectErrorStream(true);
                        Process p = pb.start();
                        p.waitFor();
                    }
                }

                SwingUtilities.invokeLater(() -> {
                    progressBar.setValue(100);
                    progressBar.setIndeterminate(false);
                    progressLabel.setText("Tous les composants ont été vérifiés !");
                    actionButton.setEnabled(true);
                    skipButton.setEnabled(true);
                    refreshComponentStatusList();
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressLabel.setText("Erreur lors de la configuration : " + e.getMessage());
                    actionButton.setEnabled(true);
                    skipButton.setEnabled(true);
                    refreshComponentStatusList();
                });
            }
        }).start();
    }

    public boolean isCompleted() {
        return completed;
    }
}
