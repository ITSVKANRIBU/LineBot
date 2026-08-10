# インサイダーゲーム Bot

LINE Messaging APIを使って、インサイダーゲームとWerewordsを運営するBotです。Heroku上でSpring Bootアプリとして動作します。ゲーム状態はDBへ保存せず、プロセスのメモリだけで管理します。

## ゲーム仕様

### 通常村

1. LINEで`お題`または`題`を送ると通常村を作成します。
2. `神`を送ると、GM（ゲームマスター）を含む通常村を作成します。
3. 村のオーナーが人数（2人以上）を送ると、参加人数を確定します。
4. オーナーが自由文を送ると、その村のお題になります。
5. 参加者が4桁の村番号を送ると、参加順に役職を受け取ります。
6. オーナーが村番号を送ると、参加状況とお題を確認できます。

通常村の役職は、村人・インサイダー・（神モード時のみ）GMです。役職の割り当て順は参加順と、村作成時に決まる番号で決定されます。

### 特殊操作

| 入力 | 動作 |
| --- | --- |
| `@取得` / `＠取得` | お題候補を取得する |
| `@配布` / `＠配布` | Bot配布用の案内を表示する |
| `@特殊` / `＠特殊` | 特殊村作成フォームを表示する |
| `@逆村` / `＠逆村` | 作成直後の自分の村を逆村にする |
| `@わーわーず` / `＠わーわーず` | 条件を満たす通常村からWerewords村を作成する |

`お題の自動取得`のボタンでは難易度別のお題候補を取得できます。お題データは`sample-spring-boot-echo/src/main/resources/word.csv`を使用します。

### 特殊村

特殊村は、外部フォームから次のJSONを`/specialvillage`へ送信して作成します。

```json
{"message":["役職メッセージ1","役職メッセージ2"]}
```

メッセージをランダムに並べ替え、5桁の村番号を返します。参加者が村番号を送ると、参加順に対応するメッセージを受け取ります。

## HTTP API

### LINE webhook

- `POST /callback`
- LINE Messaging APIの署名検証後、テキスト・ポストバック・スタンプイベントを処理します。

### 村操作API

```text
GET /callapi?message=<メッセージ>&userId=<LINEユーザーID>
```

LINEへ送るのと同じ内容を`message`に渡し、応答をLINE Message API形式のJSON配列で返します。LINEの村番号判定が101以上であるのに対し、このAPIでは1000以上を村番号として扱います。

| `message` | 動作 |
| --- | --- |
| 10000以上 | 特殊村へ参加する |
| 1000〜9999 | 通常村へ参加する。オーナーの場合は配布状況を返す |
| 0〜999 | 自分の村の参加人数を設定する |
| `お題` / `題` / `神` | 通常村を作成する |
| その他の文字列 | 自分の村のお題に設定する |

- `message`または`userId`が空の場合: HTTP 400
- 存在しない村番号、対象の村がない場合: `村が作成されていません`
- 満員の場合: `村がいっぱいです。`
- 人数が2未満の場合: `村の人数は2人以上に設定してください。`
- 内部エラー: HTTP 500と`{"error":"内部エラーが発生しました。"}`
- CORS: 有効

`userId`はAPI呼び出し元から渡された値を、そのまま参加者識別に使用します。認証・レート制限はありません。

### 特殊村作成API

```text
POST /specialvillage
```

リクエスト本文の`message`配列から特殊村を作成し、`{"data":"<村番号>"}`を返します。不正なJSONや必須データ不足の場合はHTTP 400です。

## 状態管理と制限

- DB、migration、保存済み履歴は使用しません。
- 通常村は最大50件、特殊村は最大30件です。
- 上限を超えた場合は古い村からFIFOで削除します。
- Herokuの再起動・再デプロイで、作成中の村は失われます。
- 同じユーザーが再参加した場合は、既存の役職・メッセージを再表示します。
- 村番号は通常村が4桁、特殊村が5桁です。

## 画像取得

Bot起動後および5分間隔で、Google Apps Scriptから役職画像の一覧を取得します。取得に失敗した場合は、`MessageConst`に定義されたGitHub Rawの標準画像を使用します。

## 運用

必要環境はJava 8です。

```bash
./gradlew :sample-spring-boot-echo:bootRun
```

Herokuでは`Procfile`に従い、次のjarを起動します。

```text
sample-spring-boot-echo/build/libs/sample-spring-boot-echo-*.jar
```

LINE Messaging APIのチャネル設定では、Webhook URLを`https://<アプリのホスト>/callback`に設定してください。Botのアクセストークン等の秘密情報は、ソースコードへ記録せずHerokuの環境変数で管理します。

### ログ

webhook eventにはLINEユーザーIDとユーザーが入力したお題が含まれるため、event自体はログへ出力しません。記録するのはevent種別と処理結果までです。

受信イベントの種別を追う必要がある場合は、次の環境変数でDEBUGへ引き上げます。個人情報そのものは引き上げても出力されません。

```bash
LOGGING_LEVEL_COM_EXAMPLE_BOT=DEBUG
```

CIはGitHub Actionsで、本番と同じJava 8で`./gradlew check`と`bootJar`を実行します（`.github/workflows/ci.yml`）。

## 主なコード構成

- `sample-spring-boot-echo/.../EchoApplication.java`: LINEイベント処理とコマンド判定
- `sample-spring-boot-echo/.../spring/game/VillageService.java`: LINEと`/callapi`で共通のゲーム操作
- `sample-spring-boot-echo/.../Village.java`: 通常村の状態・役職・メッセージ
- `sample-spring-boot-echo/.../spring/game/`: 特殊村・Werewordsのゲームロジックとレジストリ
- `sample-spring-boot-echo/.../MainController.java`: `/callapi`
- `sample-spring-boot-echo/.../SpecialVillageController.java`: `/specialvillage`
- `sample-spring-boot-echo/.../ApiExceptionHandler.java`: 公開APIの内部エラー応答
- `sample-spring-boot-echo/.../VillageList.java`: 通常村一覧とFIFO管理
- `sample-spring-boot-echo/src/main/resources/word.csv`: お題データ
- `line-bot-*`: LINE Messaging API SDKとSpring Boot連携基盤

## 注意

このBotは現在、単一プロセス内のメモリ状態を前提にしています。複数インスタンスでの共有、ゲーム状態の永続化、認証・課金機能は実装していません。
