package app.services;

import app.utils.FileUtils;
import app.ui.TimelinePanel;
import app.utils.TimerClass;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

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

    private long lastWheelTime = 0;

    /** Adjust current time based on mouse wheel input (seeking). */
    public void adjustTimeByWheel(MouseWheelEvent event,
                                  TimerClass timer,
                                  EmbeddedMediaPlayerComponent mediaPlayerComponent) {
        event.consume();
        long now = System.currentTimeMillis();
        if (now - lastWheelTime < 30) {
            return; // Ignore duplicate event triggered in rapid succession (<30ms)
        }
        lastWheelTime = now;

        double wheelRotation = event.getPreciseWheelRotation();
        if (wheelRotation == 0) return;

        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
            mediaPlayerComponent.mediaPlayer().controls().pause();
        }
        if (timer.isRunning()) {
            timer.toggle();
        }

        int modifiers = event.getModifiersEx();
        double step = 0.1; // 1 marquage entier (0.1s)
        if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
            step = 1.0;
        } else if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
            step = 0.5;
        }
        long currentTenths = Math.round(timer.getTime() * 10.0);
        long deltaTenths = Math.round(Math.signum(wheelRotation) * step * 10.0);
        if (deltaTenths == 0) {
            deltaTenths = wheelRotation > 0 ? 1L : -1L;
        }
        double next = Math.max(0.0, (currentTenths + deltaTenths) / 10.0);
        timer.setTime(next);
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                var mp = mediaPlayerComponent.mediaPlayer();
                var state = mp.status().state();
                long targetMs = (long) (next * 1000);
                if (state == uk.co.caprica.vlcj.player.base.State.ENDED || state == uk.co.caprica.vlcj.player.base.State.STOPPED) {
                    String path = null;
                    if (mp.media().info() != null) {
                        path = mp.media().info().mrl();
                    }
                    if (path != null) {
                        mp.media().startPaused(path, ":start-time=" + (targetMs / 1000.0));
                        mp.controls().setTime(targetMs);
                    } else {
                        mp.controls().play();
                        mp.controls().setTime(targetMs);
                        mp.controls().pause();
                    }
                } else {
                    if (mp.status().isPlaying()) {
                        mp.controls().pause();
                    }
                    mp.controls().setTime(targetMs);
                }
            } catch (Throwable ignored) {}
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

        // Boîte de dialogue de configuration des paramètres d'export (taille, FPS, audio)
        BufferedImage videoSnapshot = null;
        try {
            if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
                videoSnapshot = mediaPlayerComponent.mediaPlayer().snapshots().get();
            }
        } catch (Exception ignored) {}
        app.ui.ExportVideoDialog dialog = new app.ui.ExportVideoDialog(owner, timelinePanel, videoSnapshot);
        dialog.setVisible(true);
        app.ui.ExportVideoDialog.ExportConfig config = dialog.getExportConfig();
        if (!config.approved) return;

        File outputFile = FileUtils.chooseSaveFile(owner, "Sauvegarder la vidéo rythmo", "mp4");
        if (outputFile == null) return;
        final File finalOutput = outputFile;

        int fps = config.fps;
        int totalFrames = (int) Math.ceil(dureeSec * fps);
        int frameW = config.isMontageMode ? config.bandRect.width : config.width;
        int frameH = config.isMontageMode ? config.bandRect.height : config.height;

        // Session de rendu pré-calculée en mémoire (1 seule allocation des objets de la timeline)
        TimelinePanel.ExportSession session = timelinePanel.createExportSession(frameW, frameH, config.visibleSeconds);

        JDialog progressDialog = new JDialog(owner, "Export vidéo en cours...", false);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        String sizeInfo = config.isMontageMode
                ? ("Montage " + config.width + "x" + config.height + " (Bande " + config.bandRect.width + "x" + config.bandRect.height + ")")
                : (config.width + "x" + config.height);
        JLabel statusLabel = new JLabel("Initialisation de l'export (" + sizeInfo + " @ " + config.fps + " FPS)...");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JLabel speedLabel = new JLabel("Démarrage du streaming vidéo haute vitesse...");
        speedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        speedLabel.setForeground(new Color(110, 110, 120));

        JButton btnCancel = new JButton("Annuler l'export");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnCancel.setFocusPainted(false);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.add(statusLabel);
        headerPanel.add(speedLabel);
        panel.add(headerPanel, BorderLayout.NORTH);

        panel.add(progressBar, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.add(btnCancel);
        panel.add(btnPanel, BorderLayout.SOUTH);

        progressDialog.add(panel);
        progressDialog.setSize(520, 165);
        progressDialog.setLocationRelativeTo(owner);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        AtomicReference<Process> ffmpegProcessRef = new AtomicReference<>();

        SwingWorker<String, ExportProgress> worker = new SwingWorker<>() {
            @Override
            protected String doInBackground() throws Exception {
                String ffmpegPath = findFfmpeg();
                if (ffmpegPath == null) {
                    return "FFMPEG_NOT_FOUND";
                }

                File separatedAudio = null;
                try {
                    if (config.isMontageMode && config.removeVocals && fichierSelectionne != null && fichierSelectionne.exists()) {
                        if (isDemucsAvailable()) {
                            publish(new ExportProgress(0, 0, totalFrames, 0, 0, "Demucs IA (Initialisation séparation vocale...)"));
                            try {
                                File tempWav = File.createTempFile("omeryth_demucs_", ".wav");
                                tempWav.deleteOnExit();
                                boolean ok = runDemucsSeparation(fichierSelectionne, tempWav, (pct, msg) -> {
                                    publish(new ExportProgress(Math.min(99, pct), 0, totalFrames, 0, 0, "Demucs IA : " + msg));
                                }, () -> isCancelled(), ffmpegProcessRef);
                                if (ok && tempWav.exists() && tempWav.length() > 0) {
                                    separatedAudio = tempWav;
                                }
                            } catch (Exception ignored) {
                                separatedAudio = null;
                            }
                        }
                    }

                    if (isCancelled()) {
                        return "CANCELLED";
                    }

                    EncoderSettings encoderSettings = detectEncoder(ffmpegPath, config.encoder);

                    java.util.List<String> command = new java.util.ArrayList<>();
                    command.add(ffmpegPath);
                    command.add("-y");

                    // Entrée 0 : Streaming direct des images brutes par stdin (zéro I/O disque)
                    command.add("-f"); command.add("rawvideo");
                    command.add("-pix_fmt"); command.add("bgr24");
                    command.add("-s"); command.add(frameW + "x" + frameH);
                    command.add("-framerate"); command.add(String.valueOf(fps));
                    command.add("-i"); command.add("-"); // [0:v] depuis stdin

                    if (config.isMontageMode && fichierSelectionne != null && fichierSelectionne.exists()) {
                        // Mode Montage Vidéo + Bandeau interactif
                        command.add("-i");
                        command.add(fichierSelectionne.getAbsolutePath()); // [1:v], [1:a]

                        if (separatedAudio != null) {
                            command.add("-i");
                            command.add(separatedAudio.getAbsolutePath()); // [2:a] Piste Demucs isolée
                        }

                        int cW = config.width;
                        int cH = config.height;
                        int vX = config.videoRect.x;
                        int vY = config.videoRect.y;
                        int vW = config.videoRect.width;
                        int vH = config.videoRect.height;
                        int bX = config.bandRect.x;
                        int bY = config.bandRect.y;

                        StringBuilder filter = new StringBuilder();
                        filter.append("color=s=").append(cW).append("x").append(cH)
                              .append(":c=black:r=").append(fps)
                              .append(":d=").append(String.format(Locale.US, "%.3f", dureeSec)).append("[bg];");
                        filter.append("[1:v]scale=w=").append(vW).append(":h=").append(vH)
                              .append(":force_original_aspect_ratio=decrease:flags=fast_bilinear,pad=").append(vW).append(":").append(vH)
                              .append(":(ow-iw)/2:(oh-ih)/2:color=black");
                        if (config.antiCopyright && config.antiCopyrightOpacity > 0) {
                            double alphaFloat = Math.max(0.0, Math.min(1.0, config.antiCopyrightOpacity / 100.0));
                            filter.append(String.format(Locale.US, ",drawbox=x=0:y=0:w=iw:h=ih:color=white@%.2f:t=fill", alphaFloat));
                        }
                        filter.append("[vscaled];");
                        filter.append("[bg][vscaled]overlay=").append(vX).append(":").append(vY).append("[bg_vid];");
                        filter.append("[bg_vid][0:v]overlay=").append(bX).append(":").append(bY).append(":shortest=1[vout]");

                        if (separatedAudio != null) {
                            filter.append(";[2:a]aresample=async=1000[aout]");
                        } else if (config.removeVocals) {
                            filter.append(";[1:a]stereotools=mlev=0.015625:slev=1.3,highpass=f=80,aresample=async=1000[aout]");
                        } else if (config.includeAudio) {
                            filter.append(";[1:a]aresample=async=1000[aout]");
                        }

                        command.add("-filter_complex");
                        command.add(filter.toString());
                        command.add("-map");
                        command.add("[vout]");

                        if (config.removeVocals || config.includeAudio) {
                            command.add("-map");
                            command.add("[aout]?");
                            command.add("-c:a");
                            command.add("aac");
                            command.add("-b:a");
                            command.add("192k");
                        }

                        command.add("-c:v");
                        command.add(encoderSettings.codec);
                        command.addAll(encoderSettings.extraArgs);
                        command.add("-pix_fmt");
                        command.add("yuv420p");
                        command.add("-shortest");
                        command.add(finalOutput.getAbsolutePath());

                    } else {
                        // Mode classique : Bandeau seul
                        if (config.includeAudio && fichierSelectionne != null && fichierSelectionne.exists()) {
                            command.add("-i");
                            command.add(fichierSelectionne.getAbsolutePath());
                            command.add("-map");
                            command.add("0:v:0");
                            command.add("-map");
                            command.add("1:a:0?");
                            command.add("-c:a");
                            command.add("aac");
                            command.add("-b:a");
                            command.add("192k");
                            command.add("-shortest");
                        }

                        command.add("-c:v");
                        command.add(encoderSettings.codec);
                        command.addAll(encoderSettings.extraArgs);
                        command.add("-pix_fmt");
                        command.add("yuv420p");
                        command.add(finalOutput.getAbsolutePath());
                    }

                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.redirectErrorStream(true);
                    Process process = pb.start();
                    ffmpegProcessRef.set(process);

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

                    // Buffer d'image unique réutilisé pour chaque frame (0 allocation répétée)
                    BufferedImage frameBuffer = new BufferedImage(frameW, frameH, BufferedImage.TYPE_3BYTE_BGR);
                    Graphics2D g2 = frameBuffer.createGraphics();
                    byte[] frameBytes = ((DataBufferByte) frameBuffer.getRaster().getDataBuffer()).getData();

                    long startTime = System.currentTimeMillis();
                    try (OutputStream pipeOut = new BufferedOutputStream(process.getOutputStream(), 2 * 1024 * 1024)) {
                        for (int i = 0; i < totalFrames; i++) {
                            if (isCancelled()) {
                                process.destroyForcibly();
                                return "CANCELLED";
                            }
                            double time = (double) i / fps;
                            session.renderFrameDirect(g2, time);
                            pipeOut.write(frameBytes);

                            if (i % 15 == 0 || i == totalFrames - 1) {
                                int progress = (int) Math.min(99, ((long) (i + 1) * 100 / totalFrames));
                                long elapsed = Math.max(1, System.currentTimeMillis() - startTime);
                                double currentFps = ((double) (i + 1) * 1000.0) / elapsed;
                                int remainingSec = (int) Math.max(0, (totalFrames - (i + 1)) / (currentFps > 0 ? currentFps : 30));
                                publish(new ExportProgress(progress, i + 1, totalFrames, currentFps, remainingSec, encoderSettings.displayName));
                            }
                        }
                        pipeOut.flush();
                    } catch (IOException e) {
                        if (isCancelled()) {
                            process.destroyForcibly();
                            return "CANCELLED";
                        }
                        // Le pipe s'est fermé prématurément, ffmpeg va renvoyer son code de retour et ses logs d'erreur
                    } finally {
                        g2.dispose();
                    }

                    int exitCode = process.waitFor();
                    logReader.join(2000);

                    if (isCancelled()) {
                        return "CANCELLED";
                    }

                    if (exitCode != 0) {
                        String logs = ffmpegLogs.toString();
                        if (logs.length() > 1200) {
                            logs = logs.substring(logs.length() - 1200);
                        }
                        return "FFMPEG_ERROR\n" + logs;
                    }

                    publish(new ExportProgress(100, totalFrames, totalFrames, 0, 0, encoderSettings.displayName));
                    return "OK";
                } finally {
                    if (separatedAudio != null && separatedAudio.exists()) {
                        try {
                            separatedAudio.delete();
                        } catch (Exception ignored) {}
                    }
                }
            }

            @Override
            protected void process(java.util.List<ExportProgress> chunks) {
                if (!chunks.isEmpty()) {
                    ExportProgress p = chunks.get(chunks.size() - 1);
                    progressBar.setValue(p.percent);
                    progressBar.setString(p.percent + "%");
                    if (p.encoderName != null && p.encoderName.startsWith("Demucs IA")) {
                        statusLabel.setText("Extraction vocale IA (Demucs en cours)...");
                        speedLabel.setText(p.encoderName);
                    } else {
                        statusLabel.setText("Exportation : image " + p.frame + " / " + p.total + " (" + p.percent + "%)");
                        speedLabel.setText(String.format(Locale.FRENCH,
                                "⚡ Vitesse : %.0f FPS | Restant : ~%ds | %s",
                                p.fps, p.remainingSeconds, p.encoderName));
                    }
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    String result = get();
                    if ("OK".equals(result)) {
                        JOptionPane.showMessageDialog(owner,
                                "Vidéo exportée avec succès :\n" + finalOutput.getAbsolutePath(),
                                "Export Réussi",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else if ("CANCELLED".equals(result)) {
                        if (finalOutput.exists()) {
                            finalOutput.delete();
                        }
                    } else if ("FFMPEG_NOT_FOUND".equals(result)) {
                        JOptionPane.showMessageDialog(owner,
                                "ffmpeg est introuvable.\nInstallez ffmpeg et assurez-vous qu'il est accessible dans le PATH.",
                                "Erreur", JOptionPane.ERROR_MESSAGE);
                    } else {
                        String details = "";
                        if (result != null && result.startsWith("FFMPEG_ERROR\n")) {
                            details = "\n\nDétails ffmpeg (fin de log):\n" + result.substring("FFMPEG_ERROR\n".length());
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

        btnCancel.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(progressDialog,
                    "Voulez-vous vraiment interrompre et annuler l'export vidéo ?",
                    "Annuler l'exportation",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                worker.cancel(true);
                Process p = ffmpegProcessRef.get();
                if (p != null && p.isAlive()) {
                    p.destroyForcibly();
                }
                progressDialog.dispose();
            }
        });

        progressDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                btnCancel.doClick();
            }
        });

        progressDialog.setVisible(true);
        worker.execute();
    }

    /**
     * Extrait la piste audio de la vidéo en éliminant les voix (Karaoké / Piste témoin pour doublage).
     * Utilise le réseau neuronal IA Demucs (Facebook Research) pour isoler les voix avec une haute fidélité,
     * ou se replie automatiquement sur le filtre acoustique FFmpeg si Demucs n'est pas disponible.
     */
    public void extraireAudioSansVoix(javax.swing.JFrame owner, File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            JOptionPane.showMessageDialog(owner, "Aucune vidéo chargée. Veuillez d'abord ouvrir un projet.", "Vidéo requise", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String ffmpegPath = findFfmpeg();
        if (ffmpegPath == null) {
            JOptionPane.showMessageDialog(owner, "ffmpeg est introuvable. Installation requise pour l'extraction.", "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File outputFile = FileUtils.chooseSaveFile(owner, "Exporter l'audio sans voix (Doublage / Karaoké)", "wav");
        if (outputFile == null) return;
        final File finalOutput = outputFile;

        boolean demucsAvailable = isDemucsAvailable();

        JDialog progressDialog = new JDialog(owner, "Extraction audio sans voix...", false);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setString(demucsAvailable ? "Initialisation du modèle IA Demucs..." : "Suppression des voix et isolation de l'ambiance...");
        if (!demucsAvailable) {
            progressBar.setIndeterminate(true);
        }

        JLabel titleLabel = new JLabel(demucsAvailable ? "🤖 Séparation vocale par IA (Facebook Demucs)" : "⚙️ Filtrage acoustique des voix (FFmpeg)");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        JLabel statusLabel = new JLabel(demucsAvailable ? "Préparation de l'extraction et du réseau neuronal..." : "Filtrage des fréquences vocales...");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(new Color(110, 110, 120));

        JButton btnCancel = new JButton("Annuler");
        btnCancel.setFont(new Font("Segoe UI", Font.PLAIN, 11));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JPanel headerPanel = new JPanel(new GridLayout(2, 1, 4, 4));
        headerPanel.add(titleLabel);
        headerPanel.add(statusLabel);
        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.add(btnCancel);
        panel.add(btnPanel, BorderLayout.SOUTH);

        progressDialog.add(panel);
        progressDialog.setSize(500, 160);
        progressDialog.setLocationRelativeTo(owner);
        progressDialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        AtomicReference<Process> activeProcess = new AtomicReference<>();

        SwingWorker<Boolean, String> worker = new SwingWorker<>() {
            private String usedEngine = demucsAvailable ? "Demucs IA (Haute Fidélité)" : "Filtre FFmpeg (stereotools)";

            @Override
            protected Boolean doInBackground() throws Exception {
                if (demucsAvailable) {
                    boolean isWav = finalOutput.getName().toLowerCase().endsWith(".wav");
                    File targetWav = isWav ? finalOutput : File.createTempFile("demucs_out_", ".wav");
                    if (!isWav) targetWav.deleteOnExit();

                    boolean ok = runDemucsSeparation(videoFile, targetWav, (pct, msg) -> {
                        publish(pct + ":" + msg);
                    }, () -> isCancelled(), activeProcess);

                    if (ok && targetWav.exists() && targetWav.length() > 0) {
                        if (!isWav) {
                            publish("98:Conversion vers le format de destination...");
                            ProcessBuilder convPb = new ProcessBuilder(
                                    ffmpegPath, "-y", "-i", targetWav.getAbsolutePath(),
                                    "-c:a", "libmp3lame", "-b:a", "320k", finalOutput.getAbsolutePath()
                            );
                            convPb.redirectErrorStream(true);
                            Process convP = convPb.start();
                            activeProcess.set(convP);
                            try (InputStream is = convP.getInputStream()) {
                                is.transferTo(OutputStream.nullOutputStream());
                            }
                            convP.waitFor();
                            targetWav.delete();
                        }
                        return finalOutput.exists() && finalOutput.length() > 0;
                    }

                    if (isCancelled()) return false;

                    // En cas d'échec imprévu de Demucs, repli sur le filtre FFmpeg
                    usedEngine = "Filtre FFmpeg (Repli)";
                    publish("50:Repli sur le filtre acoustique FFmpeg...");
                }

                // Filtrage acoustique standard FFmpeg (stereotools)
                String vocalFilter = "stereotools=mlev=0.015625:slev=1.3,highpass=f=80,dynaudnorm=f=120:g=15:m=10.0:r=0.9";
                java.util.List<String> cmd = new java.util.ArrayList<>();
                cmd.add(ffmpegPath);
                cmd.add("-y");
                cmd.add("-i");
                cmd.add(videoFile.getAbsolutePath());
                cmd.add("-vn");
                cmd.add("-sn");
                cmd.add("-dn");
                cmd.add("-af");
                cmd.add(vocalFilter);

                if (finalOutput.getName().toLowerCase().endsWith(".mp3")) {
                    cmd.add("-c:a");
                    cmd.add("libmp3lame");
                    cmd.add("-b:a");
                    cmd.add("320k");
                } else {
                    cmd.add("-c:a");
                    cmd.add("pcm_s16le");
                }

                cmd.add(finalOutput.getAbsolutePath());

                ProcessBuilder pb = new ProcessBuilder(cmd);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                activeProcess.set(p);

                try (InputStream is = p.getInputStream()) {
                    is.transferTo(OutputStream.nullOutputStream());
                }

                int exitCode = p.waitFor();
                return exitCode == 0 && finalOutput.exists() && finalOutput.length() > 0;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                if (!chunks.isEmpty()) {
                    String last = chunks.get(chunks.size() - 1);
                    int colonIdx = last.indexOf(':');
                    if (colonIdx != -1) {
                        try {
                            int pct = Integer.parseInt(last.substring(0, colonIdx));
                            String msg = last.substring(colonIdx + 1);
                            progressBar.setIndeterminate(false);
                            progressBar.setValue(pct);
                            progressBar.setString(pct + "% - " + msg);
                            statusLabel.setText(msg);
                        } catch (Exception ignored) {}
                    } else {
                        statusLabel.setText(last);
                    }
                }
            }

            @Override
            protected void done() {
                progressDialog.dispose();
                if (isCancelled()) {
                    if (finalOutput.exists()) finalOutput.delete();
                    return;
                }
                try {
                    boolean success = get();
                    if (success) {
                        JOptionPane.showMessageDialog(owner,
                                "Piste audio sans les voix exportée avec succès !\n\n" +
                                "Moteur utilisé : " + usedEngine + "\n" +
                                "Fichier : " + finalOutput.getAbsolutePath() +
                                "\n\nLes voix ont été retirées tout en préservant la musique, les bruitages et l'ambiance sonore.",
                                "Extraction Réussie",
                                JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(owner,
                                "Erreur lors de l'extraction audio sans voix.",
                                "Erreur",
                                JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(owner,
                            "Erreur : " + ex.getMessage(),
                            "Erreur",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        btnCancel.addActionListener(e -> {
            worker.cancel(true);
            Process p = activeProcess.get();
            if (p != null) p.destroyForcibly();
            progressDialog.dispose();
        });
        progressDialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                worker.cancel(true);
                Process p = activeProcess.get();
                if (p != null) p.destroyForcibly();
                progressDialog.dispose();
            }
        });

        progressDialog.setVisible(true);
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

    public static class EncoderSettings {
        public final String codec;
        public final java.util.List<String> extraArgs;
        public final String displayName;

        public EncoderSettings(String codec, java.util.List<String> extraArgs, String displayName) {
            this.codec = codec;
            this.extraArgs = extraArgs;
            this.displayName = displayName;
        }
    }

    private static EncoderSettings cachedEncoderSettings = null;

    public static synchronized EncoderSettings detectEncoder(String ffmpegPath, String preference) {
        if ("cpu".equalsIgnoreCase(preference)) {
            return new EncoderSettings("libx264",
                    java.util.List.of("-preset", "veryfast", "-crf", "22", "-threads", "0"),
                    "CPU Multi-cœurs (x264 veryfast)");
        }
        if ("nvenc".equalsIgnoreCase(preference)) {
            if (testEncoder(ffmpegPath, "h264_nvenc", java.util.List.of("-preset", "p4", "-cq", "23"))) {
                return new EncoderSettings("h264_nvenc",
                        java.util.List.of("-preset", "p4", "-cq", "23"),
                        "GPU NVIDIA NVENC");
            }
        }
        if (cachedEncoderSettings != null && ("auto".equalsIgnoreCase(preference) || preference == null)) {
            return cachedEncoderSettings;
        }
        if (testEncoder(ffmpegPath, "h264_nvenc", java.util.List.of("-preset", "p4", "-cq", "23"))) {
            cachedEncoderSettings = new EncoderSettings("h264_nvenc",
                    java.util.List.of("-preset", "p4", "-cq", "23"),
                    "GPU NVIDIA NVENC");
            return cachedEncoderSettings;
        }
        if (testEncoder(ffmpegPath, "h264_amf", java.util.List.of("-quality", "speed"))) {
            cachedEncoderSettings = new EncoderSettings("h264_amf",
                    java.util.List.of("-quality", "speed"),
                    "GPU AMD AMF");
            return cachedEncoderSettings;
        }
        if (testEncoder(ffmpegPath, "h264_qsv", java.util.List.of("-preset", "veryfast"))) {
            cachedEncoderSettings = new EncoderSettings("h264_qsv",
                    java.util.List.of("-preset", "veryfast"),
                    "GPU Intel QSV");
            return cachedEncoderSettings;
        }
        cachedEncoderSettings = new EncoderSettings("libx264",
                java.util.List.of("-preset", "veryfast", "-crf", "22", "-threads", "0"),
                "CPU Multi-cœurs (x264 veryfast)");
        return cachedEncoderSettings;
    }

    private static boolean testEncoder(String ffmpegPath, String codec, java.util.List<String> extraArgs) {
        try {
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(ffmpegPath);
            cmd.add("-y");
            cmd.add("-f"); cmd.add("lavfi");
            cmd.add("-i"); cmd.add("color=c=black:s=64x64:d=0.04");
            cmd.add("-c:v"); cmd.add(codec);
            cmd.addAll(extraArgs);
            cmd.add("-f"); cmd.add("null");
            cmd.add("-");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public static class ExportProgress {
        public final int percent;
        public final int frame;
        public final int total;
        public final double fps;
        public final int remainingSeconds;
        public final String encoderName;

        public ExportProgress(int percent, int frame, int total, double fps, int remainingSeconds, String encoderName) {
            this.percent = percent;
            this.frame = frame;
            this.total = total;
            this.fps = fps;
            this.remainingSeconds = remainingSeconds;
            this.encoderName = encoderName;
        }
    }

    public File findDemucsWorkerScript() {
        File f = new File("whisperx_engine/demucs_worker.py");
        if (f.exists()) return f;

        File parentLocal = new File("OmeRyth/whisperx_engine/demucs_worker.py");
        if (parentLocal.exists()) return parentLocal;

        return null;
    }

    public String findPython() {
        String[] candidates = {"python", "python3"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                try (InputStream is = p.getInputStream()) {
                    is.transferTo(OutputStream.nullOutputStream());
                }
                if (p.waitFor() == 0) return cmd;
            } catch (Exception ignored) {}
        }
        return null;
    }

    private static Boolean cachedDemucsAvailable = null;

    public boolean isDemucsAvailable() {
        if (cachedDemucsAvailable != null) {
            return cachedDemucsAvailable;
        }
        String python = findPython();
        File script = findDemucsWorkerScript();
        if (python == null || script == null) {
            cachedDemucsAvailable = false;
            return false;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(python, "-c", "import demucs, torch");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (InputStream is = p.getInputStream()) {
                is.transferTo(OutputStream.nullOutputStream());
            }
            cachedDemucsAvailable = (p.waitFor() == 0);
            return cachedDemucsAvailable;
        } catch (Exception e) {
            cachedDemucsAvailable = false;
            return false;
        }
    }

    public boolean runDemucsSeparation(File inputFile,
                                       File outputFile,
                                       java.util.function.BiConsumer<Integer, String> progressCallback,
                                       java.util.function.BooleanSupplier cancelChecker,
                                       AtomicReference<Process> activeProcessRef) {
        String python = findPython();
        File script = findDemucsWorkerScript();
        if (python == null || script == null) return false;

        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(python);
        cmd.add("-u");
        cmd.add(script.getAbsolutePath());
        cmd.add("--input");
        cmd.add(inputFile.getAbsolutePath());
        cmd.add("--output");
        cmd.add(outputFile.getAbsolutePath());
        cmd.add("--stem");
        cmd.add("no_vocals");
        cmd.add("--device");
        cmd.add("auto");

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            if (activeProcessRef != null) {
                activeProcessRef.set(process);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (cancelChecker != null && cancelChecker.getAsBoolean()) {
                        process.destroyForcibly();
                        return false;
                    }
                    if (line.startsWith("PROGRESS:")) {
                        String[] parts = line.split(":", 4);
                        if (parts.length >= 4) {
                            try {
                                int step = Integer.parseInt(parts[1]);
                                String msg = parts[3];
                                if (progressCallback != null) {
                                    progressCallback.accept(step, msg);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }

            int exitCode = process.waitFor();
            return exitCode == 0 && outputFile.exists() && outputFile.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    public String findFfmpeg() {
        File local = new File("ffmpeg/ffmpeg.exe");
        if (local.exists()) return local.getAbsolutePath();

        File parentLocal = new File("OmeRyth/ffmpeg/ffmpeg.exe");
        if (parentLocal.exists()) return parentLocal.getAbsolutePath();

        try {
            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-version");
            Process p = pb.start();
            p.destroy();
            return "ffmpeg";
        } catch (Exception ignored) {}

        File common = new File("C:/ffmpeg/bin/ffmpeg.exe");
        if (common.exists()) return common.getAbsolutePath();

        return null;
    }
}
