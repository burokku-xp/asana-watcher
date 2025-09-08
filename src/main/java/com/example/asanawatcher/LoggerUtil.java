package com.example.asanawatcher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 簡易ファイルロガー。
 * System.out / System.err をアプリ用データディレクトリ配下のログファイルへリダイレクトする。
 * 依存を増やさず、最小限の仕組みでログを永続化する目的。
 */
public final class LoggerUtil {
    private LoggerUtil() {}

    public static void initLogging(Path appDir) {
        try {
            // アプリ用ディレクトリを作成
            Files.createDirectories(appDir);
            Path logFile = getLogFile(appDir);
            // Simple size-based rotation (~1MB)
            // おおよそ 1MB 超で .1 へローテーション
            tryRotate(logFile, 1_000_000);

            FileOutputStream fos = new FileOutputStream(logFile.toFile(), true);
            PrintStream fileOut = new PrintStream(fos, true, StandardCharsets.UTF_8);
            // Redirect both out and err to the same file
            // 標準出力/標準エラーを同一ファイルへ
            System.setOut(fileOut);
            System.setErr(fileOut);
            System.out.println("=== AsanaWatcher started ===");
        } catch (Exception e) {
            // If logging init fails, keep default System.out/err
            // ログ初期化に失敗した場合はコンソール出力のまま継続
        }
    }

    public static Path getLogFile(Path appDir) {
        // ログファイルパス（固定名）を返す
        return appDir.resolve("asana-watcher.log");
    }

    private static void tryRotate(Path file, long maxBytes) throws IOException {
        if (Files.exists(file) && Files.size(file) > maxBytes) {
            Path backup = file.resolveSibling(file.getFileName().toString() + ".1");
            try {
                Files.deleteIfExists(backup);
            } catch (IOException ignored) {}
            // 既存ログを .1 にリネームしてローテーション
            Files.move(file, backup);
        }
    }
}
