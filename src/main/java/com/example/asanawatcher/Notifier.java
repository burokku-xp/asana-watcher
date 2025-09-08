package com.example.asanawatcher;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// 通知表示の実装。
// - Windows では SnoreToast.exe を優先（AUMID の登録も試行）
// - 見つからなければ AWT のトレイバルーンにフォールバック
public class Notifier {
    private final Path appBaseDir;
    private final String snoreToastPath;
    private final String boardUrl;
    private final Image trayImage;
    private static volatile boolean appIdInstallAttempted = false;

    public Notifier(Path appBaseDir, String snoreToastPath, String boardUrl, Image trayImage) {
        this.appBaseDir = appBaseDir;
        this.snoreToastPath = snoreToastPath;
        this.boardUrl = boardUrl;
        this.trayImage = trayImage != null ? trayImage : TrayApp.createDefaultIcon();
    }

    // 閾値超過の通知
    public void notifyThreshold(int count, String sectionName, int threshold) throws IOException, InterruptedException {
        File exe = resolveSnoreToast();
        if (exe != null && exe.exists()) {
            maybeInstallAppId(exe);
            List<String> cmd = new ArrayList<>();
            cmd.add(exe.getAbsolutePath());
            cmd.add("-appID");
            cmd.add("AsanaWatcher");
            cmd.add("-t");
            cmd.add(sectionName + "が" + threshold + "件以上");
            cmd.add("-m");
            cmd.add("現在 " + count + " 件");
            cmd.add("-d");
            cmd.add("long");
            // If an icon file exists next to exe, prefer app.ico, then Asana.ico
            File icon = new File(exe.getParentFile(), "app.ico");
            if (!icon.exists()) {
                File alt = new File(exe.getParentFile(), "Asana.ico");
                if (alt.exists()) icon = alt; else icon = null;
            }
            if (icon != null && icon.exists()) {
                cmd.add("-p");
                cmd.add(icon.getAbsolutePath());
            }
            // SnoreToast.exe をプロセスとして起動
            System.out.println("Executing SnoreToast with args: " + String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int code = process.waitFor();
            System.out.println("SnoreToast exit=" + code + " output=" + output.trim());
            return;
        }

        // Fallback: AWT tray balloon (no click handling)
        // SnoreToast が使えない場合は簡易バルーン通知
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            TrayIcon trayIcon = new TrayIcon(trayImage, "AsanaWatcher");
            trayIcon.setImageAutoSize(true);
            boolean added = false;
            try {
                try {
                    tray.add(trayIcon);
                    added = true;
                } catch (AWTException e) {
                    // ignore fallback failure silently
                }
                if (added) {
                    trayIcon.displayMessage(sectionName + "が" + threshold + "件以上", "現在 " + count + " 件", TrayIcon.MessageType.INFO);
            }
            } finally {
                if (added) {
                    try { tray.remove(trayIcon); } catch (Exception ignored) {}
                }
            }
        }
    }

