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
                g2.setColor(OmeTheme.bgRaised());
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(issue.isGrammar ? OmeTheme.warning() : OmeTheme.danger());
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rootPanel.setOpaque(false);
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));
        rootPanel.setBorder(new EmptyBorder(12, 14, 12, 14));

        // 1. En-tête : Badge Catégorie + Titre
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerPanel.setOpaque(false);

        String badgeText = issue.isGrammar ? "GRAMMAIRE" : "ORTHOGRAPHE";
        Color badgeColor = issue.isGrammar ? OmeTheme.warning() : OmeTheme.danger();
        JLabel badgeLabel = new JLabel(badgeText);
        badgeLabel.setFont(OmeTheme.fontMono(Font.BOLD, 10));
        badgeLabel.setForeground(badgeColor);
        badgeLabel.setOpaque(true);
        badgeLabel.setBackground(OmeTheme.surface());
        badgeLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(badgeColor, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        headerPanel.add(badgeLabel);

        JLabel wordLabel = new JLabel("« " + issue.originalText + " »");
        wordLabel.setFont(OmeTheme.fontSans(Font.BOLD, 13));
        wordLabel.setForeground(OmeTheme.text());
        headerPanel.add(wordLabel);

        rootPanel.add(headerPanel);
        rootPanel.add(Box.createVerticalStrut(6));

        // Message descriptif
        if (issue.message != null && !issue.message.isBlank()) {
            JLabel descLabel = new JLabel(issue.message);
            descLabel.setFont(OmeTheme.fontSans(Font.PLAIN, 11.5f));
            descLabel.setForeground(OmeTheme.textSecondary());
            descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            rootPanel.add(descLabel);
            rootPanel.add(Box.createVerticalStrut(8));
        }

        // 2. Suggestions de correction
        List<String> suggestions = issue.suggestions;
        if (suggestions != null && !suggestions.isEmpty()) {
            JLabel suggTitle = new JLabel("SUGGESTIONS");
            suggTitle.setFont(OmeTheme.fontMono(Font.BOLD, 10));
            suggTitle.setForeground(OmeTheme.textTertiary());
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
            noSugg.setFont(OmeTheme.fontSans(Font.ITALIC, 11));
            noSugg.setForeground(OmeTheme.textTertiary());
            noSugg.setAlignmentX(Component.LEFT_ALIGNMENT);
            rootPanel.add(noSugg);
            rootPanel.add(Box.createVerticalStrut(4));
        }

        rootPanel.add(Box.createVerticalStrut(4));

        // 3. Actions secondaires (Ignorer)
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footerPanel.setOpaque(false);
        footerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        OmeTheme.OmeButton ignoreBtn = OmeTheme.createGhostButton("Ignorer");
        ignoreBtn.setFont(OmeTheme.fontMono(Font.PLAIN, 10.5f));
        ignoreBtn.setMargin(new Insets(3, 8, 3, 8));
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
        OmeTheme.OmeButton btn = OmeTheme.createSecondaryButton(suggestion);
        btn.setFont(OmeTheme.fontSans(Font.BOLD, 12));
        btn.setMargin(new Insets(5, 10, 5, 10));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

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
