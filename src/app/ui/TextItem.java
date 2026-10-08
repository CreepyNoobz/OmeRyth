package app.ui;

/**
 * Représente un segment de texte (réplique ou portion de dialogue) sur la bande rythmo.
 * 
 * Chaque TextItem est ancré à une coordonnée temporelle 'x' (en coordonnées monde)
 * sur une piste/bande spécifique, et peut être associé à un comédien/personnage ('role').
 */
public class TextItem {

    /** Contenu textuel de la réplique ou du fragment */
    public String text;

    /** Coordonnée horizontale temporelle (en pixels monde) */
    public int x;

    /** Indice de la piste (bande) sur laquelle repose le texte (0 = première bande) */
    public int band;

    /** Mise en cache de la largeur calculée pour optimiser le rendu à 60 FPS */
    public double cachedWidth;

    /** Code clavier associé pour déclenchement rapide (optionnel) */
    public int keyCode = -1;

    /** Rôle / personnage assigné à cette réplique (couleur, nom du comédien) */
    public Role role = null;

    public TextItem(String text, int x, int band) {
        this.text = text;
        this.x = x;
        this.band = band;
    }
}