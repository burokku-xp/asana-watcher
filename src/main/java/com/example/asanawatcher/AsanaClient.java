package com.example.asanawatcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

// Asana API への最低限のアクセスを提供するクライアント
// - プロジェクト内の指定セクション名から GID を検索
// - セクション内の未完了タスク数をカウント（ページング対応）
public class AsanaClient {
    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String pat;

    public AsanaClient(String pat) {
        // Personal Access Token（PAT）を保持し、HTTP クライアントを初期化
        this.pat = pat;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    // 指定したプロジェクト内から、名前が一致するセクションの GID を探す
    // 見つかった場合は Optional に包んで返し、なければ Optional.empty()
    public Optional<String> findSectionGidByName(String projectGid, String targetName) throws IOException, InterruptedException {
        String url = "https://app.asana.com/api/1.0/projects/" + encode(projectGid) + "/sections";
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + pat)
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            // 2xx 以外は API エラーとして扱う
            throw new IOException("Asana API error: " + resp.statusCode() + " - " + resp.body());
        }
        JsonNode root = mapper.readTree(resp.body());
        // data 配列から name が一致するものを探す
        for (JsonNode node : root.path("data")) {
            if (targetName.equals(node.path("name").asText())) {
                return Optional.of(node.path("gid").asText());
            }
        }
        return Optional.empty();
    }

    // 指定セクションに含まれる未完了タスクの件数を取得する
    // Asana のページング（next_page.uri）に追従し、全件を走査してカウント
    public int countIncompleteTasksInSection(String sectionGid) throws IOException, InterruptedException {
        int count = 0;
        String nextUrl = "https://app.asana.com/api/1.0/sections/" + encode(sectionGid) + "/tasks?opt_fields=completed&limit=100";
        while (nextUrl != null) {
            HttpRequest req = HttpRequest.newBuilder(URI.create(nextUrl))
                    .header("Authorization", "Bearer " + pat)
                    .GET()
                    .timeout(Duration.ofSeconds(30))
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                // 2xx 以外は API エラー
                throw new IOException("Asana API error: " + resp.statusCode() + " - " + resp.body());
            }
            JsonNode root = mapper.readTree(resp.body());
            // data 配列の completed=false の数を積み上げ
            for (JsonNode task : root.path("data")) {
                boolean completed = task.path("completed").asBoolean(false);
                if (!completed) count++;
            }
            // next_page.uri があれば続けて取得、なければ終了
            JsonNode next = root.path("next_page");
            if (next.isMissingNode() || next.isNull() || next.path("uri").isMissingNode()) {
                nextUrl = null;
            } else {
                nextUrl = next.path("uri").asText(null);
            }
        }
        return count;
    }

    // URL パラメータ用に UTF-8 でエンコード
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
