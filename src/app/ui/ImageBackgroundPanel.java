package app.ui;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

public class ImageBackgroundPanel extends JPanel {

    private Color fallbackColor = new Color(40, 40, 40);
    private String imagePath = "";
    private String cachedPath = "";
    private BufferedImage cachedImage;

    public ImageBackgroundPanel(LayoutManager layout) {
        super(layout);
        setOpaque(false);
    }

    /** Set the panel fallback color and optional image path for the background. */
    public void setBackgroundStyle(Color fallbackColor, String imagePath) {
        if (fallbackColor != null) {
            this.fallbackColor = fallbackColor;
        }
        this.imagePath = imagePath == null ? "" : imagePath.trim();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        BufferedImage img = resolveImage();

        if (img != null) {
            g2.drawImage(img, 0, 0, getWidth(), getHeight(), null);
        } else {
            g2.setColor(fallbackColor);
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
