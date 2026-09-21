package app.utils;

import app.ui.TimelinePanel;
import javax.swing.*;
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
    private Timer timer;
    private Color themeColor = new Color(25, 25, 25);
    private String imagePath = "";
    private String cachedPath = "";
    private BufferedImage cachedImage;

    private TimelinePanel timeline;

    // Horloge haute précision (nanoTime) pour éviter les micro-saccades de 15.6ms de Windows
    private long startRealTimeNs = 0;
    private double startRythmoTime = 0;

    /**
     * Crée un composant timer lié à une `TimelinePanel` pour propager la position temporelle.
     */
    public TimerClass(TimelinePanel timeline) {
        this.timeline = timeline;

        setLayout(new BorderLayout());
        setBackground(themeColor);
        setPreferredSize(new Dimension(180, 120));
        setMinimumSize(new Dimension(180, 120));
        setOpaque(false);

        timerLabel = new JLabel("00:00:00.0", SwingConstants.CENTER);
        timerLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        timerLabel.setForeground(new Color(230, 230, 230)); // Modern off-white
        timerLabel.setOpaque(false);

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
            if (onStopCallback != null) onStopCallback.run();
        }
        if (time <= 0) {
            time = 0;
            timer.stop();
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
     * À la mise en pause, le temps est tronqué au dixième de seconde inférieur (ex: 1.37s -> 1.30s)
     * pour garantir un alignement géométrique net avec les graduations du quadrillage de la bande rythmo.
     * </p>
     *
     * @param newDirection Sens souhaité (>= 0 : avance, < 0 : marche arrière).
     */
    public void toggle(int newDirection) {
        int requestedDirection = newDirection >= 0 ? 1 : -1;
        if (timer.isRunning()) {
            if (direction == requestedDirection) {
                timer.stop();
                // À la mise en pause, troncature au dixième inférieur (ex: 1.32s -> 1.30s)
                time = Math.floor(time * 10.0) / 10.0;
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

    public void applyCustomization(app.ui.AppCustomization c) {
        if (c.autoResizeTimerFont) {
            // Un ratio de 5 semble bon pour 180px -> 36.
            int newSize = Math.max(10, c.timerPanelWidth / 5);
            timerLabel.setFont(new Font("Segoe UI", Font.BOLD, newSize));
        } else {
            timerLabel.setFont(new Font("Segoe UI", Font.BOLD, c.timerFontSize));
        }
        timerLabel.setForeground(c.timerTextColor);
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
