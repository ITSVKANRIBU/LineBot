# 運用

## 実行環境

| 項目 | 値 |
| --- | --- |
| 言語ランタイム | Java 8（Temurin 8、aarch64） |
| フレームワーク | Spring Boot |
| ビルド | Gradle（マルチプロジェクト） |
| 稼働環境 | Oracle Cloud Always Free の VM.Standard.A1.Flex（ap-tokyo-1、2 OCPU / 12 GB、Ubuntu 24.04）1 台 |
| プロセス管理 | systemd（`linebot.service`、異常終了時は 5 秒後に再起動） |
| 公開 | Caddy が `https://bot.<domain>` で TLS を終端し、`/callback` `/callapi` `/specialvillage` `/actuator/health` だけを loopback の Spring Boot へ流す。他のパスは 404 |
| プロセス数 | **1（複数インスタンス不可）** |

プロセスを 2 つ以上に増やすと、村がインスタンス間で共有されず、参加者が別のインスタンスへ振り分けられた時点で村を見つけられなくなります（[architecture.md](architecture.md) の「単一プロセス前提」参照）。**スケールアウトは構成として禁止です。**Always Free の枠（1,500 OCPU 時間 / 月）も 2 台目を許しません。

VM の構築手順は [deploy/setup.md](../deploy/setup.md)、切替とロールバックは [deploy/cutover.md](../deploy/cutover.md) にあります。

## 設定

秘密情報はソースコードに書かず、VM 上の `/etc/linebot.env`（`root:root`、`0600`）に置いて systemd が環境変数として渡します。値はパスワードマネージャにも保管し、VM 再作成時はそこから復元します。

| 環境変数 | 必須 | 説明 |
| --- | --- | --- |
| `LINE_BOT_CHANNEL_TOKEN` | ○ | チャネルアクセストークン |
| `LINE_BOT_CHANNEL_SECRET` | ○ | チャネルシークレット。webhook の署名検証に使う |
| `LOGGING_LEVEL_INSIDERGAME` | | ログレベル。既定は INFO |
| `LINE_BOT_API_END_POINT` | | 返信 API の接続先。切替前の検証でスタブへ向けるときだけ設定し、本番では**設定しない** |

待ち受けは `--server.address=127.0.0.1 --server.port=8081` を systemd unit が固定で渡します。LINE Developers コンソール側では、Webhook URL を `https://bot.<domain>/callback` に設定します。

## ローカルでの起動

```bash
./gradlew :insider-game-bot:bootRun
```

環境変数 `LINE_BOT_CHANNEL_TOKEN` と `LINE_BOT_CHANNEL_SECRET` が必要です。webhook を受けるには、ローカルのポートを外部へ公開する必要があります。

`/callapi` は LINE の資格情報なしで叩けます。入力の解釈は LINE 経由と共通のため、**`@` で始まるコマンドを含めてゲームの規則を確認できます**。ただし次は確認できません（[interfaces.md](interfaces.md) 参照）。

- 署名検証
- ポストバックとスタンプ（この API に入口がありません）
- 返信 API へ実際に送っていること（この API は返信 API を呼びません）
- 対象の村がないときの応答（この API はテキスト、LINE は確認テンプレート）

## ビルドとデプロイ

CI は GitHub Actions で、`master` と `develop` への push、およびプルリクエストで実行されます。

- **本番と同じ Java 8 でビルドします。**ビルド JDK を揃えないと、ソース互換性の設定だけでは実行時の差分を拾えないためです。
- テストの実行、実行可能 jar と sha256 の生成、VM 上の更新スクリプトと検証スクリプトのテストを行います。
- テストレポートは成否にかかわらず成果物として保存されます。

**`master` への push は本番反映です。**`build` が通った jar を、ホスト鍵を固定した SSH で VM の `/opt/linebot/incoming/<commit>/` へ送り、VM 上の更新スクリプトが sha256 を検証してから `/opt/linebot/releases/<commit>/` へ昇格し、`current.jar` を差し替えて再起動し、`GET /actuator/health` が `{"status":"UP"}` を返すまで最長 60 秒待ちます。通らなければ直前の jar へ自動で戻します。同時に走った deploy は直列化され、`master` の最新でなくなった commit は何もせず成功終了します。手動の承認ステップはありません。

## ログ

### 方針

**受信イベントそのものをログへ出力しません。**イベントには LINE ユーザー ID とお題が含まれ、後者はゲームの答えそのものだからです。記録するのはイベント種別と処理結果までです。

