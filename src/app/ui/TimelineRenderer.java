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
import java.util.List;
import java.util.Map;

/**
 * Moteur de rendu graphique de la bande rythmo (Timeline).
 * 
 * Cette classe est responsable de l'affichage en temps réel (60+ images par seconde) :
 * - Des pistes horizontales (bandes de doublage pour chaque comédien ou personnage)
 * - De la forme d'onde audio (waveform) en arrière-plan
 * - Des graduations temporelles (dixièmes de seconde et secondes entières)
 * - Des répliques textuelles déformées et étirées élastiquement pour coller au rythme labial
 * - Des repères et séparateurs de synchro (START, END, INNER, FVR, MPB, etc.)
 * - Des marqueurs de changement de plan vidéo
 * - Du curseur rouge de lecture (barre de synchro verticale)
 */
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

    /** Définit le nombre de bandes (pistes de comédiens) à dessiner. */
    public void setBandCount(int bandCount) {
        this.bandCount = Math.max(1, bandCount);
    }

    /** Met à jour la position X du curseur rouge (barre de lecture fixe ou mobile). */
    public void setCursorX(int cursorX) {
        this.cursorX = cursorX;
    }

    public int getCursorX() {
        return cursorX;
    }

    private long lastTypingTimestamp = 0L;

    public void setLastTypingTimestamp(long lastTypingTimestamp) {
        this.lastTypingTimestamp = lastTypingTimestamp;
    }

    private int selectionStart = -1;
    private int selectionEnd = -1;

    public void setSelection(int start, int end) {
        this.selectionStart = start;
        this.selectionEnd = end;
    }

    private int activeSegmentEndMarkX = Integer.MAX_VALUE;

    public void setActiveSegmentEndMarkX(int activeSegmentEndMarkX) {
        this.activeSegmentEndMarkX = activeSegmentEndMarkX;
    }

    // Champs de mise en cache pour fluidité maximale (60+ FPS) sans Garbage Collection
    private Font cachedLabelFont = null;
    private FontMetrics cachedLabelFontMetrics = null;
    private int cachedLabelBandHeight = -1;
    private float cachedLabelFontSize = -1.0f;
    private final ArrayList<SeparatorMark> scratchInnerMarks = new ArrayList<>();
    private int[] cachedPhraseStarts = new int[64];
    private int[] cachedPhraseEnds = new int[64];

    /** Opacité appliquée lorsqu'une étiquette de rôle empiète sur une phrase (45%). */
    public static final float OVERLAPPING_ROLE_BADGE_ALPHA = 0.45f;

    private double roleLabelScale = 1.0;

    /**
     * Définit le facteur d'échelle des étiquettes de rôles (de 0.10 soit 10% à 2.00 soit 200%).
     */
    public void setRoleLabelScale(double roleLabelScale) {
        double clamped = Math.max(0.10, Math.min(2.00, roleLabelScale));
        if (Math.abs(this.roleLabelScale - clamped) > 0.0001) {
            this.roleLabelScale = clamped;
            this.cachedLabelFont = null;
            this.cachedLabelFontMetrics = null;
            this.cachedLabelFontSize = -1.0f;
        }
    }

    public double getRoleLabelScale() {
        return roleLabelScale;
    }

    /**
     * Vérifie si l'étiquette de rôle (définie par ses bornes horizontales monde [badgeWorldLeft, badgeWorldRight]
     * sur la piste {@code band}) empiète / surpasse sur une phrase de la même piste.
     */
    public static boolean doesRoleBadgeOverlapAnyPhrase(double badgeWorldLeft,
                                                        double badgeWorldRight,
                                                        int band,
                                                        int selfIndex,
                                                        ArrayList<TextItem> texts,
                                                        int[] phraseStarts,
                                                        int[] phraseEnds) {
        if (texts == null || phraseStarts == null || phraseEnds == null) return false;
        int n = Math.min(texts.size(), Math.min(phraseStarts.length, phraseEnds.length));
        for (int i = 0; i < n; i++) {
            if (i == selfIndex) continue;
            TextItem other = texts.get(i);
            if (other == null || other.band != band) continue;
            int pStart = Math.min(other.x, phraseStarts[i]);
            int pEnd = phraseEnds[i];
            if (pEnd > pStart && badgeWorldLeft < pEnd && badgeWorldRight > pStart) {
                return true;
            }
        }
        return false;
    }

    private boolean exportMode = false;

    /** Active le mode d'exportation vidéo haute performance (supprime la vérification orthographique et optimise la vitesse de rendu). */
    public void setExportMode(boolean exportMode) {
        this.exportMode = exportMode;
    }

    public boolean isExportMode() {
        return exportMode;
    }

    /** Applique les paramètres de personnalisation visuelle (thème sombre, couleurs, polices). */
    public void setCustomization(AppCustomization customization) {
        this.customization = customization != null ? customization : new AppCustomization();
        this.cachedTimelineFont = null;
        this.cachedFontMetrics = null;
        this.cachedLabelFont = null;
        this.cachedLabelFontMetrics = null;
        this.cachedBadgeFont = null;
        this.cachedBadgeMetrics = null;
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

        if (exportMode) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_SPEED);
            g2.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING,
                    RenderingHints.VALUE_COLOR_RENDER_SPEED);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        } else {
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

            app.services.SpellGrammarService.getInstance().clearVisibleIssues();
        }

        int bandHeight = panelHeight / bandCount;

        int editingBand = isEditing ? (editingItem != null ? editingItem.band : activeBand) : -1;
        drawBands(g2, panelWidth, panelHeight, bandHeight, selectedBand, editingBand);
        
        if (customization.showWaveform) {
            drawWaveform(g2, waveformData, panelWidth, panelHeight, bandHeight, offsetX, pixelsPerSecond, customization.waveformColor);
        }
        
        if (graduationsVisible) {
            drawGrid(g2, panelWidth, panelHeight, bandHeight, offsetX, pixelsPerSecond);
        }
        drawTexts(g2, texts, bandSeparators, bandHeight, offsetX, activeBand, isEditing, textX, currentInput, editingItem, cursorIndex, cursorRightSide, caretVisible, separatorsVisible, panelWidth);
        if (separatorsVisible) {
            drawSeparators(g2, bandSeparators, texts, bandHeight, offsetX, panelWidth);
        }
        if (isEditing && editingItem != null && caretVisible) {
            drawEditingCaret(g2, editingItem, bandSeparators, bandHeight, offsetX, cursorIndex, cursorRightSide, panelWidth);
        }
        drawPlanMarkers(g2, planMarkers, offsetX, panelHeight, panelWidth);
        drawCursor(g2, panelHeight);
    }

    private Color darkenColor(Color color) {
        if (color == null) {
            return new Color(25, 25, 25);
        }
        Color darker = color.darker();
        if (darker.equals(color)) {
            return new Color(Math.max(0, color.getRed() - 15),
                             Math.max(0, color.getGreen() - 15),
                             Math.max(0, color.getBlue() - 15),
                             color.getAlpha());
        }
        return darker;
    }

    private void drawBands(Graphics2D g2, int panelWidth, int panelHeight, int bandHeight, int selectedBand, int editingBand) {
        if (AppCustomization.BAND_BG_IMAGE_GLOBAL.equals(customization.bandBackgroundMode)) {
            BufferedImage globalImage = getBandImageForIndex(0);
            if (globalImage != null) {
                // Une texture unique étirée sur toute la hauteur de la zone des bandes
                g2.drawImage(globalImage, 0, 0, panelWidth, panelHeight, null);
            } else {
                g2.setColor(customization.timelineEvenBand);
                g2.fillRect(0, 0, panelWidth, panelHeight);
            }

            // Bande actuellement sélectionnée : mise en valeur par un assombrissement
            if (selectedBand >= 0 && selectedBand < bandCount) {
                int y = selectedBand * bandHeight;
                g2.setColor(new Color(0, 0, 0, 60));
                g2.fillRect(0, y, panelWidth, bandHeight);
            }

            // Bande en cours d'édition directe de texte : voile sombre d'accentuation
            if (editingBand >= 0 && editingBand < bandCount) {
                int y = editingBand * bandHeight;
                g2.setColor(new Color(0, 0, 0, 75));
                g2.fillRect(0, y, panelWidth, bandHeight);
            }
            return;
        }

        for (int i = 0; i < bandCount; i++) {
            Color baseBandColor = (i % 2 == 0) ? customization.timelineEvenBand : customization.timelineOddBand;

            if (AppCustomization.BAND_BG_COLOR.equals(customization.bandBackgroundMode)) {
                if (i == selectedBand)
                    g2.setColor(customization.timelineSelectedBand != null ? customization.timelineSelectedBand : darkenColor(baseBandColor));
                else
                    g2.setColor(baseBandColor);

                g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                if (i == editingBand) {
                    g2.setColor(new Color(47, 190, 85, 24));
                    g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                }
                if (i > 0) {
                    g2.setColor(new Color(255, 255, 255, 16));
                    g2.drawLine(0, i * bandHeight, panelWidth, i * bandHeight);
                }
                continue;
            }

            BufferedImage image = getBandImageForIndex(i);
            if (image != null) {
                g2.drawImage(image, 0, i * bandHeight, panelWidth, bandHeight, null);
                if (i == selectedBand) {
                    g2.setColor(new Color(0, 0, 0, 60));
                    g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                }
                if (i == editingBand) {
                    g2.setColor(new Color(0, 0, 0, 75));
                    g2.fillRect(0, i * bandHeight, panelWidth, bandHeight);
                }
            } else {
                if (i == selectedBand)
                    g2.setColor(darkenColor(baseBandColor));
                else
                    g2.setColor(baseBandColor);
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

        int startX = Math.max(0, (int) Math.floor(offsetX));
        int endX = Math.min(panelWidth, (int) Math.ceil(waveformData.getDurationSeconds() * pixelsPerSecond + offsetX));
        if (startX >= endX) return;

        for (int screenX = startX; screenX < endX; screenX++) {
            double timeSeconds = (screenX - offsetX) / pixelsPerSecond;
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
                           boolean separatorsVisible,
                           int panelWidth) {

        Font textFont = buildTimelineFont(bandHeight);
        g2.setFont(textFont);
        FontMetrics fm = (cachedFontMetrics != null && cachedFontMetrics.getFont() == textFont)
                ? cachedFontMetrics
                : g2.getFontMetrics(textFont);
        cachedFontMetrics = fm;
        int textTopY = 1;
        int targetTextHeight = Math.max(8, bandHeight - 2);
        double scaleY = computeScaleY(fm, targetTextHeight);

        g2.setColor(Color.WHITE);

        // Prévisualisation interactive en temps réel pendant la frappe au clavier
        if (activeBand != -1 && isEditing && editingItem == null) {
            double drawX = textX + offsetX;
            int drawY = computeBaselineY(activeBand, bandHeight, textTopY, fm, targetTextHeight);
            String preview = caretVisible ? (currentInput + "|") : currentInput;
            drawScaledText(g2, fm, preview, drawX, drawY, null, targetTextHeight, false);
            g2.setColor(Color.WHITE);
        }

        int nTexts = texts.size();
        if (cachedPhraseStarts.length < nTexts) {
            cachedPhraseStarts = new int[Math.max(nTexts, 64)];
            cachedPhraseEnds = new int[Math.max(nTexts, 64)];
        }

        int[] resolvedBounds = new int[4];
        for (int ti = 0; ti < nTexts; ti++) {
            TextItem t = texts.get(ti);
            if (t == null) {
                cachedPhraseStarts[ti] = 0;
                cachedPhraseEnds[ti] = 0;
                continue;
            }
            if (t.text == null) t.text = "";

            ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
            resolvePhraseBoundaries(sepList, t.x, resolvedBounds);
            int leftSep = resolvedBounds[0];
            int rightSep = resolvedBounds[1];
            int nextPhraseStartX = resolvedBounds[2];
            int maxInnerX = resolvedBounds[3];

            int segmentStart = (leftSep != Integer.MIN_VALUE) ? leftSep : t.x;
            int segmentEnd;

            double textWidth = measureTextWidth(fm, t.text) * scaleY;
            int naturalWidth = Math.max(24, (int) Math.ceil(textWidth + 6.0));

            if (rightSep != Integer.MAX_VALUE) {
                segmentEnd = Math.max(segmentStart + 10, rightSep);
            } else {
                segmentEnd = segmentStart + naturalWidth;
                if (maxInnerX != Integer.MIN_VALUE && maxInnerX > segmentStart) {
                    segmentEnd = Math.max(segmentEnd, maxInnerX + Math.max(60, naturalWidth));
                }
                if (nextPhraseStartX != Integer.MAX_VALUE && segmentEnd > nextPhraseStartX - 4) {
                    segmentEnd = Math.max(segmentStart + 10, nextPhraseStartX - 4);
                }
            }
            cachedPhraseStarts[ti] = segmentStart;
            cachedPhraseEnds[ti] = segmentEnd;
        }

        // Passe 1 : Rendu des textes des phrases, de la sélection et des soulignements orthographiques
        for (int ti = 0; ti < nTexts; ti++) {
            TextItem t = texts.get(ti);
            if (t == null) continue;

            int segmentStart = cachedPhraseStarts[ti];
            int segmentEnd = cachedPhraseEnds[ti];

            // Viewport Culling Mathématiquement Exact & Optimal (supporte des timelines de 7h+) :
            // Un texte n'est ignoré que si son extrémité droite est complètement sortie à gauche (< -400px),
            // ou si son début n'a pas encore atteint l'écran (> panelWidth + 400px).
            double screenStart = segmentStart + offsetX;
            double screenEnd = segmentEnd + offsetX;
            if (screenEnd < -400 || screenStart > panelWidth + 400) {
                continue;
            }

            int bandBaselineY = computeBaselineY(t.band, bandHeight, textTopY, fm, targetTextHeight);
            ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
            ArrayList<SeparatorMark> innerMarks = scratchInnerMarks;
            innerMarks.clear();

            // Collecte des repères syllabiques internes (INNER) valides (sans coupure après clitique ni vide)
            collectValidInnerMarks(sepList, segmentStart, segmentEnd, t, innerMarks);

            double availableWidth = Math.max(20.0, (double) (segmentEnd - segmentStart));

            if (!t.text.isEmpty()) {
                Color roleColor = (t.role != null && t.role.color != null) ? t.role.color : Color.WHITE;
                double luminance = 0.299 * roleColor.getRed() + 0.587 * roleColor.getGreen() + 0.114 * roleColor.getBlue();
                boolean isDarkBg = isDarkColor(customization.timelineEvenBand);
                if (isDarkBg) {
                    if (luminance < 45) {
                        roleColor = new Color(225, 225, 225);
                    }
                } else {
                    if (luminance > 185) {
                        roleColor = new Color(30, 35, 45);
                    }
                }

                if (innerMarks.isEmpty()) {
                    // Pas de séparateurs internes : la phrase entière est étirée d'un seul bloc
                    g2.setColor(roleColor);
                    double leftPad = 2.0;
                    double rightPad = 4.0;
                    double drawX = segmentStart + offsetX + leftPad;
                    double width = Math.max(2.0, availableWidth - (leftPad + rightPad));

                    drawScaledText(g2, fm, t.text, drawX, bandBaselineY, width, targetTextHeight, false);
                } else {
                    // Présence de séparateurs syllabiques : chaque portion est étirée et bornée indépendamment
                    int len = t.text.length();
                    int prevX = segmentStart;
                    int prevIdx = 0;
                    g2.setColor(roleColor);
                    boolean anyTextDrawn = false;

                    for (int mi = 0; mi < innerMarks.size(); mi++) {
                        SeparatorMark mark = innerMarks.get(mi);
                        int segStart = prevX;
                        int segEnd = mark.x;
                        if (segEnd <= segStart) continue;

                        int idx = mark.splitIndex;
                        if (idx < 0 || idx > len) {
                            double ratio = (double) (mark.x - segmentStart) / Math.max(1, segmentEnd - segmentStart);
                            idx = (int) Math.round(ratio * len);
                        }
                        if (idx < prevIdx) idx = prevIdx;
                        if (idx > len) idx = len;

                        String segText = t.text.substring(prevIdx, idx);
                        boolean hasVisibleChars = !segText.trim().isEmpty();
                        if (hasVisibleChars) {
                            double leftPad = 2.0;
                            double rightPad = 3.0;
                            double segStartX = segStart + offsetX + leftPad;
                            double segEndX = segEnd + offsetX - rightPad;
                            double width = Math.max(2.0, segEndX - segStartX);

                            drawScaledText(g2, fm, segText, segStartX, bandBaselineY, width, targetTextHeight, false);
                            anyTextDrawn = true;
                            prevX = mark.x;
                            prevIdx = idx;
                        } else {
                            // Tronçon vide = vraie pause : le mot suivant démarre à la barre d'ouverture
                            prevIdx = idx;
                            prevX = mark.x;
                        }
                    }

                    String remaining = t.text.substring(Math.min(prevIdx, len));
                    if (!remaining.isEmpty()) {
                        double leftPad = 2.0;
                        double rightPad = 4.0;
                        double segStartX = prevX + offsetX + leftPad;
                        double segEndX = segmentEnd + offsetX - rightPad;
                        double width = Math.max(2.0, segEndX - segStartX);

                        drawScaledText(g2, fm, remaining, segStartX, bandBaselineY, width, targetTextHeight, false);
                        anyTextDrawn = true;
                    }

                    // Filet de sécurité anti-texte fantôme : uniquement si aucun séparateur interne
                    if (!anyTextDrawn && innerMarks.isEmpty()) {
                        drawScaledText(g2, fm, t.text, segmentStart + offsetX + 4, bandBaselineY, availableWidth - 8, targetTextHeight, false);
                    }
                }
            }

            // Affichage de la sélection de texte (surbrillance bleue Windows)
            if (t == editingItem && isEditing && selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd) {
                int selStart = Math.min(selectionStart, selectionEnd);
                int selEnd = Math.max(selectionStart, selectionEnd);
                int tlen = (t.text != null) ? t.text.length() : 0;
                selStart = Math.max(0, Math.min(selStart, tlen));
                selEnd = Math.max(selStart, Math.min(selEnd, tlen));
                if (selEnd > selStart) {
                    double selStartX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, selStart, true, targetTextHeight, this.activeSegmentEndMarkX);
                    double selEndX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, selEnd, false, targetTextHeight, this.activeSegmentEndMarkX);
                    if (selEndX < selStartX) {
                        double tmp = selStartX;
                        selStartX = selEndX;
                        selEndX = tmp;
                    }
                    int boxTop = bandBaselineY - (int) Math.round(fm.getAscent() * ((double) targetTextHeight / Math.max(1, fm.getHeight())));
                    int boxHeight = (int) Math.round(fm.getHeight() * ((double) targetTextHeight / Math.max(1, fm.getHeight()))) + 4;
                    double boxWidth = Math.max(2.0, selEndX - selStartX);

                    g2.setColor(new Color(51, 153, 255, 120));
                    g2.fill(new Rectangle2D.Double(selStartX, boxTop - 2, boxWidth, boxHeight));
                    g2.setColor(new Color(0, 102, 204, 180));
                    g2.setStroke(new BasicStroke(1.0f));
                    g2.draw(new Rectangle2D.Double(selStartX, boxTop - 2, boxWidth, boxHeight));
                }
            }

            // Soulignement des anomalies orthographiques et grammaticales
            // Condition explicite : visible uniquement si les signes/séparateurs sont affichés et hors export vidéo
            // Temporisation 1 seconde : si l'utilisateur est activement en train de taper ce texte, on masque les erreurs pendant la frappe
            if (!exportMode && separatorsVisible && t.text != null && !t.text.isEmpty()) {
                boolean isActivelyTypingThisItem = isEditing && (t == editingItem) && (System.currentTimeMillis() - lastTypingTimestamp < 1000);
                if (!isActivelyTypingThisItem) {
                    List<app.services.SpellGrammarService.SpellCheckIssue> issues =
                            app.services.SpellGrammarService.getInstance().checkText(t.text);
                    if (issues != null && !issues.isEmpty()) {
                        for (app.services.SpellGrammarService.SpellCheckIssue issue : issues) {
                            int safeStart = Math.max(0, Math.min(issue.startIdx, t.text.length()));
                            int safeEnd = Math.max(safeStart, Math.min(issue.endIdx, t.text.length()));
                            double startScreenX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, safeStart, false, targetTextHeight);
                            double endScreenX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, safeEnd, false, targetTextHeight);

                            if (endScreenX >= -100 && startScreenX <= panelWidth + 100) {
                                int ascent = (int) Math.round(fm.getAscent() * ((double) targetTextHeight / Math.max(1, fm.getHeight())));
                                issue.screenStartX = startScreenX;
                                issue.screenEndX = endScreenX;
                                issue.screenY = bandBaselineY - ascent;
                                issue.screenHeight = ascent + 6;
                                issue.targetItem = t;
                                issue.band = t.band;
                                app.services.SpellGrammarService.getInstance().registerVisibleIssue(issue);

                                drawSquigglyUnderline(g2, startScreenX, endScreenX, bandBaselineY + 3, issue.isGrammar);
                            }
                        }
                    }
                }
            }
        }

        // Passe 2 : Rendu des étiquettes de rôles (avec mise à l'échelle 10% à 200% et translucidité en cas de chevauchement sur une phrase)
        float baseLabelFontSize = exportMode
                ? Math.max(3.0f, (float) (bandHeight * 0.125f))
                : Math.max(7.5f, Math.min(11.0f, (float) (bandHeight * 0.125f)));
        float labelFontSize = Math.max(1.5f, baseLabelFontSize * (float) roleLabelScale);

        if (cachedLabelFont == null || cachedLabelBandHeight != bandHeight
                || Math.abs(cachedLabelFontSize - labelFontSize) > 0.01f
                || !cachedLabelFont.getFamily().equals(textFont.getFamily())) {
            cachedLabelFont = textFont.deriveFont(Font.BOLD, labelFontSize);
            cachedLabelFontMetrics = g2.getFontMetrics(cachedLabelFont);
            cachedLabelBandHeight = bandHeight;
            cachedLabelFontSize = labelFontSize;
        }
        Font labelFont = cachedLabelFont;
        FontMetrics lfm = cachedLabelFontMetrics;
        int padX = Math.max(1, Math.round(labelFontSize * 0.25f));
        int padY = Math.max(0, Math.round(labelFontSize * 0.10f));

        for (int ti = 0; ti < nTexts; ti++) {
            TextItem t = texts.get(ti);
            if (t == null || t.role == null || t.role.name == null || t.role.name.isEmpty()) continue;

            int segmentStart = cachedPhraseStarts[ti];
            int segmentEnd = cachedPhraseEnds[ti];
            double screenStart = segmentStart + offsetX;
            double screenEnd = segmentEnd + offsetX;
            if (screenEnd < -400 || screenStart > panelWidth + 400) {
                continue;
            }

            Color roleColor = (t.role.color != null) ? t.role.color : new Color(0, 120, 215);
            Font oldFont = g2.getFont();
            g2.setFont(labelFont);

            int lw = lfm.stringWidth(t.role.name) + padX * 2;
            int lh = lfm.getHeight() + padY * 2;

            int minTop = t.band * bandHeight + 1;
            int maxTop = Math.max(minTop, (t.band + 1) * bandHeight - lh - 1);
            int badgeTop = Math.max(minTop, Math.min(maxTop, t.band * bandHeight + Math.max(2, (bandHeight - lh) / 6)));
            int labelY = badgeTop + padY + lfm.getAscent();

            // Positionnement sur le côté GAUCHE du repère START avec marge aérée
            double labelX = segmentStart + offsetX - lw - 3;
            double badgeWorldLeft = segmentStart - lw - 3;
            double badgeWorldRight = segmentStart - 3;

            boolean overlapsPhrase = doesRoleBadgeOverlapAnyPhrase(
                    badgeWorldLeft, badgeWorldRight, t.band, ti, texts, cachedPhraseStarts, cachedPhraseEnds);

            if (!overlapsPhrase && activeBand == t.band && isEditing && editingItem == null
                    && currentInput != null && !currentInput.isEmpty()) {
                double liveW = measureTextWidth(fm, currentInput) * scaleY;
                if (badgeWorldLeft < textX + liveW && badgeWorldRight > textX) {
                    overlapsPhrase = true;
                }
            }

            Composite oldComp = g2.getComposite();
            if (overlapsPhrase) {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, OVERLAPPING_ROLE_BADGE_ALPHA));
            }

            // Fond avec la couleur du rôle, bords rectangulaires nets
            g2.setColor(roleColor);
            g2.fillRect((int) Math.round(labelX), badgeTop, lw, lh);

            // Contour net et contrasté
            g2.setColor(isDarkColor(roleColor) ? new Color(255, 255, 255, 175) : new Color(0, 0, 0, 95));
            g2.setStroke(new BasicStroke(0.9f));
            g2.drawRect((int) Math.round(labelX), badgeTop, lw, lh);

            // Couleur du texte : noir par défaut, blanc si le rôle est noir/sombre
            Color textColor = isDarkColor(roleColor) ? Color.WHITE : Color.BLACK;
            g2.setColor(textColor);
            g2.drawString(t.role.name, (float) (labelX + padX), (float) labelY);

            if (overlapsPhrase) {
                g2.setComposite(oldComp);
            }
            g2.setFont(oldFont);
        }
    }

    /**
     * Dessine un soulignement ondulé (squiggly wave) sous les fautes d'orthographe (rouge)
     * ou les fautes de grammaire (orange/ambre), reproduisant le style standard des traitements de texte.
     */
    private void drawSquigglyUnderline(Graphics2D g2, double startX, double endX, int y, boolean isGrammar) {
        if (endX <= startX) return;
        Stroke oldStroke = g2.getStroke();
        Color oldColor = g2.getColor();

        Color waveColor = isGrammar ? new Color(245, 140, 0, 225) : new Color(235, 50, 50, 235);
        g2.setColor(waveColor);
        g2.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        java.awt.geom.Path2D.Double wave = new java.awt.geom.Path2D.Double();
        wave.moveTo(startX, y);
        double step = 3.5;
        double amp = 1.6;
        boolean up = true;
        for (double curX = startX + step; curX <= endX; curX += step) {
            wave.lineTo(curX, y + (up ? amp : -amp));
            up = !up;
        }
        if (endX > startX) {
            wave.lineTo(endX, y + (up ? amp : -amp));
        }
        g2.draw(wave);

        g2.setStroke(oldStroke);
        g2.setColor(oldColor);
    }

    private void drawTextSegment(Graphics2D g2, FontMetrics fm, String text, double segStartX, double segEndX, int baselineY, int targetTextHeight) {
        if (text == null || text.isEmpty()) return;
        double width = Math.max(6.0, segEndX - segStartX);
        drawScaledText(g2, fm, text, segStartX, baselineY, width, targetTextHeight, false);
    }

    public Font buildTimelineFont(int bandHeight) {
        String family = customization.timelineFontFamily != null && !customization.timelineFontFamily.isBlank()
                ? customization.timelineFontFamily
                : "Arial";
        if (cachedTimelineFont != null && cachedBandHeight == bandHeight && family.equals(cachedFontFamily)) {
            return cachedTimelineFont;
        }
        int size = Math.max(18, bandHeight);
        cachedTimelineFont = new Font(family, Font.BOLD, size);
        cachedFontMetrics = null;
        cachedLabelFont = null;
        cachedLabelFontMetrics = null;
        cachedBadgeFont = null;
        cachedBadgeMetrics = null;
        cachedBandHeight = bandHeight;
        cachedFontFamily = family;
        return cachedTimelineFont;
    }

    /**
     * Calcule le facteur d'échelle vertical (scaleY) exact selon les métriques de la police active
     * afin que le texte (ascension + descente) tienne toujours dans la bande sans déborder.
     */
    public static double computeScaleY(FontMetrics fm, int targetTextHeight) {
        if (fm == null) return 1.0;
        int verticalSpan = Math.max(1, Math.max(fm.getHeight(), fm.getAscent() + fm.getDescent()));
        return (double) Math.max(8, targetTextHeight) / verticalSpan;
    }

    private static final java.awt.font.FontRenderContext FRACTIONAL_FRC =
            new java.awt.font.FontRenderContext(null, true, true);

    /**
     * Mesure la largeur réelle (en pixels non-étirés) d'une chaîne pour la police donnée,
     * en prenant le maximum entre stringWidth et les bornes fractionnaires des glyphes
     * afin d'éviter tout débordement lors d'un changement de police.
     */
    public static double measureTextWidth(FontMetrics fm, String text) {
        if (fm == null || text == null || text.isEmpty()) return 0.0;
        double sw = fm.stringWidth(text);
        try {
            Font f = fm.getFont();
            if (f != null) {
                Rectangle2D bounds = f.getStringBounds(text, FRACTIONAL_FRC);
                double bw = Math.max(bounds.getWidth(), bounds.getMaxX());
                return Math.max(sw, bw);
            }
        } catch (Exception ignored) {
        }
        return sw;
    }

    /**
     * Résout de manière déterministe les bornes d'une phrase commençant à {@code textX} :
     * - {@code outBounds[0]} = leftSep (START de cette phrase)
     * - {@code outBounds[1]} = rightSep (END de cette phrase, ou Integer.MAX_VALUE)
     * - {@code outBounds[2]} = nextPhraseStartX (START de la phrase suivante, ou Integer.MAX_VALUE)
     * - {@code outBounds[3]} = maxInnerX (dernier INNER appartenant à cette phrase, ou Integer.MIN_VALUE)
     *
     * Gère proprement le cas où le symbole END de la phrase précédente est collé exactement sur (ou très près de)
     * le symbole START de la phrase courante : un END situé en {@code <= leftSep} appartient toujours à la phrase
     * précédente et n'est jamais confondu avec la fin de la phrase courante.
     */
    public static void resolvePhraseBoundaries(ArrayList<SeparatorMark> sepList, int textX, int[] outBounds) {
        int leftSep = Integer.MIN_VALUE;
        int rightSep = Integer.MAX_VALUE;
        int nextPhraseStartX = Integer.MAX_VALUE;
        int maxInnerX = Integer.MIN_VALUE;

        if (sepList != null && !sepList.isEmpty()) {
            int startMarkIdx = -1;
            int searchIdx = findSepIndex(sepList, textX);
            // 1. Trouver le repère START associé à ce TextItem (priorité à s.x == textX, sinon le START immédiatement <= textX)
            for (int i = Math.max(0, searchIdx - 2); i < sepList.size(); i++) {
                SeparatorMark s = sepList.get(i);
                if (s == null) continue;
                if (s.x > textX) break;
                if (s.isStartBoundary() && s.x == textX) {
                    leftSep = s.x;
                    startMarkIdx = i;
                    break;
                }
            }
            if (startMarkIdx == -1) {
                int maxI = Math.min(searchIdx + 2, sepList.size() - 1);
                for (int i = maxI; i >= 0; i--) {
                    SeparatorMark s = sepList.get(i);
                    if (s == null || s.x > textX) continue;
                    if (s.isStartBoundary()) {
                        leftSep = s.x;
                        startMarkIdx = i;
                        break;
                    }
                    if (s.isEndBoundary() && s.x < textX) {
                        break;
                    }
                }
            }

            int phraseOriginX = (leftSep != Integer.MIN_VALUE) ? leftSep : textX;
            int scanFrom = (startMarkIdx != -1) ? (startMarkIdx + 1) : Math.max(0, searchIdx - 1);
            for (int i = scanFrom; i < sepList.size(); i++) {
                SeparatorMark s = sepList.get(i);
                if (s == null) continue;
                // Ignorer tout repère situé avant ou exactement sur l'origine de la phrase
                // (notamment le repère END de la phrase précédente lorsqu'il est collé au START courant)
                if (s.x <= phraseOriginX) continue;
                if (s.isEndBoundary()) {
                    rightSep = s.x;
                    break;
                }
                if (s.isStartBoundary()) {
                    nextPhraseStartX = s.x;
                    break;
                }
                if ((s.type == SeparatorMark.Type.INNER || s.type == SeparatorMark.Type.LEGACY) && s.x > maxInnerX) {
                    maxInnerX = s.x;
                }
            }
        }

        outBounds[0] = leftSep;
        outBounds[1] = rightSep;
        outBounds[2] = nextPhraseStartX;
        outBounds[3] = maxInnerX;
    }

    /**
     * Calcule la coordonnée verticale (Y) de la ligne de base (baseline) d'une bande.
     * 
     * En typographie numérique, la ligne de base est la ligne imaginaire sur laquelle
     * reposent les lettres (sans compter les jambages descendants comme pour 'p', 'q', 'y').
     * En alignant la baseline au centre vertical de la bande avec l'ascension de la police,
     * le texte reste parfaitement lisible et harmonieux quel que soit le redimensionnement.
     */
    private int computeBaselineY(int bandIndex, int bandHeight, int textTopY, FontMetrics fm, int targetTextHeight) {
        int bandTop = bandIndex * bandHeight;
        double scaleY = computeScaleY(fm, targetTextHeight);
        return bandTop + textTopY + (int) Math.round(fm.getAscent() * scaleY);
    }

    /**
     * Détermine si une couleur est sombre via la formule de luminance perceptuelle standard (ITU-R BT.709 / sRGB).
     * Permet d'adapter dynamiquement la couleur du texte (noir ou blanc) pour un contraste WCAG optimal.
     */
    private boolean isDarkColor(Color color) {
        if (color == null) return false;
        int r = color.getRed();
        int g = color.getGreen();
        int b = color.getBlue();
        double luminance = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0;
        return luminance < 0.55;
    }

    /**
     * Recherche dichotomique (Binary Search en O(log N)) dans une liste ordonnée de repères de séparation.
     * Retourne le premier index >= targetX (lower bound).
     */
    public static int findSepIndex(ArrayList<SeparatorMark> list, int targetX) {
        if (list == null || list.isEmpty()) return 0;
        int low = 0;
        int high = list.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1; // Décalage de bit non signé évitant les dépassements d'entier
            int midVal = list.get(mid).x;
            if (midVal < targetX) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Recherche dichotomique (Binary Search en O(log N)) pour localiser rapidement un élément de texte
     * parmi tous les textes triés selon leur coordonnée X.
     */
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
        String family = customization.timelineFontFamily != null && !customization.timelineFontFamily.isBlank()
                ? customization.timelineFontFamily : "Segoe UI";
        if (cachedBadgeFont != null && cachedBadgeBandHeight == bandHeight
                && cachedBadgeFont.getFamily().equals(family)) {
            return cachedBadgeFont;
        }
        int size = Math.max(9, (int)(bandHeight * 0.2f));
        cachedBadgeFont = new Font(family, Font.BOLD, size);
        cachedBadgeBandHeight = bandHeight;
        return cachedBadgeFont;
    }

    /**
     * Détecte si une portion de texte correspond à une prolongation phonétique explicite
     * (ex: "Nooooon !", "Euuuh...", voyelles répétées au moins 3 fois, tildes de prolongation).
     */
    public static boolean isTextProlongedPattern(String text) {
        if (text == null || text.length() < 2) return false;
        String clean = text.trim().toLowerCase();
        // Voyelles ou consonnes sonores répétées 3+ fois d'affilée (ex: "ooo", "aaa", "eee", "mmm", "uuu")
        if (clean.matches(".*([a-zà-ÿ])\\1{2,}.*")) return true;
        // Tilde de prolongation vocalique (ex: '~')
        if (clean.contains("~")) return true;
        return false;
    }

    /**
     * Calcule le facteur d'échelle horizontal (scaleX) pour un texte ou tronçon sur la bande rythmo.
     * 
     * Étire, rétrécit ou compresse élastiquement le texte selon la largeur cible (targetWidth)
     * et la largeur réelle mesurée dans la police active (sourceWidth), afin que le texte
     * occupe exactement toute la durée de la phrase (ou du segment délimité par une fin)
     * sans jamais déborder en dehors de la phrase.
     */
    public static double computeEffectiveScaleX(String text, double targetWidth, double sourceWidth, double scaleY) {
        if (sourceWidth <= 0) return scaleY;
        double rawScaleX = targetWidth / sourceWidth;
        if (Double.isNaN(rawScaleX) || Double.isInfinite(rawScaleX) || rawScaleX <= 0) {
            return scaleY;
        }

        double minScaleX = 0.02;
        return Math.max(minScaleX, rawScaleX);
    }

    /**
     * Dessine une chaîne de caractères étirée ou compressée élastiquement via une transformation affine 2D (AffineTransform).
     */
    private void drawScaledText(Graphics2D g2,
                                FontMetrics fm,
                                String text,
                                double anchorX,
                                int baselineY,
                                Double targetWidth,
                                int targetTextHeight,
                                boolean anchoredRight) {
        if (text == null || text.isEmpty()) return;
        double sourceWidth = measureTextWidth(fm, text);
        if (sourceWidth <= 0) return;

        double scaleY = computeScaleY(fm, targetTextHeight);
        double scaleX = scaleY;
        if (targetWidth != null && targetWidth > 0) {
            scaleX = computeEffectiveScaleX(text, targetWidth, sourceWidth, scaleY);
        }

        AffineTransform old = g2.getTransform();
        g2.translate(anchorX, (double) baselineY);
        g2.scale(scaleX, scaleY);
        float drawX = anchoredRight ? (float) (-sourceWidth) : 0f;
        g2.drawString(text, drawX, 0f);
        g2.setTransform(old);
    }

    /**
     * Filtre et collecte les repères syllabiques internes (INNER) valides :
     * élimine les coupures redondantes, les trous/espaces vides et les césures après clitiques français.
     */
    public static void collectValidInnerMarks(ArrayList<SeparatorMark> sepList, int segmentStart, int segmentEnd, TextItem t, ArrayList<SeparatorMark> outList) {
        outList.clear();
        if (sepList == null || sepList.isEmpty()) return;
        int textLen = (t != null && t.text != null) ? t.text.length() : 0;
        int prevInnerX = Integer.MIN_VALUE;

        for (SeparatorMark m : sepList) {
            if (m == null) continue;
            if (m.x <= segmentStart || m.x >= segmentEnd) continue;
            if (m.type == SeparatorMark.Type.INNER || m.type == SeparatorMark.Type.LEGACY) {
                if (prevInnerX != Integer.MIN_VALUE && !outList.isEmpty()) {
                    SeparatorMark last = outList.get(outList.size() - 1);
                    boolean distinctSplits = (last.splitIndex >= 0 && m.splitIndex >= 0 && last.splitIndex != m.splitIndex);
                    int dedupTol = distinctSplits ? 2 : 4;
                    if (Math.abs(m.x - prevInnerX) <= dedupTol) {
                        if (last.signType == SeparatorMark.SignType.DEFAULT && m.signType != SeparatorMark.SignType.DEFAULT) {
                            outList.set(outList.size() - 1, m);
                            prevInnerX = m.x;
                        }
                        continue; // Doublon strict ou superposé
                    }
                }
                prevInnerX = m.x;
                outList.add(m);
            }
        }

        int prevSplit = 0;
        for (int i = 0; i < outList.size(); i++) {
            SeparatorMark m = outList.get(i);
            if (m.splitIndex < 0) {
                if (textLen > 0) {
                    int nextKnownSplit = textLen;
                    for (int j = i + 1; j < outList.size(); j++) {
                        if (outList.get(j).splitIndex >= 0) {
                            nextKnownSplit = Math.min(textLen, outList.get(j).splitIndex);
                            break;
                        }
                    }
                    double ratio = (double) (m.x - segmentStart) / Math.max(1, segmentEnd - segmentStart);
                    int interp = (int) Math.round(ratio * textLen);
                    m.splitIndex = Math.max(prevSplit, Math.min(Math.max(prevSplit, nextKnownSplit), interp));
                } else {
                    m.splitIndex = 0;
                }
            } else {
                m.splitIndex = Math.max(prevSplit, Math.min(textLen, m.splitIndex));
            }
            prevSplit = m.splitIndex;
        }
    }

    /**
     * Calcule la position horizontale précise en pixels à l'écran du curseur de texte (caret d'édition)
     * en tenant compte des déformations non linéaires introduites par les séparateurs syllabiques internes.
     */
    public double computeCursorXForSegments(FontMetrics fm,
                                            TextItem t,
                                            double offsetX,
                                            int startWorldX,
                                            int endWorldX,
                                            ArrayList<SeparatorMark> innerMarks,
                                            int cursorIdx,
                                            boolean cursorRightSide) {
        int targetTextHeight = (fm != null) ? Math.max(8, fm.getHeight()) : 40;
        return computeCursorXForSegments(fm, t, offsetX, startWorldX, endWorldX, innerMarks, cursorIdx, cursorRightSide, targetTextHeight, this.activeSegmentEndMarkX);
    }

    public double computeCursorXForSegments(FontMetrics fm,
                                            TextItem t,
                                            double offsetX,
                                            int startWorldX,
                                            int endWorldX,
                                            ArrayList<SeparatorMark> innerMarks,
                                            int cursorIdx,
                                            boolean cursorRightSide,
                                            int targetTextHeight) {
        return computeCursorXForSegments(fm, t, offsetX, startWorldX, endWorldX, innerMarks, cursorIdx, cursorRightSide, targetTextHeight, this.activeSegmentEndMarkX);
    }

    public double computeCursorXForSegments(FontMetrics fm,
                                            TextItem t,
                                            double offsetX,
                                            int startWorldX,
                                            int endWorldX,
                                            ArrayList<SeparatorMark> innerMarks,
                                            int cursorIdx,
                                            boolean cursorRightSide,
                                            int targetTextHeight,
                                            int targetSegEndMarkX) {
        if (t == null || t.text == null) {
            return startWorldX + offsetX + 4.0;
        }

        int len = t.text.length();
        if (len == 0 && (innerMarks == null || innerMarks.isEmpty())) {
            return startWorldX + offsetX + 4.0;
        }

        if (fm == null) {
            if (cachedFontMetrics != null) {
                fm = cachedFontMetrics;
            } else {
                Font f = buildTimelineFont(targetTextHeight);
                BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                fm = dummy.getGraphics().getFontMetrics(f != null ? f : new Font("Arial", Font.BOLD, Math.max(18, targetTextHeight)));
            }
        }
        if (targetTextHeight <= 0) {
            targetTextHeight = (fm != null) ? Math.max(8, fm.getHeight()) : 40;
        }

        double scaleY = computeScaleY(fm, targetTextHeight);

        // Cas 1 : Aucun séparateur interne
        if (innerMarks == null || innerMarks.isEmpty()) {
            double leftPad = 2.0;
            double rightPad = 4.0;
            double segStartX = startWorldX + offsetX + leftPad;
            double segEndX = endWorldX + offsetX - rightPad;
            double segWidth = Math.max(2.0, segEndX - segStartX);
            double total = measureTextWidth(fm, t.text);
            if (total <= 0) return segStartX;

            double scaleX = computeEffectiveScaleX(t.text, segWidth, total, scaleY);
            int localIdx = Math.max(0, Math.min(cursorIdx, len));
            double charOffset = measureTextWidth(fm, t.text.substring(0, localIdx)) * scaleX;
            return segStartX + charOffset;
        }

        // Cas 2 : Présence de séparateurs internes
        int matchedSegIdx = -1;
        if (targetSegEndMarkX != -1) {
            int pIdx = 0;
            for (int mi = 0; mi < innerMarks.size(); mi++) {
                SeparatorMark m = innerMarks.get(mi);
                int idx = Math.max(pIdx, Math.min(len, m.splitIndex >= 0 ? m.splitIndex : 0));
                if (targetSegEndMarkX != Integer.MAX_VALUE && Math.abs(m.x - targetSegEndMarkX) <= 6) {
                    if (cursorIdx >= pIdx && cursorIdx <= idx) {
                        matchedSegIdx = mi;
                    }
                    break;
                }
                pIdx = idx;
            }
            if (matchedSegIdx == -1 && targetSegEndMarkX == Integer.MAX_VALUE) {
                int lastSplit = Math.max(0, Math.min(len, innerMarks.get(innerMarks.size() - 1).splitIndex));
                if (cursorIdx >= lastSplit && cursorIdx <= len) {
                    matchedSegIdx = innerMarks.size();
                }
            }
        }

        int prevX = startWorldX;
        int prevIdx = 0;

        for (int mi = 0; mi < innerMarks.size(); mi++) {
            SeparatorMark mark = innerMarks.get(mi);
            int segStart = prevX;
            int segEnd = mark.x;
            int idx = mark.splitIndex;
            if (idx < 0) {
                double ratio = (double) (mark.x - startWorldX) / Math.max(1, endWorldX - startWorldX);
                idx = (int) Math.round(ratio * len);
            }
            if (idx < prevIdx) idx = prevIdx;
            if (idx > len) idx = len;

            boolean inThisSegment = (matchedSegIdx != -1)
                    ? (mi == matchedSegIdx)
                    : ((cursorIdx < idx) || (cursorIdx == idx && !cursorRightSide));

            if (inThisSegment) {
                String segText = t.text.substring(prevIdx, idx);
                double leftPad = 2.0;
                double rightPad = 3.0;
                double segStartX = segStart + offsetX + leftPad;
                double segEndX = segEnd + offsetX - rightPad;
                double segWidth = Math.max(2.0, segEndX - segStartX);
                if (segText.isEmpty()) {
                    return Math.min(segStartX, segEnd + offsetX - 3.0);
                }

                double total = measureTextWidth(fm, segText);
                if (total <= 0) return Math.min(segStartX, segEnd + offsetX - 3.0);

                double scaleX = computeEffectiveScaleX(segText, segWidth, total, scaleY);
                int localIdx = Math.max(0, Math.min(cursorIdx - prevIdx, segText.length()));
                double charOffset = measureTextWidth(fm, segText.substring(0, localIdx)) * scaleX;
                return Math.min(segStartX + charOffset, segEnd + offsetX - 3.0);
            }

            prevX = mark.x;
            prevIdx = idx;
        }

        // Dernier tronçon après le dernier séparateur interne
        int segStart = prevX;
        int segEnd = endWorldX;
        String segText = t.text.substring(Math.min(prevIdx, len));
        double leftPad = 2.0;
        double rightPad = 4.0;
        double segStartX = segStart + offsetX + leftPad;
        double segEndX = segEnd + offsetX - rightPad;
        double segWidth = Math.max(2.0, segEndX - segStartX);
        if (segText.isEmpty()) return segStartX;

        double total = measureTextWidth(fm, segText);
        if (total <= 0) return segStartX;

        double scaleX = computeEffectiveScaleX(segText, segWidth, total, scaleY);
        int localIdx = Math.max(0, Math.min(cursorIdx - prevIdx, segText.length()));
        double charOffset = measureTextWidth(fm, segText.substring(0, localIdx)) * scaleX;
        return segStartX + charOffset;
    }

    /**
     * Dessine le curseur clignotant d'édition (caret) en superposition au-dessus des séparateurs.
     * Garantit une visibilité totale, supprime tout artefact de coloration jaune, et fournit un
     * indicateur d'orientation cyan clair (◄ ou ►) pour distinguer instantanément le côté actif du signe.
     */
    private void drawEditingCaret(Graphics2D g2,
                                  TextItem editingItem,
                                  Map<Integer, ArrayList<SeparatorMark>> bandSeparators,
                                  int bandHeight,
                                  double offsetX,
                                  int cursorIndex,
                                  boolean cursorRightSide,
                                  int panelWidth) {
        if (editingItem == null || editingItem.text == null) return;
        Font textFont = buildTimelineFont(bandHeight);
        FontMetrics fm = g2.getFontMetrics(textFont);
        int textTopY = 1;
        int targetTextHeight = Math.max(8, bandHeight - 2);
        double scaleY = computeScaleY(fm, targetTextHeight);
        int bandBaselineY = computeBaselineY(editingItem.band, bandHeight, textTopY, fm, targetTextHeight);

        ArrayList<SeparatorMark> sepList = bandSeparators.get(editingItem.band);
        int[] resolvedBounds = new int[4];
        resolvePhraseBoundaries(sepList, editingItem.x, resolvedBounds);
        int leftSep = resolvedBounds[0];
        int rightSep = resolvedBounds[1];
        int nextPhraseStartX = resolvedBounds[2];
        int maxInnerX = resolvedBounds[3];
        ArrayList<SeparatorMark> innerMarks = new ArrayList<>();

        int segmentStart = (leftSep != Integer.MIN_VALUE) ? leftSep : editingItem.x;
        int segmentEnd;
        double textWidth = measureTextWidth(fm, editingItem.text) * scaleY;
        int naturalWidth = Math.max(24, (int) Math.ceil(textWidth + 6.0));
        if (rightSep != Integer.MAX_VALUE) {
            segmentEnd = Math.max(segmentStart + 10, rightSep);
        } else {
            segmentEnd = segmentStart + naturalWidth;
            if (maxInnerX != Integer.MIN_VALUE && maxInnerX > segmentStart) {
                segmentEnd = Math.max(segmentEnd, maxInnerX + Math.max(60, naturalWidth));
            }
            if (nextPhraseStartX != Integer.MAX_VALUE && segmentEnd > nextPhraseStartX - 4) {
                segmentEnd = Math.max(segmentStart + 10, nextPhraseStartX - 4);
            }
        }

        collectValidInnerMarks(sepList, segmentStart, segmentEnd, editingItem, innerMarks);

        int len = editingItem.text.length();
        int safeIdx = Math.max(0, Math.min(cursorIndex, len));
        double cursorScreenX = computeCursorXForSegments(fm, editingItem, offsetX, segmentStart, segmentEnd, innerMarks, safeIdx, cursorRightSide, targetTextHeight, this.activeSegmentEndMarkX);

        if (cursorScreenX >= -50 && cursorScreenX <= panelWidth + 50) {
            int cursorTop = bandBaselineY - (int) Math.round(fm.getAscent() * scaleY);
            int cursorBottom = bandBaselineY + 2;
            int cursorH = Math.max(12, cursorBottom - cursorTop);
            double cursorW = 3.0;
            double drawX = cursorScreenX - cursorW / 2.0;

            // Déterminer dans quel segment se trouve le caret pour orienter la flèche cyan (◄ ou ►)
            int activeSegIdx = -1;
            if (!innerMarks.isEmpty()) {
                int pIdx = 0;
                for (int mi = 0; mi < innerMarks.size(); mi++) {
                    SeparatorMark m = innerMarks.get(mi);
                    int idx = Math.max(pIdx, Math.min(len, m.splitIndex >= 0 ? m.splitIndex : 0));
                    if (this.activeSegmentEndMarkX != Integer.MAX_VALUE && Math.abs(m.x - this.activeSegmentEndMarkX) <= 6) {
                        if (safeIdx >= pIdx && safeIdx <= idx) {
                            activeSegIdx = mi;
                        }
                        break;
                    }
                    pIdx = idx;
                }
                if (activeSegIdx == -1 && this.activeSegmentEndMarkX == Integer.MAX_VALUE) {
                    activeSegIdx = innerMarks.size();
                }
                if (activeSegIdx == -1) {
                    for (int mi = 0; mi < innerMarks.size(); mi++) {
                        SeparatorMark m = innerMarks.get(mi);
                        if (safeIdx < m.splitIndex || (safeIdx == m.splitIndex && !cursorRightSide)) {
                            activeSegIdx = mi;
                            break;
                        }
                    }
                    if (activeSegIdx == -1) activeSegIdx = innerMarks.size();
                }
            } else {
                activeSegIdx = 0;
            }

            boolean isNearSeparator = false;
            boolean pointRight = cursorRightSide;
            if (!innerMarks.isEmpty()) {
                int segStartChar = (activeSegIdx == 0) ? 0 : innerMarks.get(activeSegIdx - 1).splitIndex;
                int segEndChar = (activeSegIdx == innerMarks.size()) ? len : innerMarks.get(activeSegIdx).splitIndex;
                if (segStartChar == segEndChar) {
                    // Segment vide : le curseur est au début de ce segment (prêt à écrire vers la droite ►)
                    isNearSeparator = true;
                    pointRight = true;
                } else if (activeSegIdx < innerMarks.size() && safeIdx == segEndChar) {
                    isNearSeparator = true;
                    pointRight = false; // Côté gauche du séparateur de droite (◄)
                } else if (activeSegIdx > 0 && safeIdx == segStartChar) {
                    isNearSeparator = true;
                    pointRight = true;  // Côté droit du séparateur de gauche (►)
                } else if (activeSegIdx == 0 && safeIdx == 0) {
                    isNearSeparator = true;
                    pointRight = true;
                } else if (activeSegIdx == innerMarks.size() && safeIdx == len && len > 0) {
                    isNearSeparator = true;
                    pointRight = false;
                }
            } else {
                if (safeIdx == 0) {
                    isNearSeparator = true;
                    pointRight = true;
                } else if (safeIdx == len && len > 0) {
                    isNearSeparator = true;
                    pointRight = false;
                }
            }

            // Dessin du caret blanc haute visibilité (jamais recouvert par un séparateur ni teinté en jaune)
            g2.setColor(Color.WHITE);
            g2.fill(new Rectangle2D.Double(drawX, cursorTop, cursorW, cursorH));
            g2.setColor(new Color(20, 20, 20, 230));
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new Rectangle2D.Double(drawX, cursorTop, cursorW, cursorH));

            // Indicateur visuel d'orientation (Gauche / Droite du signe)
            if (isNearSeparator) {
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                Color dirColor = new Color(0, 225, 255);
                g2.setColor(dirColor);
                int midY = (cursorTop + cursorBottom) / 2;
                if (!pointRight) {
                    g2.drawLine((int) Math.round(drawX), cursorTop, (int) Math.round(drawX - 4), cursorTop);
                    g2.drawLine((int) Math.round(drawX), cursorBottom, (int) Math.round(drawX - 4), cursorBottom);
                    int[] arrowX = { (int) Math.round(drawX - 6), (int) Math.round(drawX - 1), (int) Math.round(drawX - 1) };
                    int[] arrowY = { midY, midY - 3, midY + 3 };
                    g2.fillPolygon(arrowX, arrowY, 3);
                } else {
                    g2.drawLine((int) Math.round(drawX + cursorW), cursorTop, (int) Math.round(drawX + cursorW + 4), cursorTop);
                    g2.drawLine((int) Math.round(drawX + cursorW), cursorBottom, (int) Math.round(drawX + cursorW + 4), cursorBottom);
                    int[] arrowX = { (int) Math.round(drawX + cursorW + 6), (int) Math.round(drawX + cursorW + 1), (int) Math.round(drawX + cursorW + 1) };
                    int[] arrowY = { midY, midY - 3, midY + 3 };
                    g2.fillPolygon(arrowX, arrowY, 3);
                }
            }
        }
    }

    private void drawSeparators(Graphics2D g2, Map<Integer, ArrayList<SeparatorMark>> bandSeparators, ArrayList<TextItem> texts, int bandHeight, double offsetX, int panelWidth) {
        float strokeW = Math.max(2f, (float) (bandHeight * 0.035f));
        g2.setStroke(new BasicStroke(strokeW));
        double triSize = Math.max(8.0, bandHeight * 0.15);
        double dotR = Math.max(3.0, bandHeight * 0.06);

        for (int band : bandSeparators.keySet()) {
            ArrayList<SeparatorMark> list = bandSeparators.get(band);
            if (list == null || list.isEmpty()) continue;
            int bandTop = band * bandHeight;
            int bandMid = bandTop + bandHeight / 2;

            for (int i = 0; i < list.size(); i++) {
                SeparatorMark mark = list.get(i);
                if (mark == null) continue;
                double sx = mark.x + offsetX;
                if (sx < -100 || sx > panelWidth + 100) {
                    continue; // Hors champ visible
                }
                switch (mark.type) {
                    case START -> {
                        // Anti-doublon : ne jamais afficher deux flèches vertes au même endroit
                        if (i > 0) {
                            SeparatorMark prevMark = list.get(i - 1);
                            if (prevMark.type == SeparatorMark.Type.START && Math.abs(prevMark.x - mark.x) <= 6) {
                                continue;
                            }
                        }
                        // Si un END de la phrase précédente est collé sur ce START, décaler légèrement le START à droite
                        boolean gluedWithPrevEnd = false;
                        for (int k = Math.max(0, i - 2); k <= Math.min(list.size() - 1, i + 2); k++) {
                            SeparatorMark other = list.get(k);
                            if (other != null && other.type == SeparatorMark.Type.END && Math.abs(other.x - mark.x) <= 6) {
                                gluedWithPrevEnd = true;
                                break;
                            }
                        }
                        double drawSx = gluedWithPrevEnd ? (sx + 3.5) : sx;
                        BufferedImage startImg = getStartImage();
                        int arrowH = Math.max(14, (int) Math.round(bandHeight * 0.38));
                        if (startImg != null) {
                            double aspect = (double) startImg.getWidth() / Math.max(1, startImg.getHeight());
                            double imgWidth = arrowH * aspect;
                            int imgX = (int) Math.round(drawSx - imgWidth / 2.0);
                            int imgY = bandTop + bandHeight - arrowH - 4;
                            g2.drawImage(startImg, imgX, imgY, (int) Math.round(imgWidth), arrowH, null);
                        } else {
                            int isx = (int) Math.round(drawSx);
                            int itriW = (int) Math.round(Math.max(8.0, arrowH * 0.60));
                            int baseY = bandTop + bandHeight - 4;
                            int[] px = { isx, isx - itriW / 2, isx + itriW / 2 };
                            int[] py = { baseY - arrowH, baseY, baseY };
                            g2.setColor(new Color(80, 220, 120));
                            g2.fillPolygon(px, py, 3);
                        }
                    }
                    case END -> {
                        // Anti-doublon : ne jamais afficher deux flèches rouges au même endroit
                        if (i > 0) {
                            SeparatorMark prevMark = list.get(i - 1);
                            if (prevMark.type == SeparatorMark.Type.END && Math.abs(prevMark.x - mark.x) <= 6) {
                                continue;
                            }
                        }
                        // Si ce END est collé sur le START de la phrase suivante, décaler légèrement le END à gauche
                        boolean gluedWithNextStart = false;
                        for (int k = Math.max(0, i - 2); k <= Math.min(list.size() - 1, i + 2); k++) {
                            SeparatorMark other = list.get(k);
                            if (other != null && other.type == SeparatorMark.Type.START && Math.abs(other.x - mark.x) <= 6) {
                                gluedWithNextStart = true;
                                break;
                            }
                        }
                        double drawSx = gluedWithNextStart ? (sx - 3.5) : sx;
                        BufferedImage endImg = getEndImage();
                        int arrowH = Math.max(14, (int) Math.round(bandHeight * 0.38));
                        if (endImg != null) {
                            double aspect = (double) endImg.getWidth() / Math.max(1, endImg.getHeight());
                            double imgWidth = arrowH * aspect;
                            int imgX = (int) Math.round(drawSx - imgWidth / 2.0);
                            int imgY = bandTop + bandHeight - arrowH - 4;
                            g2.drawImage(endImg, imgX, imgY, (int) Math.round(imgWidth), arrowH, null);
                        } else {
                            int isx = (int) Math.round(drawSx);
                            int itriW = (int) Math.round(Math.max(8.0, arrowH * 0.60));
                            int baseY = bandTop + bandHeight - 4;
                            int[] px = { isx, isx - itriW / 2, isx + itriW / 2 };
                            int[] py = { baseY - arrowH, baseY, baseY };
                            g2.setColor(new Color(255, 60, 60));
                            g2.fillPolygon(px, py, 3);
                        }
                    }
                    case INNER -> {
                        BufferedImage sepImg = getSepImage();
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
                        if (sepImg != null) {
                            double imgHeight = Math.max(14.0, bandHeight * 0.60);
                            double aspect = (double) sepImg.getWidth() / Math.max(1, sepImg.getHeight());
                            double imgWidth = imgHeight * aspect;
                            int imgX = (int) Math.round(sx - imgWidth / 2.0);
                            int imgY = bandTop + bandHeight - 7;
                            g2.drawImage(sepImg, imgX, imgY, (int) Math.round(imgWidth),
                                    (int) Math.round(imgHeight), null);
                        }
                        

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
    private static BufferedImage sepImage;
    private static boolean sepImageLoaded = false;

    private static BufferedImage getSepImage() {
        if (sepImageLoaded) return sepImage;
        try {
            BufferedImage raw = null;
            java.net.URL url = TimelineRenderer.class.getResource("/images/Sep.png");
            if (url != null) {
                raw = ImageIO.read(url);
            } else {
                File f = new File("src/images/Sep.png");
                if (f.exists()) {
                    raw = ImageIO.read(f);
                } else {
                    f = new File("images/Sep.png");
                    if (f.exists()) {
                        raw = ImageIO.read(f);
                    }
                }
            }
            if (raw != null) {
                sepImage = raw;
            }
        } catch (Exception e) {
            sepImage = null;
        }
        sepImageLoaded = true;
        return sepImage;
    }

    /**
     * Applique une incrustation couleur (Chroma Keying) pour transformer le fond blanc
     * d'une icône en transparence Alpha totale (canal Alpha à 0x00).
     * 
     * Fonctionnement binaire des pixels ARGB 32 bits (DirectColorModel) :
     * - bits 24..31 : canal Alpha (opacité de 0 à 255)
     * - bits 16..23 : canal Rouge
     * - bits 8..15  : canal Vert
     * - bits 0..7   : canal Bleu
     * 
     * Si les trois composantes R, G, B dépassent le seuil de 235 (blanc quasi pur),
     * le pixel devient totalement transparent (0x00000000), garantissant une intégration
     * parfaite sur n'importe quel arrière-plan ou texture personnalisée.
     */
    private static BufferedImage makeWhiteTransparent(BufferedImage image) {
        if (image == null) return null;
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage transparentImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int minX = width, maxX = -1, minY = height, maxY = -1;
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
                } else if (alpha > 20) {
                    transparentImage.setRGB(x, y, rgb);
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                } else {
                    transparentImage.setRGB(x, y, 0x00000000);
                }
            }
        }
        if (minX <= maxX && minY <= maxY) {
            int cropW = maxX - minX + 1;
            int cropH = maxY - minY + 1;
            BufferedImage cropped = new BufferedImage(cropW, cropH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = cropped.createGraphics();
            g.drawImage(transparentImage.getSubimage(minX, minY, cropW, cropH), 0, 0, null);
            g.dispose();
            return cropped;
        }
        return transparentImage;
    }
}