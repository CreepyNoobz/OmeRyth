package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * Canevas visuel interactif pour la composition du montage d'export vidéo.
 * Affiche en temps réel le cadre d'export (16:9, 9:16 vertical TikTok/Shorts, etc.)
 * et permet de déplacer et redimensionner directement à la souris la Vidéo et la Bande Rythmo.
 */
public class MontagePreviewCanvas extends JPanel {

    public enum ElementType {
        NONE, VIDEO, BAND
    }

    private static final int HANDLE_SIZE = 8;
    private static final int MIN_ELEMENT_SIZE = 30;

    // Poignées de redimensionnement :
    // 0: NW, 1: N, 2: NE, 3: E, 4: SE, 5: S, 6: SW, 7: W
    private static final int HANDLE_NW = 0;
    private static final int HANDLE_N  = 1;
    private static final int HANDLE_NE = 2;
    private static final int HANDLE_E  = 3;
    private static final int HANDLE_SE = 4;
    private static final int HANDLE_S  = 5;
    private static final int HANDLE_SW = 6;
    private static final int HANDLE_W  = 7;

    private int exportWidth = 1920;
    private int exportHeight = 1080;

    private final Rectangle videoRect = new Rectangle(0, 0, 1920, 982);
    private final Rectangle bandRect  = new Rectangle(0, 982, 1920, 98);

    private ElementType selectedElement = ElementType.BAND;

    private int bandCount = 1;
    private TimelinePanel timelinePanel;
    private BufferedImage videoSnapshot;
    private BufferedImage cachedRealBandImage;
    private int cachedBandW = -1;
    private int cachedBandH = -1;
    private double cachedVisibleSeconds = 8.0;
    private double visibleSeconds = 8.0;
    private double previewTime = 0.0;
    private boolean antiCopyright = false;
    private int antiCopyrightOpacity = 20;

    private boolean blurBackgroundVideo = false;
    private int blurRadius = 25;
    private int blurOpacity = 100;
    private java.util.List<String> layerOrder = new java.util.ArrayList<>(java.util.List.of("BACKGROUND_BLUR", "VIDEO", "BAND"));
    private BufferedImage cachedBlurredSnapshot = null;
    private int cachedBlurRadius = -1;
    private BufferedImage cachedBlurSource = null;

    private boolean isDragging = false;
    private boolean isResizing = false;
    private int activeHandle = -1;

    private Point dragStartMouse = new Point();
    private Rectangle dragStartRect = new Rectangle();

    private Runnable onLayoutChanged;

    public MontagePreviewCanvas() {
        setBackground(new Color(24, 24, 27));
        setPreferredSize(new Dimension(540, 380));
        setMinimumSize(new Dimension(320, 240));

        MouseAdapter mouseHandler = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleMousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleMouseReleased(e);
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                handleMouseDragged(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                handleMouseMoved(e);
            }
        };

        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public void setOnLayoutChanged(Runnable callback) {
        this.onLayoutChanged = callback;
    }

    public void setTimelinePanel(TimelinePanel panel) {
        this.timelinePanel = panel;
        if (panel != null) {
            this.bandCount = Math.max(1, panel.getBandCount());
            this.previewTime = panel.getCurrentTime();
        }
        invalidateBandCache();
        repaint();
    }

    public void setBandCount(int count) {
        this.bandCount = Math.max(1, count);
        invalidateBandCache();
        repaint();
    }

    public int getBandCount() {
        return bandCount;
    }

    public void setVideoSnapshot(BufferedImage snapshot) {
        this.videoSnapshot = snapshot;
        repaint();
    }

    public void setVisibleSeconds(double sec) {
        this.visibleSeconds = Math.max(1.0, sec);
        invalidateBandCache();
        repaint();
    }

    public void setPreviewTime(double time) {
        this.previewTime = Math.max(0.0, time);
        invalidateBandCache();
        repaint();
    }

    public void setAntiCopyright(boolean antiCopyright) {
        this.antiCopyright = antiCopyright;
        repaint();
    }

    public void setAntiCopyright(boolean antiCopyright, int opacityPercent) {
        this.antiCopyright = antiCopyright;
        this.antiCopyrightOpacity = Math.max(0, Math.min(100, opacityPercent));
        repaint();
    }

    public void setAntiCopyrightOpacity(int opacityPercent) {
        this.antiCopyrightOpacity = Math.max(0, Math.min(100, opacityPercent));
        repaint();
    }