    // テスト通知（メニューから呼び出し）
    public void notifyTest() throws IOException, InterruptedException {
        File exe = resolveSnoreToast();
        if (exe != null && exe.exists()) {
            maybeInstallAppId(exe);
            List<String> cmd = new ArrayList<>();
            cmd.add(exe.getAbsolutePath());
            cmd.add("-appID");
            cmd.add("AsanaWatcher");
            cmd.add("-t");
            cmd.add("AsanaWatcher テスト");
            cmd.add("-m");
            cmd.add("これはテスト通知です");
            cmd.add("-d");
            cmd.add("long");
            File icon = new File(exe.getParentFile(), "app.ico");
            if (!icon.exists()) {
                File alt = new File(exe.getParentFile(), "Asana.ico");
                if (alt.exists()) icon = alt; else icon = null;
            }
            if (icon != null && icon.exists()) {
                cmd.add("-p");
                cmd.add(icon.getAbsolutePath());
            }
            System.out.println("Executing SnoreToast TEST with args: " + String.join(" ", cmd));
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int code = process.waitFor();
            System.out.println("SnoreToast test exit=" + code + " output=" + output.trim());
            return;
        }

        // Fallback AWT balloon
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            TrayIcon trayIcon = new TrayIcon(trayImage, "AsanaWatcher");
            trayIcon.setImageAutoSize(true);
            boolean added = false;
            try {
                try { tray.add(trayIcon); added = true; } catch (AWTException e) {}
                if (added) {
                    trayIcon.displayMessage("AsanaWatcher テスト", "これはテスト通知です", TrayIcon.MessageType.INFO);
                }
            } finally {
                if (added) { try { tray.remove(trayIcon); } catch (Exception ignored) {} }
            }
        }
    }

    // URL を開く機能や PowerShell 経由の表示は使用しません（SnoreToastに統一）。

    // SnoreToast.exe を以下の優先順位で探索：
    // 1) 設定で明示されたパス（相対の場合はアプリルートも探索）
    // 2) 実行中 JAR 近傍（jpackage 配置想定）
    // 3) APPDATA/bin 配下
    // 4) 開発用同梱パスから APPDATA/bin へコピーして使用
    private File resolveSnoreToast() {
        // 1) explicit path from config
        if (snoreToastPath != null && !snoreToastPath.isBlank()) {
            Path p = Path.of(snoreToastPath);
            // if relative, try relative to app root and working dir
            if (!p.isAbsolute()) {
                File jarRoot = tryGetJarRoot();
                if (jarRoot != null) {
                    File f = new File(jarRoot, snoreToastPath);
                    if (f.exists()) {
                        System.out.println("Using SnoreToast at (relative to app): " + f);
                        return f;
                    }
                }
            }
            if (p.toFile().exists()) {
                System.out.println("Using SnoreToast at (explicit): " + p);
                return p.toFile();
            }
        }

        // 2) next to running JAR (jpackage places resources in app root)
        File jarRoot = tryGetJarRoot();
        if (jarRoot != null) {
            File f1 = new File(jarRoot, "SnoreToast.exe");
            if (f1.exists()) {
                System.out.println("Using SnoreToast at (app root): " + f1);
                return f1;
            }
        }

        // 3) APPDATA bin fallback
        if (appBaseDir != null) {
            File bin = appBaseDir.resolve("bin").toFile();
            File f2 = new File(bin, "SnoreToast.exe");
            if (f2.exists()) {
                System.out.println("Using SnoreToast at (APPDATA/bin): " + f2);
                return f2;
            }
        }

        // 4) dev-time path; if exists, copy to APPDATA/bin and use it
        File dev = Path.of("packaging", "windows", "resources", "SnoreToast.exe").toFile();
        if (dev.exists() && appBaseDir != null) {
            try {
                File bin = appBaseDir.resolve("bin").toFile();
                if (!bin.exists()) bin.mkdirs();
                File dst = new File(bin, "SnoreToast.exe");
                if (!dst.exists()) Files.copy(dev.toPath(), dst.toPath());
                // Also copy icon if present (prefer app.ico then Asana.ico)
                File resDir = dev.getParentFile();
                if (resDir != null) {
                    File iconSrc = new File(resDir, "app.ico");
                    if (!iconSrc.exists()) {
                        File alt = Path.of("packaging", "windows", "Asana.ico").toFile();
                        if (alt.exists()) iconSrc = alt; else iconSrc = null;
                    }
                    if (iconSrc != null && iconSrc.exists()) {
                        File iconDst = new File(bin, iconSrc.getName());
                        if (!iconDst.exists()) Files.copy(iconSrc.toPath(), iconDst.toPath());
                    }
                }
                if (dst.exists()) {
                    System.out.println("Copied SnoreToast from dev to APPDATA/bin: " + dst);
                    return dst;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private File tryGetJarRoot() {
        try {
            File jar = new File(Notifier.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            // jar is .../app/asana-watcher-<ver>.jar; root is parent of parent
            File appDir = jar.getParentFile();
            if (appDir != null) {
                File root = appDir.getParentFile();
                if (root != null && root.exists()) return root;
            }
        } catch (Exception ignored) {}
        return null;
    }

    // SnoreToast の -install を 1 度だけ試行し、AUMID を登録（Windows の安定したトースト通知用）
    private void maybeInstallAppId(File snore) {
        if (appIdInstallAttempted) return;
        appIdInstallAttempted = true;
        try {
            // Attempt to register AUMID via shortcut once using current executable
            File jarRoot = tryGetJarRoot();
            if (jarRoot == null) return;
            File appExe = new File(jarRoot, "AsanaWatcher.exe");
            if (!appExe.exists()) return;
            List<String> cmd = new ArrayList<>();
            cmd.add(snore.getAbsolutePath());
            cmd.add("-install");
            cmd.add("AsanaWatcher");
            cmd.add(appExe.getAbsolutePath());
            cmd.add("AsanaWatcher");
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();
            System.out.println("SnoreToast -install exit=" + code + " output=" + out.trim());
        } catch (Exception ignored) {
        }
    }
}
