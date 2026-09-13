package app.ui;

import java.awt.Color;

public class AppCustomization {
    public static final String BAND_BG_COLOR = "COLOR";
    public static final String BAND_BG_IMAGE_GLOBAL = "IMAGE_GLOBAL";
    public static final String BAND_BG_IMAGE_PER_BAND = "IMAGE_PER_BAND";

    public int bandCount = 4;
    public int bandHeight = 50;
    public int timelineCursorX = 80;
    public int timerPanelWidth = 180;
    
    public boolean autoResizeTimerFont = true;
    public int timerFontSize = 36;

    public Color timerBackground = new Color(24, 24, 24);
    public Color timerTextColor = new Color(230, 230, 230);
    public Color historyBackground = new Color(24, 24, 24);
    public Color mediaBackground = new Color(30, 30, 30);
    public Color timelineEvenBand = new Color(37, 37, 38);
    public Color timelineOddBand = new Color(45, 45, 48);
    public Color timelineSelectedBand = new Color(50, 50, 70);
    public Color timelineGrid = new Color(80, 80, 80);
    public Color timelineCursor = new Color(220, 50, 50);
    public Color timelineSeparator = new Color(0, 200, 255);

    public String timerImagePath = "";
    public String historyImagePath = "";
    public String mediaImagePath = "";

    // One parameter decides whether bands use color or images.
    public String bandBackgroundMode = BAND_BG_COLOR;
    public String globalBandImagePath = "";
    // ';' separated paths, one per band index.
    public String perBandImagePaths = "";
    public String timelineFontFamily = "Segoe UI";
    
    public boolean showWaveform = true;
    public Color waveformColor = new Color(0, 180, 255, 40);
    
    public String defaultProjectFormat = "rythmo"; // "rythmo" or "detx"
}
