package app.utils;

import app.ui.OmeTheme;
import app.ui.TimelinePanel;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;

/**
 * Composant de timer affichant le temps courant et pilotant la timeline.
 * Utilise une horloge système en temps réel (System.currentTimeMillis()) pour garantir
 * une synchronisation 1:1 absolue avec la vitesse de la vidéo (sans retard ni lenteur)
 * tout en conservant une fluidité d'affichage 60 FPS parfaite.
 */
public class TimerClass extends JPanel {
    private double time = 0; // secondes
    private double maxTime = Double.MAX_VALUE; // durée maximale du timer
    private int direction = 1; // 1 = forward, -1 = reverse
    private JLabel timerLabel;
    private JLabel statusTextLabel;
    private JPanel statusDot;
    private JPanel hudTopBar;
    private Timer timer;
    private Color themeColor = OmeTheme.bgRaised();
    private String imagePath = "";
    private String cachedPath = "";
    private BufferedImage cachedImage;

    private TimelinePanel timeline;

    // Horloge haute précision (nanoTime) pour éviter les micro-saccades de 15.6ms de Windows
    private long startRealTimeNs = 0;
    private double startRythmoTime = 0;

    private java.util.function.DoubleSupplier externalTimeSource = null;
    private java.util.function.BooleanSupplier externalIsPlaying = null;
    private long lastSyncNs = 0L;

    public void setExternalTimeSource(java.util.function.DoubleSupplier timeSupplier, java.util.function.BooleanSupplier isPlayingSupplier) {
        this.externalTimeSource = timeSupplier;
        this.externalIsPlaying = isPlayingSupplier;
    }

