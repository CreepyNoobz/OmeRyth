package app.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Simple modal tutorial dialog with Next/Back navigation.
 */
public class TutorialDialog extends JDialog {

    private final String[] pages;
    private int index = 0;
    private final JLabel headerLabel;
    private final JTextArea contentArea;
    private final OmeTheme.OmeButton backBtn;
    private final OmeTheme.OmeButton nextBtn;
    private final OmeTheme.OmeButton closeBtn;

    public TutorialDialog(Frame owner, String[] pages) {
        super(owner, "Tutoriel de démarrage", true);
        this.pages = pages == null ? new String[0] : pages;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(OmeTheme.bg());

        JPanel top = new JPanel(new BorderLayout());
        top.setBackground(OmeTheme.bgRaised());
        top.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, OmeTheme.border()),
                new EmptyBorder(14, 18, 14, 18)
        ));
        JLabel title = new JLabel("Tutoriel de démarrage");
        title.setFont(OmeTheme.fontSans(Font.BOLD, 16));
        title.setForeground(OmeTheme.text());
        headerLabel = new JLabel("", SwingConstants.RIGHT);
        headerLabel.setFont(OmeTheme.fontMono(Font.BOLD, 12));
        headerLabel.setForeground(OmeTheme.accent());
        top.add(title, BorderLayout.WEST);
        top.add(headerLabel, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setOpaque(true);
        contentArea.setBackground(OmeTheme.bgRaised());
        contentArea.setForeground(OmeTheme.text());
        contentArea.setFont(OmeTheme.fontSans(Font.PLAIN, 13.5f));
        contentArea.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setBackground(OmeTheme.bg());
        centerWrap.setBorder(new EmptyBorder(14, 18, 14, 18));
        JScrollPane scroll = new JScrollPane(contentArea);
        OmeTheme.styleScrollPane(scroll);
        scroll.setPreferredSize(new Dimension(540, 240));
        centerWrap.add(scroll, BorderLayout.CENTER);
        add(centerWrap, BorderLayout.CENTER);

        backBtn = OmeTheme.createSecondaryButton("← Précédent");
        nextBtn = OmeTheme.createPrimaryButton("Suivant →");
        closeBtn = OmeTheme.createGhostButton("Fermer");
        add(OmeTheme.createDialogFooter(closeBtn, backBtn, nextBtn), BorderLayout.SOUTH);

        backBtn.addActionListener(e -> {
            if (index > 0) {
                index--;
                updatePage();
            }
        });
        nextBtn.addActionListener(e -> {
            if (index < TutorialDialog.this.pages.length - 1) {
                index++;
                updatePage();
            } else {
                dispose();
            }
        });
        closeBtn.addActionListener(e -> dispose());

        updatePage();
        pack();
        setLocationRelativeTo(owner);
    }

    private void updatePage() {
        int total = Math.max(1, pages.length);
        headerLabel.setText((index + 1) + " / " + total);
        String txt = pages.length > 0 ? pages[index] : "";
        contentArea.setText(txt);
        contentArea.setCaretPosition(0);
        backBtn.setEnabled(index > 0);
        nextBtn.setText(index == pages.length - 1 ? "Terminer" : "Suivant →");
    }
}
