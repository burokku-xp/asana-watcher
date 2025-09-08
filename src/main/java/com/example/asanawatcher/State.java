package com.example.asanawatcher;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// 監視の内部状態を保持するクラス（JSON に保存）
@JsonIgnoreProperties(ignoreUnknown = true)
public class State {
    // 直近の未完了タスク数
    public int lastCount = 0;
    // 直前が閾値未満だったか（true のとき、今回で閾値以上になれば上昇エッジとみなす）
    public boolean wasBelowThreshold = true; // start as below to allow initial notify if >= threshold after cooldown
    // 最後に通知した時刻（エポックミリ秒）— クールダウン判定に使用
    public long lastNotifiedAtEpochMillis = 0L;
    // 前回使用した閾値（設定変更の検知に使用）
    public int lastThreshold = -1; // for detecting threshold changes

    // 状態を読み込み。存在しない場合は初期状態で保存して返却
    public static State load(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        if (Files.exists(path)) {
            try (var in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                return mapper.readValue(in, State.class);
            }
        }
        State s = new State();
        save(s, path);
        return s;
    }

    // 状態を JSON として保存
    public static void save(State s, Path path) throws IOException {
        Files.createDirectories(path.getParent());
        ObjectMapper mapper = new ObjectMapper();
        var json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(s);
        Files.writeString(path, json, StandardCharsets.UTF_8);
    }
}