公開 API の予期しないエラーも、定型のメッセージへ丸めてから返します。フレームワークの既定のエラー応答には例外メッセージとリクエストパスが含まれ、そこにお題やユーザー ID が現れ得るためです。

### 出力される内容

| レベル | 内容 |
| --- | --- |
| INFO | 役職画像カタログの更新 |
| WARN | 役職画像カタログの取得失敗（前回取得した一覧を保持して継続。一度も取得できていなければ既定画像）。カタログ内の規約外の要素を読み飛ばしたとき（無視したファイル名を列挙する。ファイル名に個人情報は含まれない） |
| ERROR | 返信の送信失敗、お題辞書の読み込み失敗、`/callapi` の予期しない例外（`/specialvillage` の例外は 400 に丸めるだけで記録しない） |
| DEBUG | 受信イベントの種別、ユーザー ID を持たないイベントの拒否 |

イベント種別を追う必要がある場合は DEBUG へ引き上げます。**引き上げてもユーザー ID とお題は出力されません。**

```bash
LOGGING_LEVEL_INSIDERGAME=DEBUG
```

## 障害時の挙動

| 事象 | 挙動 | 対応 |
| --- | --- | --- |
| アプリケーションの再起動・再デプロイ | **すべての村が消える** | 利用者が村を作り直す。デプロイは利用者のいない時間帯に行う |
| デプロイした jar が起動しない | 更新スクリプトが直前の jar へ戻して再起動し、deploy job を失敗させる。戻し先がなければ停止する | Actions のログを見て修正し、再 push する |
| VM の停止（Always Free のアイドル回収） | 7 日間のアイドル判定でメール通知、その 1 週間後に停止。削除ではない | 通知が届いたら [deploy/cutover.md](../deploy/cutover.md) の PAYG の手順へ。停止後はコンソールから起動できる |
| VM の喪失 | Bot が無応答になり、外形監視が通知する | [deploy/setup.md](../deploy/setup.md) で再作成する。秘密情報はパスワードマネージャから復元する |
| 役職画像カタログの取得失敗 | 前回取得した一覧で配布を継続。一度も取得できていなければ既定画像。5 分後に再取得 | 継続して失敗する場合は URL の失効を疑う（下記）。再起動すると一覧が消え、次に取得できるまで既定画像になる |
| お題辞書の読み込み失敗 | お題の抽選が `null` を返し、**そのまま応答に現れる**。お題の自動取得は「お題は『null』です。」を返し、ランダム村はお題が `null` のまま作られる。エラーにはならないため利用者は気付けない | 起動時の ERROR ログで判別する。同梱リソースの欠落を疑い、ビルド成果物を確認する |
| 返信 API の失敗 | ログへ記録し、利用者へは何も返らない | 利用者は同じ操作を再送する。状態は変更済みの場合がある |
| 村が上限を超えた | 古い村から消える | 仕様。[architecture.md](architecture.md) 参照 |
| `/callapi` `/specialvillage` への大量リクエスト | Caddy が 1 IP あたり 30 / 10 req/分を超えた分に 429 を返す。防止ではなく遅延 | 複数 IP からの追い出しは防げない（[architecture.md](architecture.md) の「既知の制約」） |

### 役職画像カタログの URL

Google Apps Script の**デプロイ URL**（`/macros/s/<デプロイ ID>/exec`）を指定します。ブラウザでこの URL を開くと別ドメインへリダイレクトされますが、**リダイレクト先の URL を設定してはいけません**。リダイレクト先に含まれるキーは一時的な発行物で、失効すると以降エラーを返し続けます。

画像が既定のものに戻り続けている場合、この設定を疑ってください。

## デプロイ時の注意

**進行中の村は再起動で消えます。**`master` への push はそのまま本番反映なので、ゲームが行われていない時間帯に push してください。これは受け入れている副作用であり、回避手段はありません（状態の永続化は非目的）。

## 監視

| 監視 | 内容 | 通知先 |
| --- | --- | --- |
| OCI Alarm | `MemoryUtilization` が 20% を下回る、または指標が欠測する（エージェント停止・VM 停止） | メール |
| 外形監視 | `GET https://bot.<domain>/actuator/health` を 5 分間隔で確認 | メール |
| Oracle からのメール | アイドル判定の通知。届いたら 1 週間以内に PAYG へ上げる（[deploy/cutover.md](../deploy/cutover.md)） | メール |

利用者からの報告（Bot の応答に含まれる意見フォーム）も引き続き検知経路です。
