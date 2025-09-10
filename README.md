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
- トレイの「設定を開く」で GUI ダイアログが開き、PAT、プロジェクト/セクション、閾値、ポーリング、クールダウン、URL を編集できます。保存すると次回以降のポーリング間隔も再スケジュールされます。
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
補足: `snoreToastPath` はGUIでは編集しません。必要なら `config.json` を直接編集してください。

ビルド手順

## 前提条件
- Java 21 (JDK 21) 必須（`jpackage` 利用のため JDK 推奨）
- Windows環境での実行を想定

## 基本ビルド手順

### 1) 依存の取得と実行 JAR 作成（Shadow Jar）
```bash
gradle shadowJar
```
出力: `build/libs/asana-watcher-1.0.0.jar`

### 2) 必要リソースの自動準備
```bash
# SnoreToast.exeの自動ダウンロード
gradle downloadSnoreToast

# アプリアイコンの準備（自動実行される）
gradle prepareAppIcon
```

### 3) パッケージング

#### 簡易（WiX 自動判定・これを実行してください）
```bash
gradle packageWin
```
- WiX（candle.exe / light.exe）が PATH 上に見つかれば EXE を作成、見つからなければポータブルZIPへ自動フォールバックします。
- ポータブルを強制: `gradle -PforcePortable=true packageWin`

#### WiX の準備（EXE / MSI を作る場合）
- いずれかで WiX v3 をインストール（v4ではなくv3推奨）:
  - winget: `winget install --id WiXToolset.WiXToolset --source winget`
  - Chocolatey: `choco install wixtoolset -y`
  - Scoop: `scoop bucket add extras && scoop install wixtoolset`
- PATH を通す（PowerShell 例・一時反映）:
```powershell
$wixBin = (Get-ChildItem "C:\\Program Files (x86)\\WiX Toolset v3*" -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName + "\\bin"
if (Test-Path $wixBin) { $env:PATH = "$wixBin;$env:PATH" }
candle.exe -?; light.exe -?
```

#### Windows実行ファイル（.exe）の作成
```bash
gradle packageWinExe
```

#### Windows インストーラー（.msi）の作成
```bash
gradle packageWinMsi
```

#### ポータブル版の作成
```bash
# JRE同梱のアプリフォルダを作成
gradle packageWinImage

# ポータブルZIPファイルを作成
gradle packageWinPortableZip
```

### 出力ファイル
- `build/dist/AsanaWatcher-1.0.0.exe` - Windows実行ファイル
- `build/dist/AsanaWatcher-1.0.0.msi` - Windowsインストーラー
- `build/dist/AsanaWatcher/` - ポータブル版アプリフォルダ
- `build/dist/AsanaWatcher-1.0.0-portable.zip` - ポータブル版ZIP

## 高度なビルドオプション

### カスタムアイコンの指定
```bash
gradle packageWinExe -PappIcon=/path/to/custom.ico
```

### SnoreToast の手動URL指定
```bash
gradle downloadSnoreToast -PsnoretoastUrl=https://example.com/SnoreToast.exe
```

### ワンコマンドビルド
```bash
# WiX 自動判定で EXE かポータブルZIP を作成
gradle packageWin

# ポータブルZIPを強制
gradle -PforcePortable=true packageWin
```

## 手動リソース配置（オプション）
- `packaging/windows/Asana.ico` - デフォルトアプリアイコン
- `packaging/windows/resources/SnoreToast.exe` - 手動で配置する場合

動作
- 実行後、トレイに常駐します（メニュー: 今すぐチェック / 設定を開く / ログを表示 / テスト通知 / 終了）。
- 3分ごとに指定セクションの未完了数を取得します。
- 条件: 4→5 になったタイミングで1回通知、5以上継続中は通知なし、5未満に戻った後に再び5以上で再通知。
- 通知クリックの動作は現状未実装（SnoreToastのクリック検知は未使用）。

補足
- SnoreToast が見つからない場合は AWT のバルーン通知にフォールバック（クリック動作なし）。
- `snoreToastPath` は相対/絶対どちらでも可。既定は `bin/SnoreToast.exe`（インストール先直下に `bin` を作る運用を想定）。
- SnoreToast の探索順: 1) `snoreToastPath` で指定 2) アプリ配下（jpackage配置） 3) `%APPDATA%/AsanaWatcher/bin` 4) 開発用 `packaging/windows/resources/SnoreToast.exe` を `%APPDATA%/AsanaWatcher/bin` へ初回コピー。
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
- ビルド失敗（EXE/MSI）: WiX v3 がインストール済みで `candle.exe`/`light.exe` が PATH で見えるか確認。未導入時は `gradle packageWin` でポータブルにフォールバック。
- jpackage が見つからない: JDK 21 をインストールし、`jpackage` が利用可能か確認。
