package app.ui;

public class SeparatorMark {
    public enum Type {
        START,
        INNER,
        END,
        LEGACY
    }

    public int x;
    public Type type;
    public int splitIndex;

    public SeparatorMark(int x, Type type) {
        this(x, type, -1);
    }

    public SeparatorMark(int x, Type type, int splitIndex) {
        this.x = x;
        this.type = (type != null) ? type : Type.LEGACY;
        this.splitIndex = splitIndex;
    }

    public boolean isStartBoundary() {
        return type == Type.START || type == Type.LEGACY;
    }

    public boolean isEndBoundary() {
        return type == Type.END || type == Type.LEGACY;
    }
}