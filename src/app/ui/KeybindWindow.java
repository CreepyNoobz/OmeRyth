package app.ui;

import app.utils.FileUtils;
import app.MainFenetre;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.Properties;
import java.util.function.IntConsumer;

public class KeybindWindow extends JDialog {
    private KeyButton marcheArretButton = new KeyButton("SPACE");
    private KeyButton avanceMSButton = new KeyButton("→");
    private KeyButton retourDebutButton = new KeyButton("R"); 
    private KeyButton reculeMSButton = new KeyButton("←"); 
    private KeyButton separateurButton = new KeyButton("M");
    private KeyButton zoomInButton = new KeyButton("+");
    private KeyButton zoomOutButton = new KeyButton("-");
    private KeyButton finPhraseButton = new KeyButton("NUMPAD 3");
    private KeyButton signeMpbButton = new KeyButton("NUMPAD 4");
    private KeyButton signeFvrButton = new KeyButton("NUMPAD 5");
    private KeyButton signeNeutralButton = new KeyButton("NUMPAD 6");
    private KeyButton signeVoyelleButton = new KeyButton("NUMPAD 7");
    private KeyButton signeRespirationButton = new KeyButton("NUMPAD 8");
    private JButton saveButton = new JButton("✔ Enregistrer");

    private MainFenetre parent;

    public KeybindWindow(MainFenetre parent) {
        super(parent, "Configuration des touches", true);
        this.parent = parent;

        setSize(620, 680);
        setMinimumSize(new Dimension(620, 580));
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(250, 250, 250));

