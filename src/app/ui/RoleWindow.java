package app.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class RoleWindow extends JDialog {

    private final ArrayList<Role> roles;
    private final DefaultListModel<Role> listModel = new DefaultListModel<>();
    private final JList<Role> roleList;

    public RoleWindow(Frame parent, ArrayList<Role> roles) {
        super(parent, "Gestion des Rôles", true);
        this.roles = roles;
        setSize(360, 420);
        setMinimumSize(new Dimension(360, 420));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));

        for (Role r : roles) listModel.addElement(r);

        roleList = new JList<>(listModel);
        roleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roleList.setCellRenderer(new RoleCellRenderer());
        JScrollPane scroll = new JScrollPane(roleList);
        add(scroll, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(1, 3, 4, 0));
        JButton addBtn    = new JButton("Ajouter");
        JButton editBtn   = new JButton("Modifier");
        JButton removeBtn = new JButton("Supprimer");
        btnPanel.add(addBtn);
        btnPanel.add(editBtn);
        btnPanel.add(removeBtn);
        add(btnPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> openAddEditDialog(null, -1));

        editBtn.addActionListener(e -> {
            int idx = roleList.getSelectedIndex();
            if (idx >= 0) openAddEditDialog(roles.get(idx), idx);
        });

        roleList.addMouseListener(new MouseAdapter() {
            @Override
            // Double-click opens the edit dialog for the selected role.
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int idx = roleList.getSelectedIndex();
                    if (idx >= 0) openAddEditDialog(roles.get(idx), idx);
                }
            }
        });

        removeBtn.addActionListener(e -> {
            int idx = roleList.getSelectedIndex();
            if (idx >= 0) { roles.remove(idx); listModel.remove(idx); }
        });
    }

    private void openAddEditDialog(Role existing, int idx) {
        JDialog dlg = new JDialog(this, existing == null ? "Ajouter un rôle" : "Modifier le rôle", true);
        dlg.setSize(320, 160);
        dlg.setMinimumSize(new Dimension(320, 160));
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JTextField nameField = new JTextField(existing != null ? existing.name : "", 16);
        Color initColor = existing != null ? existing.color : Color.WHITE;
        ColorPreviewButton colorBtn = new ColorPreviewButton(initColor);
        colorBtn.setPreferredSize(new Dimension(40, 24));
        final Color[] chosen = { initColor };

        colorBtn.addActionListener(e -> {
            Color c = JColorChooser.showDialog(dlg, "Choisir une couleur", chosen[0]);
            if (c != null) {
                chosen[0] = c;
                colorBtn.setPreviewColor(c);
            }
        });

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; form.add(new JLabel("Nom :"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; form.add(nameField, gbc);
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; form.add(new JLabel("Couleur :"), gbc);
        gbc.gridx = 1; gbc.weightx = 1; form.add(colorBtn, gbc);
        dlg.add(form, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Annuler");
        actions.add(ok); actions.add(cancel);
        dlg.add(actions, BorderLayout.SOUTH);

        ok.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { nameField.requestFocus(); return; }
            if (idx < 0) {
                Role r = new Role(name, chosen[0]);
                roles.add(r);
                listModel.addElement(r);
            } else {
                roles.get(idx).name = name;
                roles.get(idx).color = chosen[0];
                listModel.set(idx, roles.get(idx));
            }
            dlg.dispose();
        });
        nameField.addActionListener(ok.getActionListeners()[0]);
        cancel.addActionListener(e -> dlg.dispose());
        dlg.setVisible(true);
    }

    // ─── Custom renderer: colored circle + name ───────────────────────────
    private static class RoleCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, isSelected, cellHasFocus);
            if (value instanceof Role) {
                Role r = (Role) value;
                label.setText(r.name);
                label.setIcon(new ColorCircleIcon(r.color, 14));
                label.setIconTextGap(8);
            }
            return label;
        }
    }

    private static class ColorPreviewButton extends JButton {
        private Color previewColor;

        ColorPreviewButton(Color previewColor) {
            this.previewColor = previewColor;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180)));
        }

        void setPreviewColor(Color previewColor) {
            this.previewColor = previewColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(previewColor != null ? previewColor : Color.WHITE);
            g2.fillRect(1, 1, getWidth() - 2, getHeight() - 2);
            g2.dispose();
            super.paintComponent(g);
        }
    }

        private static class ColorCircleIcon implements javax.swing.Icon {
        private final Color color;
        private final int size;
        ColorCircleIcon(Color c, int size) { this.color = c; this.size = size; }
        @Override
        // Paint the filled circle used as the role color preview icon.
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(color);
            g.fillOval(x, y, size, size);
            g.setColor(Color.DARK_GRAY);
            g.drawOval(x, y, size, size);
        }
        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }
    }

    // ─── Static pick dialog (same capabilities as Role panel) ────────
    public static Role pickRole(Component parent, ArrayList<Role> roles) {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(parent),
                "Choisir un rôle", Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(380, 440);
        dlg.setMinimumSize(new Dimension(380, 440));
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout(8, 8));
        dlg.getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));

        DefaultListModel<Role> model = new DefaultListModel<>();
        for (Role r : roles) model.addElement(r);
        JList<Role> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new RoleCellRenderer());
        if (!model.isEmpty()) list.setSelectedIndex(0);
        dlg.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(6, 6));

        JPanel editRow = new JPanel(new GridLayout(1, 3, 4, 0));
        JButton addBtn = new JButton("Ajouter");
        JButton editBtn = new JButton("Modifier");
        JButton removeBtn = new JButton("Supprimer");
        editRow.add(addBtn);
        editRow.add(editBtn);
        editRow.add(removeBtn);

        addBtn.addActionListener(e -> {
            JDialog roleDlg = new JDialog(dlg, "Ajouter un rôle", true);
            roleDlg.setSize(320, 160);
            roleDlg.setMinimumSize(new Dimension(320, 160));
            roleDlg.setLocationRelativeTo(dlg);
            roleDlg.setLayout(new BorderLayout(8, 8));
            roleDlg.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField nameField = new JTextField("", 16);
            final Color[] chosen = { Color.WHITE };
            ColorPreviewButton colorBtn = new ColorPreviewButton(chosen[0]);
            colorBtn.setPreferredSize(new Dimension(40, 24));
            colorBtn.addActionListener(ae -> {
                Color c = JColorChooser.showDialog(roleDlg, "Choisir une couleur", chosen[0]);
                if (c != null) {
                    chosen[0] = c;
                    colorBtn.setPreviewColor(c);
                }
            });

            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; form.add(new JLabel("Nom :"), gbc);
            gbc.gridx = 1; gbc.weightx = 1; form.add(nameField, gbc);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; form.add(new JLabel("Couleur :"), gbc);
            gbc.gridx = 1; gbc.weightx = 1; form.add(colorBtn, gbc);
            roleDlg.add(form, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Annuler");
            actions.add(ok);
            actions.add(cancel);
            roleDlg.add(actions, BorderLayout.SOUTH);

            ok.addActionListener(ae -> {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    nameField.requestFocus();
                    return;
                }
                Role r = new Role(name, chosen[0]);
                roles.add(r);
                model.addElement(r);
                list.setSelectedValue(r, true);
                roleDlg.dispose();
            });
            cancel.addActionListener(ae -> roleDlg.dispose());
            roleDlg.setVisible(true);
        });

        editBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) return;
            Role existing = roles.get(idx);

            JDialog roleDlg = new JDialog(dlg, "Modifier le rôle", true);
            roleDlg.setSize(320, 160);
            roleDlg.setMinimumSize(new Dimension(320, 160));
            roleDlg.setLocationRelativeTo(dlg);
            roleDlg.setLayout(new BorderLayout(8, 8));
            roleDlg.getRootPane().setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel form = new JPanel(new GridBagLayout());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            JTextField nameField = new JTextField(existing.name, 16);
            final Color[] chosen = { existing.color };
            ColorPreviewButton colorBtn = new ColorPreviewButton(chosen[0]);
            colorBtn.setPreferredSize(new Dimension(40, 24));
            colorBtn.addActionListener(ae -> {
                Color c = JColorChooser.showDialog(roleDlg, "Choisir une couleur", chosen[0]);
                if (c != null) {
                    chosen[0] = c;
                    colorBtn.setPreviewColor(c);
                }
            });

            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0; form.add(new JLabel("Nom :"), gbc);
            gbc.gridx = 1; gbc.weightx = 1; form.add(nameField, gbc);
            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0; form.add(new JLabel("Couleur :"), gbc);
            gbc.gridx = 1; gbc.weightx = 1; form.add(colorBtn, gbc);
            roleDlg.add(form, BorderLayout.CENTER);

            JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton ok = new JButton("OK");
            JButton cancel = new JButton("Annuler");
            actions.add(ok);
            actions.add(cancel);
            roleDlg.add(actions, BorderLayout.SOUTH);

            ok.addActionListener(ae -> {
                String name = nameField.getText().trim();
                if (name.isEmpty()) {
                    nameField.requestFocus();
                    return;
                }
                existing.name = name;
                existing.color = chosen[0];
                model.set(idx, existing);
                list.setSelectedIndex(idx);
                roleDlg.dispose();
            });
            cancel.addActionListener(ae -> roleDlg.dispose());
            roleDlg.setVisible(true);
        });

        removeBtn.addActionListener(e -> {
            int idx = list.getSelectedIndex();
            if (idx < 0) return;
            roles.remove(idx);
            model.remove(idx);
            if (!model.isEmpty()) list.setSelectedIndex(Math.min(idx, model.size() - 1));
        });

        final Role[] result = { null };
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton choose = new JButton("Choisir");
        JButton cancel = new JButton("Annuler");
        btnRow.add(choose);
        btnRow.add(cancel);

        bottom.add(editRow, BorderLayout.NORTH);
        bottom.add(btnRow, BorderLayout.SOUTH);
        dlg.add(bottom, BorderLayout.SOUTH);

        choose.addActionListener(e -> {
            result[0] = list.getSelectedValue();
            dlg.dispose();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override
            // Double-click selects the role and closes the picker dialog.
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    result[0] = list.getSelectedValue();
                    dlg.dispose();
                }
            }
        });
        cancel.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
        return result[0];
    }
}