    public int getAntiCopyrightOpacity() {
        return antiCopyrightOpacity;
    }

    public boolean isAntiCopyright() {
        return antiCopyright;
    }

    public void setBlurBackgroundVideo(boolean blur) {
        this.blurBackgroundVideo = blur;
        repaint();
    }

    public boolean isBlurBackgroundVideo() {
        return blurBackgroundVideo;
    }

    public void setBlurRadius(int radius) {
        this.blurRadius = Math.max(1, Math.min(60, radius));
        this.cachedBlurredSnapshot = null;
        repaint();
    }

    public int getBlurRadius() {
        return blurRadius;
    }

    public void setBlurOpacity(int opacityPercent) {
        this.blurOpacity = Math.max(10, Math.min(100, opacityPercent));
        repaint();
    }

    public int getBlurOpacity() {
        return blurOpacity;
    }

    public void setLayerOrder(java.util.List<String> order) {
        if (order != null && !order.isEmpty()) {
            this.layerOrder = new java.util.ArrayList<>(order);
            repaint();
        }
    }

    public java.util.List<String> getLayerOrder() {
        return new java.util.ArrayList<>(layerOrder);
    }

    public BufferedImage getBlurredSnapshot() {
        if (videoSnapshot == null) return null;
        if (cachedBlurredSnapshot != null && cachedBlurRadius == blurRadius && cachedBlurSource == videoSnapshot) {
            return cachedBlurredSnapshot;
        }
        cachedBlurredSnapshot = createBlurredSnapshot(videoSnapshot, blurRadius);
        cachedBlurRadius = blurRadius;
        cachedBlurSource = videoSnapshot;
        return cachedBlurredSnapshot;
    }

    public static BufferedImage createBlurredSnapshot(BufferedImage src, int radius) {
        if (src == null) return null;
        int w = Math.max(32, src.getWidth() / 4);
        int h = Math.max(18, src.getHeight() / 4);
        BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();

        int r = Math.max(1, Math.min(30, radius / 2));
        boxBlur(small, r);
        boxBlur(small, r);
        return small;
    }

    private static void boxBlur(BufferedImage img, int radius) {
        int w = img.getWidth();
        int h = img.getHeight();
        int[] pixels = new int[w * h];
        img.getRGB(0, 0, w, h, pixels, 0, w);
        int[] temp = new int[w * h];

        for (int y = 0; y < h; y++) {
            int yOffset = y * w;
            for (int x = 0; x < w; x++) {
                int r = 0, g = 0, b = 0, count = 0;
                for (int kx = -radius; kx <= radius; kx++) {
                    int px = Math.min(w - 1, Math.max(0, x + kx));
                    int rgb = pixels[yOffset + px];
                    r += (rgb >> 16) & 0xFF;
                    g += (rgb >> 8) & 0xFF;
                    b += rgb & 0xFF;
                    count++;
                }
                temp[yOffset + x] = (0xFF << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
            }
        }

        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int r = 0, g = 0, b = 0, count = 0;
                for (int ky = -radius; ky <= radius; ky++) {
                    int py = Math.min(h - 1, Math.max(0, y + ky));
                    int rgb = temp[py * w + x];
                    r += (rgb >> 16) & 0xFF;
                    g += (rgb >> 8) & 0xFF;
                    b += rgb & 0xFF;
                    count++;
                }
                pixels[y * w + x] = (0xFF << 24) | ((r / count) << 16) | ((g / count) << 8) | (b / count);
            }
        }

