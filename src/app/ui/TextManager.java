package app.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Gestionnaire des textes et des marqueurs (separators) pour la timeline.
 *
 * Responsable du stockage des `TextItem` et `SeparatorMark`, des opérations
 * d'édition (startPhrase/endPhrase/typeChar), et des utilitaires pour calculer
 * les positions/cursors côté "world" (coordonnées timeline).
 */
public class TextManager {

    private final ArrayList<TextItem> texts = new ArrayList<>();
    private final Map<Integer, ArrayList<SeparatorMark>> bandSeparators = new HashMap<>();
    private final ArrayList<Integer> planMarkers = new ArrayList<>();

    private FontMetrics fontMetrics = null;
    private static FontMetrics fallbackFontMetrics = null;

    private boolean isEditing = false;
    private TextItem selectedText = null;
    private int textX = 0;
    private String currentInput = "";
    private int activeBand = -1;
    private int cursorIndex = 0;
    private boolean cursorRightSide = false;
    private int activeSegmentEndMarkX = Integer.MAX_VALUE;
    private int activeSegmentIndex = -1;
    private int selectionStart = -1;
    private int selectionEnd = -1;

    // ==================== GETTERS & SETTERS ====================
    public void setFontMetrics(FontMetrics fontMetrics) {
        this.fontMetrics = fontMetrics;
    }

    public FontMetrics getFontMetrics() {
        return this.fontMetrics;
    }

    public FontMetrics getEffectiveFontMetrics() {
        if (fontMetrics != null) {
            return fontMetrics;
        }
        if (fallbackFontMetrics == null) {
            try {
                BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                Font f = new Font("Arial", Font.BOLD, 50);
                fallbackFontMetrics = dummy.getGraphics().getFontMetrics(f);
            } catch (Throwable ignored) {}
        }
        return fallbackFontMetrics;
    }

    public ArrayList<TextItem> getTexts() { return texts; }
    public Map<Integer, ArrayList<SeparatorMark>> getBandSeparators() { return bandSeparators; }
    public ArrayList<Integer> getPlanMarkers() { return planMarkers; }
    public boolean isEditing() { return isEditing; }
    public int getTextX() { return textX; }
    public String getCurrentInput() { return currentInput; }
    public int getActiveBand() { return activeBand; }
    public int getCursorIndex() { return cursorIndex; }
    public boolean isCursorRightSide() { return cursorRightSide; }
    public int getActiveSegmentEndMarkX() { return activeSegmentEndMarkX; }
    public TextItem getEditingItem() { return selectedText; }

    public boolean hasSelection() {
        return selectionStart != -1 && selectionEnd != -1 && selectionStart != selectionEnd;
    }
    public int getSelectionStart() {
        return Math.min(selectionStart, selectionEnd);
    }
    public int getSelectionEnd() {
        return Math.max(selectionStart, selectionEnd);
    }
    public void setSelection(int start, int end) {
        if (selectedText != null && selectedText.text != null && (currentInput == null || !currentInput.equals(selectedText.text))) {
            currentInput = selectedText.text;
        }
        int len = (currentInput != null) ? currentInput.length() : 0;
        this.selectionStart = Math.max(0, Math.min(start, len));
        this.selectionEnd = Math.max(0, Math.min(end, len));
    }
    public void selectAll() {
        int len = (currentInput != null) ? currentInput.length() : 0;
        this.selectionStart = 0;
        this.selectionEnd = len;
        this.cursorIndex = len;
        this.cursorRightSide = true;
        this.activeSegmentIndex = -1;
        this.activeSegmentEndMarkX = Integer.MAX_VALUE;
    }
    public void selectActiveSegmentText() {
        int len = (currentInput != null) ? currentInput.length() : 0;
        ArrayList<SeparatorMark> inner = getInnerMarksForSelectedPhrase();
        if (inner == null || inner.isEmpty()) {
            selectAll();
            this.activeSegmentIndex = 0;
            return;
        }
        int segIdx = getActiveSegmentIndex(inner, len);
        int segStart = (segIdx == 0) ? 0 : inner.get(segIdx - 1).splitIndex;
        int segEnd = (segIdx == inner.size()) ? len : inner.get(segIdx).splitIndex;
        segStart = Math.max(0, Math.min(len, segStart));
        segEnd = Math.max(segStart, Math.min(len, segEnd));
        this.selectionStart = segStart;
        this.selectionEnd = segEnd;
        this.cursorIndex = segEnd;
        this.activeSegmentIndex = segIdx;
        this.activeSegmentEndMarkX = (segIdx < inner.size()) ? inner.get(segIdx).x : Integer.MAX_VALUE;
        this.cursorRightSide = (segStart == segEnd) ? (segIdx > 0) : (segIdx == inner.size());
    }
    public void clearSelection() {
        this.selectionStart = -1;
        this.selectionEnd = -1;
    }
    public String getSelectedText() {
        if (!hasSelection() || currentInput == null) return "";
        int s = getSelectionStart();
        int e = getSelectionEnd();
        if (s < 0 || e > currentInput.length() || s >= e) return "";
        return currentInput.substring(s, e);
    }
    public void setCursorIndex(int idx) {
        this.cursorIndex = clampCursorIndex(idx);
        updateActiveSegmentFromCursor();
    }
    public boolean deleteSelection() {
        if (!hasSelection() || currentInput == null) return false;
        int start = getSelectionStart();
        int end = getSelectionEnd();
        int count = end - start;
        if (count <= 0) {
            clearSelection();
            return false;
        }
        ArrayList<SeparatorMark> innerBefore = getInnerMarksForSelectedPhrase();
        int savedSegIdx = (innerBefore != null && !innerBefore.isEmpty())
                ? getActiveSegmentIndex(innerBefore, currentInput.length())
                : 0;
        currentInput = currentInput.substring(0, start) + currentInput.substring(end);
        shiftInnerSplitIndicesOnDelete(start, count);
        cursorIndex = clampCursorIndex(start);
        clearSelection();
        if (selectedText != null) {
            selectedText.text = currentInput;
        }
        ArrayList<SeparatorMark> innerAfter = getInnerMarksForSelectedPhrase();
        if (innerAfter != null && !innerAfter.isEmpty()) {
            activeSegmentIndex = Math.max(0, Math.min(innerAfter.size(), savedSegIdx));
            int segStart = (activeSegmentIndex == 0) ? 0 : innerAfter.get(activeSegmentIndex - 1).splitIndex;
            cursorRightSide = (cursorIndex == segStart && activeSegmentIndex > 0);
            activeSegmentEndMarkX = (activeSegmentIndex < innerAfter.size()) ? innerAfter.get(activeSegmentIndex).x : Integer.MAX_VALUE;
        } else {
            activeSegmentIndex = 0;
            cursorRightSide = false;
            activeSegmentEndMarkX = Integer.MAX_VALUE;
        }
        return true;
    }

    // =========================================================================
    // GESTION DES TEXTES ET RECALAGE TEMPOREL
    // =========================================================================

    /** Ajoute une nouvelle réplique textuelle à la coordonnée monde X sur la bande spécifiée. */
    public void addText(String text, int x, int band) {
        texts.add(new TextItem(text, x, band));
    }

    /** Ajoute une instance existante de TextItem au gestionnaire. */
    public void addTextItem(TextItem item) {
        texts.add(item);
    }

    /** Trie l'ensemble des répliques par ordre chronologique croissant de leur coordonnée X. */
    public void sortTexts() {
        texts.sort(Comparator.comparingInt(t -> t.x));
    }

    /**
     * Recale l'ensemble des coordonnées temporelles autour d'un point d'ancrage fixe (utilisé lors du zoom).
     * 
     * Formule de dilatation affine :
     *   nouvellePos = ancre + (anciennePos - ancre) * ratio
     * 
     * Cette transformation préserve scrupuleusement la position relative de chaque mot,
     * séparateur et repère de plan par rapport au centre de vue de l'utilisateur.
     */
    public void scaleTimelineX(int anchorX, double ratio) {
        if (ratio <= 0 || Math.abs(ratio - 1.0) < 1e-9) return;

        for (TextItem t : texts) {
            t.x = scaleAroundAnchor(t.x, anchorX, ratio);
        }

        for (ArrayList<SeparatorMark> list : bandSeparators.values()) {
            for (SeparatorMark mark : list) {
                mark.x = scaleAroundAnchor(mark.x, anchorX, ratio);
            }
            sortSeparators(list);
        }

        for (int i = 0; i < planMarkers.size(); i++) {
            planMarkers.set(i, scaleAroundAnchor(planMarkers.get(i), anchorX, ratio));
        }

        if (isEditing) {
            textX = scaleAroundAnchor(textX, anchorX, ratio);
            if (activeSegmentEndMarkX != Integer.MAX_VALUE && activeSegmentEndMarkX != -1) {
                activeSegmentEndMarkX = scaleAroundAnchor(activeSegmentEndMarkX, anchorX, ratio);
            }
        }
    }

    private int scaleAroundAnchor(int value, int anchor, double ratio) {
        return (int) Math.round(anchor + (value - anchor) * ratio);
    }

