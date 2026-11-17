package app.ui;

import app.utils.FileUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

public class NewProjectDialog extends JDialog {

    private final JTextField videoPathField = new JTextField();
    private final JSpinner bandCountSpinner;

    private File videoFile = null;
    private boolean confirmed = false;

    public NewProjectDialog(JFrame parent, int initialBandCount) {
        super(parent, "Nouveau projet", true);
        setSize(540, 340);
        setMinimumSize(new Dimension(460, 290));
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        add(OmeTheme.createDialogHeader("NOUVEAU PROJET", "Nouveau projet OmeRyth", "Choisissez la vidéo et le nombre de bandes."), BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(OmeTheme.bg());
        body.setBorder(new EmptyBorder(16, 20, 16, 20));

        // VIDEO
        JPanel videoPanel = new JPanel(new BorderLayout(8, 0));
        videoPanel.setOpaque(false);
        OmeTheme.styleTextField(videoPathField);
        OmeTheme.OmeButton browseVideo = OmeTheme.createSecondaryButton("Choisir vidéo");
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

        // BANDES
        JPanel bandsPanel = new JPanel(new BorderLayout(10, 0));
        bandsPanel.setOpaque(false);
        JLabel bandsLabel = new JLabel("Nombre de bandes :");
        bandsLabel.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
        bandsLabel.setForeground(OmeTheme.textSecondary());
        int safeInitialBandCount = Math.max(1, initialBandCount);
        bandCountSpinner = new JSpinner(new SpinnerNumberModel(safeInitialBandCount, 1, 64, 1));
        bandCountSpinner.setPreferredSize(new Dimension(80, 30));
        OmeTheme.styleSpinner(bandCountSpinner);
        bandsPanel.add(bandsLabel, BorderLayout.WEST);
        bandsPanel.add(bandCountSpinner, BorderLayout.EAST);

        body.add(OmeTheme.createSectionCard("01", "Source média", videoPanel));
        body.add(Box.createVerticalStrut(12));
        body.add(OmeTheme.createSectionCard("02", "Configuration des bandes", bandsPanel));
        add(body, BorderLayout.CENTER);

        OmeTheme.OmeButton cancel = OmeTheme.createGhostButton("Annuler");
        cancel.addActionListener(e -> dispose());
        OmeTheme.OmeButton confirm = OmeTheme.createPrimaryButton("Créer");
        confirm.addActionListener(e -> {
            if (videoFile == null) {
                JOptionPane.showMessageDialog(this, "Choisis une vidéo !");
                return;
            }
            confirmed = true;
            dispose();
        });

        add(OmeTheme.createDialogFooter(cancel, confirm), BorderLayout.SOUTH);
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
