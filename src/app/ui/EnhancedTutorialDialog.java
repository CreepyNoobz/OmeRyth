package app.ui;

import app.MainFenetre;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;

/**
 * Tutoriel interactif amélioré avec des étapes progressives et visuelles.
 * Guide complet du démarrage à la maîtrise.
 */
public class EnhancedTutorialDialog extends JDialog {

    private final MainFenetre mainFenetre;
    private int currentStep = 0;
    private JLabel headerLabel;
    private JTextArea contentArea;
    private JButton backBtn;
    private JButton nextBtn;
    private JButton closeBtn;
    private JProgressBar progressBar;

    private final String[] STEPS;
    private final String[] CONTENTS;

    private String[] createSteps(boolean isEn) {
        if (isEn) {
            return new String[] {
                "1. Welcome to OmeRyth!",
                "2. What is OmeRyth?",
                "3. How to create a project",
                "4. How to synchronize text",
                "5. Role management",
                "6. Essential keyboard shortcuts",
                "7. Saving and exporting",
                "8. You are ready!"
            };
        } else {
            return new String[] {
                "1. Bienvenue sur OmeRyth !",
                "2. Qu'est-ce qu'OmeRyth ?",
                "3. Comment créer un projet",
                "4. Comment synchroniser",
                "5. Gestion des rôles",
                "6. Raccourcis clavier essentiels",
                "7. Sauvegarde et export",
                "8. Vous êtes prêt !"
            };
        }
    }