    /**
     * Ajoute un séparateur syllabique interne (INNER) à la position X.
     * Si la position est à l'intérieur d'une réplique en cours d'édition,
     * l'index de découpe (splitIndex) est automatiquement initialisé à la position actuelle du curseur de frappe.
     */
    public boolean canAddSeparatorAt(int band, int x) {
        if (band < 0) return false;
        if (bandSeparators.containsKey(band)) {
            for (SeparatorMark sep : bandSeparators.get(band)) {
                if (sep != null && Math.abs(sep.x - x) <= 4) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Ajoute un séparateur syllabique interne (INNER) à la position X.
     * Si la position est à l'intérieur d'une réplique en cours d'édition,
     * l'index de découpe (splitIndex) est automatiquement initialisé à la position actuelle du curseur de frappe.
     */
    public static boolean isInnerOrLegacy(SeparatorMark.Type t) {
        return t == SeparatorMark.Type.INNER || t == SeparatorMark.Type.LEGACY;
    }

    public boolean addSeparator(int band, int x) {
        return addSeparatorAtCursorWithSign(band, x, SeparatorMark.SignType.DEFAULT);
    }

    public boolean addSeparator(int band, int x, SeparatorMark.SignType signType) {
        return addSeparatorAtCursorWithSign(band, x, signType);
    }

    public boolean addSeparatorAtCursorWithSign(int band, int x, SeparatorMark.SignType signType) {
        if (!canAddSeparatorAt(band, x)) {
            return false;
        }

        // 1. Si on est en train d'éditer une phrase sur cette bande et que x est dans son voisinage
        if (isEditing && activeBand == band && selectedText != null) {
            Integer leftBoundary = getLeftBoundary(band, selectedText.x);
            Integer rightBoundary = getRightBoundary(band, selectedText.x);
            Integer nextStart = getNextStartBoundary(band, selectedText.x);
            int startX = (leftBoundary != null) ? leftBoundary : selectedText.x;

            boolean inSelectedBounds = (x >= startX - 15)
                    && (rightBoundary == null || x <= rightBoundary + 15)
                    && (nextStart == null || x < nextStart);

            if (inSelectedBounds) {
                int targetX = Math.max(startX + 8, x);
                if (rightBoundary != null) {
                    targetX = Math.min(rightBoundary - 8, targetX);
                } else if (nextStart != null) {
                    targetX = Math.min(nextStart - 8, targetX);
                }
                if (targetX <= startX + 6 || (rightBoundary != null && targetX >= rightBoundary - 6)) {
                    return false;
                }

                if (bandSeparators.containsKey(band)) {
                    for (SeparatorMark sep : bandSeparators.get(band)) {
                        if (sep == null) continue;
                        if (Math.abs(sep.x - targetX) <= 6) {
                            if (isInnerOrLegacy(sep.type) && signType != SeparatorMark.SignType.DEFAULT) {
                                sep.signType = signType;
                                return true;
                            }
                            return false; // Impossible d'empiler plusieurs séparateurs au même endroit
                        }
                    }
                }

                int len = currentInput != null ? currentInput.length() : 0;
                int safeCursor = Math.max(0, Math.min(cursorIndex, len));

                ArrayList<SeparatorMark> innerMarks = getInnerMarksForSelectedPhrase();
                SeparatorMark prevMark = null;
                SeparatorMark nextMark = null;
                for (SeparatorMark m : innerMarks) {
                    if (Math.abs(m.x - targetX) <= 6) {
                        if (signType != SeparatorMark.SignType.DEFAULT) {
                            m.signType = signType;
                            return true;
                        }
                        return false; // Doublon spatial strict interdit
                    }
                    if (m.x < targetX) {
                        prevMark = m;
                    } else if (m.x > targetX && nextMark == null) {
                        nextMark = m;
                    }
                }

                int minSplit = (prevMark != null && prevMark.splitIndex >= 0) ? Math.min(len, prevMark.splitIndex) : 0;
                int maxSplit = (nextMark != null && nextMark.splitIndex >= 0) ? Math.min(len, nextMark.splitIndex) : len;
                int splitIdx;
                if (prevMark == null && targetX <= startX + 20) {
                    // Séparateur placé tout à gauche juste après START : (début) : (séparateur) phrase
                    splitIdx = 0;
                } else if (nextMark == null && rightBoundary != null && targetX >= rightBoundary - 20) {
                    // Séparateur placé tout à droite juste avant END : phrase (séparateur) : (fin)
                    splitIdx = len;
                } else {
                    splitIdx = Math.max(minSplit, Math.min(maxSplit, safeCursor));
                }

                SeparatorMark addedMark = addSeparator(band, targetX, SeparatorMark.Type.INNER, splitIdx, signType);

                if (len == 0 && prevMark == null && targetX > startX + 20) {
                    // Phrase vide avec un séparateur placé au milieu/droite : on garde le curseur dans la partie gauche
                    cursorIndex = 0;
                    cursorRightSide = false;
                    activeSegmentIndex = 0;
                    activeSegmentEndMarkX = (addedMark != null) ? addedMark.x : targetX;
                } else {
                    // Place le curseur immédiatement à droite du nouveau séparateur (prêt à écrire la suite)
                    cursorIndex = splitIdx;
                    cursorRightSide = true;
                    activeSegmentIndex = -1;
                    activeSegmentEndMarkX = (nextMark != null) ? nextMark.x : Integer.MAX_VALUE;
                    updateActiveSegmentFromCursor();
                }
                return true;
            }
        }

        // 2. Si hors édition (ou clic/curseur dans une autre phrase de la bande)
        TextItem targetItem = null;
        for (TextItem t : texts) {
            if (t.band != band) continue;
            int[] bounds = getSegmentBounds(t);
            if (x >= bounds[0] - 15 && x <= bounds[1] + 15) {
                targetItem = t;
                break;
            }
        }
        if (targetItem == null) {
            targetItem = getOpenPhraseInBand(band);
        }
        if (targetItem != null) {
            int[] bounds = getSegmentBounds(targetItem);
            Integer rightBoundary = getRightBoundary(band, targetItem.x);
            Integer nextStart = getNextStartBoundary(band, targetItem.x);
            int targetX = Math.max(bounds[0] + 8, x);
            if (rightBoundary != null) {
                targetX = Math.min(rightBoundary - 8, targetX);
            } else if (nextStart != null) {
                targetX = Math.min(nextStart - 8, targetX);
            }
            if (targetX <= bounds[0] + 6 || (rightBoundary != null && targetX >= rightBoundary - 6)) {
                return false;
            }

            int len = (targetItem.text != null) ? targetItem.text.length() : 0;
            ArrayList<SeparatorMark> bandMarks = bandSeparators.get(band);
            SeparatorMark prevMark = null;
            SeparatorMark nextMark = null;
            if (bandMarks != null) {
                for (SeparatorMark sm : bandMarks) {
                    if (sm == null) continue;
                    if (Math.abs(sm.x - targetX) <= 6) {
                        if (isInnerOrLegacy(sm.type) && signType != SeparatorMark.SignType.DEFAULT) {
                            sm.signType = signType;
                            return true;
                        }
                        return false; // Interdire tout doublon spatial
                    }
                    if (sm.x <= bounds[0] || (rightBoundary != null && sm.x >= rightBoundary)) continue;
                    if (nextStart != null && sm.x >= nextStart) continue;
                    if (isInnerOrLegacy(sm.type)) {
                        if (sm.x < targetX) {
                            prevMark = sm;
                        } else if (sm.x > targetX && nextMark == null) {
                            nextMark = sm;
                        }
                    }
                }
            }

            int segStartX = (prevMark != null) ? prevMark.x : bounds[0];
            int segEndX = (nextMark != null) ? nextMark.x : Math.max(targetX + 20, bounds[1]);
            int minSplit = (prevMark != null && prevMark.splitIndex >= 0) ? Math.min(len, prevMark.splitIndex) : 0;
            int maxSplit = (nextMark != null && nextMark.splitIndex >= 0) ? Math.min(len, nextMark.splitIndex) : len;

            int splitIdx;
            if (len > 0 && maxSplit > minSplit) {
                if (prevMark == null && targetX <= bounds[0] + 20) {
                    splitIdx = 0;
                } else if (nextMark == null && ((rightBoundary != null && targetX >= rightBoundary - 20)
                        || (rightBoundary == null && targetX >= bounds[1] - 20))) {
                    splitIdx = len;
                } else {
                    double ratio = (double) (targetX - segStartX) / Math.max(1, segEndX - segStartX);
                    splitIdx = minSplit + (int) Math.round(ratio * (maxSplit - minSplit));
                    splitIdx = Math.max(minSplit, Math.min(maxSplit, splitIdx));
                }
            } else {
                splitIdx = minSplit;
            }

            addSeparator(band, targetX, SeparatorMark.Type.INNER, splitIdx, signType);
            return true;
        }

        // 3. Hors de toute phrase existante (repère libre sur la bande)
        if (bandSeparators.containsKey(band)) {
            for (SeparatorMark sep : bandSeparators.get(band)) {
                if (sep != null && Math.abs(sep.x - x) <= 6) {
                    if (isInnerOrLegacy(sep.type) && signType != SeparatorMark.SignType.DEFAULT) {
                        sep.signType = signType;
                        return true;
                    }
                    return false;
                }
            }
        }
        addSeparator(band, x, SeparatorMark.Type.INNER, -1, signType);
        return true;
    }

    /** Ajoute un séparateur du type spécifié (START, END, INNER, LEGACY) à la coordonnée X. */
    public void addSeparator(int band, int x, SeparatorMark.Type type) {
        addSeparator(band, x, type, -1, SeparatorMark.SignType.DEFAULT);
    }

    /** Ajoute un séparateur avec indice de découpe de chaîne spécifié. */
    public void addSeparator(int band, int x, SeparatorMark.Type type, int splitIndex) {
        addSeparator(band, x, type, splitIndex, SeparatorMark.SignType.DEFAULT);
    }

    /** Ajoute un repère complet avec type de signe labial (FVR, MPB, voyelle ouverte, etc.). */
    public SeparatorMark addSeparator(int band, int x, SeparatorMark.Type type, int splitIndex, SeparatorMark.SignType signType) {
        ArrayList<SeparatorMark> list = bandSeparators.computeIfAbsent(band, k -> new ArrayList<>());
        for (SeparatorMark mark : list) {
            // Le signe de départ est exclusivement la flèche verte (START) :
            // Ne jamais créer deux repères START, ni autoriser un repère INNER superposé au START.
            if (mark.type == SeparatorMark.Type.START && Math.abs(mark.x - x) <= 6) {
                if (type == SeparatorMark.Type.START) {
                    mark.x = x;
                    return mark;
                } else if (isInnerOrLegacy(type)) {
                    // Refuser la superposition d'un signe intérieur sur la flèche de départ
                    return mark;
                }
            }
            if (mark.type == SeparatorMark.Type.END && Math.abs(mark.x - x) <= 6) {
                if (type == SeparatorMark.Type.END) {
                    mark.x = x;
                    return mark;
                } else if (isInnerOrLegacy(type)) {
                    // Refuser la superposition d'un signe intérieur sur la fin de réplique
                    return mark;
                }
            }
            if (mark.type == type || (isInnerOrLegacy(mark.type) && isInnerOrLegacy(type))) {
                boolean distinctSplits = (mark.splitIndex >= 0 && splitIndex >= 0 && mark.splitIndex != splitIndex);
                int mergeTol = distinctSplits ? 2 : 4;
                if (Math.abs(mark.x - x) <= mergeTol) {
                    if (signType != SeparatorMark.SignType.DEFAULT) {
                        mark.signType = signType;
                    }
                    if (splitIndex >= 0 && !distinctSplits) {
                        mark.splitIndex = splitIndex;
                    }
                    return mark;
                }
            }
        }
        SeparatorMark sm = new SeparatorMark(x, type, splitIndex, signType);
        list.add(sm);
        sortSeparators(list);
        return sm;
    }

    public void addPlanMarker(int x) {
        if (!planMarkers.contains(x)) {
            planMarkers.add(x);
            Collections.sort(planMarkers);
        }
    }

    public boolean removePlanMarker(int x) {
        return planMarkers.remove(Integer.valueOf(x));
    }

    public void movePlanMarker(int oldX, int newX) {
        if (oldX == newX) return;
        if (planMarkers.remove(Integer.valueOf(oldX))) {
            addPlanMarker(newX);
        }
    }

    public SeparatorMark findSeparatorMarkAtScaled(int mouseX, int mouseY, int offsetX, int panelHeight, int bandCount, int tolerancePx) {
        int safePanelHeight = (panelHeight > 0) ? panelHeight : 100;
        int safeBandCount = Math.max(1, bandCount);
        int bandHeight = Math.max(1, safePanelHeight / safeBandCount);
        int clickedBand = Math.max(0, Math.min(safeBandCount - 1, mouseY / bandHeight));

        int clickedBandTop = clickedBand * bandHeight;
        int clickedBandBottom = clickedBandTop + bandHeight;

        // On vérifie d'abord la bande cliquée, puis très légèrement la frontière si le clic est sur la ligne (<= 2px)
        ArrayList<Integer> bandsToSearch = new ArrayList<>(2);
        bandsToSearch.add(clickedBand);
        if (mouseY - clickedBandTop <= 2 && clickedBand > 0) {
            bandsToSearch.add(clickedBand - 1);
        } else if (clickedBandBottom - mouseY <= 2 && clickedBand < safeBandCount - 1) {
            bandsToSearch.add(clickedBand + 1);
        }

        SeparatorMark bestMark = null;
        int bestDist = Integer.MAX_VALUE;

        for (int band : bandsToSearch) {
            ArrayList<SeparatorMark> list = bandSeparators.get(band);
            if (list == null || list.isEmpty()) continue;

            int bandTop = band * bandHeight;
            int bandBottom = bandTop + bandHeight;

            for ( int i = 0; i < list.size(); i++) {
                SeparatorMark sep = list.get(i);
                if (sep == null) continue;
                double effectiveSx = sep.x + offsetX;
                if (sep.isEndBoundary()) {
                    for (int k = Math.max(0, i - 2); k <= Math.min(list.size() - 1, i + 2); k++) {
                        SeparatorMark other = list.get(k);
                        if (other != null && other.isStartBoundary() && Math.abs(other.x - sep.x) <= 6) {
                            effectiveSx -= 3.5;
                            break;
                        }
                    }
                } else if (sep.isStartBoundary()) {
                    for (int k = Math.max(0, i - 2); k <= Math.min(list.size() - 1, i + 2); k++) {
                        SeparatorMark other = list.get(k);
                        if (other != null && other.isEndBoundary() && Math.abs(other.x - sep.x) <= 6) {
                            effectiveSx += 3.5;
                            break;
                        }
                    }
                }
                int dist = (int) Math.round(Math.abs(mouseX - effectiveSx));

                int tolX;
                if (sep.isStartBoundary() || sep.isEndBoundary()) {
                    // Flèches de début / fin de phrase :
                    // La flèche visuelle a une largeur de ~10-12px -> demi-largeur ~5-6px
                    int arrowH = Math.max(14, (int) Math.round(bandHeight * 0.38f));
                    int arrowW = Math.max(9, (int) Math.round(arrowH * 0.55f));
                    int baseTol = Math.max(5, (arrowW / 2) + 1);
                    tolX = (tolerancePx > 0) ? Math.min(tolerancePx, baseTol) : baseTol;

                    // Hitbox verticale : hauteur de la bande (borne de la réplique)
                    if (mouseY < bandTop - 2 || mouseY > bandBottom + 2) {
                        continue;
                    }
                } else if (sep.type == SeparatorMark.Type.INNER) {
                    if (sep.signType != SeparatorMark.SignType.DEFAULT) {
                        // Signes phonétiques (FVR, MPB, A, N, respiration) : pastille avec lettre ~12-14px
                        int baseTol = 7;
                        tolX = (tolerancePx > 0) ? Math.min(tolerancePx, baseTol) : baseTol;
                    } else {
                        // Séparateur syllabique classique : trait vertical 2px avec point 6px
                        int baseTol = 5;
                        tolX = (tolerancePx > 0) ? Math.min(tolerancePx, baseTol) : baseTol;
                    }

                    // Hitbox verticale : hauteur de la bande
                    if (mouseY < bandTop - 2 || mouseY > bandBottom + 2) {
                        continue;
                    }
                } else {
                    tolX = (tolerancePx > 0) ? Math.min(tolerancePx, 6) : 6;
                    if (mouseY < bandTop - 2 || mouseY > bandBottom + 2) {
                        continue;
                    }
                }

                if (dist <= tolX) {
                    if (dist < bestDist || (dist == bestDist && sep.isStartBoundary() && mouseX >= sep.x + offsetX)) {
                        bestDist = dist;
                        bestMark = sep;
                    }
                }
            }

            if (bestMark != null && band == clickedBand) {
                break;
            }
        }

        return bestMark;
    }

    public int[] findSeparatorAtScaled(int mouseX, int mouseY, int offsetX, int panelHeight, int bandCount, int tolerancePx) {
        int safePanelHeight = (panelHeight > 0) ? panelHeight : 100;
        int safeBandCount = Math.max(1, bandCount);
        int bandHeight = Math.max(1, safePanelHeight / safeBandCount);
        int clickedBand = Math.max(0, Math.min(safeBandCount - 1, mouseY / bandHeight));

        SeparatorMark mark = findSeparatorMarkAtScaled(mouseX, mouseY, offsetX, panelHeight, bandCount, tolerancePx);
        if (mark == null) return null;

        for (Map.Entry<Integer, ArrayList<SeparatorMark>> entry : bandSeparators.entrySet()) {
            if (entry.getValue().contains(mark)) {
                return new int[]{entry.getKey(), mark.x};
            }
        }
        return new int[]{clickedBand, mark.x};
    }

    /** Move a specific separator mark from its current position to newX on the given band. */
    public void moveSpecificSeparator(int band, SeparatorMark mark, int newX) {
        if (mark == null) return;
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return;
        sortSeparators(list);
        int oldX = mark.x;
        int idx = list.indexOf(mark);
        SeparatorMark prevMark = (idx > 0) ? list.get(idx - 1) : null;
        SeparatorMark nextMark = (idx >= 0 && idx + 1 < list.size()) ? list.get(idx + 1) : null;

        if (mark.type == SeparatorMark.Type.START) {
            if (prevMark != null) {
                int minX = prevMark.isEndBoundary() ? prevMark.x : (prevMark.x + 8);
                newX = Math.max(newX, minX);
            }
            if (nextMark != null) {
                newX = Math.min(newX, nextMark.x - 8);
            }
            Integer maxX = getRightBoundary(band, oldX);
            if (maxX != null) {
                newX = Math.min(newX, maxX - 8);
            }
            TextItem owner = getTextByStart(band, oldX);
            if (owner != null) {
                owner.x = newX;
                if (owner == selectedText) {
                    textX = newX;
                }
                texts.sort(Comparator.comparingInt(t -> t.x));
            }
        } else if (mark.type == SeparatorMark.Type.END) {
            if (prevMark != null) {
                newX = Math.max(newX, prevMark.x + 8);
            } else {
                Integer minX = getLeftBoundary(band, oldX);
                if (minX != null) {
                    newX = Math.max(newX, minX + 8);
                }
            }
            if (nextMark != null) {
                // Autoriser le collage exact du END de la phrase 1 sur le START de la phrase 2 (distance >= 0),
                // sans jamais franchir le START de la phrase 2.
                int maxX = nextMark.isStartBoundary() ? nextMark.x : (nextMark.x - 8);
                newX = Math.min(newX, maxX);
            }
        } else if (mark.type == SeparatorMark.Type.INNER) {
            if (prevMark != null) {
                newX = Math.max(newX, prevMark.x + 6);
            } else {
                Integer leftX = getLeftBoundary(band, oldX);
                if (leftX != null) newX = Math.max(newX, leftX + 6);
            }
            if (nextMark != null) {
                newX = Math.min(newX, nextMark.x - 6);
            } else {
                Integer rightX = getRightBoundary(band, oldX);
                if (rightX != null) newX = Math.min(newX, rightX - 6);
            }
            // Déplacement physique du symbole sans altérer splitIndex ni déformer les mots
            if (activeSegmentEndMarkX != Integer.MAX_VALUE && Math.abs(oldX - activeSegmentEndMarkX) <= 6) {
                activeSegmentEndMarkX = newX;
            }
        }
        mark.x = newX;
        sortSeparators(list);
    }

    /** Move an existing separator from oldX to newX on the given band. */
    public void moveSeparator(int band, int oldX, int newX) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return;
        for (SeparatorMark mark : list) {
            if (mark.x == oldX) {
                moveSpecificSeparator(band, mark, newX);
                return;
            }
        }
    }

    private Integer getRightBoundary(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return null;
        for (SeparatorMark sep : sepList) {
            if (sep.x <= x) continue;
            // Si on rencontre un nouveau début de phrase avant une fin, la phrase à x est ouverte !
            if (sep.isStartBoundary()) {
                return null;
            }
            if (sep.isEndBoundary()) {
                return sep.x;
            }
        }
        return null;
    }

    private Integer getNextStartBoundary(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return null;
        for (SeparatorMark sep : sepList) {
            if (sep.x > x && sep.isStartBoundary()) {
                return sep.x;
            }
        }
        return null;
    }

    private Integer getLeftBoundary(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return null;
        int leftSep = Integer.MIN_VALUE;
        for (SeparatorMark sep : sepList) {
            if (sep.x > x) break;
            if (sep.isStartBoundary()) {
                leftSep = sep.x;
            } else if (sep.isEndBoundary() && sep.x < x) {
                leftSep = Integer.MIN_VALUE;
            }
        }
        return (leftSep == Integer.MIN_VALUE) ? null : leftSep;
    }

    private boolean hasStartBoundaryAt(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return false;
        for (SeparatorMark sep : sepList) {
            if (Math.abs(sep.x - x) <= 6 && sep.isStartBoundary()) return true;
        }
        return false;
    }

    public TextItem getOpenPhraseInBand(int band) {
        TextItem open = null;
        for (TextItem t : texts) {
            if (t.band != band) continue;
            if (!hasStartBoundaryAt(band, t.x)) continue;
            if (getRightBoundary(band, t.x) == null) {
                if (open == null || t.x > open.x) {
                    open = t;
                }
            }
        }
        return open;
    }

    public boolean hasOpenPhraseInBand(int band) {
        return getOpenPhraseInBand(band) != null;
    }

    public boolean focusOpenPhraseInBand(int band) {
        TextItem open = getOpenPhraseInBand(band);
        if (open == null) return false;
        startEditingExistingText(open, open.text.length());
        return true;
    }

    public boolean focusOpenPhraseInBand(int band, int mouseX, int offsetX) {
        TextItem open = getOpenPhraseInBand(band);
        if (open == null) return false;
        int cursorIdx = getCursorIndexForClick(open, mouseX, offsetX);
        startEditingExistingText(open, cursorIdx);
        return true;
    }

    /** Démarre la saisie libre d'un nouveau texte à la coordonnée monde X sur la bande spécifiée. */
    public void startTyping(int band, int x) {
        this.isEditing = true;
        this.activeBand = band;
        this.textX = x;
        this.currentInput = "";
        this.selectedText = null;
        this.cursorIndex = 0;
        this.cursorRightSide = false;
        this.activeSegmentIndex = 0;
        this.activeSegmentEndMarkX = Integer.MAX_VALUE;
    }

    /**
     * Crée une nouvelle réplique complète :
     * - Place le repère de début START à la position courante du curseur
     * - Alloue un nouveau TextItem vide associé au rôle choisi
     * - Passe immédiatement en mode édition active avec le caret à l'indice 0.
     */
    public void startPhrase(int band, int cursorX, Role role) {
        // Si une phrase de la bande est déjà ouverte, on redonne le focus à celle-ci
        if (focusOpenPhraseInBand(band)) {
            return;
        }

        // Interdiction de créer une nouvelle phrase chevauchant une phrase existante
        if (hasPhraseAtPosition(band, cursorX)) {
            return;
        }

        // Interdiction de superposer un nouveau symbole sur un symbole existant
        if (hasSeparatorAt(band, cursorX)) {
            return;
        }

        // Repère de début (START)
        addSeparator(band, cursorX, SeparatorMark.Type.START);

        // TextItem positionné à l'ancre du repère de départ
        TextItem item = new TextItem("", cursorX, band);
        item.role = role;
        texts.add(item);
        texts.sort(Comparator.comparingInt(t -> t.x));

        this.selectedText = item;
        this.activeBand = band;
        this.textX = cursorX;
        this.currentInput = "";
        this.isEditing = true;
        this.cursorIndex = 0;
        this.cursorRightSide = false;
        this.activeSegmentIndex = 0;
        this.activeSegmentEndMarkX = Integer.MAX_VALUE;
        clearSelection();
    }

    /**
     * Clôture la phrase en cours d'édition :
     * - Place le repère de fin END à la coordonnée X du curseur de lecture
     * - Sauvegarde définitivement la chaîne saisie dans le TextItem
     * - Quitte le mode d'édition active.
     */
    public void endPhrase(int cursorX) {
        if (!isEditing) return;
        // On ne ferme qu'une phrase réelle (ayant un repère START valide)
        if (selectedText == null || !hasStartBoundaryAt(activeBand, selectedText.x)) {
            stopTyping();
            return;
        }

        // Si la phrase possède déjà un repère de fin END, on sauvegarde sans en ajouter un second
        if (getRightBoundary(activeBand, selectedText.x) != null) {
            if (selectedText != null) {
                selectedText.text = currentInput;
            }
            stopTyping();
            return;
        }

        if (selectedText != null) {
            selectedText.text = currentInput;
        } else if (!currentInput.isEmpty()) {
            texts.add(new TextItem(currentInput, textX, activeBand));
        }

        if (activeBand >= 0) {
            int targetEnd = cursorX;
            Integer nextStart = getNextStartBoundary(activeBand, selectedText.x);

            // Vérifier le dernier séparateur interne appartenant à cette phrase ouverte
            int maxInnerX = selectedText.x;
            ArrayList<SeparatorMark> sepList = bandSeparators.get(activeBand);
            if (sepList != null) {
                for (SeparatorMark s : sepList) {
                    if (s == null || s.x <= selectedText.x) continue;
                    if (nextStart != null && s.x >= nextStart) break;
                    if (s.isEndBoundary()) break;
                    if (isInnerOrLegacy(s.type) && s.x > maxInnerX) {
                        maxInnerX = s.x;
                    }
                }
            }

            // Si le curseur est placé avant ou sur le repère de début (ou avant un séparateur interne), on calcule la fin naturelle
            if (targetEnd <= maxInnerX + 10) {
                FontMetrics fm = getEffectiveFontMetrics();
                double textWidth = (fm != null && currentInput != null && !currentInput.isEmpty())
                        ? fm.stringWidth(currentInput)
                        : (currentInput != null && !currentInput.isEmpty() ? currentInput.length() * 10 : 80);
                int naturalWidth = Math.max(80, (int) Math.round(textWidth * 1.25));
                targetEnd = Math.max(selectedText.x + naturalWidth, maxInnerX + 40);
            }
            // S'il y a un début de phrase ultérieur, la fin ne doit pas empiéter dessus
            if (nextStart != null && targetEnd >= nextStart) {
                targetEnd = Math.max(selectedText.x + 10, nextStart - 5);
            }
            addSeparator(activeBand, targetEnd, SeparatorMark.Type.END);
        }
        stopTyping();
    }

    /** Ouvre une réplique existante en mode édition et positionne le curseur à l'indice spécifié. */
    public void startEditingExistingText(TextItem text, int cursorIdx) {
        boolean savedRightSide = this.cursorRightSide;
        int savedSegEndMarkX = this.activeSegmentEndMarkX;
        int savedSegIdx = this.activeSegmentIndex;
        this.selectedText = text;
        this.activeBand = text.band;
        this.textX = text.x;
        this.currentInput = text.text != null ? text.text : "";
        this.isEditing = true;
        this.cursorIndex = clampCursorIndex(cursorIdx);
        this.cursorRightSide = savedRightSide;
        this.activeSegmentEndMarkX = savedSegEndMarkX;
        this.activeSegmentIndex = savedSegIdx;
        clearSelection();
        updateActiveSegmentFromCursor();
    }

    /**
     * Traite la frappe d'un caractère au clavier :
     * - Insère le caractère dans le tampon courant
     * - Décale vers la droite tous les indices de découpe (splitIndex) des séparateurs internes situés après le segment actif
     * - Répercute instantanément le changement dans l'objet TextItem.
     */
    public void typeChar(char c) {
        if (!isEditing) return;

        if (c == '\n') {
            // Touche Entrée : repli en cas d'édition brute hors séparateurs
            if (selectedText == null && !currentInput.isEmpty()) {
                texts.add(new TextItem(currentInput, textX, activeBand));
            } else if (selectedText != null) {
                selectedText.text = currentInput;
            }
            stopTyping();
            return;
        }

        if (c == '\b') {
            deleteChar();
            return;
        }

        if (!Character.isISOControl(c)) {
            if (hasSelection()) {
                deleteSelection();
            }
            shiftInnerSplitIndicesOnInsert(cursorIndex, 1);
            currentInput = currentInput.substring(0, cursorIndex) + c + currentInput.substring(cursorIndex);
            cursorIndex++;
            if (activeSegmentEndMarkX != Integer.MAX_VALUE) {
                cursorRightSide = false;
            } else if (cursorIndex == currentInput.length()) {
                cursorRightSide = true;
            }
        }

        if (selectedText != null) {
            selectedText.text = currentInput;
        }
        updateActiveSegmentFromCursor();
    }

    /** Quitte le mode édition et réinitialise l'état de frappe. */
    public void stopTyping() {
        isEditing = false;
        currentInput = "";
        activeBand = -1;
        selectedText = null;
        cursorIndex = 0;
        cursorRightSide = false;
        activeSegmentIndex = -1;
        activeSegmentEndMarkX = Integer.MAX_VALUE;
        clearSelection();
    }

    /** Efface tous les textes, séparateurs et marqueurs de la timeline. */
    public void clearAll() {
        texts.clear();
        bandSeparators.clear();
        planMarkers.clear();
        stopTyping();
    }

    /**
     * Déplace le curseur de saisie de delta positions.
     * Gère avec précision le franchissement des repères internes (y compris dans les segments vides).
     */
    public void moveCursor(int delta) {
        if (!isEditing || delta == 0) return;
        if (hasSelection()) {
            if (delta < 0) {
                cursorIndex = getSelectionStart();
                cursorRightSide = false;
            } else {
                cursorIndex = getSelectionEnd();
                cursorRightSide = true;
            }
            clearSelection();
            updateActiveSegmentFromCursor();
            return;
        }
        int len = currentInput != null ? currentInput.length() : 0;

        ArrayList<SeparatorMark> inner = (selectedText != null) ? getInnerMarksForSelectedPhrase() : null;
        if (selectedText == null || inner == null || inner.isEmpty()) {
            cursorIndex = clampCursorIndex(cursorIndex + delta);
            cursorRightSide = (cursorIndex > 0);
            activeSegmentIndex = 0;
            activeSegmentEndMarkX = Integer.MAX_VALUE;
            return;
        }

        int k = inner.size();
        int segIdx = getActiveSegmentIndex(inner, len);
        int segStart = (segIdx == 0) ? 0 : inner.get(segIdx - 1).splitIndex;
        int segEnd = (segIdx == k) ? len : inner.get(segIdx).splitIndex;

        if (delta > 0) {
            if (cursorIndex < segEnd) {
                cursorIndex = Math.min(segEnd, cursorIndex + delta);
                cursorRightSide = (cursorIndex < segEnd) ? (cursorIndex > segStart || segIdx > 0) : (segIdx == k);
            } else {
                if (segIdx < k) {
                    segIdx++;
                    cursorIndex = segEnd;
                    cursorRightSide = true;
                } else {
                    cursorIndex = len;
                    cursorRightSide = true;
                }
            }
        } else {
            if (cursorIndex > segStart) {
                cursorIndex = Math.max(segStart, cursorIndex + delta);
                cursorRightSide = (cursorIndex == segStart && segIdx > 0);
            } else {
                if (segIdx > 0) {
                    segIdx--;
                    cursorIndex = segStart;
                    cursorRightSide = false;
                } else {
                    cursorIndex = 0;
                    cursorRightSide = false;
                }
            }
        }

        activeSegmentIndex = segIdx;
        activeSegmentEndMarkX = (segIdx < k) ? inner.get(segIdx).x : Integer.MAX_VALUE;
    }

    /** Déplace le curseur au début de la réplique (indice 0, premier segment). */
    public void moveCursorToStart() {
        clearSelection();
        cursorIndex = 0;
        cursorRightSide = false;
        activeSegmentIndex = 0;
        ArrayList<SeparatorMark> inner = (selectedText != null) ? getInnerMarksForSelectedPhrase() : null;
        activeSegmentEndMarkX = (inner != null && !inner.isEmpty()) ? inner.get(0).x : Integer.MAX_VALUE;
    }

    /** Déplace le curseur à la fin de la réplique (dernier segment). */
    public void moveCursorToEnd() {
        clearSelection();
        cursorIndex = currentInput != null ? currentInput.length() : 0;
        cursorRightSide = true;
        ArrayList<SeparatorMark> inner = (selectedText != null) ? getInnerMarksForSelectedPhrase() : null;
        activeSegmentIndex = (inner != null) ? inner.size() : 0;
        activeSegmentEndMarkX = Integer.MAX_VALUE;
    }

    /** Déplace le curseur d'un mot complet vers la gauche ou vers la droite. */
    public void moveCursorByWord(int direction) {
        clearSelection();
        if (direction < 0) {
            int i = cursorIndex - 1;
            while (i > 0 && currentInput.charAt(i - 1) == ' ') i--;
            while (i > 0 && currentInput.charAt(i - 1) != ' ') i--;
            cursorIndex = Math.max(0, i);
            cursorRightSide = false;
        } else {
            int i = cursorIndex;
            int len = currentInput.length();
            while (i < len && currentInput.charAt(i) == ' ') i++;
            while (i < len && currentInput.charAt(i) != ' ') i++;
            cursorIndex = Math.min(len, i);
            cursorRightSide = true;
        }
        cursorIndex = clampCursorIndex(cursorIndex);
        activeSegmentIndex = -1;
        activeSegmentEndMarkX = -1;
        updateActiveSegmentFromCursor();
    }

    /**
     * Supprime le caractère à gauche du curseur (Backspace) ou la sélection active, et ajuste
     * automatiquement les coupures syllabiques des séparateurs internes situés à droite.
     */
    public void deleteChar() {
        if (!isEditing || currentInput == null) return;
        if (deleteSelection()) return;
        if (cursorIndex <= 0) return;

        ArrayList<SeparatorMark> inner = getInnerMarksForSelectedPhrase();
        if (inner != null && !inner.isEmpty()) {
            int len = currentInput.length();
            int segIdx = getActiveSegmentIndex(inner, len);
            int segStart = (segIdx == 0) ? 0 : inner.get(segIdx - 1).splitIndex;
            if (cursorIndex <= segStart) {
                // Au bord gauche du segment actif (contre un séparateur) : ne pas supprimer dans le segment précédent
                return;
            }
            int deletePos = cursorIndex - 1;
            currentInput = currentInput.substring(0, deletePos) + currentInput.substring(cursorIndex);
            for (int i = segIdx; i < inner.size(); i++) {
                SeparatorMark m = inner.get(i);
                m.splitIndex = Math.max(0, Math.min(currentInput.length(), m.splitIndex - 1));
            }
            cursorIndex = clampCursorIndex(deletePos);
            if (selectedText != null) selectedText.text = currentInput;
            cursorRightSide = (cursorIndex == segStart && segIdx > 0);
            activeSegmentEndMarkX = (segIdx < inner.size()) ? inner.get(segIdx).x : Integer.MAX_VALUE;
            return;
        }

        int deletePos = cursorIndex - 1;
        currentInput = currentInput.substring(0, deletePos) + currentInput.substring(cursorIndex);
        cursorIndex = clampCursorIndex(deletePos);
        if (selectedText != null) selectedText.text = currentInput;
        updateActiveSegmentFromCursor();
    }

    /** Supprime le mot complet à gauche du curseur (Ctrl+Backspace) ou la sélection. */
    public void deleteWord() {
        if (!isEditing || currentInput == null) return;
        if (deleteSelection()) return;
        if (cursorIndex <= 0) return;

        ArrayList<SeparatorMark> inner = getInnerMarksForSelectedPhrase();
        if (inner != null && !inner.isEmpty()) {
            int len = currentInput.length();
            int segIdx = getActiveSegmentIndex(inner, len);
            int segStart = (segIdx == 0) ? 0 : inner.get(segIdx - 1).splitIndex;
            if (cursorIndex <= segStart) {
                return;
            }
            int i = cursorIndex;
            while (i > segStart && currentInput.charAt(i - 1) == ' ') i--;
            while (i > segStart && currentInput.charAt(i - 1) != ' ') i--;
            int deletedCount = cursorIndex - i;
            if (deletedCount <= 0) return;

            currentInput = currentInput.substring(0, i) + currentInput.substring(cursorIndex);
            for (int mIdx = segIdx; mIdx < inner.size(); mIdx++) {
                SeparatorMark m = inner.get(mIdx);
                m.splitIndex = Math.max(0, Math.min(currentInput.length(), m.splitIndex - deletedCount));
            }
            cursorIndex = clampCursorIndex(i);
            if (selectedText != null) selectedText.text = currentInput;
            cursorRightSide = (cursorIndex == segStart && segIdx > 0);
            activeSegmentEndMarkX = (segIdx < inner.size()) ? inner.get(segIdx).x : Integer.MAX_VALUE;
            return;
        }

        int i = cursorIndex;
        while (i > 0 && currentInput.charAt(i - 1) == ' ') i--;
        while (i > 0 && currentInput.charAt(i - 1) != ' ') i--;
        if (i == cursorIndex) return;

        currentInput = currentInput.substring(0, i) + currentInput.substring(cursorIndex);
        cursorIndex = clampCursorIndex(i);
        if (selectedText != null) selectedText.text = currentInput;
        updateActiveSegmentFromCursor();
    }

    /** Supprime le caractère à droite du curseur (Delete) ou la sélection active. */
    public void deleteForward() {
        if (!isEditing || currentInput == null) return;
        if (deleteSelection()) return;
        if (cursorIndex >= currentInput.length()) return;

        ArrayList<SeparatorMark> inner = getInnerMarksForSelectedPhrase();
        if (inner != null && !inner.isEmpty()) {
            int len = currentInput.length();
            int segIdx = getActiveSegmentIndex(inner, len);
            int segEnd = (segIdx == inner.size()) ? len : inner.get(segIdx).splitIndex;
            if (cursorIndex >= segEnd) {
                // Au bord droit du segment actif (contre un séparateur) : ne pas supprimer le segment suivant
                return;
            }
            currentInput = currentInput.substring(0, cursorIndex) + currentInput.substring(cursorIndex + 1);
            for (int i = segIdx; i < inner.size(); i++) {
                SeparatorMark m = inner.get(i);
                m.splitIndex = Math.max(0, Math.min(currentInput.length(), m.splitIndex - 1));
            }
            cursorIndex = clampCursorIndex(cursorIndex);
            if (selectedText != null) selectedText.text = currentInput;
            activeSegmentEndMarkX = (segIdx < inner.size()) ? inner.get(segIdx).x : Integer.MAX_VALUE;
            return;
        }

        currentInput = currentInput.substring(0, cursorIndex) + currentInput.substring(cursorIndex + 1);
        cursorIndex = clampCursorIndex(cursorIndex);
        if (selectedText != null) selectedText.text = currentInput;
        updateActiveSegmentFromCursor();
    }

    /** Insère un bloc de texte complet au curseur (Collage / Paste). */
    public void insertText(String text) {
        if (!isEditing || text == null || text.isEmpty()) return;
        String normalized = text.replace('\r', ' ').replace('\n', ' ');
        if (normalized.isEmpty()) return;
        if (hasSelection()) {
            deleteSelection();
        }
        shiftInnerSplitIndicesOnInsert(cursorIndex, normalized.length());
        currentInput = currentInput.substring(0, cursorIndex) + normalized + currentInput.substring(cursorIndex);
        cursorIndex += normalized.length();
        if (activeSegmentEndMarkX != Integer.MAX_VALUE) {
            cursorRightSide = false;
        } else if (cursorIndex == currentInput.length()) {
            cursorRightSide = true;
        }
        if (selectedText != null) {
            selectedText.text = currentInput;
        }
        updateActiveSegmentFromCursor();
    }

    private int clampCursorIndex(int rawIndex) {
        return Math.max(0, Math.min(currentInput != null ? currentInput.length() : 0, rawIndex));
    }

    // ==================== SEGMENT BOUNDS ====================
    public int[] getSegmentBounds(TextItem t) {
        if (t == null) return new int[]{0, 300};

        ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
        int[] resolved = new int[4];
        TimelineRenderer.resolvePhraseBoundaries(sepList, t.x, resolved);
        int leftSep = resolved[0];
        int rightSep = resolved[1];
        int nextPhraseStartX = resolved[2];
        int maxInnerX = resolved[3];

        int segmentStart = (leftSep != Integer.MIN_VALUE) ? leftSep : t.x;
        int segmentEnd;

        FontMetrics fm = getEffectiveFontMetrics();
        int targetTextHeight = (fm != null && fm.getFont() != null) ? Math.max(8, fm.getFont().getSize() - 2) : 40;
        double scaleY = TimelineRenderer.computeScaleY(fm, targetTextHeight);
        double textWidth = (fm != null && t.text != null)
                ? (TimelineRenderer.measureTextWidth(fm, t.text) * scaleY)
                : (t.text != null ? t.text.length() * 10 : 100);
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

        return new int[]{segmentStart, segmentEnd};
    }

    public ArrayList<SeparatorMark> getInnerMarksForSegment(TextItem t, int segmentStart, int segmentEnd) {
        ArrayList<SeparatorMark> result = new ArrayList<>();
        if (t == null) return result;
        ArrayList<SeparatorMark> list = bandSeparators.get(t.band);
        if (list == null) return result;
        TimelineRenderer.collectValidInnerMarks(list, segmentStart, segmentEnd, t, result);
        return result;
    }

    private boolean hasPhraseAtPosition(int band, int worldX) {
        for (TextItem t : texts) {
            if (t.band != band) continue;
            int[] bounds = getSegmentBounds(t);
            if (worldX >= bounds[0] && worldX <= bounds[1]) {
                return true;
            }
        }
        return false;
    }

    private TextItem findOrCreateTextItemForStartSeparators(int band, int worldX) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null || sepList.isEmpty()) return null;

        SeparatorMark startMark = null;
        int endMarkX = Integer.MAX_VALUE;
        int nextStartX = Integer.MAX_VALUE;
        int maxInnerX = Integer.MIN_VALUE;

        for (int i = 0; i < sepList.size(); i++) {
            SeparatorMark s = sepList.get(i);
            if (s == null) continue;
            if (s.isStartBoundary() && s.x <= worldX + 6) {
                startMark = s;
                endMarkX = Integer.MAX_VALUE;
                nextStartX = Integer.MAX_VALUE;
                maxInnerX = Integer.MIN_VALUE;
                for (int j = i + 1; j < sepList.size(); j++) {
                    SeparatorMark next = sepList.get(j);
                    if (next == null) continue;
                    if (next.isEndBoundary()) {
                        endMarkX = next.x;
                        break;
                    }
                    if (next.isStartBoundary()) {
                        nextStartX = next.x;
                        break;
                    }
                    if (isInnerOrLegacy(next.type) && next.x > maxInnerX) {
                        maxInnerX = next.x;
                    }
                }
            }
        }

        if (startMark == null) return null;
        int effectiveEnd = (endMarkX != Integer.MAX_VALUE)
                ? endMarkX
                : (nextStartX != Integer.MAX_VALUE ? nextStartX - 5 : Math.max(worldX + 20, Math.max(startMark.x + 120, (maxInnerX != Integer.MIN_VALUE ? maxInnerX + 120 : startMark.x + 120))));
        if (nextStartX != Integer.MAX_VALUE && effectiveEnd > nextStartX - 5) {
            effectiveEnd = nextStartX - 5;
        }
        if (worldX < startMark.x - 6 || worldX > effectiveEnd) {
            return null;
        }

        for (TextItem t : texts) {
            if (t.band == band && Math.abs(t.x - startMark.x) <= 6) {
                return t;
            }
        }

        TextItem created = new TextItem("", startMark.x, band);
        texts.add(created);
        texts.sort(Comparator.comparingInt(t -> t.x));
        return created;
    }

