package app.ui;

import java.awt.Color;

public class Role {
    public String name;
    public Color color;

    public Role(String name, Color color) {
        this.name = name;
        this.color = color;
    }

    @Override
    public String toString() { return name; }
}
