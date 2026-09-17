package app.ui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
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

    // Cache pour la police de la timeline (évite les allocations d'objets Font/FontMetrics à chaque frame)
    private int cachedBandHeight = -1;
    private String cachedFontFamily = null;
    private Font cachedTimelineFont = null;
    private FontMetrics cachedFontMetrics = null;
    private int cachedBadgeBandHeight = -1;
    private Font cachedBadgeFont = null;
    private FontMetrics cachedBadgeMetrics = null;

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

    /** Surcharge de compatibilité pour le rendu avec décalage entier. */
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
        render(g, bandSeparators, texts, (double) offsetX, activeBand, isEditing, selectedBand,
                textX, currentInput, editingItem, cursorIndex, cursorRightSide, panelWidth,
                panelHeight, pixelsPerSecond, caretVisible, separatorsVisible, graduationsVisible,
                planMarkers, waveformData);
    }

    /**
     * Rendu haute performance avec décalage subpixel (double offsetX) pour une fluidité
     * 60+ FPS absolue, sans saccades ni micro-sauts de quantification de pixel.
     */
    public void render(Graphics g,
                       Map<Integer, ArrayList<SeparatorMark>> bandSeparators,
                       ArrayList<TextItem> texts,
                       double offsetX,
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

        // Rendu subpixel haute qualité et anticrénelage pur
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                RenderingHints.VALUE_STROKE_PURE);

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
        }
        drawPlanMarkers(g2, planMarkers, offsetX, panelHeight, panelWidth);
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

    private void drawWaveform(Graphics2D g2, app.services.AudioWaveformData waveformData, int panelWidth, int panelHeight, int bandHeight, double offsetX, double pixelsPerSecond, Color waveformColor) {
        if (waveformData == null || waveformData.isEmpty() || pixelsPerSecond <= 0) return;

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

    private void drawGrid(Graphics2D g2, int panelWidth, int panelHeight, int bandHeight, double offsetX, double pixelsPerSecond) {
        g2.setColor(customization.timelineGrid);

        double minorStepPx = Math.max(2.0, pixelsPerSecond * 0.1); // 0.1s
        int majorEvery = 5; // 0.5s

        int firstTickIndex = (int) Math.floor((-offsetX) / minorStepPx) - 1;

        for (int i = firstTickIndex; ; i++) {
            double x = i * minorStepPx + offsetX;
            if (x > panelWidth) break;
            if (x < -minorStepPx) continue;

            boolean major = (i % majorEvery) == 0;
            int tickLen = major ? Math.max(10, bandHeight / 3) : Math.max(5, bandHeight / 6);
            int ix = (int) Math.round(x);

            for (int band = 0; band < bandCount; band++) {
                int top = band * bandHeight;
                int bottom = Math.min(panelHeight, top + bandHeight);
                g2.drawLine(ix, top, ix, Math.min(bottom, top + tickLen));
                g2.drawLine(ix, Math.max(top, bottom - tickLen), ix, bottom);
            }
        }
    }

    private void drawTexts(Graphics2D g2,
                           ArrayList<TextItem> texts,
                           Map<Integer, ArrayList<SeparatorMark>> bandSeparators,
                           int bandHeight,
                           double offsetX,
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
        FontMetrics fm = (cachedFontMetrics != null && cachedTimelineFont == textFont)
                ? cachedFontMetrics
                : g2.getFontMetrics(textFont);
        cachedFontMetrics = fm;
        int textTopY = 1;
        int targetTextHeight = Math.max(8, bandHeight - 2);

        g2.setColor(Color.WHITE);

        // Preview for new text while editing.
        if (activeBand != -1 && isEditing && editingItem == null) {
            double drawX = textX + offsetX;
            int drawY = computeBaselineY(activeBand, bandHeight, textTopY, fm, targetTextHeight);
            String preview = caretVisible ? (currentInput + "|") : currentInput;
            drawScaledText(g2, fm, preview, drawX, drawY, null, targetTextHeight, false);
            g2.setColor(Color.WHITE);
        }

        int firstIdx = Math.max(0, findTextIndex(texts, (int) Math.floor(-offsetX - 500)) - 1);
        for (int ti = firstIdx; ti < texts.size(); ti++) {
            TextItem t = texts.get(ti);
            if (t.text == null || t.text.isEmpty()) continue;

            // Pré-filtrage ultra-rapide côté droit : si le début est déjà loin après l'écran à droite,
            // l'élément n'a pas encore atteint l'affichage et tous les suivants non plus.
            if (t.x + offsetX > panelWidth + 500) {
                break;
            }

            int bandBaselineY = computeBaselineY(t.band, bandHeight, textTopY, fm, targetTextHeight);

            ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
            int leftSep = Integer.MIN_VALUE;
            int rightSep = Integer.MAX_VALUE;
            int nextPhraseStartX = Integer.MAX_VALUE;
            ArrayList<SeparatorMark> innerMarks = new ArrayList<>();

            if (sepList != null && !sepList.isEmpty()) {
                int searchIdx = findSepIndex(sepList, t.x);
                // Recherche vers la gauche du START le plus proche <= t.x
                int maxI = Math.min(searchIdx + 1, sepList.size() - 1);
                for (int i = maxI; i >= 0; i--) {
                    SeparatorMark s = sepList.get(i);
                    if (s.x > t.x) continue;
                    if (s.isStartBoundary()) {
                        leftSep = s.x;
                        break;
                    }
                    if (s.isEndBoundary()) {
                        // Un END situé à gauche ou à t.x appartient à une phrase antérieure :
                        // On ne doit JAMAIS traverser un END vers la gauche pour voler un START précédent !
                        break;
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
                        nextPhraseStartX = s.x;
                        break; // Nouvelle phrase commence à droite : ne jamais traverser un START !
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
                if (nextPhraseStartX != Integer.MAX_VALUE && segmentEnd > nextPhraseStartX - 5) {
                    segmentEnd = Math.max(segmentStart + 10, nextPhraseStartX - 5);
                }
                anchoredRight = false;
            }

            // Viewport Culling Mathématiquement Exact & Optimal (supporte des timelines de 7h+) :
            // Un texte n'est ignoré que si son extrémité droite est complètement sortie à gauche (< -300px),
            // ou si son début n'a pas encore atteint l'écran (> panelWidth + 300px).
            double screenStart = segmentStart + offsetX;
            double screenEnd = segmentEnd + offsetX;
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

            double availableWidth = Math.max(20.0, (double) (segmentEnd - segmentStart));

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
                double labelX;

                if (leftSep != Integer.MIN_VALUE) {
                    labelX = segmentStart + offsetX - lw - 2;
                } else {
                    labelX = segmentStart + offsetX + 2;
                }

                g2.setColor(labelBackground);
                g2.fill(new RoundRectangle2D.Double(labelX, badgeTop, lw, lh, arc, arc));
                g2.setColor(labelTextColor);
                g2.drawString(t.role.name, (float) (labelX + padX), (float) labelY);
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
                double drawX = anchoredRight ? segmentEnd + offsetX : segmentStart + offsetX;
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
                double cursorScreenX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, safeIdx, cursorRightSide);
                if (cursorScreenX >= 0) {
                    int cursorTop = bandBaselineY - (int) Math.round(fm.getAscent() * ((double) targetTextHeight / Math.max(1, fm.getHeight())));
                    int cursorBottom = bandBaselineY + 2;
                    double cursorWidth = 3.0;
                    double drawX = cursorScreenX - cursorWidth / 2.0;
                    g2.setColor(new Color(255, 255, 255, 230));
                    g2.fill(new Rectangle2D.Double(drawX, cursorTop, cursorWidth, cursorBottom - cursorTop));
                    g2.setColor(new Color(0, 0, 0, 140));
                    g2.setStroke(new BasicStroke(1f));
                    g2.draw(new Rectangle2D.Double(drawX, cursorTop, cursorWidth, cursorBottom - cursorTop));
                    g2.setStroke(new BasicStroke(1f));
                    g2.setColor((t.role != null && t.role.color != null) ? t.role.color : Color.WHITE);
                }
            }
        }
    }

    private void drawTextSegment(Graphics2D g2, FontMetrics fm, String text, double segStartX, double segEndX, int baselineY, int targetTextHeight) {
        if (text == null || text.isEmpty()) return;
        double width = segEndX - segStartX;
        if (width <= 3.0) return;
        drawScaledText(g2, fm, text, segStartX, baselineY, width, targetTextHeight, false);
    }

    private Font buildTimelineFont(int bandHeight) {
        String family = customization.timelineFontFamily != null && !customization.timelineFontFamily.isBlank()
                ? customization.timelineFontFamily
                : "Arial";
        if (cachedTimelineFont != null && cachedBandHeight == bandHeight && family.equals(cachedFontFamily)) {
            return cachedTimelineFont;
        }
        int size = Math.max(18, bandHeight);
        cachedTimelineFont = new Font(family, Font.BOLD, size);
        cachedBandHeight = bandHeight;
        cachedFontFamily = family;
        return cachedTimelineFont;
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

    public static int findTextIndex(ArrayList<TextItem> list, int targetX) {
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

    private Font getBadgeFont(int bandHeight) {
        if (cachedBadgeFont != null && cachedBadgeBandHeight == bandHeight) {
            return cachedBadgeFont;
        }
        String family = customization.timelineFontFamily != null && !customization.timelineFontFamily.isBlank()
                ? customization.timelineFontFamily : "Segoe UI";
        int size = Math.max(9, (int)(bandHeight * 0.2f));
        cachedBadgeFont = new Font(family, Font.BOLD, size);
        cachedBadgeBandHeight = bandHeight;
        return cachedBadgeFont;
    }

    private void drawScaledText(Graphics2D g2,
                                FontMetrics fm,
                                String text,
                                double anchorX,
                                int baselineY,
                                Double targetWidth,
                                int targetTextHeight,
                                boolean anchoredRight) {
        if (text == null || text.isEmpty()) return;
        double sourceWidth = fm.stringWidth(text);
        if (sourceWidth <= 0) return;

        double scaleY = (double) targetTextHeight / Math.max(1, fm.getHeight());
        double scaleX = scaleY;
        if (targetWidth != null && targetWidth > 0) {
            scaleX = targetWidth / sourceWidth;
            if (Double.isNaN(scaleX) || Double.isInfinite(scaleX) || scaleX <= 0) {
                scaleX = scaleY;
            } else {
                scaleX = Math.max(0.05, Math.min(scaleX, 15.0));
            }
        }

        AffineTransform old = g2.getTransform();
        g2.translate(anchorX, (double) baselineY);
        g2.scale(scaleX, scaleY);
        float drawX = anchoredRight ? (float) (-sourceWidth) : 0f;
        g2.drawString(text, drawX, 0f);
        g2.setTransform(old);
    }

    private double computeCursorXForSegments(FontMetrics fm,
                                              TextItem t,
                                              double offsetX,
                                              int startWorldX,
                                              int endWorldX,
                                              ArrayList<SeparatorMark> innerMarks,
                                              int cursorIdx,
                                              boolean cursorRightSide) {
        double prevX = startWorldX + offsetX;
        int prevIdx = 0;
        int len = t.text.length();

        for (SeparatorMark mark : innerMarks) {
            double segStart = prevX;
            double segEnd = mark.x + offsetX;
            int idx = mark.splitIndex;
            if (idx < 0) {
                double ratio = (double) (mark.x - startWorldX) / Math.max(1, endWorldX - startWorldX);
                idx = (int) Math.round(ratio * len);
            }
            if (idx < prevIdx) idx = prevIdx;
            if (idx > len) idx = len;

            if (cursorIdx < idx) {
                String segText = t.text.substring(prevIdx, idx);
                double segWidth = segEnd - segStart;
                if (segWidth <= 0) return segStart;
                if (segText.isEmpty()) return segStart;
                double total = fm.stringWidth(segText);
                if (total <= 0) return segStart;
                String before = segText.substring(0, Math.max(0, Math.min(cursorIdx - prevIdx, segText.length())));
                double bw = fm.stringWidth(before);
                return segStart + (bw / total) * segWidth;
            }

            prevX = mark.x + offsetX;
            prevIdx = idx;
        }

        String segText = t.text.substring(prevIdx);
        double segWidth = (endWorldX + offsetX) - prevX;
        if (segWidth <= 0) return prevX;
        if (segText.isEmpty()) return prevX;
        double total = fm.stringWidth(segText);
        if (total <= 0) return prevX;
        int localIdx = Math.max(0, Math.min(cursorIdx - prevIdx, segText.length()));
        double bw = fm.stringWidth(segText.substring(0, localIdx));
        return prevX + (bw / total) * segWidth;
    }

    private void drawSeparators(Graphics2D g2, Map<Integer, ArrayList<SeparatorMark>> bandSeparators, int bandHeight, double offsetX, int panelWidth) {
        float strokeW = Math.max(2f, (float) (bandHeight * 0.035f));
        g2.setStroke(new BasicStroke(strokeW));
        double triSize = Math.max(8.0, bandHeight * 0.15);
        double dotR = Math.max(3.0, bandHeight * 0.06);

        double minWorldX = -offsetX - 70;
        double maxWorldX = panelWidth - offsetX + 70;

        for (int band : bandSeparators.keySet()) {
            ArrayList<SeparatorMark> list = bandSeparators.get(band);
            if (list == null || list.isEmpty()) continue;
            int bandTop = band * bandHeight;
            int bandMid = bandTop + bandHeight / 2;

            int startIdx = Math.max(0, findSepIndex(list, (int) Math.floor(minWorldX)) - 1);

            for (int i = startIdx; i < list.size(); i++) {
                SeparatorMark mark = list.get(i);
                double sx = mark.x + offsetX;
                if (mark.x > maxWorldX) {
                    break; // La liste étant triée, tous les suivants sont hors écran à droite
                }
                if (sx < -70) {
                    continue; // Hors champ visible à gauche
                }
                switch (mark.type) {
                    case START -> {
                        BufferedImage startImg = getStartImage();
                        if (startImg != null) {
                            double imgHeight = Math.max(14.0, bandHeight * 0.40);
                            double aspect = (double) startImg.getWidth() / Math.max(1, startImg.getHeight());
                            double imgWidth = imgHeight * aspect;
                            double imgX = sx - imgWidth / 2.0;
                            double imgY = bandTop + bandHeight - 2;
                            AffineTransform at = AffineTransform.getTranslateInstance(imgX, imgY);
                            at.scale(imgWidth / startImg.getWidth(), imgHeight / startImg.getHeight());
                            g2.drawImage(startImg, at, null);
                        } else {
                            int isx = (int) Math.round(sx);
                            int itriW = (int) Math.round(Math.max(8.0, bandHeight * 0.15));
                            int itriH = (int) Math.round(Math.max(10.0, bandHeight * 0.25));
                            int baseY = bandTop + bandHeight - 2;
                            int[] px = { isx, isx - itriW / 2, isx + itriW / 2 };
                            int[] py = { baseY, baseY + itriH, baseY + itriH };
                            g2.setColor(new Color(80, 220, 120));
                            g2.fillPolygon(px, py, 3);
                        }
                    }
                    case END -> {
                        BufferedImage endImg = getEndImage();
                        if (endImg != null) {
                            double imgHeight = Math.max(14.0, bandHeight * 0.40);
                            double aspect = (double) endImg.getWidth() / Math.max(1, endImg.getHeight());
                            double imgWidth = imgHeight * aspect;
                            double imgX = sx - imgWidth / 2.0;
                            double imgY = bandTop + bandHeight - 2;
                            AffineTransform at = AffineTransform.getTranslateInstance(imgX, imgY);
                            at.scale(imgWidth / endImg.getWidth(), imgHeight / endImg.getHeight());
                            g2.drawImage(endImg, at, null);
                        } else {
                            int isx = (int) Math.round(sx);
                            int itriW = (int) Math.round(Math.max(8.0, bandHeight * 0.15));
                            int itriH = (int) Math.round(Math.max(10.0, bandHeight * 0.25));
                            int baseY = bandTop + bandHeight - 2;
                            int[] px = { isx, isx - itriW / 2, isx + itriW / 2 };
                            int[] py = { baseY, baseY + itriH, baseY + itriH };
                            g2.setColor(new Color(255, 60, 60));
                            g2.fillPolygon(px, py, 3);
                        }
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

                        int isx = (int) Math.round(sx);
                        int idotR = (int) Math.round(dotR);
                        g2.setColor(sepCol);
                        g2.drawLine(isx, bandTop, isx, bandTop + bandHeight);
                        g2.fillOval(isx - idotR, bandMid - idotR, idotR * 2, idotR * 2);
                        g2.setColor(new Color(30, 30, 30));
                        g2.drawOval(isx - idotR, bandMid - idotR, idotR * 2, idotR * 2);

                        if (badge != null) {
                            Font badgeFont = getBadgeFont(bandHeight);
                            FontMetrics bfm = g2.getFontMetrics(badgeFont);
                            int bw = bfm.stringWidth(badge);
                            int bx = isx - bw / 2;
                            int by = bandTop + bfm.getAscent() + 2;
                            g2.setColor(new Color(20, 20, 20, 200));
                            g2.fillRoundRect(bx - 2, by - bfm.getAscent(), bw + 4, bfm.getHeight(), 3, 3);
                            g2.setColor(sepCol);
                            g2.setFont(badgeFont);
                            g2.drawString(badge, bx, by);
                        }
                    }
                    case LEGACY -> {
                        int isx = (int) Math.round(sx);
                        int itriSize = (int) Math.round(triSize);
                        g2.setColor(customization.timelineSeparator);
                        g2.drawLine(isx, bandTop, isx, bandTop + bandHeight);
                        g2.setColor(new Color(255, 200, 50));
                        int[] lpx = { isx - 1, isx - 1 - itriSize, isx - 1 - itriSize };
                        int[] lpy = { bandMid, bandMid - itriSize / 2, bandMid + itriSize / 2 };
                        g2.fillPolygon(lpx, lpy, 3);

                        int[] rpx = { isx + 1, isx + 1 + itriSize, isx + 1 + itriSize };
                        int[] rpy = { bandMid, bandMid - itriSize / 2, bandMid + itriSize / 2 };
                        g2.fillPolygon(rpx, rpy, 3);
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

    private void drawPlanMarkers(Graphics2D g2, ArrayList<Integer> planMarkers, double offsetX, int panelHeight, int panelWidth) {
        if (planMarkers == null || planMarkers.isEmpty()) return;
        float strokeW = Math.max(2f, (float) (panelHeight * 0.006f));
        for (Integer markerX : planMarkers) {
            double sx = markerX + offsetX;
            if (sx < -10 || sx > panelWidth + 10) continue;

            // Ombre contrastée
            g2.setColor(new Color(20, 20, 25, 160));
            g2.setStroke(new BasicStroke(strokeW + 2f));
            g2.draw(new Line2D.Double(sx, 0, sx, panelHeight));

            // Ligne principale de repère de plan (blanc/argenté lumineux)
            g2.setColor(new Color(245, 245, 250, 230));
            g2.setStroke(new BasicStroke(strokeW));
            g2.draw(new Line2D.Double(sx, 0, sx, panelHeight));

            // Repères triangulaires discrets en haut et en bas pour repérage rapide
            double tagSize = Math.max(5.0, strokeW * 2.2);
            Path2D.Double topMarker = new Path2D.Double();
            topMarker.moveTo(sx - tagSize, 0);
            topMarker.lineTo(sx + tagSize, 0);
            topMarker.lineTo(sx, tagSize * 1.5);
            topMarker.closePath();
            g2.setColor(new Color(250, 204, 21, 230)); // Jaune d'or pour plan cut
            g2.fill(topMarker);

            Path2D.Double bottomMarker = new Path2D.Double();
            bottomMarker.moveTo(sx - tagSize, panelHeight);
            bottomMarker.lineTo(sx + tagSize, panelHeight);
            bottomMarker.lineTo(sx, panelHeight - tagSize * 1.5);
            bottomMarker.closePath();
            g2.fill(bottomMarker);
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

    private static BufferedImage startImage;
    private static boolean startImageLoaded = false;

    private static BufferedImage getStartImage() {
        if (startImageLoaded) return startImage;
        try {
            BufferedImage raw = null;
            java.net.URL url = TimelineRenderer.class.getResource("/images/Start.png");
            if (url != null) {
                raw = ImageIO.read(url);
            } else {
                File f = new File("src/images/Start.png");
                if (f.exists()) {
                    raw = ImageIO.read(f);
                } else {
                    f = new File("images/Start.png");
                    if (f.exists()) {
                        raw = ImageIO.read(f);
                    }
                }
            }
            if (raw != null) {
                startImage = makeWhiteTransparent(raw);
            }
        } catch (Exception e) {
            startImage = null;
        }
        startImageLoaded = true;
        return startImage;
    }

    private static BufferedImage endImage;
    private static boolean endImageLoaded = false;

    private static BufferedImage getEndImage() {
        if (endImageLoaded) return endImage;
        try {
            BufferedImage raw = null;
            java.net.URL url = TimelineRenderer.class.getResource("/images/End.png");
            if (url != null) {
                raw = ImageIO.read(url);
            } else {
                File f = new File("src/images/End.png");
                if (f.exists()) {
                    raw = ImageIO.read(f);
                } else {
                    f = new File("images/End.png");
                    if (f.exists()) {
                        raw = ImageIO.read(f);
                    }
                }
            }
            if (raw != null) {
                endImage = makeWhiteTransparent(raw);
            }
        } catch (Exception e) {
            endImage = null;
        }
        endImageLoaded = true;
        return endImage;
    }

    private static BufferedImage makeWhiteTransparent(BufferedImage image) {
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage transparentImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                int red = (rgb >> 16) & 0xFF;
                int green = (rgb >> 8) & 0xFF;
                int blue = rgb & 0xFF;
                // Si le pixel est blanc opaque, on le rend transparent
                if (alpha > 200 && red > 235 && green > 235 && blue > 235) {
                    transparentImage.setRGB(x, y, 0x00000000);
                } else {
                    transparentImage.setRGB(x, y, rgb);
                }
            }
        }
        return transparentImage;
    }
}