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
        setSize(600, 450);
        setMinimumSize(new Dimension(600, 450));
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel headerLabel = new JLabel("Créer un nouveau projet");
        headerLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        add(headerLabel, BorderLayout.NORTH);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // 1. Vidéo
        contentPanel.add(createSectionHeader("1. Fichier Vidéo / Audio"));
        JPanel videoPanel = new JPanel(new BorderLayout(5, 5));
        videoPathField = new JTextField();
        videoPathField.setEditable(false);
        JButton browseVideoBtn = new JButton("📁 Parcourir...");
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
        videoPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        videoPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(videoPanel);
        contentPanel.add(Box.createVerticalStrut(20));

        // 2. Sauvegarde
        contentPanel.add(createSectionHeader("2. Emplacement du Projet"));
        JPanel savePanel = new JPanel(new BorderLayout(5, 5));
        JTextField saveField = new JTextField();
        saveField.setEditable(false);
        JButton browseSaveBtn = new JButton("📁 Enregistrer sous...");
        browseSaveBtn.addActionListener(e -> {
            File f = FileUtils.chooseSaveFile(this, "Enregistrer projet sous", "rythmo");
            if (f != null) {
                projectFile = f;
                saveField.setText(projectFile.getAbsolutePath());
            }
        });
        savePanel.add(saveField, BorderLayout.CENTER);
        savePanel.add(browseSaveBtn, BorderLayout.EAST);
        savePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        savePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(savePanel);
        contentPanel.add(Box.createVerticalStrut(20));

        // 3. Bandes
        contentPanel.add(createSectionHeader("3. Nombre de bandes (Lignes de texte)"));
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bandCountSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
        configPanel.add(bandCountSpinner);
        configPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(configPanel);
        contentPanel.add(Box.createVerticalStrut(20));


        add(contentPanel, BorderLayout.CENTER);

        // Boutons
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton finishBtn = new JButton("✔ Créer le projet");
        finishBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JButton cancelBtn = new JButton("Annuler");

        finishBtn.addActionListener(e -> finishProject());
        cancelBtn.addActionListener(e -> dispose());

        btnPanel.add(cancelBtn);
        btnPanel.add(Box.createHorizontalStrut(10));
        btnPanel.add(finishBtn);

        add(btnPanel, BorderLayout.SOUTH);
    }

    private JLabel createSectionHeader(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setBorder(new EmptyBorder(0, 0, 5, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
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
