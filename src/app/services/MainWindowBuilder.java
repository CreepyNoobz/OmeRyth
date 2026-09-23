package app.services;

import app.MainFenetre;
import app.ui.ImageBackgroundPanel;
import app.ui.TimelinePanel;
import app.ui.VolumePanel;
import app.ui.AppCustomization;
import app.ui.Role;
import app.services.ActionHistoryService;
import app.services.AutosaveService;
import app.services.DropImportService;
import app.services.MediaWorkflowService;
import app.services.ProjectWorkflowService;
import app.services.MainWindowEventBinder;
import app.utils.TimerClass;

import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;
import uk.co.caprica.vlcj.player.component.MediaPlayerSpecs;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

/**
 * Constructeur d'interface graphique assurant l'assemblage modulaire de la fenêtre principale {@link MainFenetre}.
 * <p>
 * Responsabilités architecturales :
 * <ul>
 *   <li><b>Découplage UI / Contrôleur :</b> Extrait l'instanciation verbeuse des composants Swing hors de {@link MainFenetre}.</li>
 *   <li><b>Zone Supérieure (Média & Contrôles) :</b>
 *     <ul>
 *       <li>À gauche : panneau du chronomètre haute précision, potentiomètre de volume et historique textuel des répliques.</li>
 *       <li>Au centre/droite : lecteur vidéo haute fidélité VLCJ intégré sur fond personnalisable avec bascule d'état (CardLayout).</li>
 *     </ul>
 *   </li>
 *   <li><b>Zone Inférieure :</b> Bande rythmo défilante interactive ({@link TimelinePanel}).</li>
 *   <li><b>Synchronisation média/horloge :</b> Liaison des écouteurs de fin de vidéo VLCJ et de pause de l'horloge.</li>
 * </ul>
 * </p>
 */
public class MainWindowBuilder {

    /**
     * Conteneur d'agrégation regroupant l'ensemble des sous-composants Swing et services instanciés.
     */
    public static class Parts {
        public JPanel mainPanel;
        public TimelinePanel timelinePanel;
        public TimerClass timer;
        public VolumePanel volumePanel;
        public ImageBackgroundPanel mediaPanel;
        public JPanel timerPanel;
        public ImageBackgroundPanel mediaEmptyPanel;
        public CardLayout mediaCardLayout;
        public JPanel mediaContentPanel;
        public EmbeddedMediaPlayerComponent mediaPlayerComponent;
        public MediaPlayerFactory mediaPlayerFactory;
        public ActionHistoryService actionHistoryService;
        public AutosaveService autosaveService;
        public DropImportService dropImportService;
        public MediaWorkflowService mediaWorkflowService;
        public ProjectWorkflowService projectWorkflowService;
        public MainWindowEventBinder windowEventBinder;
    }

    public static Parts build(MainFenetre window,
                              AppCustomization customization,
                              ArrayList<Role> roles,
                              File autosaveFile,
                              int actionHistoryMaxLines,
                              int autosaveIntervalMs) {

        Parts p = new Parts();

        // Timeline + services
        p.timelinePanel = new TimelinePanel();
        p.timelinePanel.setRoles(roles);
        p.actionHistoryService = new ActionHistoryService(actionHistoryMaxLines, 250);
        p.actionHistoryService.bind(p.timelinePanel);
        p.autosaveService = new AutosaveService(autosaveFile, autosaveIntervalMs, window::autosaveProject);
        p.dropImportService = new DropImportService(window::openDroppedProject, window::openDroppedMedia, window::showDropWarning);
        p.mediaWorkflowService = new MediaWorkflowService();
        p.projectWorkflowService = new ProjectWorkflowService();
        p.windowEventBinder = new MainWindowEventBinder();

        // Timer
        p.timer = new TimerClass(p.timelinePanel);

        // Main panel assembly
        p.mainPanel = new JPanel(new BorderLayout());

        // Top panel / media
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBackground(new Color(245, 245, 220));

        p.mediaPanel = new ImageBackgroundPanel(new BorderLayout());
        p.mediaPanel.setBackgroundStyle(customization.mediaBackground, customization.mediaImagePath);

        p.mediaCardLayout = new CardLayout();
        p.mediaContentPanel = new JPanel(p.mediaCardLayout);
        p.mediaContentPanel.setOpaque(false); // Make it transparent

        p.mediaEmptyPanel = new ImageBackgroundPanel(new BorderLayout());
        p.mediaEmptyPanel.setBackgroundStyle(customization.mediaBackground, customization.mediaImagePath);

        JPanel mediaPlayerHost = new JPanel(new BorderLayout());
        mediaPlayerHost.setOpaque(false); // Make it transparent
        java.util.ArrayList<String> vlcArgs = new java.util.ArrayList<>();
        vlcArgs.add("--quiet");
        vlcArgs.add("--verbose=-1");
        vlcArgs.add("--no-plugins-cache");
        vlcArgs.add("--no-media-library");
        // Note: --plugin-path and --reset-plugins-cache are not valid in newer VLC versions
        // so we intentionally omit them to avoid warnings
        p.mediaPlayerFactory = new MediaPlayerFactory(vlcArgs.toArray(new String[0]));
        p.mediaPlayerComponent = MediaPlayerSpecs.embeddedMediaPlayerSpec()
                .withFactory(p.mediaPlayerFactory)
                .embeddedMediaPlayer();
        mediaPlayerHost.add(p.mediaPlayerComponent, BorderLayout.CENTER);

        p.mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(new uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter() {
            @Override
            public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    if (p.timer != null) {
                        if (p.timer.isRunning()) {
                            p.timer.toggle();
                        }
                        p.timer.setTime(p.timer.getMaxTime());
                    }
                });
            }

