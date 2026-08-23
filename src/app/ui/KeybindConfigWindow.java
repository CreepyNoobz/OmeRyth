package app.ui;

import app.MainFenetre;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

/**
 * Fenêtre de configuration des raccourcis clavier.
 * Permet à l'utilisateur de redéfinir les touches et les sauvegarde.
 */
public class KeybindConfigWindow extends JDialog {
    private MainFenetre mainFenetre;
    private Properties keybinds;
    private static final String KEYBINDS_FILE = "keybinds.properties";

    public KeybindConfigWindow(MainFenetre mainFenetre) {
        super(mainFenetre, "Configuration des Raccourcis", true);
        this.mainFenetre = mainFenetre;
        this.keybinds = loadKeybinds();
        
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setSize(500, 400);
        setLocationRelativeTo(mainFenetre);
        
        initUI();
    }

    private void initUI() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        // Titre
        JLabel titleLabel = new JLabel("Redéfinir les raccourcis clavier");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        panel.add(titleLabel, BorderLayout.NORTH);
        
        // Grille des raccourcis
        JPanel gridPanel = new JPanel(new GridLayout(0, 2, 10, 10));
        gridPanel.add(new JLabel("Action"));
        gridPanel.add(new JLabel("Touche"));
        
        // Play/Pause
        gridPanel.add(new JLabel("Lecture / Arrêt"));
        gridPanel.add(createKeybindField("marcheArretCode", KeyEvent.VK_SPACE));
        
        // Avancer
        gridPanel.add(new JLabel("Avancer 0.5s"));
        gridPanel.add(createKeybindField("avanceMSCode", KeyEvent.VK_RIGHT));
        
        // Reculer
        gridPanel.add(new JLabel("Reculer 0.5s"));
        gridPanel.add(createKeybindField("reculerMSCode", KeyEvent.VK_LEFT));
        
        // Retour début
        gridPanel.add(new JLabel("Retour au début"));
        gridPanel.add(createKeybindField("retourDebutCode", KeyEvent.VK_R));
        
        // Séparateur
        gridPanel.add(new JLabel("Ajouter séparateur"));
        gridPanel.add(createKeybindField("separateurKeyCode", KeyEvent.VK_M));

        // Zoom In
        gridPanel.add(new JLabel("Zoom avant"));
        gridPanel.add(createKeybindField("zoomInCode", KeyEvent.VK_EQUALS));
        
        // Zoom Out
        gridPanel.add(new JLabel("Zoom arrière"));
        gridPanel.add(createKeybindField("zoomOutCode", KeyEvent.VK_MINUS));

        JScrollPane scrollPane = new JScrollPane(gridPanel);
        panel.add(scrollPane, BorderLayout.CENTER);
        
        // Boutons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        JButton saveButton = new JButton("Enregistrer");
        saveButton.addActionListener(e -> {
            saveKeybinds();
            JOptionPane.showMessageDialog(this,
                "Raccourcis enregistrés et appliqués !",
                "Succès", JOptionPane.INFORMATION_MESSAGE);
            dispose();
        });
        
        JButton resetButton = new JButton("Réinitialiser");
        resetButton.addActionListener(e -> {
            resetKeybinds();
        });
        
        JButton cancelButton = new JButton("Annuler");
        cancelButton.addActionListener(e -> dispose());
        
