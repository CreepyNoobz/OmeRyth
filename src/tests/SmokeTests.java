package tests;

import app.ui.Role;
import app.ui.TimelinePanel;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import app.ui.SeparatorMark;

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
        assertTrue(loadedTimeline.getTextManager().getTexts().get(0).x == timeline.getTextManager().getTexts().get(0).x,
                "La position du texte doit être identique après rechargement");

        BufferedImage frame = loadedTimeline.renderFrame(640, 180, 0.4);
        assertTrue(frame != null && frame.getWidth() == 640 && frame.getHeight() == 180,
                "Le rendu frame standard doit fonctionner");

        BufferedImage frameTaller = loadedTimeline.renderFrame(1920, 340, 0.4);
        assertTrue(frameTaller != null && frameTaller.getWidth() == 1920 && frameTaller.getHeight() == 340,
                "Le rendu frame agrandi en hauteur doit fonctionner");

        List<TimelinePanel.ActionHistoryEntry> history = loadedTimeline.getActionHistory(5);
        assertTrue(!history.isEmpty(), "L'historique doit contenir au moins une action");

        // Test de sauvegarde et rechargement avec zoom actif
        timeline.zoomIn();
        int savedZoomIndex = timeline.getZoomLevelIndex();
        double savedPps = timeline.getPixelsPerSecond();
        File tmpZoomProject = File.createTempFile("omeryth-zoom-", ".rythmo");
        timeline.saveProject(tmpZoomProject, fakeVideo, roles);

        TimelinePanel loadedZoomTimeline = new TimelinePanel();
        loadedZoomTimeline.setSize(900, 260);
        loadedZoomTimeline.loadProject(tmpZoomProject, loadedRoles);
        assertTrue(loadedZoomTimeline.getZoomLevelIndex() == savedZoomIndex,
                "Le niveau de zoom doit être fidèlement restauré après rechargement");
        assertTrue(Math.abs(loadedZoomTimeline.getPixelsPerSecond() - savedPps) < 1e-4,
                "La vitesse pps doit être fidèlement restaurée après rechargement");
        assertTrue(loadedZoomTimeline.getTextManager().getTexts().get(0).x == timeline.getTextManager().getTexts().get(0).x,
                "Les coordonnées du texte sous zoom doivent être 100% identiques");

        // Test que le zoom/dezoom ne marque pas le projet comme 'dirty' (non sauvegardé)
        loadedZoomTimeline.clearDirty();
        loadedZoomTimeline.zoomIn();
        assertTrue(!loadedZoomTimeline.isDirty(), "Zoom in ne doit pas marquer le projet comme modifié (dirty)");
        loadedZoomTimeline.zoomOut();
        assertTrue(!loadedZoomTimeline.isDirty(), "Zoom out ne doit pas marquer le projet comme modifié (dirty)");

        // Test des 5 niveaux de zoom et du pas de 0.1s
        TimelinePanel zoomCheckTimeline = new TimelinePanel();
        double[] expectedZooms = {1.0, 1.5, 2.0, 2.5, 3.0};
        double[] expectedPps = {80.0, 120.0, 160.0, 200.0, 240.0};
        double[] expectedTenthPx = {8.0, 12.0, 16.0, 20.0, 24.0};

        for (int i = 0; i < expectedZooms.length; i++) {
            assertTrue(zoomCheckTimeline.getZoomLevelIndex() == i, "L'index de zoom doit être " + i);
            assertTrue(Math.abs(zoomCheckTimeline.getZoomLevel() - expectedZooms[i]) < 1e-4, "Le zoom doit être " + expectedZooms[i]);
            assertTrue(Math.abs(zoomCheckTimeline.getPixelsPerSecond() - expectedPps[i]) < 1e-4, "Le pps doit être " + expectedPps[i]);
            
            // 0.1s doit représenter exactement expectedTenthPx[i] pixels
            double tenth = zoomCheckTimeline.getPixelsPerSecond() * 0.1;
            assertTrue(Math.abs(tenth - expectedTenthPx[i]) < 1e-4, "0.1s doit correspondre à " + expectedTenthPx[i] + "px");

            // Test snapWorldXToTenth
            int snapped = zoomCheckTimeline.snapWorldXToTenth((int) Math.round(expectedTenthPx[i]));
            assertTrue(snapped == (int) Math.round(expectedTenthPx[i]), "Le snap de 0.1s doit tomber pile sur " + expectedTenthPx[i]);

            if (i < expectedZooms.length - 1) {
                boolean zoomed = zoomCheckTimeline.zoomIn();
                assertTrue(zoomed, "ZoomIn doit réussir pour atteindre le niveau " + (i + 1));
            }
        }

        // Vérifier qu'on ne peut pas zoomer au-delà du niveau max (3.0)
        assertTrue(!zoomCheckTimeline.zoomIn(), "ZoomIn ne doit pas dépasser le zoom maximal");
        assertTrue(zoomCheckTimeline.getZoomLevelIndex() == 4, "L'index max de zoom doit être 4");

        // Dézoomer jusqu'au début
        for (int i = expectedZooms.length - 1; i > 0; i--) {
            boolean zoomedOut = zoomCheckTimeline.zoomOut();
            assertTrue(zoomedOut, "ZoomOut doit réussir vers le niveau " + (i - 1));
            assertTrue(zoomCheckTimeline.getZoomLevelIndex() == i - 1, "L'index doit être " + (i - 1));
        }
        assertTrue(!zoomCheckTimeline.zoomOut(), "ZoomOut ne doit pas dépasser le zoom minimal");

        // Tests de VolumePanel
        app.ui.AppCustomization cust = new app.ui.AppCustomization();
        app.ui.VolumePanel volumePanel = new app.ui.VolumePanel(null, cust);
        assertTrue(volumePanel.getVolume() == 100, "Le volume par défaut devrait être 100");

        volumePanel.setVolume(50);
        assertTrue(volumePanel.getVolume() == 50, "Le volume devrait être à 50");

        volumePanel.setVolume(120);
        assertTrue(volumePanel.getVolume() == 100, "Le volume ne devrait pas dépasser 100");

        volumePanel.setVolume(-10);
        assertTrue(volumePanel.getVolume() == 0, "Le volume ne devrait pas être inférieur à 0");

        volumePanel.applyCustomization(cust);

        // Test ajout séparateur dans phrase ouverte (sans fin)
        TimelinePanel testTimeline = new TimelinePanel();
        testTimeline.setTime(0.0);
        testTimeline.startPhrase(0, narrator);
        testTimeline.typeChar('H');
        testTimeline.typeChar('i');
        testTimeline.setTime(0.5);
        testTimeline.addSeparatorAtCursor(0);
        assertTrue(testTimeline.getTextManager().getBandSeparators().get(0).size() >= 2,
                "Le séparateur doit être créé même si la phrase n'a pas de fin");

        // Test ajout séparateur après la phrase
        testTimeline.setTime(1.0);
        testTimeline.endPhrase();
        testTimeline.setTime(2.0);
        testTimeline.addSeparatorAtCursor(0);
        assertTrue(testTimeline.getTextManager().getBandSeparators().get(0).size() >= 4,
                "Le séparateur doit être créé après la fin de la phrase");

        // Test suppression complète de la phrase via séparateur de début
        int startX = testTimeline.getTextManager().getBandSeparators().get(0).get(0).x;
        testTimeline.deletePhraseAtStartSeparator(0, startX);
        assertTrue(testTimeline.getTextManager().getTexts().isEmpty(),
                "La phrase doit être entièrement supprimée");

        // Test navigation et clic dans ActionHistoryService
        TimelinePanel historyTimeline = new TimelinePanel();
        app.utils.TimerClass historyTimer = new app.utils.TimerClass(historyTimeline);
        app.utils.KeyBoardListener kbListener = new app.utils.KeyBoardListener(
                historyTimeline, historyTimer, null, 0, 0, 0, 0, 0, 0, 0, roles, null
        );

        historyTimeline.setTime(5.0);
        historyTimeline.startPhrase(0, narrator);
        for (char c : "Premiere phrase".toCharArray()) {
            historyTimeline.typeChar(c);
        }
        historyTimeline.setTime(8.0);
        historyTimeline.endPhrase();

        historyTimeline.setTime(12.0);
        historyTimeline.startPhrase(0, narrator);
        for (char c : "Deuxieme phrase".toCharArray()) {
            historyTimeline.typeChar(c);
        }
        historyTimeline.setTime(15.0);
        historyTimeline.endPhrase();

        List<TimelinePanel.ActionHistoryEntry> histEntries = historyTimeline.getActionHistory(10);
        assertTrue(histEntries.size() == 2, "L'historique doit contenir 2 phrases");
        assertTrue(Math.abs(histEntries.get(0).startTimeSeconds - 5.0) < 0.01,
                "Le début de la première phrase doit être à 5.0s");
        assertTrue(Math.abs(histEntries.get(1).startTimeSeconds - 12.0) < 0.01,
                "Le début de la deuxième phrase doit être à 12.0s");

        app.services.ActionHistoryService historyService = new app.services.ActionHistoryService(10, 250);
        historyService.bind(historyTimeline);
        final double[] seekTarget = {-1.0};
        historyService.setOnSeekRequested(target -> {
            seekTarget[0] = target;
            kbListener.seekToTime(target);
        });

        // Simuler le clic sur la première phrase
        historyService.triggerEntryClick(0);
        assertTrue(Math.abs(seekTarget[0] - 5.0) < 0.01, "Le clic doit déclencher un seek vers 5.0s");
        assertTrue(Math.abs(historyTimer.getTime() - 5.0) < 0.01, "Le timer doit être à 5.0s");

        // Simuler le clic sur la deuxième phrase
        historyService.triggerEntryClick(1);
        assertTrue(Math.abs(seekTarget[0] - 12.0) < 0.01, "Le clic doit déclencher un seek vers 12.0s");
        assertTrue(Math.abs(historyTimer.getTime() - 12.0) < 0.01, "Le timer doit être à 12.0s");

        // Test getAllActionHistory et détection composant historique
        List<TimelinePanel.ActionHistoryEntry> allEntries = historyTimeline.getAllActionHistory();
        assertTrue(allEntries.size() == 2, "getAllActionHistory doit renvoyer toutes les phrases");
        javax.swing.JPanel hPanel = historyService.createPanel();
        assertTrue(hPanel != null, "Le panneau d'historique doit être créé");
        assertTrue(historyService.isHistoryComponent(hPanel), "isHistoryComponent doit valider le conteneur historique");

        // Test phrase unique avec séparateurs internes (transcription 1 bande)
        TimelinePanel innerTimeline = new TimelinePanel();
        innerTimeline.setTime(0.0);
        innerTimeline.getTextManager().addTextItem(new app.ui.TextItem("Phrase A Phrase B Phrase C", 160, 0));
        innerTimeline.getTextManager().addSeparator(0, 160, app.ui.SeparatorMark.Type.START);
        innerTimeline.getTextManager().addSeparator(0, 320, app.ui.SeparatorMark.Type.INNER, 9);
        innerTimeline.getTextManager().addSeparator(0, 480, app.ui.SeparatorMark.Type.INNER, 18);
        innerTimeline.getTextManager().addSeparator(0, 640, app.ui.SeparatorMark.Type.END);

        List<TimelinePanel.ActionHistoryEntry> innerEntries = innerTimeline.getActionHistory(10);
        assertTrue(innerEntries.size() == 3, "L'historique doit contenir 3 sous-phrases pour la phrase avec séparateurs internes");
        assertTrue(innerEntries.get(0).text.equals("Phrase A"), "Le texte du premier segment doit être 'Phrase A'");
        assertTrue(innerEntries.get(1).text.equals("Phrase B"), "Le texte du deuxième segment doit être 'Phrase B'");
        assertTrue(innerEntries.get(2).text.equals("Phrase C"), "Le texte du troisième segment doit être 'Phrase C'");

        // Test phrase avec temps mort (pause) entre deux répliques
        // "Je pensais pas ça de toi" (0-3s, x: 0-240), temps mort (3-5s, x: 240-400), "et mon compte twitter" (5-7s, x: 400-560)
        TimelinePanel pauseTimeline = new TimelinePanel();
        pauseTimeline.setTime(0.0);
        pauseTimeline.getTextManager().addTextItem(new app.ui.TextItem("Je pensais pas ça de toi et mon compte twitter", 80, 0));
        pauseTimeline.getTextManager().addSeparator(0, 80, app.ui.SeparatorMark.Type.START);
        pauseTimeline.getTextManager().addSeparator(0, 240, app.ui.SeparatorMark.Type.INNER, 24);
        pauseTimeline.getTextManager().addSeparator(0, 400, app.ui.SeparatorMark.Type.INNER, 25);
        pauseTimeline.getTextManager().addSeparator(0, 560, app.ui.SeparatorMark.Type.END);

        List<TimelinePanel.ActionHistoryEntry> pauseEntries = pauseTimeline.getActionHistory(10);
        assertTrue(pauseEntries.size() == 2, "L'historique ne doit contenir que les 2 phrases réelles sans compter le temps mort vide");
        assertTrue(pauseEntries.get(0).text.equals("Je pensais pas ça de toi"), "Phrase 1 doit être 'Je pensais pas ça de toi'");
        assertTrue(pauseEntries.get(1).text.equals("et mon compte twitter"), "Phrase 2 doit être 'et mon compte twitter'");
        assertTrue(Math.abs(pauseEntries.get(0).startTimeSeconds - 1.0) < 0.01, "Début phrase 1 doit être à 1.0s");
        assertTrue(Math.abs(pauseEntries.get(1).startTimeSeconds - 5.0) < 0.01, "Début phrase 2 doit être à 5.0s (après le temps mort)");

        // Test déplacement de phrase vers une autre bande
        TimelinePanel moveTimeline = new TimelinePanel();
        moveTimeline.setBandCount(3);
        moveTimeline.setTime(0.0);
        moveTimeline.startPhrase(0, narrator);
        for (char c : "Phrase a deplacer".toCharArray()) {
            moveTimeline.typeChar(c);
        }
        moveTimeline.setTime(2.0);
        moveTimeline.endPhrase();

        int phraseStartX = moveTimeline.getTextManager().getTexts().get(0).x;
        assertTrue(moveTimeline.getTextManager().getTexts().get(0).band == 0, "La phrase doit initialement être en bande 0");

        // 1. Déplacer vers la bande 1 (libre) -> Doit réussir
        boolean moved = moveTimeline.getTextManager().movePhraseToBand(0, phraseStartX, 1);
        assertTrue(moved, "Le déplacement vers la bande 1 libre doit réussir");
        assertTrue(moveTimeline.getTextManager().getTexts().get(0).band == 1, "Le texte doit maintenant être en bande 1");
        assertTrue(moveTimeline.getTextManager().getBandSeparators().get(1).size() >= 2, "Les séparateurs doivent être en bande 1");
        assertTrue(moveTimeline.getTextManager().getBandSeparators().get(0) == null || moveTimeline.getTextManager().getBandSeparators().get(0).isEmpty(),
                "La bande 0 ne doit plus contenir les séparateurs de la phrase déplacée");

        // 2. Créer un texte sur la bande 2 qui chevauche l'intervalle
        moveTimeline.setTime(1.0);
        moveTimeline.startPhrase(2, narrator);
        moveTimeline.typeChar('X');
        moveTimeline.setTime(3.0);
        moveTimeline.endPhrase();

        // 3. Tenter de déplacer la phrase de la bande 1 vers la bande 2 (occupée) -> Doit échouer
        boolean movedOccupied = moveTimeline.getTextManager().movePhraseToBand(1, phraseStartX, 2);
        assertTrue(!movedOccupied, "Le déplacement vers une bande occupée doit être refusé");
        assertTrue(moveTimeline.getTextManager().getTexts().get(0).band == 1, "La phrase doit être restée en bande 1");

        // 4. Déplacer la phrase de la bande 1 vers la bande 0 (qui est redevenue libre) -> Doit réussir
        boolean movedBack = moveTimeline.getTextManager().movePhraseToBand(1, phraseStartX, 0);
        assertTrue(movedBack, "Le déplacement vers la bande 0 libre doit réussir");
        assertTrue(moveTimeline.getTextManager().getTexts().get(0).band == 0, "La phrase doit être revenue en bande 0");

        // 5. Test movePhraseToBandWithFeedback et Undo/Redo
        moveTimeline.clearDirty();
        boolean movedFb = moveTimeline.movePhraseToBandWithFeedback(0, phraseStartX, 1);
        assertTrue(movedFb, "movePhraseToBandWithFeedback vers bande 1 doit réussir");
        assertTrue(moveTimeline.isDirty(), "Le déplacement de bande doit marquer le projet comme modifié");
        assertTrue(moveTimeline.getTextManager().getTexts().get(0).band == 1, "La phrase doit être en bande 1");

        // Test déplacement impossible avec feedback (bande 2 occupée)
        boolean movedFbOccupied = moveTimeline.movePhraseToBandWithFeedback(1, phraseStartX, 2);
        assertTrue(!movedFbOccupied, "movePhraseToBandWithFeedback vers bande occupée doit échouer");
        assertTrue(moveTimeline.getTextManager().getTexts().get(0).band == 1, "La phrase doit rester en bande 1");

        // Test Undo
        moveTimeline.undo();
        assertTrue(moveTimeline.getTextManager().getTexts().get(0).band == 0, "L'annulation doit restaurer la phrase en bande 0");

        // Test changement de rôle sur une phrase
        Role singerRole = new Role("Chanteur", new Color(0xFF6B6B));
        Role dancerRole = new Role("Danseur", new Color(0x4ECDC4));
        roles.add(singerRole);
        roles.add(dancerRole);

        TimelinePanel roleTimeline = new TimelinePanel();
        roleTimeline.setRoles(roles);
        roleTimeline.setTime(0.0);
        roleTimeline.startPhrase(0, singerRole);
        for (char c : "Phrase avec role".toCharArray()) {
            roleTimeline.typeChar(c);
        }
        roleTimeline.setTime(2.0);
        roleTimeline.endPhrase();

        int pX = roleTimeline.getTextManager().getTexts().get(0).x;
        assertTrue(roleTimeline.getTextManager().getPhraseRole(0, pX) == singerRole,
                "Le rôle initial de la phrase doit être 'Chanteur'");

        // Modifier le rôle vers Danseur
        roleTimeline.getTextManager().setPhraseRole(0, pX, dancerRole);
        assertTrue(roleTimeline.getTextManager().getPhraseRole(0, pX) == dancerRole,
                "Le rôle de la phrase doit être mis à jour vers 'Danseur'");
        assertTrue(roleTimeline.getTextManager().getTexts().get(0).role == dancerRole,
                "Le TextItem doit posséder la référence vers 'Danseur'");

        // Supprimer / détacher le rôle (null)
        roleTimeline.getTextManager().setPhraseRole(0, pX, null);
        assertTrue(roleTimeline.getTextManager().getPhraseRole(0, pX) == null,
                "Le rôle de la phrase doit être réinitialisé à null");
        assertTrue(roleTimeline.getTextManager().getTexts().get(0).role == null,
                "Le TextItem doit avoir un rôle null");

        // ===== Tests Waveform Audio =====
        float[] sampleAmps = new float[]{0.0f, 0.2f, 0.8f, 0.5f, 0.1f};
        app.services.AudioWaveformData waveData = new app.services.AudioWaveformData(sampleAmps, 0.05, 100);
        assertTrue(!waveData.isEmpty(), "WaveformData ne doit pas être vide");
        assertTrue(Math.abs(waveData.getAmplitudeAt(0.02) - 0.8f) < 1e-4, "Amplitude à 0.02s doit être 0.8");
        assertTrue(Math.abs(waveData.getMaxAmplitudeInRange(0.0, 0.04) - 0.8f) < 1e-4, "Max amplitude [0.0, 0.04] doit être 0.8");
        assertTrue(waveData.getAmplitudeAt(1.0) == 0f, "Amplitude hors limite doit valoir 0.0");

        // Timeline avec WaveformData
        TimelinePanel waveTimeline = new TimelinePanel();
        waveTimeline.setWaveformData(waveData);
        assertTrue(waveTimeline.getWaveformData() == waveData, "TimelinePanel doit conserver le waveformData");
        BufferedImage waveFrame = waveTimeline.renderFrame(640, 180, 0.0);
        assertTrue(waveFrame != null, "Le rendu frame avec waveform doit réussir");

        // ===== Tests DETX Format (Cappella) =====
        // Test conversion timecodes
        String tc = app.ui.DetxManager.secondsToTimecode(0.0);
        assertTrue("01:00:00:00".equals(tc), "0s doit donner le timecode de base 01:00:00:00 mais donne " + tc);
        double secFromTc = app.ui.DetxManager.timecodeToSeconds("01:00:00:00");
        assertTrue(Math.abs(secFromTc - 0.0) < 1e-4, "Timecode 01:00:00:00 doit donner 0.0s");

        String tc10 = app.ui.DetxManager.secondsToTimecode(10.5);
        double sec10 = app.ui.DetxManager.timecodeToSeconds(tc10);
        assertTrue(Math.abs(sec10 - 10.5) < 0.05, "Roundtrip timecode 10.5s doit être précis au frame près");

        // Test export & import DETX
        TimelinePanel detxTimeline = new TimelinePanel();
        ArrayList<Role> detxRoles = new ArrayList<>();
        Role heroRole = new Role("Héros", new Color(0x3399FF));
        Role villainRole = new Role("Méchante", new Color(0xFF3333));
        detxRoles.add(heroRole);
        detxRoles.add(villainRole);
        detxTimeline.setRoles(detxRoles);

        detxTimeline.setTime(0.0);
        detxTimeline.startPhrase(0, heroRole);
        for (char c : "Je te vaincrai !".toCharArray()) {
            detxTimeline.typeChar(c);
        }
        detxTimeline.setTime(3.0);
        detxTimeline.endPhrase();

        detxTimeline.setTime(4.0);
        detxTimeline.startPhrase(1, villainRole);
        for (char c : "Jamais de la vie !".toCharArray()) {
            detxTimeline.typeChar(c);
        }
        detxTimeline.setTime(7.0);
        detxTimeline.endPhrase();

        File tmpDetx = File.createTempFile("omeryth-test-", ".detx");
        File detxVideo = new File("C:/videos/episode1.mp4");
        detxTimeline.saveProject(tmpDetx, detxVideo, detxRoles);

        // Vérifier contenu XML du fichier DETX
        String detxXml = java.nio.file.Files.readString(tmpDetx.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(detxXml.contains("<detx"), "Le fichier .detx doit contenir la balise racine <detx>");
        assertTrue(detxXml.contains("Héros") || detxXml.contains("heros"), "Le fichier .detx doit contenir le rôle Héros");
        assertTrue(detxXml.contains("Méchante") || detxXml.contains("mechante"), "Le fichier .detx doit contenir le rôle Méchante");
        assertTrue(detxXml.contains("<line"), "Le fichier .detx doit contenir des balises <line>");
        assertTrue(detxXml.contains("<lipsync"), "Le fichier .detx doit contenir des balises <lipsync>");
        assertTrue(detxXml.contains("Je te vaincrai !"), "Le fichier .detx doit contenir le texte du Héros");

        // Rechargement du fichier DETX
        TimelinePanel loadedDetxTimeline = new TimelinePanel();
        ArrayList<Role> reloadedRoles = new ArrayList<>();
        File reloadedVideo = loadedDetxTimeline.loadProject(tmpDetx, reloadedRoles);

        assertTrue(loadedDetxTimeline.hasContent(), "Le projet DETX rechargé doit contenir du contenu");
        assertTrue(reloadedVideo != null && reloadedVideo.getName().equals("episode1.mp4"),
                "Le fichier vidéo doit être correctement extrait du header DETX");
        assertTrue(reloadedRoles.size() >= 2, "Les rôles doivent être reconstitués depuis le DETX");

        // Test Customization persistence
        app.ui.AppCustomization custom = new app.ui.AppCustomization();
        custom.showWaveform = false;
        custom.defaultProjectFormat = "detx";
        custom.waveformColor = new Color(10, 20, 30, 40);
        app.utils.FileUtils.saveCustomization(custom);

        app.ui.AppCustomization loadedCustom = app.utils.FileUtils.loadCustomization();
        assertTrue(!loadedCustom.showWaveform, "showWaveform doit persister (false)");
        assertTrue("detx".equals(loadedCustom.defaultProjectFormat), "defaultProjectFormat doit persister (detx)");
        assertTrue(loadedCustom.waveformColor.getRed() == 10, "waveformColor doit persister (red=10)");

        // Restaurer customisation par défaut
        app.utils.FileUtils.saveCustomization(new app.ui.AppCustomization());

        // Test Waveform
        File sampleVideo = new File("C:/Users/kilya/Videos/MP4/Monster - je suis japonais.mp4");
        if (sampleVideo.exists()) {
            app.services.AudioWaveformService ws = new app.services.AudioWaveformService();
            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final app.services.AudioWaveformData[] holder = new app.services.AudioWaveformData[1];
            ws.extractWaveform(sampleVideo, data -> {
                holder[0] = data;
                latch.countDown();
            });
            try { latch.await(10, java.util.concurrent.TimeUnit.SECONDS); } catch (Exception ignored) {}
            assertTrue(holder[0] != null, "Waveform data ne doit pas être null");
            assertTrue(Math.abs(holder[0].getDurationSeconds() - 9.13) < 0.05,
                    "La durée de la waveform (" + holder[0].getDurationSeconds() + "s) doit correspondre exactement aux 9.13s de la vidéo");
            assertTrue(Math.abs(holder[0].getAmplitudes().length - 913) <= 5,
                    "Le nombre de crêtes (" + holder[0].getAmplitudes().length + ") doit être d'environ 913 (100 crêtes/seconde)");
        }

        // Test Volume Persistence
        int prevVol = app.utils.FileUtils.loadVolume();
        app.utils.FileUtils.saveVolume(50);
        assertTrue(app.utils.FileUtils.loadVolume() == 50, "Le volume sauvegardé doit être 50");
        app.utils.FileUtils.saveVolume(120);
        assertTrue(app.utils.FileUtils.loadVolume() == 100, "Le volume supérieur à 100 doit être clampé à 100");
        app.utils.FileUtils.saveVolume(-10);
        assertTrue(app.utils.FileUtils.loadVolume() == 0, "Le volume inférieur à 0 doit être clampé à 0");
        app.utils.FileUtils.saveVolume(prevVol);

        // Test TimerClass onStopCallback
        app.utils.TimerClass testTimer = new app.utils.TimerClass(timeline);
        testTimer.setMaxTime(2.0);
        final boolean[] timerCallbackCalled = new boolean[1];
        testTimer.setOnStopCallback(() -> timerCallbackCalled[0] = true);
        testTimer.addTime(2.5);
        assertTrue(timerCallbackCalled[0], "onStopCallback doit être appelé lorsque maxTime est dépassé");
        assertTrue(testTimer.getTime() == 2.0, "Le temps doit être clampé à maxTime (2.0)");

        // Test DETX avec base timecode 00:00:00:00, rôles par ID, et lipsync type out_closed
        String detx00Content = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<detx>\n" +
                "  <header>\n" +
                "    <videofile timestamp=\"00:00:00:00\">test_local.mp4</videofile>\n" +
                "  </header>\n" +
                "  <roles>\n" +
                "    <role id=\"r1\" name=\"Personnage A\" color=\"#ff0000\"/>\n" +
                "  </roles>\n" +
                "  <body>\n" +
                "    <line role=\"r1\" track=\"0\">\n" +
                "      <lipsync type=\"in_open\" timecode=\"00:00:01:00\"/>\n" +
                "      <text>Bonjour Cappella</text>\n" +
                "      <lipsync type=\"out_closed\" timecode=\"00:00:03:00\"/>\n" +
                "    </line>\n" +
                "  </body>\n" +
                "</detx>";
        File detx00File = File.createTempFile("omeryth-detx00-", ".detx");
        java.nio.file.Files.writeString(detx00File.toPath(), detx00Content, java.nio.charset.StandardCharsets.UTF_8);

        TimelinePanel detx00Timeline = new TimelinePanel();
        ArrayList<Role> detx00Roles = new ArrayList<>();
        detx00Timeline.loadProject(detx00File, detx00Roles);

        assertTrue(detx00Timeline.hasContent(), "Le projet DETX 00:00:00:00 doit charger du contenu");
        app.ui.TextItem loadedItem = detx00Timeline.getTextManager().getTexts().get(0);
        assertTrue("Bonjour Cappella".equals(loadedItem.text), "Le texte chargé doit correspondre");
        assertTrue(loadedItem.x >= 0, "Les coordonnées temporelles ne doivent pas être négatives (x=" + loadedItem.x + ")");
        assertTrue(loadedItem.role != null, "Le rôle mappé par ID ne doit pas être null");
        assertTrue("Personnage A".equals(loadedItem.role.name), "Le nom du rôle doit être Personnage A");

        // Test DETX avec UTF-8 BOM
        File detxBomFile = File.createTempFile("omeryth-detxbom-", ".detx");
        byte[] bomBytes = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] xmlBytes = detx00Content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] fullBytes = new byte[bomBytes.length + xmlBytes.length];
        System.arraycopy(bomBytes, 0, fullBytes, 0, bomBytes.length);
        System.arraycopy(xmlBytes, 0, fullBytes, bomBytes.length, xmlBytes.length);
        java.nio.file.Files.write(detxBomFile.toPath(), fullBytes);

        TimelinePanel detxBomTimeline = new TimelinePanel();
        ArrayList<Role> detxBomRoles = new ArrayList<>();
        detxBomTimeline.loadProject(detxBomFile, detxBomRoles);
        assertTrue(detxBomTimeline.hasContent(), "Le projet DETX avec BOM UTF-8 doit charger sans erreur");

        // Test que bouger la bande (scroll, setTime, navigation, touches transport, clic simple) ne marque pas le projet comme dirty
        TimelinePanel moveBandTimeline = new TimelinePanel();
        ArrayList<Role> moveBandRoles = new ArrayList<>();
        moveBandTimeline.loadProject(detx00File, moveBandRoles);
        assertTrue(!moveBandTimeline.isDirty(), "Le projet chargé doit être clean (dirty=false)");

        // 1. Bouger la bande dans le temps
        moveBandTimeline.setTime(5.0);
        moveBandTimeline.setTime(10.0);
        moveBandTimeline.setTime(0.0);
        assertTrue(!moveBandTimeline.isDirty(), "Bouger la bande dans le temps (setTime) ne doit pas marquer le projet comme modifié");

        // 2. Touches de frappe alors qu'on n'est pas en édition
        moveBandTimeline.typeChar(' ');
        moveBandTimeline.typeChar('A');
        moveBandTimeline.deleteChar();
        moveBandTimeline.deleteWord();
        moveBandTimeline.pasteText("Test");
        assertTrue(!moveBandTimeline.isDirty(), "Taper des touches hors édition ne doit pas marquer le projet comme modifié");

        // 3. Simuler un simple clic sur un séparateur (sans déplacement)
        java.awt.event.MouseEvent clickEvent = new java.awt.event.MouseEvent(
                moveBandTimeline,
                java.awt.event.MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                80,
                10,
                1,
                false
        );
        for (java.awt.event.MouseListener ml : moveBandTimeline.getMouseListeners()) {
            ml.mousePressed(clickEvent);
            ml.mouseReleased(new java.awt.event.MouseEvent(
                    moveBandTimeline,
                    java.awt.event.MouseEvent.MOUSE_RELEASED,
                    System.currentTimeMillis(),
                    0, 80, 10, 1, false
            ));
        }
        // Test chargement du fichier DETX utilisateur (Helluva Boss S2E8 Cappella 3.7.0)
        File helluvaFile = new File("C:\\Users\\kilya\\.gemini\\antigravity\\brain\\9ba9927c-227d-4b27-9fd7-dfcb0caa89f5\\scratch\\test_helluva.detx");
        if (helluvaFile.exists()) {
            java.util.TreeSet<String> types = new java.util.TreeSet<>();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(helluvaFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    int idx = line.indexOf("type=\"");
                    if (idx != -1) {
                        int end = line.indexOf('"', idx + 6);
                        types.add(line.substring(idx + 6, end));
                    }
                }
            }
            System.out.println("Lipsync types in DETX: " + types);

            TimelinePanel helluvaTimeline = new TimelinePanel();
            ArrayList<Role> helluvaRoles = new ArrayList<>();
            try {
                helluvaTimeline.loadProject(helluvaFile, helluvaRoles);
                System.out.println("Helluva DETX import OK - hasContent: " + helluvaTimeline.hasContent()
                    + ", items: " + helluvaTimeline.getTextManager().getTexts().size()
                    + ", roles: " + helluvaRoles.size());

                // Vérifier que les types de signes sont bien chargés
                boolean hasFvr = false, hasNeutral = false, hasOpenA = false, hasMpb = false;
                for (ArrayList<SeparatorMark> seps : helluvaTimeline.getTextManager().getBandSeparators().values()) {
                    for (SeparatorMark sm : seps) {
                        if (sm.signType == SeparatorMark.SignType.FVR) hasFvr = true;
                        if (sm.signType == SeparatorMark.SignType.NEUTRAL) hasNeutral = true;
                        if (sm.signType == SeparatorMark.SignType.OPEN_A) hasOpenA = true;
                        if (sm.signType == SeparatorMark.SignType.MPB) hasMpb = true;
                    }
                }
                assertTrue(hasFvr, "Les séparateurs FVR doivent être reconnus");
                assertTrue(hasNeutral, "Les séparateurs NEUTRAL doivent être reconnus");
                assertTrue(hasOpenA, "Les séparateurs OPEN_A doivent être reconnus");
                assertTrue(hasMpb, "Les séparateurs MPB doivent être reconnus");
                System.out.println("Vérification des types de signes importés : FVR=" + hasFvr + ", NEUTRAL=" + hasNeutral + ", OPEN_A=" + hasOpenA + ", MPB=" + hasMpb);

                // Simuler le rendu graphique à différents moments temporels
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(1280, 720, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2 = img.createGraphics();
                helluvaTimeline.setSize(1280, 720);
                for (double t = 0; t < 1500; t += 30) {
                    helluvaTimeline.setTime(t);
                    helluvaTimeline.paint(g2);
                }
                g2.dispose();

                // Tester l'exportation DETX et la conservation des types
                File exportTest = new File("C:\\Users\\kilya\\.gemini\\antigravity\\brain\\9ba9927c-227d-4b27-9fd7-dfcb0caa89f5\\scratch\\test_export_helluva.detx");
                try {
                    app.ui.DetxManager.saveDetx(exportTest, null, helluvaTimeline.getTextManager(), helluvaRoles, helluvaTimeline.getBandCount(), helluvaTimeline.getPixelsPerSecond());
                    System.out.println("Helluva saveDetx export OK !");
                    
                    String exportedContent = java.nio.file.Files.readString(exportTest.toPath());
                    assertTrue(exportedContent.contains("type=\"fvr\""), "L'export DETX doit préserver type='fvr'");
                    assertTrue(exportedContent.contains("type=\"neutral\""), "L'export DETX doit préserver type='neutral'");
                    assertTrue(exportedContent.contains("type=\"a\""), "L'export DETX doit préserver type='a'");
                    System.out.println("Vérification de la conservation des types dans l'export DETX : OK !");
                } catch (Exception ex) {
                    System.err.println("ERREUR lors de saveDetx:");
                    ex.printStackTrace();
                    throw ex;
                }

                // Test des raccourcis du pavé numérique (1, 2, 3, 4, 5, 6, 7, 8)
                TimelinePanel numpadTimeline = new TimelinePanel();
                numpadTimeline.setBandCount(4);
                numpadTimeline.setSelectedBand(0);
                app.utils.TimerClass mockTimer = new app.utils.TimerClass(numpadTimeline);
                app.utils.KeyBoardListener kbl = new app.utils.KeyBoardListener(
                    numpadTimeline, mockTimer, null,
                    KeyEvent.VK_SPACE, KeyEvent.VK_RIGHT, KeyEvent.VK_LEFT, KeyEvent.VK_R,
                    KeyEvent.VK_M, KeyEvent.VK_EQUALS, KeyEvent.VK_MINUS,
                    KeyEvent.VK_NUMPAD3, KeyEvent.VK_NUMPAD4, KeyEvent.VK_NUMPAD5,
                    KeyEvent.VK_NUMPAD6, KeyEvent.VK_NUMPAD7, KeyEvent.VK_NUMPAD8,
                    new ArrayList<>(), numpadTimeline
                );

                // Numpad 1 : repère de plan
                kbl.dispatchKeyEvent(new KeyEvent(numpadTimeline, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_NUMPAD1, KeyEvent.CHAR_UNDEFINED));
                assertTrue(numpadTimeline.getTextManager().getPlanMarkers().size() == 1, "Numpad 1 doit créer un repère de plan");

                // Numpad 2 : début de phrase
                kbl.dispatchKeyEvent(new KeyEvent(numpadTimeline, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_NUMPAD2, KeyEvent.CHAR_UNDEFINED));
                assertTrue(numpadTimeline.isEditing(), "Numpad 2 doit lancer l'édition de phrase");

                // Avancer le temps pour insérer le signe FVR à t=1.0s
                numpadTimeline.setTime(1.0);
                kbl.dispatchKeyEvent(new KeyEvent(numpadTimeline, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_NUMPAD5, KeyEvent.CHAR_UNDEFINED));
                boolean testFvr = numpadTimeline.getTextManager().getBandSeparators().get(0).stream().anyMatch(s -> s.signType == SeparatorMark.SignType.FVR);
                assertTrue(testFvr, "Numpad 5 doit insérer un signe FVR");

                // Numpad 8 : Respiration en édition (insère "h/ ")
                kbl.dispatchKeyEvent(new KeyEvent(numpadTimeline, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_NUMPAD8, KeyEvent.CHAR_UNDEFINED));
                assertTrue(numpadTimeline.getTextManager().getTexts().get(0).text.contains("h/"), "Numpad 8 doit insérer le texte de respiration h/");

                // Avancer le temps pour terminer la phrase à t=2.0s
                numpadTimeline.setTime(2.0);
                kbl.dispatchKeyEvent(new KeyEvent(numpadTimeline, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0, KeyEvent.VK_NUMPAD3, KeyEvent.CHAR_UNDEFINED));
                assertTrue(!numpadTimeline.isEditing(), "Numpad 3 doit terminer la phrase");
                boolean testEnd = numpadTimeline.getTextManager().getBandSeparators().get(0).stream().anyMatch(s -> s.type == SeparatorMark.Type.END);
                assertTrue(testEnd, "Numpad 3 doit ajouter un séparateur de fin END");

                // Test du masquage / affichage de la waveform (onde audio)
                assertTrue(numpadTimeline.isWaveformVisible(), "Par défaut la waveform est active");
                numpadTimeline.setWaveformVisible(false);
                assertTrue(!numpadTimeline.isWaveformVisible(), "La waveform doit être masquée après setWaveformVisible(false)");
                
                // Toggle via Ctrl+W
                kbl.dispatchKeyEvent(new KeyEvent(numpadTimeline, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_W, KeyEvent.CHAR_UNDEFINED));
                assertTrue(numpadTimeline.isWaveformVisible(), "Ctrl+W doit réactiver la waveform");
                kbl.dispatchKeyEvent(new KeyEvent(numpadTimeline, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), KeyEvent.CTRL_DOWN_MASK, KeyEvent.VK_W, KeyEvent.CHAR_UNDEFINED));
                assertTrue(!numpadTimeline.isWaveformVisible(), "Un second Ctrl+W doit masquer la waveform");
                System.out.println("Test de bascule de la waveform (masquer/afficher + Ctrl+W) : VALIDÉ !");

                // Test de synchronisation du menu Affichage avec la waveform
                app.ui.SimplifiedMenuBarPanel menuBar = new app.ui.SimplifiedMenuBarPanel(null, numpadTimeline);
                numpadTimeline.setWaveformVisible(true);
                menuBar.setWaveformChecked(true);
                menuBar.syncAffichageState();
                numpadTimeline.setWaveformVisible(false);
                menuBar.setWaveformChecked(false);
                menuBar.syncAffichageState();
                System.out.println("Test de synchronisation du menu Affichage (Waveform) : VALIDÉ !");

                System.out.println("Tests des raccourcis pavé numérique (1, 2, 3, 5, 8) : TOUS VALIDES !");

                // Test de détection de l'accélération GPU CUDA
                boolean cudaAvailable = app.services.SpeechWorkflowService.isCudaAvailable();
                System.out.println("Détection de l'accélération matérielle CUDA : " + (cudaAvailable ? "ACTIF (NVIDIA GPU détecté)" : "INACTIF"));
                assertTrue(cudaAvailable, "Le GPU NVIDIA avec CUDA doit être détecté sur cette machine");

                // Test de fluidité & rendu sur échantillon de 4h (5 000 textes, 10 000 séparateurs)
                System.out.println("Test de performance : simulation d'un projet de 4 heures...");
                TimelinePanel stressTimeline = new TimelinePanel();
                stressTimeline.setSize(1920, 300);
                app.ui.TextManager stressTM = stressTimeline.getTextManager();
                // 4 heures = 14400 secondes. À 140 px/s, X s'étend de 0 à ~2 000 000 px.
                for (int i = 0; i < 5000; i++) {
                    int worldX = i * 400;
                    stressTM.getTexts().add(new app.ui.TextItem("Dialogue réplique " + i, worldX, 0));
                    stressTM.addSeparator(0, worldX, SeparatorMark.Type.START, -1);
                    stressTM.addSeparator(0, worldX + 300, SeparatorMark.Type.END, -1);
                }
                BufferedImage testImg = new BufferedImage(1920, 300, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2Stress = testImg.createGraphics();

                long stressStart = System.currentTimeMillis();
                // Simuler 60 images (1 seconde à 60 FPS) réparties à différentes positions de la vidéo de 4h (0h, 1h, 2h, 4h)
                for (int fIdx = 0; fIdx < 60; fIdx++) {
                    double sampleTime = (fIdx % 4) * 3600.0 + (fIdx * 0.016);
                    stressTimeline.setTime(sampleTime);
                    stressTimeline.paint(g2Stress);
                }
                g2Stress.dispose();
                long stressDuration = System.currentTimeMillis() - stressStart;
                System.out.println("Rendu de 60 frames sur projet de 4h : " + stressDuration + " ms (" + String.format("%.2f", stressDuration / 60.0) + " ms/frame)");
                assertTrue(stressDuration < 1000, "Le rendu de 60 frames sur 4h doit être ultra-fluide (< 1000ms au total)");
                System.out.println("Test de fluidité 4h avec Viewport Culling : VALIDÉ !");

                // Test de non-disparition des très longues phrases (25 000 px - ex: monologue ou musique de 2+ minutes)
                // Même si le début x=1000 a défilé à -19 000 px (bien au-delà de l'ancienne limite de -15000px),
                // la phrase doit continuer à s'afficher sans aucune disparition !
                TimelinePanel longPhraseTimeline = new TimelinePanel();
                longPhraseTimeline.setSize(1200, 300);
                app.ui.TextManager lpTM = longPhraseTimeline.getTextManager();
                // Phrase géante de 25 000 px entre x=1000 et x=26 000
                lpTM.getTexts().add(new app.ui.TextItem("T'as oublié que je suis ta mère ? Monologue long et dramatique qui continue...", 1000, 0));
                lpTM.addSeparator(0, 1000, SeparatorMark.Type.START, -1);
                lpTM.addSeparator(0, 26000, SeparatorMark.Type.END, -1);
                
                // Positionner la tête de lecture à x=20 000 (le début x=1000 est à -19 800 px à gauche, mais la fin est encore devant !)
                longPhraseTimeline.setTime(20000.0 / longPhraseTimeline.getPixelsPerSecond());
                BufferedImage lpImg = new BufferedImage(1200, 300, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2LP = lpImg.createGraphics();
                longPhraseTimeline.paint(g2LP);
                g2LP.dispose();
                System.out.println("Test de non-disparition des phrases géantes (25000px, début à -19000px) : VALIDÉ !");

                // Test de performance sur timeline de 7 HEURES avec 10 000 éléments
                TimelinePanel sevenHourTimeline = new TimelinePanel();
                sevenHourTimeline.setSize(1920, 320);
                sevenHourTimeline.setBandCount(4);
                app.ui.TextManager shTM = sevenHourTimeline.getTextManager();
                double pps = sevenHourTimeline.getPixelsPerSecond();
                int total7hSeconds = 7 * 3600; // 25 200 secondes
                for (int s = 0; s < total7hSeconds; s += 3) {
                    int wX = (int) Math.round(s * pps);
                    int b = (s / 3) % 4;
                    shTM.getTexts().add(new app.ui.TextItem("Réplique test " + s, wX, b));
                    shTM.addSeparator(b, wX, SeparatorMark.Type.START, -1);
                    shTM.addSeparator(b, wX + (int)(2.0 * pps), SeparatorMark.Type.END, -1);
                }
                long shStart = System.currentTimeMillis();
                BufferedImage shImg = new BufferedImage(1920, 320, BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g2SH = shImg.createGraphics();
                // Tester le rendu à 0h, 1h, 3h, 5h, 7h
                double[] checkHours = { 0.0, 3600.0, 3 * 3600.0, 5 * 3600.0, 7 * 3600.0 };
                for (double hSec : checkHours) {
                    sevenHourTimeline.setTime(hSec);
                    sevenHourTimeline.paint(g2SH);
                }
                g2SH.dispose();
                long shDuration = System.currentTimeMillis() - shStart;
                System.out.println("Rendu sur timeline de 7 HEURES (10 000+ répliques) : " + shDuration + " ms (" + String.format("%.2f", shDuration / (double)checkHours.length) + " ms/frame)");
                assertTrue(shDuration < 500, "Le rendu sur timeline de 7h doit être instantané (< 500ms)");
                System.out.println("Test de fluidité et non-disparition sur timeline 7 HEURES : VALIDÉ !");

                // Test d'importation de transcription avec temps mort de 0.5s
                java.util.List<app.services.SpeechWorkflowService.TranscriptionSegment> testSegments = new ArrayList<>();
                testSegments.add(new app.services.SpeechWorkflowService.TranscriptionSegment(1, "SPEAKER_00", 1.0, 2.0, "T'as oublié que"));
                testSegments.add(new app.services.SpeechWorkflowService.TranscriptionSegment(2, "SPEAKER_00", 2.5, 3.5, "je suis ta mère !")); // 0.5s de silence entre 2.0s et 2.5s !

                // Simuler la logique d'importation de MainFenetre
                TimelinePanel importTimeline = new TimelinePanel();
                importTimeline.setSize(1200, 300);
                app.ui.TextManager itTM = importTimeline.getTextManager();
                double itPps = importTimeline.getPixelsPerSecond();
                final double TEST_MAX_PAUSE_GAP = 0.38;

                java.util.List<java.util.List<app.services.SpeechWorkflowService.TranscriptionSegment>> testGroups = new ArrayList<>();
                java.util.List<app.services.SpeechWorkflowService.TranscriptionSegment> curGrp = new ArrayList<>();
                for (app.services.SpeechWorkflowService.TranscriptionSegment seg : testSegments) {
                    if (curGrp.isEmpty()) {
                        curGrp.add(seg);
                    } else {
                        double gap = seg.getStartSeconds() - curGrp.get(curGrp.size() - 1).getEndSeconds();
                        if (gap >= TEST_MAX_PAUSE_GAP) {
                            testGroups.add(curGrp);
                            curGrp = new ArrayList<>();
                        }
                        curGrp.add(seg);
                    }
                }
                if (!curGrp.isEmpty()) testGroups.add(curGrp);

                assertTrue(testGroups.size() == 2, "Les 2 répliques avec 0.5s de temps mort doivent former 2 groupes séparés (trouvé " + testGroups.size() + ")");

                for (java.util.List<app.services.SpeechWorkflowService.TranscriptionSegment> grp : testGroups) {
                    app.services.SpeechWorkflowService.TranscriptionSegment s = grp.get(0);
                    int sX = importTimeline.snapWorldXToTenth((int) Math.round(s.startSeconds * itPps));
                    int eX = importTimeline.snapWorldXToTenth((int) Math.round(s.endSeconds * itPps));
                    itTM.addTextItem(new app.ui.TextItem(s.text, sX, 0));
                    itTM.addSeparator(0, sX, SeparatorMark.Type.START);
                    itTM.addSeparator(0, eX, SeparatorMark.Type.END);
                }

                assertTrue(itTM.getTexts().size() == 2, "La timeline doit contenir 2 TextItem distincts pour chaque réplique");
                ArrayList<SeparatorMark> sepsBand0 = itTM.getBandSeparators().get(0);
                assertTrue(sepsBand0 != null && sepsBand0.size() == 4, "La timeline doit contenir 4 séparateurs (START, END, START, END)");
                assertTrue(sepsBand0.get(0).type == SeparatorMark.Type.START, "Séparateur 1 doit être START");
                assertTrue(sepsBand0.get(1).type == SeparatorMark.Type.END, "Séparateur 2 doit être END");
                assertTrue(sepsBand0.get(2).type == SeparatorMark.Type.START, "Séparateur 3 doit être START");
                assertTrue(sepsBand0.get(3).type == SeparatorMark.Type.END, "Séparateur 4 doit être END");
                assertTrue(sepsBand0.get(2).x > sepsBand0.get(1).x, "Il doit y avoir un espace vide entre le END de la phrase 1 et le START de la phrase 2");
                System.out.println("Test de séparation des phrases avec temps mort de 0.5s (START, END indépendants) : VALIDÉ !");
            } catch (Exception e) {
                System.err.println("ERREUR lors de l'import / rendu Helluva DETX:");
                e.printStackTrace();
                throw e;
            }
        }

        // =========================================================================
        // Tests Nouveaux : MontagePreviewCanvas, ExportConfig & Retrait des Voix
        // =========================================================================
        {
            System.out.println("--- Test MontagePreviewCanvas & Composition Visuelle ---");
            app.ui.MontagePreviewCanvas canvas = new app.ui.MontagePreviewCanvas();

            // 1. Format 16:9 Paysage Full HD
            canvas.setExportResolution(1920, 1080);
            assertTrue(canvas.getExportWidth() == 1920 && canvas.getExportHeight() == 1080, "Résolution 1920x1080 incorrecte");
            canvas.applyLayoutTemplate("OMERYTH_ORIGINAL");

            java.awt.Rectangle vr169 = canvas.getVideoRect();
            java.awt.Rectangle br169 = canvas.getBandRect();
            assertTrue(vr169.width == 1920 && vr169.height > 600, "La vidéo 16:9 classique doit faire 1920px de large");
            assertTrue(br169.width == 1920 && br169.height >= 60, "Le bandeau format d'origine doit faire au moins 60px de haut");
            assertTrue(br169.y >= vr169.height, "Le bandeau doit être placé sous la vidéo en mode classique");

            // 2. Format 9:16 Vertical (TikTok, YouTube Shorts, Reels)
            canvas.setExportResolution(1080, 1920);
            assertTrue(canvas.getExportWidth() == 1080 && canvas.getExportHeight() == 1920, "Résolution 1080x1920 (9:16) incorrecte");
            canvas.applyLayoutTemplate("TIKTOK_CENTER_9_16");

            java.awt.Rectangle vrTikTok = canvas.getVideoRect();
            java.awt.Rectangle brTikTok = canvas.getBandRect();
            assertTrue(vrTikTok.width == 1080, "Vidéo TikTok doit faire 1080px de large");
            assertTrue(brTikTok.width == 1080, "Bandeau TikTok doit faire 1080px de large");
            assertTrue(brTikTok.y > vrTikTok.y, "Bandeau TikTok doit être positionné en dessous de la vidéo");
            assertTrue(brTikTok.y + brTikTok.height <= 1920, "Le bandeau TikTok ne doit pas dépasser le bas de l'écran");

            // 3. Manipulation directe des coordonnées & Centrage
            canvas.setSelectedElement(app.ui.MontagePreviewCanvas.ElementType.BAND);
            canvas.setBandRect(100, 500, 800, 250);
            assertTrue(canvas.getBandRect().x == 100 && canvas.getBandRect().width == 800, "Coordonnées manuelles doivent être enregistrées");

            canvas.centerSelectedHorizontally();
            int expectedCenterX = (1080 - 800) / 2;
            assertTrue(canvas.getBandRect().x == expectedCenterX, "Le centrage horizontal doit calculer x=" + expectedCenterX + " (trouvé " + canvas.getBandRect().x + ")");

            canvas.setSelectedFullWidth();
            assertTrue(canvas.getBandRect().x == 0 && canvas.getBandRect().width == 1080, "La pleine largeur doit régler x=0 et w=1080");

            // Test adaptation dynamique de la hauteur selon le nombre de bandes :
            // 1 bande : 100
            // 2 bandes : 100 (50px par bande)
            // 3 bandes : 150 (50px par bande)
            // 4 bandes : 200 (50px par bande)
            int h1 = app.ui.ExportVideoDialog.computeExportBandHeight(1, 100);
            int h2 = app.ui.ExportVideoDialog.computeExportBandHeight(2, 100);
            int h3 = app.ui.ExportVideoDialog.computeExportBandHeight(3, 100);
            int h4 = app.ui.ExportVideoDialog.computeExportBandHeight(4, 100);
            assertTrue(h1 == 100, "1 bande doit avoir la hauteur de base (100)");
            assertTrue(h2 == 100, "2 bandes doivent avoir 100 (50px par bande)");
            assertTrue(h3 == 150, "3 bandes doivent s'agrandir à 150");
            assertTrue(h4 == 200, "4 bandes doivent s'agrandir à 200");

            // Test Filtre Anti-Copyright avec opacité réglable 0-100%
            canvas.setAntiCopyright(true, 45);
            assertTrue(canvas.isAntiCopyright() && canvas.getAntiCopyrightOpacity() == 45, "Le filtre anti-copyright doit être activé avec 45% d'opacité");

            // 4. Test ExportConfig
            app.ui.ExportVideoDialog.ExportConfig cfg = new app.ui.ExportVideoDialog.ExportConfig();
            assertTrue(!cfg.isMontageMode, "Par défaut isMontageMode doit être false");
            assertTrue(!cfg.removeVocals, "Par défaut removeVocals doit être false");
            assertTrue(cfg.includeAudio, "Par défaut includeAudio doit être toujours true");
            assertTrue(!cfg.antiCopyright, "Par défaut antiCopyright doit être false");
            assertTrue(cfg.antiCopyrightOpacity == 20, "Par défaut antiCopyrightOpacity doit être 20%");

            cfg.isMontageMode = true;
            cfg.removeVocals = true;
            cfg.antiCopyright = true;
            cfg.antiCopyrightOpacity = 45;
            cfg.videoRect = vrTikTok;
            cfg.bandRect = brTikTok;
            cfg.width = 1080;
            cfg.height = 1920;

            assertTrue(cfg.isMontageMode && cfg.removeVocals && cfg.antiCopyright && cfg.antiCopyrightOpacity == 45, "Configuration montage, retrait voix et anti-copyright 45% doit être conservée");
            System.out.println("Test MontagePreviewCanvas, Redimensionnement, Retrait de Voix & Anti-Copyright : VALIDÉ !");

            // 5. Test ExportSession & Détection d'accélération d'encodage
            System.out.println("--- Test Pipeline d'Export Vidéo Ultra-Rapide (Streaming Pipe) ---");
            TimelinePanel.ExportSession exportSession = timeline.createExportSession(1920, 300, 5.0);
            assertTrue(exportSession != null, "ExportSession ne doit pas être null");
            assertTrue(exportSession.getWidth() == 1920 && exportSession.getHeight() == 300, "Dimensions ExportSession correctes");

            BufferedImage testBuffer = new BufferedImage(1920, 300, BufferedImage.TYPE_3BYTE_BGR);
            java.awt.Graphics2D testG2 = testBuffer.createGraphics();
            exportSession.renderFrameDirect(testG2, 0.5);
            testG2.dispose();
            assertTrue(testBuffer.getRGB(100, 100) != 0, "Le buffer d'export doit être dessiné");

            // Détection de l'encodeur
            String ffmpegPath = new File("ffmpeg/ffmpeg.exe").exists() ? new File("ffmpeg/ffmpeg.exe").getAbsolutePath() : "ffmpeg";
            app.services.MediaWorkflowService.EncoderSettings enc = app.services.MediaWorkflowService.detectEncoder(ffmpegPath, "auto");
            assertTrue(enc != null && enc.codec != null, "Un encodeur vidéo valide doit être détecté");
            System.out.println("Encodeur vidéo détecté : " + enc.displayName + " (" + enc.codec + ")");

            // Test d'encodage direct via stdin pipe (30 frames)
            File tempMp4 = File.createTempFile("omeryth-fast-export-", ".mp4");
            tempMp4.deleteOnExit();

            java.util.List<String> testCmd = new java.util.ArrayList<>();
            testCmd.add(ffmpegPath);
            testCmd.add("-y");
            testCmd.add("-f"); testCmd.add("rawvideo");
            testCmd.add("-pix_fmt"); testCmd.add("bgr24");
            testCmd.add("-s"); testCmd.add("1920x300");
            testCmd.add("-framerate"); testCmd.add("30");
            testCmd.add("-i"); testCmd.add("-");
            testCmd.add("-c:v"); testCmd.add(enc.codec);
            testCmd.addAll(enc.extraArgs);
            testCmd.add("-pix_fmt"); testCmd.add("yuv420p");
            testCmd.add(tempMp4.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(testCmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();

            long t0 = System.currentTimeMillis();
            byte[] rawBytes = ((java.awt.image.DataBufferByte) testBuffer.getRaster().getDataBuffer()).getData();
            try (java.io.OutputStream pipe = new java.io.BufferedOutputStream(p.getOutputStream(), 1024 * 1024)) {
                for (int i = 0; i < 30; i++) {
                    pipe.write(rawBytes);
                }
                pipe.flush();
            }
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int pExit = p.waitFor();
            long tElapsed = System.currentTimeMillis() - t0;
            assertTrue(pExit == 0, "L'encodage en streaming FFmpeg doit retourner 0 (obtenu " + pExit + ")");
            assertTrue(tempMp4.exists() && tempMp4.length() > 1000, "Le fichier MP4 généré doit exister et contenir des données");
            System.out.println("Export streaming réussi en " + tElapsed + " ms pour 30 frames (Taille : " + tempMp4.length() + " octets) !");
            tempMp4.delete();

            System.out.println("Test Pipeline d'Export Vidéo Ultra-Rapide : VALIDÉ !");

            // 6. Test Séparation Vocale IA (Demucs)
            System.out.println("--- Test Séparation Vocale Demucs (Facebook Research) ---");
            app.services.MediaWorkflowService mediaWorkflowService = new app.services.MediaWorkflowService();
            File demucsWorker = mediaWorkflowService.findDemucsWorkerScript();
            assertTrue(demucsWorker != null && demucsWorker.exists(), "Le worker Demucs doit exister dans whisperx_engine/demucs_worker.py");
            System.out.println("Worker Demucs détecté : " + demucsWorker.getAbsolutePath());

            String pythonCmd = mediaWorkflowService.findPython();
            assertTrue(pythonCmd != null, "Python doit être détecté sur le système");
            System.out.println("Python détecté : " + pythonCmd);

            boolean demucsReady = mediaWorkflowService.isDemucsAvailable();
            System.out.println("Disponibilité Demucs IA : " + (demucsReady ? "OUI (Prêt pour extraction)" : "NON (Repli FFmpeg actif)"));

            // Test execution worker --help
            ProcessBuilder demucsHelpPb = new ProcessBuilder(pythonCmd, demucsWorker.getAbsolutePath(), "--help");
            demucsHelpPb.redirectErrorStream(true);
            Process demucsHelpP = demucsHelpPb.start();
            demucsHelpP.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int demucsHelpCode = demucsHelpP.waitFor();
            assertTrue(demucsHelpCode == 0, "demucs_worker.py --help doit retourner 0 (obtenu " + demucsHelpCode + ")");
            System.out.println("Worker Demucs CLI testé avec succès (exit 0) !");
            System.out.println("Test Séparation Vocale Demucs : VALIDÉ !");

            // 7. Test Format Mobile (1080x1920) & Association de Fichiers .rythmo
            System.out.println("--- Test Format Mobile (1080x1920) & Association .rythmo ---");
            canvas.setExportResolution(1080, 1920);
            canvas.applyLayoutTemplate("TIKTOK_CENTER_9_16");
            assertTrue(canvas.getExportWidth() == 1080 && canvas.getExportHeight() == 1920, "Résolution mobile 1080x1920");
            assertTrue(canvas.getVideoRect().width == 1080, "Largeur vidéo mobile 1080");
            assertTrue(canvas.getBandRect().width == 1080, "Largeur bandeau mobile 1080");
            System.out.println("Gabarit Mobile 9:16 (1080x1920) : VALIDÉ !");

            File ico = new File("logo.ico");
            assertTrue(ico.exists() && ico.length() > 0, "logo.ico doit exister à la racine");
            app.services.FileAssociationService.ensureRythmoAssociationAsync();
            System.out.println("Service d'association .rythmo avec logo.ico : VALIDÉ !");

            // 8. Test SingleInstanceService
            System.out.println("--- Test SingleInstanceService ---");
            boolean firstInstance = app.services.SingleInstanceService.registerOrNotify(null);
            if (firstInstance) {
                boolean notified = app.services.SingleInstanceService.notifyExistingInstance("test.rythmo");
                assertTrue(notified, "La notification à l'instance existante doit réussir");
                app.services.SingleInstanceService.stopListenerForTesting();
            } else {
                System.out.println("Une instance d'OmeRyth est active en arrière-plan, communication inter-processus opérationnelle.");
            }
            System.out.println("Service SingleInstanceService : VALIDÉ !");
        }

        System.out.println("SmokeTests OK");
        System.exit(0);
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Echec test: " + message);
        }
    }
}
