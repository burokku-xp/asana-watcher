package com.example.asanawatcher;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;

// システムトレイ常駐アイコンとメニューを提供
public class TrayApp {
    private final Image iconImage;
    private final ScheduledExecutorService scheduler;
    private final Runnable checkNow;
    private final Runnable openSettings;
    private final Runnable openLogs;
    private final Runnable testNotify;

    public TrayApp(ScheduledExecutorService scheduler, Runnable checkNow, Runnable openSettings, Runnable openLogs, Runnable testNotify) {
        this.scheduler = scheduler;
        this.checkNow = checkNow;
        this.openSettings = openSettings;
        this.openLogs = openLogs;
        this.testNotify = testNotify;
        this.iconImage = createIcon();
    }

    public Image getIconImage() { return iconImage; }

    // システムトレイにアイコンとメニューを追加
    public void install() throws AWTException {
        if (!SystemTray.isSupported()) return;
        SystemTray tray = SystemTray.getSystemTray();
        PopupMenu menu = new PopupMenu();

        MenuItem miCheck = new MenuItem("今すぐチェック");
        miCheck.addActionListener((ActionEvent e) -> checkNow.run());
        menu.add(miCheck);

        MenuItem miSettings = new MenuItem("設定を開く");
        miSettings.addActionListener((ActionEvent e) -> openSettings.run());
        menu.add(miSettings);

        MenuItem miLogs = new MenuItem("ログを表示");
        miLogs.addActionListener((ActionEvent e) -> openLogs.run());
        menu.add(miLogs);

        MenuItem miTest = new MenuItem("テスト通知");
        miTest.addActionListener((ActionEvent e) -> testNotify.run());
        menu.add(miTest);

        menu.addSeparator();
        MenuItem miExit = new MenuItem("終了");
        miExit.addActionListener(e -> {
            // スケジューラを停止してプロセスを終了
            scheduler.shutdownNow();
            System.exit(0);
        });
        menu.add(miExit);

        TrayIcon trayIcon = new TrayIcon(iconImage, "AsanaWatcher", menu);
        trayIcon.setImageAutoSize(true);
        tray.add(trayIcon);
    }

    // 実行時に埋め込みや外部アイコンが無ければ、簡易アイコンを生成
    private Image createIcon() {
        return createDefaultIcon();
    }

    public static Image createDefaultIcon() {
        int s = 16;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(0x2D, 0x9C, 0xDB));
        g.fillRect(0,0,s,s);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 12));
        g.drawString("A", 4, 12);
        g.dispose();
        return img;
    }
}
