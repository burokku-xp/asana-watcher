package com.example.asanawatcher;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final String APP_NAME = "AsanaWatcher";

    private static ScheduledExecutorService scheduler;
    private static volatile ScheduledFuture<?> scheduledTask;

    public static void main(String[] args) throws Exception {
        // アプリ用ディレクトリ（Windows: %APPDATA%/AsanaWatcher, それ以外: ~/.asana-watcher）
        Path baseDir = getAppDataDir();
        // initialize simple file logging early so packaged app without console still records logs
        // 早期にファイルロギングを有効化（パッケージ配布でもログが残るように）
        LoggerUtil.initLogging(baseDir);
        Path configPath = baseDir.resolve("config.json");
        Path statePath = baseDir.resolve("state.json");

        boolean firstRun = Files.notExists(configPath);

        // ポーリングおよびチェック処理専用のスレッドを1本だけ用意
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "asana-watcher");
            t.setDaemon(false);
            return t;
        });

        // 設定と内部状態をロード（なければ初期化）
        Config config = Config.load(configPath);
        State state = State.load(statePath);

        if (config.personalAccessToken == null || config.personalAccessToken.isBlank()) {
            System.out.println("config.json に Personal Access Token を設定してください。");
        }

        // トレイアプリ（メニュー：今すぐチェック/設定/ログ/テスト通知/終了）のインストール
        TrayApp trayApp = new TrayApp(
                scheduler,
                () -> runCheck(configPath, statePath),
                () -> openSettings(configPath),
                () -> openLogs(baseDir),
                () -> testNotify(baseDir, configPath)
        );
        try { trayApp.install(); } catch (Exception ignored) {}

        // First run immediately, then at fixed delay
        if (firstRun || config.personalAccessToken == null || config.personalAccessToken.isBlank()) {
            // 初回起動 or PAT 未設定時は設定ダイアログを開く
            openSettings(configPath);
        }
        // 起動直後に1回実行し、以後はスケジュールで実行
        runCheck(configPath, statePath);
        schedulePolling(configPath, statePath);
    }

    private static final Object RUN_LOCK = new Object();

    // メインの監視処理：
    // - 設定/状態の読み込み
    // - 未完了タスク数の取得
    // - 閾値の「下→上」遷移を検出し、クールダウンを満たす場合に通知
    private static void runCheck(Path configPath, Path statePath) {
        synchronized (RUN_LOCK) {
        try {
            Config cfg = Config.load(configPath);
            State st = State.load(statePath);

            // If threshold changed, reset edge/cooldown so next check can notify immediately when at/above
            // 閾値が変更されたら、上昇エッジ検出とクールダウンをリセット
            if (st.lastThreshold != cfg.threshold) {
                System.out.println(now() + " 閾値が変更されました: " + st.lastThreshold + " -> " + cfg.threshold + "（通知条件をリセット）");
                st.wasBelowThreshold = true; // force next check to treat as rising edge if at/above
                st.lastNotifiedAtEpochMillis = 0L; // clear cooldown
                st.lastThreshold = cfg.threshold;
            }

            if (cfg.personalAccessToken == null || cfg.personalAccessToken.isBlank()) {
                System.out.println(now() + " PAT 未設定のためスキップ");
                return;
            }

            // 対象セクションの GID を取得
            AsanaClient client = new AsanaClient(cfg.personalAccessToken);
            Optional<String> sectionGidOpt = client.findSectionGidByName(cfg.projectGid, cfg.targetSectionName);
            if (sectionGidOpt.isEmpty()) {
                System.out.println(now() + " セクションが見つかりません: " + cfg.targetSectionName);
                return;
            }
            String sectionGid = sectionGidOpt.get();

            // 未完了タスク数をカウント
            int count = client.countIncompleteTasksInSection(sectionGid);
            System.out.println(now() + " 未完了数: " + count);

            boolean nowBelow = count < cfg.threshold;
            boolean crossedUp = st.wasBelowThreshold && !nowBelow; // below -> at/above
            boolean cooldownOk = st.lastNotifiedAtEpochMillis == 0 ||
                    (Duration.between(Instant.ofEpochMilli(st.lastNotifiedAtEpochMillis), Instant.now()).toMinutes() >= cfg.cooldownMinutes);

            // 閾値を下回っていた状態から「閾値以上」に上がり、かつクールダウンを満たしていれば通知
            if (!nowBelow && crossedUp && cooldownOk) {
                Notifier notifier = new Notifier(getAppDataDir(), cfg.snoreToastPath, cfg.boardUrl, null);
                try {
                    notifier.notifyThreshold(count, cfg.targetSectionName, cfg.threshold);
                } catch (IOException | InterruptedException e) {
                    System.err.println("通知に失敗: " + e.getMessage());
                }
                st.lastNotifiedAtEpochMillis = System.currentTimeMillis();
            }

            // 状態を更新・保存
            st.wasBelowThreshold = nowBelow;
            st.lastCount = count;
            State.save(st, statePath);
        } catch (Exception e) {
            System.err.println(now() + " チェック中にエラー: " + e.getMessage());
        }
        }
    }

    // 設定ダイアログを EDT 上で開く。保存された場合はポーリングスケジュールを更新
    private static void openSettings(Path configPath) {
        Runnable showDialog = () -> {
            try {
                Config cfg = Config.load(configPath);
                SettingsDialog dlg = new SettingsDialog(null, cfg);
                dlg.setVisible(true);
                if (dlg.isSaved()) {
                    try {
                        Config.save(cfg, configPath);
                        schedulePolling(configPath, getAppDataDir().resolve("state.json"));
                    } catch (IOException ex) {
                        System.err.println("設定保存に失敗: " + ex.getMessage());
                    }
                }
            } catch (Exception e) {
                System.err.println("設定ダイアログの表示に失敗: " + e.getMessage());
            }
        };

        try {
            if (EventQueue.isDispatchThread()) {
                showDialog.run();
            } else {
                EventQueue.invokeAndWait(showDialog);
            }
        } catch (Exception e) {
            System.err.println("設定ダイアログの起動に失敗: " + e.getMessage());
        }
    }

    // アプリデータディレクトリの解決（Windows は %APPDATA%/AsanaWatcher を優先）
    private static Path getAppDataDir() {
        if (isWindows()) {
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isBlank()) {
                return Path.of(appData, APP_NAME);
            }
        }
        return Path.of(System.getProperty("user.home"), ".asana-watcher");
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win");
    }

    private static String now() {
        return Instant.now().toString();
    }

    // ポーリングスケジュールを設定/更新（既存のスケジュールはキャンセルして再登録）
    private static void schedulePolling(Path configPath, Path statePath) {
        try {
            Config cfg = Config.load(configPath);
            int minutes = Math.max(1, cfg.pollingMinutes);
            if (scheduledTask != null && !scheduledTask.isCancelled()) {
                scheduledTask.cancel(false);
            }
            scheduledTask = scheduler.scheduleWithFixedDelay(() -> runCheck(configPath, statePath), minutes, minutes, TimeUnit.MINUTES);
        } catch (IOException e) {
            System.err.println("ポーリングスケジュール更新に失敗: " + e.getMessage());
        }
    }

    private static volatile LogViewerDialog logDialog;
    // ログビューアダイアログを開く（必要に応じて生成）
    private static void openLogs(Path baseDir) {
        Runnable show = () -> {
            try {
                Path log = LoggerUtil.getLogFile(baseDir);
                if (logDialog == null) {
                    logDialog = new LogViewerDialog(null, log);
                }
                logDialog.setVisible(true);
                logDialog.toFront();
            } catch (Exception e) {
                System.err.println("ログビューアの表示に失敗: " + e.getMessage());
            }
        };
        try {
            if (EventQueue.isDispatchThread()) show.run(); else EventQueue.invokeLater(show);
        } catch (Exception ignored) {}
    }

    // テスト通知を送出（SnoreToast が見つかればそちら、なければ AWT バルーン）
    private static void testNotify(Path baseDir, Path configPath) {
        try {
            Config cfg = Config.load(configPath);
            Notifier notifier = new Notifier(baseDir, cfg.snoreToastPath, cfg.boardUrl, null);
            notifier.notifyTest();
        } catch (Exception e) {
            System.err.println("テスト通知に失敗: " + e.getMessage());
        }
    }
}
