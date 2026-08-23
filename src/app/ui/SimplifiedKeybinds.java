package app.ui;

import java.awt.event.KeyEvent;

/**
 * Gestionnaire des raccourcis clavier simplifiés.
 * Réduit à 5-6 raccourcis essentiels pour éviter la surcharge cognitive.
 */
public class SimplifiedKeybinds {

    public static class Keybind {
        public int keyCode;
        public String description;
        public String action;

        public Keybind(int keyCode, String description, String action) {
            this.keyCode = keyCode;
            this.description = description;
            this.action = action;
        }
    }

    // Les 5 raccourcis essentiels
    public static final Keybind PLAY_PAUSE = new Keybind(
        KeyEvent.VK_SPACE, 
        "ESPACE", 
        "Lecture / Arrêt"
    );

    public static final Keybind MOVE_FORWARD = new Keybind(
        KeyEvent.VK_RIGHT, 
        "→", 
        "Avancer de 0.5s"
    );

    public static final Keybind MOVE_BACKWARD = new Keybind(
        KeyEvent.VK_LEFT, 
        "←", 
        "Reculer de 0.5s"
    );

    public static final Keybind RETURN_TO_START = new Keybind(
        KeyEvent.VK_R, 
        "R", 
        "Retour au début"
    );

    public static final Keybind ADD_SEPARATOR = new Keybind(
        KeyEvent.VK_M, 
        "M", 
        "Ajouter séparateur"
    );

    public static final Keybind UNDO = new Keybind(
        KeyEvent.VK_Z, 
        "CTRL+Z", 
        "Annuler"
    );

    public static final Keybind REDO = new Keybind(
        KeyEvent.VK_Y, 
        "CTRL+Y", 
        "Rétablir"
    );
    
    public static final Keybind ZOOM_IN = new Keybind(
        KeyEvent.VK_EQUALS, 
        "+", 
        "Zoomer"
    );

    public static final Keybind ZOOM_OUT = new Keybind(
        KeyEvent.VK_MINUS, 
        "-", 
        "Dézoomer"
    );

    public static Keybind[] getEssentialKeybinds() {
        return new Keybind[]{
            PLAY_PAUSE,
            MOVE_FORWARD,
            MOVE_BACKWARD,
            RETURN_TO_START,
            ADD_SEPARATOR,
            UNDO,
            REDO,
            ZOOM_IN,
            ZOOM_OUT
        };
    }

    public static String getKeybindsList() {
        StringBuilder sb = new StringBuilder();
        sb.append("Raccourcis clavier essentiels :\n\n");
        for (Keybind kb : getEssentialKeybinds()) {
            sb.append(String.format("%-12s = %s\n", kb.description, kb.action));
        }
        return sb.toString();
    }
}
