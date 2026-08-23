package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Panneau principal de la timeline : gère l'affichage des bandes, des formes de séparation
 * (START/END/INNER), l'édition de texte, le zoom et l'historique d'actions.
 *
 * Contient la logique d'interaction souris (clic, drag, double-clic) et délègue
 * la gestion des textes/séparateurs à `TextManager` et le rendu à `TimelineRenderer`.
 */
public class TimelinePanel extends JPanel {

    public static class ActionHistoryEntry {
        public final String roleName;
        public final String text;
        public final double startTimeSeconds;
        public final Double endTimeSeconds;
        public final boolean active;

        public ActionHistoryEntry(String roleName, String text, double startTimeSeconds, Double endTimeSeconds, boolean active) {
            this.roleName = roleName;
            this.text = text;
            this.startTimeSeconds = startTimeSeconds;
            this.endTimeSeconds = endTimeSeconds;
            this.active = active;
        }
    }

    private static class TimelineSnapshot {
        int bandCount;
        int selectedBand;
        int zoomLevelIndex;
        ArrayList<TextItem> texts;
        ArrayList<SeparatorState> separators;
        ArrayList<Integer> planMarkers;
    }

    private static class SeparatorState {
        int band;
        int x;
        SeparatorMark.Type type;
        int splitIndex;
    }

    // ================= VARIABLES =================
    private int bandCount = 4;
    private int bandHeight = 50;
    private int cursorX = 80;
    private static final double BASE_PIXELS_PER_SECOND = 80.0;
    private static final double[] ZOOM_LEVELS = {1.0, 1.5, 2.0};
    private int zoomLevelIndex = 0;

    private double currentTime = 0;
    private double pixelsPerSecond = BASE_PIXELS_PER_SECOND;
    private int offsetX = 0;
    private int selectedBand = -1;
    private boolean draggingSeparator = false;      // Ctrl+drag : déplace le symbole
    private boolean draggingPlanMarker = false;
    private boolean shiftingText = false;             // drag simple : transfère du texte
    private int draggingSeparatorBand = -1;
    private int draggingSeparatorX = Integer.MIN_VALUE;
    private int draggingPlanMarkerX = Integer.MIN_VALUE;
    private boolean suppressNextClick = false;
    private float dragPixelAccum = 0f;
    // Pixels needed to shift one character; tuned in constructor
    private int pixelsPerChar = 2;
    // For shifting text: base separator X (world coord) and previous pointer world X
    private int shiftingBaseSeparatorX = Integer.MIN_VALUE;
    private int shiftingPointerPrevX = Integer.MIN_VALUE;
    private int contextSeparatorBand = -1;
    private int contextSeparatorX = Integer.MIN_VALUE;
    private boolean caretVisible = true;
    private final Timer caretBlinkTimer;
    private boolean insertionPulseVisible = false;
    private int insertionPulseBand = -1;
    private int insertionPulseWorldX = Integer.MIN_VALUE;
    private boolean separatorsVisible = true;
    private boolean graduationsVisible = true;
    private final Timer insertionPulseTimer;
    private final AppCustomization customization = new AppCustomization();

    private final TimelineRenderer renderer = new TimelineRenderer(bandCount, cursorX);
    private final TextManager textManager = new TextManager();
    private final Deque<TimelineSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<TimelineSnapshot> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY_DEPTH = 80;
    private boolean restoringHistory = false;
    private boolean dirty = false;

