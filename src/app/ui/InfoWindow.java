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

        setSize(440, 290);
        setMinimumSize(new Dimension(340, 240));
        setResizable(true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        add(OmeTheme.createDialogHeader("SPÉCIFICATIONS", "À propos du média", "OmeRyth Studio • Informations techniques"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(OmeTheme.bg());
        content.setBorder(new EmptyBorder(18, 22, 18, 22));

        File videoFile = parent.getCurrentVideoFile();
        String fileName = videoFile != null ? videoFile.getName() : "Aucun fichier ouvert";

        double fps = getVideoFPS();
        String fpsText;
        if (fps <= 0) {
            fpsText = "Inconnu";
        } else if (fps == Math.floor(fps)) {
            fpsText = String.format("%.0f fps", fps);
        } else {
            fpsText = String.format("%.2f fps", fps).replace(",", ".");
        }

        JPanel specsGrid = new JPanel(new GridLayout(2, 1, 0, 10));
        specsGrid.setOpaque(false);
        specsGrid.add(createSpecRow("FICHIER MÉDIA", fileName, false));
        specsGrid.add(createSpecRow("FRÉQUENCE D'IMAGES", fpsText, true));

        content.add(OmeTheme.createSectionCard("INF", "Média actif", specsGrid));

        add(content, BorderLayout.CENTER);

        OmeTheme.OmeButton close = OmeTheme.createPrimaryButton("Fermer");
        close.addActionListener(e -> dispose());
        add(OmeTheme.createDialogFooter(close), BorderLayout.SOUTH);
    }

    private JPanel createSpecRow(String key, String value, boolean highlight) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setFont(OmeTheme.fontMono(Font.BOLD, 10.5f));
        k.setForeground(OmeTheme.textTertiary());
        JLabel v = new JLabel(value);
        v.setFont(highlight ? OmeTheme.fontMono(Font.BOLD, 13) : OmeTheme.fontSans(Font.BOLD, 13));
        v.setForeground(highlight ? OmeTheme.accent() : OmeTheme.text());
        row.add(k, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
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
