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
    private int signeMpbKeyCode = KeyEvent.VK_NUMPAD4;
    private int signeFvrKeyCode = KeyEvent.VK_NUMPAD5;
    private int signeNeutralKeyCode = KeyEvent.VK_NUMPAD6;
    private int signeVoyelleKeyCode = KeyEvent.VK_NUMPAD7;
    private int signeRespirationKeyCode = KeyEvent.VK_NUMPAD8;
    private boolean skipNextTyped = false;
    private TimelinePanel timelinePanel;
    
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
             KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD5,
             KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD7, KeyEvent.VK_NUMPAD8,
             roles, parentComponent);
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
        // ★ Séparateur standard (M ou configuré)
        if (e.getKeyCode() == separateurKeyCode) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) {
                band = 0;
            }
            timelinePanel.addSeparatorAtCursor(band);
            e.consume();
            return true;
        }

        // ★ Fin de phrase (Pavé numérique 3 par défaut ou touche configurée)
        if (e.getKeyCode() == finPhraseKeyCode || (e.getKeyCode() == KeyEvent.VK_NUMPAD3 && finPhraseKeyCode == KeyEvent.VK_NUMPAD3)) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.endPhraseAtCursor(band);
            e.consume();
            return true;
        }

        // ★ Labiale MPB (Pavé numérique 4 par défaut ou touche configurée)
        if (e.getKeyCode() == signeMpbKeyCode || (e.getKeyCode() == KeyEvent.VK_NUMPAD4 && signeMpbKeyCode == KeyEvent.VK_NUMPAD4)) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.MPB);
            e.consume();
            return true;
        }

        // ★ Demi-labiale / Dentale FVR (Pavé numérique 5 par défaut ou touche configurée)
        if (e.getKeyCode() == signeFvrKeyCode || (e.getKeyCode() == KeyEvent.VK_NUMPAD5 && signeFvrKeyCode == KeyEvent.VK_NUMPAD5)) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.FVR);
            e.consume();
            return true;
        }

        // ★ Consonne neutre (Pavé numérique 6 par défaut ou touche configurée)
        if (e.getKeyCode() == signeNeutralKeyCode || (e.getKeyCode() == KeyEvent.VK_NUMPAD6 && signeNeutralKeyCode == KeyEvent.VK_NUMPAD6)) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.NEUTRAL);
            e.consume();
            return true;
        }

        // ★ Grande ouverture A / voyelles (Pavé numérique 7 par défaut ou touche configurée)
        if (e.getKeyCode() == signeVoyelleKeyCode || (e.getKeyCode() == KeyEvent.VK_NUMPAD7 && signeVoyelleKeyCode == KeyEvent.VK_NUMPAD7)) {
            skipNextTyped = true;
            int band = timelinePanel.getSelectedBand();
            if (band < 0) band = 0;
            timelinePanel.addSeparatorAtCursor(band, SeparatorMark.SignType.OPEN_A);
            e.consume();
            return true;
        }

        // ★ Signe Respiration / Souffle h/ (Pavé numérique 8 par défaut ou touche configurée)
        if (e.getKeyCode() == signeRespirationKeyCode || (e.getKeyCode() == KeyEvent.VK_NUMPAD8 && signeRespirationKeyCode == KeyEvent.VK_NUMPAD8)) {
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
                if (timelinePanel.zoomIn()) {
                    skipNextTyped = true;
                }
                e.consume();
                return true;
            }
            if (minusShortcut) {
                if (timelinePanel.zoomOut()) {
                    skipNextTyped = true;
                }
                e.consume();
                return true;
            }
        }

        // Mode édition — gestion des touches propres à l'édition de texte
        if (timelinePanel.isEditing()) {

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

            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
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

        // NUMPAD1 : ajout de repère de plan
        if (e.getKeyCode() == KeyEvent.VK_NUMPAD1) {
            timelinePanel.addPlanMarkerAtCursor();
            e.consume();
            return true;
        }

        // NUMPAD2 : début de phrase
        if (e.getKeyCode() == KeyEvent.VK_NUMPAD2) {
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
    public void keyReleased(KeyEvent e) {}

    @Override
    public void keyTyped(KeyEvent e) {
        if (skipNextTyped) {
            skipNextTyped = false;
            e.consume();
            return;
        }
        if (!timelinePanel.isEditing()) {
            e.consume();
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

    public void togglePlayback() {
        togglePlayback(1);
    }

    public void togglePlayback(int direction) {
        if (mediaPlayerComponent != null && mediaPlayerComponent.mediaPlayer() != null) {
            try {
                var mp = mediaPlayerComponent.mediaPlayer();
                float rate = direction >= 0 ? 1.0f : -1.0f;
                var state = mp.status().state();

                if (timer.isRunning()) {
                    // On met en pause : stopper le timer et VLC
                    timer.toggle(direction);
                    if (mp.status().isPlaying()) {
                        mp.controls().pause();
                    }
                    mp.controls().setTime((long) (timer.getTime() * 1000));
                } else {
                    // On démarre la lecture
                    long targetMs = (long) (timer.getTime() * 1000);
                    if (state == uk.co.caprica.vlcj.player.base.State.ENDED || state == uk.co.caprica.vlcj.player.base.State.STOPPED) {
                        String path = getMediaFilePath();
                        if (path != null) {
                            mp.media().play(path, ":start-time=" + (targetMs / 1000.0));
                        } else {
                            mp.controls().play();
                        }
                        mp.controls().setRate(rate);
                        mp.controls().setTime(targetMs);
                    } else {
                        mp.controls().setRate(rate);
                        mp.controls().setTime(targetMs);
                        mp.controls().play();
                    }
                    timer.toggle(direction);
                }
            } catch (Throwable t) {
                timer.toggle(direction);
            }
        } else {
            timer.toggle(direction);
        }
    }

    public void seekTime(double deltaSeconds) {
        if (deltaSeconds == 0) return;
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

        safeSeekAndPause((long) (targetSec * 1000));
    }

    public void seekToTime(double targetSeconds) {
        if (timer.isRunning()) {
            timer.toggle();
        }

        // Toujours caler sur les graduations exactes de 0.1s (1 marquage par 1 marquage)
        double targetSec = Math.max(0.0, Math.round(targetSeconds * 10.0) / 10.0);
        timer.setTime(targetSec);

        safeSeekAndPause((long) (targetSec * 1000));
    }

    private void safeSeekAndPause(long targetMs) {
        if (mediaPlayerComponent == null || mediaPlayerComponent.mediaPlayer() == null) return;
        try {
            var mp = mediaPlayerComponent.mediaPlayer();
            var state = mp.status().state();
            if (state == uk.co.caprica.vlcj.player.base.State.ENDED || state == uk.co.caprica.vlcj.player.base.State.STOPPED) {
                String path = getMediaFilePath();
                if (path != null) {
                    mp.media().startPaused(path, ":start-time=" + (targetMs / 1000.0));
                    mp.controls().setTime(targetMs);
                } else {
                    mp.controls().play();
                    mp.controls().setTime(targetMs);
                    mp.controls().pause();
                }
            } else {
                if (mp.status().isPlaying()) {
                    mp.controls().pause();
                }
                mp.controls().setTime(targetMs);
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
