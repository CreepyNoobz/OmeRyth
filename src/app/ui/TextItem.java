package app.ui;

public class TextItem {
    public String text;
    public int x;
    public int band;
    public double cachedWidth;
    public int keyCode = -1;
    public Role role = null;

    public TextItem(String text, int x, int band) {
        this.text = text;
        this.x = x;
        this.band = band;
    }
}