package com.example.asanawatcher;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

// ログファイルの内容を簡易表示するダイアログ。
// 一定間隔で末尾を自動更新し、手動更新/自動更新の停止・再開も可能。
public class LogViewerDialog extends JDialog {
    private final JTextArea textArea = new JTextArea();
    private final Path logFile;
    private final Timer timer;

    public LogViewerDialog(Window owner, Path logFile) {
        super(owner, "ログビューア", ModalityType.MODELESS);
        this.logFile = logFile;
        setDefaultCloseOperation(HIDE_ON_CLOSE);
        setLayout(new BorderLayout(4,4));

        textArea.setEditable(false);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        add(new JScrollPane(textArea), BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refresh = new JButton("更新");
        JButton pause = new JButton("自動更新 停止");
        controls.add(refresh);
        controls.add(pause);
        add(controls, BorderLayout.SOUTH);

        // 「更新」クリックで即時再読込
        refresh.addActionListener(e -> loadLogTail());

        timer = new Timer(2000, e -> loadLogTail());
        timer.setRepeats(true);
        timer.start();

        // 自動更新の停止/再開トグル
        pause.addActionListener(e -> {
            if (timer.isRunning()) {
                timer.stop();
                pause.setText("自動更新 再開");
            } else {
                timer.start();
                pause.setText("自動更新 停止");
            }
        });

        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) { loadLogTail(); }
        });

        setPreferredSize(new Dimension(720, 420));
        pack();
        setLocationRelativeTo(owner);
    }

    // ログファイルの末尾（最大1000行）を読み込み、テキストエリアに表示
    private void loadLogTail() {
        try {
            if (!Files.exists(logFile)) {
                textArea.setText("(ログファイルなし)\n" + logFile);
                return;
            }
            List<String> all = Files.readAllLines(logFile, StandardCharsets.UTF_8);
            int n = all.size();
            int from = Math.max(0, n - 1000); // show last 1000 lines
            StringBuilder sb = new StringBuilder();
            for (int i = from; i < n; i++) sb.append(all.get(i)).append('\n');
            textArea.setText(sb.toString());
            textArea.setCaretPosition(textArea.getDocument().getLength());
        } catch (IOException ex) {
            textArea.setText("ログ読み込みに失敗: " + ex.getMessage());
        }
    }
}
