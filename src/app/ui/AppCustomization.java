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

    public Color timerBackground = new Color(0x101216);
    public Color timerTextColor = new Color(0xECEEEF);
    public Color historyBackground = new Color(0x101216);
    public Color mediaBackground = new Color(0x0A0B0D);
    public Color timelineEvenBand = new Color(0x15171C);
    public Color timelineOddBand = new Color(0x1B1E24);
    public Color timelineSelectedBand = new Color(0x14261B);
    public Color timelineGrid = new Color(255, 255, 255, 26);
    public Color timelineCursor = new Color(0xFF453A);
    public Color timelineSeparator = new Color(0x2FBE55);

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
    public Color waveformColor = new Color(47, 190, 85, 55);
    
    public String defaultProjectFormat = "rythmo"; // "rythmo" or "detx"
    public String appLanguage = "fr"; // "fr" (Français) ou "en" (English)
    public String keyboardLayout = "auto"; // "auto", "azerty", "qwerty"
}
