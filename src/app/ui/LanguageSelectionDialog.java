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
        setSize(500, 390);
        setMinimumSize(new Dimension(400, 300));
        setLocationRelativeTo(parent);
        setResizable(true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        add(OmeTheme.createDialogHeader(
                "LANGUE / LANGUAGE",
                "Bienvenue sur OmeRyth",
                "Choisissez votre langue de travail et de correction orthographique."
        ), BorderLayout.NORTH);

        // Options Radio / Cartes
        JPanel optionsPanel = new JPanel(new GridLayout(2, 1, 10, 12));
        optionsPanel.setOpaque(false);
        optionsPanel.setBorder(new EmptyBorder(18, 22, 18, 22));

        ButtonGroup group = new ButtonGroup();

        JRadioButton frRadio = new JRadioButton("<html><b>Français (FR)</b><br><small>Interface & correction orthographique/grammaticale en français</small></html>");
        frRadio.setFont(OmeTheme.fontSans(Font.PLAIN, 13.5f));
        frRadio.setForeground(OmeTheme.text());
        frRadio.setOpaque(true);
        frRadio.setBackground(OmeTheme.bgRaised());
        frRadio.setFocusPainted(false);
        frRadio.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        frRadio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OmeTheme.accent(), 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        frRadio.setSelected(true);
        group.add(frRadio);

        JRadioButton enRadio = new JRadioButton("<html><b>English (EN)</b><br><small>Interface & English spelling and grammar check</small></html>");
        enRadio.setFont(OmeTheme.fontSans(Font.PLAIN, 13.5f));
        enRadio.setForeground(OmeTheme.text());
        enRadio.setOpaque(true);
        enRadio.setBackground(OmeTheme.bgRaised());
        enRadio.setFocusPainted(false);
        enRadio.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        enRadio.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OmeTheme.border(), 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));
        group.add(enRadio);

        frRadio.addActionListener(e -> {
            selectedLanguage = "fr";
            frRadio.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(OmeTheme.accent(), 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));
            enRadio.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(OmeTheme.border(), 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));
        });

        enRadio.addActionListener(e -> {
            selectedLanguage = "en";
            enRadio.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(OmeTheme.accent(), 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));
            frRadio.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(OmeTheme.border(), 1),
                    BorderFactory.createEmptyBorder(12, 14, 12, 14)
            ));
        });

        optionsPanel.add(frRadio);
        optionsPanel.add(enRadio);
        add(optionsPanel, BorderLayout.CENTER);

        OmeTheme.OmeButton confirmBtn = OmeTheme.createPrimaryButton("Confirmer / Confirm");
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

        add(OmeTheme.createDialogFooter(confirmBtn), BorderLayout.SOUTH);
    }
}
