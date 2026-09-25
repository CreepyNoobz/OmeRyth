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

    // =========================================================================
    // PARAMÈTRES ET VARIABLES DU SYSTÈME DE COORDONNÉES TEMPORELLES
    // =========================================================================
    private int bandCount = 4;
    private int bandHeight = 50;
    private int cursorX = 80;
    
    // Vitesse de base du défilement : 80 pixels pour 1 seconde de temps réel.
    // L'échelle de zoom multiplie ce facteur (1.0x, 1.5x, 2.0x, 2.5x, 3.0x).
    private static final double BASE_PIXELS_PER_SECOND = 80.0;
    private static final double[] ZOOM_LEVELS = {1.0, 1.5, 2.0, 2.5, 3.0};
    private int zoomLevelIndex = 0;

    /**
     * Temps courant de lecture vidéo en secondes.
     * 
     * Formule fondamentale de transformation des coordonnées :
     *   worldX  = pixelsPerSecond * timeSeconds
     *   screenX = worldX + offsetX
     *   offsetX = cursorX - (currentTime * pixelsPerSecond)
     * 
     * À tout moment, le point dans le signal correspondant à currentTime est aligné
     * précisément sous le curseur rouge d'écoute (cursorX).
     */
    private double currentTime = 0;
    private double pixelsPerSecond = BASE_PIXELS_PER_SECOND;
    private double offsetX = 80.0;

    public int getIntOffsetX() {
        return (int) Math.round(offsetX);
    }
    private int selectedBand = -1;
    private boolean draggingSeparator = false;      // Ctrl+drag : déplace le repère physique dans le temps
    private boolean draggingPlanMarker = false;     // Déplacement à la souris d'un repère de changement de plan
    private boolean shiftingText = false;           // Drag simple sur un INNER : redistribution élastique des lettres
    private int draggingSeparatorBand = -1;
    private int draggingSeparatorX = Integer.MIN_VALUE;
    private int draggingPlanMarkerX = Integer.MIN_VALUE;
    private boolean suppressNextClick = false;
    private float dragPixelAccum = 0f;
    
    // Nombre de pixels de déplacement de souris nécessaires pour faire sauter 1 caractère d'un sous-segment à l'autre
    private int pixelsPerChar = 2;
    // Position de référence (en coordonnées monde) lors du glissement de texte
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

    private app.services.SpellGrammarService.SpellCheckIssue currentHoveredSpellIssue = null;
    private javax.swing.Timer spellHoverTimer = null;
    private SpellSuggestionPopup spellSuggestionPopup = null;

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
     * Initialise le panneau de la timeline :
     * - Configure les timers de clignotement du caret et de pulsation d'insertion
     * - Installe les écouteurs de souris pour le survol, le drag, le clic droit et le double-clic
     * - Évalue la sensibilité de déplacement des caractères selon les métriques de la police courante.
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

        // Calcule la sensibilité de glissement selon la largeur moyenne de la lettre 'M' dans la police choisie
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
                // Survol interactif des fautes d'orthographe et de grammaire (0.5s pour apparition progressive)
                if (separatorsVisible) {
                    app.services.SpellGrammarService.SpellCheckIssue hitIssue =
                            app.services.SpellGrammarService.getInstance().findIssueAt(e.getX(), e.getY());
                    if (hitIssue != null) {
                        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                        if (hitIssue != currentHoveredSpellIssue) {
                            currentHoveredSpellIssue = hitIssue;
                            if (spellHoverTimer != null && spellHoverTimer.isRunning()) {
                                spellHoverTimer.stop();
                            }
                            Point mouseLoc = e.getLocationOnScreen();
                            spellHoverTimer = new Timer(500, evt -> {
                                if (currentHoveredSpellIssue == hitIssue && isShowing()) {
                                    showSpellSuggestionPopup(hitIssue, mouseLoc);
                                }
                            });
                            spellHoverTimer.setRepeats(false);
                            spellHoverTimer.start();
                        }
                        return;
                    } else {
                        if (spellHoverTimer != null && spellHoverTimer.isRunning()) {
                            spellHoverTimer.stop();
                        }
                        currentHoveredSpellIssue = null;
                        if (spellSuggestionPopup != null && spellSuggestionPopup.isVisible()) {
                            try {
                                Point screenPt = e.getLocationOnScreen();
                                if (!spellSuggestionPopup.getBounds().contains(screenPt)) {
                                    spellSuggestionPopup.fadeOutAndHide();
                                }
                            } catch (Throwable ignored) {}
                        }
                    }
                } else {
                    if (spellHoverTimer != null && spellHoverTimer.isRunning()) {
                        spellHoverTimer.stop();
                    }
                    currentHoveredSpellIssue = null;
                    if (spellSuggestionPopup != null && spellSuggestionPopup.isVisible()) {
                        spellSuggestionPopup.fadeOutAndHide();
                    }
                }

                int intOffsetX = getIntOffsetX();
                TextItem hoveredText = textManager.getTextAtScaled(e.getX(), e.getY(), intOffsetX, getHeight(), bandCount);
                int[] hitSep = textManager.findSeparatorAtScaled(e.getX(), e.getY(), intOffsetX, getHeight(), bandCount, 8);
                boolean hitMarker = false;
                int worldX = e.getX() - intOffsetX;
                for (Integer markerX : textManager.getPlanMarkers()) {
                    if (Math.abs(markerX - worldX) <= 12) {
                        hitMarker = true;
                        break;
                    }
                }
                if (hitMarker) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR));
                } else if (hitSep != null || hoveredText != null) {
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
                    // Arrondi au dixième de seconde près pour aligner sur les traits de marquage
                    int snappedWorldX = snapWorldXToTenth(newWorldX);
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
                    // Déplace physiquement le symbole, pas de transfert de chars
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
                int worldX = e.getX() - intOffsetX;

                // Déplacement de repère de plan comme un signe (clic gauche simple ou avec Ctrl)
                if (SwingUtilities.isLeftMouseButton(e)) {
                    Integer hitMarker = null;
                    for (Integer markerX : textManager.getPlanMarkers()) {
                        if (Math.abs(markerX - worldX) <= 12) {
                            hitMarker = markerX;
                            break;
                        }
                    }
                    if (hitMarker != null) {
                        draggingPlanMarker = true;
                        draggingPlanMarkerX = hitMarker;
                        repaint();
                        return;
                    }
                }

                if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) != 0
                        && SwingUtilities.isLeftMouseButton(e)) {
                    int[] hit = textManager.findSeparatorAtScaled(
                            e.getX(), e.getY(), intOffsetX, getHeight(), bandCount, 8);
                    if (hit != null) {
                        draggingSeparator = true;
                        draggingSeparatorBand = hit[0];
                        draggingSeparatorX = hit[1];
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
                    // reset shifting helper state
                    shiftingBaseSeparatorX = Integer.MIN_VALUE;
                    shiftingPointerPrevX = Integer.MIN_VALUE;
                }

                if ((e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) && !hasMovedDuringDrag) {
                    int intOffsetX = getIntOffsetX();
                    int worldX = e.getX() - intOffsetX;
                    for (Integer markerX : textManager.getPlanMarkers()) {
                        if (Math.abs(markerX - worldX) <= 14) {
                            showPlanMarkerContextMenu(e.getComponent(), e.getX(), e.getY(), markerX);
                            suppressNextClick = true;
                            return;
                        }
                    }
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
                        if (Math.abs(markerX - worldX) <= 14) {
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

            @Override
            public void mouseExited(MouseEvent e) {
                if (spellHoverTimer != null && spellHoverTimer.isRunning()) {
                    spellHoverTimer.stop();
                }
                currentHoveredSpellIssue = null;
                if (spellSuggestionPopup != null && spellSuggestionPopup.isVisible()) {
                    try {
                        PointerInfo pi = MouseInfo.getPointerInfo();
                        if (pi == null || !spellSuggestionPopup.getBounds().contains(pi.getLocation())) {
                            spellSuggestionPopup.fadeOutAndHide();
                        }
                    } catch (Throwable ignored) {
                        spellSuggestionPopup.fadeOutAndHide();
                    }
                }
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
        JMenuItem supprimer = new JMenuItem("🗑️ Supprimer le repère de plan");
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

    // =========================================================================
    // QUANTIFICATION ET MAGNÉTISME TEMPOREL (DIXIÈMES DE SECONDE)
    // =========================================================================

    /**
     * Aligne (snap) une coordonnée monde au dixième de seconde le plus proche (arrondi standard).
     * 
     * En doublage professionnel, le pas de travail conventionnel est le dixième de seconde (0.1s)
     * ou l'image cinéma/vidéo (24/25 fps).
     * Calcul mathématique :
     *   pasEnPixels = pixelsPerSecond * 0.1
     *   positionQuantifiée = round(worldX / pasEnPixels) * pasEnPixels
     */
    public int snapWorldXToTenth(double worldX) {
        double step = pixelsPerSecond * 0.1;
        if (step <= 0) return (int) Math.round(worldX);
        return (int) Math.round(Math.round(worldX / step) * step);
    }

    public int snapWorldXToTenth(int worldX) {
        return snapWorldXToTenth((double) worldX);
    }

    /**
     * Aligne (snap) une coordonnée monde au dixième de seconde inférieur (arrondi par défaut / floor).
     * Utilisé lors de certains déplacements pour garantir de ne pas empiéter sur le pas suivant.
     */
    public int snapWorldXDownToTenth(double worldX) {
        double step = pixelsPerSecond * 0.1;
        if (step <= 0) return (int) Math.round(worldX);
        return (int) Math.round(Math.floor(worldX / step) * step);
    }

    public int snapWorldXDownToTenth(int worldX) {
        return snapWorldXDownToTenth((double) worldX);
    }

    /**
     * Restreint le déplacement d'un séparateur pour empêcher toute collision ou inversion d'ordre :
     * un séparateur ne peut jamais dépasser son voisin précédent ou suivant, préservant
     * un écart minimal de sécurité (minGap = 0.1 seconde).
     */
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

    /** Insère un marqueur de changement de plan vidéo (cut) à la position temporelle courante. */
    public void addPlanMarkerAtCursor() {
        recordUndoSnapshot();
        int markerX = snapWorldXToTenth(cursorX - offsetX);
        textManager.addPlanMarker(markerX);
        repaint();
    }

    /** Ajoute un repère de synchronisation standard à la position du curseur sur la bande spécifiée. */
    public boolean addSeparatorAtCursor(int band) {
        return addSeparatorAtCursor(band, SeparatorMark.SignType.DEFAULT);
    }

    /** Ajoute un repère spécifique (FVR, MPB, voyelle A, etc.) magnétisé au dixième de seconde. */
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

    /** Démarre une nouvelle phrase sur une bande (crée le repère START + texte vide et entre en mode saisie). */
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

    /** Clôture la phrase courante en insérant un séparateur END à la position du curseur. */
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
        this.customization.appLanguage = c.appLanguage;
        app.services.SpellGrammarService.getInstance().setLanguage(c.appLanguage);

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

    /** Insère un caractère dans la phrase en cours d'édition (avec support complet des accents et de l'historique d'annulation). */
    public void typeChar(char c) {
        if (!textManager.isEditing()) return;
        recordUndoSnapshot();
        textManager.typeChar(c);
        resetCaretBlink();
        repaint();
    }

    /** Déplace le curseur de saisie de delta positions et met à jour l'affichage visuel. */
    public void moveCursor(int delta) {
        textManager.moveCursor(delta);
        resetCaretBlink();
        repaint();
    }

    /** Déplace le curseur mot par mot dans la direction spécifiée (-1 vers la gauche, +1 vers la droite). */
    public void moveCursorByWord(int direction) {
        textManager.moveCursorByWord(direction);
        resetCaretBlink();
        repaint();
    }

    /** Déplace le curseur au tout début de la réplique éditée. */
    public void moveCursorToStart() {
        textManager.moveCursorToStart();
        resetCaretBlink();
        repaint();
    }

    /** Déplace le curseur à la toute fin de la réplique éditée. */
    public void moveCursorToEnd() {
        textManager.moveCursorToEnd();
        resetCaretBlink();
        repaint();
    }

    /** Supprime le caractère situé immédiatement avant le curseur (touche Retour arrière / Backspace). */
    public void deleteChar() {
        if (!textManager.isEditing()) return;
        recordUndoSnapshot();
        textManager.deleteChar();
        resetCaretBlink();
        repaint();
    }

    /** Supprime le mot précédent situé avant le curseur (raccourci Ctrl+Backspace). */
    public void deleteWord() {
        if (!textManager.isEditing()) return;
        recordUndoSnapshot();
        textManager.deleteWord();
        resetCaretBlink();
        repaint();
    }

    /** Quitte le mode édition et masque le curseur clignotant. */
    public void stopTyping() {
        textManager.stopTyping();
        caretVisible = false;
        repaint();
    }

    /** Colle le texte fourni à la position actuelle du curseur de saisie. */
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

    /** Réinitialise l'ensemble de la timeline (textes et séparateurs) avec sauvegarde dans l'historique Annuler. */
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

    /** Active ou masque l'affichage des séparateurs et repères de synchro. */
    public void setSeparatorsVisible(boolean visible) {
        this.separatorsVisible = visible;
        if (!visible) {
            if (spellHoverTimer != null && spellHoverTimer.isRunning()) {
                spellHoverTimer.stop();
            }
            currentHoveredSpellIssue = null;
            if (spellSuggestionPopup != null && spellSuggestionPopup.isVisible()) {
                spellSuggestionPopup.fadeOutAndHide();
            }
        }
        repaint();
    }

    private void showSpellSuggestionPopup(app.services.SpellGrammarService.SpellCheckIssue issue, Point mouseLoc) {
        if (!separatorsVisible || issue == null) return;
        if (spellSuggestionPopup != null) {
            spellSuggestionPopup.dispose();
        }
        Window parentWin = SwingUtilities.getWindowAncestor(this);
        spellSuggestionPopup = new SpellSuggestionPopup(
            parentWin,
            issue,
            this::applySpellCorrection,
            this::ignoreSpellWord
        );

        Point panelLoc = getLocationOnScreen();
        int popupX = (int) (panelLoc.x + issue.screenStartX);
        int popupY = (int) (panelLoc.y + issue.screenY + issue.screenHeight + 4);

        Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
        if (popupY + 160 > screenDim.height) {
            popupY = Math.max(10, (int) (panelLoc.y + issue.screenY - 160));
        }
        if (popupX + 260 > screenDim.width) {
            popupX = Math.max(10, screenDim.width - 270);
        }
        if (popupX < 10) popupX = 10;

        spellSuggestionPopup.showProgressive(new Point(popupX, popupY));
    }

    private void applySpellCorrection(app.services.SpellGrammarService.SpellCheckIssue issue, String replacement) {
        if (issue == null || issue.targetItem == null || replacement == null) return;
        recordUndoSnapshot();
        textManager.replaceWordInTextItem(issue.targetItem, issue.startIdx, issue.endIdx, replacement);
        app.services.SpellGrammarService.getInstance().invalidateCache(issue.targetItem.text);
        currentHoveredSpellIssue = null;
        repaint();
    }

    private void ignoreSpellWord(app.services.SpellGrammarService.SpellCheckIssue issue) {
        if (issue == null) return;
        app.services.SpellGrammarService.getInstance().ignoreWord(issue.originalText);
        currentHoveredSpellIssue = null;
        repaint();
    }

    public boolean isGraduationsVisible() {
        return graduationsVisible;
    }

    /** Active ou masque l'affichage des graduations temporelles (dixièmes et secondes). */
    public void setGraduationsVisible(boolean visible) {
        this.graduationsVisible = visible;
        repaint();
    }

    public boolean isWaveformVisible() {
        return customization.showWaveform;
    }

    /** Active ou masque l'affichage de la forme d'onde audio (waveform). */
    public void setWaveformVisible(boolean visible) {
        this.customization.showWaveform = visible;
        repaint();
    }

    /**
     * Avertit l'utilisateur si la réduction du nombre de bandes risquerait de masquer
     * des répliques ou des séparateurs déjà placés sur les pistes supprimées.
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

    /**
     * Applique le niveau de zoom sélectionné :
     * - Met à l'échelle toutes les coordonnées X de la timeline selon le ratio (nouveauPPS / ancienPPS)
     * - Met à jour l'historique d'annulation pour que les snapshots restent cohérents
     * - Recale l'affichage pour que la position temporelle courante reste stationnaire sous le curseur.
     */
    private void applyZoomLevel() {
        double oldPixelsPerSecond = pixelsPerSecond;
        pixelsPerSecond = BASE_PIXELS_PER_SECOND * ZOOM_LEVELS[zoomLevelIndex];
        if (oldPixelsPerSecond > 0) {
            double ratio = pixelsPerSecond / oldPixelsPerSecond;
            textManager.scaleTimelineX(0, ratio);
            scaleSnapshots(undoStack, ratio);
            scaleSnapshots(redoStack, ratio);
        }
        // Conserve le calage exact du curseur sur le temps courant pendant le zoom
        setTime(currentTime);
    }

    /**
     * Session de rendu dédiée et ultra-rapide pour l'exportation vidéo finale (Burn-in rythmo).
     * 
     * Optimisations clés :
     * - Pré-calcule et met en cache l'ensemble des coordonnées géométriques mises à l'échelle
     * - Évite toute allocation mémoire (Garbage Collector) pendant la boucle de rendu des images (24, 25 ou 30 fps)
     * - Utilise une instance dédiée et isolée de TimelineRenderer, garantissant l'indépendance vis-à-vis de l'UI.
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
     * Crée une session d'export pré-calculée pour le rendu rapide de milliers d'images consécutives.
     */
    public ExportSession createExportSession(int width, int height, double visibleSecondsAhead) {
        return new ExportSession(width, height, visibleSecondsAhead, this);
    }

    /**
     * Calcule et génère l'image d'une frame de la bande rythmo à un instant T donné.
     * Met à l'échelle proportionnellement les bandes, les textes et les repères pour une netteté maximale.
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
        // La sauvegarde sur disque réinitialise l'état modifié (dirty flag)
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
        // Le chargement d'un projet initialise un état propre avec un historique d'annulation vierge
        undoStack.clear();
        redoStack.clear();
        clearDirty();
        return loaded.videoFile;
    }

    public boolean isDirty() { return dirty; }
    // Callback pour notifier la fenêtre principale dès que l'état d'enregistrement change
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

    /**
     * Enregistre un instantané complet (Pattern Memento / Snapshot) de l'état de la timeline
     * avant toute action destructive ou modification utilisateur (saisie, déplacement, ajout/suppression de repère).
     * Vide la pile 'Rétablir' (Redo) et marque le document comme modifié.
     */
    public void recordUndoSnapshot() {
        if (restoringHistory) return;
        pushSnapshot(undoStack, captureSnapshot());
        redoStack.clear();
        // Marque le projet comme modifié par l'utilisateur
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