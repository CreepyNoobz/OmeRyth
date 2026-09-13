package app.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;

public class RoleWindow extends JDialog {

    public static final Role NO_ROLE = new Role("Aucun rôle", new Color(170, 170, 170));

    private static final Color[] PRESET_COLORS = {
            new Color(0xFF453A), // Rouge éclatant
            new Color(0xFF9F0A), // Orange vif
            new Color(0xFFD60A), // Jaune doré
            new Color(0x30D158), // Vert menthe
            new Color(0x34C759), // Vert émeraude
            new Color(0x64D2FF), // Bleu ciel
            new Color(0x0A84FF), // Bleu royal
            new Color(0x5E5CE6), // Indigo
            new Color(0xBF5AF2), // Violet
            new Color(0xFF375F), // Rose framboise
            new Color(0x4ECDC4), // Turquoise
            new Color(0xFFFFFF)  // Blanc pur
    };

    private final ArrayList<Role> roles;
    private final DefaultListModel<Role> listModel = new DefaultListModel<>();
    private final JList<Role> roleList;
    private final CardLayout centerCardLayout = new CardLayout();
    private final JPanel centerContainer = new JPanel(centerCardLayout);
    private final JPanel emptyPanel = new JPanel();
    private Role selectedResultRole = null;

    public RoleWindow(Frame parent, ArrayList<Role> roles) {
        this((Window) parent, roles, null, false);
    }

    public RoleWindow(Window parent, ArrayList<Role> roles, Role preselectedRole, boolean pickerMode) {
        super(parent, pickerMode ? "Sélection du Rôle" : "Gestion des Rôles", ModalityType.APPLICATION_MODAL);
        this.roles = roles;

        setSize(480, 560);
        setMinimumSize(new Dimension(440, 500));
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(245, 246, 248));

