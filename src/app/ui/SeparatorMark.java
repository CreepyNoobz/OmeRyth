package app.ui;

public class SeparatorMark {
    public enum Type {
        START,
        INNER,
        END,
        LEGACY
    }

    public enum SignType {
        DEFAULT,     // Séparateur standard
        MPB,         // Labiale (M, P, B - lèvres fermées)
        FVR,         // Demi-labiale / Dentale (F, V, R)
        NEUTRAL,     // Consonne neutre
        OPEN_A,      // Grande ouverture (A / voyelles)
        RESPIRATION  // Inspiration / Souffle (h/)
    }

    public int x;
    public Type type;
    public int splitIndex;
    public SignType signType = SignType.DEFAULT;
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

    public boolean isStartBoundary() {
        return type == Type.START || type == Type.LEGACY;
    }

    public boolean isEndBoundary() {
        return type == Type.END || type == Type.LEGACY;
    }
}