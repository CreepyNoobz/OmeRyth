package app.utils;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.util.ArrayList;
import app.utils.TimerClass;
import app.MainFenetre;
import app.ui.TimelinePanel;
import app.ui.Role;
import app.ui.RoleWindow;
import app.ui.SeparatorMark;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

/**
 * Écouteur et répartiteur central des événements clavier pour l'édition et le doublage rythmo.
 * <p>
 * Deux modes opératoires étanches :
 * <ul>
 *   <li><b>Mode Navigation / Lecture :</b> Contrôle la tête de lecture (Espace, avance/recul frame-accurate),
 *       le zoom temporel (+ / -), l'historique d'annulation (Ctrl+Z / Ctrl+Y), et l'insertion instantanée
 *       de repères phonétiques (pavé numérique 4: MPB, 5: FVR, 6: Neutre, 7: Voyelle A, 8: Respiration).</li>
 *   <li><b>Mode Saisie de texte :</b> Capture les caractères typés pour les injecter directement dans le segment actif,
 *       gère le collage presse-papiers (Ctrl+V), la suppression par mot (Ctrl+Backspace), le saut par mot (Ctrl+Flèches),
 *       et la clôture de réplique (Entrée / fin de phrase).</li>
 * </ul>
 * </p>
 */
public class KeyBoardListener implements KeyListener, TimelinePanel.PhraseCreationListener {
    private TimerClass timer;
    private EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private ArrayList<Role> roles;
    private java.awt.Component parentComponent;

    private int marcheArretKeyCode;
    private int avanceMSKeyCode;
    private int reculerMSKeyCode;
    private int retourDebutKeyCode;
    private int separateurKeyCode;
    private int zoomInKeyCode;
    private int zoomOutKeyCode;
    private int finPhraseKeyCode = KeyEvent.VK_NUMPAD3;
    private int signeMpbKeyCode = KeyEvent.VK_M;
    private int signeFvrKeyCode = KeyEvent.VK_NUMPAD5;
    private int signeNeutralKeyCode = KeyEvent.VK_NUMPAD6;
    private int signeVoyelleKeyCode = KeyEvent.VK_NUMPAD7;
    private int signeRespirationKeyCode = KeyEvent.VK_NUMPAD8;
    private int signePlanKeyCode = KeyEvent.VK_NUMPAD0;
    private boolean skipNextTyped = false;
    private boolean mpbKeyDown = false;
    private long lastMpbTimestamp = 0;
    private long lastSepTimestamp = 0;
    private TimelinePanel timelinePanel;
    
    public KeyBoardListener(TimelinePanel panel) {
        this.timelinePanel = panel;
    }

    public KeyBoardListener(
        TimelinePanel panel,
        TimerClass timer,
        EmbeddedMediaPlayerComponent mediaPlayerComponent,
        int marcheArretKeyCode,
        int avanceMSKeyCode,
        int reculerMSKeyCode,
        int retourDebutKeyCode,
        int separateurKeyCode,
        int zoomInKeyCode,
        int zoomOutKeyCode,
        ArrayList<Role> roles,
        java.awt.Component parentComponent
    ) {
        this(panel, timer, mediaPlayerComponent, marcheArretKeyCode, avanceMSKeyCode, reculerMSKeyCode,
             retourDebutKeyCode, separateurKeyCode, zoomInKeyCode, zoomOutKeyCode,
             KeyEvent.VK_NUMPAD3, KeyEvent.VK_M, KeyEvent.VK_NUMPAD5,
             KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD7, KeyEvent.VK_NUMPAD8,
             KeyEvent.VK_NUMPAD0, roles, parentComponent);
    }