    public TextItem getTextInSegmentAt(int mouseX, int mouseY, int offsetX, int panelHeight, int bandCount) {
        int bandHeight = Math.max(1, panelHeight / Math.max(1, bandCount));
        int clickBand = mouseY / bandHeight;
        int worldX = mouseX - offsetX;

        for (TextItem t : texts) {
            if (t.band != clickBand) continue;
            int[] bounds = getSegmentBounds(t);
            if (worldX >= bounds[0] && worldX <= bounds[1]) {
                return t;
            }
        }
        return findOrCreateTextItemForStartSeparators(clickBand, worldX);
    }

    /**
     * Convertit un clic souris (coordonnée écran mouseX) en index précis de caractère (cursorIndex)
     * au sein d'une réplique déformée élastiquement par des séparateurs internes.
     * Utilise les dimensions réelles des caractères (FontMetrics) pour un positionnement exact
     * au niveau du caractère cliqué sans décalage.
     */
    public int getCursorIndexForClick(TextItem t, int mouseX, int offsetX) {
        FontMetrics fm = getEffectiveFontMetrics();
        int targetTextHeight = fm != null ? Math.max(8, fm.getHeight()) : 40;
        return getCursorIndexForClick(t, mouseX, offsetX, fm, targetTextHeight);
    }

