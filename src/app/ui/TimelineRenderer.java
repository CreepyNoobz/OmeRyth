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

    public int getCursorX() {
        return cursorX;
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
                       ArrayList<Integer> planMarkers,
                       app.services.AudioWaveformData waveformData) {

        Graphics2D g2 = (Graphics2D) g;

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int bandHeight = panelHeight / bandCount;

        int editingBand = isEditing ? (editingItem != null ? editingItem.band : activeBand) : -1;
        drawBands(g2, panelWidth, panelHeight, bandHeight, selectedBand, editingBand);
        
        if (customization.showWaveform) {
            drawWaveform(g2, waveformData, panelWidth, panelHeight, bandHeight, offsetX, pixelsPerSecond, customization.waveformColor);
        }
        
        if (graduationsVisible) {
            drawGrid(g2, panelWidth, panelHeight, bandHeight, offsetX, pixelsPerSecond);
        }
        drawTexts(g2, texts, bandSeparators, bandHeight, offsetX, activeBand, isEditing, textX, currentInput, editingItem, cursorIndex, cursorRightSide, caretVisible, panelWidth);
        if (separatorsVisible) {
            drawSeparators(g2, bandSeparators, bandHeight, offsetX, panelWidth);
            drawPlanMarkers(g2, planMarkers, offsetX, panelHeight, panelWidth);
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

    private void drawWaveform(Graphics2D g2, app.services.AudioWaveformData waveformData, int panelWidth, int panelHeight, int bandHeight, int offsetX, double pixelsPerSecond, Color waveformColor) {
        if (waveformData == null || waveformData.isEmpty()) return;

        g2.setColor(waveformColor);
        int totalHeight = Math.max(bandHeight, Math.min(panelHeight, bandCount * bandHeight));
        int centerY = totalHeight / 2;

        for (int screenX = 0; screenX < panelWidth; screenX++) {
            double timeSeconds = (screenX - offsetX) / pixelsPerSecond;
            if (timeSeconds < 0 || timeSeconds > waveformData.getDurationSeconds()) continue;

            float amplitude = waveformData.getAmplitudeAt(timeSeconds);
            if (amplitude > 0) {
                int barHeight = (int) (amplitude * (totalHeight / 2.0) * 0.92);
                if (barHeight < 1) barHeight = 1;

                g2.drawLine(screenX, centerY - barHeight, screenX, centerY + barHeight);
            }
        }
    }

    private void drawGrid(Graphics2D g2, int panelWidth, int panelHeight, int bandHeight, int offsetX, double pixelsPerSecond) {
        g2.setColor(customization.timelineGrid);

        double minorStepPx = Math.max(2.0, pixelsPerSecond * 0.1); // 0.1s
        int majorEvery = 5; // 0.5s

        int firstTickIndex = (int) Math.floor((-offsetX) / minorStepPx) - 1;

        for (int i = firstTickIndex; ; i++) {
            int x = (int) Math.round(i * minorStepPx) + offsetX;
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
            if (t.text == null || t.text.isEmpty()) continue;

            // Pré-filtrage ultra-rapide côté droit : si le début est déjà loin après l'écran à droite,
            // l'élément n'a pas encore atteint l'affichage.
            if (t.x + offsetX > panelWidth + 500) {
                continue;
            }

            int bandBaselineY = computeBaselineY(t.band, bandHeight, textTopY, fm, targetTextHeight);

            ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
            int leftSep = Integer.MIN_VALUE;
            int rightSep = Integer.MAX_VALUE;
            ArrayList<SeparatorMark> innerMarks = new ArrayList<>();

            if (sepList != null && !sepList.isEmpty()) {
                int searchIdx = findSepIndex(sepList, t.x);
                // Recherche vers la gauche du START le plus proche <= t.x
                for (int i = Math.min(searchIdx, sepList.size() - 1); i >= 0; i--) {
                    SeparatorMark s = sepList.get(i);
                    if (s.x > t.x) continue;
                    if (s.isStartBoundary()) {
                        leftSep = s.x;
                        break;
                    }
                    if (s.isEndBoundary() && s.x < t.x) {
                        break; // On a dépassé la phrase précédente vers la gauche
                    }
                }
                // Recherche vers la droite du END le plus proche >= t.x
                for (int i = Math.max(0, searchIdx - 1); i < sepList.size(); i++) {
                    SeparatorMark s = sepList.get(i);
                    if (s.x < t.x) continue;
                    if (s.isEndBoundary()) {
                        rightSep = s.x;
                        break;
                    }
                    if (s.isStartBoundary() && s.x > t.x) {
                        break; // Nouvelle phrase commence à droite
                    }
                }
            }

            int segmentStart = (leftSep != Integer.MIN_VALUE) ? leftSep : t.x;
            int segmentEnd;
            boolean anchoredRight;

            double textWidth = fm.stringWidth(t.text);
            int naturalWidth = Math.max(100, (int) Math.round(textWidth * 1.25));

            if (rightSep != Integer.MAX_VALUE) {
                segmentEnd = Math.max(segmentStart + 10, rightSep);
                anchoredRight = true;
            } else {
                segmentEnd = segmentStart + naturalWidth;
                anchoredRight = false;
            }

            // Viewport Culling Mathématiquement Exact & Optimal (supporte des timelines de 7h+) :
            // Un texte n'est ignoré que si son extrémité droite est complètement sortie à gauche (< -300px),
            // ou si son début n'a pas encore atteint l'écran (> panelWidth + 300px).
            int screenStart = segmentStart + offsetX;
            int screenEnd = segmentEnd + offsetX;
            if (screenEnd < -300 || screenStart > panelWidth + 300) {
                continue;
            }

            // Collecte des marques internes INNER situées strictement entre segmentStart et segmentEnd
            if (sepList != null && !sepList.isEmpty()) {
                int startI = findSepIndex(sepList, segmentStart + 1);
                for (int i = startI; i < sepList.size(); i++) {
                    SeparatorMark m = sepList.get(i);
                    if (m.x >= segmentEnd) break;
                    if (m.type == SeparatorMark.Type.INNER && m.x > segmentStart) {
                        innerMarks.add(m);
                    }
                }
            }

            int availableWidth = Math.max(20, segmentEnd - segmentStart);

            // Role label: badge du personnage affiché proprement avant le séparateur de début
            if (t.role != null && t.role.name != null && !t.role.name.isEmpty()) {
                Color roleColor = (t.role.color != null) ? t.role.color : Color.WHITE;
                boolean useDarkText = isDarkColor(roleColor);
                Color labelBackground = roleColor;
                Color labelTextColor = useDarkText ? Color.WHITE : Color.BLACK;

                Font oldFont = g2.getFont();
                float labelFontSize = Math.max(9.5f, Math.min(13.5f, (float) (bandHeight * 0.16f)));
                Font labelFont = oldFont.deriveFont(Font.BOLD, labelFontSize);
                g2.setFont(labelFont);
                FontMetrics lfm = g2.getFontMetrics();

                int padX = Math.max(3, Math.round(labelFontSize * 0.35f));
                int padY = Math.max(1, Math.round(labelFontSize * 0.12f));
                int lw = lfm.stringWidth(t.role.name) + padX * 2;
                int lh = lfm.getHeight() + padY;
                int arc = Math.max(3, Math.round(lh * 0.35f));

                int badgeTop = t.band * bandHeight + 2;
                int labelY = badgeTop + padY + lfm.getAscent() - 1;
                int labelX;

                if (leftSep != Integer.MIN_VALUE) {
                    labelX = segmentStart + offsetX - lw - 2;
                } else {
                    labelX = segmentStart + offsetX + 2;
                }

                g2.setColor(labelBackground);
                g2.fillRoundRect(labelX, badgeTop, lw, lh, arc, arc);
                g2.setColor(labelTextColor);
                g2.drawString(t.role.name, labelX + padX, labelY);
                g2.setFont(oldFont);
            }

            if (textWidth <= 0) continue;
            Color roleColor = (t.role != null && t.role.color != null) ? t.role.color : Color.WHITE;
            double luminance = 0.299 * roleColor.getRed() + 0.587 * roleColor.getGreen() + 0.114 * roleColor.getBlue();
            if (luminance < 45) {
                roleColor = new Color(225, 225, 225);
            }

            if (innerMarks.isEmpty()) {
                g2.setColor(roleColor);
                int drawX = anchoredRight ? segmentEnd + offsetX : segmentStart + offsetX;
                drawScaledText(g2, fm, t.text, drawX, bandBaselineY, availableWidth, targetTextHeight, anchoredRight);
            } else {
                int len = t.text.length();
                int prevX = segmentStart;
                int prevIdx = 0;
                g2.setColor(roleColor);

                for (int mi = 0; mi < innerMarks.size(); mi++) {
                    SeparatorMark mark = innerMarks.get(mi);
                    int segStart = prevX;
                    int segEnd = mark.x;
                    int idx = mark.splitIndex;
                    if (idx < 0) {
                        // Réparation automatique de l'index de découpe si non défini
                        double ratio = (double) (mark.x - segmentStart) / Math.max(1, segmentEnd - segmentStart);
                        idx = (int) Math.round(ratio * len);
                    }
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

    public static int findSepIndex(ArrayList<SeparatorMark> list, int targetX) {
        if (list == null || list.isEmpty()) return 0;
        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = list.get(mid).x;
            if (midVal < targetX) {
                low = mid + 1;
            } else if (midVal > targetX) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return low;
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
        double scaleX = scaleY;
        if (targetWidth != null && targetWidth > 0) {
            scaleX = (double) targetWidth / sourceWidth;
            if (Double.isNaN(scaleX) || Double.isInfinite(scaleX) || scaleX <= 0) {
                scaleX = scaleY;
            } else {
                scaleX = Math.max(0.05, Math.min(scaleX, 15.0));
            }
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
            if (idx < 0) {
                double ratio = (double) (mark.x - startWorldX) / Math.max(1, endWorldX - startWorldX);
                idx = (int) Math.round(ratio * len);
            }
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

    private void drawSeparators(Graphics2D g2, Map<Integer, ArrayList<SeparatorMark>> bandSeparators, int bandHeight, int offsetX, int panelWidth) {
        float strokeW = Math.max(2f, (float) (bandHeight * 0.035f));
        g2.setStroke(new BasicStroke(strokeW));
        int triSize = Math.max(8, (int) Math.round(bandHeight * 0.15f));
        int dotR = Math.max(3, (int) Math.round(bandHeight * 0.06f));

        int minWorldX = -offsetX - 70;
        int maxWorldX = panelWidth - offsetX + 70;

        for (int band : bandSeparators.keySet()) {
            ArrayList<SeparatorMark> list = bandSeparators.get(band);
            if (list == null || list.isEmpty()) continue;
            int bandTop = band * bandHeight;
            int bandMid = bandTop + bandHeight / 2;

            int startIdx = Math.max(0, findSepIndex(list, minWorldX) - 1);

            for (int i = startIdx; i < list.size(); i++) {
                SeparatorMark mark = list.get(i);
                int sx = mark.x + offsetX;
                if (mark.x > maxWorldX) {
                    break; // La liste étant triée, tous les suivants sont hors écran à droite
                }
                if (sx < -70) {
                    continue; // Hors champ visible à gauche
                }
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
                        Color sepCol = customization.timelineSeparator;
                        String badge = null;
                        if (mark.signType == SeparatorMark.SignType.FVR) {
                            sepCol = new Color(0, 190, 255); // Cyan pour FVR
                            badge = "F";
                        } else if (mark.signType == SeparatorMark.SignType.OPEN_A) {
                            sepCol = new Color(255, 190, 0); // Jaune / Or pour voyelle ouverte A
                            badge = "A";
                        } else if (mark.signType == SeparatorMark.SignType.NEUTRAL) {
                            sepCol = new Color(180, 150, 230); // Mauve pour consonne neutre
                            badge = "N";
                        } else if (mark.signType == SeparatorMark.SignType.MPB) {
                            sepCol = new Color(255, 90, 150); // Rose / Magenta pour labiale MPB
                            badge = "M";
                        } else if (mark.signType == SeparatorMark.SignType.RESPIRATION) {
                            sepCol = new Color(70, 210, 200); // Turquoise pour respiration
                            badge = "h/";
                        }

                        g2.setColor(sepCol);
                        g2.drawLine(sx, bandTop, sx, bandTop + bandHeight);
                        g2.fillOval(sx - dotR, bandMid - dotR, dotR * 2, dotR * 2);
                        g2.setColor(new Color(30, 30, 30));
                        g2.drawOval(sx - dotR, bandMid - dotR, dotR * 2, dotR * 2);

                        if (badge != null) {
                            Font origFont = g2.getFont();
                            Font badgeFont = new Font(origFont.getName(), Font.BOLD, Math.max(9, (int)(bandHeight * 0.2f)));
                            g2.setFont(badgeFont);
                            FontMetrics bfm = g2.getFontMetrics();
                            int bw = bfm.stringWidth(badge);
                            int bx = sx - bw / 2;
                            int by = bandTop + bfm.getAscent() + 2;
                            g2.setColor(new Color(20, 20, 20, 200));
                            g2.fillRoundRect(bx - 2, by - bfm.getAscent(), bw + 4, bfm.getHeight(), 3, 3);
                            g2.setColor(sepCol);
                            g2.drawString(badge, bx, by);
                            g2.setFont(origFont);
                        }
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
        float strokeW = Math.max(2f, (float) (panelHeight * 0.006f));
        g2.setStroke(new BasicStroke(strokeW));
        g2.drawLine(cursorX, 0, cursorX, panelHeight);
        g2.setStroke(new BasicStroke(1));
    }

    private void drawPlanMarkers(Graphics2D g2, ArrayList<Integer> planMarkers, int offsetX, int panelHeight, int panelWidth) {
        if (planMarkers == null || planMarkers.isEmpty()) return;
        g2.setColor(new Color(220, 220, 220, 120));
        float strokeW = Math.max(2f, (float) (panelHeight * 0.006f));
        g2.setStroke(new BasicStroke(strokeW));
        for (Integer markerX : planMarkers) {
            int sx = markerX + offsetX;
            if (sx < -10 || sx > panelWidth + 10) continue;
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