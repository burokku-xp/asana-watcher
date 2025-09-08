package com.example.asanawatcher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

// アプリのユーザー設定（JSON ファイルに保存/読み込み）
@JsonIgnoreProperties(ignoreUnknown = true)
public class Config {
    // Asana の PAT（Personal Access Token）
    public String personalAccessToken = "";
    // 対象プロジェクトの GID
    public String projectGid = "";
    // 監視対象のセクション名（ボード列名）
    public String targetSectionName = "";
    // 通知の閾値（未完了タスク数がこれ以上になったら通知）
    public int threshold = 5;
    // ポーリング間隔（分）
    public int pollingMinutes = 3;
    // 再通知までのクールダウン（分）
    public int cooldownMinutes = 30;
    // ボードの URL（通知や設定で参照）
    public String boardUrl = "";
    // SnoreToast.exe のパス（インストール先 or 作業ディレクトリからの相対パスも可）
    public String snoreToastPath = "bin/SnoreToast.exe"; // relative to install dir or working dir

    // 設定ファイルを読み込み。存在しない場合はデフォルト設定を書き出して返す
    public static Config load(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if (Files.exists(path)) {
            try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return mapper.readValue(in, Config.class);
            }
        }
        Config cfg = new Config();
        save(cfg, path);
        return cfg;
    }

    // 設定を JSON として保存
    public static void save(Config cfg, Path path) throws IOException {
        Objects.requireNonNull(cfg);
        Files.createDirectories(path.getParent());
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(cfg);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }
}
