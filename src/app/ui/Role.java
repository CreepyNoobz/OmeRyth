package app.ui;

import java.awt.Color;

/**
 * Représente un personnage ou un comédien de doublage (rôle) dans le projet OmeRyth.
 * <p>
 * Dans l'univers de la post-synchronisation et du doublage professionnel :
 * <ul>
 *   <li>Chaque réplique sur la bande rythmo est associée à un comédien / personnage.</li>
 *   <li>Une couleur chromatique distinctive est attribuée à chaque rôle pour permettre
 *       une identification visuelle instantanée par les comédiens lors de l'enregistrement au plateau.</li>
 *   <li>Ce modèle est sérialisé dans les fichiers projets {@code .omeryth} et exporté dans les balises
 *       {@code <role id="..." color="..."/>} des fichiers standards DETX / Cappella.</li>
 * </ul>
 * </p>
 */
public class Role {

    /** Nom du comédien ou du personnage incarné (ex: "Narrateur", "Héroïne"). */
    public String name;

    /** Couleur de surlignage et de rendu du texte sur la bande rythmo. */
    public Color color;

    /**
     * Initialise un nouveau rôle avec son identifiant textuel et sa teinte d'affichage.
     *
     * @param name   Nom du personnage ou du comédien.
     * @param color  Couleur associée.
     */
    public Role(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String toString() {
        return name;
    }
}