        // En-tête
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(30, 30, 30));
        headerPanel.setBorder(new EmptyBorder(15, 20, 15, 20));
        JLabel titleLabel = new JLabel("Configuration des touches");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        headerPanel.add(titleLabel, BorderLayout.WEST);
        add(headerPanel, BorderLayout.NORTH);

        // Contenu
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(new Color(250, 250, 250));
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // Instructions
        JLabel instruction = new JLabel("Cliquez sur une touche pour la modifier, puis appuyez sur votre clavier.");
        instruction.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        instruction.setForeground(new Color(100, 100, 100));
        instruction.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(instruction);
        contentPanel.add(Box.createVerticalStrut(20));

        addRow(contentPanel, "Lecture / Arrêt de la vidéo", marcheArretButton);
        addRow(contentPanel, "Avance rapide", avanceMSButton);
        addRow(contentPanel, "Recul rapide", reculeMSButton);
        addRow(contentPanel, "Retour au début", retourDebutButton);
        addRow(contentPanel, "Séparateur de plan (standard)", separateurButton);
        addRow(contentPanel, "Zoom avant", zoomInButton);
        addRow(contentPanel, "Zoom arrière", zoomOutButton);
        addRow(contentPanel, "Fin de phrase", finPhraseButton);
        addRow(contentPanel, "Signe Labiale MPB (M, P, B)", signeMpbButton);
        addRow(contentPanel, "Signe Demi-labiale / Dentale (F, V, R)", signeFvrButton);
        addRow(contentPanel, "Signe Consonne Neutre", signeNeutralButton);
        addRow(contentPanel, "Signe Grande Ouverture (A / Voyelle)", signeVoyelleButton);
        addRow(contentPanel, "Signe Respiration / Souffle (h/)", signeRespirationButton);

        JScrollPane scroll = new JScrollPane(contentPanel);
        scroll.setBorder(null);
        add(scroll, BorderLayout.CENTER);

        // Charger les touches sauvegardées
        Properties props = FileUtils.loadKeybinds();
        marcheArretButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("marcheArretCode", "32"))));
        avanceMSButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("avanceMSCode", "39"))));
        reculeMSButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("reculerMSCode", "37"))));
        retourDebutButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("retourDebutCode", "82"))));
        separateurButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("separateurKeyCode", "77"))));
        zoomInButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("zoomInCode", "107"))));
        zoomOutButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("zoomOutCode", "109"))));
        finPhraseButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("finPhraseCode", "99"))));
        signeMpbButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeMpbCode", "100"))));
        signeFvrButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeFvrCode", "101"))));
        signeNeutralButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeNeutralCode", "102"))));
        signeVoyelleButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeVoyelleCode", "103"))));
        signeRespirationButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeRespirationCode", "104"))));

        // Listeners
        marcheArretButton.addActionListener(e -> listenForKey(marcheArretButton, "marcheArretCode", parent::setMarcheArretKeyCode));
        avanceMSButton.addActionListener(e -> listenForKey(avanceMSButton, "avanceMSCode", parent::setAvanceMSKeyCode));
        reculeMSButton.addActionListener(e -> listenForKey(reculeMSButton, "reculerMSCode", parent::setReculerMSKeyCode));
        retourDebutButton.addActionListener(e -> listenForKey(retourDebutButton, "retourDebutCode", parent::setRetourDebutKeyCode));
        separateurButton.addActionListener(e -> listenForKey(separateurButton, "separateurKeyCode", parent::setSeparateurKeyCode));
        zoomInButton.addActionListener(e -> listenForKey(zoomInButton, "zoomInCode", parent::setZoomInKeyCode));
        zoomOutButton.addActionListener(e -> listenForKey(zoomOutButton, "zoomOutCode", parent::setZoomOutKeyCode));
        finPhraseButton.addActionListener(e -> listenForKey(finPhraseButton, "finPhraseCode", parent::setFinPhraseKeyCode));
        signeMpbButton.addActionListener(e -> listenForKey(signeMpbButton, "signeMpbCode", parent::setSigneMpbKeyCode));
        signeFvrButton.addActionListener(e -> listenForKey(signeFvrButton, "signeFvrCode", parent::setSigneFvrKeyCode));
        signeNeutralButton.addActionListener(e -> listenForKey(signeNeutralButton, "signeNeutralCode", parent::setSigneNeutralKeyCode));
        signeVoyelleButton.addActionListener(e -> listenForKey(signeVoyelleButton, "signeVoyelleCode", parent::setSigneVoyelleKeyCode));
        signeRespirationButton.addActionListener(e -> listenForKey(signeRespirationButton, "signeRespirationCode", parent::setSigneRespirationKeyCode));

        // Footer
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 15));
        footerPanel.setBackground(Color.WHITE);
        footerPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)));
        
        saveButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        saveButton.setBackground(new Color(0, 120, 215));
        saveButton.setForeground(Color.BLACK); // Noir au lieu de blanc
        saveButton.setFocusPainted(false);
        saveButton.addActionListener(e -> {
            FileUtils.saveKeybind("marcheArretCode", Integer.toString(parent.getMarcheArretKeyCode()));
            FileUtils.saveKeybind("avanceMSCode", Integer.toString(parent.getAvanceMSKeyCode()));
            FileUtils.saveKeybind("reculerMSCode", Integer.toString(parent.getReculerMSKeyCode()));
            FileUtils.saveKeybind("retourDebutCode", Integer.toString(parent.getRetourDebutKeyCode()));
            FileUtils.saveKeybind("separateurKeyCode", Integer.toString(parent.getSeparateurKeyCode()));
            FileUtils.saveKeybind("zoomInCode", Integer.toString(parent.getZoomInKeyCode()));
            FileUtils.saveKeybind("zoomOutCode", Integer.toString(parent.getZoomOutKeyCode()));
            FileUtils.saveKeybind("finPhraseCode", Integer.toString(parent.getFinPhraseKeyCode()));
            FileUtils.saveKeybind("signeMpbCode", Integer.toString(parent.getSigneMpbKeyCode()));
            FileUtils.saveKeybind("signeFvrCode", Integer.toString(parent.getSigneFvrKeyCode()));
            FileUtils.saveKeybind("signeNeutralCode", Integer.toString(parent.getSigneNeutralKeyCode()));
            FileUtils.saveKeybind("signeVoyelleCode", Integer.toString(parent.getSigneVoyelleKeyCode()));
            FileUtils.saveKeybind("signeRespirationCode", Integer.toString(parent.getSigneRespirationKeyCode()));
            dispose();
        });

        JButton cancelButton = new JButton("Annuler");
        cancelButton.setFocusPainted(false);
        cancelButton.addActionListener(e -> dispose());

        footerPanel.add(cancelButton);
        footerPanel.add(saveButton);
        add(footerPanel, BorderLayout.SOUTH);
    }

    private void addRow(JPanel container, String labelText, KeyButton button) {
        JPanel row = new JPanel(new BorderLayout(15, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(8, 0, 8, 0));
        
        JLabel descLabel = new JLabel(labelText);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        descLabel.setForeground(new Color(50, 50, 50));
        
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(button);

        row.add(descLabel, BorderLayout.CENTER);
        row.add(btnPanel, BorderLayout.EAST);
        
        container.add(row);
        
        JSeparator sep = new JSeparator();
        sep.setForeground(new Color(230, 230, 230));
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        container.add(sep);
    }

    private void listenForKey(KeyButton button, String fileKey, IntConsumer setter) {
        button.setKeyText("...");
        KeyboardFocusManager focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyEventDispatcher[] holder = new KeyEventDispatcher[1];
        holder[0] = e -> {
            if (!isVisible()) {
                focusManager.removeKeyEventDispatcher(holder[0]);
                return false;
            }
            if (e.getID() != KeyEvent.KEY_PRESSED) return false;

            int code = e.getKeyCode();
            String name = parent.getKeyText(code);
            button.setKeyText(name);

            FileUtils.saveKeybind(fileKey, Integer.toString(code));
            setter.accept(code);

            focusManager.removeKeyEventDispatcher(holder[0]);
            return true;
        };

        focusManager.addKeyEventDispatcher(holder[0]);
        this.requestFocus();
        this.requestFocusInWindow();
    }

    static class KeyButton extends JButton {
        private String text;

        public KeyButton(String text) {
            super("");
            setKeyText(text);
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
        }

        public void setKeyText(String text) {
            this.text = text;
            setPreferredSize(new Dimension(getFontMetrics(new Font("Segoe UI", Font.BOLD, 12)).stringWidth(text) + 30, 30));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            if (getModel().isPressed()) {
                g2.setColor(new Color(220, 220, 220));
                g2.fillRoundRect(0, 2, w, h - 2, 6, 6);
            } else if (getModel().isRollover()) {
                g2.setColor(new Color(200, 200, 200));
                g2.fillRoundRect(0, 2, w, h - 2, 6, 6);
                g2.setColor(new Color(235, 235, 235));
                g2.fillRoundRect(0, 0, w, h - 3, 6, 6);
                g2.setColor(new Color(190, 190, 190));
                g2.drawRoundRect(0, 0, w - 1, h - 4, 6, 6);
            } else {
                g2.setColor(new Color(200, 200, 200));
                g2.fillRoundRect(0, 2, w, h - 2, 6, 6);
                g2.setColor(new Color(245, 245, 245));
                g2.fillRoundRect(0, 0, w, h - 3, 6, 6);
                g2.setColor(new Color(210, 210, 210));
                g2.drawRoundRect(0, 0, w - 1, h - 4, 6, 6);
            }

            g2.setColor(new Color(50, 50, 50));
            g2.setFont(new Font("Segoe UI", Font.BOLD, 12));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() - (getModel().isPressed() ? 0 : 2);
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }
}