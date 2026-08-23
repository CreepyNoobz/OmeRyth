package app.services;

import app.ui.TimelinePanel;
import app.utils.TimerClass;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class MediaWorkflowService {

    /** Load a video file into the media player component and set timeline duration. */
    public void loadVideo(javax.swing.JFrame owner,
                          File videoFile,
                          EmbeddedMediaPlayerComponent mediaPlayerComponent,
                          CardLayout mediaCardLayout,
                          JPanel mediaContentPanel,
                          TimerClass timer,
                          Runnable onLoaded) {
        if (videoFile == null) return;

        JDialog loadingDialog = new JDialog(owner, "Chargement video", false);
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        loadingDialog.add(progress);
        loadingDialog.setSize(280, 70);
        loadingDialog.setLocationRelativeTo(owner);

        SwingWorker<Long, Void> worker = new SwingWorker<>() {
            @Override
            protected Long doInBackground() throws Exception {
                if (mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
                    mediaPlayerComponent.mediaPlayer().controls().stop();
                }
                mediaPlayerComponent.mediaPlayer().media().startPaused(videoFile.getAbsolutePath());
                return mediaPlayerComponent.mediaPlayer().media().info().duration();
            }

            @Override
            protected void done() {
                loadingDialog.dispose();
                try {
                    long dureeMs = get();
                    if (dureeMs <= 0) {
                        JOptionPane.showMessageDialog(owner,
                                "Video non supportee ou metadonnees illisibles.",
                                "Chargement video",
                                JOptionPane.ERROR_MESSAGE);
                        mediaCardLayout.show(mediaContentPanel, "EMPTY");
                        return;
                    }
                    mediaCardLayout.show(mediaContentPanel, "PLAYER");
                    timer.setMaxTime(dureeMs / 1000.0);
                    if (onLoaded != null) onLoaded.run();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner,
                            "Impossible de charger la video: " + ex.getMessage(),
                            "Chargement video",
                            JOptionPane.ERROR_MESSAGE);
                    mediaCardLayout.show(mediaContentPanel, "EMPTY");
                }
            }
        };

        worker.execute();
        loadingDialog.setVisible(true);
    }

    /** Adjust current time based on mouse wheel input (seeking). */
    public void adjustTimeByWheel(MouseWheelEvent event,
                                  TimerClass timer,
                                  EmbeddedMediaPlayerComponent mediaPlayerComponent) {
        double wheelRotation = event.getPreciseWheelRotation();
        if (wheelRotation == 0) return;

        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
            mediaPlayerComponent.mediaPlayer().controls().pause();
        }
        if (timer.isRunning()) {
            timer.toggle();
        }

        int modifiers = event.getModifiersEx();
        double step = 0.05;
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            step = 1.0;
        } else if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            step = 0.5;
        }
        double delta = Math.signum(wheelRotation) * step;
        double next = timer.getTime() + delta;
        if (next < 0) {
            timer.reset();
            if (mediaPlayerComponent != null) {
                mediaPlayerComponent.mediaPlayer().controls().setTime(0);
            }
            return;
        }

        timer.addTime(delta);
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.mediaPlayer().controls().setTime((long) (timer.getTime() * 1000));
        }
    }

    /** Export the timeline as a video file using frame rendering + ffmpeg. */
    public void exportVideo(javax.swing.JFrame owner,
                            File fichierSelectionne,
                            EmbeddedMediaPlayerComponent mediaPlayerComponent,
                            TimelinePanel timelinePanel) {
        if (fichierSelectionne == null) {
            JOptionPane.showMessageDialog(owner, "Aucune video chargee. Veuillez d'abord ouvrir un projet.");
            return;
        }

        long dureeMs = mediaPlayerComponent.mediaPlayer().media().info().duration();
        if (dureeMs <= 0) {
            JOptionPane.showMessageDialog(owner, "Impossible de determiner la duree de la video.");
            return;
        }
        double dureeSec = dureeMs / 1000.0;

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Sauvegarder la video rythmo");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Fichier MP4", "mp4"));
        if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return;
        File outputFile = chooser.getSelectedFile();
        if (!outputFile.getName().toLowerCase().endsWith(".mp4")) {
            outputFile = new File(outputFile.getAbsolutePath() + ".mp4");
        }
        final File finalOutput = outputFile;

        JDialog progressDialog = new JDialog(owner, "Export en cours...", false);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        JLabel statusLabel = new JLabel("Generation des images...");
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel);
        progressDialog.setSize(380, 110);
        progressDialog.setLocationRelativeTo(owner);
        progressDialog.setVisible(true);

        SwingWorker<String, Integer> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                int fps = 60;
                int totalFrames = (int) Math.ceil(dureeSec * fps);
                int width = timelinePanel.getWidth();
                int height = timelinePanel.getHeight();
                if (width <= 0) width = 800;
                if (height <= 0) height = 200;
                if (width % 2 != 0) width++;
                if (height % 2 != 0) height++;

                File tempDir = new File(System.getProperty("java.io.tmpdir"),
                        "rythmo_export_" + System.currentTimeMillis());
                tempDir.mkdirs();

                try {
                    for (int i = 0; i < totalFrames; i++) {
                        if (isCancelled()) break;
                        final double time = (double) i / fps;
                        final int fw = width, fh = height;
                        final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
                        final java.awt.image.BufferedImage[] frameHolder = new java.awt.image.BufferedImage[1];
                        SwingUtilities.invokeLater(() -> {
                            frameHolder[0] = timelinePanel.renderFrame(fw, fh, time);
                            latch.countDown();
                        });
                        latch.await();
                        File frameFile = new File(tempDir, String.format("frame%06d.png", i));
                        ImageIO.write(frameHolder[0], "PNG", frameFile);
                        publish((int) (i * 75 / totalFrames));
                    }

                    publish(75);
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Encodage video..."));

                    String ffmpegPath = findFfmpeg();
                    if (ffmpegPath == null) {
                        return "FFMPEG_NOT_FOUND";
                    }

                    ProcessBuilder pb = new ProcessBuilder(
                            ffmpegPath,
                            "-y",
                            "-framerate", String.valueOf(fps),
                            "-i", new File(tempDir, "frame%06d.png").getAbsolutePath(),
                            "-c:v", "libx264",
                            "-pix_fmt", "yuv420p",
                            finalOutput.getAbsolutePath()
                    );
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    StringBuilder ffmpegLogs = new StringBuilder();
                    Thread logReader = new Thread(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                ffmpegLogs.append(line).append('\n');
                            }
                        } catch (Exception ignored) {
                        }
                    }, "ffmpeg-log-reader");
                    logReader.setDaemon(true);
                    logReader.start();

                    int exitCode = process.waitFor();
                    logReader.join(2000);
                    if (exitCode != 0) {
                        String logs = ffmpegLogs.toString();
                        if (logs.length() > 1200) {
                            logs = logs.substring(logs.length() - 1200);
                        }
                        return "FFMPEG_ERROR\n" + logs;
                    }

                    publish(100);
                    return "OK";

                } finally {
                    File[] files = tempDir.listFiles();
                    if (files != null) for (File f : files) f.delete();
                    tempDir.delete();
                }
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                if (!chunks.isEmpty()) progressBar.setValue(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    String result = get();
                    if ("OK".equals(result)) {
                        JOptionPane.showMessageDialog(owner,
                                "Video exportee avec succes :\n" + finalOutput.getAbsolutePath());
                    } else if ("FFMPEG_NOT_FOUND".equals(result)) {
                        JOptionPane.showMessageDialog(owner,
                                "ffmpeg est introuvable.\nInstallez ffmpeg et assurez-vous qu'il est accessible dans le PATH.",
                                "Erreur", JOptionPane.ERROR_MESSAGE);
                    } else {
                        String details = "";
                        if (result != null && result.startsWith("FFMPEG_ERROR\n")) {
                            details = "\n\nDetails ffmpeg (fin de log):\n" + result.substring("FFMPEG_ERROR\n".length());
                        }
                        JOptionPane.showMessageDialog(owner,
                                "Erreur lors de l'encodage avec ffmpeg." + details,
                                "Erreur", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner,
                            "Erreur lors de l'export : " + ex.getMessage(),
                            "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /** Auto-detect scene changes using FFmpeg and add plan markers to the timeline. */
    public void detectSceneChanges(javax.swing.JFrame owner,
                                   File videoFile,
                                   TimelinePanel timelinePanel) {
        if (videoFile == null) return;
        String ffmpegPath = findFfmpeg();
        if (ffmpegPath == null) {
            JOptionPane.showMessageDialog(owner, "ffmpeg est introuvable. Installation requise.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JDialog progressDialog = new JDialog(owner, "Détection des plans...", false);
        JProgressBar progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setStringPainted(true);
        progressBar.setString("Analyse de la vidéo...");
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel);
        progressDialog.setSize(380, 110);
        progressDialog.setLocationRelativeTo(owner);
        progressDialog.setVisible(true);

        SwingWorker<java.util.List<Double>, Void> worker = new SwingWorker<>() {
            @Override
            protected java.util.List<Double> doInBackground() throws Exception {
                java.util.List<Double> times = new java.util.ArrayList<>();
                ProcessBuilder pb = new ProcessBuilder(
                        ffmpegPath,
                        "-i", videoFile.getAbsolutePath(),
                        "-filter:v", "select='gt(scene,0.60)',showinfo",
                        "-f", "null",
                        "-"
                );
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (isCancelled()) {
                            process.destroy();
                            break;
                        }
                        // FFmpeg showinfo prints: pts_time:1.234
                        int idx = line.indexOf("pts_time:");
                        if (idx != -1) {
                            int endIdx = line.indexOf(" ", idx);
                            if (endIdx == -1) endIdx = line.length();
                            try {
                                String timeStr = line.substring(idx + 9, endIdx);
                                times.add(Double.parseDouble(timeStr));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                process.waitFor();
                return times;
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    java.util.List<Double> times = get();
                    if (times != null && !times.isEmpty()) {
                        for (Double t : times) {
                            int markerX = (int) Math.round(t * timelinePanel.getPixelsPerSecond());
                            timelinePanel.getTextManager().addPlanMarker(markerX);
                        }
                        timelinePanel.repaint();
                        JOptionPane.showMessageDialog(owner, times.size() + " plans détectés avec succès.");
                    } else {
                        JOptionPane.showMessageDialog(owner, "Aucun changement de plan détecté.");
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner, "Erreur lors de la détection : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private String findFfmpeg() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process p = pb.start();
            p.destroy();
            return "ffmpeg";
        } catch (Exception ignored) {}

        File local = new File("ffmpeg/ffmpeg.exe");
        if (local.exists()) return local.getAbsolutePath();

        File common = new File("C:/ffmpeg/bin/ffmpeg.exe");
        if (common.exists()) return common.getAbsolutePath();

        return null;
    }
}
