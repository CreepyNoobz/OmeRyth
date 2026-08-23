package app.utils;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import app.utils.TimerClass;
import app.ui.TimelinePanel;
import app.ui.Role;
import app.ui.RoleWindow;
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
        this.roles = roles;
        this.parentComponent = parentComponent;
        panel.setPhraseCreationListener(this);
    }

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
        // En mode édition : seules les touches d'édition et raccourcis Ctrl sont actives.
        // La touche séparateur et toutes les autres touches spéciales sont neutralisées
        // pour ne pas interférer avec la saisie de texte.
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
            // Hors mode édition : séparateur, undo/redo, zoom, et commandes de lecture actives.

            // ★ Séparateur — uniquement hors mode édition
            if (e.getKeyCode() == separateurKeyCode) {
                int band = timelinePanel.getSelectedBand();
                skipNextTyped = true;
                if (band != -1) {
                    timelinePanel.addSeparatorAtCursor(band);
                }
                e.consume();
                return true;
            }

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
            togglePlayback();
            e.consume();
            return true;
        }

        // ===== Avancer / Reculer =====
        if (e.getKeyCode() == avanceMSKeyCode || e.getKeyCode() == reculerMSKeyCode) {
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
            timer.reset();
            if (mediaPlayerComponent != null) {
                mediaPlayerComponent.mediaPlayer().controls().setTime(0);
                if (mediaPlayerComponent.mediaPlayer().status().isPlaying()) {
                    mediaPlayerComponent.mediaPlayer().controls().pause();
                }
            }
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

    private void togglePlayback() {
        togglePlayback(1);
    }

    private void togglePlayback(int direction) {
        if (mediaPlayerComponent != null) {
            float rate = direction >= 0 ? 1.0f : -1.0f;
            boolean playing = mediaPlayerComponent.mediaPlayer().status().isPlaying();
            if (playing) {
                if (timer.getDirection() == direction) {
                    // On pause : d'abord stopper VLC, puis toggler le timer (qui arrondit le temps),
                    // puis resynchroniser VLC sur le temps arrondi du timer.
                    mediaPlayerComponent.mediaPlayer().controls().pause();
                    timer.toggle(direction);
                    mediaPlayerComponent.mediaPlayer().controls().setTime((long) (timer.getTime() * 1000));
                    return; // déjà togglé, on sort
                } else {
                    mediaPlayerComponent.mediaPlayer().controls().setRate(rate);
                }
            } else {
                // On démarre : synchroniser VLC sur le timer, puis lancer la lecture.
                mediaPlayerComponent.mediaPlayer().controls().setRate(rate);
                mediaPlayerComponent.mediaPlayer().controls().setTime((long) (timer.getTime() * 1000));
                mediaPlayerComponent.mediaPlayer().controls().play();
            }
        }
        timer.toggle(direction);
    }

    private void seekTime(double deltaSeconds) {
        if (deltaSeconds == 0) return;
        if (timer.isRunning()) {
            timer.toggle();
        }

        timer.addTime(deltaSeconds);
        if (timer.getTime() < 0) {
            timer.reset();
        }
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.mediaPlayer().controls().setTime((long) (timer.getTime() * 1000));
        }
    }
}
