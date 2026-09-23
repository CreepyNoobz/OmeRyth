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
    public void addSeparator(int band, int x) {
        if (hasSeparatorAt(band, x)) {
            return;
        }

        // Si on est en train d'éditer une phrase sur cette bande
        if (isEditing && activeBand == band && selectedText != null) {
            Integer leftBoundary = getLeftBoundary(band, selectedText.x);
            Integer rightBoundary = getRightBoundary(band, selectedText.x);

            // Si x est à l'intérieur de la phrase en cours d'édition (même sans fin définie)
            if (leftBoundary != null && x > leftBoundary && (rightBoundary == null || x < rightBoundary)) {
                int len = currentInput.length();
                int safeCursor = Math.max(0, Math.min(cursorIndex, len));
                addSeparator(band, x, SeparatorMark.Type.INNER, safeCursor);
                cursorIndex = Math.max(0, safeCursor - 1);
                return;
            }
        }

        // Si hors édition OU si le curseur est après la fin / en dehors de la phrase :
        // On vérifie si x est dans les limites d'une autre phrase
        for (TextItem t : texts) {
            if (t.band != band) continue;
            int[] bounds = getSegmentBounds(t);
            if (x >= bounds[0] && x <= bounds[1]) {
                int splitIdx = t.text.length();
                addSeparator(band, x, SeparatorMark.Type.INNER, splitIdx);
                return;
            }
        }

        // Sinon, zone libre ou après la fin de la phrase : créer un séparateur INNER à la position x
        addSeparator(band, x, SeparatorMark.Type.INNER, -1);
    }

    public void addSeparator(int band, int x, SeparatorMark.SignType signType) {
        addSeparatorAtCursorWithSign(band, x, signType);
    }

    public void addSeparatorAtCursorWithSign(int band, int x, SeparatorMark.SignType signType) {
        if (bandSeparators.containsKey(band)) {
            for (SeparatorMark sep : bandSeparators.get(band)) {
                if (sep.x == x) {
                    return;
                }
            }
        }

        // Si on est en train d'éditer une phrase sur cette bande
        if (isEditing && activeBand == band && selectedText != null) {
            Integer leftBoundary = getLeftBoundary(band, selectedText.x);
            Integer rightBoundary = getRightBoundary(band, selectedText.x);

            if (leftBoundary != null && x > leftBoundary && (rightBoundary == null || x < rightBoundary)) {
                int len = currentInput.length();
                int safeCursor = Math.max(0, Math.min(cursorIndex, len));
                addSeparator(band, x, SeparatorMark.Type.INNER, safeCursor, signType);
                cursorIndex = Math.max(0, safeCursor - 1);
                return;
            }
        }

        for (TextItem t : texts) {
            if (t.band != band) continue;
            int[] bounds = getSegmentBounds(t);
            if (x >= bounds[0] && x <= bounds[1]) {
                int splitIdx = t.text.length();
                addSeparator(band, x, SeparatorMark.Type.INNER, splitIdx, signType);
                return;
            }
        }

        addSeparator(band, x, SeparatorMark.Type.INNER, -1, signType);
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
            if (mark.x == x && mark.type == type) {
                return mark;
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

    public int[] findSeparatorAtScaled(int mouseX, int mouseY, int offsetX, int panelHeight, int bandCount, int tolerancePx) {
        int bandHeight = panelHeight / bandCount;
        for (Map.Entry<Integer, ArrayList<SeparatorMark>> entry : bandSeparators.entrySet()) {
            int band = entry.getKey();
            int bandTop = band * bandHeight;
            int bandBottom = bandTop + bandHeight;

            for (SeparatorMark sep : entry.getValue()) {
                int sx = sep.x + offsetX;
                if (sep.isStartBoundary() || sep.isEndBoundary()) {
                    int imgHeight = Math.max(14, (int) Math.round(bandHeight * 0.40f));
                    int imgWidth = Math.max(16, (int) Math.round(imgHeight * 1.5));
                    int tolX = Math.max(tolerancePx, imgWidth / 2 + 2);
                    int yMin = bandTop;
                    int yMax = bandBottom + imgHeight + 4;

                    if (mouseX >= sx - tolX && mouseX <= sx + tolX && mouseY >= yMin && mouseY <= yMax) {
                        return new int[]{band, sep.x};
                    }
                } else {
                    if (mouseY >= bandTop && mouseY <= bandBottom) {
                        if (Math.abs(mouseX - sx) <= tolerancePx) {
                            return new int[]{band, sep.x};
                        }
                    }
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

    /** Démarre la saisie libre d'un nouveau texte à la coordonnée monde X sur la bande spécifiée. */
    public void startTyping(int band, int x) {
        this.isEditing = true;
        this.activeBand = band;
        this.textX = x;
        this.currentInput = "";
        this.selectedText = null;
        this.cursorIndex = 0;
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

        this.selectedText = item;
        this.activeBand = band;
        this.textX = cursorX;
        this.currentInput = "";
        this.isEditing = true;
        this.cursorIndex = 0;
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
            addSeparator(activeBand, cursorX, SeparatorMark.Type.END);
        }
        stopTyping();
    }

    /** Ouvre une réplique existante en mode édition et positionne le curseur à l'indice spécifié. */
    public void startEditingExistingText(TextItem text, int cursorIdx) {
        this.selectedText = text;
        this.activeBand = text.band;
        this.textX = text.x;
        this.currentInput = text.text;
        this.isEditing = true;
        this.cursorIndex = clampCursorIndex(cursorIdx);
        this.cursorRightSide = false;
    }

    /**
     * Traite la frappe d'un caractère au clavier :
     * - Insère le caractère dans le tampon courant
     * - Décale vers la droite tous les indices de découpe (splitIndex) des séparateurs internes situés après le curseur
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

    /** Quitte le mode édition et réinitialise l'état de frappe. */
    public void stopTyping() {
        isEditing = false;
        currentInput = "";
        activeBand = -1;
        selectedText = null;
        cursorIndex = 0;
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
     * Gère avec précision le franchissement des repères internes (bords gauche / droit de la coupure).
     */
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

    /** Déplace le curseur au début de la réplique (indice 0). */
    public void moveCursorToStart() {
        cursorIndex = 0;
        cursorRightSide = false;
    }

    /** Déplace le curseur à la fin de la réplique. */
    public void moveCursorToEnd() {
        cursorIndex = currentInput.length();
        cursorRightSide = true;
    }

    /** Déplace le curseur d'un mot complet vers la gauche ou vers la droite. */
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

    /**
     * Supprime le caractère à gauche du curseur (Backspace) et ajuste
     * automatiquement les coupures syllabiques des séparateurs internes situés à droite.
     */
    public void deleteChar() {
        if (!isEditing || cursorIndex == 0) return;
        int deletePos = cursorIndex - 1;
        currentInput = currentInput.substring(0, deletePos) + currentInput.substring(cursorIndex);
        shiftInnerSplitIndicesOnDelete(deletePos, 1);
        cursorIndex = deletePos;
        cursorIndex = clampCursorIndex(cursorIndex);
        if (selectedText != null) selectedText.text = currentInput;
    }

    /** Supprime le mot complet à gauche du curseur (Ctrl+Backspace). */
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

    /** Insère un bloc de texte complet au curseur (Collage / Paste). */
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

    /**
     * Convertit un clic souris (coordonnée écran mouseX) en index précis de caractère (cursorIndex)
     * au sein d'une réplique déformée élastiquement par des séparateurs internes.
     * 
     * Problématique & Algorithme géométrique :
     * 1. La phrase est divisée en plusieurs sous-intervalles géométriques [segStart, segEnd] par les repères INNER.
     * 2. Chaque intervalle contient un nombre variable de caractères : segChars = idx - prevIdx.
     * 3. On identifie dans quel sous-intervalle se trouve la coordonnée monde du clic (worldX = mouseX - offsetX).
     * 4. On calcule le ratio d'avancement linéaire local :
     *      rel = (worldX - segStart) / (segEnd - segStart)
     * 5. L'indice résultant correspond à :
     *      result = prevIdx + round(rel * segChars)
     * 6. L'état cursorRightSide est ajusté pour savoir si le caret doit clignoter avant ou après le repère.
     */
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
        int endX = Integer.MAX_VALUE;
        for (int i = startIndex + 1; i < list.size(); i++) {
            SeparatorMark m = list.get(i);
            if (m.type == SeparatorMark.Type.START) {
                break;
            }
            if (m.type == SeparatorMark.Type.END) {
                endX = m.x;
                break;
            }
        }

        // Si la phrase était en cours d'édition, stopper l'édition
        if (isEditing && activeBand == band) {
            if (selectedText != null && selectedText.x >= startX && (endX == Integer.MAX_VALUE || selectedText.x <= endX)) {
                stopTyping();
            }
        }

        final int finalEndX = endX;
        // Supprimer tous les TextItems de cette réplique
        texts.removeIf(t -> t.band == band && t.x >= startX && (finalEndX == Integer.MAX_VALUE || t.x <= finalEndX));

        // Supprimer tous les séparateurs (START, INNER, END) compris dans cette réplique
        list.removeIf(m -> m.x >= startX && (finalEndX == Integer.MAX_VALUE ? (m.x == startX || m.type != SeparatorMark.Type.START) : m.x <= finalEndX));

        if (list.isEmpty()) {
            bandSeparators.remove(band);
        }
        return true;
    }

    public Integer getStartSeparatorXForText(TextItem t) {
        if (t == null) return null;
        ArrayList<SeparatorMark> sepList = bandSeparators.get(t.band);
        if (sepList == null) return t.x;
        int leftSep = Integer.MIN_VALUE;
        for (SeparatorMark sep : sepList) {
            if (sep.isStartBoundary() && sep.x <= t.x && sep.x > leftSep) {
                leftSep = sep.x;
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
                break;
            }
            if (m.type == SeparatorMark.Type.END) {
                endX = m.x;
                break;
            }
        }

        if (endX == Integer.MIN_VALUE) {
            // Pas de séparateur END explicite : trouver l'étendue maximale
            int maxInnerOrText = startX + 300;
            for (int i = startIndex + 1; i < list.size(); i++) {
                SeparatorMark m = list.get(i);
                if (m.type == SeparatorMark.Type.START) break;
                if (m.x > maxInnerOrText) maxInnerOrText = m.x;
            }
            for (TextItem t : texts) {
                if (t.band == band && t.x >= startX) {
                    int tEnd = t.x + Math.max(300, t.text.length() * 12);
                    if (tEnd > maxInnerOrText) maxInnerOrText = tEnd;
                }
            }
            endX = maxInnerOrText;
        }

        return new int[]{startX, endX};
    }

    public Role getPhraseRole(int band, int startX) {
        int[] bounds = getPhraseBoundsAtStart(band, startX);
        int phraseStart = (bounds != null) ? bounds[0] : startX;
        int phraseEnd = (bounds != null) ? bounds[1] : (startX + 300);
        for (TextItem t : texts) {
            if (t.band == band && t.x >= phraseStart && t.x <= phraseEnd) {
                if (t.role != null) return t.role;
            }
        }
        for (TextItem t : texts) {
            if (t.band == band && t.x == startX) {
                return t.role;
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
            if (t.band == band && t.x >= phraseStart && t.x <= phraseEnd) {
                t.role = role;
                matched = true;
            }
        }
        if (!matched) {
            for (TextItem t : texts) {
                if (t.band == band && Math.abs(t.x - startX) <= 15) {
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
                if (m.x >= startX && m.x <= endX) {
                    return false;
                }
                if (m.type == SeparatorMark.Type.START) {
                    int[] pBounds = getPhraseBoundsAtStart(targetBand, m.x);
                    if (pBounds != null) {
                        if (Math.max(startX, pBounds[0]) <= Math.min(endX, pBounds[1])) {
                            return false;
                        }
                    }
                }
            }
        }

        for (TextItem t : texts) {
            if (t.band == targetBand) {
                int[] bounds = getSegmentBounds(t);
                if (Math.max(startX, bounds[0]) <= Math.min(endX, bounds[1])) {
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

        // 1. Déplacer les TextItems de la phrase
        for (TextItem t : texts) {
            if (t.band == sourceBand && t.x >= phraseStart && t.x <= phraseEnd) {
                t.band = targetBand;
            }
        }

        // 2. Extraire et déplacer les séparateurs de la phrase
        ArrayList<SeparatorMark> srcList = bandSeparators.get(sourceBand);
        if (srcList != null) {
            ArrayList<SeparatorMark> toMove = new ArrayList<>();
            for (SeparatorMark m : srcList) {
                if (m.x >= phraseStart && m.x <= phraseEnd) {
                    toMove.add(m);
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

    /**
     * Décale les indices de coupure syllabique des séparateurs internes lors de l'insertion de nouveaux caractères.
     * Tout séparateur situé après la position d'insertion voit son splitIndex incrémenté de delta.
     */
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

    /**
     * Ajuste les indices de coupure syllabique des séparateurs internes lors de la suppression de caractères.
     */
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