        // ─── En-tête moderne ───
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setBackground(new Color(30, 30, 30));
        headerPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel titleLabel = new JLabel(pickerMode ? "🎭 Sélectionner un rôle" : "🎭 Gestion des rôles");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel(pickerMode
                ? "Choisissez un rôle pour colorer et identifier la réplique sélectionnée."
                : "Créez et personnalisez les rôles des comédiens pour la bande rythmo.");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(180, 180, 180));

        JPanel headerTextPanel = new JPanel();
        headerTextPanel.setLayout(new BoxLayout(headerTextPanel, BoxLayout.Y_AXIS));
        headerTextPanel.setOpaque(false);
        headerTextPanel.add(titleLabel);
        headerTextPanel.add(Box.createVerticalStrut(4));
        headerTextPanel.add(subtitleLabel);

        headerPanel.add(headerTextPanel, BorderLayout.CENTER);
        add(headerPanel, BorderLayout.NORTH);

        // ─── Liste des rôles ───
        for (Role r : roles) {
            listModel.addElement(r);
        }

        roleList = new JList<>(listModel);
        roleList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        roleList.setCellRenderer(new RoleCardRenderer());
        roleList.setFixedCellHeight(52);
        roleList.setBackground(Color.WHITE);

        if (preselectedRole != null) {
            for (int i = 0; i < listModel.size(); i++) {
                Role r = listModel.get(i);
                if (r.name != null && r.name.equalsIgnoreCase(preselectedRole.name)) {
                    roleList.setSelectedIndex(i);
                    roleList.ensureIndexIsVisible(i);
                    break;
                }
            }
        } else if (!listModel.isEmpty() && pickerMode) {
            roleList.setSelectedIndex(0);
        }

        JScrollPane scroll = new JScrollPane(roleList);
        scroll.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(12, 16, 12, 16),
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1, true)
        ));
        scroll.getViewport().setBackground(Color.WHITE);

        // Panneau état vide
        buildEmptyPanel();

        centerContainer.add(scroll, "LIST");
        centerContainer.add(emptyPanel, "EMPTY");
        updateListVisibility();
        add(centerContainer, BorderLayout.CENTER);

        // ─── Barre d'actions inférieure ───
        JPanel bottomPanel = new JPanel(new BorderLayout(8, 8));
        bottomPanel.setBackground(new Color(245, 246, 248));
        bottomPanel.setBorder(new EmptyBorder(0, 16, 16, 16));

        // Rangée 1 : Gestion des rôles (+ Ajouter, Modifier, Supprimer, Présets)
        JPanel manageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        manageRow.setOpaque(false);

        JButton addBtn = createStyledButton("➕ Ajouter", new Color(0, 120, 215), Color.WHITE);
        JButton editBtn = createStyledButton("✏️ Modifier", new Color(235, 238, 242), new Color(40, 40, 40));
        JButton removeBtn = createStyledButton("🗑️ Supprimer", new Color(235, 238, 242), new Color(180, 40, 40));
        JButton presetBtn = createStyledButton("⚡ Présets...", new Color(235, 238, 242), new Color(40, 40, 40));

        manageRow.add(addBtn);
        manageRow.add(editBtn);
        manageRow.add(removeBtn);
        manageRow.add(presetBtn);
        bottomPanel.add(manageRow, BorderLayout.NORTH);

        // Rangée 2 : Boutons de validation ou fermeture
        JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        actionRow.setOpaque(false);

        if (pickerMode) {
            JButton noRoleBtn = createStyledButton("🚫 Sans rôle", new Color(235, 238, 242), new Color(80, 80, 80));
            JButton chooseBtn = createStyledButton("✔ Choisir ce rôle", new Color(46, 160, 67), Color.WHITE);
            JButton cancelBtn = createStyledButton("Annuler", new Color(235, 238, 242), new Color(40, 40, 40));

            noRoleBtn.addActionListener(e -> {
                selectedResultRole = NO_ROLE;
                dispose();
            });

            chooseBtn.addActionListener(e -> {
                Role chosen = roleList.getSelectedValue();
                if (chosen != null) {
                    selectedResultRole = chosen;
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Veuillez sélectionner un rôle dans la liste ou cliquer sur « Sans rôle ».",
                            "Aucune sélection", JOptionPane.INFORMATION_MESSAGE);
                }
            });

            cancelBtn.addActionListener(e -> {
                selectedResultRole = null;
                dispose();
            });

            actionRow.add(noRoleBtn);
            actionRow.add(cancelBtn);
            actionRow.add(chooseBtn);
        } else {
            JButton closeBtn = createStyledButton("Fermer", new Color(0, 120, 215), Color.WHITE);
            closeBtn.addActionListener(e -> dispose());
            actionRow.add(closeBtn);
        }

        bottomPanel.add(actionRow, BorderLayout.SOUTH);
        add(bottomPanel, BorderLayout.SOUTH);

        // ─── Événements ───
        addBtn.addActionListener(e -> openAddEditDialog(null, -1));

        editBtn.addActionListener(e -> {
            int idx = roleList.getSelectedIndex();
            if (idx >= 0 && idx < roles.size()) {
                openAddEditDialog(roles.get(idx), idx);
            } else {
                JOptionPane.showMessageDialog(this, "Veuillez d'abord sélectionner un rôle à modifier.",
                        "Information", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        removeBtn.addActionListener(e -> {
            int idx = roleList.getSelectedIndex();
            if (idx >= 0 && idx < roles.size()) {
                Role toRemove = roles.get(idx);
                int rep = JOptionPane.showConfirmDialog(this,
                        "Voulez-vous vraiment supprimer le rôle « " + toRemove.name + " » ?",
                        "Confirmation de suppression", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (rep == JOptionPane.YES_OPTION) {
                    roles.remove(idx);
                    listModel.remove(idx);
                    updateListVisibility();
                    if (!listModel.isEmpty()) {
                        roleList.setSelectedIndex(Math.max(0, idx - 1));
                    }
                }
            }
        });

        presetBtn.addActionListener(e -> showPresetMenu(presetBtn));

        roleList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    if (pickerMode) {
                        Role chosen = roleList.getSelectedValue();
                        if (chosen != null) {
                            selectedResultRole = chosen;
                            dispose();
                        }
                    } else {
                        int idx = roleList.getSelectedIndex();
                        if (idx >= 0 && idx < roles.size()) {
                            openAddEditDialog(roles.get(idx), idx);
                        }
                    }
                }
            }
        });

        // Touche Entrée pour valider / Echap pour fermer
        getRootPane().registerKeyboardAction(e -> {
            if (pickerMode) {
                Role chosen = roleList.getSelectedValue();
                if (chosen != null) {
                    selectedResultRole = chosen;
                    dispose();
                }
            } else {
                dispose();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        getRootPane().registerKeyboardAction(e -> {
            selectedResultRole = null;
            dispose();
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private void buildEmptyPanel() {
        emptyPanel.setLayout(new GridBagLayout());
        emptyPanel.setBackground(Color.WHITE);
        emptyPanel.setBorder(BorderFactory.createCompoundBorder(
                new EmptyBorder(12, 16, 12, 16),
                BorderFactory.createLineBorder(new Color(220, 224, 230), 1, true)
        ));

        JPanel centerBox = new JPanel();
        centerBox.setLayout(new BoxLayout(centerBox, BoxLayout.Y_AXIS));
        centerBox.setOpaque(false);

        JLabel iconLabel = new JLabel("🎭");
        iconLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 40));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel emptyTitle = new JLabel("Aucun rôle défini");
        emptyTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        emptyTitle.setForeground(new Color(60, 60, 60));
        emptyTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel emptyDesc = new JLabel("<html><center>Cliquez sur <b>➕ Ajouter</b> ou chargez un <b>⚡ Préset</b><br>pour configurer vos personnages.</center></html>");
        emptyDesc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        emptyDesc.setForeground(new Color(120, 120, 120));
        emptyDesc.setAlignmentX(Component.CENTER_ALIGNMENT);

        centerBox.add(iconLabel);
        centerBox.add(Box.createVerticalStrut(8));
        centerBox.add(emptyTitle);
        centerBox.add(Box.createVerticalStrut(6));
        centerBox.add(emptyDesc);

        emptyPanel.add(centerBox);
    }

    private void updateListVisibility() {
        if (listModel.isEmpty()) {
            centerCardLayout.show(centerContainer, "EMPTY");
        } else {
            centerCardLayout.show(centerContainer, "LIST");
        }
    }

    private void showPresetMenu(Component invoker) {
        JPopupMenu popup = new JPopupMenu();
        for (String presetName : RolePresets.getPresetNames()) {
            if ("Vide".equalsIgnoreCase(presetName)) continue;
            JMenuItem item = new JMenuItem(presetName);
            item.addActionListener(ev -> {
                ArrayList<Role> presetRoles = RolePresets.createRolesFromPreset(presetName);
                if (presetRoles.isEmpty()) return;

                int option = JOptionPane.showOptionDialog(this,
                        "Comment souhaitez-vous appliquer le préset « " + presetName + " » ?",
                        "Charger le préset",
                        JOptionPane.YES_NO_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE,
                        null,
                        new String[]{"Remplacer les rôles actuels", "Ajouter aux rôles existants", "Annuler"},
                        "Ajouter aux rôles existants");

                if (option == 0) { // Remplacer
                    roles.clear();
                    listModel.clear();
                    for (Role r : presetRoles) {
                        roles.add(r);
                        listModel.addElement(r);
                    }
                } else if (option == 1) { // Ajouter
                    for (Role r : presetRoles) {
                        roles.add(r);
                        listModel.addElement(r);
                    }
                }
                updateListVisibility();
                if (!listModel.isEmpty()) roleList.setSelectedIndex(0);
            });
            popup.add(item);
        }
        popup.show(invoker, 0, invoker.getHeight());
    }

    // ─── Boîte de dialogue d'Ajout / Modification ───
    private void openAddEditDialog(Role existing, int idx) {
        boolean isEdit = (existing != null && idx >= 0);
        JDialog dlg = new JDialog(this, isEdit ? "Modifier le rôle" : "Nouveau rôle", true);
        dlg.setSize(440, 420);
        dlg.setMinimumSize(new Dimension(420, 400));
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());
        dlg.getContentPane().setBackground(new Color(245, 246, 248));

        // En-tête dialog
        JPanel dlgHeader = new JPanel(new BorderLayout());
        dlgHeader.setBackground(new Color(35, 35, 35));
        dlgHeader.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel dlgTitle = new JLabel(isEdit ? "✏️ Modifier le rôle" : "➕ Ajouter un rôle");
        dlgTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        dlgTitle.setForeground(Color.WHITE);
        dlgHeader.add(dlgTitle, BorderLayout.WEST);
        dlg.add(dlgHeader, BorderLayout.NORTH);

        // Corps dialog
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(14, 16, 14, 16));

        // Champ Nom
        JLabel nameLbl = new JLabel("Nom du personnage / rôle :");
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        nameLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameLbl);
        body.add(Box.createVerticalStrut(6));

        JTextField nameField = new JTextField(isEdit ? existing.name : "", 20);
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        nameField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 205, 210), 1, true),
                new EmptyBorder(6, 8, 6, 8)
        ));
        nameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        nameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(nameField);
        body.add(Box.createVerticalStrut(14));

        // Champ Couleur + Palette rapide
        JLabel colorLbl = new JLabel("Couleur associée :");
        colorLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        colorLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(colorLbl);
        body.add(Box.createVerticalStrut(6));

        Color initColor = (isEdit && existing.color != null) ? existing.color : PRESET_COLORS[0];
        final Color[] chosen = { initColor };

        // Zone d'aperçu dynamique de la réplique
        JLabel previewLbl = new JLabel("Aperçu sur la bande rythmo :");
        previewLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        previewLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(previewLbl);
        body.add(Box.createVerticalStrut(6));

        LiveRythmoPreviewBox previewBox = new LiveRythmoPreviewBox(
                nameField.getText().isBlank() ? "Exemple" : nameField.getText().trim(),
                chosen[0]
        );
        previewBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        previewBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(previewBox);

        ColorPreviewButton customColorBtn = new ColorPreviewButton(chosen[0]);
        customColorBtn.setPreferredSize(new Dimension(34, 26));

        JLabel hexLabel = new JLabel(toHex(chosen[0]));
        hexLabel.setFont(new Font("Monospaced", Font.BOLD, 12));
        hexLabel.setForeground(new Color(100, 100, 100));

        Runnable updatePreviewAction = () -> {
            String txt = nameField.getText().trim();
            previewBox.updatePreview(txt.isEmpty() ? "Rôle" : txt, chosen[0]);
            customColorBtn.setPreviewColor(chosen[0]);
            hexLabel.setText(toHex(chosen[0]));
            dlg.repaint();
        };

        // Palette de pastilles de couleurs
        JPanel paletteGrid = new JPanel(new GridLayout(2, 6, 6, 6));
        paletteGrid.setOpaque(false);
        paletteGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        paletteGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        ColorSwatchButton[] swatchButtons = new ColorSwatchButton[PRESET_COLORS.length];
        for (int i = 0; i < PRESET_COLORS.length; i++) {
            Color c = PRESET_COLORS[i];
            ColorSwatchButton sb = new ColorSwatchButton(c);
            swatchButtons[i] = sb;
            if (isSameColor(c, chosen[0])) sb.setSelected(true);

            final int buttonIdx = i;
            sb.addActionListener(ev -> {
                chosen[0] = c;
                for (ColorSwatchButton b : swatchButtons) b.setSelected(false);
                swatchButtons[buttonIdx].setSelected(true);
                updatePreviewAction.run();
            });
            paletteGrid.add(sb);
        }
        body.add(paletteGrid);
        body.add(Box.createVerticalStrut(10));

        // Bouton couleur personnalisée
        JPanel customColorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        customColorRow.setOpaque(false);
        customColorRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton chooseMoreColor = createStyledButton("🎨 Autre couleur...", new Color(235, 238, 242), new Color(40, 40, 40));

        chooseMoreColor.addActionListener(ev -> {
            Color c = JColorChooser.showDialog(dlg, "Choisir une couleur personnalisée", chosen[0]);
            if (c != null) {
                chosen[0] = c;
                for (ColorSwatchButton b : swatchButtons) {
                    b.setSelected(isSameColor(b.color, c));
                }
                updatePreviewAction.run();
            }
        });

        customColorRow.add(customColorBtn);
        customColorRow.add(chooseMoreColor);
        customColorRow.add(hexLabel);
        body.add(customColorRow);
        body.add(Box.createVerticalStrut(14));

        // Mettre à jour l'aperçu en direct à chaque frappe
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { updatePreviewAction.run(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { updatePreviewAction.run(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { updatePreviewAction.run(); }
        });

        dlg.add(body, BorderLayout.CENTER);

        // Actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        actions.setBackground(new Color(245, 246, 248));
        JButton okBtn = createStyledButton("✔ Enregistrer", new Color(0, 120, 215), Color.WHITE);
        JButton cancelBtn = createStyledButton("Annuler", new Color(235, 238, 242), new Color(40, 40, 40));

        actions.add(cancelBtn);
        actions.add(okBtn);
        dlg.add(actions, BorderLayout.SOUTH);

        okBtn.addActionListener(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(dlg, "Veuillez entrer un nom pour le rôle.",
                        "Nom requis", JOptionPane.WARNING_MESSAGE);
                nameField.requestFocus();
                return;
            }
            if (!isEdit) {
                Role r = new Role(name, chosen[0]);
                roles.add(r);
                listModel.addElement(r);
                roleList.setSelectedValue(r, true);
            } else {
                roles.get(idx).name = name;
                roles.get(idx).color = chosen[0];
                listModel.set(idx, roles.get(idx));
                roleList.setSelectedIndex(idx);
            }
            updateListVisibility();
            dlg.dispose();
        });

        nameField.addActionListener(okBtn.getActionListeners()[0]);
        cancelBtn.addActionListener(e -> dlg.dispose());

        dlg.setVisible(true);
    }

    // ─── Static Picker Dialog ───
    public static Role pickRole(Component parent, ArrayList<Role> roles) {
        return pickRole(parent, roles, null);
    }

    public static Role pickRole(Component parent, ArrayList<Role> roles, Role preselectedRole) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return (roles != null && !roles.isEmpty()) ? roles.get(0) : new Role("Défaut", Color.WHITE);
        }
        Window parentWindow = (parent instanceof Window) ? (Window) parent : SwingUtilities.getWindowAncestor(parent);
        RoleWindow dlg = new RoleWindow(parentWindow, roles, preselectedRole, true);
        dlg.setVisible(true);
        return dlg.selectedResultRole;
    }

    // ─── Rendu personnalisé d'une carte de rôle ───
    private static class RoleCardRenderer extends JPanel implements ListCellRenderer<Role> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel hexLabel = new JLabel();
        private final ColorCircleIcon colorIcon = new ColorCircleIcon(Color.WHITE, 22);
        private final JLabel iconLabel = new JLabel(colorIcon);
        private final LiveRoleBadge sampleBadge = new LiveRoleBadge();

        RoleCardRenderer() {
            setLayout(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(6, 12, 6, 12));
            setOpaque(true);

            JPanel textCol = new JPanel();
            textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));
            textCol.setOpaque(false);

            nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            hexLabel.setFont(new Font("Monospaced", Font.PLAIN, 11));
            hexLabel.setForeground(new Color(120, 120, 120));

            textCol.add(nameLabel);
            textCol.add(Box.createVerticalStrut(2));
            textCol.add(hexLabel);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            left.setOpaque(false);
            left.add(iconLabel);
            left.add(Box.createHorizontalStrut(10));
            left.add(textCol);

            add(left, BorderLayout.WEST);
            add(sampleBadge, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Role> list, Role value,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            if (value != null) {
                nameLabel.setText(value.name);
                Color c = (value.color != null) ? value.color : Color.WHITE;
                colorIcon.setColor(c);
                hexLabel.setText(toHex(c));
                sampleBadge.setBadge(value.name, c);
            }

            if (isSelected) {
                setBackground(new Color(225, 238, 255));
                nameLabel.setForeground(new Color(0, 80, 180));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(0, 120, 215)),
                        new EmptyBorder(6, 8, 6, 12)
                ));
            } else {
                setBackground(index % 2 == 0 ? Color.WHITE : new Color(250, 251, 253));
                nameLabel.setForeground(new Color(30, 30, 30));
                setBorder(new EmptyBorder(6, 12, 6, 12));
            }

            return this;
        }
    }

    // ─── Badge d'aperçu d'un rôle ───
    private static class LiveRoleBadge extends JPanel {
        private String name = "Rôle";
        private Color color = Color.WHITE;

        LiveRoleBadge() {
            setOpaque(false);
            setPreferredSize(new Dimension(140, 30));
        }

        void setBadge(String name, Color color) {
            this.name = name != null ? name : "";
            this.color = color != null ? color : Color.WHITE;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int h = 24;
            int y = (getHeight() - h) / 2;
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int w = Math.min(getWidth() - 4, fm.stringWidth(name) + 16);
            int x = getWidth() - w - 2;

            g2.setColor(color);
            g2.fillRoundRect(x, y, w, h, 12, 12);

            g2.setColor(new Color(0, 0, 0, 40));
            g2.drawRoundRect(x, y, w, h, 12, 12);

            boolean isDark = isDarkColor(color);
            g2.setColor(isDark ? Color.WHITE : Color.BLACK);
            int textX = x + (w - fm.stringWidth(name)) / 2;
            int textY = y + fm.getAscent() + (h - fm.getHeight()) / 2;
            g2.drawString(name, textX, textY);

            g2.dispose();
        }
    }

    // ─── Boîte d'aperçu dynamique de bande rythmo ───
    private static class LiveRythmoPreviewBox extends JPanel {
        private String roleName;
        private Color roleColor;

        LiveRythmoPreviewBox(String roleName, Color roleColor) {
            this.roleName = roleName;
            this.roleColor = roleColor;
            setPreferredSize(new Dimension(380, 42));
            setBackground(new Color(35, 35, 35));
            setBorder(BorderFactory.createLineBorder(new Color(60, 60, 60), 1, true));
        }

        void updatePreview(String roleName, Color roleColor) {
            this.roleName = roleName;
            this.roleColor = roleColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int badgeH = 22;
            int badgeY = (getHeight() - badgeH) / 2;
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            FontMetrics lfm = g2.getFontMetrics();
            int badgeW = lfm.stringWidth(roleName) + 16;
            int badgeX = 10;

            // Badge du rôle
            g2.setColor(roleColor);
            g2.fillRoundRect(badgeX, badgeY, badgeW, badgeH, 10, 10);
            g2.setColor(isDarkColor(roleColor) ? Color.WHITE : Color.BLACK);
            int labelY = badgeY + lfm.getAscent() + (badgeH - lfm.getHeight()) / 2;
            g2.drawString(roleName, badgeX + 8, labelY);

            // Texte de la réplique
            g2.setFont(new Font("Arial", Font.BOLD, 14));
            FontMetrics tfm = g2.getFontMetrics();
            g2.setColor(roleColor);
            int textX = badgeX + badgeW + 12;
            int textY = (getHeight() + tfm.getAscent() - tfm.getDescent()) / 2;
            g2.drawString("Exemple de réplique sur la rythmo", textX, textY);

            g2.dispose();
        }
    }

    // ─── Pastille de couleur cliquable ───
    private static class ColorSwatchButton extends JButton {
        final Color color;
        private boolean selected = false;

        ColorSwatchButton(Color color) {
            this.color = color;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(30, 30));
        }

        @Override
        public void setSelected(boolean selected) {
            this.selected = selected;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = Math.min(getWidth(), getHeight()) - 4;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            g2.setColor(color);
            g2.fillRoundRect(x, y, size, size, 8, 8);

            if (selected) {
                g2.setColor(new Color(0, 120, 215));
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawRoundRect(x - 1, y - 1, size + 2, size + 2, 10, 10);

                // Checkmark
                g2.setColor(isDarkColor(color) ? Color.WHITE : Color.BLACK);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("✓", x + (size - fm.stringWidth("✓")) / 2, y + fm.getAscent() + (size - fm.getHeight()) / 2);
            } else {
                g2.setColor(new Color(0, 0, 0, 50));
                g2.drawRoundRect(x, y, size, size, 8, 8);
            }

            g2.dispose();
        }
    }

    private static class ColorPreviewButton extends JButton {
        private Color previewColor;

        ColorPreviewButton(Color previewColor) {
            this.previewColor = previewColor;
            setFocusPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1, true));
        }

        void setPreviewColor(Color previewColor) {
            this.previewColor = previewColor;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(previewColor != null ? previewColor : Color.WHITE);
            g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 6, 6);
            g2.setColor(new Color(0, 0, 0, 40));
            g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class ColorCircleIcon implements Icon {
        private Color color;
        private final int size;

        ColorCircleIcon(Color c, int size) {
            this.color = c;
            this.size = size;
        }

        void setColor(Color c) {
            this.color = c;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color != null ? color : Color.WHITE);
            g2.fillOval(x, y, size, size);
            g2.setColor(new Color(0, 0, 0, 60));
            g2.drawOval(x, y, size, size);
            g2.dispose();
        }

        @Override public int getIconWidth()  { return size; }
        @Override public int getIconHeight() { return size; }
    }

    private static JButton createStyledButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(bg.getRed() > 30 ? bg.getRed() - 25 : 0,
                        bg.getGreen() > 30 ? bg.getGreen() - 25 : 0,
                        bg.getBlue() > 30 ? bg.getBlue() - 25 : 0), 1, true),
                new EmptyBorder(6, 12, 6, 12)
        ));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private static boolean isDarkColor(Color c) {
        if (c == null) return false;
        double luma = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return luma < 145;
    }

    private static boolean isSameColor(Color c1, Color c2) {
        if (c1 == null || c2 == null) return false;
        return c1.getRed() == c2.getRed() && c1.getGreen() == c2.getGreen() && c1.getBlue() == c2.getBlue();
    }

    private static String toHex(Color c) {
        if (c == null) return "#FFFFFF";
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}