    private String[] createContents(boolean isEn) {
        String space = mainFenetre != null ? mainFenetre.getKeyText(mainFenetre.getMarcheArretKeyCode()) : "ESPACE";
        String right = mainFenetre != null ? mainFenetre.getKeyText(mainFenetre.getAvanceMSKeyCode()) : "→";
        String left = mainFenetre != null ? mainFenetre.getKeyText(mainFenetre.getReculerMSKeyCode()) : "←";
        String restart = mainFenetre != null ? mainFenetre.getKeyText(mainFenetre.getRetourDebutKeyCode()) : "R";
        String separator = mainFenetre != null ? mainFenetre.getKeyText(mainFenetre.getSeparateurKeyCode()) : "M";

        if (isEn) {
            return new String[] {
                """
                Welcome to OmeRyth - the rhythm and typography synchronization tool!

                This tutorial will guide you through the main features.
                
                You will learn how to:
                * Create a new project
                * Synchronize text with music and video
                * Manage multiple roles (singer, actor, narrator, etc.)
                * Use essential keyboard shortcuts
                
                Click "Next" to get started!
                """,

                """
                What is OmeRyth?

                OmeRyth is a professional synchronization tool that allows you to:

                1. Import a video or audio track
                2. Divide content into "bands" (text lines)
                3. Synchronize each band with the media
                4. Assign different roles to bands (singer, actor, narrator, etc.)
                5. Export your work as a video

                Ideal for dubbing, music videos, presentations, karaoke, etc.
                """,

                """
                How to create a project

                Simple steps:

                1. Click "New Project"
                2. Select your video or audio file (MP4, MP3, WAV, OGG, etc.)
                3. Choose the number of bands (text lines)
                4. Select a role preset (optional)
                5. Click "Create project"

                Tip: Start with 2-3 bands, you can add more later!
                """,

                """
                How to synchronize text

                Simple synchronization:

                1. Double-click on a band to edit text
                2. Video playback pauses automatically
                3. Type your text
                4. Click "Add separator" (or press %s) to mark pauses
                5. Press %s to play or pause playback
                6. Use %s / %s to step backward or forward by 0.5s
                
                Tip: Listen multiple times and adjust separators!
                """.formatted(separator, space, left, right),

                """
                Role management

                Roles allow you to manage multiple characters or actors:

                * Each role has its own band with its unique color
                * Click "Role" in the menu to manage roles
                * You can add, delete, or rename roles
                * Each role can have a distinct color
                
                Example:
                - Red band = Singer
                - Blue band = Actor
                - Yellow band = Narrator
                """,

                """
                Essential keyboard shortcuts

                Top shortcuts to remember:

                %-12s = Play / Pause
                %-12s = Rewind 0.5s
                %-12s = Forward 0.5s
                %-12s = Return to start
                %-12s = Add separator
                CTRL+Z / Y   = Undo / Redo

                Tip: Other options are available in the top menu!
                """.formatted(space, left, right, restart, separator),

                """
                Saving and exporting

                Automatic save:
                * Your work is automatically saved every 15 seconds
                * You won't lose your progress!

                Export to video:
                1. Click "Folder" -> "Export video..."
                2. The final video will be generated with your synced subtitles
                3. Wait for the process to complete

                Tip: Review your timeline before exporting!
                """,

                """
                Congratulations!

                You have completed the OmeRyth tutorial!

                You are now ready to:
                * Create your own projects
                * Synchronize text with music and video
                * Export your creations

                Tip: Explore the menus to discover additional features.

                Have fun creating!
                """
            };
        }

        return new String[] {
            """
            Bienvenue dans OmeRyth - l'outil de synchronisation de rythme et typographie !

            Ce tutoriel vous guidera à travers les fonctionnalités principales.
            
            Vous apprendrez à :
            * Créer un nouveau projet
            * Synchroniser du texte avec la musique/vidéo
            * Gérer plusieurs rôles (chanteur, danseur, etc.)
            * Utiliser les raccourcis clavier essentiels
            
            Cliquez sur "Suivant" pour commencer !
            """,

            """
            Qu'est-ce qu'OmeRyth ?

            OmeRyth est un outil de synchronisation professionnel qui vous permet de :

            1. Importer une vidéo ou une piste audio
            2. Diviser le contenu en "bandes" (lignes de texte)
            3. Synchroniser chaque bande avec la musique
            4. Assigner différents rôles aux bandes (chanteur, danseur, narrateur, etc.)
            5. Exporter votre travail en vidéo

            Idéal pour les clips musicaux, présentations, karaoke, etc.
            """,

            """
            Comment créer un projet

            Étapes simples :

            1. Cliquez sur "Nouveau Projet"
            2. Sélectionnez votre fichier vidéo/audio (MP4, MP3, WAV, OGG, etc.)
            3. Choisissez le nombre de bandes (lignes de texte)
            4. Sélectionnez un préset de rôles (optionnel)
            5. Cliquez sur "Créer le projet"

            Conseil : Commencez avec 2-3 bandes, vous pouvez en ajouter après !
            """,

            """
            Comment synchroniser du texte

            Synchronisation simple :

            1. Double-cliquez sur une bande pour éditer le texte
            2. La lecture de la vidéo s'arrête automatiquement
            3. Tapez votre texte
            4. Cliquez sur "Ajouter séparateur" (ou appuyez sur %s) pour marquer les pauses
            5. Appuyez sur %s pour jouer/arrêter la lecture
            6. Utilisez %s / %s pour avancer/reculer de 0.5s
            
            Conseil : Écoutez plusieurs fois et ajustez les séparateurs !
            """.formatted(separator, space, left, right),

            """
            Gestion des rôles

            Les rôles permettent de gérer plusieurs "acteurs" :

            * Chaque rôle a sa propre bande avec sa couleur
            * Cliquez sur "Gestion des Rôles" pour modifier
            * Vous pouvez ajouter/supprimer/renommer des rôles
            * Chaque rôle peut avoir une couleur différente
            
            Exemple : 
            - Bande Rouge = Chanteur
            - Bande Bleue = Danseur
            - Bande Jaune = Narrateur
            """,

            """
            Raccourcis clavier essentiels

            Les 5 raccourcis à retenir :

            %-12s = Lecture / Arrêt
            %-12s = Reculer de 0.5s
            %-12s = Avancer de 0.5s
            %-12s = Retour au début
            %-12s = Ajouter un séparateur
            CTRL+Z / Y   = Annuler / Rétablir

            Conseil : Les autres options sont dans les menus !
            """.formatted(space, left, right, restart, separator),

            """
            Sauvegarde et export

            Sauvegarde automatique :
            * Votre travail est sauvegardé automatiquement chaque 15 secondes
            * Vous ne perdrez rien !

            Exporter en vidéo :
            1. Cliquez sur "Fichier" -> "Prendre en vidéo"
            2. La vidéo finale sera créée avec votre synchronisation
            3. Attendez la fin du traitement

            Conseil : Vérifiez votre travail avant d'exporter !
            """,

            """
            Félicitations !

            Vous avez terminé le tutoriel d'OmeRyth !

            Vous êtes maintenant prêt à :
            * Créer vos propres projets
            * Synchroniser du texte avec la musique
            * Exporter vos créations

            Conseil : Explorez les menus pour découvrir d'autres fonctionnalités.

            Amusez-vous bien !
            """
        };
    }

