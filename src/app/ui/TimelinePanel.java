package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Line2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        SeparatorMark.SignType signType;
        String rawDetxType;
        int splitIndex;
    }

    // ================= VARIABLES =================
    private int bandCount = 4;
    private int bandHeight = 50;
    private int cursorX = 80;
    private static final double BASE_PIXELS_PER_SECOND = 80.0;
    private static final double[] ZOOM_LEVELS = {1.0, 1.5, 2.0, 2.5, 3.0};
    private int zoomLevelIndex = 0;

    private double currentTime = 0;
    private double pixelsPerSecond = BASE_PIXELS_PER_SECOND;
    private double offsetX = 80.0;

    public int getIntOffsetX() {
        return (int) Math.round(offsetX);
    }
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
    private ArrayList<Role> roles = new ArrayList<>();
    private app.services.AudioWaveformData waveformData;

    private final TimelineRenderer renderer = new TimelineRenderer(bandCount, cursorX);
    private final TextManager textManager = new TextManager();

    public void setWaveformData(app.services.AudioWaveformData data) {
        this.waveformData = data;
        repaint();
    }

    public app.services.AudioWaveformData getWaveformData() {
        return waveformData;
    }
    private final Deque<TimelineSnapshot> undoStack = new ArrayDeque<>();
    private final Deque<TimelineSnapshot> redoStack = new ArrayDeque<>();
    private static final int MAX_HISTORY_DEPTH = 80;
    private boolean restoringHistory = false;
    private boolean dirty = false;
    private boolean hasMovedDuringDrag = false;

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
                int intOffsetX = getIntOffsetX();
                TextItem hoveredText = textManager.getTextAtScaled(e.getX(), e.getY(), intOffsetX, getHeight(), bandCount);
                int[] hitSep = textManager.findSeparatorAtScaled(e.getX(), e.getY(), intOffsetX, getHeight(), bandCount, 8);
                boolean hitMarker = false;
                int worldX = e.getX() - intOffsetX;
                for (Integer markerX : textManager.getPlanMarkers()) {
                    if (Math.abs(markerX - worldX) <= 8) {
                        hitMarker = true;
                        break;
                    }
                }
                if (hitSep != null || hitMarker || hoveredText != null) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int intOffsetX = getIntOffsetX();
                int newWorldX = e.getX() - intOffsetX;

                if (shiftingText) {
                    // Clic gauche seul sur INNER : transfère des caractères sans bouger le symbole
                    int pointerX = newWorldX;
                    int delta = shiftingPointerPrevX - pointerX;
                    dragPixelAccum += delta;
                    shiftingPointerPrevX = pointerX;
                    double ratio = (double) dragPixelAccum / Math.max(1, pixelsPerChar);
                    int steps = (ratio > 0) ? (int) Math.floor(ratio) : (int) Math.ceil(ratio);
                    if (steps != 0) {
                        if (!hasMovedDuringDrag) {
                            recordUndoSnapshot();
                            hasMovedDuringDrag = true;
                        }
                        textManager.shiftInnerSepTextBy(draggingSeparatorBand, shiftingBaseSeparatorX, steps);
                        dragPixelAccum -= steps * pixelsPerChar;
                        repaint();
                    }
                    return;
                }

                if (draggingPlanMarker) {
                    int snappedWorldX = snapWorldXDownToTenth(newWorldX);
                    if (snappedWorldX != draggingPlanMarkerX) {
                        if (!hasMovedDuringDrag) {
                            recordUndoSnapshot();
                            hasMovedDuringDrag = true;
                        }
                        textManager.movePlanMarker(draggingPlanMarkerX, snappedWorldX);
                        draggingPlanMarkerX = snappedWorldX;
                        repaint();
                    }
                } else if (draggingSeparator) {
                    // Ctrl+drag : déplace physiquement le symbole, pas de transfert de chars
                    int snappedWorldX = snapWorldXDownToTenth(newWorldX);
                    snappedWorldX = clampSeparatorMove(draggingSeparatorBand, draggingSeparatorX, snappedWorldX);
                    if (snappedWorldX != draggingSeparatorX) {
                        if (!hasMovedDuringDrag) {
                            recordUndoSnapshot();
                            hasMovedDuringDrag = true;
                        }
                        textManager.moveSeparator(draggingSeparatorBand, draggingSeparatorX, snappedWorldX);
                        draggingSeparatorX = snappedWorldX;
                        repaint();
                    }
                }
            }
        });

        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int h = getHeight() > 0 ? getHeight() : Math.max(20, TimelinePanel.this.bandHeight * Math.max(1, bandCount));
                int bHeight = Math.max(1, h / Math.max(1, bandCount));
                selectedBand = e.getY() / bHeight;
                hasMovedDuringDrag = false;

                int intOffsetX = getIntOffsetX();
                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0
                        && SwingUtilities.isLeftMouseButton(e)) {
                    int worldX = e.getX() - intOffsetX;
                    Integer hitMarker = null;
                    for (Integer markerX : textManager.getPlanMarkers()) {
                        if (Math.abs(markerX - worldX) <= 8) {
                            hitMarker = markerX;
                            break;
                        }
                    }
                    if (hitMarker != null) {
                        draggingPlanMarker = true;
                        draggingPlanMarkerX = hitMarker;
                    } else {
                        int[] hit = textManager.findSeparatorAtScaled(
                                e.getX(), e.getY(), intOffsetX, getHeight(), bandCount, 8);
                        if (hit != null) {
                            draggingSeparator = true;
                            draggingSeparatorBand = hit[0];
                            draggingSeparatorX = hit[1];
                        }
                    }
                } else if (SwingUtilities.isLeftMouseButton(e)) {
                    // Clic gauche seul : transfert de texte autour d'un séparateur INNER
                    int[] hit = textManager.findSeparatorAtScaled(
                            e.getX(), e.getY(), intOffsetX, getHeight(), bandCount, 8);
                    if (hit != null && textManager.getSeparatorType(hit[0], hit[1]) == SeparatorMark.Type.INNER) {
                        shiftingText = true;
                        draggingSeparatorBand = hit[0];
                        shiftingBaseSeparatorX = hit[1];
                        shiftingPointerPrevX = e.getX() - intOffsetX;
                        dragPixelAccum = 0f;
                    }
                }
                repaint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (draggingSeparator || shiftingText || draggingPlanMarker) {
                    if (hasMovedDuringDrag) {
                        suppressNextClick = true;
                    }
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
                    hasMovedDuringDrag = false;
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (suppressNextClick) {
                    suppressNextClick = false;
                    return;
                }

                int intOffsetX = getIntOffsetX();
                int x = e.getX();
                int y = e.getY();
                int h = getHeight() > 0 ? getHeight() : Math.max(20, TimelinePanel.this.bandHeight * Math.max(1, bandCount));
                int bHeight = Math.max(1, h / Math.max(1, bandCount));
                int band = y / bHeight;

                // Clic droit → menu contextuel sur un symbole ou repère de plan ou texte de phrase
                if (SwingUtilities.isRightMouseButton(e)) {
                    int worldX = e.getX() - intOffsetX;
                    for (Integer markerX : textManager.getPlanMarkers()) {
                        if (Math.abs(markerX - worldX) <= 10) {
                            showPlanMarkerContextMenu(e.getComponent(), e.getX(), e.getY(), markerX);
                            return;
                        }
                    }

                    int[] hit = textManager.findSeparatorAtScaled(x, y, intOffsetX, getHeight(), bandCount, 10);
                    if (hit != null) {
                        showSeparatorContextMenu(e.getComponent(), e.getX(), e.getY(), hit[0], hit[1]);
                        return;
                    }

                    TextItem existing = textManager.getTextAtScaled(x, y, intOffsetX, getHeight(), bandCount);
                    if (existing == null) {
                        existing = textManager.getTextInSegmentAt(x, y, intOffsetX, getHeight(), bandCount);
                    }
                    if (existing != null) {
                        showPhraseContextMenu(e.getComponent(), e.getX(), e.getY(), existing);
                        return;
                    }
                    return;
                }

                // Clic gauche sur le début de phrase (séparateur START) -> menu pour changer de bande
                if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
                    int[] hit = textManager.findSeparatorAtScaled(x, y, intOffsetX, getHeight(), bandCount, 10);
                    if (hit != null && textManager.getSeparatorType(hit[0], hit[1]) == SeparatorMark.Type.START) {
                        showSeparatorContextMenu(e.getComponent(), e.getX(), e.getY(), hit[0], hit[1]);
                        return;
                    }
                }

                if (e.getClickCount() == 2) {
                    // Double-clic : créer phrase si vide, ou éditer si du texte existe
                    TextItem existing = textManager.getTextAtScaled(x, y, intOffsetX, getHeight(), bandCount);
                    if (existing == null) {
                        existing = textManager.getTextInSegmentAt(x, y, intOffsetX, getHeight(), bandCount);
                    }
                    if (existing != null) {
                        boolean emptyGap = textManager.isInEmptyInnerGap(existing, x, intOffsetX);
                        int cursorIdx = textManager.getCursorIndexForClick(existing, x, intOffsetX);
                        textManager.startEditingExistingText(existing, cursorIdx);
                        if (emptyGap) {
                            triggerInsertionPulse(existing.band, x - intOffsetX);
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
                TextItem clickedText = textManager.getTextAtScaled(x, y, intOffsetX, getHeight(), bandCount);
                if (clickedText != null) {
                    boolean emptyGap = textManager.isInEmptyInnerGap(clickedText, x, intOffsetX);
                    int cursorIdx = textManager.getCursorIndexForClick(clickedText, x, intOffsetX);
                    textManager.startEditingExistingText(clickedText, cursorIdx);
                    if (emptyGap) {
                        triggerInsertionPulse(clickedText.band, x - intOffsetX);
                    }
                    resetCaretBlink();
                } else {
                    TextItem segmentText = textManager.getTextInSegmentAt(x, y, intOffsetX, getHeight(), bandCount);
                    if (segmentText != null) {
                        boolean emptyGap = textManager.isInEmptyInnerGap(segmentText, x, intOffsetX);
                        int cursorIdx = textManager.getCursorIndexForClick(segmentText, x, intOffsetX);
                        textManager.startEditingExistingText(segmentText, cursorIdx);
                        if (emptyGap) {
                            triggerInsertionPulse(segmentText.band, x - intOffsetX);
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

        // Pour un séparateur START (début de phrase) : proposer "Changer le rôle", "Changer de bande" et "Supprimer la phrase"
        if (type == SeparatorMark.Type.START) {
            JMenuItem changerRole = new JMenuItem("🎭 Changer le rôle...");
            changerRole.addActionListener(ev -> {
                changePhraseRole(band, worldX);
            });
            menu.add(changerRole);
            menu.addSeparator();

            if (bandCount > 1) {
                JMenu changerBandeMenu = new JMenu("Changer de bande");
                for (int b = 0; b < bandCount; b++) {
                    final int targetB = b;
                    if (targetB == band) continue;
                    String label = "Bande " + (targetB + 1);
                    JMenuItem item = new JMenuItem(label);
                    item.addActionListener(ev -> {
                        movePhraseToBandWithFeedback(band, worldX, targetB);
                    });
                    changerBandeMenu.add(item);
                }
                menu.add(changerBandeMenu);
                menu.addSeparator();
            }

            JMenuItem supprimerPhrase = new JMenuItem("Supprimer la phrase");
            supprimerPhrase.addActionListener(ev -> {
                deletePhraseAtStartSeparator(band, worldX);
            });
            menu.add(supprimerPhrase);
        } else {
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
        }

        menu.show(parent, screenX, screenY);
    }

    private void showPhraseContextMenu(Component parent, int screenX, int screenY, TextItem item) {
        if (item == null) return;
        Integer startX = textManager.getStartSeparatorXForText(item);
        if (startX == null) {
            startX = item.x;
        }
        final int phraseStartX = startX;
        final int phraseBand = item.band;

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

        JMenuItem changerRole = new JMenuItem("🎭 Changer le rôle...");
        changerRole.addActionListener(ev -> {
            changePhraseRole(phraseBand, phraseStartX);
        });
        menu.add(changerRole);
        menu.addSeparator();

        if (bandCount > 1) {
            JMenu changerBandeMenu = new JMenu("Changer de bande");
            for (int b = 0; b < bandCount; b++) {
                final int targetB = b;
                if (targetB == phraseBand) continue;
                String label = "Bande " + (targetB + 1);
                JMenuItem bItem = new JMenuItem(label);
                bItem.addActionListener(ev -> {
                    movePhraseToBandWithFeedback(phraseBand, phraseStartX, targetB);
                });
                changerBandeMenu.add(bItem);
            }
            menu.add(changerBandeMenu);
            menu.addSeparator();
        }

        JMenuItem supprimerPhrase = new JMenuItem("Supprimer la phrase");
        supprimerPhrase.addActionListener(ev -> {
            deletePhraseAtStartSeparator(phraseBand, phraseStartX);
        });
        menu.add(supprimerPhrase);

        menu.show(parent, screenX, screenY);
    }

    public void changePhraseRole(int band, int phraseStartX) {
        if (roles == null) {
            roles = new ArrayList<>();
        }
        Role currentRole = textManager.getPhraseRole(band, phraseStartX);
        Role chosen = RoleWindow.pickRole(this, roles, currentRole);
        if (chosen != null) {
            recordUndoSnapshot();
            Role targetRole = (chosen == RoleWindow.NO_ROLE) ? null : chosen;
            textManager.setPhraseRole(band, phraseStartX, targetRole);
            markDirty();
            repaint();
            requestFocusInWindow();
        }
    }

    public void setRoles(ArrayList<Role> roles) {
        this.roles = (roles != null) ? roles : new ArrayList<>();
    }

    public ArrayList<Role> getRoles() {
        return roles;
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

    public int snapWorldXToTenth(double worldX) {
        double step = pixelsPerSecond * 0.1;
        if (step <= 0) return (int) Math.round(worldX);
        return (int) Math.round(Math.round(worldX / step) * step);
    }

    public int snapWorldXToTenth(int worldX) {
        return snapWorldXToTenth((double) worldX);
    }

    public int snapWorldXDownToTenth(double worldX) {
        double step = pixelsPerSecond * 0.1;
        if (step <= 0) return (int) Math.round(worldX);
        return (int) Math.round(Math.floor(worldX / step) * step);
    }

    public int snapWorldXDownToTenth(int worldX) {
        return snapWorldXDownToTenth((double) worldX);
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
            return Math.min(desiredX, nextSeparatorX - minGap);
        } else if (desiredX < currentX) {
            int prevSeparatorX = Integer.MIN_VALUE;
            for (SeparatorMark separator : separators) {
                if (separator.x < currentX && separator.x > prevSeparatorX) {
                    prevSeparatorX = separator.x;
                }
            }
            return Math.max(desiredX, prevSeparatorX + minGap);
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
        return addSeparatorAtCursor(band, SeparatorMark.SignType.DEFAULT);
    }

    public boolean addSeparatorAtCursor(int band, SeparatorMark.SignType signType) {
        if (band < 0) {
            band = selectedBand >= 0 ? selectedBand : 0;
        }

        recordUndoSnapshot();
        int separatorX = snapWorldXToTenth(cursorX - offsetX);
        textManager.addSeparator(band, separatorX, signType);
        markDirty();
        repaint();
        return true;
    }

    /** Supprime la phrase complète (texte, début, internes, fin) commençant au séparateur START. */
    public boolean deletePhraseAtStartSeparator(int band, int startX) {
        recordUndoSnapshot();
        boolean removed = textManager.deleteFullPhraseAtStart(band, startX);
        if (removed) {
            markDirty();
            repaint();
        }
        return removed;
    }

    /** Déplace une phrase vers une autre bande avec vérification d'espace et message d'erreur si occupé. */
    public boolean movePhraseToBandWithFeedback(int sourceBand, int startX, int targetBand) {
        if (sourceBand == targetBand) return true;
        if (targetBand < 0 || targetBand >= bandCount) {
            try {
                JOptionPane.showMessageDialog(this,
                        "La bande cible (Bande " + (targetBand + 1) + ") n'existe pas.",
                        "Erreur de déplacement",
                        JOptionPane.ERROR_MESSAGE);
            } catch (HeadlessException ignored) {}
            return false;
        }

        int[] bounds = textManager.getPhraseBoundsAtStart(sourceBand, startX);
        if (bounds == null) {
            try {
                JOptionPane.showMessageDialog(this,
                        "Impossible de trouver la phrase sélectionnée.",
                        "Erreur",
                        JOptionPane.ERROR_MESSAGE);
            } catch (HeadlessException ignored) {}
            return false;
        }

        if (!textManager.isSpaceFreeOnBand(targetBand, bounds[0], bounds[1])) {
            try {
                JOptionPane.showMessageDialog(this,
                        "Impossible de déplacer la phrase : l'espace est déjà occupé sur la Bande " + (targetBand + 1) + ".",
                        "Espace occupé",
                        JOptionPane.WARNING_MESSAGE);
            } catch (HeadlessException ignored) {}
            return false;
        }

        recordUndoSnapshot();
        boolean moved = textManager.movePhraseToBand(sourceBand, startX, targetBand);
        if (moved) {
            selectedBand = targetBand;
            markDirty();
            repaint();
        }
        return moved;
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
        markDirty();
        repaint();
    }

    public void endPhraseAtCursor(int band) {
        if (textManager.isEditing()) {
            endPhrase();
            return;
        }
        if (band < 0) {
            band = selectedBand >= 0 ? selectedBand : 0;
        }
        if (textManager.hasOpenPhraseInBand(band)) {
            textManager.focusOpenPhraseInBand(band);
            endPhrase();
            return;
        }
        recordUndoSnapshot();
        int worldCursorX = snapWorldXToTenth(cursorX - offsetX);
        textManager.addSeparator(band, worldCursorX, SeparatorMark.Type.END, -1, SeparatorMark.SignType.DEFAULT);
        markDirty();
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
        this.customization.showWaveform = c.showWaveform;
        this.customization.waveformColor = c.waveformColor;
        this.customization.defaultProjectFormat = c.defaultProjectFormat;

        this.bandCount = Math.max(1, customization.bandCount);
        this.bandHeight = Math.max(20, customization.bandHeight);
        this.cursorX = Math.max(20, customization.timelineCursorX);
        renderer.setBandCount(this.bandCount);
        renderer.setCursorX(this.cursorX);
        renderer.setCustomization(this.customization);
        offsetX = cursorX - (currentTime * pixelsPerSecond);
        setPreferredSize(new Dimension(getPreferredSize().width, this.bandCount * this.bandHeight));
        revalidate();
        repaint();
    }

    public void setTime(double time) {
        currentTime = time;
        offsetX = cursorX - (currentTime * pixelsPerSecond);
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
                textManager.getPlanMarkers(),
                waveformData);

        if (insertionPulseVisible && insertionPulseBand >= 0 && insertionPulseWorldX != Integer.MIN_VALUE) {
            Graphics2D g2 = (Graphics2D) g.create();
            int bh = getHeight() / Math.max(1, bandCount);
            int top = insertionPulseBand * bh;
            int bottom = top + bh;
            double sx = insertionPulseWorldX + offsetX;
            g2.setColor(new Color(255, 235, 90, 220));
            g2.setStroke(new BasicStroke(3f));
            g2.draw(new Line2D.Double(sx, top + 2, sx, bottom - 2));
            g2.dispose();
        }
    }

    /** Type a character into the currently edited phrase (handles special keys). */
    public void typeChar(char c) {
        if (!textManager.isEditing()) return;
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
        if (!textManager.isEditing()) return;
        recordUndoSnapshot();
        textManager.deleteChar();
        resetCaretBlink();
        repaint();
    }

    /** Delete the previous word before the cursor in the current edit. */
    public void deleteWord() {
        if (!textManager.isEditing()) return;
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
        if (!textManager.isEditing() || text == null || text.isEmpty()) return;
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
        offsetX = cursorX - (currentTime * pixelsPerSecond);
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

    public boolean isWaveformVisible() {
        return customization.showWaveform;
    }

    /** Toggle visibility of audio waveform and repaint. */
    public void setWaveformVisible(boolean visible) {
        this.customization.showWaveform = visible;
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

    /** Returns the X coordinate of the playhead cursor in screen/panel space. */
    public int getCursorX() { return cursorX; }

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

    private void scaleSnapshots(java.util.Collection<TimelineSnapshot> stack, double ratio) {
        if (stack == null || ratio <= 0 || Math.abs(ratio - 1.0) < 1e-9) return;
        for (TimelineSnapshot s : stack) {
            if (s == null) continue;
            s.zoomLevelIndex = zoomLevelIndex;
            for (TextItem t : s.texts) {
                t.x = (int) Math.round(t.x * ratio);
            }
            for (SeparatorState sep : s.separators) {
                sep.x = (int) Math.round(sep.x * ratio);
            }
            if (s.planMarkers != null) {
                for (int i = 0; i < s.planMarkers.size(); i++) {
                    s.planMarkers.set(i, (int) Math.round(s.planMarkers.get(i) * ratio));
                }
            }
        }
    }

    private void applyZoomLevel() {
        double oldPixelsPerSecond = pixelsPerSecond;
        pixelsPerSecond = BASE_PIXELS_PER_SECOND * ZOOM_LEVELS[zoomLevelIndex];
        if (oldPixelsPerSecond > 0) {
            double ratio = pixelsPerSecond / oldPixelsPerSecond;
            textManager.scaleTimelineX(0, ratio);
            scaleSnapshots(undoStack, ratio);
            scaleSnapshots(redoStack, ratio);
        }
        // Keep the timeline centered on the current time when zoom changes.
        setTime(currentTime);
    }

    /**
    /**
     * Session de rendu optimisée pour l'export vidéo.
     * Met en cache les textes, séparateurs et marqueurs dimensionnés une fois pour toutes,
     * et utilise un TimelineRenderer dédié indépendant de l'affichage UI.
     */
    public static class ExportSession {
        private final int width;
        private final int height;
        private final int cx;
        private final double pps;
        private final ArrayList<TextItem> renderTexts;
        private final Map<Integer, ArrayList<SeparatorMark>> renderSeparators;
        private final ArrayList<Integer> renderMarkers;
        private final TimelineRenderer dedicatedRenderer;
        private final Color backgroundColor;
        private final boolean separatorsVisible;
        private final boolean graduationsVisible;
        private final app.services.AudioWaveformData waveformData;

        public ExportSession(int width, int height, double visibleSecondsAhead, TimelinePanel panel) {
            this.width = Math.max(2, width);
            this.height = Math.max(2, height);

            this.cx = Math.max(60, (int) Math.round(this.width * 0.08));
            double visibleWidth = Math.max(100.0, this.width - this.cx);

            if (visibleSecondsAhead > 0.5) {
                this.pps = visibleWidth / visibleSecondsAhead;
            } else {
                double baseH = Math.max(100.0, panel.getHeight() > 0 ? (double) panel.getHeight() : 200.0);
                double scale = (double) this.height / baseH;
                this.pps = panel.pixelsPerSecond * (scale > 0 ? scale : 1.0);
            }

            double currentPps = (panel.pixelsPerSecond > 0) ? panel.pixelsPerSecond : BASE_PIXELS_PER_SECOND;
            double coordScale = this.pps / currentPps;

            this.renderTexts = new ArrayList<>();
            for (TextItem t : panel.textManager.getTexts()) {
                TextItem scaledItem = new TextItem(t.text, (int) Math.round(t.x * coordScale), t.band);
                scaledItem.role = t.role;
                this.renderTexts.add(scaledItem);
            }

            this.renderSeparators = new HashMap<>();
            for (Map.Entry<Integer, ArrayList<SeparatorMark>> entry : panel.textManager.getBandSeparators().entrySet()) {
                ArrayList<SeparatorMark> scaledList = new ArrayList<>();
                for (SeparatorMark m : entry.getValue()) {
                    scaledList.add(new SeparatorMark((int) Math.round(m.x * coordScale), m.type, m.splitIndex));
                }
                this.renderSeparators.put(entry.getKey(), scaledList);
            }

            this.renderMarkers = new ArrayList<>();
            for (Integer mx : panel.textManager.getPlanMarkers()) {
                this.renderMarkers.add((int) Math.round(mx * coordScale));
            }

            this.dedicatedRenderer = new TimelineRenderer(panel.bandCount, this.cx);
            this.dedicatedRenderer.setCustomization(panel.customization);

            this.backgroundColor = panel.getBackground() != null ? panel.getBackground() : new Color(40, 40, 40);
            this.separatorsVisible = panel.separatorsVisible;
            this.graduationsVisible = panel.graduationsVisible;
            this.waveformData = panel.waveformData;
        }

        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public double getPixelsPerSecond() { return pps; }

        /**
         * Rendu ultra-rapide directement dans le contexte Graphics2D fourni sans allocation d'objets.
         */
        public void renderFrameDirect(Graphics2D g2, double time) {
            double frameOffsetX = cx - (time * pps);
            g2.setColor(backgroundColor);
            g2.fillRect(0, 0, width, height);

            dedicatedRenderer.render(g2,
                    renderSeparators,
                    renderTexts,
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
                    pps,
                    false,
                    separatorsVisible,
                    graduationsVisible,
                    renderMarkers,
                    waveformData);
        }
    }

    /**
     * Crée une session d'export pré-calculée pour le rendu rapide de milliers de frames consécutives.
     */
    public ExportSession createExportSession(int width, int height, double visibleSecondsAhead) {
        return new ExportSession(width, height, visibleSecondsAhead, this);
    }

    /**
     * Renders the timeline at a given time position into a BufferedImage.
     * Scales tracks, text, and timecode proportionally to target resolution for high readability and sharp text.
     */
    public BufferedImage renderFrame(int width, int height, double time, double visibleSecondsAhead) {
        ExportSession session = createExportSession(width, height, visibleSecondsAhead);
        BufferedImage img = new BufferedImage(session.getWidth(), session.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        try {
            session.renderFrameDirect(g2, time);
        } finally {
            g2.dispose();
        }
        return img;
    }

    public BufferedImage renderFrame(int width, int height, double time) {
        return renderFrame(width, height, time, 5.0);
    }

    public int getZoomLevelIndex() {
        return zoomLevelIndex;
    }

    public void saveProject(File file, File videoFile, java.util.ArrayList<Role> roles) {
        ProjectManager.save(file, videoFile, textManager, roles, bandCount, pixelsPerSecond, zoomLevelIndex);
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
        ProjectManager.LoadedProject loaded = ProjectManager.load(file, textManager, roles);
        setBandCount(loaded.bandCount);
        if (loaded.zoomLevelIndex >= 0 && loaded.zoomLevelIndex < ZOOM_LEVELS.length) {
            this.zoomLevelIndex = loaded.zoomLevelIndex;
            this.pixelsPerSecond = (loaded.pixelsPerSecond > 0) ? loaded.pixelsPerSecond : (BASE_PIXELS_PER_SECOND * ZOOM_LEVELS[this.zoomLevelIndex]);
        } else if (loaded.pixelsPerSecond > 0) {
            this.pixelsPerSecond = loaded.pixelsPerSecond;
            int bestIdx = 0;
            double minDiff = Double.MAX_VALUE;
            for (int i = 0; i < ZOOM_LEVELS.length; i++) {
                double diff = Math.abs((BASE_PIXELS_PER_SECOND * ZOOM_LEVELS[i]) - loaded.pixelsPerSecond);
                if (diff < minDiff) {
                    minDiff = diff;
                    bestIdx = i;
                }
            }
            this.zoomLevelIndex = bestIdx;
        } else {
            this.zoomLevelIndex = 0;
            this.pixelsPerSecond = BASE_PIXELS_PER_SECOND;
        }
        setTime(currentTime);
        repaint();
        // Loading a project is considered a clean state with clean undo history.
        undoStack.clear();
        redoStack.clear();
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

    public List<ActionHistoryEntry> getAllActionHistory() {
        double currentWorldX = cursorX - offsetX;
        double pps = Math.max(1.0, pixelsPerSecond);
        ArrayList<ActionHistoryEntry> entries = new ArrayList<>();

        for (TextItem item : textManager.getTexts()) {
            if (item == null) continue;

            int startX = item.x;
            Integer endX = findEndBoundaryX(item.band, startX);

            String roleName = (item.role != null && item.role.name != null && !item.role.name.isBlank())
                    ? item.role.name
                    : "Sans rôle";
            String fullText = item.text == null ? "" : item.text;

            ArrayList<SeparatorMark> sepList = textManager.getBandSeparators().get(item.band);
            ArrayList<SeparatorMark> innerMarks = new ArrayList<>();
            if (sepList != null) {
                for (SeparatorMark mark : sepList) {
                    if (mark != null && mark.type == SeparatorMark.Type.INNER && mark.x > startX && (endX == null || mark.x < endX)) {
                        innerMarks.add(mark);
                    }
                }
            }

            if (innerMarks.isEmpty()) {
                boolean activeNow = startX <= currentWorldX && (endX == null || currentWorldX <= endX);
                double startSec = Math.max(0.0, startX / pps);
                Double endSec = endX == null ? null : Math.max(0.0, endX / pps);
                entries.add(new ActionHistoryEntry(roleName, fullText, startSec, endSec, activeNow));
            } else {
                int len = fullText.length();
                int prevX = startX;
                int prevIdx = 0;

                for (SeparatorMark mark : innerMarks) {
                    int segStartX = prevX;
                    int segEndX = mark.x;
                    int idx = mark.splitIndex >= 0 ? mark.splitIndex : len;
                    if (idx < prevIdx) idx = prevIdx;
                    if (idx > len) idx = len;

                    String segText = fullText.substring(prevIdx, idx).trim();
                    boolean activeNow = segStartX <= currentWorldX && currentWorldX <= segEndX;
                    double startSec = Math.max(0.0, segStartX / pps);
                    Double endSec = Math.max(0.0, segEndX / pps);

                    if (!segText.isEmpty()) {
                        entries.add(new ActionHistoryEntry(roleName, segText, startSec, endSec, activeNow));
                    }

                    prevX = mark.x;
                    prevIdx = idx;
                }

                int segStartX = prevX;
                Integer segEndX = endX;
                String segText = (prevIdx <= len) ? fullText.substring(prevIdx).trim() : "";
                boolean activeNow = segStartX <= currentWorldX && (segEndX == null || currentWorldX <= segEndX);
                double startSec = Math.max(0.0, segStartX / pps);
                Double endSec = segEndX == null ? null : Math.max(0.0, segEndX / pps);

                if (!segText.isEmpty()) {
                    entries.add(new ActionHistoryEntry(roleName, segText, startSec, endSec, activeNow));
                }
            }
        }

        entries.sort(Comparator.comparingDouble(a -> a.startTimeSeconds));
        return entries;
    }

    public List<ActionHistoryEntry> getActionHistory(int maxEntries) {
        int safeMax = Math.max(1, maxEntries);
        double currentWorldX = cursorX - offsetX;

        List<ActionHistoryEntry> all = getAllActionHistory();
        if (all.size() <= safeMax) {
            return all;
        }

        ArrayList<ActionHistoryEntry> past = new ArrayList<>();
        ArrayList<ActionHistoryEntry> future = new ArrayList<>();

        for (ActionHistoryEntry entry : all) {
            if (entry.active || (entry.endTimeSeconds != null ? entry.endTimeSeconds <= (currentWorldX / pixelsPerSecond) : entry.startTimeSeconds <= (currentWorldX / pixelsPerSecond))) {
                past.add(entry);
            } else {
                future.add(entry);
            }
        }

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
        markDirty();
    }

    public void markDirty() {
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
                state.signType = sep.signType;
                state.rawDetxType = sep.rawDetxType;
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
                SeparatorMark sm = textManager.addSeparator(s.band, s.x, s.type, s.splitIndex, s.signType);
                if (s.rawDetxType != null) {
                    sm.rawDetxType = s.rawDetxType;
                }
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