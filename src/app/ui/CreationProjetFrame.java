package app.ui;

import app.utils.FileUtils;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

/**
 * Assistant pour créer un nouveau projet, rassemblé sur une seule page propre.
 */
public class CreationProjetFrame extends JDialog {

    private JTextField videoPathField;
    private JSpinner bandCountSpinner;
    private File videoFile = null;
    private File projectFile = null;
    private boolean confirmed = false;

    public CreationProjetFrame(Frame parent) {
        super(parent, "Créer un nouveau projet", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(620, 500);
        setMinimumSize(new Dimension(480, 380));
        setResizable(true);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        add(OmeTheme.createDialogHeader(
                "NOUVEAU PROJET",
                "Créer un projet OmeRyth",
                "Sélectionnez la source média, l'emplacement du fichier et le nombre de bandes."
        ), BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(OmeTheme.bg());
        contentPanel.setBorder(new EmptyBorder(18, 22, 18, 22));

        // 01. Vidéo
        JPanel videoPanel = new JPanel(new BorderLayout(8, 0));
        videoPanel.setOpaque(false);
        videoPathField = new JTextField();
        videoPathField.setEditable(false);
        OmeTheme.styleTextField(videoPathField);
        OmeTheme.OmeButton browseVideoBtn = OmeTheme.createSecondaryButton("Parcourir...");
        browseVideoBtn.addActionListener(e -> {
            File selected = FileUtils.chooseOpenFile(this, "Choisir vidéo/audio",
                    "mp4", "mp3", "wav", "ogg", "avi", "mkv");
            if (selected != null) {
                videoFile = selected;
                videoPathField.setText(videoFile.getAbsolutePath());
            }
        });
        videoPanel.add(videoPathField, BorderLayout.CENTER);
        videoPanel.add(browseVideoBtn, BorderLayout.EAST);
        contentPanel.add(OmeTheme.createSectionCard("01", "Fichier Vidéo / Audio", videoPanel));
        contentPanel.add(Box.createVerticalStrut(14));

        // 02. Sauvegarde
        JPanel savePanel = new JPanel(new BorderLayout(8, 0));
        savePanel.setOpaque(false);
        JTextField saveField = new JTextField();
        saveField.setEditable(false);
        OmeTheme.styleTextField(saveField);
        OmeTheme.OmeButton browseSaveBtn = OmeTheme.createSecondaryButton("Enregistrer sous...");
        browseSaveBtn.addActionListener(e -> {
            File f = FileUtils.chooseSaveFile(this, "Enregistrer projet sous", "rythmo");
            if (f != null) {
                projectFile = f;
                saveField.setText(projectFile.getAbsolutePath());
            }
        });
        savePanel.add(saveField, BorderLayout.CENTER);
        savePanel.add(browseSaveBtn, BorderLayout.EAST);
        contentPanel.add(OmeTheme.createSectionCard("02", "Emplacement du Projet", savePanel));
        contentPanel.add(Box.createVerticalStrut(14));

        // 03. Bandes
        JPanel configPanel = new JPanel(new BorderLayout(12, 0));
        configPanel.setOpaque(false);
        JLabel bandDesc = new JLabel("Nombre de pistes synchronisées simultanément :");
        bandDesc.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
        bandDesc.setForeground(OmeTheme.textSecondary());
        bandCountSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        bandCountSpinner.setPreferredSize(new Dimension(80, 32));
        OmeTheme.styleSpinner(bandCountSpinner);
        configPanel.add(bandDesc, BorderLayout.CENTER);
        configPanel.add(bandCountSpinner, BorderLayout.EAST);
        contentPanel.add(OmeTheme.createSectionCard("03", "Nombre de bandes (Lignes de texte)", configPanel));

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        OmeTheme.styleBorderlessScrollPane(scrollPane);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        // Boutons
        OmeTheme.OmeButton cancelBtn = OmeTheme.createGhostButton("Annuler");
        OmeTheme.OmeButton finishBtn = OmeTheme.createPrimaryButton("Créer le projet");

        cancelBtn.addActionListener(e -> dispose());
        finishBtn.addActionListener(e -> finishProject());

        add(OmeTheme.createDialogFooter(cancelBtn, finishBtn), BorderLayout.SOUTH);
    }

    private void finishProject() {
        if (videoFile == null) {
            JOptionPane.showMessageDialog(this, "Veuillez sélectionner un fichier vidéo ou audio !");
            return;
        }
        if (projectFile == null) {
            JOptionPane.showMessageDialog(this, "Veuillez choisir un emplacement de sauvegarde pour le projet !");
            return;
        }
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() { return confirmed; }
    public File getVideoFile() { return videoFile; }
    public int getBandCount() { return ((Number) bandCountSpinner.getValue()).intValue(); }
    public File getProjectFile() { return projectFile; }
}
