package app.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

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

    private boolean isEditing = false;
    private TextItem selectedText = null;
    private int textX = 0;
    private String currentInput = "";
    private int activeBand = -1;
    private int cursorIndex = 0;
    private boolean cursorRightSide = false;

    // ==================== GETTERS ====================
    public ArrayList<TextItem> getTexts() { return texts; }
    public Map<Integer, ArrayList<SeparatorMark>> getBandSeparators() { return bandSeparators; }
    public ArrayList<Integer> getPlanMarkers() { return planMarkers; }
    public boolean isEditing() { return isEditing; }
    public int getTextX() { return textX; }
    public String getCurrentInput() { return currentInput; }
    public int getActiveBand() { return activeBand; }
    public int getCursorIndex() { return cursorIndex; }
    public boolean isCursorRightSide() { return cursorRightSide; }
    public TextItem getEditingItem() { return selectedText; }

    // ==================== TEXT MANAGEMENT ====================
    /** Add a new text string at world x on the given band. */
    public void addText(String text, int x, int band) {
        texts.add(new TextItem(text, x, band));
    }

    /** Add an existing TextItem to the manager. */
    public void addTextItem(TextItem item) {
        texts.add(item);
    }

    /** Rescale timeline coordinates around an anchor (used for zoom). */
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
        }
    }

    private int scaleAroundAnchor(int value, int anchor, double ratio) {
        return (int) Math.round(anchor + (value - anchor) * ratio);
    }

    /** Add an INNER separator at x inside the currently edited phrase for band. */
    public void addSeparator(int band, int x) {
        // User separator key creates an INNER separator only inside the current phrase.
        if (!isEditing || selectedText == null || activeBand != band) return;

        Integer leftBoundary = getLeftBoundary(band, selectedText.x);
        if (leftBoundary == null) return;

        Integer rightBoundary = getRightBoundary(band, selectedText.x);
        if (x <= leftBoundary) return;
        if (rightBoundary != null && x >= rightBoundary) return;

        int len = currentInput.length();
        if (len == 0) return;

        // Texte avant le curseur = gauche du séparateur, texte après = droite.
        int safeCursor = Math.max(0, Math.min(cursorIndex, len));
        // On garde le texte intact, splitIndex indique la frontière.
        addSeparator(band, x, SeparatorMark.Type.INNER, safeCursor);
        // Positionne le curseur juste avant la frontière pour qu'une flèche droite
        // l'emmène immédiatement côté droit du séparateur.
        cursorIndex = Math.max(0, safeCursor - 1);
    }

    /** Add a separator of the specified type at x on the given band. */
    public void addSeparator(int band, int x, SeparatorMark.Type type) {
        addSeparator(band, x, type, -1);
    }

    /** Add a separator with optional split index at x on band. */
    public void addSeparator(int band, int x, SeparatorMark.Type type, int splitIndex) {
        ArrayList<SeparatorMark> list = bandSeparators.computeIfAbsent(band, k -> new ArrayList<>());
        for (SeparatorMark mark : list) {
            if (mark.x == x) {
                return;
            }
        }
        list.add(new SeparatorMark(x, type, splitIndex));
        sortSeparators(list);
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

    public int[] findSeparatorAtScaled(int mouseX, int mouseY, int offsetX, int panelHeight, int bandCount, int tolerancePx) {
        int bandHeight = panelHeight / bandCount;
        for (Map.Entry<Integer, ArrayList<SeparatorMark>> entry : bandSeparators.entrySet()) {
            int band = entry.getKey();
            int bandTop = band * bandHeight;
            int bandBottom = bandTop + bandHeight;
            if (mouseY < bandTop || mouseY > bandBottom) continue;

            for (SeparatorMark sep : entry.getValue()) {
                int sx = sep.x + offsetX;
                if (Math.abs(mouseX - sx) <= tolerancePx) {
                    return new int[]{band, sep.x};
                }
            }
        }
        return null;
    }

    /** Move an existing separator from oldX to newX on the given band. */
    public void moveSeparator(int band, int oldX, int newX) {
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return;
        for (SeparatorMark mark : list) {
            if (mark.x == oldX) {
                if (mark.type == SeparatorMark.Type.START) {
                    Integer maxX = getRightBoundary(band, oldX);
                    if (maxX != null) {
                        newX = Math.min(newX, maxX - 1);
                    }
                    TextItem owner = getTextByStart(band, oldX);
                    if (owner != null) {
                        owner.x = newX;
                        if (owner == selectedText) {
                            textX = newX;
                        }
                    }
                } else if (mark.type == SeparatorMark.Type.END) {
                    Integer minX = getLeftBoundary(band, oldX);
                    if (minX != null) {
                        newX = Math.max(newX, minX + 1);
                    }
                } else if (mark.type == SeparatorMark.Type.INNER) {
                    Integer leftX = getLeftBoundary(band, oldX);
                    Integer rightX = getRightBoundary(band, oldX);
                    if (leftX != null) newX = Math.max(newX, leftX + 1);
                    if (rightX != null) newX = Math.min(newX, rightX - 1);
                    // Le transfert de caractères est géré par shiftInnerSepText() appelé depuis TimelinePanel.
                }
                mark.x = newX;
                sortSeparators(list);
                return;
            }
        }
    }

    private Integer getRightBoundary(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return null;
        int rightSep = Integer.MAX_VALUE;
        for (SeparatorMark sep : sepList) {
            if (!sep.isEndBoundary()) continue;
            if (sep.x > x && sep.x < rightSep) rightSep = sep.x;
        }
        return (rightSep == Integer.MAX_VALUE) ? null : rightSep;
    }

    private Integer getLeftBoundary(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return null;
        int leftSep = Integer.MIN_VALUE;
        for (SeparatorMark sep : sepList) {
            if (!sep.isStartBoundary()) continue;
            if (sep.x <= x && sep.x > leftSep) leftSep = sep.x;
        }
        return (leftSep == Integer.MIN_VALUE) ? null : leftSep;
    }

    private boolean hasStartBoundaryAt(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return false;
        for (SeparatorMark sep : sepList) {
            if (sep.x == x && sep.isStartBoundary()) return true;
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

    /** Begin typing a new text at world x on the specified band. */
    public void startTyping(int band, int x) {
        this.isEditing = true;
        this.activeBand = band;
        this.textX = x;
        this.currentInput = "";
        this.selectedText = null;
        this.cursorIndex = 0;
    }

    /**
     * Crée un début de phrase : séparateur de début à cursorX,
     * un nouveau TextItem vide ancré juste après, et entre en édition.
     */
    /** Create a new phrase (start separator + empty TextItem) and enter edit mode. */
    public void startPhrase(int band, int cursorX, Role role) {
        // Tant qu'une phrase de la bande n'est pas fermée, on réédite cette phrase
        if (focusOpenPhraseInBand(band)) {
            return;
        }

        // Ne pas créer une nouvelle phrase à l'intérieur d'une phrase existante.
        if (hasPhraseAtPosition(band, cursorX)) {
            return;
        }

        // Ne pas superposer un nouveau symbole sur un symbole existant.
        if (hasSeparatorAt(band, cursorX)) {
            return;
        }

        // séparateur de début (la colonne curseur sur la timeline)
        addSeparator(band, cursorX, SeparatorMark.Type.START);

        // TextItem positionné juste après le séparateur
        TextItem item = new TextItem("", cursorX, band);
        item.role = role;
        texts.add(item);

        this.selectedText = item;
        this.activeBand = band;
        this.textX = cursorX;
        this.currentInput = "";
        this.isEditing = true;
        this.cursorIndex = 0;
    }

    /**
     * Crée un séparateur de fin à cursorX, sauvegarde le texte en cours et quitte l'édition.
     */
    /** Close the current editing phrase by adding an END separator and saving text. */
    public void endPhrase(int cursorX) {
        if (!isEditing) return;
        // On ne ferme qu'une phrase réelle (ancrée par un début)
        if (selectedText == null || !hasStartBoundaryAt(activeBand, selectedText.x)) {
            stopTyping();
            return;
        }

        // Phrase déjà fermée: on quitte l'édition sans ajouter un nouveau séparateur de fin.
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
            addSeparator(activeBand, cursorX, SeparatorMark.Type.END);
        }
        stopTyping();
    }

    /** Begin editing an existing TextItem at the specified cursor index. */
    public void startEditingExistingText(TextItem text, int cursorIdx) {
        this.selectedText = text;
        this.activeBand = text.band;
        this.textX = text.x;
        this.currentInput = text.text;
        this.isEditing = true;
        this.cursorIndex = clampCursorIndex(cursorIdx);
        this.cursorRightSide = false;
    }

    /** Insert a character into the current editing buffer (handles enter/backspace). */
    public void typeChar(char c) {
        if (!isEditing) return;

        if (c == '\n') {
            // Enter is now handled by endPhrase() called from outside (places end separator)
            // kept as fallback for simple startTyping mode (no separators)
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
            shiftInnerSplitIndicesOnInsert(cursorIndex, 1);
            currentInput = currentInput.substring(0, cursorIndex) + c + currentInput.substring(cursorIndex);
            cursorIndex++;
            if (cursorIndex == currentInput.length()) {
                cursorRightSide = true;
            }
        }

        if (selectedText != null) {
            selectedText.text = currentInput;
        }
    }

    /** Stop typing and clear the editing state. */
    public void stopTyping() {
        isEditing = false;
        currentInput = "";
        activeBand = -1;
        selectedText = null;
        cursorIndex = 0;
    }

    /** Clear all texts and separators, resetting manager state. */
    public void clearAll() {
        texts.clear();
        bandSeparators.clear();
        planMarkers.clear();
        stopTyping();
    }

    /** Move the text cursor by delta positions (supports wrap-around across inner separators). */
    public void moveCursor(int delta) {
        if (!isEditing || delta == 0) return;
        int len = currentInput.length();

        ArrayList<SeparatorMark> inner = (selectedText != null) ? getInnerMarksForSelectedPhrase() : null;
        if (selectedText != null && inner != null && !inner.isEmpty()) {
            for (SeparatorMark m : inner) {
                if (m.splitIndex == cursorIndex) {
                    if (delta < 0 && cursorRightSide) {
                        cursorRightSide = false;
                        return;
                    }
                    if (delta > 0 && !cursorRightSide) {
                        cursorRightSide = true;
                        return;
                    }
                }
            }

            if (delta < 0 && cursorIndex == 0) {
                cursorIndex = len;
                cursorRightSide = false;
                return;
            }
            if (delta > 0 && cursorIndex == len) {
                cursorIndex = 0;
                cursorRightSide = true;
                return;
            }
        }

        cursorIndex = clampCursorIndex(cursorIndex + delta);
    }

    /** Move the cursor to the start of the current editing buffer. */
    public void moveCursorToStart() {
        cursorIndex = 0;
        cursorRightSide = false;
    }

    /** Move the cursor to the end of the current editing buffer. */
    public void moveCursorToEnd() {
        cursorIndex = currentInput.length();
        cursorRightSide = true;
    }

    /** Move the cursor one word forward or backward depending on direction. */
    public void moveCursorByWord(int direction) {
        if (direction < 0) {
            int i = cursorIndex - 1;
            while (i > 0 && currentInput.charAt(i - 1) == ' ') i--;
            while (i > 0 && currentInput.charAt(i - 1) != ' ') i--;
            cursorIndex = i;
        } else {
            int i = cursorIndex;
            int len = currentInput.length();
            while (i < len && currentInput.charAt(i) == ' ') i++;
            while (i < len && currentInput.charAt(i) != ' ') i++;
            cursorIndex = i;
        }
        cursorIndex = clampCursorIndex(cursorIndex);
    }

    /** Delete a single character to the left of the cursor. */
    public void deleteChar() {
        if (!isEditing || cursorIndex == 0) return;
        int deletePos = cursorIndex - 1;
        currentInput = currentInput.substring(0, deletePos) + currentInput.substring(cursorIndex);
        shiftInnerSplitIndicesOnDelete(deletePos, 1);
        cursorIndex = deletePos;
        cursorIndex = clampCursorIndex(cursorIndex);
        if (selectedText != null) selectedText.text = currentInput;
    }

    /** Delete the last word to the left of the cursor. */
    public void deleteWord() {
        if (!isEditing || cursorIndex == 0) return;
        int i = cursorIndex - 1;
        while (i > 0 && currentInput.charAt(i - 1) == ' ') i--;
        while (i > 0 && currentInput.charAt(i - 1) != ' ') i--;
        currentInput = currentInput.substring(0, i) + currentInput.substring(cursorIndex);
        shiftInnerSplitIndicesOnDelete(i, cursorIndex - i);
        cursorIndex = i;
        cursorIndex = clampCursorIndex(cursorIndex);
        if (selectedText != null) selectedText.text = currentInput;
    }

    /** Insert a block of text at the current cursor position. */
    public void insertText(String text) {
        if (!isEditing || text == null || text.isEmpty()) return;
        String normalized = text.replace('\r', ' ').replace('\n', ' ');
        if (normalized.isEmpty()) return;
        currentInput = currentInput.substring(0, cursorIndex) + normalized + currentInput.substring(cursorIndex);
        shiftInnerSplitIndicesOnInsert(cursorIndex, normalized.length());
        cursorIndex += normalized.length();
        if (selectedText != null) {
            selectedText.text = currentInput;
        }
    }

    private int clampCursorIndex(int rawIndex) {
        return Math.max(0, Math.min(currentInput.length(), rawIndex));
    }

    // ==================== SEGMENT BOUNDS ====================
    private int[] getSegmentBounds(TextItem t) {
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

        if (leftSep != Integer.MIN_VALUE && rightSep != Integer.MAX_VALUE) {
            segmentEnd = rightSep;
        } else if (rightSep != Integer.MAX_VALUE) {
            segmentEnd = rightSep;
        } else if (leftSep != Integer.MIN_VALUE) {
            segmentEnd = leftSep + 300;
        }

        int segmentStart = (leftSep != Integer.MIN_VALUE) ? leftSep : t.x;
        return new int[]{segmentStart, segmentEnd};
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

    public TextItem getTextInSegmentAt(int mouseX, int mouseY, int offsetX, int panelHeight, int bandCount) {
        int bandHeight = panelHeight / bandCount;
        int clickBand = mouseY / bandHeight;
        int worldX = mouseX - offsetX;

        for (TextItem t : texts) {
            if (t.band != clickBand) continue;
            int[] bounds = getSegmentBounds(t);
            if (worldX >= bounds[0] && worldX <= bounds[1]) {
                return t;
            }
        }
        return null;
    }

    public int getCursorIndexForClick(TextItem t, int mouseX, int offsetX) {
        int[] bounds = getSegmentBounds(t);
        int segmentStart = bounds[0];
        int segmentEnd = bounds[1];

        int worldX = mouseX - offsetX;
        worldX = Math.max(segmentStart, Math.min(segmentEnd, worldX));

        ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
        ArrayList<SeparatorMark> innerMarks = new ArrayList<>();
        if (sepList != null) {
            for (SeparatorMark mark : sepList) {
                if (mark.type == SeparatorMark.Type.INNER && mark.x > segmentStart && mark.x < segmentEnd) {
                    innerMarks.add(mark);
                }
            }
        }

        int len = t.text.length();
        int prevX = segmentStart;
        int prevIdx = 0;

        for (SeparatorMark mark : innerMarks) {
            int segStart = prevX;
            int segEnd = mark.x;
            int idx = mark.splitIndex;
            if (idx < prevIdx) idx = prevIdx;
            if (idx > len) idx = len;

            if (worldX <= segEnd) {
                if (segEnd <= segStart) {
                    cursorRightSide = false;
                    return prevIdx;
                }
                int segChars = idx - prevIdx;
                if (segChars <= 0) {
                    cursorRightSide = true;
                    return prevIdx;
                }
                double rel = (double) (worldX - segStart) / (double) (segEnd - segStart);
                rel = Math.max(0.0, Math.min(1.0, rel));
                int result = prevIdx + (int) Math.round(rel * segChars);
                if (result == idx) {
                    cursorRightSide = worldX > mark.x;
                } else {
                    cursorRightSide = false;
                }
                return result;
            }

            prevX = mark.x;
            prevIdx = idx;
        }

        int segStart = prevX;
        int segEnd = segmentEnd;
        if (segEnd <= segStart) {
            cursorRightSide = false;
            return prevIdx;
        }
        int segChars = len - prevIdx;
        if (segChars <= 0) {
            cursorRightSide = true;
            return prevIdx;
        }
        double rel = (double) (worldX - segStart) / (double) (segEnd - segStart);
        rel = Math.max(0.0, Math.min(1.0, rel));
        int result = prevIdx + (int) Math.round(rel * segChars);
        cursorRightSide = result == len;
        return result;
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
        for (SeparatorMark mark : sepList) {
            if (mark.type == SeparatorMark.Type.INNER && mark.x > segmentStart && mark.x < segmentEnd) {
                innerMarks.add(mark);
            }
        }
        if (innerMarks.isEmpty()) return false;

        int len = t.text.length();
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
        int bandHeight = panelHeight / bandCount;

        for (TextItem t : texts) {
            int bandY = t.band * bandHeight + bandHeight - 20;

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

            if (leftSep != Integer.MIN_VALUE) segmentStart = leftSep;
            if (rightSep != Integer.MAX_VALUE) segmentEnd = rightSep;

            int availableWidth = segmentEnd - t.x;
            if (availableWidth <= 0) continue;

            // On utilise FontMetrics approximatif
            int textWidth = t.text.length() * 10; // approximation si tu n’as pas Graphics ici
            double scale = (textWidth > 0) ? ((double) availableWidth / textWidth) : 1.0;

            int scaledStartX = t.x + offsetX;
            int scaledEndX = (int)(scaledStartX + textWidth * scale);

            int textTopY = bandY - 36; // approximé font height
            int textBottomY = bandY;

            if (mouseX >= scaledStartX && mouseX <= scaledEndX &&
                mouseY >= textTopY && mouseY <= textBottomY) {
                return t;
            }
        }

        return null;
    }

    private boolean hasSeparatorAt(int band, int x) {
        ArrayList<SeparatorMark> sepList = bandSeparators.get(band);
        if (sepList == null) return false;
        for (SeparatorMark sep : sepList) {
            if (sep.x == x) return true;
        }
        return false;
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
            int len = owner.text.length();
            int minSplit = getMinSplitForInner(owner, mark);
            int maxSplit = getMaxSplitForInner(owner, mark);
            int newSplit = Math.max(minSplit, Math.min(maxSplit, mark.splitIndex + direction));
            mark.splitIndex = newSplit;
            // Si on est en cours d'édition de cette phrase, mettre à jour aussi cursorIndex
            if (owner == selectedText) {
                cursorIndex = Math.max(0, Math.min(currentInput.length(), cursorIndex));
            }
            return;
        }
    }

    /**
     * Shift an INNER separator by multiple characters (positive -> move right, negative -> left)
     * Applied in a single operation to reduce UI jank when dragging.
     */
    public void shiftInnerSepTextBy(int band, int x, int delta) {
        if (delta == 0) return;
        ArrayList<SeparatorMark> list = bandSeparators.get(band);
        if (list == null) return;
        for (SeparatorMark mark : list) {
            if (mark.x != x || mark.type != SeparatorMark.Type.INNER) continue;
            TextItem owner = getTextForInnerSeparator(band, x);
            if (owner == null || owner.text == null || owner.text.isEmpty()) return;
            int minSplit = getMinSplitForInner(owner, mark);
            int maxSplit = getMaxSplitForInner(owner, mark);
            int newSplit = Math.max(minSplit, Math.min(maxSplit, mark.splitIndex + delta));
            mark.splitIndex = newSplit;
            if (owner == selectedText) {
                cursorIndex = Math.max(0, Math.min(currentInput.length(), cursorIndex));
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

    private void sortSeparators(ArrayList<SeparatorMark> list) {
        Collections.sort(list, Comparator.comparingInt(m -> m.x));
    }

    private TextItem getTextByStart(int band, int startX) {
        for (TextItem t : texts) {
            if (t.band == band && t.x == startX) return t;
        }
        return null;
    }

    private TextItem getTextForInnerSeparator(int band, int sepX) {
        TextItem owner = null;
        for (TextItem t : texts) {
            if (t.band != band) continue;
            Integer left = getLeftBoundary(band, t.x);
            Integer right = getRightBoundary(band, t.x);
            if (left == null || right == null) continue;
            if (sepX > left && sepX < right) {
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
        Integer left = getLeftBoundary(selectedText.band, selectedText.x);
        Integer right = getRightBoundary(selectedText.band, selectedText.x);
        if (left == null || right == null) return result;
        for (SeparatorMark m : list) {
            if (m.type == SeparatorMark.Type.INNER && m.x > left && m.x < right) {
                result.add(m);
            }
        }
        return result;
    }

    private void rotateInnerSplitIndices(TextItem phrase, int cursor) {
        if (phrase == null) return;
        ArrayList<SeparatorMark> list = bandSeparators.get(phrase.band);
        if (list == null) return;
        int len = phrase.text.length();
        if (len <= 0) return;
        Integer left = getLeftBoundary(phrase.band, phrase.x);
        Integer right = getRightBoundary(phrase.band, phrase.x);
        if (left == null || right == null) return;
        for (SeparatorMark m : list) {
            if (m.type != SeparatorMark.Type.INNER) continue;
            if (m.x <= left || m.x >= right) continue;
            if (m.splitIndex < 0) continue;
            if (m.splitIndex >= cursor) {
                m.splitIndex = m.splitIndex - cursor;
            } else {
                m.splitIndex = len - cursor + m.splitIndex;
            }
        }
    }

    private void shiftInnerSplitIndicesOnInsert(int atIndex, int delta) {
        if (selectedText == null || delta <= 0) return;
        for (SeparatorMark m : getInnerMarksForSelectedPhrase()) {
            if (cursorRightSide && m.splitIndex == atIndex) {
                continue;
            }
            if (m.splitIndex >= atIndex) {
                m.splitIndex += delta;
            }
        }
    }

    private void shiftInnerSplitIndicesOnDelete(int startIndex, int deletedLen) {
        if (selectedText == null || deletedLen <= 0) return;
        int endIndex = startIndex + deletedLen;
        for (SeparatorMark m : getInnerMarksForSelectedPhrase()) {
            if (m.splitIndex >= endIndex) {
                m.splitIndex -= deletedLen;
            } else if (m.splitIndex > startIndex) {
                m.splitIndex = startIndex;
            }
        }
    }

    private int getMinSplitForInner(TextItem owner, SeparatorMark mark) {
        int min = 0;
        ArrayList<SeparatorMark> list = bandSeparators.get(owner.band);
        if (list == null) return min;
        int leftX = Integer.MIN_VALUE;
        SeparatorMark prevInner = null;
        for (SeparatorMark m : list) {
            if (m.x >= mark.x) break;
            if (m.isStartBoundary()) leftX = m.x;
            if (m.type == SeparatorMark.Type.INNER && m.x > leftX) prevInner = m;
        }
        if (prevInner != null && prevInner.splitIndex >= 0) min = prevInner.splitIndex;
        return min;
    }

    private int getMaxSplitForInner(TextItem owner, SeparatorMark mark) {
        int max = owner.text.length();
        ArrayList<SeparatorMark> list = bandSeparators.get(owner.band);
        if (list == null) return max;
        SeparatorMark nextInner = null;
        int rightX = Integer.MAX_VALUE;
        for (SeparatorMark m : list) {
            if (m.x <= mark.x) continue;
            if (m.isEndBoundary()) {
                rightX = m.x;
                break;
            }
        }
        for (SeparatorMark m : list) {
            if (m.x <= mark.x) continue;
            if (m.type == SeparatorMark.Type.INNER && m.x < rightX) {
                nextInner = m;
                break;
            }
        }
        if (nextInner != null && nextInner.splitIndex >= 0) max = nextInner.splitIndex;
        return max;
    }
}