package app.ui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TimelineRenderer {

    private int bandCount;
    private int cursorX;
    private AppCustomization customization;
    private String cachedGlobalBandImagePath = "";
    private BufferedImage cachedGlobalBandImage;
    private final Map<String, BufferedImage> bandImageCache = new HashMap<>();

    public TimelineRenderer(int bandCount, int cursorX) {
        this.bandCount = bandCount;
        this.cursorX = cursorX;
        this.customization = new AppCustomization();
    }

    /** Set the number of bands (tracks) the renderer should draw. */
    public void setBandCount(int bandCount) {
        this.bandCount = Math.max(1, bandCount);
    }

    /** Update the cursor X coordinate (in world / screen coordinates) for rendering. */
    public void setCursorX(int cursorX) {
        this.cursorX = cursorX;
    }

    /** Set customization parameters (colors, fonts, images) used for rendering. */
    public void setCustomization(AppCustomization customization) {
        this.customization = customization != null ? customization : new AppCustomization();
    }

    /** Render the timeline into the provided Graphics context. */
    public void render(Graphics g,
                       Map<Integer, ArrayList<SeparatorMark>> bandSeparators,
                       ArrayList<TextItem> texts,
                       int offsetX,
                       int activeBand,
                       boolean isEditing,
                       int selectedBand,
                       int textX,
                       String currentInput,
                       TextItem editingItem,
                       int cursorIndex,
                       boolean cursorRightSide,
                       int panelWidth,
                       int panelHeight,
                       double pixelsPerSecond,
                       boolean caretVisible,
                       boolean separatorsVisible,
                       boolean graduationsVisible,
                       ArrayList<Integer> planMarkers) {

        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int bandHeight = panelHeight / bandCount;

        int editingBand = isEditing ? (editingItem != null ? editingItem.band : activeBand) : -1;
        drawBands(g2, panelWidth, panelHeight, bandHeight, selectedBand, editingBand);
        if (graduationsVisible) {
            drawGrid(g2, panelWidth, panelHeight, bandHeight, offsetX, pixelsPerSecond);
        }
        drawTexts(g2, texts, bandSeparators, bandHeight, offsetX, activeBand, isEditing, textX, currentInput, editingItem, cursorIndex, cursorRightSide, caretVisible, panelWidth);
        if (separatorsVisible) {
            drawSeparators(g2, bandSeparators, bandHeight, offsetX);
            drawPlanMarkers(g2, planMarkers, offsetX, panelHeight);
        }
        drawCursor(g2, panelHeight);
    }

    private void drawBands(Graphics2D g2, int panelWidth, int panelHeight, int bandHeight, int selectedBand, int editingBand) {
        if (AppCustomization.BAND_BG_IMAGE_GLOBAL.equals(customization.bandBackgroundMode)) {
            BufferedImage globalImage = getBandImageForIndex(0);
            if (globalImage != null) {
                // One unique image stretched across the whole band zone.
                g2.drawImage(globalImage, 0, 0, panelWidth, panelHeight, null);
            } else {
                g2.setColor(customization.timelineEvenBand);
                g2.fillRect(0, 0, panelWidth, panelHeight);
            }

            // Keep selected band visible with a translucent overlay.
            if (selectedBand >= 0 && selectedBand < bandCount) {
                int y = selectedBand * bandHeight;
                Color sel = customization.timelineSelectedBand;
                g2.setColor(new Color(sel.getRed(), sel.getGreen(), sel.getBlue(), 90));
                g2.fillRect(0, y, panelWidth, bandHeight);
            }

            if (editingBand >= 0 && editingBand < bandCount) {
                int y = editingBand * bandHeight;
                g2.setColor(new Color(0, 0, 0, 75));
                g2.fillRect(0, y, panelWidth, bandHeight);
            }
            return;
        }

        for (int i = 0; i < bandCount; i++) {
            if (AppCustomization.BAND_BG_COLOR.equals(customization.bandBackgroundMode)) {
                if (i == selectedBand)
                    g2.setColor(customization.timelineSelectedBand);
                else if (i % 2 == 0)
                    g2.setColor(customization.timelineEvenBand);
                else
                    g2.setColor(customization.timelineOddBand);

                g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                if (i == editingBand) {
                    g2.setColor(new Color(0, 0, 0, 75));
                    g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                }
                continue;
            }

            BufferedImage image = getBandImageForIndex(i);
            if (image != null) {
                g2.drawImage(image, 0, i * bandHeight, panelWidth, bandHeight, null);
                if (i == editingBand) {
                    g2.setColor(new Color(0, 0, 0, 75));
                    g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                }
            } else {
                if (i == selectedBand)
                    g2.setColor(customization.timelineSelectedBand);
                else if (i % 2 == 0)
                    g2.setColor(customization.timelineEvenBand);
                else
                    g2.setColor(customization.timelineOddBand);
                g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                if (i == editingBand) {
                    g2.setColor(new Color(0, 0, 0, 75));
                    g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                }
            }
        }
    }

    private void drawGrid(Graphics2D g2, int panelWidth, int panelHeight, int bandHeight, int offsetX, double pixelsPerSecond) {
        g2.setColor(customization.timelineGrid);

        int minorStepPx = Math.max(2, (int) Math.round(pixelsPerSecond * 0.1)); // 0.1s
        int majorEvery = 5; // 0.5s

        int firstTickIndex = (int) Math.floor((-offsetX) / (double) minorStepPx) - 1;

        for (int i = firstTickIndex; ; i++) {
            int x = i * minorStepPx + offsetX;
            if (x > panelWidth) break;
            if (x < -minorStepPx) continue;

            boolean major = (i % majorEvery) == 0;
            int tickLen = major ? Math.max(10, bandHeight / 3) : Math.max(5, bandHeight / 6);

            for (int band = 0; band < bandCount; band++) {
                int top = band * bandHeight;
                int bottom = Math.min(panelHeight, top + bandHeight);
                g2.drawLine(x, top, x, Math.min(bottom, top + tickLen));
                g2.drawLine(x, Math.max(top, bottom - tickLen), x, bottom);
            }
        }
    }

    private void drawTexts(Graphics2D g2,
                           ArrayList<TextItem> texts,
                           Map<Integer, ArrayList<SeparatorMark>> bandSeparators,
                           int bandHeight,
                           int offsetX,
                           int activeBand,
                           boolean isEditing,
                           int textX,
                           String currentInput,
                           TextItem editingItem,
                           int cursorIndex,
                           boolean cursorRightSide,
                           boolean caretVisible,
                           int panelWidth) {

        Font textFont = buildTimelineFont(bandHeight);
        g2.setFont(textFont);
        FontMetrics fm = g2.getFontMetrics(textFont);
        int textTopY = 1;
        int targetTextHeight = Math.max(8, bandHeight - 2);

        g2.setColor(Color.WHITE);

        // Preview for new text while editing.
        if (activeBand != -1 && isEditing && editingItem == null) {
            int drawX = textX + offsetX;
            int drawY = computeBaselineY(activeBand, bandHeight, textTopY, fm, targetTextHeight);
            String preview = caretVisible ? (currentInput + "|") : currentInput;
            drawScaledText(g2, fm, preview, drawX, drawY, null, targetTextHeight, false);
            g2.setColor(Color.WHITE);
        }

        for (TextItem t : texts) {
            int bandBaselineY = computeBaselineY(t.band, bandHeight, textTopY, fm, targetTextHeight);

            int segmentStart = t.x;
            int segmentEnd = t.x + 300;

            ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);

            int leftSep = Integer.MIN_VALUE;
            int rightSep = Integer.MAX_VALUE;

            if (sepList != null) {
                for (SeparatorMark sep : sepList) {
                    if (sep.isStartBoundary() && sep.x <= t.x && sep.x > leftSep) leftSep = sep.x;
                    if (sep.isEndBoundary() && sep.x > t.x && sep.x < rightSep) rightSep = sep.x;
                }
            }

            boolean anchoredRight = false;

            if (leftSep != Integer.MIN_VALUE && rightSep != Integer.MAX_VALUE) {
                segmentStart = leftSep;
                segmentEnd = rightSep;
                anchoredRight = true;
            } else if (rightSep != Integer.MAX_VALUE) {
                segmentEnd = rightSep;
                anchoredRight = true;
            } else if (leftSep != Integer.MIN_VALUE) {
                segmentStart = leftSep;
                segmentEnd = leftSep + 300;
            }

            int availableWidth = segmentEnd - t.x;
            if (availableWidth <= 5) continue;

            // Role label: prefer to place it left of the start boundary for readability.
            if (t.role != null && t.role.name != null && !t.role.name.isEmpty()) {
                Color roleColor = (t.role.color != null) ? t.role.color : Color.WHITE;
                boolean useDarkText = isDarkColor(roleColor);
                Color labelBackground = roleColor;
                Color labelTextColor = useDarkText ? Color.WHITE : Color.BLACK;

                Font oldFont = g2.getFont();
                Font labelFont = oldFont.deriveFont(Font.BOLD, 12f);
                g2.setFont(labelFont);
                FontMetrics lfm = g2.getFontMetrics();
                int lw = lfm.stringWidth(t.role.name) + 8;
                int labelY = t.band * bandHeight + 12;
                int labelX;
                // If we have a left separator (start boundary), always show label to the left of it.
                if (leftSep != Integer.MIN_VALUE) {
                    labelX = segmentStart + offsetX - lw - 4;
                } else {
                    labelX = segmentStart + offsetX + 4;
                }

                g2.setColor(labelBackground);
                g2.fillRoundRect(labelX - 2, labelY - 11, lw, 14, 6, 6);
                g2.setColor(labelTextColor);
                g2.drawString(t.role.name, labelX + 2, labelY);
                g2.setFont(oldFont);
            }

            double textWidth = fm.stringWidth(t.text);
            if (textWidth <= 0) continue;
            Color roleColor = (t.role != null && t.role.color != null) ? t.role.color : Color.WHITE;

            ArrayList<SeparatorMark> innerMarks = new ArrayList<>();
            if (sepList != null && rightSep != Integer.MAX_VALUE) {
                for (SeparatorMark mark : sepList) {
                    if (mark.type == SeparatorMark.Type.INNER && mark.x > segmentStart && mark.x < rightSep) {
                        innerMarks.add(mark);
                    }
                }
            }

            if (innerMarks.isEmpty()) {
                g2.setColor(roleColor);
                int drawX = anchoredRight ? segmentEnd + offsetX : t.x + offsetX;
                drawScaledText(g2, fm, t.text, drawX, bandBaselineY, availableWidth, targetTextHeight, anchoredRight);
            } else {
                int len = t.text.length();
                int prevX = segmentStart;
                int prevIdx = 0;
                g2.setColor(roleColor);

                for (SeparatorMark mark : innerMarks) {
                    int segStart = prevX;
                    int segEnd = mark.x;
                    int idx = mark.splitIndex;
                    if (idx < prevIdx) idx = prevIdx;
                    if (idx > len) idx = len;

                    drawTextSegment(g2, fm, t.text.substring(prevIdx, idx), segStart + offsetX, segEnd + offsetX, bandBaselineY, targetTextHeight);

                    prevX = mark.x;
                    prevIdx = idx;
                }

                drawTextSegment(g2, fm, t.text.substring(prevIdx), prevX + offsetX, segmentEnd + offsetX, bandBaselineY, targetTextHeight);
            }

            // Draw cursor line for the item currently being edited
            if (t == editingItem && isEditing && caretVisible) {
                int safeIdx = Math.max(0, Math.min(cursorIndex, t.text.length()));
                int cursorScreenX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, safeIdx, cursorRightSide);
                if (cursorScreenX >= 0) {
                    int cursorTop = bandBaselineY - (int) Math.round(fm.getAscent() * ((double) targetTextHeight / Math.max(1, fm.getHeight())));
                    int cursorBottom = bandBaselineY + 2;
                    int cursorWidth = 3;
                    int drawX = cursorScreenX - cursorWidth / 2;
                    g2.setColor(new Color(255, 255, 255, 230));
                    g2.fillRect(drawX, cursorTop, cursorWidth, cursorBottom - cursorTop);
                    g2.setColor(new Color(0, 0, 0, 140));
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRect(drawX, cursorTop, cursorWidth - 1, cursorBottom - cursorTop - 1);
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor((t.role != null && t.role.color != null) ? t.role.color : Color.WHITE);
                }
            }
        }
    }

    private void drawTextSegment(Graphics2D g2, FontMetrics fm, String text, int segStartX, int segEndX, int baselineY, int targetTextHeight) {
        if (text == null || text.isEmpty()) return;
        int width = segEndX - segStartX;
        if (width <= 3) return;
        drawScaledText(g2, fm, text, segStartX, baselineY, width, targetTextHeight, false);
    }

    private Font buildTimelineFont(int bandHeight) {
        String family = customization.timelineFontFamily != null && !customization.timelineFontFamily.isBlank()
                ? customization.timelineFontFamily
                : "Arial";
        int size = Math.max(18, bandHeight);
        return new Font(family, Font.BOLD, size);
    }

    private int computeBaselineY(int bandIndex, int bandHeight, int textTopY, FontMetrics fm, int targetTextHeight) {
        int bandTop = bandIndex * bandHeight;
        double scaleY = (double) targetTextHeight / Math.max(1, fm.getHeight());
        return bandTop + textTopY + (int) Math.round(fm.getAscent() * scaleY);
    }

    private boolean isDarkColor(Color color) {
        if (color == null) return false;
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        return luminance < 0.55;
    }

    private void drawScaledText(Graphics2D g2,
                                FontMetrics fm,
                                String text,
                                int anchorX,
                                int baselineY,
                                Integer targetWidth,
                                int targetTextHeight,
                                boolean anchoredRight) {
        if (text == null || text.isEmpty()) return;
        double sourceWidth = fm.stringWidth(text);
        if (sourceWidth <= 0) return;

        double scaleY = (double) targetTextHeight / Math.max(1, fm.getHeight());
        // Keep text attached to segment width when a target width is provided.
        double scaleX = scaleY;
        if (targetWidth != null && targetWidth > 0) {
            scaleX = (double) targetWidth / sourceWidth;
        }

        AffineTransform old = g2.getTransform();
        g2.translate(anchorX, baselineY);
        g2.scale(scaleX, scaleY);
        int drawX = anchoredRight ? (int) Math.round(-sourceWidth) : 0;
        g2.drawString(text, drawX, 0);
        g2.setTransform(old);
    }

    private int computeCursorXForSegments(FontMetrics fm,
                                          TextItem t,
                                          int offsetX,
                                          int startWorldX,
                                          int endWorldX,
                                          ArrayList<SeparatorMark> innerMarks,
                                          int cursorIdx,
                                          boolean cursorRightSide) {
        int prevX = startWorldX + offsetX;
        int prevIdx = 0;
        int len = t.text.length();

        for (SeparatorMark mark : innerMarks) {
            int segStart = prevX;
            int segEnd = mark.x + offsetX;
            int idx = mark.splitIndex;
            if (idx < prevIdx) idx = prevIdx;
            if (idx > len) idx = len;

            if (cursorIdx < idx) {
                String segText = t.text.substring(prevIdx, idx);
                int segWidth = segEnd - segStart;
                if (segWidth <= 0) return segStart;
                if (segText.isEmpty()) return segStart;
                double total = fm.stringWidth(segText);
                if (total <= 0) return segStart;
                String before = segText.substring(0, Math.max(0, Math.min(cursorIdx - prevIdx, segText.length())));
                double bw = fm.stringWidth(before);
                return (int) (segStart + (bw / total) * segWidth);
            }

            prevX = mark.x + offsetX;
            prevIdx = idx;
        }

        String segText = t.text.substring(prevIdx);
        int segWidth = (endWorldX + offsetX) - prevX;
        if (segWidth <= 0) return prevX;
        if (segText.isEmpty()) return prevX;
        double total = fm.stringWidth(segText);
        if (total <= 0) return prevX;
        int localIdx = Math.max(0, Math.min(cursorIdx - prevIdx, segText.length()));
        double bw = fm.stringWidth(segText.substring(0, localIdx));
        return (int) (prevX + (bw / total) * segWidth);
    }

    private void drawSeparators(Graphics2D g2, Map<Integer, ArrayList<SeparatorMark>> bandSeparators, int bandHeight, int offsetX) {
        g2.setStroke(new BasicStroke(2));
        for (int band : bandSeparators.keySet()) {
            ArrayList<SeparatorMark> list = bandSeparators.get(band);
            int bandTop = band * bandHeight;
            int bandMid = bandTop + bandHeight / 2;
            int triSize = 8;
            for (SeparatorMark mark : list) {
                int sx = mark.x + offsetX;
                switch (mark.type) {
                    case START -> {
                        g2.setColor(new Color(80, 220, 120));
                        g2.drawLine(sx, bandTop, sx, bandTop + bandHeight);
                        int[] xRight = { sx + 1, sx + 1 + triSize, sx + 1 + triSize };
                        int[] yRight = { bandMid, bandMid - triSize / 2, bandMid + triSize / 2 };
                        g2.fillPolygon(xRight, yRight, 3);
                    }
                    case END -> {
                        g2.setColor(new Color(255, 120, 90));
                        g2.drawLine(sx, bandTop, sx, bandTop + bandHeight);
                        int[] xLeft = { sx - 1, sx - 1 - triSize, sx - 1 - triSize };
                        int[] yLeft = { bandMid, bandMid - triSize / 2, bandMid + triSize / 2 };
                        g2.fillPolygon(xLeft, yLeft, 3);
                    }
                    case INNER -> {
                        g2.setColor(customization.timelineSeparator);
                        g2.drawLine(sx, bandTop, sx, bandTop + bandHeight);
                        g2.fillOval(sx - 3, bandMid - 3, 6, 6);
                        g2.setColor(new Color(30, 30, 30));
                        g2.drawOval(sx - 3, bandMid - 3, 6, 6);
                    }
                    case LEGACY -> {
                        g2.setColor(customization.timelineSeparator);
                        g2.drawLine(sx, bandTop, sx, bandTop + bandHeight);
                        g2.setColor(new Color(255, 200, 50));
                        int[] xLeft = { sx - 1, sx - 1 - triSize, sx - 1 - triSize };
                        int[] yLeft = { bandMid, bandMid - triSize / 2, bandMid + triSize / 2 };
                        g2.fillPolygon(xLeft, yLeft, 3);
                        int[] xRight = { sx + 1, sx + 1 + triSize, sx + 1 + triSize };
                        int[] yRight = { bandMid, bandMid - triSize / 2, bandMid + triSize / 2 };
                        g2.fillPolygon(xRight, yRight, 3);
                    }
                }
            }
        }
        g2.setStroke(new BasicStroke(1));
    }

    private void drawCursor(Graphics2D g2, int panelHeight) {
        g2.setColor(customization.timelineCursor);
        g2.setStroke(new BasicStroke(2));
        g2.drawLine(cursorX, 0, cursorX, panelHeight);
    }

    private void drawPlanMarkers(Graphics2D g2, ArrayList<Integer> planMarkers, int offsetX, int panelHeight) {
        if (planMarkers == null || planMarkers.isEmpty()) return;
        g2.setColor(new Color(220, 220, 220, 120));
        g2.setStroke(new BasicStroke(2));
        for (Integer markerX : planMarkers) {
            int sx = markerX + offsetX;
            g2.drawLine(sx, 0, sx, panelHeight);
        }
        g2.setStroke(new BasicStroke(1));
    }

    private BufferedImage getBandImageForIndex(int bandIndex) {
        if (AppCustomization.BAND_BG_IMAGE_GLOBAL.equals(customization.bandBackgroundMode)) {
            String path = customization.globalBandImagePath != null ? customization.globalBandImagePath.trim() : "";
            if (path.isEmpty()) {
                cachedGlobalBandImagePath = "";
                cachedGlobalBandImage = null;
                return null;
            }

            if (path.equals(cachedGlobalBandImagePath) && cachedGlobalBandImage != null) {
                return cachedGlobalBandImage;
            }

            try {
                cachedGlobalBandImage = ImageIO.read(new File(path));
                cachedGlobalBandImagePath = path;
                return cachedGlobalBandImage;
            } catch (Exception e) {
                cachedGlobalBandImagePath = path;
                cachedGlobalBandImage = null;
                return null;
            }
        }

        if (AppCustomization.BAND_BG_IMAGE_PER_BAND.equals(customization.bandBackgroundMode)) {
            String[] parts = (customization.perBandImagePaths == null ? "" : customization.perBandImagePaths)
                    .split(";");
            if (bandIndex < 0 || bandIndex >= parts.length) return null;
            String path = parts[bandIndex].trim();
            if (path.isEmpty()) return null;

            BufferedImage cached = bandImageCache.get(path);
            if (cached != null) return cached;
            try {
                BufferedImage img = ImageIO.read(new File(path));
                if (img != null) {
                    bandImageCache.put(path, img);
                }
                return img;
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }
}