package app;

import app.utils.FileUtils;
import java.io.File;

public class TestFileUtils {
    public static void main(String[] args) {
        System.out.println("Testing chooseOpenFile...");
        File selected = FileUtils.chooseOpenFile(null, "Choisir vidéo/audio", 
            "mp4", "mp3", "wav", "ogg", "avi", "mkv");
            
        System.out.println("Result: " + selected);
    }
}
