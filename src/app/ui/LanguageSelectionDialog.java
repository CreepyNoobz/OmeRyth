package app.ui;

import app.services.SpellGrammarService;
import app.utils.FileUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Properties;

/**
 * Boîte de dialogue du premier lancement pour la sélection de la langue
 * de l'application (Français ou Anglais) et du moteur de correction.
 */
public class LanguageSelectionDialog extends JDialog {

    private static final String LANG_KEY = "language_selected";

    public static boolean isLanguageSelected() {
        Properties props = FileUtils.loadAppState();
        return "true".equalsIgnoreCase(props.getProperty(LANG_KEY, "false"));
    }

    public static void showIfNeeded(JFrame parent, AppCustomization customization) {
        if (!isLanguageSelected()) {
            LanguageSelectionDialog dialog = new LanguageSelectionDialog(parent, customization);
            dialog.setVisible(true);
        }
    }

    private String selectedLanguage = "fr";

    public LanguageSelectionDialog(JFrame parent, AppCustomization customization) {
        super(parent, "OmeRyth - Choix de la langue", true);
        setSize(480, 360);
        setLocationRelativeTo(parent);
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(28, 28, 30));

        // En-tête
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(20, 25, 10, 25));

        JLabel titleLabel = new JLabel("Bienvenue / Welcome");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel subtitleLabel = new JLabel("Choisissez votre langue de travail et de correction :");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitleLabel.setForeground(new Color(180, 180, 185));
        headerPanel.add(subtitleLabel, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // Options Radio / Cartes
        JPanel optionsPanel = new JPanel(new GridLayout(2, 1, 10, 12));
        optionsPanel.setOpaque(false);
        optionsPanel.setBorder(new EmptyBorder(15, 25, 15, 25));

        ButtonGroup group = new ButtonGroup();

        JRadioButton frRadio = new JRadioButton("<html><b>🇫🇷 Français</b><br><small style='color:#a0a0a0;'>Interface & correction orthographique/grammaticale en français</small></html>");
        frRadio.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        frRadio.setForeground(Color.WHITE);
        frRadio.setOpaque(true);
        frRadio.setBackground(new Color(42, 42, 46));
        frRadio.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 150, 255), 1),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        frRadio.setSelected(true);
        group.add(frRadio);

        JRadioButton enRadio = new JRadioButton("<html><b>🇬🇧 English</b><br><small style='color:#a0a0a0;'>Interface & English spelling and grammar check</small></html>");
        enRadio.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        enRadio.setForeground(Color.WHITE);
        enRadio.setOpaque(true);
        enRadio.setBackground(new Color(42, 42, 46));
        enRadio.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(60, 60, 65), 1),
            BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        group.add(enRadio);

        frRadio.addActionListener(e -> {
            selectedLanguage = "fr";
            frRadio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 150, 255), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));
            enRadio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 65), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));
        });

        enRadio.addActionListener(e -> {
            selectedLanguage = "en";
            enRadio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0, 150, 255), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));
            frRadio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 65), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
            ));
        });

        optionsPanel.add(frRadio);
        optionsPanel.add(enRadio);
        add(optionsPanel, BorderLayout.CENTER);

        // Bouton de validation
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 25, 18));
        footerPanel.setOpaque(false);

        JButton confirmBtn = new JButton("Confirmer / Confirm");
        confirmBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setBackground(new Color(0, 122, 255));
        confirmBtn.setFocusPainted(false);
        confirmBtn.setPreferredSize(new Dimension(170, 36));
        confirmBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        confirmBtn.addActionListener(e -> {
            if (customization != null) {
                customization.appLanguage = selectedLanguage;
                FileUtils.saveCustomization(customization);
            }
            SpellGrammarService.getInstance().setLanguage(selectedLanguage);

            Properties appState = FileUtils.loadAppState();
            appState.setProperty(LANG_KEY, "true");
            FileUtils.saveAppState(appState);

            dispose();
        });

        footerPanel.add(confirmBtn);
        add(footerPanel, BorderLayout.SOUTH);
    }
}