        img.setRGB(0, 0, w, h, pixels, 0, w);
    }

    public void invalidateBandCache() {
        this.cachedRealBandImage = null;
        this.cachedBandW = -1;
        this.cachedBandH = -1;
    }

    public void setExportResolution(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.exportWidth = width;
        this.exportHeight = height;

        // Si les éléments dépassent totalement les nouvelles dimensions, on les repositionne de façon saine
        clampElementToBounds(videoRect);
        clampElementToBounds(bandRect);
        invalidateBandCache();

        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    public int getExportWidth() {
        return exportWidth;
    }

    public int getExportHeight() {
        return exportHeight;
    }

    public Rectangle getVideoRect() {
        return new Rectangle(videoRect);
    }

    public Rectangle getBandRect() {
        return new Rectangle(bandRect);
    }

    public void setVideoRect(int x, int y, int w, int h) {
        videoRect.setBounds(x, y, Math.max(MIN_ELEMENT_SIZE, w), Math.max(MIN_ELEMENT_SIZE, h));
        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    public void setBandRect(int x, int y, int w, int h) {
        bandRect.setBounds(x, y, Math.max(MIN_ELEMENT_SIZE, w), Math.max(MIN_ELEMENT_SIZE, h));
        invalidateBandCache();
        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    public ElementType getSelectedElement() {
        return selectedElement;
    }

    public void setSelectedElement(ElementType sel) {
        this.selectedElement = sel;
        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    public void centerSelectedHorizontally() {
        Rectangle r = getSelectedRectangle();
        if (r == null) return;
        r.x = Math.max(0, (exportWidth - r.width) / 2);
        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    public void centerSelectedVertically() {
        Rectangle r = getSelectedRectangle();
        if (r == null) return;
        r.y = Math.max(0, (exportHeight - r.height) / 2);
        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    public void setSelectedFullWidth() {
        Rectangle r = getSelectedRectangle();
        if (r == null) return;
        r.x = 0;
        r.width = exportWidth;
        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    public void applyLayoutTemplate(String templateName) {
        if ("OMERYTH_ORIGINAL".equals(templateName) || "CLASSIC_16_9".equals(templateName)) {
            // Disposition OmeRyth d'origine :
            // Règle d'adaptation selon le nombre de bandes :
            // 1 bande : hauteur de base
            // 2 bandes : hauteur divisée par 2 pour chaque bande (hauteur totale identique)
            // au-delà de 2 bandes : on augmente la taille globale
            int baseSingleH = Math.max(60, Math.min(140, (int) Math.round(exportHeight * 0.09)));
            int bandH = ExportVideoDialog.computeExportBandHeight(bandCount, baseSingleH);
            if (bandH % 2 != 0) bandH++;
            int vidH = Math.max(100, exportHeight - bandH);
            videoRect.setBounds(0, 0, exportWidth, vidH);
            bandRect.setBounds(0, vidH, exportWidth, bandH);
        } else if ("OVERLAY_BOTTOM".equals(templateName)) {
            // Vidéo plein écran, Bandeau incrusté en bas
            int baseSingleH = Math.max(60, Math.min(140, (int) Math.round(exportHeight * 0.09)));
            int bandH = ExportVideoDialog.computeExportBandHeight(bandCount, baseSingleH);
            if (bandH % 2 != 0) bandH++;
            videoRect.setBounds(0, 0, exportWidth, exportHeight);
            bandRect.setBounds(0, exportHeight - bandH, exportWidth, bandH);
        } else if ("LARGE_BAND_25".equals(templateName)) {
            // Grand bandeau studio (25% base pour 1 bande, adapté si plus de bandes)
            int baseSingleH = (int) Math.round(exportHeight * 0.25);
            int bandH = ExportVideoDialog.computeExportBandHeight(bandCount, baseSingleH);
            if (bandH % 2 != 0) bandH++;
            int vidH = Math.max(100, exportHeight - bandH);
            videoRect.setBounds(0, 0, exportWidth, vidH);
            bandRect.setBounds(0, vidH, exportWidth, bandH);
        } else if ("TIKTOK_CENTER_9_16".equals(templateName)) {
            // 9:16 Vertical TikTok : Vidéo 16:9 au milieu, Bandeau sous la vidéo
            int vidW = exportWidth;
            int vidH = (int) Math.round(vidW * (9.0 / 16.0));
            if (vidH % 2 != 0) vidH++;
            int baseBandH = Math.min(180, Math.max(70, (int) Math.round(exportHeight * 0.08)));
            int bandH = ExportVideoDialog.computeExportBandHeight(bandCount, baseBandH);
            if (bandH % 2 != 0) bandH++;
            int vidY = (exportHeight - vidH - bandH) / 2;
            if (vidY < 40) vidY = 40;
            videoRect.setBounds(0, vidY, vidW, vidH);
            bandRect.setBounds(0, vidY + vidH + 15, exportWidth, bandH);
        } else if ("STACKED_TOP_BOTTOM".equals(templateName)) {
            // 50% / 50%
            int half = exportHeight / 2;
            if (half % 2 != 0) half++;
            videoRect.setBounds(0, 0, exportWidth, half);
            bandRect.setBounds(0, half, exportWidth, exportHeight - half);
        }

        invalidateBandCache();
        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    private Rectangle getSelectedRectangle() {
        if (selectedElement == ElementType.VIDEO) return videoRect;
        if (selectedElement == ElementType.BAND) return bandRect;
        return null;
    }

    private void clampElementToBounds(Rectangle r) {
        if (r.width > exportWidth) r.width = exportWidth;
        if (r.height > exportHeight) r.height = exportHeight;
        if (r.x + r.width > exportWidth) r.x = Math.max(0, exportWidth - r.width);
        if (r.y + r.height > exportHeight) r.y = Math.max(0, exportHeight - r.height);
        if (r.x < 0) r.x = 0;
        if (r.y < 0) r.y = 0;
    }

    // ===== Calculs d'échelle et transformations de coordonnées =====

    private static class SheetMetrics {
        int sheetX, sheetY, sheetW, sheetH;
        double scale;
    }

    private SheetMetrics computeSheetMetrics() {
        SheetMetrics m = new SheetMetrics();
        int panelW = Math.max(10, getWidth());
        int panelH = Math.max(10, getHeight());
        int pad = 24;
        int availW = Math.max(10, panelW - pad * 2);
        int availH = Math.max(10, panelH - pad * 2);

        m.scale = Math.min((double) availW / exportWidth, (double) availH / exportHeight);
        m.sheetW = Math.max(10, (int) Math.round(exportWidth * m.scale));
        m.sheetH = Math.max(10, (int) Math.round(exportHeight * m.scale));
        m.sheetX = (panelW - m.sheetW) / 2;
        m.sheetY = (panelH - m.sheetH) / 2;
        return m;
    }

    private Rectangle toScreenRect(Rectangle exportRect, SheetMetrics m) {
        int sx = m.sheetX + (int) Math.round(exportRect.x * m.scale);
        int sy = m.sheetY + (int) Math.round(exportRect.y * m.scale);
        int sw = Math.max(4, (int) Math.round(exportRect.width * m.scale));
        int sh = Math.max(4, (int) Math.round(exportRect.height * m.scale));
        return new Rectangle(sx, sy, sw, sh);
    }

    // ===== Gestion des événements de souris =====

    private void handleMousePressed(MouseEvent e) {
        SheetMetrics m = computeSheetMetrics();
        Point p = e.getPoint();

        // 1. Vérifier si on clique sur une poignée de l'élément sélectionné
        Rectangle selExport = getSelectedRectangle();
        if (selExport != null) {
            Rectangle selScreen = toScreenRect(selExport, m);
            int handle = getHandleAtPoint(selScreen, p);
            if (handle != -1) {
                isResizing = true;
                isDragging = false;
                activeHandle = handle;
                dragStartMouse = p;
                dragStartRect = new Rectangle(selExport);
                return;
            }
        }

        // 2. Vérifier si on clique sur un des éléments (selon l'ordre des calques, du 1er plan vers l'arrière)
        Rectangle bandScreen = toScreenRect(bandRect, m);
        Rectangle videoScreen = toScreenRect(videoRect, m);

        ElementType hit = ElementType.NONE;
        for (int i = layerOrder.size() - 1; i >= 0; i--) {
            String layer = layerOrder.get(i);
            if ("BAND".equals(layer) && bandScreen.contains(p)) {
                hit = ElementType.BAND;
                break;
            } else if ("VIDEO".equals(layer) && videoScreen.contains(p)) {
                hit = ElementType.VIDEO;
                break;
            }
        }

        selectedElement = hit;
        if (hit != ElementType.NONE) {
            isDragging = true;
            isResizing = false;
            activeHandle = -1;
            dragStartMouse = p;
            dragStartRect = new Rectangle(getSelectedRectangle());
        } else {
            isDragging = false;
            isResizing = false;
        }

        repaint();
        if (onLayoutChanged != null) onLayoutChanged.run();
    }

    private void handleMouseReleased(MouseEvent e) {
        isDragging = false;
        isResizing = false;
        activeHandle = -1;
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    private void handleMouseDragged(MouseEvent e) {
        SheetMetrics m = computeSheetMetrics();
        if (m.scale <= 0) return;

        Rectangle sel = getSelectedRectangle();
        if (sel == null) return;

        int dxScreen = e.getX() - dragStartMouse.x;
        int dyScreen = e.getY() - dragStartMouse.y;
        int dxExport = (int) Math.round(dxScreen / m.scale);
        int dyExport = (int) Math.round(dyScreen / m.scale);

        if (isDragging) {
            int newX = dragStartRect.x + dxExport;
            int newY = dragStartRect.y + dyExport;

            // Magnétisme aux bords de la feuille (tolérance 15 px réels)
            int snapDist = (int) Math.round(12 / m.scale);
            if (Math.abs(newX) < snapDist) newX = 0;
            if (Math.abs(newX + sel.width - exportWidth) < snapDist) newX = exportWidth - sel.width;
            if (Math.abs(newY) < snapDist) newY = 0;
            if (Math.abs(newY + sel.height - exportHeight) < snapDist) newY = exportHeight - sel.height;

            // Magnétisme au centre horizontal
            int centerX = (exportWidth - sel.width) / 2;
            if (Math.abs(newX - centerX) < snapDist) newX = centerX;

            // Borner
            newX = Math.max(0, Math.min(exportWidth - sel.width, newX));
            newY = Math.max(0, Math.min(exportHeight - sel.height, newY));

            sel.x = newX;
            sel.y = newY;
            repaint();
            if (onLayoutChanged != null) onLayoutChanged.run();

        } else if (isResizing && activeHandle != -1) {
            int newX = dragStartRect.x;
            int newY = dragStartRect.y;
            int newW = dragStartRect.width;
            int newH = dragStartRect.height;

            switch (activeHandle) {
                case HANDLE_NW -> {
                    newX = Math.min(dragStartRect.x + dxExport, dragStartRect.x + dragStartRect.width - MIN_ELEMENT_SIZE);
                    newY = Math.min(dragStartRect.y + dyExport, dragStartRect.y + dragStartRect.height - MIN_ELEMENT_SIZE);
                    newW = dragStartRect.width - (newX - dragStartRect.x);
                    newH = dragStartRect.height - (newY - dragStartRect.y);
                }
                case HANDLE_N -> {
                    newY = Math.min(dragStartRect.y + dyExport, dragStartRect.y + dragStartRect.height - MIN_ELEMENT_SIZE);
                    newH = dragStartRect.height - (newY - dragStartRect.y);
                }
                case HANDLE_NE -> {
                    newY = Math.min(dragStartRect.y + dyExport, dragStartRect.y + dragStartRect.height - MIN_ELEMENT_SIZE);
                    newW = Math.max(MIN_ELEMENT_SIZE, dragStartRect.width + dxExport);
                    newH = dragStartRect.height - (newY - dragStartRect.y);
                }
                case HANDLE_E -> {
                    newW = Math.max(MIN_ELEMENT_SIZE, dragStartRect.width + dxExport);
                }
                case HANDLE_SE -> {
                    newW = Math.max(MIN_ELEMENT_SIZE, dragStartRect.width + dxExport);
                    newH = Math.max(MIN_ELEMENT_SIZE, dragStartRect.height + dyExport);
                }
                case HANDLE_S -> {
                    newH = Math.max(MIN_ELEMENT_SIZE, dragStartRect.height + dyExport);
                }
                case HANDLE_SW -> {
                    newX = Math.min(dragStartRect.x + dxExport, dragStartRect.x + dragStartRect.width - MIN_ELEMENT_SIZE);
                    newW = dragStartRect.width - (newX - dragStartRect.x);
                    newH = Math.max(MIN_ELEMENT_SIZE, dragStartRect.height + dyExport);
                }
                case HANDLE_W -> {
                    newX = Math.min(dragStartRect.x + dxExport, dragStartRect.x + dragStartRect.width - MIN_ELEMENT_SIZE);
                    newW = dragStartRect.width - (newX - dragStartRect.x);
                }
            }

            // Borner dans la feuille
            if (newX < 0) {
                newW += newX;
                newX = 0;
            }
            if (newY < 0) {
                newH += newY;
                newY = 0;
            }
            if (newX + newW > exportWidth) newW = exportWidth - newX;
            if (newY + newH > exportHeight) newH = exportHeight - newY;

            sel.setBounds(newX, newY, Math.max(MIN_ELEMENT_SIZE, newW), Math.max(MIN_ELEMENT_SIZE, newH));
            repaint();
            if (onLayoutChanged != null) onLayoutChanged.run();
        }
    }

    private void handleMouseMoved(MouseEvent e) {
        if (isDragging || isResizing) return;

        SheetMetrics m = computeSheetMetrics();
        Point p = e.getPoint();

        Rectangle selExport = getSelectedRectangle();
        if (selExport != null) {
            Rectangle selScreen = toScreenRect(selExport, m);
            int handle = getHandleAtPoint(selScreen, p);
            if (handle != -1) {
                setCursor(getCursorForHandle(handle));
                return;
            }
            if (selScreen.contains(p)) {
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                return;
            }
        }

        // Vérifier survol de l'autre élément
        Rectangle bandScreen = toScreenRect(bandRect, m);
        Rectangle videoScreen = toScreenRect(videoRect, m);

        if (bandScreen.contains(p) || videoScreen.contains(p)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private int getHandleAtPoint(Rectangle r, Point p) {
        Rectangle[] handles = computeHandles(r);
        for (int i = 0; i < handles.length; i++) {
            if (handles[i].contains(p)) return i;
        }
        return -1;
    }

    private Rectangle[] computeHandles(Rectangle r) {
        int half = HANDLE_SIZE / 2;
        Rectangle[] h = new Rectangle[8];
        h[HANDLE_NW] = new Rectangle(r.x - half, r.y - half, HANDLE_SIZE, HANDLE_SIZE);
        h[HANDLE_N]  = new Rectangle(r.x + r.width / 2 - half, r.y - half, HANDLE_SIZE, HANDLE_SIZE);
        h[HANDLE_NE] = new Rectangle(r.x + r.width - half, r.y - half, HANDLE_SIZE, HANDLE_SIZE);
        h[HANDLE_E]  = new Rectangle(r.x + r.width - half, r.y + r.height / 2 - half, HANDLE_SIZE, HANDLE_SIZE);
        h[HANDLE_SE] = new Rectangle(r.x + r.width - half, r.y + r.height - half, HANDLE_SIZE, HANDLE_SIZE);
        h[HANDLE_S]  = new Rectangle(r.x + r.width / 2 - half, r.y + r.height - half, HANDLE_SIZE, HANDLE_SIZE);
        h[HANDLE_SW] = new Rectangle(r.x - half, r.y + r.height - half, HANDLE_SIZE, HANDLE_SIZE);
        h[HANDLE_W]  = new Rectangle(r.x - half, r.y + r.height / 2 - half, HANDLE_SIZE, HANDLE_SIZE);
        return h;
    }

    private Cursor getCursorForHandle(int handle) {
        return switch (handle) {
            case HANDLE_NW -> Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
            case HANDLE_N  -> Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
            case HANDLE_NE -> Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
            case HANDLE_E  -> Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
            case HANDLE_SE -> Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
            case HANDLE_S  -> Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
            case HANDLE_SW -> Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
            case HANDLE_W  -> Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
            default -> Cursor.getDefaultCursor();
        };
    }

    // ===== Rendu graphique du Canevas =====

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        SheetMetrics m = computeSheetMetrics();

        // 1. Fond sombre d'atelier avec motif quadrillé discret
        drawArtboardGrid(g2);

        // 2. Ombre portée de la feuille de rendu
        g2.setColor(new Color(0, 0, 0, 110));
        g2.fillRoundRect(m.sheetX + 4, m.sheetY + 4, m.sheetW, m.sheetH, 8, 8);

        // 3. Fond de la feuille de rendu (Canvas final exporté)
        g2.setColor(new Color(10, 10, 12));
        g2.fillRect(m.sheetX, m.sheetY, m.sheetW, m.sheetH);

        Rectangle vScreen = toScreenRect(videoRect, m);
        Rectangle bScreen = toScreenRect(bandRect, m);

        // 4. Dessiner les calques selon l'ordre personnalisé (de l'arrière vers l'avant)
        for (String layer : layerOrder) {
            if ("BACKGROUND_BLUR".equals(layer)) {
                drawBlurredBackgroundElement(g2, m);
            } else if ("VIDEO".equals(layer)) {
                drawVideoElement(g2, vScreen, selectedElement == ElementType.VIDEO);
            } else if ("BAND".equals(layer)) {
                drawBandElement(g2, bScreen, selectedElement == ElementType.BAND);
            }
        }

        // 5. Dessiner les poignées de redimensionnement de l'élément sélectionné
        if (selectedElement == ElementType.VIDEO) {
            drawHandles(g2, vScreen, new Color(56, 189, 248));
        } else if (selectedElement == ElementType.BAND) {
            drawHandles(g2, bScreen, new Color(245, 158, 11));
        }

        // 7. Bordure et étiquette de la feuille de rendu
        g2.setColor(new Color(82, 82, 91));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawRect(m.sheetX, m.sheetY, m.sheetW, m.sheetH);

        drawBadge(g2, m);

        g2.dispose();
    }

    private void drawArtboardGrid(Graphics2D g2) {
        g2.setColor(new Color(39, 39, 42));
        int step = 20;
        for (int x = 0; x < getWidth(); x += step) {
            for (int y = 0; y < getHeight(); y += step) {
                g2.fillRect(x, y, 1, 1);
            }
        }
    }

    private void drawBadge(Graphics2D g2, SheetMetrics m) {
        String ratioStr;
        double ratio = (double) exportWidth / exportHeight;
        if (Math.abs(ratio - 16.0 / 9.0) < 0.05) ratioStr = "16:9 Paysage";
        else if (Math.abs(ratio - 9.0 / 16.0) < 0.05) ratioStr = "9:16 Vertical (TikTok/Reels)";
        else if (Math.abs(ratio - 1.0) < 0.05) ratioStr = "1:1 Carré";
        else ratioStr = String.format("%.2f:1", ratio);

        String text = "Cadre d'exportation : " + exportWidth + " × " + exportHeight + " px (" + ratioStr + ")";
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);

        int bx = m.sheetX;
        int by = Math.max(4, m.sheetY - 18);
        g2.setColor(new Color(24, 24, 27, 220));
        g2.fillRoundRect(bx, by, tw + 12, 16, 4, 4);
        g2.setColor(new Color(212, 212, 216));
        g2.drawString(text, bx + 6, by + 12);
    }

    private void drawBlurredBackgroundElement(Graphics2D g2, SheetMetrics m) {
        if (!blurBackgroundVideo) return;

        Composite oldComp = g2.getComposite();
        float alpha = Math.max(0.05f, Math.min(1.0f, blurOpacity / 100.0f));
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        BufferedImage blurImg = getBlurredSnapshot();
        Shape oldClip = g2.getClip();
        g2.clipRect(m.sheetX, m.sheetY, m.sheetW, m.sheetH);

        if (blurImg != null) {
            // Remplir entièrement la feuille de rendu sans bandes noires (cadrage cover)
            double imgRatio = (double) blurImg.getWidth() / blurImg.getHeight();
            double sheetRatio = (double) m.sheetW / m.sheetH;
            int drawW, drawH, drawX, drawY;
            if (imgRatio > sheetRatio) {
                drawH = m.sheetH;
                drawW = (int) Math.round(drawH * imgRatio);
                drawX = m.sheetX + (m.sheetW - drawW) / 2;
                drawY = m.sheetY;
            } else {
                drawW = m.sheetW;
                drawH = (int) Math.round(drawW / imgRatio);
                drawX = m.sheetX;
                drawY = m.sheetY + (m.sheetH - drawH) / 2;
            }
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(blurImg, drawX, drawY, drawW, drawH, null);
        } else {
            g2.setColor(new Color(20, 25, 45));
            g2.fillRect(m.sheetX, m.sheetY, m.sheetW, m.sheetH);
        }

        g2.setClip(oldClip);
        g2.setComposite(oldComp);
    }

    private void drawVideoElement(Graphics2D g2, Rectangle r, boolean isSelected) {
        boolean drawnSnapshot = false;
        if (videoSnapshot != null && r.width > 20 && r.height > 10) {
            g2.drawImage(videoSnapshot, r.x, r.y, r.width, r.height, null);
            drawnSnapshot = true;
        }

        if (!drawnSnapshot) {
            // Fond de la vidéo
            g2.setColor(new Color(23, 37, 84, 235)); // Bleu nuit moderne
            g2.fillRect(r.x, r.y, r.width, r.height);

            // Motif de caméra / cadrage
            g2.setColor(new Color(30, 58, 138, 160));
            g2.setStroke(new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{4, 4}, 0));
            g2.drawLine(r.x + r.width / 2, r.y + 4, r.x + r.width / 2, r.y + r.height - 4);
            g2.drawLine(r.x + 4, r.y + r.height / 2, r.x + r.width - 4, r.y + r.height / 2);
        }

        // Filtre Anti-Copyright : voile blanc à opacité réglable de 0 à 100%
        if (antiCopyright && antiCopyrightOpacity > 0 && r.width > 0 && r.height > 0) {
            int alpha = (int) Math.round((antiCopyrightOpacity / 100.0) * 255.0);
            alpha = Math.max(0, Math.min(255, alpha));
            g2.setColor(new Color(255, 255, 255, alpha));
            g2.fillRect(r.x, r.y, r.width, r.height);
        }

        // Bordure
        Color borderColor = isSelected ? new Color(56, 189, 248) : new Color(30, 58, 138);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(isSelected ? 2.5f : 1.2f));
        g2.drawRect(r.x, r.y, r.width, r.height);

        // Titre et dimensions
        String title = "🎬 Vidéo source (" + videoRect.width + " × " + videoRect.height + (antiCopyright && antiCopyrightOpacity > 0 ? " — Anti-Copyright " + antiCopyrightOpacity + "%" : "") + ")";
        drawElementHeader(g2, r, title, new Color(56, 189, 248), isSelected);
    }

    private void drawBandElement(Graphics2D g2, Rectangle r, boolean isSelected) {
        boolean drawnRealBand = false;
        if (timelinePanel != null && r.width > 20 && r.height > 10) {
            int bw = Math.max(60, bandRect.width);
            int bh = Math.max(20, bandRect.height);
            if (cachedRealBandImage == null || cachedBandW != bw || cachedBandH != bh || Math.abs(cachedVisibleSeconds - visibleSeconds) > 0.05) {
                cachedBandW = bw;
                cachedBandH = bh;
                cachedVisibleSeconds = visibleSeconds;
                try {
                    cachedRealBandImage = timelinePanel.renderFrame(bw, bh, previewTime, visibleSeconds);
                } catch (Exception ex) {
                    cachedRealBandImage = null;
                }
            }
            if (cachedRealBandImage != null) {
                g2.drawImage(cachedRealBandImage, r.x, r.y, r.width, r.height, null);
                drawnRealBand = true;
            }
        }

        if (!drawnRealBand) {
            // Fond studio bande rythmo
            g2.setColor(new Color(28, 25, 23, 245)); // Gris foncé studio chaud
            g2.fillRect(r.x, r.y, r.width, r.height);

            // Pistes de bande si multiples
            if (bandCount > 1) {
                g2.setColor(new Color(60, 60, 65));
                g2.setStroke(new BasicStroke(1.0f));
                for (int b = 1; b < bandCount; b++) {
                    int by = r.y + (r.height * b) / bandCount;
                    g2.drawLine(r.x, by, r.x + r.width, by);
                }
            }

            // Ligne témoin rouge centrale (curseur de synchronisation rythmo)
            int cx = r.x + r.width / 2;
            g2.setColor(new Color(239, 68, 68, 220));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawLine(cx, r.y + 1, cx, r.y + r.height - 1);
        }

        // Bordure
        Color borderColor = isSelected ? new Color(245, 158, 11) : new Color(180, 83, 9);
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(isSelected ? 2.5f : 1.2f));
        g2.drawRect(r.x, r.y, r.width, r.height);

        // Titre et dimensions
        String title = "🎵 Bande Rythmo (" + bandRect.width + " × " + bandRect.height + " — " + bandCount + " bande" + (bandCount > 1 ? "s" : "") + ")";
        drawElementHeader(g2, r, title, new Color(245, 158, 11), isSelected);
    }

    private void drawElementHeader(Graphics2D g2, Rectangle r, String text, Color accent, boolean isSelected) {
        g2.setFont(new Font("Segoe UI", isSelected ? Font.BOLD : Font.PLAIN, 10));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(text);

        int hx = r.x + 4;
        int hy = r.y + 4;
        g2.setColor(new Color(15, 15, 18, 200));
        g2.fillRoundRect(hx, hy, Math.min(r.width - 8, tw + 8), 15, 3, 3);

        g2.setColor(isSelected ? Color.WHITE : accent);
        Shape oldClip = g2.getClip();
        g2.clipRect(r.x + 2, r.y + 2, Math.max(0, r.width - 4), Math.max(0, r.height - 4));
        g2.drawString(text, hx + 4, hy + 11);
        g2.setClip(oldClip);
    }

    private void drawHandles(Graphics2D g2, Rectangle r, Color handleColor) {
        Rectangle[] handles = computeHandles(r);
        for (Rectangle h : handles) {
            g2.setColor(Color.WHITE);
            g2.fillRect(h.x, h.y, h.width, h.height);
            g2.setColor(handleColor);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(h.x, h.y, h.width, h.height);
        }
    }
}
