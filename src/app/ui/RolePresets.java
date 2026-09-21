package app.ui;

import java.awt.*;
import java.util.*;

/**
 * Catalogue des configurations types de personnages pour les sessions de doublage.
 * <p>
 * Fournit un ensemble de modèles prêts à l'emploi (Solo, Duo, Trio, Quartet) associant
 * à chaque comédien un nom générique et une couleur contrastée pour faciliter l'initialisation
 * rapide d'un nouveau projet OmeRyth.
 * </p>
 */
public class RolePresets {

    /**
     * Structure de données encapsulant un ensemble prédéfini de comédiens.
     */
    public static class RolePreset {
        /** Nom descriptif du gabarit (ex: "Duo", "Quartet"). */
        public String name;
        /** Codes hexadécimaux des teintes allouées à chaque intervenant. */
        public String[] roleLabelColors;

        public RolePreset(String name, String[] roleLabelColors) {
            this.name = name;
            this.roleLabelColors = roleLabelColors;
        }
    }

    private static final Map<String, RolePreset> PRESETS = new LinkedHashMap<>();

    static {
        // Présetsstandard
        PRESETS.put("Vide", new RolePreset("Vide", new String[]{}));
        
        PRESETS.put("Chanteur", new RolePreset("Chanteur", 
            new String[]{"#FF6B6B"})); // Rouge
        
        PRESETS.put("Danseur", new RolePreset("Danseur", 
            new String[]{"#4ECDC4"})); // Turquoise
        
        PRESETS.put("Narrateur", new RolePreset("Narrateur", 
            new String[]{"#FFD93D"})); // Jaune
        
        PRESETS.put("Duo (Chanteur & Danseur)", new RolePreset("Duo", 
            new String[]{"#FF6B6B", "#4ECDC4"})); // Rouge et Turquoise
        
        PRESETS.put("Trio (Chanteur, Danseur, Narrateur)", new RolePreset("Trio", 
            new String[]{"#FF6B6B", "#4ECDC4", "#FFD93D"})); // Rouge, Turquoise, Jaune
        
        PRESETS.put("Quartet (4 rôles)", new RolePreset("Quartet", 
            new String[]{"#FF6B6B", "#4ECDC4", "#FFD93D", "#95E1D3"})); // 4 couleurs
    }

    public static Collection<String> getPresetNames() {
        return PRESETS.keySet();
    }

    public static RolePreset getPreset(String name) {
        return PRESETS.getOrDefault(name, PRESETS.get("Vide"));
    }

    public static ArrayList<Role> createRolesFromPreset(String presetName) {
        ArrayList<Role> roles = new ArrayList<>();
        RolePreset preset = getPreset(presetName);
        
        String[] defaultNames = {"Chanteur", "Danseur", "Narrateur", "Rôle 4"};
        
        for (int i = 0; i < preset.roleLabelColors.length; i++) {
            String name = defaultNames[i % defaultNames.length];
            Color color = Color.decode(preset.roleLabelColors[i]);
            Role role = new Role(name, color);
            roles.add(role);
        }
        
        return roles;
    }
}
