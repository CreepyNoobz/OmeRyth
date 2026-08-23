package app.ui;

import app.utils.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class NewProjectDialog extends JDialog {

    private JTextField videoPathField = new JTextField();
    private final JSpinner bandCountSpinner;

    private File videoFile = null;

    private boolean confirmed = false;

    public NewProjectDialog(JFrame parent, int initialBandCount) {
        super(parent, "Nouveau projet", true);
        setSize(500, 160);
        setMinimumSize(new Dimension(500, 160));
        setLayout(new GridLayout(3, 1));

        // ===== VIDEO =====
        JPanel videoPanel = new JPanel(new BorderLayout());
        JButton browseVideo = new JButton("Choisir vidéo");

        browseVideo.addActionListener(e -> {
            File selected = FileUtils.chooseOpenFile(this,
                    "Choisir vidéo/audio", "mp4", "mp3", "wav", "ogg");
            if (selected != null) {
                videoFile = selected;
                videoPathField.setText(videoFile.getAbsolutePath());
            }
        });

        videoPanel.add(videoPathField, BorderLayout.CENTER);
        videoPanel.add(browseVideo, BorderLayout.EAST);

        // ===== BANDES =====
        JPanel bandsPanel = new JPanel(new BorderLayout());
        JLabel bandsLabel = new JLabel("Nombre de bandes :");
        int safeInitialBandCount = Math.max(1, initialBandCount);
        bandCountSpinner = new JSpinner(new SpinnerNumberModel(safeInitialBandCount, 1, 64, 1));
        bandsPanel.add(bandsLabel, BorderLayout.WEST);
        bandsPanel.add(bandCountSpinner, BorderLayout.EAST);

        // ===== BUTTON =====
        JButton confirm = new JButton("Créer");

        confirm.addActionListener(e -> {
            if (videoFile == null) {
                JOptionPane.showMessageDialog(this, "Choisis une vidéo !");
                return;
            }
            confirmed = true;
            dispose();
        });

        add(videoPanel);
    add(bandsPanel);
        add(confirm);

        setLocationRelativeTo(parent);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public File getVideoFile() {
        return videoFile;
    }

    public int getBandCount() {
        return ((Number) bandCountSpinner.getValue()).intValue();
    }
}