    public int getCursorIndexForClick(TextItem t, int mouseX, int offsetX, FontMetrics fm) {
        int targetTextHeight = fm != null ? Math.max(8, fm.getHeight()) : 40;
        return getCursorIndexForClick(t, mouseX, offsetX, fm, targetTextHeight);
    }

    public int getCursorIndexForClick(TextItem t, int mouseX, int offsetX, FontMetrics fm, int targetTextHeight) {
        if (t == null) return 0;
        int[] bounds = getSegmentBounds(t);
        int segmentStart = bounds[0];
        int segmentEnd = bounds[1];

        FontMetrics effFm = (fm != null) ? fm : getEffectiveFontMetrics();
        if (effFm == null) return 0;
        if (targetTextHeight <= 0) targetTextHeight = Math.max(8, effFm.getHeight());

        int len = (t.text != null) ? t.text.length() : 0;

        ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
        ArrayList<SeparatorMark> innerMarks = new ArrayList<>();
        TimelineRenderer.collectValidInnerMarks(sepList, segmentStart, segmentEnd, t, innerMarks);

        if (len == 0 && innerMarks.isEmpty()) {
            activeSegmentIndex = 0;
            activeSegmentEndMarkX = Integer.MAX_VALUE;
            cursorRightSide = false;
            return 0;
        }

        double clickScreenX = mouseX;

        if (innerMarks.isEmpty()) {
            activeSegmentIndex = 0;
            activeSegmentEndMarkX = Integer.MAX_VALUE;
            double leftPad = 2.0;
            double rightPad = 4.0;
            double segStartX = segmentStart + offsetX + leftPad;
            double segEndX = segmentEnd + offsetX - rightPad;
            double segWidth = Math.max(2.0, segEndX - segStartX);
            double total = TimelineRenderer.measureTextWidth(effFm, t.text);
            if (total <= 0) {
                cursorRightSide = false;
                return 0;
            }
            double scaleY = TimelineRenderer.computeScaleY(effFm, targetTextHeight);
            double scaleX = TimelineRenderer.computeEffectiveScaleX(t.text, segWidth, total, scaleY);

            for (int c = 0; c < len; c++) {
                double charStart = segStartX + TimelineRenderer.measureTextWidth(effFm, t.text.substring(0, c)) * scaleX;
                double charEnd = segStartX + TimelineRenderer.measureTextWidth(effFm, t.text.substring(0, c + 1)) * scaleX;
                double charMid = (charStart + charEnd) / 2.0;
                if (clickScreenX < charMid) {
                    cursorRightSide = (c > 0);
                    return c;
                }
            }
            cursorRightSide = true;
            return len;
        }

        // Cas avec des séparateurs internes
        int prevX = segmentStart;
        int prevIdx = 0;

        for (int mi = 0; mi < innerMarks.size(); mi++) {
            SeparatorMark mark = innerMarks.get(mi);
            int segStart = prevX;
            int segEnd = mark.x;
            int idx = mark.splitIndex;
            if (idx < prevIdx) idx = prevIdx;
            if (idx > len) idx = len;

            double segScreenEnd = segEnd + offsetX;

            if (clickScreenX <= segScreenEnd || (mi == innerMarks.size() - 1 && mark.x >= segmentEnd)) {
                activeSegmentIndex = mi;
                activeSegmentEndMarkX = mark.x;
                int segChars = idx - prevIdx;
                if (segChars <= 0) {
                    cursorRightSide = (mi > 0);
                    return prevIdx;
                }

                String segText = t.text.substring(prevIdx, idx);
                double leftPad = 2.0;
                double rightPad = 3.0;
                double segStartX = segStart + offsetX + leftPad;
                double segEndX = segEnd + offsetX - rightPad;
                double segWidth = Math.max(2.0, segEndX - segStartX);
                double total = TimelineRenderer.measureTextWidth(effFm, segText);

                if (total <= 0) {
                    cursorRightSide = (mi > 0);
                    return prevIdx;
                }

                double scaleY = TimelineRenderer.computeScaleY(effFm, targetTextHeight);
                double scaleX = TimelineRenderer.computeEffectiveScaleX(segText, segWidth, total, scaleY);

                for (int c = 0; c < segText.length(); c++) {
                    double charStart = segStartX + TimelineRenderer.measureTextWidth(effFm, segText.substring(0, c)) * scaleX;
                    double charEnd = segStartX + TimelineRenderer.measureTextWidth(effFm, segText.substring(0, c + 1)) * scaleX;
                    double charMid = (charStart + charEnd) / 2.0;
                    if (clickScreenX < charMid) {
                        int res = prevIdx + c;
                        cursorRightSide = (c > 0 || mi > 0);
                        return res;
                    }
                }

                cursorRightSide = false;
                return idx;
            }

            prevX = mark.x;
            prevIdx = idx;
        }

        // Dernier tronçon (après le dernier séparateur interne)
        activeSegmentIndex = innerMarks.size();
        activeSegmentEndMarkX = Integer.MAX_VALUE;
        int segStart = prevX;
        int segEnd = segmentEnd;
        int segChars = len - prevIdx;
        if (segChars <= 0) {
            cursorRightSide = true;
            return len;
        }

        String segText = t.text.substring(Math.min(prevIdx, len));
        double leftPad = 2.0;
        double rightPad = 4.0;
        double segStartX = segStart + offsetX + leftPad;
        double segEndX = segEnd + offsetX - rightPad;
        double segWidth = Math.max(2.0, segEndX - segStartX);
        double total = TimelineRenderer.measureTextWidth(effFm, segText);

        if (total <= 0) {
            cursorRightSide = true;
            return prevIdx;
        }

        double scaleY = TimelineRenderer.computeScaleY(effFm, targetTextHeight);
        double scaleX = TimelineRenderer.computeEffectiveScaleX(segText, segWidth, total, scaleY);

        for (int c = 0; c < segText.length(); c++) {
            double charStart = segStartX + TimelineRenderer.measureTextWidth(effFm, segText.substring(0, c)) * scaleX;
            double charEnd = segStartX + TimelineRenderer.measureTextWidth(effFm, segText.substring(0, c + 1)) * scaleX;
            double charMid = (charStart + charEnd) / 2.0;
            if (clickScreenX < charMid) {
                int res = prevIdx + c;
                cursorRightSide = true;
                return res;
            }
        }

        cursorRightSide = true;
        return len;
    }

