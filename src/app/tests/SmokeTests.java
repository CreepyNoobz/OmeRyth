package app.tests;

import app.ui.Role;
import app.ui.TimelinePanel;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SmokeTests {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        TimelinePanel timeline = new TimelinePanel();
        timeline.setSize(900, 260);

        ArrayList<Role> roles = new ArrayList<>();
        Role narrator = new Role("Narrateur", new Color(0x2AA198));
        roles.add(narrator);

        timeline.setSelectedBand(0);
        timeline.startPhrase(0, narrator);
        for (char c : "Bonjour le monde".toCharArray()) {
            timeline.typeChar(c);
        }
        timeline.endPhrase();

        assertTrue(timeline.hasContent(), "Le contenu timeline devrait exister");

        File tmpProject = File.createTempFile("omeryth-smoke-", ".rythmo");
        File fakeVideo = new File("C:/temp/demo.mp4");
        timeline.saveProject(tmpProject, fakeVideo, roles);

        TimelinePanel loadedTimeline = new TimelinePanel();
        loadedTimeline.setSize(900, 260);
        ArrayList<Role> loadedRoles = new ArrayList<>();
        File loadedVideo = loadedTimeline.loadProject(tmpProject, loadedRoles);

        assertTrue(loadedVideo != null, "Le chemin video charge ne doit pas etre null");
        assertTrue(loadedTimeline.hasContent(), "Le projet charge devrait contenir du texte");

        BufferedImage frame = loadedTimeline.renderFrame(640, 180, 0.4);
        assertTrue(frame != null && frame.getWidth() == 640 && frame.getHeight() == 180,
                "Le rendu frame doit fonctionner");

        List<TimelinePanel.ActionHistoryEntry> history = loadedTimeline.getActionHistory(5);
        assertTrue(!history.isEmpty(), "L'historique doit contenir au moins une action");

        System.out.println("SmokeTests OK");
        System.exit(0);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Echec test: " + message);
        }
    }
}
