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
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout(15, 15));

        Color bgLight = new Color(245, 245, 247);
        Color cardBg = Color.WHITE;
        Color textBlack = Color.BLACK;

        getContentPane().setBackground(bgLight);

        // ── En-tête ──
        JPanel headerPanel = new JPanel(new BorderLayout(5, 5));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(20, 25, 10, 25));

        JLabel titleLabel = new JLabel("Initialisation des Composants Essentiels");
        titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        titleLabel.setForeground(textBlack);

        JLabel subtitleLabel = new JLabel("<html>OmeRyth intègre des modules autonomes pour garantir une expérience fluide sans configuration complexe sur votre ordinateur.</html>");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(textBlack);

        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(subtitleLabel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // ── Liste des composants ──
        componentsListPanel = new JPanel();
        componentsListPanel.setLayout(new BoxLayout(componentsListPanel, BoxLayout.Y_AXIS));
        componentsListPanel.setOpaque(false);
        componentsListPanel.setBorder(new EmptyBorder(5, 25, 10, 25));

        JScrollPane scrollPane = new JScrollPane(componentsListPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);

        // ── Pied de page & Actions ──
        JPanel footerPanel = new JPanel(new BorderLayout(10, 10));
        footerPanel.setOpaque(false);
        footerPanel.setBorder(new EmptyBorder(10, 25, 20, 25));

        // Zone de progression
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressPanel.setOpaque(false);

        progressLabel = new JLabel("Prêt pour la configuration");
        progressLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        progressLabel.setForeground(textBlack);
        progressLabel.setVisible(false);

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setForeground(new Color(0, 122, 255));
        progressBar.setBackground(Color.WHITE);
        progressBar.setVisible(false);

        progressPanel.add(progressLabel, BorderLayout.NORTH);
        progressPanel.add(progressBar, BorderLayout.CENTER);

        // Boutons
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonsPanel.setOpaque(false);

        skipButton = new JButton("Passer cette étape");
        skipButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        skipButton.setForeground(textBlack);
        skipButton.setBackground(new Color(230, 233, 238));
        skipButton.setFocusPainted(false);
        skipButton.setPreferredSize(new Dimension(160, 36));
        skipButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        skipButton.addActionListener(e -> dispose());

        actionButton = new JButton("Installer les composants manquants");
        actionButton.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        actionButton.setForeground(textBlack);
        actionButton.setBackground(new Color(215, 230, 250));
        actionButton.setFocusPainted(false);
        actionButton.setPreferredSize(new Dimension(280, 36));
        actionButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
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
            actionButton.setText("Tous les composants sont prêts ! Continuer");
            actionButton.setBackground(new Color(215, 240, 220));
            skipButton.setVisible(false);
        } else {
            actionButton.setText("Configurer les composants manquants");
            actionButton.setBackground(new Color(215, 230, 250));
            skipButton.setVisible(true);
        }

        componentsListPanel.revalidate();
        componentsListPanel.repaint();
    }

    private JPanel createComponentCard(ComponentStatus status) {
        JPanel card = new JPanel(new BorderLayout(10, 10));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(status.isInstalled ? new Color(40, 167, 69) : new Color(220, 130, 20), 1),
                new EmptyBorder(10, 15, 10, 15)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));

        // Info gauche
        JPanel leftPanel = new JPanel(new GridLayout(2, 1, 0, 3));
        leftPanel.setBackground(Color.WHITE);

        JLabel nameLabel = new JLabel(status.type.getDisplayName() + " (" + status.type.getApproximateSize() + ")");
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nameLabel.setForeground(Color.BLACK);

        JLabel descLabel = new JLabel(status.type.getDescription() + " — " + status.details);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        descLabel.setForeground(Color.BLACK);

        leftPanel.add(nameLabel);
        leftPanel.add(descLabel);

        // Badge droite
        JLabel badgeLabel = new JLabel(status.isInstalled ? "Détecté" : "Manquant");
        badgeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        badgeLabel.setForeground(status.isInstalled ? new Color(24, 134, 52) : new Color(185, 90, 0));

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
