package app.ui;

import app.utils.TimerClass;
import app.MainFenetre;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class InfoWindow extends JDialog {

    private TimerClass timer;
    private MainFenetre parent;

    public InfoWindow(MainFenetre parent, TimerClass timer) {
        super(parent, "À propos de la vidéo", true);
        this.parent = parent;
        this.timer = timer;

        setSize(350, 200);
        setMinimumSize(new Dimension(350, 200));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(250, 250, 250));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(new Color(250, 250, 250));
        content.setBorder(new EmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Informations sur le fichier actuel");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(title);
        content.add(Box.createVerticalStrut(15));

        File videoFile = parent.getCurrentVideoFile();
        String fileName = videoFile != null ? videoFile.getName() : "Aucun fichier ouvert";
        
        JLabel fileLabel = new JLabel("Fichier : " + fileName);
        fileLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        fileLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(fileLabel);
        content.add(Box.createVerticalStrut(10));

        double fps = getVideoFPS();
        String fpsText;
        if (fps <= 0) {
            fpsText = "Inconnu";
        } else if (fps == Math.floor(fps)) {
            fpsText = String.format("%.0f fps", fps);
        } else {
            fpsText = String.format("%.2f fps", fps).replace(",", ".");
        }

        JLabel videoFPSLabel = new JLabel("Fréquence d'images : " + fpsText);
        videoFPSLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        videoFPSLabel.setForeground(new Color(0, 100, 200));
        videoFPSLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(videoFPSLabel);

        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel();
        footer.setBackground(Color.WHITE);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        JButton close = new JButton("Fermer");
        close.setFocusPainted(false);
        close.addActionListener(e -> dispose());
        footer.add(close);
        add(footer, BorderLayout.SOUTH);
    }

    /**
     * Récupère le FPS réel du fichier vidéo en utilisant ffprobe.
     */
    private double getVideoFPS() {
        try {
            File videoFile = parent.getCurrentVideoFile();
            if (videoFile == null || !videoFile.exists()) return 0.0;

            File ffprobeFile = new File("ffmpeg/ffprobe.exe");
            if (!ffprobeFile.exists()) {
                System.err.println("ffprobe introuvable : " + ffprobeFile.getAbsolutePath());
                return 0.0;
            }

            String[] command = {
                ffprobeFile.getAbsolutePath(),
                "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=r_frame_rate",
                "-of", "default=noprint_wrappers=1:nokey=1",
                videoFile.getAbsolutePath()
            };

            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine(); 
            process.waitFor();

            if (line != null && !line.isEmpty()) {
                line = line.trim();
                // ffprobe renvoie souvent une fraction, ex: "30000/1001" ou "30/1"
                if (line.contains("/")) {
                    String[] parts = line.split("/");
                    if (parts.length == 2) {
                        double num = Double.parseDouble(parts[0]);
                        double den = Double.parseDouble(parts[1]);
                        if (den > 0) return num / den;
                    }
                } else {
                    return Double.parseDouble(line);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return 0.0; 
    }
}
