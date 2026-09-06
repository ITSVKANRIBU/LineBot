# sample-spring-boot-echo

インサイダーゲームBotの本体です。Spring Bootアプリとして動作し、
LINE webhookの受信、ゲーム状態の保持、外部フォーム向けHTTP APIの提供をすべてこのモジュールで行います。

ゲームの遊び方・コマンド・APIの仕様はリポジトリルートの[README.md](../README.md)にまとめています。
ここではモジュール内部の構造と、開発時の動かし方を説明します。

## 起動

Java 8が必要です。リポジトリルートから次のコマンドで起動します。

```bash
./gradlew :sample-spring-boot-echo:bootRun
```

チャネル情報は`src/main/resources/application.yml`から環境変数として読み込みます。

```yaml
line.bot:
  channel-token: ${LINE_BOT_CHANNEL_TOKEN}
  channel-secret: ${LINE_BOT_CHANNEL_SECRET}
  handler.path: /callback
```

```bash
export LINE_BOT_CHANNEL_TOKEN='チャネルアクセストークン'
export LINE_BOT_CHANNEL_SECRET='チャネルシークレット'
```

トークンとシークレットはLINE Developersのチャネル設定画面から取得します。
ソースコードには書かず、環境変数（Herokuの場合はConfig Vars）で渡します。

Herokuでは`Procfile`に従って`build/libs/sample-spring-boot-echo-*.jar`が起動します。
デプロイ後、LINEチャネルのWebhook URLに`https://<アプリのホスト>/callback`を設定してください。

## エンドポイント

| path | 実装 | 用途 |
| --- | --- | --- |
| `POST /callback` | `EchoApplication`（`@LineMessageHandler`） | LINE webhook。署名検証は`line-bot-spring-boot`が行う |
| `GET /callapi` | `MainController` | 外部フォームから村を操作する。応答はLINE Message API形式のJSON配列 |
| `POST /specialvillage` | `SpecialVillageController` | 特殊村を作成し、村番号を返す |

`/callapi`と`/specialvillage`はCORSを有効にしており、認証・レート制限はありません。

## 構成

### イベントの受け口

- `spring/echo/EchoApplication.java`
  LINEイベントのentry point。テキスト・ポストバック・スタンプを受け取り、
  `TextCommandHandler`が組み立てたメッセージを返信します。
  5分間隔の`@Scheduled`でイラスト一覧の再取得も行います。
- `spring/echo/MainController.java` — `/callapi`のHTTP adapter。
- `spring/echo/SpecialVillageController.java` — `/specialvillage`のHTTP adapter。
- `spring/echo/ApiExceptionHandler.java` — 公開APIの内部エラーをHTTP 500へ丸める。
- `spring/echo/EchoImageEvent.java` — スタンプへの応答（問い合わせ先とホームページの案内）。

LINEと`/callapi`で村番号の判定基準が異なる点に注意してください。
LINEは101以上、`/callapi`は1000以上を村番号として扱い、それ未満は参加人数の設定になります。

### ゲームロジック

- `spring/game/TextCommandHandler.java`
  テキスト入力の解釈を1組だけ持つ共通入口。数値の境界、コマンド表、trimの扱いを
  ここへ集約します。対象の村がない場合は`null`を返し、呼び出し側が経路に応じた
  応答へ変換します。
- `spring/game/VillageService.java`
  村の作成・人数設定・お題設定・逆村化・参加・Werewords変換を担う、
  LINEと`/callapi`の共通層。
  対象の村が見つからない場合はすべて`null`を返し、呼び出し側が
  「村が作成されていません」相当の応答へ変換します。
- `spring/game/CreatVillage.java` — 特殊村の作成。
- `spring/game/SpecialVillage.java` / `SpecialVillageList.java` — 特殊村の状態とレジストリ。
- `spring/game/CreatWereWordsLogic.java`
  Werewords村の役職（占師・インサイダー・村人・GM）を抽選し、特殊村として登録する。
  先頭の役職は「欠け」として扱う。
- `spring/game/CommonSubLogic.java` — Werewordsの役職メッセージの組み立て。

### 状態

- `spring/entity/Village.java` — 通常村の状態、役職の割り当て、メッセージ生成。
- `spring/entity/InsiderRole.java` — 役職の定義。
- `staticdata/VillageList.java` — 通常村のstaticレジストリ。上限50件、超過分はFIFOで削除。
- `spring/game/SpecialVillageList.java` — 特殊村のstaticレジストリ。上限30件。

ゲーム状態はDBに保存せず、プロセスのメモリだけで管理します。
再起動・再デプロイで作成中の村は失われ、複数インスタンスでの共有もできません。

### 共通処理とデータ

- `common/CommonModule.java`
  Google Apps Scriptから役職イラストの一覧を取得し、ファイル名の重み付けに従って抽選します。
  取得に失敗した場合は前回取得した一覧を保持し、一度も取得できていない役職は`MessageConst`のGitHub Raw画像へfallbackします。
  URLは必ずApps Scriptのデプロイ URL（`/macros/s/<デプロイID>/exec`）を指定してください。
- `common/WordGetter.java` — `word.csv`から難易度別にお題候補を1件選ぶ。
- `staticdata/MessageConst.java` — 定型文と既定イラストのURL。
- `src/main/resources/word.csv` — お題データ。

## テスト

```bash
./gradlew :sample-spring-boot-echo:test
```

ゲーム状態は`VillageList` / `SpecialVillageList`のstaticレジストリで共有されるため、
各テストは`@Before`の`clear()`に依存しています。`build.gradle`で`maxParallelForks = 1`を指定し、
同一JVMでの逐次実行を前提にしています。並列化するにはレジストリをインスタンス化してDIする必要があります。

## ログ

webhook eventにはLINEユーザーIDとユーザーが入力したお題が含まれるため、event自体はログへ出力しません。
記録するのはevent種別と処理結果までです。受信イベントの種別を追う必要がある場合は、
次の環境変数でDEBUGへ引き上げます。個人情報そのものは引き上げても出力されません。

```bash
LOGGING_LEVEL_COM_EXAMPLE_BOT=DEBUG
```