    public KeyBoardListener(
        TimelinePanel panel,
        TimerClass timer,
        EmbeddedMediaPlayerComponent mediaPlayerComponent,
        int marcheArretKeyCode,
        int avanceMSKeyCode,
        int reculerMSKeyCode,
        int retourDebutKeyCode,
        int separateurKeyCode,
        int zoomInKeyCode,
        int zoomOutKeyCode,
        int finPhraseKeyCode,
        int signeMpbKeyCode,
        int signeFvrKeyCode,
        int signeNeutralKeyCode,
        int signeVoyelleKeyCode,
        int signeRespirationKeyCode,
        ArrayList<Role> roles,
        java.awt.Component parentComponent
    ) {
        this(panel, timer, mediaPlayerComponent, marcheArretKeyCode, avanceMSKeyCode, reculerMSKeyCode,
             retourDebutKeyCode, separateurKeyCode, zoomInKeyCode, zoomOutKeyCode,
             finPhraseKeyCode, signeMpbKeyCode, signeFvrKeyCode,
             signeNeutralKeyCode, signeVoyelleKeyCode, signeRespirationKeyCode,
             KeyEvent.VK_NUMPAD0, roles, parentComponent);
    }

    public KeyBoardListener(
        TimelinePanel panel,
        TimerClass timer,
        EmbeddedMediaPlayerComponent mediaPlayerComponent,
        int marcheArretKeyCode,
        int avanceMSKeyCode,
        int reculerMSKeyCode,
        int retourDebutKeyCode,
        int separateurKeyCode,
        int zoomInKeyCode,
        int zoomOutKeyCode,
        int finPhraseKeyCode,
        int signeMpbKeyCode,
        int signeFvrKeyCode,
        int signeNeutralKeyCode,
        int signeVoyelleKeyCode,
        int signeRespirationKeyCode,
        int signePlanKeyCode,
        ArrayList<Role> roles,
        java.awt.Component parentComponent
    ) {
        this.timelinePanel = panel;
        this.timer = timer;
        this.mediaPlayerComponent = mediaPlayerComponent;
        this.marcheArretKeyCode = marcheArretKeyCode;
        this.avanceMSKeyCode = avanceMSKeyCode;
        this.reculerMSKeyCode = reculerMSKeyCode;
        this.retourDebutKeyCode = retourDebutKeyCode;
        this.separateurKeyCode = separateurKeyCode;
        this.zoomInKeyCode = zoomInKeyCode;
        this.zoomOutKeyCode = zoomOutKeyCode;
        this.finPhraseKeyCode = finPhraseKeyCode;
        this.signeMpbKeyCode = signeMpbKeyCode;
        this.signeFvrKeyCode = signeFvrKeyCode;
        this.signeNeutralKeyCode = signeNeutralKeyCode;
        this.signeVoyelleKeyCode = signeVoyelleKeyCode;
        this.signeRespirationKeyCode = signeRespirationKeyCode;
        this.signePlanKeyCode = signePlanKeyCode;
        this.roles = roles;
        this.parentComponent = parentComponent;
        panel.setPhraseCreationListener(this);
    }

    public void setFinPhraseKeyCode(int keyCode) { this.finPhraseKeyCode = keyCode; }
    public void setSigneMpbKeyCode(int keyCode) { this.signeMpbKeyCode = keyCode; }
    public void setSigneFvrKeyCode(int keyCode) { this.signeFvrKeyCode = keyCode; }
    public void setSigneNeutralKeyCode(int keyCode) { this.signeNeutralKeyCode = keyCode; }
    public void setSigneVoyelleKeyCode(int keyCode) { this.signeVoyelleKeyCode = keyCode; }
    public void setSigneRespirationKeyCode(int keyCode) { this.signeRespirationKeyCode = keyCode; }
    public void setSignePlanKeyCode(int keyCode) { this.signePlanKeyCode = keyCode; }
    public int getSignePlanKeyCode() { return this.signePlanKeyCode; }

    public void setZoomInKeyCode(int keyCode) {
        this.zoomInKeyCode = keyCode;
    }

    public void setZoomOutKeyCode(int keyCode) {
        this.zoomOutKeyCode = keyCode;
    }

    public void setMarcheArretKeyCode(int keyCode) {
        this.marcheArretKeyCode = keyCode;
    }

    public void setAvanceMSKeyCode(int keyCode) {
        this.avanceMSKeyCode = keyCode;
    }

    public void setReculerMSKeyCode(int keyCode) {
        this.reculerMSKeyCode = keyCode;
    }

    public void setRetourDebutKeyCode(int keyCode) {
        this.retourDebutKeyCode = keyCode;
    }