    // ================= CONSTRUCTEUR =================
    /**
     * Initialise le panneau : timers (caret blink / insertion pulse), écouteurs souris,
     * et calcule la sensibilité du drag selon la police pour un déplacement fluide des caractères.
     */
    public TimelinePanel() {
        setPreferredSize(new Dimension(800, 200));
        setBackground(new Color(40, 40, 40));
        setFocusable(true);
        renderer.setCustomization(customization);
        caretBlinkTimer = new Timer(450, e -> {
            if (!textManager.isEditing()) {
                caretVisible = true;
                return;
            }
            caretVisible = !caretVisible;
            repaint();
        });
        caretBlinkTimer.start();
        insertionPulseTimer = new Timer(220, e -> {
            insertionPulseVisible = false;
            repaint();
        });
        insertionPulseTimer.setRepeats(false);

        // Estimate pixels needed per character for drag sensitivity using font metrics
        try {
            Font font = new Font(customization.timelineFontFamily != null && !customization.timelineFontFamily.isBlank()
                    ? customization.timelineFontFamily : "Arial", Font.BOLD, Math.max(18, bandHeight));
            FontMetrics fm = getFontMetrics(font);
            int charW = Math.max(4, fm.charWidth('M'));
            pixelsPerChar = Math.max(2, charW / 2);
        } catch (Exception ignored) {
            pixelsPerChar = 6;
        }

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                TextItem hoveredText = textManager.getTextAtScaled(e.getX(), e.getY(), offsetX, getHeight(), bandCount);
                setCursor(hoveredText != null ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int newWorldX = e.getX() - offsetX;

                if (shiftingText) {
                    // Clic gauche seul sur INNER : transfère des caractères sans bouger le symbole
                    int pointerX = newWorldX;
                    int delta = shiftingPointerPrevX - pointerX;
                    dragPixelAccum += delta;
                    shiftingPointerPrevX = pointerX;
                    double ratio = (double) dragPixelAccum / Math.max(1, pixelsPerChar);
                    int steps = (ratio > 0) ? (int) Math.floor(ratio) : (int) Math.ceil(ratio);
                    if (steps != 0) {
                        textManager.shiftInnerSepTextBy(draggingSeparatorBand, shiftingBaseSeparatorX, steps);
                        dragPixelAccum -= steps * pixelsPerChar;
                        repaint();
                    }
                    return;
                }

                if (draggingPlanMarker) {
                    int snappedWorldX = snapWorldXDownToTenth(newWorldX);
                    textManager.movePlanMarker(draggingPlanMarkerX, snappedWorldX);
                    draggingPlanMarkerX = snappedWorldX;
                    repaint();
                } else if (draggingSeparator) {
                    // Ctrl+drag : déplace physiquement le symbole, pas de transfert de chars
                    int snappedWorldX = snapWorldXDownToTenth(newWorldX);
                    snappedWorldX = clampSeparatorMove(draggingSeparatorBand, draggingSeparatorX, snappedWorldX);
                    textManager.moveSeparator(draggingSeparatorBand, draggingSeparatorX, snappedWorldX);
                    draggingSeparatorX = snappedWorldX;
                    repaint();
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int bandHeight = getHeight() / bandCount;
                selectedBand = e.getY() / bandHeight;

                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0
                        && SwingUtilities.isLeftMouseButton(e)) {
                    int worldX = e.getX() - offsetX;
                    Integer hitMarker = null;
                    for (Integer markerX : textManager.getPlanMarkers()) {
                        if (Math.abs(markerX - worldX) <= 8) {
                            hitMarker = markerX;
                            break;
                        }
                    }
                    if (hitMarker != null) {
                        recordUndoSnapshot();
                        draggingPlanMarker = true;
                        draggingPlanMarkerX = hitMarker;
                    } else {
                        int[] hit = textManager.findSeparatorAtScaled(
                                e.getX(), e.getY(), offsetX, getHeight(), bandCount, 8);
                        if (hit != null) {
                            recordUndoSnapshot();
                            draggingSeparator = true;
                            draggingSeparatorBand = hit[0];
                            draggingSeparatorX = hit[1];
                        }
                    }
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    // Clic gauche seul : transfert de texte autour d'un séparateur INNER
                    int[] hit = textManager.findSeparatorAtScaled(
                            e.getX(), e.getY(), offsetX, getHeight(), bandCount, 8);
                    if (hit != null && textManager.getSeparatorType(hit[0], hit[1]) == SeparatorMark.Type.INNER) {
                        recordUndoSnapshot();
                        shiftingText = true;
                        draggingSeparatorBand = hit[0];
                        shiftingBaseSeparatorX = hit[1];
                        shiftingPointerPrevX = e.getX() - offsetX;
                        dragPixelAccum = 0f;
                    }
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingSeparator || shiftingText || draggingPlanMarker) {
                    draggingSeparator = false;
                    shiftingText = false;
                    draggingPlanMarker = false;
                    draggingSeparatorBand = -1;
                    draggingSeparatorX = Integer.MIN_VALUE;
                    draggingPlanMarkerX = Integer.MIN_VALUE;
                    dragPixelAccum = 0f;
                    // reset shifting helper state
                    shiftingBaseSeparatorX = Integer.MIN_VALUE;
                    shiftingPointerPrevX = Integer.MIN_VALUE;
                    suppressNextClick = true;
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (suppressNextClick) {
                    suppressNextClick = false;
                    return;
                }

                int x = e.getX();
                int y = e.getY();
                int bandHeight = getHeight() / bandCount;
                int band = y / bandHeight;

                // Clic droit → menu contextuel sur un symbole ou repère de plan
                if (SwingUtilities.isRightMouseButton(e)) {
                    int worldX = e.getX() - offsetX;
                    for (Integer markerX : textManager.getPlanMarkers()) {
                        if (Math.abs(markerX - worldX) <= 10) {
                            showPlanMarkerContextMenu(e.getComponent(), e.getX(), e.getY(), markerX);
                            return;
                        }
                    }

                    int[] hit = textManager.findSeparatorAtScaled(x, y, offsetX, getHeight(), bandCount, 10);
                    if (hit != null) {
                        showSeparatorContextMenu(e.getComponent(), e.getX(), e.getY(), hit[0], hit[1]);
                    }
                    return;
                }

                if (e.getClickCount() == 2) {
                    // Double-clic : créer phrase si vide, ou éditer si du texte existe
                    TextItem existing = textManager.getTextAtScaled(x, y, offsetX, getHeight(), bandCount);
                    if (existing == null) {
                        existing = textManager.getTextInSegmentAt(x, y, offsetX, getHeight(), bandCount);
                    }
                    if (existing != null) {
                        boolean emptyGap = textManager.isInEmptyInnerGap(existing, x, offsetX);
                        int cursorIdx = textManager.getCursorIndexForClick(existing, x, offsetX);
                        textManager.startEditingExistingText(existing, cursorIdx);
                        if (emptyGap) {
                            triggerInsertionPulse(existing.band, x - offsetX);
                        }
                        resetCaretBlink();
                    } else if (phraseCreationListener != null) {
                        // Zone vide : création d'une nouvelle phrase uniquement.
                        selectedBand = band;
                        phraseCreationListener.onPhraseCreationRequested(band);
                    }
                    requestFocusInWindow();
                    repaint();
                    return;
                }

                // Simple clic: éditer texte existant seulement
                TextItem clickedText = textManager.getTextAtScaled(x, y, offsetX, getHeight(), bandCount);
                if (clickedText != null) {
                    boolean emptyGap = textManager.isInEmptyInnerGap(clickedText, x, offsetX);
                    int cursorIdx = textManager.getCursorIndexForClick(clickedText, x, offsetX);
                    textManager.startEditingExistingText(clickedText, cursorIdx);
                    if (emptyGap) {
                        triggerInsertionPulse(clickedText.band, x - offsetX);
                    }
                    resetCaretBlink();
                } else {
                    TextItem segmentText = textManager.getTextInSegmentAt(x, y, offsetX, getHeight(), bandCount);
                    if (segmentText != null) {
                        boolean emptyGap = textManager.isInEmptyInnerGap(segmentText, x, offsetX);
                        int cursorIdx = textManager.getCursorIndexForClick(segmentText, x, offsetX);
                        textManager.startEditingExistingText(segmentText, cursorIdx);
                        if (emptyGap) {
                            triggerInsertionPulse(segmentText.band, x - offsetX);
                        }
                        resetCaretBlink();
                    } else if (textManager.isEditing()) {
                        // Clic sur zone vide : quitte l'édition sans supprimer le texte
                        textManager.stopTyping();
                    }
                }

                requestFocusInWindow();
                repaint();
            }
        });

    }

    // ================= INTERFACE CALLBACK =================
    public interface PhraseCreationListener {
        void onPhraseCreationRequested(int band);
    }
    private PhraseCreationListener phraseCreationListener;
    public void setPhraseCreationListener(PhraseCreationListener l) { this.phraseCreationListener = l; }

    // ================= MENU CONTEXTUEL SYMBOLES =================
    private void showSeparatorContextMenu(Component parent, int screenX, int screenY, int band, int worldX) {
        SeparatorMark.Type type = textManager.getSeparatorType(band, worldX);
        if (type == null) return;

        contextSeparatorBand = band;
        contextSeparatorX = worldX;

        JPopupMenu menu = new JPopupMenu();
        menu.addPopupMenuListener(new javax.swing.event.PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(javax.swing.event.PopupMenuEvent e) {}

            @Override
            public void popupMenuWillBecomeInvisible(javax.swing.event.PopupMenuEvent e) {
                requestFocusInWindow();
            }

            @Override
            public void popupMenuCanceled(javax.swing.event.PopupMenuEvent e) {
                requestFocusInWindow();
            }
        });

        // Changer le type — seulement pour INNER et LEGACY
        if (type == SeparatorMark.Type.INNER || type == SeparatorMark.Type.LEGACY) {
            JMenu changeTypeMenu = new JMenu("Changer le symbole");

            JMenuItem toInner = new JMenuItem("Séparateur interne");
            toInner.addActionListener(ev -> {
                recordUndoSnapshot();
                textManager.setSeparatorType(band, worldX, SeparatorMark.Type.INNER);
                repaint();
            });
            JMenuItem toLegacy = new JMenuItem("Séparateur double (legacy)");
            toLegacy.addActionListener(ev -> {
                recordUndoSnapshot();
                textManager.setSeparatorType(band, worldX, SeparatorMark.Type.LEGACY);
                repaint();
            });

            changeTypeMenu.add(toInner);
            changeTypeMenu.add(toLegacy);
            menu.add(changeTypeMenu);
            menu.addSeparator();
        }

        JMenuItem supprimer = new JMenuItem("Supprimer");
        supprimer.addActionListener(ev -> {
            deleteContextSelectedSeparator();
        });
        menu.add(supprimer);

        menu.show(parent, screenX, screenY);
    }

    private void showPlanMarkerContextMenu(Component parent, int screenX, int screenY, int worldX) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem supprimer = new JMenuItem("Supprimer le repère de plan");
        supprimer.addActionListener(ev -> {
            recordUndoSnapshot();
            textManager.removePlanMarker(worldX);
            repaint();
        });
        menu.add(supprimer);
        menu.show(parent, screenX, screenY);
    }

    // ================= METHODES =================
    public boolean isEditing() {
        return textManager.isEditing();
    }

    public int getActiveBand() {
        return textManager.getActiveBand();
    }

    private int snapWorldXToTenth(int worldX) {
        double step = pixelsPerSecond * 0.1;
        if (step <= 0) return worldX;
        return (int) Math.round(Math.round(worldX / step) * step);
    }

    private int snapWorldXDownToTenth(int worldX) {
        double step = pixelsPerSecond * 0.1;
        if (step <= 0) return worldX;
        double snapped = Math.floor(worldX / step) * step;
        return (int) Math.round(snapped);
    }

    private int clampSeparatorMove(int band, int currentX, int desiredX) {
        ArrayList<SeparatorMark> separators = textManager.getBandSeparators().get(band);
        if (separators == null || separators.size() <= 1) {
            return desiredX;
        }

        int minGap = Math.max(1, (int) Math.round(pixelsPerSecond * 0.1));

        if (desiredX > currentX) {
            int nextSeparatorX = Integer.MAX_VALUE;
            for (SeparatorMark separator : separators) {
                if (separator.x > currentX && separator.x < nextSeparatorX) {
                    nextSeparatorX = separator.x;
                }
            }
            if (nextSeparatorX != Integer.MAX_VALUE) {
                desiredX = Math.min(desiredX, Math.max(currentX, nextSeparatorX - minGap));
            }
        } else if (desiredX < currentX) {
            int previousSeparatorX = Integer.MIN_VALUE;
            for (SeparatorMark separator : separators) {
                if (separator.x < currentX && separator.x > previousSeparatorX) {
                    previousSeparatorX = separator.x;
                }
            }
            if (previousSeparatorX != Integer.MIN_VALUE) {
                desiredX = Math.max(desiredX, Math.min(currentX, previousSeparatorX + minGap));
            }
        }

        return desiredX;
    }

    public void addPlanMarkerAtCursor() {
        recordUndoSnapshot();
        int markerX = snapWorldXToTenth(cursorX - offsetX);
        textManager.addPlanMarker(markerX);
        repaint();
    }

    /** Add a separator at the current cursor position (snapped) on the given band. */
    public boolean addSeparatorAtCursor(int band) {
        if (band < 0) {
            return false;
        }

        if (!textManager.isEditing() || textManager.getActiveBand() != band) {
            if (!textManager.focusOpenPhraseInBand(band)) {
                return false;
            }
        }

        recordUndoSnapshot();
        int separatorX = snapWorldXToTenth(cursorX - offsetX);
        textManager.addSeparator(band, separatorX);
        repaint();
        return true;
    }

    public boolean deleteContextSelectedSeparator() {
        if (contextSeparatorBand < 0 || contextSeparatorX == Integer.MIN_VALUE) {
            return false;
        }
        recordUndoSnapshot();
        boolean removed = textManager.removeSeparator(contextSeparatorBand, contextSeparatorX);
        contextSeparatorBand = -1;
        contextSeparatorX = Integer.MIN_VALUE;
        if (removed) {
            repaint();
        }
        return removed;
    }

    /** Start a new phrase on a band (creates start separator + empty text and enters edit). */
    public void startPhrase(int band, Role role) {
        recordUndoSnapshot();
        int worldCursorX = snapWorldXToTenth(cursorX - offsetX);
        textManager.startPhrase(band, worldCursorX, role);
        if (textManager.isEditing()) {
            resetCaretBlink();
        }
        repaint();
    }

    public boolean hasOpenPhraseInBand(int band) {
        return textManager.hasOpenPhraseInBand(band);
    }

    public boolean focusOpenPhraseInBand(int band) {
        boolean focused = textManager.focusOpenPhraseInBand(band);
        if (focused) {
            resetCaretBlink();
            repaint();
        }
        return focused;
    }

    /** End the current editing phrase by inserting an END separator at the cursor. */
    public void endPhrase() {
        recordUndoSnapshot();
        int worldCursorX = snapWorldXToTenth(cursorX - offsetX);
        textManager.endPhrase(worldCursorX);
        if (textManager.isEditing()) {
            resetCaretBlink();
        }
        repaint();
    }

    public void applyCustomization(AppCustomization c) {
        if (c == null) return;
        this.customization.bandCount = c.bandCount;
        this.customization.bandHeight = c.bandHeight;
        this.customization.timelineCursorX = c.timelineCursorX;
        this.customization.timerPanelWidth = c.timerPanelWidth;
        this.customization.timerBackground = c.timerBackground;
        this.customization.mediaBackground = c.mediaBackground;
        this.customization.timelineEvenBand = c.timelineEvenBand;
        this.customization.timelineOddBand = c.timelineOddBand;
        this.customization.timelineSelectedBand = c.timelineSelectedBand;
        this.customization.timelineGrid = c.timelineGrid;
        this.customization.timelineCursor = c.timelineCursor;
        this.customization.timelineSeparator = c.timelineSeparator;
        this.customization.bandBackgroundMode = c.bandBackgroundMode;
        this.customization.globalBandImagePath = c.globalBandImagePath;
        this.customization.perBandImagePaths = c.perBandImagePaths;
        this.customization.timelineFontFamily = c.timelineFontFamily;

        this.bandCount = Math.max(1, customization.bandCount);
        this.bandHeight = Math.max(20, customization.bandHeight);
        this.cursorX = Math.max(20, customization.timelineCursorX);
        renderer.setBandCount(this.bandCount);
        renderer.setCursorX(this.cursorX);
        renderer.setCustomization(this.customization);
        setPreferredSize(new Dimension(getPreferredSize().width, this.bandCount * this.bandHeight));
        revalidate();
        repaint();
    }

    public void setTime(double time) {
        currentTime = time;
        offsetX = (int) (-currentTime * pixelsPerSecond);
        repaint();
    }

    public double getCurrentTime() {
        return currentTime;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        renderer.render(g,
                textManager.getBandSeparators(),
                textManager.getTexts(),
                offsetX,
                textManager.getActiveBand(),
                textManager.isEditing(),
                selectedBand,
                textManager.getTextX(),
                textManager.getCurrentInput(),
                textManager.getEditingItem(),
                textManager.getCursorIndex(),
                textManager.isCursorRightSide(),
                getWidth(),
                getHeight(),
                pixelsPerSecond,
                caretVisible && textManager.isEditing(),
                separatorsVisible,
                graduationsVisible,
                textManager.getPlanMarkers());

        if (insertionPulseVisible && insertionPulseBand >= 0 && insertionPulseWorldX != Integer.MIN_VALUE) {
            Graphics2D g2 = (Graphics2D) g.create();
            int bh = getHeight() / Math.max(1, bandCount);
            int top = insertionPulseBand * bh;
            int bottom = top + bh;
            int sx = insertionPulseWorldX + offsetX;
            g2.setColor(new Color(255, 235, 90, 220));
            g2.setStroke(new BasicStroke(3f));
            g2.drawLine(sx, top + 2, sx, bottom - 2);
            g2.dispose();
        }
    }

    /** Type a character into the currently edited phrase (handles special keys). */
    public void typeChar(char c) {
        recordUndoSnapshot();
        textManager.typeChar(c);
        resetCaretBlink();
        repaint();
    }

    /** Move the edit cursor by delta positions and update the UI. */
    public void moveCursor(int delta) {
        textManager.moveCursor(delta);
        resetCaretBlink();
        repaint();
    }

    /** Move the edit cursor by a word in the given direction (-1 left, +1 right). */
    public void moveCursorByWord(int direction) {
        textManager.moveCursorByWord(direction);
        resetCaretBlink();
        repaint();
    }

    /** Move the edit cursor to the start of the current editing buffer. */
    public void moveCursorToStart() {
        textManager.moveCursorToStart();
        resetCaretBlink();
        repaint();
    }

    /** Move the edit cursor to the end of the current editing buffer. */
    public void moveCursorToEnd() {
        textManager.moveCursorToEnd();
        resetCaretBlink();
        repaint();
    }

    /** Delete a single character before the cursor in the current edit. */
    public void deleteChar() {
        recordUndoSnapshot();
        textManager.deleteChar();
        resetCaretBlink();
        repaint();
    }

    /** Delete the previous word before the cursor in the current edit. */
    public void deleteWord() {
        recordUndoSnapshot();
        textManager.deleteWord();
        resetCaretBlink();
        repaint();
    }

    /** Stop typing mode and hide the caret. */
    public void stopTyping() {
        textManager.stopTyping();
        caretVisible = false;
        repaint();
    }

    /** Paste provided text into the editing buffer at cursor. */
    public void pasteText(String text) {
        recordUndoSnapshot();
        textManager.insertText(text);
        resetCaretBlink();
        repaint();
    }

    private void resetCaretBlink() {
        caretVisible = true;
    }

    private void triggerInsertionPulse(int band, int worldX) {
        insertionPulseBand = band;
        insertionPulseWorldX = worldX;
        insertionPulseVisible = true;
        insertionPulseTimer.restart();
    }

    /** Clear all timeline content (texts and separators) with undo snapshot. */
    public void clearAll() {
        recordUndoSnapshot();
        textManager.clearAll();
        offsetX = 0;
        selectedBand = -1;
        repaint();
    }

    public int getBandCount() {
        return bandCount;
    }

    public TextManager getTextManager() {
        return textManager;
    }

    public boolean isSeparatorsVisible() {
        return separatorsVisible;
    }

    /** Toggle visibility of separators and repaint. */
    public void setSeparatorsVisible(boolean visible) {
        this.separatorsVisible = visible;
        repaint();
    }

    public boolean isGraduationsVisible() {
        return graduationsVisible;
    }

    /** Toggle visibility of time graduations and repaint. */
    public void setGraduationsVisible(boolean visible) {
        this.graduationsVisible = visible;
        repaint();
    }

    /**
     * Returns a warning message if reducing to {@code newBandCount} would hide
     * bands that already contain content, or {@code null} if nothing would be hidden.
     */
    public String getHiddenBandWarning(int newBandCount) {
        java.util.LinkedHashSet<String> roles = new java.util.LinkedHashSet<>();
        boolean hasAnonymousContent = false;

        for (TextItem t : textManager.getTexts()) {
            if (t.band >= newBandCount) {
                if (t.role != null && t.role.name != null && !t.role.name.isEmpty()) {
                    roles.add(t.role.name);
                } else {
                    hasAnonymousContent = true;
                }
            }
        }
        for (java.util.Map.Entry<Integer, java.util.ArrayList<SeparatorMark>> entry
                : textManager.getBandSeparators().entrySet()) {
            if (entry.getKey() >= newBandCount && !entry.getValue().isEmpty()) {
                hasAnonymousContent = true;
            }
        }

        if (roles.isEmpty() && !hasAnonymousContent) return null;

        StringBuilder sb = new StringBuilder(
                "Attention : des bandes qui seraient masquées contiennent du contenu.\n\n");
        if (!roles.isEmpty()) {
            sb.append("Rôle(s) concerné(s) : ").append(String.join(", ", roles));
            if (hasAnonymousContent) sb.append(" (et du texte sans rôle)");
            sb.append("\n\n");
        }
        sb.append("Le contenu sera conservé mais non visible.\nVoulez-vous continuer ?");
        return sb.toString();
    }

    /** Set the number of bands (tracks) and adjust layout, keeping undo. */
    public void setBandCount(int newBandCount) {
        recordUndoSnapshot();
        bandCount = Math.max(1, newBandCount);
        customization.bandCount = bandCount;
        renderer.setBandCount(bandCount);
        setPreferredSize(new Dimension(getPreferredSize().width, bandCount * bandHeight));

        if (selectedBand >= bandCount) {
            selectedBand = -1;
        }
        if (textManager.isEditing() && textManager.getActiveBand() >= bandCount) {
            textManager.stopTyping();
        }

        revalidate();
        repaint();
    }

    public int getSelectedBand() { return selectedBand; }
    public void setSelectedBand(int band) { this.selectedBand = band; repaint(); }

    public double getPixelsPerSecond() { return pixelsPerSecond; }

    public double getZoomLevel() {
        return ZOOM_LEVELS[zoomLevelIndex];
    }

    public boolean zoomIn() {
        if (zoomLevelIndex >= ZOOM_LEVELS.length - 1) {
            return false;
        }
        zoomLevelIndex++;
        applyZoomLevel();
        return true;
    }

    public boolean zoomOut() {
        if (zoomLevelIndex <= 0) {
            return false;
        }
        zoomLevelIndex--;
        applyZoomLevel();
        return true;
    }

    private void applyZoomLevel() {
        recordUndoSnapshot();
        double oldPixelsPerSecond = pixelsPerSecond;
        pixelsPerSecond = BASE_PIXELS_PER_SECOND * ZOOM_LEVELS[zoomLevelIndex];
        if (oldPixelsPerSecond > 0) {
            double ratio = pixelsPerSecond / oldPixelsPerSecond;
            textManager.scaleTimelineX(cursorX, ratio);
        }
        // Keep the timeline centered on the current time when zoom changes.
        setTime(currentTime);
    }

    /**
     * Renders the timeline at a given time position into a BufferedImage.
     * Used for video export.
     */
    public BufferedImage renderFrame(int width, int height, double time) {
        int frameOffsetX = (int)(-time * pixelsPerSecond);
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(getBackground());
        g2.fillRect(0, 0, width, height);
        renderer.render(g2,
                textManager.getBandSeparators(),
                textManager.getTexts(),
                frameOffsetX,
                -1,
                false,
                -1,
                0,
                "",
                null,
                0,
                false,
                width,
                height,
                pixelsPerSecond,
                false,
                separatorsVisible,
                graduationsVisible,
                textManager.getPlanMarkers());
        g2.dispose();
        return img;
    }

    public void saveProject(File file, File videoFile, java.util.ArrayList<Role> roles) {
        ProjectManager.save(file, videoFile, textManager, roles, bandCount);
        // Saving to a project file clears the dirty flag.
        clearDirty();
    }

    public boolean hasContent() {
        if (!textManager.getTexts().isEmpty()) return true;
        for (ArrayList<SeparatorMark> list : textManager.getBandSeparators().values()) {
            if (list != null && !list.isEmpty()) return true;
        }
        return false;
    }

    public File loadProject(File file, java.util.ArrayList<Role> roles) {
        recordUndoSnapshot();
        ProjectManager.LoadedProject loaded = ProjectManager.load(file, textManager, roles);
        setBandCount(loaded.bandCount);
        repaint();
        // Loading a project is considered a clean state.
        clearDirty();
        return loaded.videoFile;
    }

    public boolean isDirty() { return dirty; }
    // Callback to notify host when dirty state changes.
    private Runnable dirtyCallback = null;

    public void setDirtyCallback(Runnable cb) {
        this.dirtyCallback = cb;
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        TimelineSnapshot previous = undoStack.pop();
        pushSnapshot(redoStack, captureSnapshot());
        restoreSnapshot(previous);
        return true;
    }

    public boolean redo() {
        if (redoStack.isEmpty()) return false;
        TimelineSnapshot next = redoStack.pop();
        pushSnapshot(undoStack, captureSnapshot());
        restoreSnapshot(next);
        return true;
    }

    public List<ActionHistoryEntry> getActionHistory(int maxEntries) {
        int safeMax = Math.max(1, maxEntries);
        int currentWorldX = cursorX - offsetX;
        double pps = Math.max(1.0, pixelsPerSecond);

        ArrayList<ActionHistoryEntry> past = new ArrayList<>();
        ArrayList<ActionHistoryEntry> future = new ArrayList<>();

        for (TextItem item : textManager.getTexts()) {
            if (item == null) continue;

            int startX = item.x;
            Integer endX = findEndBoundaryX(item.band, startX);
            boolean activeNow = startX <= currentWorldX && (endX == null || currentWorldX <= endX);

            String roleName = (item.role != null && item.role.name != null && !item.role.name.isBlank())
                    ? item.role.name
                    : "Sans role";
            String text = item.text == null ? "" : item.text;
            double startSec = Math.max(0.0, (startX - cursorX) / pps);
            Double endSec = endX == null ? null : Math.max(0.0, (endX - cursorX) / pps);

            ActionHistoryEntry entry = new ActionHistoryEntry(roleName, text, startSec, endSec, activeNow);
            if (startX <= currentWorldX || activeNow) {
                past.add(entry);
            } else {
                future.add(entry);
            }
        }

        past.sort(Comparator.comparingDouble(a -> a.startTimeSeconds));
        future.sort(Comparator.comparingDouble(a -> a.startTimeSeconds));

        int takePast = Math.min(safeMax, past.size());
        int startIndex = Math.max(0, past.size() - takePast);
        ArrayList<ActionHistoryEntry> out = new ArrayList<>(past.subList(startIndex, past.size()));

        int remaining = safeMax - out.size();
        for (int i = 0; i < remaining && i < future.size(); i++) {
            out.add(future.get(i));
        }
        return out;
    }

    private Integer findEndBoundaryX(int band, int startX) {
        ArrayList<SeparatorMark> separators = textManager.getBandSeparators().get(band);
        if (separators == null || separators.isEmpty()) return null;

        Integer closest = null;
        for (SeparatorMark separator : separators) {
            if (separator == null || separator.type != SeparatorMark.Type.END) continue;
            if (separator.x > startX && (closest == null || separator.x < closest)) {
                closest = separator.x;
            }
        }
        return closest;
    }

    private void recordUndoSnapshot() {
        if (restoringHistory) return;
        pushSnapshot(undoStack, captureSnapshot());
        redoStack.clear();
        // Mark document as modified by user actions
        dirty = true;
        if (dirtyCallback != null) {
            try { dirtyCallback.run(); } catch (Throwable ignored) {}
        }
    }
    public void clearDirty() {
        boolean was = dirty;
        dirty = false;
        if (was && dirtyCallback != null) {
            try { dirtyCallback.run(); } catch (Throwable ignored) {}
        }
    }

    private void pushSnapshot(Deque<TimelineSnapshot> stack, TimelineSnapshot snapshot) {
        if (snapshot == null) return;
        stack.push(snapshot);
        while (stack.size() > MAX_HISTORY_DEPTH) {
            stack.removeLast();
        }
    }

    private TimelineSnapshot captureSnapshot() {
        TimelineSnapshot snapshot = new TimelineSnapshot();
        snapshot.bandCount = bandCount;
        snapshot.selectedBand = selectedBand;
        snapshot.zoomLevelIndex = zoomLevelIndex;
        snapshot.texts = new ArrayList<>();
        snapshot.separators = new ArrayList<>();
        snapshot.planMarkers = new ArrayList<>(textManager.getPlanMarkers());

        for (TextItem t : textManager.getTexts()) {
            TextItem copy = new TextItem(t.text, t.x, t.band);
            copy.role = t.role;
            copy.keyCode = t.keyCode;
            copy.cachedWidth = t.cachedWidth;
            snapshot.texts.add(copy);
        }

        for (java.util.Map.Entry<Integer, ArrayList<SeparatorMark>> entry : textManager.getBandSeparators().entrySet()) {
            for (SeparatorMark sep : entry.getValue()) {
                SeparatorState state = new SeparatorState();
                state.band = entry.getKey();
                state.x = sep.x;
                state.type = sep.type;
                state.splitIndex = sep.splitIndex;
                snapshot.separators.add(state);
            }
        }

        return snapshot;
    }

    private void restoreSnapshot(TimelineSnapshot snapshot) {
        restoringHistory = true;
        try {
            textManager.clearAll();
            for (TextItem t : snapshot.texts) {
                TextItem copy = new TextItem(t.text, t.x, t.band);
                copy.role = t.role;
                copy.keyCode = t.keyCode;
                copy.cachedWidth = t.cachedWidth;
                textManager.addTextItem(copy);
            }
            for (SeparatorState s : snapshot.separators) {
                textManager.addSeparator(s.band, s.x, s.type, s.splitIndex);
            }
            if (snapshot.planMarkers != null) {
                for (Integer markerX : snapshot.planMarkers) {
                    textManager.addPlanMarker(markerX);
                }
            }

            bandCount = Math.max(1, snapshot.bandCount);
            selectedBand = snapshot.selectedBand;
            zoomLevelIndex = Math.max(0, Math.min(ZOOM_LEVELS.length - 1, snapshot.zoomLevelIndex));
            pixelsPerSecond = BASE_PIXELS_PER_SECOND * ZOOM_LEVELS[zoomLevelIndex];
            customization.bandCount = bandCount;
            renderer.setBandCount(bandCount);
            setPreferredSize(new Dimension(getPreferredSize().width, bandCount * bandHeight));
            setTime(currentTime);
            revalidate();
            repaint();
        } finally {
            restoringHistory = false;
        }
    }
}