    /**
     * Crée un composant timer lié à une `TimelinePanel` pour propager la position temporelle.
     */
    public TimerClass(TimelinePanel timeline) {
        this.timeline = timeline;

        setLayout(new BorderLayout());
        setBackground(themeColor);
        setPreferredSize(new Dimension(180, 110));
        setMinimumSize(new Dimension(180, 100));
        setOpaque(false);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, OmeTheme.border()));

        hudTopBar = new JPanel(new BorderLayout(6, 0));
        hudTopBar.setOpaque(false);
        hudTopBar.setBorder(new EmptyBorder(8, 12, 0, 12));

        JPanel leftTag = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        leftTag.setOpaque(false);

        statusDot = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean running = timer != null && timer.isRunning();
                if (running) {
                    g2.setColor(OmeTheme.accentSubtle());
                    g2.fillOval(0, 0, 10, 10);
                    g2.setColor(OmeTheme.accent());
                } else {
                    g2.setColor(OmeTheme.textTertiary());
                }
                g2.fillOval(2, 2, 6, 6);
                g2.dispose();
            }
        };
        statusDot.setOpaque(false);
        statusDot.setPreferredSize(new Dimension(10, 10));

        JLabel hudTitle = new JLabel("TIMECODE");
        hudTitle.setFont(OmeTheme.fontMono(Font.BOLD, 10));
        hudTitle.setForeground(OmeTheme.textTertiary());

        leftTag.add(statusDot);
        leftTag.add(hudTitle);

        statusTextLabel = new JLabel("PAUSE");
        statusTextLabel.setFont(OmeTheme.fontMono(Font.BOLD, 10));
        statusTextLabel.setForeground(OmeTheme.textTertiary());

        hudTopBar.add(leftTag, BorderLayout.WEST);
        hudTopBar.add(statusTextLabel, BorderLayout.EAST);

        timerLabel = new JLabel("00:00:00.0", SwingConstants.CENTER);
        timerLabel.setFont(OmeTheme.fontMono(Font.BOLD, 26));
        timerLabel.setForeground(OmeTheme.text());
        timerLabel.setOpaque(false);
        timerLabel.setBorder(new EmptyBorder(0, 8, 10, 8));

        add(hudTopBar, BorderLayout.NORTH);
        add(timerLabel, BorderLayout.CENTER);

        // Horloge 60 FPS calibrée sur nanoTime (16ms) avec coalescence :
        // évite les micro-saccades de battement d'affichage et garantit une fluidité parfaite
        timer = new Timer(16, e -> update());
        timer.setCoalesce(true);
    }

    private Runnable onStopCallback;
    private String lastFormattedTime = "";

    public void setOnStopCallback(Runnable onStopCallback) {
        this.onStopCallback = onStopCallback;
    }

    private void update() {
        if (timeline == null) return;

        // Calcul haute précision du temps réel écoulé (sans saccades)
        long now = System.nanoTime();
        double elapsedSec = (now - startRealTimeNs) / 1_000_000_000.0;
        time = startRythmoTime + (elapsedSec * direction);



        if (time >= maxTime) {
            time = maxTime; // bloque le timer
            timer.stop();
            startRythmoTime = time;
            timerLabel.setText(format());
            timeline.setTime(time);
            refreshHudIndicator();
            if (onStopCallback != null) onStopCallback.run();
        }
        if (time <= 0) {
            time = 0;
            timer.stop();
            startRythmoTime = time;
            timerLabel.setText(format());
            timeline.setTime(time);
            refreshHudIndicator();
            if (onStopCallback != null) onStopCallback.run();
        }

        // Met à jour le label uniquement quand les dixièmes de seconde changent
        // (évite 60-100 appels de revalidate() par seconde sur le thread Swing)
        String formatted = format();
        if (!formatted.equals(lastFormattedTime)) {
            lastFormattedTime = formatted;
            timerLabel.setText(formatted);
        }

        timeline.setTime(time);
    }

    private String format() {
        long totalTenths = Math.max(0L, Math.round(time * 10.0));
        int min = (int) (totalTenths / 600L);
        int sec = (int) ((totalTenths / 10L) % 60L);
        int tenth = (int) (totalTenths % 10L);
        return String.format("%02d:%02d:%02d.%d", min / 60, min % 60, sec, tenth);
    }

    /**
     * Bascule l'état de lecture du chronomètre (lecture / pause) dans le sens de défilement actuel.
     */
    public void toggle() {
        toggle(direction);
    }

    /**
     * Bascule l'état de lecture dans la direction demandée (+1 avance, -1 recul).
     * <p>
     * À la mise en pause (notamment avec la barre Espace), la lecture s'arrête instantanément
     * sur la position courante exacte sans aucun recul (rollback) ni à-coup visuel.
     * </p>
     *
     * @param newDirection Sens souhaité (>= 0 : avance, < 0 : marche arrière).
     */
    public void toggle(int newDirection) {
        int requestedDirection = newDirection >= 0 ? 1 : -1;
        if (timer.isRunning()) {
            if (direction == requestedDirection) {
                // Calage haute précision sur l'instant exact d'arrêt sans rollback ni à-coup
                long now = System.nanoTime();
                double elapsedSec = (now - startRealTimeNs) / 1_000_000_000.0;
                time = startRythmoTime + (elapsedSec * direction);
                // Arrondi exact au dixième de seconde (0.1s) à la pause pour calage parfait sur les marquages
                time = Math.round(time * 10.0) / 10.0;
                timer.stop();
                if (maxTime > 0 && time > maxTime) {
                    time = maxTime;
                }
                if (time < 0) {
                    time = 0;
                }
                startRythmoTime = time;
                timerLabel.setText(format());
                timeline.setTime(time);
            } else {
                direction = requestedDirection;
                startRealTimeNs = System.nanoTime();
                startRythmoTime = time;
            }
        } else {
            direction = requestedDirection;
            startRealTimeNs = System.nanoTime();
            startRythmoTime = time;
            timer.start();
        }
        refreshHudIndicator();
    }

    /**
     * Réinitialise le chronomètre au début absolu (0.0 seconde) et réaligne la timeline.
     */
    public void reset() {
        timer.stop();
        time = 0;
        startRythmoTime = 0;
        timerLabel.setText(format());
        timeline.setTime(0);
        refreshHudIndicator();
    }

    public boolean isRunning() {
        return timer.isRunning();
    }

    // ==========================
    /**
     * Définit la durée temporelle maximale autorisée pour le chronomètre (borne supérieure calée sur la vidéo).
     */
    public void setMaxTime(double maxTime) {
        this.maxTime = maxTime;
    }

    public double getTime() {
        return time;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int newDirection) {
        this.direction = newDirection >= 0 ? 1 : -1;
        startRealTimeNs = System.nanoTime();
        startRythmoTime = this.time;
    }
    
    public void setTime(double newTime) {
        this.time = Math.max(0, newTime);
        if (maxTime > 0 && this.time > maxTime) {
            this.time = maxTime;
        }
        startRealTimeNs = System.nanoTime();
        startRythmoTime = this.time;
        timerLabel.setText(format());
        if (timeline != null) {
            timeline.setTime(this.time);
        }
    }
    
    public double getMaxTime() {
        return maxTime;
    }

    /**
     * Ajoute un décalage temporel relatif (delta en secondes) au chronomètre courant et actualise la timeline.
     */
    public void addTime(double delta) {
        this.time += delta;
        if (this.time < 0) this.time = 0;

        // Limiter à la durée maximale configurée
        if (maxTime > 0 && time > maxTime) {
            time = maxTime;
            timer.stop();
            if (onStopCallback != null) onStopCallback.run();
        }

        startRealTimeNs = System.nanoTime();
        startRythmoTime = this.time;
        timerLabel.setText(format());
        timeline.setTime(time);
    }

    /**
     * Définit la couleur de fond du thème visuel du chronomètre.
     */
    public void setTheme(Color background) {
        setTheme(background, "");
    }

    /**
     * Définit la couleur de fond et le chemin optionnel d'une image d'arrière-plan pour le chronomètre.
     */
    public void setTheme(Color background, String imagePath) {
        if (background != null) {
            themeColor = background;
            setBackground(background);
        }
        this.imagePath = imagePath == null ? "" : imagePath.trim();
        repaint();
    }

    private void refreshHudIndicator() {
        if (statusTextLabel == null || statusDot == null) return;
        boolean running = timer != null && timer.isRunning();
        if (running) {
            statusTextLabel.setText(direction >= 0 ? "LIVE ▶" : "◀ REV");
            statusTextLabel.setForeground(OmeTheme.accent());
        } else {
            statusTextLabel.setText("PAUSE");
            statusTextLabel.setForeground(OmeTheme.textTertiary());
        }
        statusDot.repaint();
    }

    public void applyCustomization(app.ui.AppCustomization c) {
        if (c.autoResizeTimerFont) {
            int newSize = Math.max(12, Math.min(34, c.timerPanelWidth / 7));
            timerLabel.setFont(OmeTheme.fontMono(Font.BOLD, newSize));
        } else {
            timerLabel.setFont(OmeTheme.fontMono(Font.BOLD, c.timerFontSize));
        }
        timerLabel.setForeground(c.timerTextColor);
        setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, OmeTheme.border()));
        refreshHudIndicator();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        BufferedImage img = resolveImage();
        if (img != null) {
            g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
        } else {
            g2.setColor(themeColor);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }
        g2.dispose();
        super.paintComponent(g);
    }

    private BufferedImage resolveImage() {
        if (imagePath.isEmpty()) {
            cachedPath = "";
            cachedImage = null;
            return null;
        }
        if (imagePath.equals(cachedPath) && cachedImage != null) {
            return cachedImage;
        }
        try {
            cachedImage = ImageIO.read(new File(imagePath));
            cachedPath = imagePath;
            return cachedImage;
        } catch (Exception e) {
            cachedImage = null;
            cachedPath = imagePath;
            return null;
        }
    }
}
