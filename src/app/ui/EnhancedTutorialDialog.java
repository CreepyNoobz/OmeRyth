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

    private final String[] STEPS = {
        "📚 Bienvenue sur OmeRyth !",
        "🎯 Qu'est-ce qu'OmeRyth ?",
        "🎬 Comment créer un projet",
        "⏱️ Comment synchroniser",
        "🎭 Gestion des rôles",
        "⌨️ Raccourcis clavier essentiels",
        "💾 Sauvegarde et export",
        "🎉 Vous êtes prêt !"
    };

    private final String[] CONTENTS;

    private String[] createContents() {
        String space = mainFenetre.getKeyText(mainFenetre.getMarcheArretKeyCode());
        String right = mainFenetre.getKeyText(mainFenetre.getAvanceMSKeyCode());
        String left = mainFenetre.getKeyText(mainFenetre.getReculerMSKeyCode());
        String restart = mainFenetre.getKeyText(mainFenetre.getRetourDebutKeyCode());
        String separator = mainFenetre.getKeyText(mainFenetre.getSeparateurKeyCode());

        return new String[] {
            """
            Bienvenue dans OmeRyth - l'outil de synchronisation de rythme et typographie !

            Ce tutoriel vous guidera à travers les fonctionnalités principales.
            
            Vous apprendrez à :
            • Créer un nouveau projet
            • Synchroniser du texte avec la musique/vidéo
            • Gérer plusieurs rôles (chanteur, danseur, etc.)
            • Utiliser les raccourcis clavier essentiels
            
            Cliquez sur \"Suivant\" pour commencer !
            """,

            """
            Qu'est-ce qu'OmeRyth ?

            OmeRyth est un outil de synchronisation professionnel qui vous permet de :

            1. Importer une vidéo ou une piste audio
            2. Diviser le contenu en \"bandes\" (lignes de texte)
            3. Synchroniser chaque bande avec la musique
            4. Assigner différents rôles aux bandes (chanteur, danseur, narrateur, etc.)
            5. Exporter votre travail en vidéo

            Idéal pour les clips musicaux, présentations, karaoke, etc.
            """,

            """
            Comment créer un projet

            Étapes simples :

            1. Cliquez sur \"Nouveau Projet\"
            2. Sélectionnez votre fichier vidéo/audio (MP4, MP3, WAV, OGG, etc.)
            3. Choisissez le nombre de bandes (lignes de texte)
            4. Sélectionnez un préset de rôles (optionnel)
            5. Cliquez sur \"Créer le projet\"

            Conseil : Commencez avec 2-3 bandes, vous pouvez en ajouter après !
            """,

            """
            Comment synchroniser du texte

            Synchronisation simple :

            1. Double-cliquez sur une bande pour éditer le texte
            2. La lecture de la vidéo s'arrête automatiquement
            3. Tapez votre texte
            4. Cliquez sur \"Ajouter séparateur\" (ou appuyez sur " + separator + ") pour marquer les pauses
            5. Appuyez sur " + space + " pour jouer/arrêter la lecture
            6. Utilisez " + left + " / " + right + " pour avancer/reculer de 0.5s
            
            Conseil : Écoutez plusieurs fois et ajustez les séparateurs !
            """,

            """
            Gestion des rôles

            Les rôles permettent de gérer plusieurs \"acteurs\" :

            • Chaque rôle a sa propre bande avec sa couleur
            • Cliquez sur \"Gestion des Rôles\" pour modifier
            • Vous pouvez ajouter/supprimer/renommer des rôles
            • Chaque rôle peut avoir une couleur différente
            
            Exemple : 
            - Bande Rouge = Chanteur
            - Bande Bleue = Danseur
            - Bande Jaune = Narrateur
            """,

            """
            Raccourcis clavier essentiels

            Les 5 raccourcis à retenir :

            """ + space + "       = Lecture / Arrêt\n" +
            left + "          = Reculer de 0.5s\n" +
            right + "          = Avancer de 0.5s\n" +
            restart + "            = Retour au début\n" +
            separator + "            = Ajouter un séparateur\n" +
            "CTRL+Z / Y   = Annuler / Rétablir\n\n" +
            "Conseil : Les autres options sont dans les menus !\n",

            """
            Sauvegarde et export

            Sauvegarde automatique :
            ✓ Votre travail est sauvegardé automatiquement chaque 15 secondes
            ✓ Vous ne perdrez rien !

            Exporter en vidéo :
            1. Cliquez sur \"Fichier\" → \"Prendre en vidéo\"
            2. La vidéo finale sera créée avec votre synchronisation
            3. Attendez la fin du traitement

            Conseil : Vérifiez votre travail avant d'exporter !
            """,

            """
            Félicitations ! 🎉

            Vous avez terminé le tutoriel d'OmeRyth !

            Vous êtes maintenant prêt à :
            ✓ Créer vos propres projets
            ✓ Synchroniser du texte avec la musique
            ✓ Exporter vos créations

            Conseil : Explorez les menus pour découvrir d'autres fonctionnalités.

            Amusez-vous bien !
            """
        };
    }

    public EnhancedTutorialDialog(MainFenetre owner) {
        super(owner, "Tutoriel OmeRyth", true);
        this.mainFenetre = owner;
        this.CONTENTS = createContents();
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
        backBtn = new JButton("← Précédent");
        nextBtn = new JButton("Suivant →");
        closeBtn = new JButton("Fermer");

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
        headerLabel.setText(STEPS[currentStep]);
        contentArea.setText(CONTENTS[currentStep]);
        contentArea.setCaretPosition(0);

        backBtn.setEnabled(currentStep > 0);
        nextBtn.setText(currentStep == STEPS.length - 1 ? "Terminer" : "Suivant →");

        progressBar.setValue(currentStep);
        progressBar.setString((currentStep + 1) + " / " + STEPS.length);
    }
}
