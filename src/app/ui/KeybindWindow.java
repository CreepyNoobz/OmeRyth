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
    private KeyButton separateurButton = new KeyButton("NUMPAD 4");
    private KeyButton zoomInButton = new KeyButton("+");
    private KeyButton zoomOutButton = new KeyButton("-");
    private KeyButton finPhraseButton = new KeyButton("NUMPAD 3");
    private KeyButton signeMpbButton = new KeyButton("M");
    private KeyButton signeFvrButton = new KeyButton("NUMPAD 5");
    private KeyButton signeNeutralButton = new KeyButton("NUMPAD 6");
    private KeyButton signeVoyelleButton = new KeyButton("NUMPAD 7");
    private KeyButton signeRespirationButton = new KeyButton("NUMPAD 8");
    private KeyButton signePlanButton = new KeyButton("NUMPAD 0");
    private OmeTheme.OmeButton saveButton = OmeTheme.createPrimaryButton("Enregistrer");

    private MainFenetre parent;

    public KeybindWindow(MainFenetre parent) {
        super(parent, "Configuration des touches", true);
        this.parent = parent;

        setSize(640, 700);
        setMinimumSize(new Dimension(480, 380));
        setResizable(true);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(parent);
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        // En-tête
        add(OmeTheme.createDialogHeader(
                "CONTRÔLES",
                "Configuration des touches",
                "Cliquez sur une touche pour la modifier, puis appuyez sur votre clavier."
        ), BorderLayout.NORTH);

        // Contenu
        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(OmeTheme.bg());
        contentPanel.setBorder(new EmptyBorder(16, 22, 16, 22));

        // Carte regroupant les raccourcis
        JPanel listCard = new JPanel();
        listCard.setLayout(new BoxLayout(listCard, BoxLayout.Y_AXIS));
        listCard.setBackground(OmeTheme.bgRaised());
        listCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(OmeTheme.border(), 1),
                new EmptyBorder(6, 16, 6, 16)
        ));

        addRow(listCard, "Lecture / Arrêt de la vidéo", marcheArretButton);
        addRow(listCard, "Avance rapide", avanceMSButton);
        addRow(listCard, "Recul rapide", reculeMSButton);
        addRow(listCard, "Retour au début", retourDebutButton);
        addRow(listCard, "Séparateur bleu (synchronisme)", separateurButton);
        addRow(listCard, "Zoom avant", zoomInButton);
        addRow(listCard, "Zoom arrière", zoomOutButton);
        addRow(listCard, "Fin de phrase", finPhraseButton);
        addRow(listCard, "Signe Labiale MPB (M, P, B)", signeMpbButton);
        addRow(listCard, "Signe Demi-labiale / Dentale (F, V, R)", signeFvrButton);
        addRow(listCard, "Signe Consonne Neutre", signeNeutralButton);
        addRow(listCard, "Signe Grande Ouverture (A / Voyelle)", signeVoyelleButton);
        addRow(listCard, "Signe Respiration / Souffle (h/)", signeRespirationButton);
        addRow(listCard, "Changement de plan (Cut vidéo)", signePlanButton, false);

        contentPanel.add(listCard);
        JScrollPane scroll = new JScrollPane(contentPanel);
        OmeTheme.styleBorderlessScrollPane(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // Charger les touches sauvegardées
        Properties props = FileUtils.loadKeybinds();
        marcheArretButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("marcheArretCode", "32"))));
        avanceMSButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("avanceMSCode", "39"))));
        reculeMSButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("reculerMSCode", "37"))));
        retourDebutButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("retourDebutCode", "82"))));
        separateurButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("separateurKeyCode", String.valueOf(KeyEvent.VK_NUMPAD4)))));
        zoomInButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("zoomInCode", String.valueOf(KeyEvent.VK_EQUALS)))));
        zoomOutButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("zoomOutCode", String.valueOf(KeyEvent.VK_MINUS)))));
        finPhraseButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("finPhraseCode", "99"))));
        signeMpbButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeMpbCode", String.valueOf(KeyEvent.VK_M)))));
        signeFvrButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeFvrCode", "101"))));
        signeNeutralButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeNeutralCode", "102"))));
        signeVoyelleButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeVoyelleCode", "103"))));
        signeRespirationButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signeRespirationCode", "104"))));
        signePlanButton.setKeyText(parent.getKeyText(Integer.parseInt(props.getProperty("signePlanCode", "96"))));

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
        signePlanButton.addActionListener(e -> listenForKey(signePlanButton, "signePlanCode", parent::setSignePlanKeyCode));

        // Footer
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
            FileUtils.saveKeybind("signePlanCode", Integer.toString(parent.getSignePlanKeyCode()));
            dispose();
        });

        OmeTheme.OmeButton cancelButton = OmeTheme.createGhostButton("Annuler");
        cancelButton.addActionListener(e -> dispose());

        add(OmeTheme.createDialogFooter(cancelButton, saveButton), BorderLayout.SOUTH);
    }

    private void addRow(JPanel container, String labelText, KeyButton button) {
        addRow(container, labelText, button, true);
    }

    private void addRow(JPanel container, String labelText, KeyButton button, boolean withSep) {
        JPanel row = new JPanel(new BorderLayout(15, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(9, 0, 9, 0));

        JLabel descLabel = new JLabel(labelText);
        descLabel.setFont(OmeTheme.fontSans(Font.PLAIN, 13));
        descLabel.setForeground(OmeTheme.text());

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        btnPanel.setOpaque(false);
        btnPanel.add(button);

        row.add(descLabel, BorderLayout.CENTER);
        row.add(btnPanel, BorderLayout.EAST);

        container.add(row);

        if (withSep) {
            JSeparator sep = new JSeparator();
            sep.setForeground(OmeTheme.border());
            sep.setBackground(OmeTheme.border());
            sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
            container.add(sep);
        }
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
            int w = getFontMetrics(OmeTheme.fontMono(Font.BOLD, 11.5f)).stringWidth(text) + 28;
            setPreferredSize(new Dimension(Math.max(48, w), 28));
            revalidate();
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            boolean listening = "...".equals(text);

            Color face = listening ? OmeTheme.accentSubtleSolid()
                    : (getModel().isRollover() ? OmeTheme.surfaceHover() : OmeTheme.surface());
            Color border = listening ? OmeTheme.accent()
                    : (getModel().isRollover() ? OmeTheme.accent() : OmeTheme.borderStrong());

            g2.setColor(OmeTheme.border());
            g2.fillRoundRect(0, 2, w - 1, h - 3, 6, 6);

            int yOff = getModel().isPressed() ? 1 : 0;
            g2.setColor(face);
            g2.fillRoundRect(0, yOff, w - 1, h - 3, 6, 6);
            g2.setColor(border);
            g2.drawRoundRect(0, yOff, w - 1, h - 3, 6, 6);

            g2.setColor(listening ? OmeTheme.accent() : OmeTheme.text());
            g2.setFont(OmeTheme.fontMono(Font.BOLD, 11.5f));
            FontMetrics fm = g2.getFontMetrics();
            int tx = (w - fm.stringWidth(text)) / 2;
            int ty = (h - 2 - fm.getHeight()) / 2 + fm.getAscent() + yOff;
            g2.drawString(text, tx, ty);

            g2.dispose();
        }
    }
}