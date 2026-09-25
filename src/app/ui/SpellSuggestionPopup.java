package app.ui;

import app.services.SpellGrammarService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Popup moderne de suggestions de correction orthographique et grammaticale.
 * 
 * S'affiche avec une animation de fondu progressif ("fade-in") lorsqu'un utilisateur
 * survole une erreur pendant 0.5 seconde.
 */
public class SpellSuggestionPopup extends JWindow {

    private final SpellGrammarService.SpellCheckIssue issue;
    private final BiConsumer<SpellGrammarService.SpellCheckIssue, String> onApplyCorrection;
    private final Consumer<SpellGrammarService.SpellCheckIssue> onIgnoreWord;

    private Timer fadeTimer;
    private float currentOpacity = 0.0f;

    public SpellSuggestionPopup(Window owner,
                                SpellGrammarService.SpellCheckIssue issue,
                                BiConsumer<SpellGrammarService.SpellCheckIssue, String> onApplyCorrection,
                                Consumer<SpellGrammarService.SpellCheckIssue> onIgnoreWord) {
        super(owner);
        this.issue = issue;
        this.onApplyCorrection = onApplyCorrection;
        this.onIgnoreWord = onIgnoreWord;

        setBackground(new Color(0, 0, 0, 0)); // Fond transparent pour coins arrondis
        initUI();
    }

    private void initUI() {
        JPanel rootPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Fond sombre moderne
                g2.setColor(new Color(32, 33, 36, 245));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                // Bordure douce
                g2.setColor(issue.isGrammar ? new Color(245, 140, 0, 180) : new Color(235, 60, 60, 180));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rootPanel.setOpaque(false);
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
        rootPanel.setBorder(new EmptyBorder(10, 12, 10, 12));

        // 1. En-tête : Badge Catégorie + Titre
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        headerPanel.setOpaque(false);

        String badgeText = issue.isGrammar ? "GRAMMAIRE" : "ORTHOGRAPHE";
        Color badgeColor = issue.isGrammar ? new Color(245, 140, 0) : new Color(230, 45, 45);
        JLabel badgeLabel = new JLabel(" " + badgeText + " ");
        badgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badgeLabel.setForeground(Color.WHITE);
        badgeLabel.setOpaque(true);
        badgeLabel.setBackground(badgeColor);
        badgeLabel.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        headerPanel.add(badgeLabel);

        JLabel wordLabel = new JLabel("« " + issue.originalText + " »");
        wordLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        wordLabel.setForeground(new Color(240, 240, 240));
        headerPanel.add(wordLabel);

        rootPanel.add(headerPanel);
        rootPanel.add(Box.createVerticalStrut(6));

        // Message descriptif
        if (issue.message != null && !issue.message.isBlank()) {
            JLabel descLabel = new JLabel(issue.message);
            descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            descLabel.setForeground(new Color(190, 195, 200));
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            rootPanel.add(descLabel);
            rootPanel.add(Box.createVerticalStrut(8));
        }

        // 2. Suggestions de correction
        List<String> suggestions = issue.suggestions;
        if (suggestions != null && !suggestions.isEmpty()) {
            JLabel suggTitle = new JLabel("Suggestions :");
            suggTitle.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            suggTitle.setForeground(new Color(160, 160, 165));
            suggTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            rootPanel.add(suggTitle);
            rootPanel.add(Box.createVerticalStrut(4));

            for (String s : suggestions) {
                JButton suggBtn = createSuggestionButton(s);
                suggBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
                rootPanel.add(suggBtn);
                rootPanel.add(Box.createVerticalStrut(4));
            }
        } else {
            JLabel noSugg = new JLabel("Aucune suggestion immédiate");
            noSugg.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            noSugg.setForeground(new Color(150, 150, 150));
            noSugg.setAlignmentX(Component.LEFT_ALIGNMENT);
            rootPanel.add(noSugg);
            rootPanel.add(Box.createVerticalStrut(4));
        }

        rootPanel.add(Box.createVerticalStrut(4));

        // 3. Actions secondaires (Ignorer)
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footerPanel.setOpaque(false);
        footerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton ignoreBtn = new JButton("Ignorer");
        ignoreBtn.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        ignoreBtn.setForeground(new Color(160, 160, 160));
        ignoreBtn.setContentAreaFilled(false);
        ignoreBtn.setBorderPainted(false);
        ignoreBtn.setFocusPainted(false);
        ignoreBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        ignoreBtn.addActionListener(e -> {
            if (onIgnoreWord != null) {
                onIgnoreWord.accept(issue);
            }
            fadeOutAndHide();
        });
        footerPanel.add(ignoreBtn);
        rootPanel.add(footerPanel);

        setContentPane(rootPanel);
        pack();
    }

    private JButton createSuggestionButton(String suggestion) {
        JButton btn = new JButton("👉  " + suggestion);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(new Color(255, 255, 255));
        btn.setBackground(new Color(45, 48, 55));
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(75, 80, 95), 1),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(24, 110, 180));
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(50, 160, 240), 1),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
                ));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(45, 48, 55));
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(75, 80, 95), 1),
                    BorderFactory.createEmptyBorder(5, 10, 5, 10)
                ));
            }
        });

        btn.addActionListener(e -> {
            if (onApplyCorrection != null) {
                onApplyCorrection.accept(issue, suggestion);
            }
            fadeOutAndHide();
        });

        return btn;
    }

    /**
     * Fait apparaître le popup avec un effet de fondu progressif (Fade-in).
     */
    public void showProgressive(Point screenLocation) {
        setLocation(screenLocation);
        stopFadeTimer();

        try {
            currentOpacity = 0.0f;
            setOpacity(0.0f);
            setVisible(true);

            fadeTimer = new Timer(15, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    currentOpacity += 0.12f;
                    if (currentOpacity >= 0.98f) {
                        try {
                            setOpacity(1.0f);
                        } catch (Throwable ignored) {}
                        stopFadeTimer();
                    } else {
                        try {
                            setOpacity(currentOpacity);
                        } catch (Throwable ignored) {}
                    }
                }
            });
            fadeTimer.start();
        } catch (Throwable t) {
            // Repli direct si la translucidité n'est pas supportée par l'environnement
            setVisible(true);
        }
    }

    /**
     * Fait disparaître le popup en fondu doux.
     */
    public void fadeOutAndHide() {
        stopFadeTimer();
        try {
            fadeTimer = new Timer(15, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    currentOpacity -= 0.18f;
                    if (currentOpacity <= 0.05f) {
                        stopFadeTimer();
                        setVisible(false);
                        dispose();
                    } else {
                        try {
                            setOpacity(Math.max(0.0f, currentOpacity));
                        } catch (Throwable ignored) {}
                    }
                }
            });
            fadeTimer.start();
        } catch (Throwable t) {
            setVisible(false);
            dispose();
        }
    }

    private void stopFadeTimer() {
        if (fadeTimer != null && fadeTimer.isRunning()) {
            fadeTimer.stop();
        }
    }
}
