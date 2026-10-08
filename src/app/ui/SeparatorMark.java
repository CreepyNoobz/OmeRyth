package app.ui;

/**
 * Représente un repère visuel de synchronisation labiale (séparateur ou signe de calligraphie rythmo).
 * 
 * Dans la tradition du doublage francophone, ces repères indiquent aux comédiens :
 * - Le début exact d'une prise de parole (START)
 * - La fin précise du mouvement des lèvres (END)
 * - Les appuis labiaux stricts (bilabiales M, P, B où les lèvres se touchent)
 * - Les labiodentales (F, V, R où les dents touchent la lèvre inférieure)
 * - Les ouvertures voyelles amples (A, O)
 * - Les respirations et soupirs audibles (h/)
 */
public class SeparatorMark {

    /** Typologie structurelle du repère sur la timeline */
    public enum Type {
        /** Repère de début de phrase (triangle vert ou icône Start) */
        START,
        /** Repère syllabique interne subdivisant une réplique pour guider le débit */
        INNER,
        /** Repère de clôture de réplique (triangle rouge ou icône End) */
        END,
        /** Repère double historique (losange / double flèche) */
        LEGACY
    }

    /** Signe phonétique et labial spécifique pour le comédien */
    public enum SignType {
        /** Séparateur neutre standard sans annotation particulière */
        DEFAULT,
        /** Bilabiale (M, P, B : lèvres hermétiquement fermées à l'impact) */
        MPB,
        /** Labiodentale / Dentale (F, V, R : incisives supérieures sur la lèvre inférieure) */
        FVR,
        /** Consonne neutre standard */
        NEUTRAL,
        /** Grande ouverture buccale (voyelle A très ouverte ou cri) */
        OPEN_A,
        /** Respiration, reprise de souffle ou inspiration audible (notée traditionnellement 'h/') */
        RESPIRATION
    }

    /** Coordonnée horizontale temporelle (en pixels monde) */
    public int x;

    /** Type structurel (START, INNER, END, LEGACY) */
    public Type type;

    /** Indice de découpe du texte (nombre de caractères du mot précédant ce repère) */
    public int splitIndex;

    /** Signe labial phonétique associé */
    public SignType signType = SignType.DEFAULT;

    /** Identifiant brut d'origine pour l'import/export DETX Cappella */
    public String rawDetxType = null;

    public SeparatorMark(int x, Type type) {
        this(x, type, -1, SignType.DEFAULT);
    }

    public SeparatorMark(int x, Type type, int splitIndex) {
        this(x, type, splitIndex, SignType.DEFAULT);
    }

    public SeparatorMark(int x, Type type, int splitIndex, SignType signType) {
        this.x = x;
        this.type = (type != null) ? type : Type.LEGACY;
        this.splitIndex = splitIndex;
        this.signType = (signType != null) ? signType : SignType.DEFAULT;
    }

    /** Indique si ce repère marque le début d'un segment de parole */
    public boolean isStartBoundary() {
        return type == Type.START || type == Type.LEGACY;
    }

    /** Indique si ce repère marque la fin d'un segment de parole */
    public boolean isEndBoundary() {
        return type == Type.END || type == Type.LEGACY;
    }
}