        buttonPanel.add(resetButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);
        
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(panel);
    }

    private JTextField createKeybindField(String keyName, int defaultKeyCode) {
        int keyCode = Integer.parseInt(keybinds.getProperty(keyName, String.valueOf(defaultKeyCode)));
        JTextField field = new JTextField(KeyEvent.getKeyText(keyCode), 15);
        field.setEditable(false);
        
        field.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                field.setText("Choisir une touche...");
                field.requestFocusInWindow();
            }
        });

        field.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() != KeyEvent.VK_UNDEFINED) {
                    field.setText(KeyEvent.getKeyText(e.getKeyCode()));
                    keybinds.setProperty(keyName, String.valueOf(e.getKeyCode()));
                    // Apply immediately to main window so changes are dynamic
                    switch (keyName) {
                        case "marcheArretCode": mainFenetre.setMarcheArretKeyCode(e.getKeyCode()); break;
                        case "avanceMSCode": mainFenetre.setAvanceMSKeyCode(e.getKeyCode()); break;
                        case "reculerMSCode": mainFenetre.setReculerMSKeyCode(e.getKeyCode()); break;
                        case "retourDebutCode": mainFenetre.setRetourDebutKeyCode(e.getKeyCode()); break;
                        case "separateurKeyCode": mainFenetre.setSeparateurKeyCode(e.getKeyCode()); break;
                        case "zoomInCode": mainFenetre.setZoomInKeyCode(e.getKeyCode()); break;
                        case "zoomOutCode": mainFenetre.setZoomOutKeyCode(e.getKeyCode()); break;
                        default: break;
                    }
                    e.consume();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {}

            @Override
            public void keyTyped(KeyEvent e) {}
        });
        
        field.setFocusable(true);
        field.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        
        return field;
    }

    private Properties loadKeybinds() {
        Properties props = new Properties();
        props.setProperty("marcheArretCode", String.valueOf(KeyEvent.VK_SPACE));
        props.setProperty("avanceMSCode", String.valueOf(KeyEvent.VK_RIGHT));
        props.setProperty("reculerMSCode", String.valueOf(KeyEvent.VK_LEFT));
        props.setProperty("retourDebutCode", String.valueOf(KeyEvent.VK_R));
        props.setProperty("separateurKeyCode", String.valueOf(KeyEvent.VK_M));
        props.setProperty("zoomInCode", String.valueOf(KeyEvent.VK_EQUALS));
        props.setProperty("zoomOutCode", String.valueOf(KeyEvent.VK_MINUS));

        File file = new File(KEYBINDS_FILE);
        if (file.exists()) {
            try (FileInputStream fis = new FileInputStream(file)) {
                props.load(fis);
            } catch (Exception e) {
                System.err.println("Erreur chargement keybinds: " + e.getMessage());
            }
        }
        
        return props;
    }

    private void saveKeybinds() {
        try (FileOutputStream fos = new FileOutputStream(KEYBINDS_FILE)) {
            keybinds.store(fos, "OmeRyth Keybinds");
            
            // Charger les nouvelles touches dans MainFenetre
            mainFenetre.setMarcheArretKeyCode(Integer.parseInt(keybinds.getProperty("marcheArretCode")));
            mainFenetre.setAvanceMSKeyCode(Integer.parseInt(keybinds.getProperty("avanceMSCode")));
            mainFenetre.setReculerMSKeyCode(Integer.parseInt(keybinds.getProperty("reculerMSCode")));
            mainFenetre.setRetourDebutKeyCode(Integer.parseInt(keybinds.getProperty("retourDebutCode")));
            mainFenetre.setSeparateurKeyCode(Integer.parseInt(keybinds.getProperty("separateurKeyCode")));
            if (keybinds.containsKey("zoomInCode")) {
                mainFenetre.setZoomInKeyCode(Integer.parseInt(keybinds.getProperty("zoomInCode")));
            }
            if (keybinds.containsKey("zoomOutCode")) {
                mainFenetre.setZoomOutKeyCode(Integer.parseInt(keybinds.getProperty("zoomOutCode")));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, 
                "Erreur sauvegarde: " + e.getMessage(),
                "Erreur", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void resetKeybinds() {
        keybinds.setProperty("marcheArretCode", String.valueOf(KeyEvent.VK_SPACE));
        keybinds.setProperty("avanceMSCode", String.valueOf(KeyEvent.VK_RIGHT));
        keybinds.setProperty("reculerMSCode", String.valueOf(KeyEvent.VK_LEFT));
        keybinds.setProperty("retourDebutCode", String.valueOf(KeyEvent.VK_R));
        keybinds.setProperty("separateurKeyCode", String.valueOf(KeyEvent.VK_M));
        keybinds.setProperty("zoomInCode", String.valueOf(KeyEvent.VK_EQUALS));
        keybinds.setProperty("zoomOutCode", String.valueOf(KeyEvent.VK_MINUS));
        
        dispose();
        new KeybindConfigWindow(mainFenetre).setVisible(true);
    }
}