    public boolean isInEmptyInnerGap(TextItem t, int mouseX, int offsetX) {
        if (t == null) return false;

        int[] bounds = getSegmentBounds(t);
        int segmentStart = bounds[0];
        int segmentEnd = bounds[1];
        int worldX = mouseX - offsetX;
        worldX = Math.max(segmentStart, Math.min(segmentEnd, worldX));

        ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
        if (sepList == null || sepList.isEmpty()) return false;

        ArrayList<SeparatorMark> innerMarks = new ArrayList<>();
        TimelineRenderer.collectValidInnerMarks(sepList, segmentStart, segmentEnd, t, innerMarks);
        if (innerMarks.isEmpty()) return false;

        int len = (t.text != null) ? t.text.length() : 0;
        int prevX = segmentStart;
        int prevIdx = 0;

        for (SeparatorMark mark : innerMarks) {
            int segStart = prevX;
            int segEnd = mark.x;
            int idx = mark.splitIndex;
            if (idx < prevIdx) idx = prevIdx;
            if (idx > len) idx = len;

            if (worldX >= segStart && worldX <= segEnd) {
                return (idx - prevIdx) <= 0;
            }

            prevX = mark.x;
            prevIdx = idx;
        }

        return false;
    }

    // ==================== MOUSE HIT DETECTION ====================
    public TextItem getTextAtScaled(int mouseX, int mouseY, int offsetX, int panelHeight, int bandCount) {
        int bandHeight = Math.max(1, panelHeight / Math.max(1, bandCount));
        int clickBand = mouseY / bandHeight;

        for (TextItem t : texts) {
            if (t.band != clickBand) continue;

            int[] bounds = getSegmentBounds(t);
            int segmentStart = bounds[0];
            int segmentEnd = bounds[1];

            int scaledStartX = segmentStart + offsetX;
            int scaledEndX = segmentEnd + offsetX;

            if (mouseX >= scaledStartX && mouseX <= scaledEndX) {
                return t;
            }
        }

        return findOrCreateTextItemForStartSeparators(clickBand, mouseX - offsetX);
    }

