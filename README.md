Asana タスク通知システム（Windows 常駐）

概要
- Asana プロジェクト `1183830872180667` の指定セクション（既定は「通常対応(未振分け)」）の未完了タスク数を監視し、しきい値（既定 5 件）を超えたタイミングで Windows トースト通知します。
- 3分おきにポーリング、5件未満に戻った後に再び5件以上になったときのみ再通知（クールダウン30分も考慮）。
- トレイ常駐（終了／今すぐチェック／設定を開く）。設定は GUI ダイアログで編集可能。
- `jpackage` で .exe / .msi を作成し、JRE 同梱配布できます。

要件
- Java 21 (JDK 21) 必須（`jpackage` 利用のため JDK 推奨）。
- Asana Personal Access Token (PAT)。
- Windows 用トースト通知に `SnoreToast.exe` を同梱して使用。

設定
- 初回起動時、設定と状態ファイルが作成されます。
  - 設定: `%APPDATA%/AsanaWatcher/config.json`
  - 状態: `%APPDATA%/AsanaWatcher/state.json`
- トレイの「設定を開く」で GUI ダイアログが開き、PAT、プロジェクト/セクション、閾値、ポーリング、クールダウン、URL、SnoreToast パスを編集できます。保存すると次回以降のポーリング間隔も再スケジュールされます。
- `config.json` の例（自動生成されます）:
```json
{
  "personalAccessToken": "<YOUR_PAT>",
  "projectGid": "1183830872180667",
  "targetSectionName": "通常対応(未振分け)",
  "threshold": 5,
  "pollingMinutes": 3,
  "cooldownMinutes": 30,
  "boardUrl": "https://app.asana.com/1/1183631712692081/project/1183830872180667/board/1203964520894345",
  "snoreToastPath": "bin/SnoreToast.exe"
}
```

ビルド
1) 依存の取得と実行 JAR 作成（Shadow Jar）
```
gradle shadowJar
```

2) SnoreToast の配置
- 方法A: `gradle downloadSnoreToast` を実行すると `packaging/windows/resources/SnoreToast.exe` に自動配置します。
- 方法B: 手動で `packaging/windows/resources/` に `SnoreToast.exe` を配置してください（同梱用）。
- 任意で `packaging/windows/app.ico` を置くとアイコンが適用されます。

3) jpackage で exe / msi 作成
```
gradle packageWinExe
gradle packageWinMsi
```
出力: `build/dist/AsanaWatcher-<version>.exe` / `.msi`

動作
- 実行後、トレイに常駐します。
- 3分ごとに指定セクションの未完了数を取得します。
- 条件: 4→5 になったタイミングで1回通知、5以上継続中は通知なし、5未満に戻った後に再び5以上で再通知。
- 通知クリックで `boardUrl` のボード URL を既定ブラウザで開きます（SnoreToast の `-w` でクリック検知 → アプリ側でブラウザ起動）。

補足
- SnoreToast が見つからない場合は AWT のバルーン通知にフォールバック（クリック動作なし）。
- `snoreToastPath` は相対/絶対どちらでも可。既定は `bin/SnoreToast.exe`（インストール先直下に `bin` を作る運用を想定）。
- ネットワークや Asana API エラー時は次回ポーリングで再試行します。

受け入れ基準の対応
- 4→5 増加で 1 回だけ通知: 状態 `wasBelowThreshold` を用いた上昇クロス検出で実装。
- 5 以上継続中は通知なし: しきい値以上連続では通知しません。
- 5 未満→再び 5 以上で再通知: 下回りフラグがリセットされ、再上昇で通知。
- クリックでボード URL: SnoreToast `-w` の戻りコードでクリックを検知→ `Desktop.browse`。
- exe 配布、JRE 同梱: `jpackage` タスクで exe/msi を生成（JDK 21 必要）。

トラブルシュート
- 通知が出ない: PAT/プロジェクト/セクション名を確認。`SnoreToast.exe` を同梱したか確認。
- クリックで開かない: SnoreToast 実行時に `-w` を使う仕様のため、ブロックする実行環境でないか確認（本アプリ内で待機・検知）。
- ビルド失敗: JDK 21 が PATH にあり、`jpackage` が使用可能か確認。Gradle のネットワーク到達性を確認。
