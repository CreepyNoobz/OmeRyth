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
    private final ArrayList<SeparatorMark> scratchInnerMarks = new ArrayList<>();

    /** Applique les paramètres de personnalisation visuelle (thème sombre, couleurs, polices). */
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

        app.services.SpellGrammarService.getInstance().clearVisibleIssues();

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
            drawSeparators(g2, bandSeparators, bandHeight, offsetX, panelWidth);
        }
        if (isEditing && editingItem != null && caretVisible) {
            drawEditingCaret(g2, editingItem, bandSeparators, bandHeight, offsetX, cursorIndex, cursorRightSide, panelWidth);
        }
        drawPlanMarkers(g2, planMarkers, offsetX, panelHeight, panelWidth);
        drawCursor(g2, panelHeight);
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

            // Bande actuellement sélectionnée : mise en valeur par un voile translucide
            if (selectedBand >= 0 && selectedBand < bandCount) {
                int y = selectedBand * bandHeight;
                Color sel = customization.timelineSelectedBand;
                g2.setColor(new Color(sel.getRed(), sel.getGreen(), sel.getBlue(), 90));
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
                           boolean separatorsVisible,
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

        // Prévisualisation interactive en temps réel pendant la frappe au clavier
        if (activeBand != -1 && isEditing && editingItem == null) {
            double drawX = textX + offsetX;
            int drawY = computeBaselineY(activeBand, bandHeight, textTopY, fm, targetTextHeight);
            String preview = caretVisible ? (currentInput + "|") : currentInput;
            drawScaledText(g2, fm, preview, drawX, drawY, null, targetTextHeight, false);
            g2.setColor(Color.WHITE);
        }

        int startTextIdx = Math.max(0, findTextIndex(texts, (int) Math.floor(-offsetX - 500)) - 1);
        for (int ti = startTextIdx; ti < texts.size(); ti++) {
            TextItem t = texts.get(ti);
            if (t == null) continue;
            if (t.text == null) t.text = "";
            if (t.x > panelWidth - offsetX + 500) {
                break; // Liste triée par X : les textes suivants sont tous hors écran à droite
            }

            int bandBaselineY = computeBaselineY(t.band, bandHeight, textTopY, fm, targetTextHeight);

            ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
            int leftSep = Integer.MIN_VALUE;
            int rightSep = Integer.MAX_VALUE;
            int nextPhraseStartX = Integer.MAX_VALUE;
            ArrayList<SeparatorMark> innerMarks = scratchInnerMarks;
            innerMarks.clear();

            if (sepList != null && !sepList.isEmpty()) {
                int searchIdx = findSepIndex(sepList, t.x);
                // Recherche vers la gauche du repère START le plus proche <= t.x
                int maxI = Math.min(searchIdx + 1, sepList.size() - 1);
                for (int i = maxI; i >= 0; i--) {
                    SeparatorMark s = sepList.get(i);
                    if (s.x > t.x) continue;
                    if (s.isStartBoundary()) {
                        leftSep = s.x;
                        break;
                    }
                    if (s.isEndBoundary() && s.x < t.x) {
                        // Un repère END situé STRICTEMENT à gauche appartient à une réplique antérieure :
                        // On ne doit jamais traverser un END vers la gauche pour voler un START antérieur !
                        break;
                    }
                }
                // Recherche vers la droite du repère END le plus proche > t.x
                for (int i = Math.max(0, searchIdx - 1); i < sepList.size(); i++) {
                    SeparatorMark s = sepList.get(i);
                    if (s.x <= t.x) continue; // Un repère de fin de réplique doit être STRICTEMENT après le début
                    if (s.isEndBoundary()) {
                        rightSep = s.x;
                        break;
                    }
                    if (s.isStartBoundary()) {
                        nextPhraseStartX = s.x;
                        break; // Une nouvelle réplique commence à droite : ne jamais traverser un START !
                    }
                }
            }

            int segmentStart = (leftSep != Integer.MIN_VALUE) ? leftSep : t.x;
            int segmentEnd;

            double textWidth = fm.stringWidth(t.text);
            int naturalWidth = Math.max(100, (int) Math.round(textWidth * 1.25));

            if (rightSep != Integer.MAX_VALUE) {
                segmentEnd = Math.max(segmentStart + 10, rightSep);
            } else {
                segmentEnd = segmentStart + naturalWidth;
                if (nextPhraseStartX != Integer.MAX_VALUE && segmentEnd > nextPhraseStartX - 5) {
                    segmentEnd = Math.max(segmentStart + 10, nextPhraseStartX - 5);
                }
            }

            // Viewport Culling Mathématiquement Exact & Optimal (supporte des timelines de 7h+) :
            // Un texte n'est ignoré que si son extrémité droite est complètement sortie à gauche (< -300px),
            // ou si son début n'a pas encore atteint l'écran (> panelWidth + 300px).
            double screenStart = segmentStart + offsetX;
            double screenEnd = segmentEnd + offsetX;
            if (screenEnd < -300 || screenStart > panelWidth + 300) {
                continue;
            }

            // Collecte des repères syllabiques internes (INNER) situés strictement entre START et END
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

            // Badge du personnage (rôle) : pastille rectangulaire positionnée à GAUCHE du repère START
            if (t.role != null && t.role.name != null && !t.role.name.isEmpty()) {
                Color roleColor = (t.role.color != null) ? t.role.color : new Color(0, 120, 215);

                Font oldFont = g2.getFont();
                float labelFontSize = Math.max(9.5f, Math.min(13.5f, (float) (bandHeight * 0.16f)));
                if (cachedLabelFont == null || cachedLabelBandHeight != bandHeight) {
                    cachedLabelFont = textFont.deriveFont(Font.BOLD, labelFontSize);
                    cachedLabelFontMetrics = g2.getFontMetrics(cachedLabelFont);
                    cachedLabelBandHeight = bandHeight;
                }
                Font labelFont = cachedLabelFont;
                FontMetrics lfm = cachedLabelFontMetrics;
                g2.setFont(labelFont);

                int padX = Math.max(4, Math.round(labelFontSize * 0.38f));
                int padY = Math.max(1, Math.round(labelFontSize * 0.12f));
                int lw = lfm.stringWidth(t.role.name) + padX * 2;
                int lh = lfm.getHeight() + padY;

                int badgeTop = t.band * bandHeight + 2;
                int labelY = badgeTop + padY + lfm.getAscent() - 1;

                // Positionnement sur le côté GAUCHE du repère START
                double labelX = segmentStart + offsetX - lw - 2;

                // Fond avec la couleur du rôle, bords rectangulaires nets (pas d'arrondi)
                g2.setColor(roleColor);
                g2.fillRect((int) Math.round(labelX), badgeTop, lw, lh);

                // Contour fin
                g2.setColor(isDarkColor(roleColor) ? new Color(255, 255, 255, 160) : new Color(0, 0, 0, 80));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRect((int) Math.round(labelX), badgeTop, lw, lh);

                // Couleur du texte : noir par défaut, blanc si le rôle est noir/sombre
                Color textColor = isDarkColor(roleColor) ? Color.WHITE : Color.BLACK;
                g2.setColor(textColor);
                g2.drawString(t.role.name, (float) (labelX + padX), (float) labelY);
                g2.setFont(oldFont);
            }

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
                    double leftPad = (leftSep != Integer.MIN_VALUE) ? 6.0 : 2.0;
                    double rightPad = (rightSep != Integer.MAX_VALUE) ? 8.0 : 2.0;
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
                        if (!segText.isEmpty()) {
                            double leftPad = (prevIdx == 0 && leftSep != Integer.MIN_VALUE) ? 6.0 : 2.0;
                            double rightPad = 4.0;
                            double segStartX = segStart + offsetX + leftPad;
                            double segEndX = segEnd + offsetX - rightPad;
                            double width = Math.max(2.0, segEndX - segStartX);

                            drawScaledText(g2, fm, segText, segStartX, bandBaselineY, width, targetTextHeight, false);
                            anyTextDrawn = true;
                        }

                        prevX = mark.x;
                        prevIdx = idx;
                    }

                    String remaining = t.text.substring(Math.min(prevIdx, len));
                    if (!remaining.isEmpty()) {
                        double leftPad = 2.0;
                        double rightPad = (rightSep != Integer.MAX_VALUE) ? 8.0 : 2.0;
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
                    double selStartX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, selStart, false);
                    double selEndX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, selEnd, false);
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
            // Condition explicite : visible uniquement si les signes/séparateurs sont affichés
            // Temporisation 1 seconde : si l'utilisateur est activement en train de taper ce texte, on masque les erreurs pendant la frappe
            if (separatorsVisible && t.text != null && !t.text.isEmpty()) {
                boolean isActivelyTypingThisItem = isEditing && (t == editingItem) && (System.currentTimeMillis() - lastTypingTimestamp < 1000);
                if (!isActivelyTypingThisItem) {
                    List<app.services.SpellGrammarService.SpellCheckIssue> issues =
                            app.services.SpellGrammarService.getInstance().checkText(t.text);
                    if (issues != null && !issues.isEmpty()) {
                        for (app.services.SpellGrammarService.SpellCheckIssue issue : issues) {
                            int safeStart = Math.max(0, Math.min(issue.startIdx, t.text.length()));
                            int safeEnd = Math.max(safeStart, Math.min(issue.endIdx, t.text.length()));
                            double startScreenX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, safeStart, false);
                            double endScreenX = computeCursorXForSegments(fm, t, offsetX, segmentStart, segmentEnd, innerMarks, safeEnd, false);

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
        double scaleY = (double) targetTextHeight / Math.max(1, fm.getHeight());
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
     * 
     * Étant donné que les repères sont triés par coordonnée temporelle X croissante :
     * - Au lieu de parcourir séquentiellement des milliers de repères (ce qui ralentirait le rendu 60 FPS),
     *   on divise l'intervalle par deux à chaque étape.
     * - Retourne l'index exact ou le point d'insertion du repère le plus proche.
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
            } else if (midVal > targetX) {
                high = mid - 1;
            } else {
                return mid;
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

    /**
     * Dessine une chaîne de caractères étirée élastiquement via une transformation affine 2D (AffineTransform).
     * 
     * Spécificité métier de la bande rythmo :
     * Le texte n'est pas simplement rendu à taille fixe : il doit occuper précisément
     * l'intervalle temporel alloué au dialogue ou au mot.
     * 
     * - scaleY adapte la hauteur des lettres pour occuper harmonieusement la hauteur de la piste.
     * - scaleX étire ou compresse la largeur du texte (ratio targetWidth / sourceWidth)
     *   tout en préservant des limites de sécurité (min 0.05x, max 15x) pour éviter les distorsions extrêmes.
     * - translate(anchorX, baselineY) positionne l'origine directement sur la ligne de base.
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
        double sourceWidth = fm.stringWidth(text);
        if (sourceWidth <= 0) return;
        try {
            java.awt.geom.Rectangle2D bounds = fm.getFont().getStringBounds(text, g2.getFontRenderContext());
            if (bounds != null && bounds.getWidth() > sourceWidth) {
                sourceWidth = bounds.getWidth();
            }
        } catch (Throwable ignored) {}

        double scaleY = (double) targetTextHeight / Math.max(1, fm.getHeight());
        double scaleX = scaleY;
        if (targetWidth != null && targetWidth > 0) {
            scaleX = targetWidth / sourceWidth;
            if (Double.isNaN(scaleX) || Double.isInfinite(scaleX) || scaleX <= 0) {
                scaleX = scaleY;
            } else {
                scaleX = Math.max(0.02, Math.min(scaleX, 15.0));
            }
        }

        AffineTransform old = g2.getTransform();
        g2.translate(anchorX, (double) baselineY);
        g2.scale(scaleX, scaleY);
        float drawX = anchoredRight ? (float) (-sourceWidth) : 0f;
        g2.drawString(text, drawX, 0f);
        g2.setTransform(old);
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
        double prevX = startWorldX + offsetX;
        int prevIdx = 0;
        int len = t.text.length();

        if (cursorIdx == 0 && !cursorRightSide) {
            return (startWorldX + offsetX) - 4.0;
        }

        for (int mi = 0; mi < innerMarks.size(); mi++) {
            SeparatorMark mark = innerMarks.get(mi);
            double segStart = prevX;
            double segEnd = mark.x + offsetX;
            int idx = mark.splitIndex;
            if (idx < 0) {
                double ratio = (double) (mark.x - startWorldX) / Math.max(1, endWorldX - startWorldX);
                idx = (int) Math.round(ratio * len);
            }
            if (idx < prevIdx) idx = prevIdx;
            if (idx > len) idx = len;

            boolean isTargetSegment = (mark.x == activeSegmentEndMarkX);
            if (isTargetSegment || cursorIdx < idx || (cursorIdx == idx && !cursorRightSide)) {
                if (cursorIdx == idx && !cursorRightSide) {
                    return segEnd - 4.0; // Côté gauche du signe
                }
                if (cursorIdx == prevIdx && cursorRightSide) {
                    return segStart + 2.0; // Côté droit du signe précédent
                }
                String segText = t.text.substring(prevIdx, idx);
                double leftPad = (prevIdx == 0) ? 6.0 : 2.0;
                double rightPad = 4.0;
                double usableStart = segStart + leftPad;
                double usableEnd = segEnd - rightPad;
                double segWidth = Math.max(2.0, usableEnd - usableStart);
                if (segText.isEmpty()) return usableStart;
                double total = fm.stringWidth(segText);
                if (total <= 0) return usableStart;
                String before = segText.substring(0, Math.max(0, Math.min(cursorIdx - prevIdx, segText.length())));
                double bw = fm.stringWidth(before);
                return usableStart + (bw / total) * segWidth;
            }

            prevX = mark.x + offsetX;
            prevIdx = idx;
        }

        double segStart = prevX;
        double segEnd = endWorldX + offsetX;
        if (cursorIdx == len && cursorRightSide) {
            return segEnd + 4.0; // Côté droit du signe de fin
        }
        if (cursorIdx == prevIdx && cursorRightSide && (!innerMarks.isEmpty() || prevIdx > 0)) {
            return segStart + 2.0; // Côté droit du dernier signe interne
        }
        String segText = t.text.substring(Math.min(prevIdx, len));
        double leftPad = (prevIdx == 0) ? 6.0 : 2.0;
        double rightPad = 8.0;
        double usableStart = segStart + leftPad;
        double usableEnd = segEnd - rightPad;
        double segWidth = Math.max(2.0, usableEnd - usableStart);
        if (segText.isEmpty()) return usableStart;
        double total = fm.stringWidth(segText);
        if (total <= 0) return usableStart;
        int localIdx = Math.max(0, Math.min(cursorIdx - prevIdx, segText.length()));
        double bw = fm.stringWidth(segText.substring(0, localIdx));
        return usableStart + (bw / total) * segWidth;
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
        int bandBaselineY = computeBaselineY(editingItem.band, bandHeight, textTopY, fm, targetTextHeight);

        ArrayList<SeparatorMark> sepList = bandSeparators.get(editingItem.band);
        int leftSep = Integer.MIN_VALUE;
        int rightSep = Integer.MAX_VALUE;
        int nextPhraseStartX = Integer.MAX_VALUE;
        ArrayList<SeparatorMark> innerMarks = new ArrayList<>();

        if (sepList != null && !sepList.isEmpty()) {
            int searchIdx = findSepIndex(sepList, editingItem.x);
            int maxI = Math.min(searchIdx + 1, sepList.size() - 1);
            for (int i = maxI; i >= 0; i--) {
                SeparatorMark s = sepList.get(i);
                if (s.x > editingItem.x) continue;
                if (s.isStartBoundary()) { leftSep = s.x; break; }
                if (s.isEndBoundary() && s.x < editingItem.x) break;
            }
            for (int i = Math.max(0, searchIdx - 1); i < sepList.size(); i++) {
                SeparatorMark s = sepList.get(i);
                if (s.x <= editingItem.x) continue;
                if (s.isEndBoundary()) { rightSep = s.x; break; }
                if (s.isStartBoundary()) { nextPhraseStartX = s.x; break; }
            }
        }

        int segmentStart = (leftSep != Integer.MIN_VALUE) ? leftSep : editingItem.x;
        int segmentEnd;
        double textWidth = fm.stringWidth(editingItem.text);
        int naturalWidth = Math.max(100, (int) Math.round(textWidth * 1.25));
        if (rightSep != Integer.MAX_VALUE) {
            segmentEnd = Math.max(segmentStart + 10, rightSep);
        } else {
            segmentEnd = segmentStart + naturalWidth;
            if (nextPhraseStartX != Integer.MAX_VALUE && segmentEnd > nextPhraseStartX - 5) {
                segmentEnd = Math.max(segmentStart + 10, nextPhraseStartX - 5);
            }
        }

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

        int safeIdx = Math.max(0, Math.min(cursorIndex, editingItem.text.length()));
        double cursorScreenX = computeCursorXForSegments(fm, editingItem, offsetX, segmentStart, segmentEnd, innerMarks, safeIdx, cursorRightSide);
        if (editingItem.text.isEmpty()) {
            cursorScreenX += 12;
        }

        if (cursorScreenX >= -50 && cursorScreenX <= panelWidth + 50) {
            int cursorTop = bandBaselineY - (int) Math.round(fm.getAscent() * ((double) targetTextHeight / Math.max(1, fm.getHeight())));
            int cursorBottom = bandBaselineY + 2;
            int cursorH = Math.max(12, cursorBottom - cursorTop);
            double cursorW = 3.0;
            double drawX = cursorScreenX - cursorW / 2.0;

            boolean isNearSeparator = false;
            for (SeparatorMark m : innerMarks) {
                if (m.splitIndex == safeIdx) {
                    isNearSeparator = true;
                    break;
                }
            }
            if (safeIdx == 0 || safeIdx == editingItem.text.length()) {
                isNearSeparator = true;
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
                if (!cursorRightSide) {
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
                            int imgX = (int) Math.round(sx - imgWidth / 2.0);
                            int imgY = bandTop + bandHeight + 3;
                            g2.drawImage(startImg, imgX, imgY, (int) Math.round(imgWidth), (int) Math.round(imgHeight), null);
                        } else {
                            int isx = (int) Math.round(sx);
                            int itriW = (int) Math.round(Math.max(8.0, bandHeight * 0.15));
                            int itriH = (int) Math.round(Math.max(10.0, bandHeight * 0.25));
                            int baseY = bandTop + bandHeight + 3;
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
                            int imgX = (int) Math.round(sx - imgWidth / 2.0);
                            int imgY = bandTop + bandHeight + 3;
                            g2.drawImage(endImg, imgX, imgY, (int) Math.round(imgWidth), (int) Math.round(imgHeight), null);
                        } else {
                            int isx = (int) Math.round(sx);
                            int itriW = (int) Math.round(Math.max(8.0, bandHeight * 0.15));
                            int itriH = (int) Math.round(Math.max(10.0, bandHeight * 0.25));
                            int baseY = bandTop + bandHeight + 3;
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