    public boolean hasSeparatorNear(int band, int x, int tolerancePx) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return false;
        for (SeparatorMark sep : sepList) {
            if (Math.abs(sep.x - x) <= tolerancePx && isInnerOrLegacy(sep.type)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSeparatorAt(int band, int x) {
        return hasSeparatorNear(band, x, 6);
    }

    /** Supprime le marqueur à la position x sur la bande. Retourne true si trouvé. */
    public boolean removeSeparator(int band, int x) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).x == x) {
                SeparatorMark mark = list.get(i);
                // Si c'est un début, supprimer aussi le TextItem associé
                if (mark.type == SeparatorMark.Type.START) {
                    texts.removeIf(t -> t.band == band && t.x == x);
                }
                list.remove(i);
                // Si la liste est vide, nettoyer la map
                if (list.isEmpty()) bandSeparators.remove(band);
                return true;
            }
        }
        return false;
    }

    /**
     * Retourne la liste de tous les séparateurs situés exactement ou à proximité (tolerancePx) de worldX.
     */
    public ArrayList<SeparatorMark> getSeparatorsAtOrNear(int band, int worldX, int tolerancePx) {
        ArrayList<SeparatorMark> result = new ArrayList<>();
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return result;
        for (SeparatorMark sm : list) {
            if (Math.abs(sm.x - worldX) <= tolerancePx) {
                result.add(sm);
            }
        }
        return result;
    }

    /**
     * Supprime un séparateur spécifique précis.
     */
    public boolean removeSpecificSeparator(int band, SeparatorMark mark) {
        if (mark == null) return false;
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return false;
        boolean removed = list.remove(mark);
        if (removed) {
            if (mark.type == SeparatorMark.Type.START) {
                texts.removeIf(t -> t.band == band && t.x == mark.x);
            }
            if (list.isEmpty()) {
                bandSeparators.remove(band);
            }
        }
        return removed;
    }

    /**
     * Supprime tous les séparateurs situés à worldX (+/- tolerancePx).
     */
    public void removeAllSeparatorsAt(int band, int worldX, int tolerancePx) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return;
        boolean removedStart = false;
        java.util.Iterator<SeparatorMark> it = list.iterator();
        while (it.hasNext()) {
            SeparatorMark sm = it.next();
            if (Math.abs(sm.x - worldX) <= tolerancePx) {
                if (sm.type == SeparatorMark.Type.START) {
                    removedStart = true;
                }
                it.remove();
            }
        }
        if (removedStart) {
            texts.removeIf(t -> t.band == band && Math.abs(t.x - worldX) <= tolerancePx);
        }
        if (list.isEmpty()) {
            bandSeparators.remove(band);
        }
    }

    /**
     * Défusionne les signes superposés ou trop proches en les espaçant de spacingPx.
     */
    public void unmergeSeparators(int band, int worldX, int spacingPx) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null || list.size() <= 1) return;
        int minSpacing = Math.max(10, spacingPx);
        ArrayList<SeparatorMark> matches = new ArrayList<>();
        for (SeparatorMark sm : list) {
            if (Math.abs(sm.x - worldX) <= 8) {
                matches.add(sm);
            }
        }
        if (matches.size() <= 1) return;
        int currentX = matches.get(0).x;
        for (int i = 1; i < matches.size(); i++) {
            currentX += minSpacing;
            matches.get(i).x = currentX;
        }
        sortSeparators(list);
    }

    /**
     * Supprime l'intégralité d'une phrase commençant au séparateur START à startX :
     * texte, séparateur START, tous les séparateurs internes et le séparateur END.
     */
    public boolean deleteFullPhraseAtStart(int band, int startX) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return false;

        SeparatorMark startMark = null;
        int startIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            SeparatorMark m = list.get(i);
            if (m.x == startX && m.type == SeparatorMark.Type.START) {
                startMark = m;
                startIndex = i;
                break;
            }
        }
        if (startMark == null) return false;

        // Trouver la fin de la phrase (prochain END, ou arrêt avant le prochain START)
        int endX = -1;
        int endSepIdx = list.size() - 1;
        for (int i = startIndex + 1; i < list.size(); i++) {
            SeparatorMark m = list.get(i);
            if (m.type == SeparatorMark.Type.START) {
                endX = m.x - 1;
                endSepIdx = i - 1;
                break;
            }
            if (m.type == SeparatorMark.Type.END) {
                endX = m.x;
                endSepIdx = i;
                break;
            }
        }

        // Si aucun END ni START ultérieur dans les séparateurs :
        // Borner la phrase à l'élément de texte débutant à startX ou avant le prochain texte de la bande
        if (endX == -1) {
            int nextItemX = Integer.MAX_VALUE;
            for (TextItem t : texts) {
                if (t.band == band && t.x > startX && t.x < nextItemX) {
                    nextItemX = t.x;
                }
            }
            if (nextItemX != Integer.MAX_VALUE) {
                endX = nextItemX - 1;
            } else {
                int maxTextLen = 300;
                for (TextItem t : texts) {
                    if (t.band == band && t.x == startX) {
                        maxTextLen = Math.max(maxTextLen, t.text != null ? t.text.length() * 20 : 300);
                    }
                }
                endX = startX + maxTextLen;
            }
        }

        // Si la phrase était en cours d'édition, stopper l'édition
        if (isEditing && activeBand == band) {
            if (selectedText != null && selectedText.x == startX) {
                stopTyping();
            }
        }

        final int finalEndX = endX;
        // Supprimer uniquement le TextItem de cette réplique (sans toucher au TextItem de la phrase suivante si collée sur finalEndX)
        texts.removeIf(t -> t.band == band && (t.x == startX || (t.x > startX && t.x < finalEndX)));

        // Supprimer les séparateurs (START, INNER, END) appartenant strictement à cette réplique (indices startIndex..endSepIdx)
        for (int i = Math.min(endSepIdx, list.size() - 1); i >= startIndex; i--) {
            SeparatorMark m = list.get(i);
            if (m.x >= startX && m.x <= finalEndX) {
                list.remove(i);
            }
        }

        if (list.isEmpty()) {
            bandSeparators.remove(band);
        }
        return true;
    }

    public Integer getStartSeparatorXForText(TextItem t) {
        if (t == null) return null;
        ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
        if (sepList == null) return t.x;
        for (SeparatorMark sep : sepList) {
            if (sep.isStartBoundary() && sep.x == t.x) {
                return sep.x;
            }
        }
        int leftSep = Integer.MIN_VALUE;
        for (SeparatorMark sep : sepList) {
            if (sep.isStartBoundary() && sep.x <= t.x && sep.x > leftSep) {
                boolean endBetween = false;
                for (SeparatorMark other : sepList) {
                    if (other.isEndBoundary() && other.x > sep.x && other.x <= t.x) {
                        endBetween = true;
                        break;
                    }
                }
                if (!endBetween) {
                    leftSep = sep.x;
                }
            }
        }
        return (leftSep == Integer.MIN_VALUE) ? t.x : leftSep;
    }

    public int[] getPhraseBoundsAtStart(int band, int startX) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return null;

        SeparatorMark startMark = null;
        int startIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            SeparatorMark m = list.get(i);
            if (m.x == startX && m.type == SeparatorMark.Type.START) {
                startMark = m;
                startIndex = i;
                break;
            }
        }
        if (startMark == null) return null;

        int endX = Integer.MIN_VALUE;
        for (int i = startIndex + 1; i < list.size(); i++) {
            SeparatorMark m = list.get(i);
            if (m.type == SeparatorMark.Type.START) {
                endX = m.x - 1;
                break;
            }
            if (m.type == SeparatorMark.Type.END) {
                endX = m.x;
                break;
            }
        }

        if (endX == Integer.MIN_VALUE) {
            // Pas de séparateur END explicite : trouver l'étendue maximale sans déborder sur le texte suivant
            int nextTextX = Integer.MAX_VALUE;
            for (TextItem t : texts) {
                if (t.band == band && t.x > startX && t.x < nextTextX) {
                    nextTextX = t.x;
                }
            }
            int maxInnerOrText = startX + 300;
            for (int i = startIndex + 1; i < list.size(); i++) {
                SeparatorMark m = list.get(i);
                if (m.type == SeparatorMark.Type.START) break;
                if (m.x > maxInnerOrText) maxInnerOrText = m.x;
            }
            for (TextItem t : texts) {
                if (t.band == band && t.x == startX) {
                    int tEnd = t.x + Math.max(300, t.text.length() * 12);
                    if (tEnd > maxInnerOrText) maxInnerOrText = tEnd;
                }
            }
            if (nextTextX != Integer.MAX_VALUE && maxInnerOrText >= nextTextX) {
                maxInnerOrText = nextTextX - 1;
            }
            endX = maxInnerOrText;
        }

        return new int[]{startX, endX};
    }

    public Role getPhraseRole(int band, int startX) {
        for (TextItem t : texts) {
            if (t.band == band && t.x == startX && t.role != null) {
                return t.role;
            }
        }
        int[] bounds = getPhraseBoundsAtStart(band, startX);
        int phraseStart = (bounds != null) ? bounds[0] : startX;
        int phraseEnd = (bounds != null) ? bounds[1] : (startX + 300);
        for (TextItem t : texts) {
            if (t.band == band && t.x >= phraseStart && t.x < phraseEnd) {
                if (t.role != null) return t.role;
            }
        }
        return null;
    }

    public void setPhraseRole(int band, int startX, Role role) {
        int[] bounds = getPhraseBoundsAtStart(band, startX);
        int phraseStart = (bounds != null) ? bounds[0] : startX;
        int phraseEnd = (bounds != null) ? bounds[1] : (startX + 300);
        boolean matched = false;
        for (TextItem t : texts) {
            if (t.band == band && (t.x == phraseStart || (t.x > phraseStart && t.x < phraseEnd))) {
                t.role = role;
                matched = true;
            }
        }
        if (!matched) {
            for (TextItem t : texts) {
                if (t.band == band && Math.abs(t.x - startX) <= 6) {
                    t.role = role;
                }
            }
        }
    }

    public void setPhraseRole(TextItem item, Role role) {
        if (item == null) return;
        Integer startX = getStartSeparatorXForText(item);
        if (startX == null) startX = item.x;
        setPhraseRole(item.band, startX, role);
        item.role = role;
    }

    public boolean isSpaceFreeOnBand(int targetBand, int startX, int endX) {
        ArrayList<SeparatorMark> targetSeps = bandSeparators.get(targetBand);
        if (targetSeps != null) {
            for (SeparatorMark m : targetSeps) {
                if (m.x > startX && m.x < endX) {
                    return false;
                }
                if (m.type == SeparatorMark.Type.START) {
                    int[] pBounds = getPhraseBoundsAtStart(targetBand, m.x);
                    if (pBounds != null) {
                        if (Math.max(startX, pBounds[0]) < Math.min(endX, pBounds[1])) {
                            return false;
                        }
                    }
                }
            }
        }

        for (TextItem t : texts) {
            if (t.band == targetBand) {
                int[] bounds = getSegmentBounds(t);
                if (Math.max(startX, bounds[0]) < Math.min(endX, bounds[1])) {
                    return false;
                }
            }
        }

        return true;
    }

    public boolean movePhraseToBand(int sourceBand, int startX, int targetBand) {
        if (sourceBand == targetBand) return true;
        int[] bounds = getPhraseBoundsAtStart(sourceBand, startX);
        if (bounds == null) return false;
        if (!isSpaceFreeOnBand(targetBand, bounds[0], bounds[1])) {
            return false;
        }

        int phraseStart = bounds[0];
        int phraseEnd = bounds[1];

        // 1. Déplacer uniquement le(s) TextItem(s) de cette phrase (sans toucher au TextItem de la phrase suivante si collée)
        for (TextItem t : texts) {
            if (t.band == sourceBand && (t.x == phraseStart || (t.x > phraseStart && t.x < phraseEnd))) {
                t.band = targetBand;
            }
        }

        // 2. Extraire et déplacer les séparateurs appartenant strictement à cette phrase
        ArrayList<SeparatorMark> srcList = bandSeparators.get(sourceBand);
        if (srcList != null) {
            ArrayList<SeparatorMark> toMove = new ArrayList<>();
            boolean insidePhrase = false;
            for (SeparatorMark m : srcList) {
                if (!insidePhrase) {
                    if (m.x == phraseStart && m.type == SeparatorMark.Type.START) {
                        insidePhrase = true;
                        toMove.add(m);
                    }
                } else {
                    if (m.type == SeparatorMark.Type.START) {
                        break;
                    }
                    if (m.x <= phraseEnd) {
                        toMove.add(m);
                    }
                    if (m.type == SeparatorMark.Type.END) {
                        break;
                    }
                }
            }
            srcList.removeAll(toMove);
            if (srcList.isEmpty()) {
                bandSeparators.remove(sourceBand);
            }

            ArrayList<SeparatorMark> dstList = bandSeparators.computeIfAbsent(targetBand, k -> new ArrayList<>());
            dstList.addAll(toMove);
            sortSeparators(dstList);
        }

        // 3. Mettre à jour activeBand si la phrase était en cours d'édition
        if (isEditing && activeBand == sourceBand) {
            if (selectedText != null && selectedText.band == targetBand) {
                activeBand = targetBand;
            }
        }

        return true;
    }

    /** Change le type d'un marqueur interne/legacy. Ignoré pour START et END. */
    public boolean setSeparatorType(int band, int x, SeparatorMark.Type newType) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return false;
        for (SeparatorMark mark : list) {
            if (mark.x == x) {
                mark.type = newType;
                return true;
            }
        }
        return false;
    }

    /**
     * Transfère un caractère de droite à gauche (direction=-1) ou gauche à droite (direction=+1)
     * autour du séparateur interne à (band, x).
     */
    public void shiftInnerSepText(int band, int x, int direction) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return;
        for (SeparatorMark mark : list) {
            if (mark.x != x || mark.type != SeparatorMark.Type.INNER) continue;
            TextItem owner = getTextForInnerSeparator(band, x);
            if (owner == null || owner.text == null || owner.text.isEmpty()) return;
            if (mark.splitIndex < 0) {
                int[] b = getSegmentBounds(owner);
                double ratio = (double) (mark.x - b[0]) / Math.max(1, b[1] - b[0]);
                mark.splitIndex = (int) Math.round(ratio * owner.text.length());
            }
            int minSplit = getMinSplitForInner(owner, mark);
            int maxSplit = getMaxSplitForInner(owner, mark);
            int newSplit = Math.max(minSplit, Math.min(maxSplit, mark.splitIndex + direction));
            mark.splitIndex = newSplit;
            // Si on est en cours d'édition de cette phrase, mettre à jour aussi cursorIndex
            if (owner == selectedText) {
                cursorIndex = Math.max(0, Math.min(currentInput.length(), cursorIndex));
                updateActiveSegmentFromCursor();
            }
            return;
        }
    }

    /**
     * Décale un séparateur interne (INNER) de plusieurs caractères en une seule opération (delta négatif = vers la gauche, positif = vers la droite).
     * Permet une redistribution élastique ultra-fluide et réactive des lettres lors du glisser-déposer à la souris.
     */
    public void shiftInnerSepTextBy(int band, int x, int delta) {
        if (delta == 0) return;
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return;
        for (SeparatorMark mark : list) {
            if (mark.x != x || mark.type != SeparatorMark.Type.INNER) continue;
            TextItem owner = getTextForInnerSeparator(band, x);
            if (owner == null || owner.text == null || owner.text.isEmpty()) return;
            if (mark.splitIndex < 0) {
                int[] b = getSegmentBounds(owner);
                double ratio = (double) (mark.x - b[0]) / Math.max(1, b[1] - b[0]);
                mark.splitIndex = (int) Math.round(ratio * owner.text.length());
            }
            int minSplit = getMinSplitForInner(owner, mark);
            int maxSplit = getMaxSplitForInner(owner, mark);
            int newSplit = Math.max(minSplit, Math.min(maxSplit, mark.splitIndex + delta));
            mark.splitIndex = newSplit;
            if (owner == selectedText) {
                cursorIndex = Math.max(0, Math.min(currentInput.length(), cursorIndex));
                updateActiveSegmentFromCursor();
            }
            return;
        }
    }

    public boolean nudgeInnerSeparatorNear(int band, int worldX, int tolerancePx, int direction, int steps) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null || steps <= 0 || direction == 0) return false;

        SeparatorMark nearest = null;
        int bestDist = Integer.MAX_VALUE;
        for (SeparatorMark mark : list) {
            if (mark.type != SeparatorMark.Type.INNER) continue;
            int dist = Math.abs(mark.x - worldX);
            if (dist <= tolerancePx && dist < bestDist) {
                nearest = mark;
                bestDist = dist;
            }
        }
        if (nearest == null) return false;

        int dir = direction > 0 ? 1 : -1;
        for (int i = 0; i < steps; i++) {
            shiftInnerSepText(band, nearest.x, dir);
        }
        return true;
    }

    /** Retourne le type du marqueur à (band, x), ou null si absent. */
    public SeparatorMark.Type getSeparatorType(int band, int x) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return null;
        for (SeparatorMark mark : list) {
            if (mark.x == x) return mark.type;
        }
        return null;
    }

    /** Retourne le type de signe (FVR, MPB, etc.) du marqueur à (band, x), ou null si absent. */
    public SeparatorMark.SignType getSeparatorSignType(int band, int x) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return null;
        for (SeparatorMark mark : list) {
            if (mark.x == x) return mark.signType;
        }
        return null;
    }

    private void sortSeparators(ArrayList<SeparatorMark> list) {
        list.sort((m1, m2) -> {
            if (m1.x != m2.x) {
                return Integer.compare(m1.x, m2.x);
            }
            return Integer.compare(separatorTypeOrder(m1.type), separatorTypeOrder(m2.type));
        });
    }

    private static int separatorTypeOrder(SeparatorMark.Type t) {
        if (t == SeparatorMark.Type.END) return 0;
        if (t == SeparatorMark.Type.INNER) return 1;
        if (t == SeparatorMark.Type.START) return 2;
        return 3;
    }

    private TextItem getTextByStart(int band, int startX) {
        for (TextItem t : texts) {
            if (t.band == band && t.x == startX) return t;
        }
        for (TextItem t : texts) {
            if (t.band == band && Math.abs(t.x - startX) <= 6) return t;
        }
        return null;
    }

    private TextItem getTextForInnerSeparator(int band, int sepX) {
        if (selectedText != null && selectedText.band == band) {
            int[] bounds = getSegmentBounds(selectedText);
            if (sepX >= bounds[0] && sepX <= bounds[1]) {
                return selectedText;
            }
        }
        TextItem owner = null;
        for (TextItem t : texts) {
            if (t.band != band) continue;
            int[] bounds = getSegmentBounds(t);
            int left = bounds[0];
            int right = bounds[1];
            if (sepX >= left && sepX <= right) {
                if (owner == null || t.x > owner.x) owner = t;
            }
        }
        return owner;
    }

    private ArrayList<SeparatorMark> getInnerMarksForSelectedPhrase() {
        ArrayList<SeparatorMark> result = new ArrayList<>();
        if (selectedText == null) return result;
        ArrayList<SeparatorMark> list = bandSeparators.get(selectedText.band);
        if (list == null) return result;
        int[] bounds = getSegmentBounds(selectedText);
        int left = bounds[0];
        int right = bounds[1];
        TimelineRenderer.collectValidInnerMarks(list, left, right, selectedText, result);
        return result;
    }

    private int getActiveSegmentIndex(ArrayList<SeparatorMark> inner, int len) {
        if (inner == null || inner.isEmpty()) return 0;
        int prevIdx = 0;
        for (int i = 0; i < inner.size(); i++) {
            SeparatorMark m = inner.get(i);
            int idx = Math.max(prevIdx, Math.min(len, m.splitIndex >= 0 ? m.splitIndex : 0));
            m.splitIndex = idx;
            prevIdx = idx;
        }

        if (activeSegmentIndex >= 0 && activeSegmentIndex <= inner.size()) {
            int segStart = (activeSegmentIndex == 0) ? 0 : inner.get(activeSegmentIndex - 1).splitIndex;
            int segEnd = (activeSegmentIndex == inner.size()) ? len : inner.get(activeSegmentIndex).splitIndex;
            if (cursorIndex >= segStart && cursorIndex <= segEnd) {
                return activeSegmentIndex;
            }
        }

        prevIdx = 0;
        for (int i = 0; i < inner.size(); i++) {
            SeparatorMark m = inner.get(i);
            int idx = m.splitIndex;
            if (activeSegmentEndMarkX != Integer.MAX_VALUE && Math.abs(m.x - activeSegmentEndMarkX) <= 6) {
                if (cursorIndex >= prevIdx && cursorIndex <= idx) {
                    activeSegmentIndex = i;
                    return i;
                }
            }
            prevIdx = idx;
        }
        if (activeSegmentEndMarkX == Integer.MAX_VALUE) {
            int lastSplit = inner.get(inner.size() - 1).splitIndex;
            if ((cursorIndex > lastSplit || (cursorIndex == lastSplit && cursorRightSide)) && cursorIndex <= len) {
                activeSegmentIndex = inner.size();
                return inner.size();
            }
        }
        for (int i = 0; i < inner.size(); i++) {
            SeparatorMark m = inner.get(i);
            if (m.splitIndex > cursorIndex) {
                activeSegmentIndex = i;
                return i;
            }
            if (m.splitIndex == cursorIndex && !cursorRightSide) {
                activeSegmentIndex = i;
                return i;
            }
        }
        activeSegmentIndex = inner.size();
        return inner.size();
    }

    private void rotateInnerSplitIndices(TextItem phrase, int cursor) {
        if (phrase == null) return;
        ArrayList<SeparatorMark> list = bandSeparators.get(phrase.band);
        if (list == null) return;
        int len = (phrase.text != null) ? phrase.text.length() : 0;
        if (len <= 0) return;
        int[] bounds = getSegmentBounds(phrase);
        int left = bounds[0];
        int right = bounds[1];
        for (SeparatorMark m : list) {
            if (!isInnerOrLegacy(m.type)) continue;
            if (m.x <= left || m.x >= right) continue;
            if (m.splitIndex < 0) continue;
            if (m.splitIndex >= cursor) {
                m.splitIndex = m.splitIndex - cursor;
            } else {
                m.splitIndex = len - cursor + m.splitIndex;
            }
        }
    }

    private void updateActiveSegmentFromCursor() {
        if (selectedText == null) {
            activeSegmentIndex = -1;
            activeSegmentEndMarkX = Integer.MAX_VALUE;
            return;
        }
        ArrayList<SeparatorMark> inner = getInnerMarksForSelectedPhrase();
        if (inner == null || inner.isEmpty()) {
            activeSegmentIndex = 0;
            activeSegmentEndMarkX = Integer.MAX_VALUE;
            return;
        }
        int len = (currentInput != null) ? currentInput.length() : 0;
        int segIdx = getActiveSegmentIndex(inner, len);
        activeSegmentIndex = segIdx;
        activeSegmentEndMarkX = (segIdx < inner.size()) ? inner.get(segIdx).x : Integer.MAX_VALUE;
    }

    /**
     * Décale les indices de coupure syllabique des séparateurs internes lors de l'insertion de nouveaux caractères.
     * Tout séparateur bordant la droite du segment actif ou situé plus à droite voit son splitIndex incrémenté de delta.
     */
    private void shiftInnerSplitIndicesOnInsert(int atIndex, int delta) {
        if (selectedText == null || delta <= 0) return;
        ArrayList<SeparatorMark> inner = getInnerMarksForSelectedPhrase();
        if (inner == null || inner.isEmpty()) return;
        int len = (currentInput != null) ? currentInput.length() : 0;
        int segIdx = getActiveSegmentIndex(inner, len);
        for (int i = segIdx; i < inner.size(); i++) {
            inner.get(i).splitIndex += delta;
        }
    }

    /**
     * Ajuste les indices de coupure syllabique des séparateurs internes lors de la suppression de caractères.
     * Les séparateurs ne sont JAMAIS supprimés lors de l'effacement de caractères textuels.
     */
    private void shiftInnerSplitIndicesOnDelete(int startIndex, int deletedLen) {
        if (selectedText == null || deletedLen <= 0) return;
        int endIndex = startIndex + deletedLen;
        ArrayList<SeparatorMark> innerMarks = getInnerMarksForSelectedPhrase();
        for (SeparatorMark m : innerMarks) {
            if (m.splitIndex >= endIndex) {
                m.splitIndex -= deletedLen;
            } else if (m.splitIndex > startIndex) {
                m.splitIndex = startIndex;
            }
            if (m.splitIndex < 0) m.splitIndex = 0;
            if (m.splitIndex > currentInput.length()) m.splitIndex = currentInput.length();
        }
    }

    private int getMinSplitForInner(TextItem owner, SeparatorMark mark) {
        int min = 0;
        ArrayList<SeparatorMark> list = bandSeparators.get(owner.band);
        if (list == null) return min;
        SeparatorMark prevInner = null;
        int maxPrevX = Integer.MIN_VALUE;
        for (SeparatorMark m : list) {
            if (isInnerOrLegacy(m.type) && m.x < mark.x && m.x > maxPrevX) {
                maxPrevX = m.x;
                prevInner = m;
            }
        }
        if (prevInner != null && prevInner.splitIndex >= 0) min = prevInner.splitIndex;
        return min;
    }

    private int getMaxSplitForInner(TextItem owner, SeparatorMark mark) {
        int max = owner.text.length();
        ArrayList<SeparatorMark> list = bandSeparators.get(owner.band);
        if (list == null) return max;
        SeparatorMark nextInner = null;
        int minNextX = Integer.MAX_VALUE;
        for (SeparatorMark m : list) {
            if (isInnerOrLegacy(m.type) && m.x > mark.x && m.x < minNextX) {
                minNextX = m.x;
                nextInner = m;
            }
        }
        if (nextInner != null && nextInner.splitIndex >= 0) max = nextInner.splitIndex;
        return max;
    }

    /**
     * Remplace une portion de texte (mot corrigé) dans un TextItem et réajuste
     * les indices de césure (splitIndex) des repères intérieurs sans décaler les syllabes.
     */
    public void replaceWordInTextItem(TextItem item, int startIdx, int endIdx, String replacement) {
        if (item == null || item.text == null || replacement == null) return;
        if (startIdx < 0 || endIdx > item.text.length() || startIdx > endIdx) return;

        String before = item.text.substring(0, startIdx);
        String after = item.text.substring(endIdx);
        int oldLen = endIdx - startIdx;
        int diff = replacement.length() - oldLen;
        item.text = before + replacement + after;
        item.cachedWidth = 0;

        // Réajustement des splitIndex des repères INNER sur la bande
        if (diff != 0) {
            ArrayList<SeparatorMark> sepList = bandSeparators.get(item.band);
            if (sepList != null) {
                for (SeparatorMark sep : sepList) {
                    if (isInnerOrLegacy(sep.type) && sep.splitIndex > 0) {
                        if (sep.splitIndex >= endIdx) {
                            sep.splitIndex += diff;
                            if (sep.splitIndex < 0) sep.splitIndex = 0;
                            if (sep.splitIndex > item.text.length()) sep.splitIndex = item.text.length();
                        } else if (sep.splitIndex > startIdx) {
                            sep.splitIndex = startIdx + replacement.length();
                        }
                    }
                }
            }
        }
    }

    public static final Set<String> FRENCH_CLITICS_SET = new HashSet<>(Arrays.asList(
            "je", "j", "tu", "t", "il", "ils", "elle", "elles", "on", "nous", "vous",
            "me", "m", "te", "se", "s", "lui", "leur", "y", "en", "moi", "toi",
            "le", "la", "les", "l", "un", "une", "des", "du", "de", "d",
            "ce", "cet", "cette", "ces", "c",
            "mon", "ton", "son", "ma", "ta", "sa", "mes", "tes", "ses",
            "notre", "votre", "nos", "vos", "leurs",
            "que", "qu", "qui", "ne", "n", "si", "et", "ou", "ni", "car", "à", "au", "aux"
    ));

    public static boolean isFrenchClitic(String word) {
        if (word == null) return false;
        String clean = word.toLowerCase().trim().replaceAll("^[^a-zà-ÿ0-9'-]+|[^a-zà-ÿ0-9'-]+$", "");
        return FRENCH_CLITICS_SET.contains(clean);
    }

    /**
     * Purge chirurgicalement les repères séparateurs redondants ou incorrects sur l'ensemble des bandes :
     * 1. Le signe de départ est exclusivement la flèche verte (START) : aucun repère INNER ne doit
     *    coexister ou être superposé au début de la phrase (flèche verte unique).
     * 2. Élimine les doublons stricts de START et d'END (<= 6px).
     * 3. Élimine les doublons de séparateurs INNER (< 3px).
     * 4. Supprime tout repère INNER situé sur la fin de réplique (END).
     */
    public void sanitizeBandSeparators() {
        for (Map.Entry<Integer, ArrayList<SeparatorMark>> entry : bandSeparators.entrySet()) {
            ArrayList<SeparatorMark> list = entry.getValue();
            if (list == null || list.isEmpty()) continue;

            sortSeparators(list);

            // 1. Dédoublonnage des START et des END
            ArrayList<SeparatorMark> deduped = new ArrayList<>();
            for (SeparatorMark mark : list) {
                if (mark == null) continue;
                if (!deduped.isEmpty()) {
                    SeparatorMark last = deduped.get(deduped.size() - 1);
                    if (mark.type == SeparatorMark.Type.START && last.type == SeparatorMark.Type.START && Math.abs(mark.x - last.x) <= 6) {
                        continue;
                    }
                    if (mark.type == SeparatorMark.Type.END && last.type == SeparatorMark.Type.END && Math.abs(mark.x - last.x) <= 6) {
                        continue;
                    }
                }
                deduped.add(mark);
            }

            // 2. Élimination des séparateurs INNER superposés au début (START) ou à la fin (END) et dédoublonnage
            ArrayList<SeparatorMark> cleaned = new ArrayList<>();
            for (int i = 0; i < deduped.size(); i++) {
                SeparatorMark mark = deduped.get(i);
                if (isInnerOrLegacy(mark.type)) {
                    boolean overlapsBoundary = false;
                    for (SeparatorMark other : deduped) {
                        if (other != mark && (other.type == SeparatorMark.Type.START || other.type == SeparatorMark.Type.END) && Math.abs(other.x - mark.x) <= 4) {
                            overlapsBoundary = true;
                            break;
                        }
                    }
                    if (overlapsBoundary) {
                        // La flèche verte est l'unique signe de début : on supprime l'inner parasite au départ ou à la fin
                        continue;
                    }
                    if (!cleaned.isEmpty()) {
                        SeparatorMark last = cleaned.get(cleaned.size() - 1);
                        if (isInnerOrLegacy(last.type)) {
                            boolean distinctSplits = (last.splitIndex >= 0 && mark.splitIndex >= 0 && last.splitIndex != mark.splitIndex);
                            int dedupTol = distinctSplits ? 2 : 4;
                            if (Math.abs(mark.x - last.x) <= dedupTol) {
                                if (last.signType == SeparatorMark.SignType.DEFAULT && mark.signType != SeparatorMark.SignType.DEFAULT) {
                                    cleaned.set(cleaned.size() - 1, mark);
                                }
                                continue;
                            }
                        }
                    }
                }
                cleaned.add(mark);
            }

            list.clear();
            list.addAll(cleaned);
        }
    }
}