package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Simple modal tutorial dialog with Next/Back navigation.
 */
public class TutorialDialog extends JDialog {

    private final String[] pages;
    private int index = 0;
    private final JLabel headerLabel;
    private final JTextArea contentArea;
    private final JButton backBtn;
    private final JButton nextBtn;
    private final JButton closeBtn;

    public TutorialDialog(Frame owner, String[] pages) {
        super(owner, "Tutoriel de démarrage", true);
        this.pages = pages == null ? new String[0] : pages;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));

        headerLabel = new JLabel("", SwingConstants.CENTER);
        headerLabel.setFont(headerLabel.getFont().deriveFont(Font.BOLD, 14f));
        add(headerLabel, BorderLayout.NORTH);

        contentArea = new JTextArea();
        contentArea.setEditable(false);
        contentArea.setLineWrap(true);
        contentArea.setWrapStyleWord(true);
        contentArea.setOpaque(true);
        contentArea.setBackground(Color.WHITE);
        contentArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane scroll = new JScrollPane(contentArea);
        scroll.setPreferredSize(new Dimension(520, 220));
        add(scroll, BorderLayout.CENTER);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        backBtn = new JButton("Précédent");
        nextBtn = new JButton("Suivant");
        closeBtn = new JButton("Fermer");
        btnBar.add(backBtn);
        btnBar.add(nextBtn);
        btnBar.add(closeBtn);
        add(btnBar, BorderLayout.SOUTH);

        backBtn.addActionListener(new ActionListener() {
            @Override
            // Navigate to the previous tutorial page.
            public void actionPerformed(ActionEvent e) {
                if (index > 0) {
                    index--;
                    updatePage();
                }
            }
        });

        nextBtn.addActionListener(new ActionListener() {
            @Override
            // Navigate to the next page or finish the tutorial.
            public void actionPerformed(ActionEvent e) {
                if (index < TutorialDialog.this.pages.length - 1) {
                    index++;
                    updatePage();
                } else {
                    dispose();
                }
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
        nextBtn.setText(index == pages.length - 1 ? "Terminer" : "Suivant");
    }
}
