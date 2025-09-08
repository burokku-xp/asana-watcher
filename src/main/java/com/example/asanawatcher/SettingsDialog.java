package com.example.asanawatcher;

import javax.swing.*;
import java.awt.*;

// 設定編集用のモーダルダイアログ。
// Asana の PAT / プロジェクト / セクション / 閾値 / ポーリング / クールダウン / ボードURL を入力。
public class SettingsDialog extends JDialog {
    private final JTextField tfPat = new JTextField();
    private final JTextField tfProject = new JTextField();
    private final JTextField tfSection = new JTextField();
    private final JSpinner spThreshold = new JSpinner(new SpinnerNumberModel(5, 1, 1000, 1));
    private final JSpinner spPolling = new JSpinner(new SpinnerNumberModel(3, 1, 1440, 1));
    private final JSpinner spCooldown = new JSpinner(new SpinnerNumberModel(30, 0, 1440, 1));
    private final JTextField tfBoardUrl = new JTextField();

    private boolean saved = false;

    public SettingsDialog(Window owner, Config cfg) {
        super(owner, "設定", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));

        // 入力フォーム（2カラム：ラベル + 入力フィールド）
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4,4,4,4);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;

        int row = 0;
        addRow(form, c, row++, new JLabel("Personal Access Token"), tfPat);
        addRow(form, c, row++, new JLabel("Project GID"), tfProject);
        addRow(form, c, row++, new JLabel("Section 名"), tfSection);
        addRow(form, c, row++, new JLabel("閾値 (件)"), spThreshold);
        addRow(form, c, row++, new JLabel("取得間隔 (分)"), spPolling);
        addRow(form, c, row++, new JLabel("再通知間隔 (分)"), spCooldown);
        addRow(form, c, row++, new JLabel("ボード URL"), tfBoardUrl);

        add(new JScrollPane(form), BorderLayout.CENTER);

        // ボタン行：キャンセル / 保存
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnSave = new JButton("保存");
        JButton btnCancel = new JButton("キャンセル");
        buttons.add(btnCancel);
        buttons.add(btnSave);
        add(buttons, BorderLayout.SOUTH);

        // load
        tfPat.setText(cfg.personalAccessToken);
        tfProject.setText(cfg.projectGid);
        tfSection.setText(cfg.targetSectionName);
        spThreshold.setValue(cfg.threshold);
        spPolling.setValue(cfg.pollingMinutes);
        spCooldown.setValue(cfg.cooldownMinutes);
        tfBoardUrl.setText(cfg.boardUrl);

        // fit to initial screen width: set columns to shorten fields
        int cols = 24;
        tfPat.setColumns(cols);
        tfProject.setColumns(cols);
        tfSection.setColumns(cols);
        tfBoardUrl.setColumns(cols);

        btnCancel.addActionListener(e -> dispose());
        // 入力検証の上、Config へ反映して保存フラグを立てる
        btnSave.addActionListener(e -> {
            try {
                int th = (Integer) spThreshold.getValue();
                int pol = (Integer) spPolling.getValue();
                int cool = (Integer) spCooldown.getValue();
                if (th < 1) throw new IllegalArgumentException("閾値は1以上");
                if (pol < 1) throw new IllegalArgumentException("ポーリングは1分以上");
                if (cool < 0) throw new IllegalArgumentException("クールダウンは0以上");

                cfg.personalAccessToken = tfPat.getText().trim();
                cfg.projectGid = tfProject.getText().trim();
                cfg.targetSectionName = tfSection.getText().trim();
                cfg.threshold = th;
                cfg.pollingMinutes = pol;
                cfg.cooldownMinutes = cool;
                cfg.boardUrl = tfBoardUrl.getText().trim();
                saved = true;
                dispose();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, ex.getMessage(), "入力エラー", JOptionPane.ERROR_MESSAGE);
            }
        });

        setPreferredSize(new Dimension(500, 380));
        pack();
        setLocationRelativeTo(owner);
    }

    // GridBagLayout による 1 行（ラベル + フィールド）の追加
    private void addRow(JPanel form, GridBagConstraints c, int row, JComponent label, JComponent field) {
        c.gridx = 0; c.gridy = row; c.weightx = 0; c.gridwidth = 1;
        form.add(label, c);
        c.gridx = 1; c.gridy = row; c.weightx = 1; c.gridwidth = 1;
        form.add(field, c);
    }

    public boolean isSaved() { return saved; }
}