    public void setSeparateurKeyCode(int keyCode) {
        this.separateurKeyCode = keyCode;
    }

    public boolean dispatchKeyEvent(KeyEvent e) {
        boolean isNumpadLocation = (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD)
                || (e.getKeyCode() >= KeyEvent.VK_NUMPAD0 && e.getKeyCode() <= KeyEvent.VK_NUMPAD9);

        // ★ Changement de plan / Cut vidéo (Pavé numérique 0 par défaut ou touche configurée)
        boolean isNumpad0 = ((e.getKeyCode() == signePlanKeyCode) && (!timelinePanel.isEditing() || isNumpadLocation))
                || (e.getKeyCode() == KeyEvent.VK_NUMPAD0 && signePlanKeyCode == KeyEvent.VK_NUMPAD0)
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_INSERT);
        if (isNumpad0) {
            skipNextTyped = true;
            timelinePanel.addChangementPlanAtCursor();
            e.consume();
            return true;
        }

        // ★ Séparateur bleu de synchronisme (Pavé numérique 4 par défaut ou touche 4 / configurée)
        boolean isNumpad4 = (e.getKeyCode() == KeyEvent.VK_NUMPAD4)
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_LEFT);
        boolean isConfiguredSep = (e.getKeyCode() == separateurKeyCode && (!timelinePanel.isEditing() || isNumpad4 || isNumpadLocation));
        boolean isTopRow4 = !timelinePanel.isEditing() && (e.getKeyCode() == KeyEvent.VK_4 || e.getKeyChar() == '4'
                || e.getKeyCode() == KeyEvent.VK_QUOTE || e.getKeyChar() == '\'');

        if (isNumpad4 || isConfiguredSep || isTopRow4) {
            skipNextTyped = true;
            long now = System.currentTimeMillis();
            if (now - lastSepTimestamp > 180) { // ignore l'auto-répétition matérielle rapide
                lastSepTimestamp = now;
                int band = timelinePanel.getSelectedBand();
                if (band < 0) band = 0;
                timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.DEFAULT);
            }
            e.consume();
            return true;
        }

        // ★ Labiale MPB (Touche M par défaut ou touche configurée distincte de 4, hors édition)
        boolean isMpb = (e.getKeyCode() == signeMpbKeyCode && e.getKeyCode() != separateurKeyCode && e.getKeyCode() != KeyEvent.VK_NUMPAD4 && (!timelinePanel.isEditing() || isNumpadLocation));
        if (isMpb) {
            skipNextTyped = true;
            long now = System.currentTimeMillis();
            if (now - lastMpbTimestamp > 180) { // ignore l'auto-répétition matérielle rapide sans jamais bloquer la touche
                lastMpbTimestamp = now;
                int band = timelinePanel.getSelectedBand();
                if (band < 0) band = 0;
                timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.MPB);
            }
            e.consume();
            return true;
        }

        // ★ Fin de phrase (Pavé numérique 3 par défaut ou touche configurée)
        boolean isNumpad3 = ((e.getKeyCode() == finPhraseKeyCode) && (!timelinePanel.isEditing() || isNumpadLocation))
                || (e.getKeyCode() == KeyEvent.VK_NUMPAD3 && finPhraseKeyCode == KeyEvent.VK_NUMPAD3)
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_PAGE_DOWN);
        if (isNumpad3) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.endPhraseAtCursor(band);
            e.consume();
            return true;
        }

        // ★ Demi-labiale / Dentale FVR (Pavé numérique 5 par défaut ou touche configurée)
        boolean isNumpad5 = ((e.getKeyCode() == signeFvrKeyCode) && (!timelinePanel.isEditing() || isNumpadLocation))
                || (e.getKeyCode() == KeyEvent.VK_NUMPAD5 && signeFvrKeyCode == KeyEvent.VK_NUMPAD5)
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_CLEAR);
        if (isNumpad5) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.FVR);
            e.consume();
            return true;
        }

        // ★ Consonne neutre (Pavé numérique 6 par défaut ou touche configurée)
        boolean isNumpad6 = ((e.getKeyCode() == signeNeutralKeyCode) && (!timelinePanel.isEditing() || isNumpadLocation))
                || (e.getKeyCode() == KeyEvent.VK_NUMPAD6 && signeNeutralKeyCode == KeyEvent.VK_NUMPAD6)
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_RIGHT && !timelinePanel.isEditing());
        if (isNumpad6) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.NEUTRAL);
            e.consume();
            return true;
        }

        // ★ Grande ouverture A / voyelles (Pavé numérique 7 par défaut ou touche configurée)
        boolean isNumpad7 = ((e.getKeyCode() == signeVoyelleKeyCode) && (!timelinePanel.isEditing() || isNumpadLocation))
                || (e.getKeyCode() == KeyEvent.VK_NUMPAD7 && signeVoyelleKeyCode == KeyEvent.VK_NUMPAD7)
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_HOME);
        if (isNumpad7) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.OPEN_A);
            e.consume();
            return true;
        }

        // ★ Signe Respiration / Souffle h/ (Pavé numérique 8 par défaut ou touche configurée)
        boolean isNumpad8 = ((e.getKeyCode() == signeRespirationKeyCode) && (!timelinePanel.isEditing() || isNumpadLocation))
                || (e.getKeyCode() == KeyEvent.VK_NUMPAD8 && signeRespirationKeyCode == KeyEvent.VK_NUMPAD8)
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_UP);
        if (isNumpad8) {
            skipNextTyped = true;
            if (timelinePanel.isEditing()) {
                timelinePanel.pasteText("h/ ");
            } else {
                int band = timelinePanel.getSelectedBand();
                if (band < 0) band = 0;
                timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.RESPIRATION);
            }
            e.consume();
            return true;
        }

        // ★ Basculer l'affichage de la waveform (Ctrl+W)
        if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_W) {
            skipNextTyped = true;
            if (parentComponent instanceof MainFenetre) {
                ((MainFenetre) parentComponent).toggleWaveformVisible();
            } else {
                timelinePanel.setWaveformVisible(!timelinePanel.isWaveformVisible());
            }
            e.consume();
            return true;
        }

        // En mode édition : seules les touches d'édition et raccourcis Ctrl sont actives.
        if (timelinePanel.isEditing()) {

            // Undo / Redo (Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y) — autorisés en édition
            if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                if (e.getKeyCode() == KeyEvent.VK_Z) {
                    if ((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                        timelinePanel.redo();
                    } else {
                        timelinePanel.undo();
                    }
                    skipNextTyped = true;
                    e.consume();
                    return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_Y) {
                    timelinePanel.redo();
                    skipNextTyped = true;
                    e.consume();
                    return true;
                }
            }

            // Toutes les autres touches spéciales sont gérées dans le bloc isEditing() ci-dessous.
            // On tombe directement dessus (pas de return false ici).

        } else {
            // Hors mode édition : undo/redo, zoom, et commandes de lecture actives.

            // Undo / Redo
            if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                if (e.getKeyCode() == KeyEvent.VK_Z) {
                    if ((e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0) {
                        timelinePanel.redo();
                    } else {
                        timelinePanel.undo();
                    }
                    skipNextTyped = true;
                    e.consume();
                    return true;
                }
                if (e.getKeyCode() == KeyEvent.VK_Y) {
                    timelinePanel.redo();
                    skipNextTyped = true;
                    e.consume();
                    return true;
                }
            }

            // Zoom
            boolean plusShortcut = (e.getKeyCode() == zoomInKeyCode);
            boolean minusShortcut = (e.getKeyCode() == zoomOutKeyCode);

            if (plusShortcut) {
                skipNextTyped = true;
                timelinePanel.zoomIn();
                e.consume();
                return true;
            }
            if (minusShortcut) {
                skipNextTyped = true;
                timelinePanel.zoomOut();
                e.consume();
                return true;
            }
        }

        // Mode édition — gestion des touches propres à l'édition de texte
        if (timelinePanel.isEditing()) {

            if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 && (e.getKeyCode() == KeyEvent.VK_A || e.getKeyCode() == KeyEvent.VK_Q)) {
                timelinePanel.selectAllText();
                skipNextTyped = true;
                e.consume();
                return true;
            }

            if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_C) {
                timelinePanel.copySelectedText();
                skipNextTyped = true;
                e.consume();
                return true;
            }

            if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == KeyEvent.VK_X) {
                timelinePanel.cutSelectedText();
                skipNextTyped = true;
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                timelinePanel.deleteForward();
                skipNextTyped = true;
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_V && (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                try {
                    Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
                    if (data instanceof String) {
                        String text = (String) data;
                        if (!text.isEmpty()) {
                            timelinePanel.pasteText(text);
                        }
                    }
                } catch (Exception ignored) {
                }
                skipNextTyped = true;
                e.consume();
                return true;
            }

            if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 && e.getKeyCode() == marcheArretKeyCode) {
                skipNextTyped = true;
                togglePlayback();
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                skipNextTyped = true;
                timelinePanel.endPhrase();
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    timelinePanel.deleteWord();
                } else {
                    timelinePanel.deleteChar();
                }
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                timelinePanel.stopTyping();
                skipNextTyped = true;
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    timelinePanel.moveCursorByWord(-1);
                } else {
                    timelinePanel.moveCursor(-1);
                }
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                if ((e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0) {
                    timelinePanel.moveCursorByWord(1);
                } else {
                    timelinePanel.moveCursor(1);
                }
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_HOME) {
                timelinePanel.moveCursorToStart();
                e.consume();
                return true;
            }

            if (e.getKeyCode() == KeyEvent.VK_END) {
                timelinePanel.moveCursorToEnd();
                e.consume();
                return true;
            }

            e.consume();
            return true;
        }

        // Touche Entrée (standard ou pavé numérique) hors mode édition : clôturer la phrase ouverte
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.endPhraseAtCursor(band);
            e.consume();
            return true;
        }

        // NUMPAD1 : ajout de repère de plan
        if (e.getKeyCode() == KeyEvent.VK_NUMPAD1 || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && e.getKeyCode() == KeyEvent.VK_END)) {
            skipNextTyped = true;
            timelinePanel.addPlanMarkerAtCursor();
            e.consume();
            return true;
        }

        // NUMPAD2 : début de phrase
        if (e.getKeyCode() == KeyEvent.VK_NUMPAD2) {
            skipNextTyped = true;
            triggerPhraseCreation();
            e.consume();
            return true;
        }
        
        // ===== Marche / Arrêt =====
        if (e.getKeyCode() == marcheArretKeyCode) {
            skipNextTyped = true;
            togglePlayback();
            e.consume();
            return true;
        }

        // ===== Avancer / Reculer =====
        if (e.getKeyCode() == avanceMSKeyCode || e.getKeyCode() == reculerMSKeyCode) {
            skipNextTyped = true;
            boolean forward = e.getKeyCode() == avanceMSKeyCode;
            boolean ctrl = (e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0;
            boolean shift = (e.getModifiersEx() & KeyEvent.SHIFT_DOWN_MASK) != 0;

            if (ctrl) {
                seekTime(forward ? 1.0 : -1.0);
                e.consume();
                return true;
            }

            if (shift) {
                seekTime(forward ? 0.5 : -0.5);
                e.consume();
                return true;
            }

            seekTime(forward ? 0.1 : -0.1);
            e.consume();
            return true;
        }

        // ===== Retour au début =====
        else if (e.getKeyCode() == retourDebutKeyCode) {
            skipNextTyped = true;
            if (timer.isRunning()) {
                timer.toggle();
            }
            timer.reset();
            safeSeekAndPause(0);
            e.consume();
            return true;
        }
        return false;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        dispatchKeyEvent(e);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == separateurKeyCode || k == KeyEvent.VK_NUMPAD4 || k == KeyEvent.VK_4
                || (e.getKeyLocation() == KeyEvent.KEY_LOCATION_NUMPAD && k == KeyEvent.VK_LEFT)) {
            lastSepTimestamp = 0;
        }
        if (k == signeMpbKeyCode || k == KeyEvent.VK_M) {
            mpbKeyDown = false;
            lastMpbTimestamp = 0;
        }
    }

    @Override
    public void keyTyped(KeyEvent e) {
        if (skipNextTyped) {
            skipNextTyped = false;
            e.consume();
            return;
        }
        if ((e.getModifiersEx() & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK)) != 0) {
            return;
        }
        char c = e.getKeyChar();
        if (Character.isISOControl(c)) return;
        timelinePanel.typeChar(c);
    }

    @Override
    public void onPhraseCreationRequested(int band) {
        triggerPhraseCreationForBand(band);
    }

    private void triggerPhraseCreation() {
        int band = timelinePanel.getSelectedBand();
        if (band < 0) return;
        triggerPhraseCreationForBand(band);
    }

    private void triggerPhraseCreationForBand(int band) {
        if (timelinePanel.hasOpenPhraseInBand(band)) {
            timelinePanel.focusOpenPhraseInBand(band);
            timelinePanel.requestFocusInWindow();
            return;
        }

        Role role = RoleWindow.pickRole(parentComponent, roles);
        if (role == null) return;
        timelinePanel.startPhrase(band, role);
        timelinePanel.requestFocusInWindow();
    }

    private long lastPausedTargetMs = -1L;

    public void togglePlayback() {
        togglePlayback(1);
    }

    public void togglePlayback(int direction) {
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                var mp = mediaPlayerComponent.mediaPlayer();
                float rate = direction >= 0 ? 1.0f : -1.0f;
                var state = mp.status().state();

                long targetMs = (long) Math.round(timer.getTime() * 1000.0);
                long length = mp.status().length();
                boolean isAtEnd = (timer.getMaxTime() > 0 && timer.getTime() >= (timer.getMaxTime() - 0.08))
                               || (length > 0 && targetMs >= (length - 80));

                if (timer.isRunning()) {
                    // On met en pause : stopper le timer et figer VLC immédiatement sans seek asynchrone parasite
                    timer.toggle(timer.getDirection());
                    lastPausedTargetMs = (long) Math.round(timer.getTime() * 1000.0);
                    mp.controls().setPause(true);
                } else {
                    // L'utilisateur relance la lecture
                    if (direction > 0 && isAtEnd) {
                        mp.controls().setPause(true);
                        return;
                    }

                    if (state == uk.co.caprica.vlcj.player.base.State.ENDED
                            || state == uk.co.caprica.vlcj.player.base.State.STOPPED
                            || state == uk.co.caprica.vlcj.player.base.State.NOTHING_SPECIAL) {
                        String path = getMediaFilePath();
                        if (path != null) {
                            mp.media().play(path, ":start-time=" + (targetMs / 1000.0), ":file-caching=150", ":clock-jitter=0");
                        } else {
                            mp.controls().play();
                            mp.controls().setTime(targetMs);
                        }
                        if (mp.status().rate() != rate) {
                            mp.controls().setRate(rate);
                        }
                    } else {
                        long vlcTime = mp.status().time();
                        long diffMs = (vlcTime >= 0) ? Math.abs(vlcTime - targetMs) : Long.MAX_VALUE;
                        boolean resumedAtSameSpot = (lastPausedTargetMs >= 0 && Math.abs(targetMs - lastPausedTargetMs) <= 120);

                        if (mp.status().rate() != rate) {
                            mp.controls().setRate(rate);
                        }

                        // Ne JAMAIS appeler setTime(targetMs) lors d'une reprise immédiate au même endroit
                        // ou si VLC est proche de la cible : setTime() sur un flux en pause purge le tampon
                        // audio DirectSound/WASAPI et retarde le redémarrage de l'audio.
                        if (!resumedAtSameSpot && (diffMs > 650 || vlcTime < 0)) {
                            mp.controls().setTime(targetMs);
                        }

                        mp.controls().setPause(false);
                        if (state != uk.co.caprica.vlcj.player.base.State.PAUSED
                                && state != uk.co.caprica.vlcj.player.base.State.PLAYING) {
                            mp.controls().play();
                        }

                        // Filet de sécurité anti-race condition lors d'un Pause -> Play ultra-rapide :
                        // si la commande asynchrone setPause(true) précédente aboutit juste après cet appel,
                        // on force immédiatement la reprise de lecture 45ms plus tard.
                        javax.swing.Timer ensurePlayTimer = new javax.swing.Timer(45, evt -> {
                            try {
                                if (timer.isRunning() && mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
                                    var curMp = mediaPlayerComponent.mediaPlayer();
                                    if (curMp.status().state() == uk.co.caprica.vlcj.player.base.State.PAUSED) {
                                        curMp.controls().setPause(false);
                                    }
                                }
                            } catch (Throwable ignored) {}
                        });
                        ensurePlayTimer.setRepeats(false);
                        ensurePlayTimer.start();
                    }
                    lastPausedTargetMs = -1L;
                    timer.toggle(direction);
                }
            } catch (Throwable t) {
                timer.toggle(timer.isRunning() ? timer.getDirection() : direction);
            }
        } else {
            timer.toggle(timer.isRunning() ? timer.getDirection() : direction);
        }
    }

    public void seekTime(double deltaSeconds) {
        if (deltaSeconds == 0) return;

        // Si la lecture est en cours : mettre en pause
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                var mp = mediaPlayerComponent.mediaPlayer();
                mp.controls().setPause(true);
            } catch (Throwable ignored) {}
        }
        if (timer.isRunning()) {
            timer.toggle();
        }

        // Toujours caler sur les graduations exactes de 0.1s (1 marquage par 1 marquage)
        long currentTenths = Math.round(timer.getTime() * 10.0);
        long deltaTenths = Math.round(deltaSeconds * 10.0);
        if (deltaTenths == 0) {
            deltaTenths = deltaSeconds > 0 ? 1L : -1L;
        }
        double targetSec = Math.max(0.0, (currentTenths + deltaTenths) / 10.0);
        timer.setTime(targetSec);

        long targetMs = (long) Math.round(targetSec * 1000.0);
        lastPausedTargetMs = targetMs;
        safeSeekAndPause(targetMs);
    }

    public void seekToTime(double targetSeconds) {
        // Si la lecture est en cours : mettre en pause
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                var mp = mediaPlayerComponent.mediaPlayer();
                mp.controls().setPause(true);
            } catch (Throwable ignored) {}
        }
        if (timer.isRunning()) {
            timer.toggle();
        }

        // Toujours caler sur les graduations exactes de 0.1s (1 marquage par 1 marquage)
        double targetSec = Math.max(0.0, Math.round(targetSeconds * 10.0) / 10.0);
        timer.setTime(targetSec);

        long targetMs = (long) Math.round(targetSec * 1000.0);
        lastPausedTargetMs = targetMs;
        safeSeekAndPause(targetMs);
    }

    private void safeSeekAndPause(long targetMs) {
        if (mediaPlayerComponent == null || mediaPlayerComponent.mediaPlayer() == null) return;
        try {
            var mp = mediaPlayerComponent.mediaPlayer();
            var state = mp.status().state();
            long length = mp.status().length();
            if (length > 0 && targetMs >= length) {
                targetMs = Math.max(0, length - 50);
            }
            if (state == uk.co.caprica.vlcj.player.base.State.ENDED || state == uk.co.caprica.vlcj.player.base.State.STOPPED) {
                String path = getMediaFilePath();
                if (path != null) {
                    mp.media().startPaused(path, ":start-time=" + (targetMs / 1000.0), ":file-caching=150", ":clock-jitter=0");
                    mp.controls().setTime(targetMs);
                    mp.controls().setPause(true);
                } else {
                    mp.controls().play();
                    mp.controls().setTime(targetMs);
                    mp.controls().setPause(true);
                }
            } else {
                mp.controls().setPause(true);
                mp.controls().setTime(targetMs);
                mp.controls().setPause(true);
            }
        } catch (Throwable ignored) {}
    }

    private String getMediaFilePath() {
        if (parentComponent instanceof app.MainFenetre) {
            File f = ((app.MainFenetre) parentComponent).getFichierSelectionne();
            if (f != null && f.exists()) return f.getAbsolutePath();
        }
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                if (mediaPlayerComponent.mediaPlayer().media().info() != null) {
                    return mediaPlayerComponent.mediaPlayer().media().info().mrl();
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }
}