    public EnhancedTutorialDialog(MainFenetre owner) {
        super(owner, owner != null && owner.isEnglish() ? "OmeRyth Tutorial" : "Tutoriel OmeRyth", true);
        this.mainFenetre = owner;
        boolean isEn = owner != null && owner.isEnglish();
        this.STEPS = createSteps(isEn);
        this.CONTENTS = createContents(isEn);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(650, 500);
        setMinimumSize(new Dimension(650, 500));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));
        getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        // En-tête
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerLabel = new JLabel();
        headerLabel.setFont(new Font("Arial", Font.BOLD, 18));
        headerLabel.setForeground(new Color(70, 130, 180));
        headerPanel.add(headerLabel, BorderLayout.WEST);

        // Barre de progression
        progressBar = new JProgressBar(0, STEPS.length - 1);
        progressBar.setStringPainted(true);
        progressBar.setString("");
        headerPanel.add(progressBar, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Zone de contenu
        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setFont(new Font("Arial", Font.PLAIN, 13));
        contentArea.setBackground(Color.WHITE);
        contentArea.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        JScrollPane scroll = new JScrollPane(contentArea);
        scroll.setPreferredSize(new Dimension(620, 300));
        add(scroll, BorderLayout.CENTER);

        // Boutons de navigation
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        backBtn = new JButton(isEn ? "← Back" : "← Précédent");
        nextBtn = new JButton(isEn ? "Next →" : "Suivant →");
        closeBtn = new JButton(isEn ? "Close" : "Fermer");

        backBtn.addActionListener(e -> previousStep());
        nextBtn.addActionListener(e -> nextStep());
        closeBtn.addActionListener(e -> dispose());

        btnPanel.add(backBtn);
        btnPanel.add(nextBtn);
        btnPanel.add(Box.createHorizontalStrut(20));
        btnPanel.add(closeBtn);

        add(btnPanel, BorderLayout.SOUTH);

        updateStep();
    }

    private void nextStep() {
        if (currentStep < STEPS.length - 1) {
            currentStep++;
            updateStep();
        } else {
            dispose();
        }
    }

    private void previousStep() {
        if (currentStep > 0) {
            currentStep--;
            updateStep();
        }
    }

    private void updateStep() {
        boolean isEn = mainFenetre != null && mainFenetre.isEnglish();
        headerLabel.setText(STEPS[currentStep]);
        contentArea.setText(CONTENTS[currentStep]);
        contentArea.setCaretPosition(0);

        backBtn.setEnabled(currentStep > 0);
        nextBtn.setText(currentStep == STEPS.length - 1 ? (isEn ? "Finish" : "Terminer") : (isEn ? "Next →" : "Suivant →"));

        progressBar.setValue(currentStep);
        progressBar.setString((currentStep + 1) + " / " + STEPS.length);
    }
}