            @Override
            public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        mediaPlayer.audio().setVolume(window.getVolume());
                    } catch (Throwable ignored) {}
                });
            }
        });

        p.timer.setOnStopCallback(() -> {
            if (p.mediaPlayerComponent != null && p.mediaPlayerComponent.mediaPlayer() != null) {
                try {
                    var mp = p.mediaPlayerComponent.mediaPlayer();
                    if (mp.status().isPlaying()) {
                        mp.controls().pause();
                    }
                    mp.controls().setTime((long) (p.timer.getTime() * 1000));
                } catch (Throwable ignored) {}
            }
        });

        p.mediaContentPanel.add(p.mediaEmptyPanel, "EMPTY");
        p.mediaContentPanel.add(mediaPlayerHost, "PLAYER");
        p.mediaCardLayout.show(p.mediaContentPanel, "EMPTY");
        p.mediaPanel.add(p.mediaContentPanel, BorderLayout.CENTER);

        topPanel.add(p.mediaPanel, BorderLayout.CENTER);

        // Timer panel on left
        p.timerPanel = new JPanel(new BorderLayout());
        p.timerPanel.setOpaque(false);
        p.timerPanel.setPreferredSize(new Dimension(customization.timerPanelWidth, 220));
        p.timerPanel.setMinimumSize(new Dimension(customization.timerPanelWidth, 220));

        p.volumePanel = new VolumePanel(window, customization);
        p.volumePanel.setVolume(window.getVolume());
        p.volumePanel.setVisible(true);

        JPanel timerContainer = new JPanel(new BorderLayout());
        timerContainer.setOpaque(false);
        timerContainer.add(p.volumePanel, BorderLayout.NORTH);
        timerContainer.add(p.timer, BorderLayout.SOUTH);

        p.timerPanel.add(p.actionHistoryService.createPanel(), BorderLayout.CENTER);
        p.timerPanel.add(timerContainer, BorderLayout.SOUTH);

        JPanel verticalSeparator = new JPanel();
        verticalSeparator.setPreferredSize(new Dimension(5, 1));
        verticalSeparator.setBackground(new Color(35, 35, 35));

        JPanel leftZone = new JPanel(new BorderLayout());
        leftZone.add(p.timerPanel, BorderLayout.CENTER);
        leftZone.add(verticalSeparator, BorderLayout.EAST);
        topPanel.add(leftZone, BorderLayout.WEST);

        p.timelinePanel.applyCustomization(customization);
        p.timer.setTheme(customization.timerBackground, customization.timerImagePath);

        p.mainPanel.add(topPanel, BorderLayout.CENTER);

        // Separator + timeline at bottom
        JPanel horizontalSeparator = new JPanel();
        horizontalSeparator.setPreferredSize(new Dimension(1, 5));
        horizontalSeparator.setBackground(new Color(35, 35, 35));

        JPanel bottomZone = new JPanel(new BorderLayout());
        bottomZone.add(horizontalSeparator, BorderLayout.NORTH);
        bottomZone.add(p.timelinePanel, BorderLayout.CENTER);

        p.mainPanel.add(bottomZone, BorderLayout.SOUTH);

        return p;
    }
}
