# refactor-instructions.md — インサイダーゲーム Bot リファクタリング指示書

この文書は、実装担当モデルが `sample-spring-boot-echo`（Bot 本体）の技術的負債を、既存仕様を壊さずに段階的に減らすための指示書である。作成日 2026-09-06。同日にプロダクトオーナーの回答（第 10 章）を反映済み。対象ブランチ `future`（PR 先は `3.0`）。

**承認済みの挙動変更は第 10 章に列挙した 8 件だけ**（Q1、Q2、Q3、Q4、Q5、Q6、Q7、Q10）。それ以外の挙動変更は禁止。Q8 は「現状を許容」、Q9 は「条件付きで DI 化を実施」。

---

## 1. Objective

- 外部から観測できる振る舞い（LINE への応答文、HTTP API の応答、配役規則、村番号の体系、上限値）を**一切変えずに**、Bot 本体の重複・責務混在・到達不能コード・脆い契約を減らす。
- 変更後に「LINE 経由」と「`/callapi` 経由」のゲーム規則が 1 箇所に集約され、入力の解釈と応答形式だけが経路ごとに異なる状態にする（`docs/overview.md` 設計原則 4 の徹底）。
- 見た目の整理を目的にしない。各フェーズは「テストで固定した挙動を保ったまま、次の変更を安全にする」ためにだけ行う。
- 大きな設計変更のうち、**レジストリとサービスの DI 化は最終フェーズ（Phase 8）で条件付きで実施する**（Q9）。返信の非同期化、認証の導入、SDK モジュール（`line-bot-model`/`api-client`/`servlet`/`spring-boot`）の整理は**実装せず提案に留める**。
- 承認済みの小さな挙動変更（`/callapi` の trim 統一、お題辞書の境界導出、難易度 1 経路の削除、Werewords の欠け表記の不具合修正、ビルド設定の整理、`line-bot-cli` の削除、役職画像カタログの要素単位スキップとタイムアウト）を、それぞれ独立コミットで行う。

## 2. Project Understanding

### 2.1 何を作っているか

会話ゲーム「インサイダーゲーム」「Werewords」で、参加者ごとに異なる秘密情報（役職とお題）を LINE の 1 対 1 トークで配るための Bot。ゲーム進行・勝敗判定・永続化・認証・課金は対象外。想定利用者は数十人規模の知人グループで、Heroku 上の単一プロセスで稼働中。

### 2.2 リポジトリ構成

Gradle マルチプロジェクト（Gradle 7.5、Spring Boot 2.1.5、Java 8 ソース互換）。

| モジュール | 役割 | main/test の Java ファイル数 |
| --- | --- | --- |
| `sample-spring-boot-echo` | **Bot 本体**。本指示書の対象 | 18 / 11 |
| `line-bot-spring-boot` | webhook 受け口と自動設定（SDK 由来） | 13 / 6 |
| `line-bot-servlet` | 署名検証と本文解析（SDK 由来） | 2 / 0 |
| `line-bot-api-client` | 返信 API クライアント（SDK 由来） | 27 / 15 |
| `line-bot-model` | メッセージ・イベントの型（SDK 由来。**独自クラス 3 つを追加済み**） | 123 / 18 |
| `line-bot-cli` | リッチメニュー等の運用ツール。**使われていないため削除対象（Q10）** | 19 / 2 |

SDK 部分は line-bot-sdk-java 2.7.0 のフォークで、依存として取得せずリポジトリに同梱している。Bot 本体が使う SDK 独自クラスは `ButtonsTemplateNonURL`、`ButtonsTemplateNonTitle`、`URIActionNonAltUri`（`line-bot-model`）で、いずれも上流にない。**SDK モジュールは本指示書の変更対象外**。

### 2.3 エントリーポイント

| 入口 | 実装 | 備考 |
| --- | --- | --- |
| `POST /callback` | `LineMessageHandlerSupport`（SDK）が署名検証済みイベントを `EchoApplication` の `@EventMapping` メソッドへ同期ディスパッチ | パスは `application.yml` の `line.bot.handler.path` |
| `GET /callapi?message=&userId=` | `MainController.index` | 外部フォーム向け。CORS 有効、無認証 |
| `POST /specialvillage` | `SpecialVillageController.create` | 特殊村作成。CORS 有効、無認証 |
| 5 分間隔ジョブ | `EchoApplication.createMap()`（static、`@Scheduled(fixedDelay=300000)`） | 役職画像カタログを Apps Script から再取得 |
| 起動時 | `WordGetter` の static 初期化で `word.csv`（8,436 行）を読み込む | 失敗時は空リスト |

### 2.4 主要モジュールと責務（現状）

```text
spring/echo/
  EchoApplication          Boot 起動クラス + LINE イベント受け口 + テキストコマンド判定
                           + 返信送信（reply/replyDefoltMessage）+ お題取得 UI + 定期ジョブ
  MainController           /callapi のアダプタ。コマンド判定を EchoApplication と別実装で持つ
  SpecialVillageController /specialvillage のアダプタ。入力検証（1〜100 件、5,000 文字）
  ApiExceptionHandler      公開 API 2 つの予期しない例外を HTTP 500 定型文へ丸める
  EchoImageEvent           スタンプ応答の組み立て
  WereWordEvent            Werewords 変換の入口（人数・お題の再検証 + 案内文）
spring/game/
  VillageService           経路共通のゲーム操作（作成・人数・お題・逆村・参加）。全部 static
  CreatVillage             特殊村の作成（メッセージを in-place shuffle して登録）
  CreatWereWordsLogic      Werewords の役職メッセージ列を組み立てて特殊村として登録
  CommonSubLogic           Werewords の役職文言テーブル
  SpecialVillage           特殊村の状態（setter 注入、join のみ synchronized）
  SpecialVillageList       特殊村の static レジストリ（上限 30、FIFO 削除、番号 10000〜99998）
spring/entity/
  Village                  通常村の状態。全メソッド synchronized。配役規則を内包
  InsiderRole              参加者 1 人分（index / role / userId / checkFlg）
staticdata/
  VillageList              通常村の static レジストリ（上限 50、FIFO 削除、番号 1000〜9999）
  MessageConst             応答文の定数、役職名、既定画像 URL、GM センチネル 999
common/
  CommonModule             役職画像カタログの取得と重み付き抽選（static 可変マップ）
  WordGetter               お題辞書の読み込みと難易度別抽選（行番号で難易度を表現）
```

### 2.5 データの流れ

1. LINE → `/callback` → 署名検証 → `EchoApplication.handleTextMessageEvent` → `userId` が null なら拒否 → `replyMessage` がテキストを解釈（数値なら境界値で分岐、文字列ならコマンド表）→ `VillageService` の static メソッド → `VillageList`/`SpecialVillageList` から村を取得 → `Village`/`SpecialVillage` が状態遷移とメッセージ生成 → `List<Message>` を返信 API で同期送信。`null` が返れば既定の確認テンプレートを返す。
2. 外部フォーム → `/callapi` → `MainController` が同様に解釈（境界値が異なる）→ 同じ `VillageService` → `List<Message>` を JSON で返す。`null` は `村が作成されていません` へ変換。
3. 外部フォーム → `/specialvillage` → 検証 → `CreatVillage` → `SpecialVillageList` → 5 桁番号を JSON で返す。
4. 状態はすべてプロセスメモリ。再起動で消える（意図した設計）。

### 2.6 外部依存

- LINE Messaging API（返信のみ。プッシュは使わない）
- Google Apps Script（役職画像カタログ。URL は `CommonModule.URL` にハードコード。デプロイ URL でなければならない）
- GitHub Raw（既定の役職画像。`MessageConst` の URL は **branch `3.0` の `Image/`** を指す）
- 外部フォーム `insidergametool.netlify.app`（`/callapi` と `/specialvillage` の呼び出し元。接続先は Heroku のホスト名に固定されている）

### 2.7 ドキュメント

`docs/` が外部仕様と設計判断の一次資料。`docs/game-spec.md`（ゲーム仕様）、`docs/interfaces.md`（API 契約と数値解釈）、`docs/architecture.md`（並行性・排他・既知の制約）は**契約として扱う**。`sample-spring-boot-echo/README.md` はクラス配置を列挙しているため、ファイルを動かしたら更新する。

## 3. Behaviors To Preserve

以下は既存テストまたは `docs/` で固定されている契約。**1 文字も変えない**（応答文は既存テストが完全一致で検証している）。

### 3.1 数値の解釈（経路で異なる。統一しない）

| 入力 | LINE | `/callapi` |
| --- | --- | --- |
| 10000 以上 | 特殊村へ参加 | 特殊村へ参加 |
| 1000〜9999 | 通常村へ参加 | 通常村へ参加 |
| 101〜999 | 村番号扱い（該当なし → 既定応答） | 人数設定 |
| 100 以下 | 人数設定 | 人数設定 |

現状、LINE は `trim()` 後に `parseInt`、`/callapi` は trim せずに `parseInt`（空白を含む数値はお題文字列になる）。**Q1 で「LINE 側に統一」が承認済み**。Phase 3b で `/callapi` も trim 後に数値判定する。それ以外の境界値は表のとおり維持する。

### 3.2 ポストバック data の解釈（LINE のみ）

0〜9: お題候補取得（userId 不要）。10〜9999: 通常村の入室状況。10000 以上: 特殊村の入室状況。数値以外: 既定応答。`@取得` は内部的に rank 10 で呼び、0 と 10 はいずれも「先頭 7,646 語」から引く。

### 3.3 コマンド表

- 村作成: `お題` / `題`（通常）、`神`（神モード）、`ランダム`（隠しコマンド。既定応答の選択肢に**含めない**）
- `@逆村` `＠逆村`、`@わーわーず` `＠わーわーず`、`@取得` `＠取得`、`@配布` `＠配布`、`@特殊` `＠特殊`（LINE のみ。`/callapi` ではすべてお題文字列になる）
- 上記以外の文字列はお題として「自分が所有し、お題未設定の最新の村」へ設定。対象がなければ既定応答。
- スタンプ → 製作者情報テンプレート。その他イベント → 無応答。

### 3.4 配役規則

- 人数確定時にインサイダー席（1..n）を抽選。神モード・ランダム村では GM 席も抽選し、インサイダーと同席なら引き直す。
- 参加順に着席。席番号と抽選席が一致すればその役職、他は村人。逆村ではインサイダー席が村人、他がインサイダー、GM は GM のまま。
- 人数は 2 以上。1 以下は `ERR_NUMSETMESSAGE`。人数・お題は一度だけ設定可。二度目は「対象の村なし」として `null`。
- 逆村は参加者 0 人かつランダム村でない村のみ。ランダム村は作成時に初心者お題を自動設定し、人数確定と同一モニタ内でオーナーを 1 番目に着席させ、その場で自分の役職を返す。
- 同一ユーザーの再参加は同じ役職の再表示。満員は `村がいっぱいです。`。
- Werewords 変換の対象は「自分が所有し、参加者 0 人の最新の村」で、かつ人数 3 以上・お題設定済み。条件を満たさなければ既定応答。神モードなら人数分、非神モードなら人数 + 1 通。`CreatWereWordsLogic.getMessages` が返す列の先頭は神モードで GM 向け、非神モードで村人だが、`CreatVillage` が登録時に**全体をシャッフルする**ため、参加者に配られる順序はランダム。LINE への応答は案内の `TextMessage` 1 通。役職メッセージの文言は `CommonSubLogic` の現行テーブルどおり。**例外は Q5 で不具合と確定した 1 点**: 欠けた役職が村人のとき、GM 向け文言の「欠け」表記を `村人` にする（現状は `あなたの役職は村人です` という文が埋め込まれる）。

### 3.5 レジストリ

通常村 50 件・特殊村 30 件を超えたら**最古から削除**。番号は空きからランダム採番（4 桁 1000〜9999、5 桁 10000〜99998）。採番と登録は同一ロック内。`findLatestOwned` は新しい方から検索し、古い村へ遡って条件を満たすものを探す。

### 3.6 メッセージ整形の閾値

- 通常村インサイダー/GM の画像付きボタン: 本文 60 文字以下。GM は 160 文字以下なら画像なしボタン。超過はテキスト + 入室状況の 2 通。
- オーナー配布状況: 160 文字以下ならボタン、超過はテキスト 1 通。
- 特殊村: 160 文字以下ならボタン、超過はテキスト + 入室状況。空・null メッセージは `メッセージは特にありません。`。

### 3.7 HTTP API の契約

- `/callapi`: `message` か `userId` が空 → 400（本文なし）。村なし → 200 で `村が作成されていません`。内部エラー → 500 `{"error":"内部エラーが発生しました。"}`。CORS 有効。
- `/specialvillage`: JSON 不正・`message` 欠落・空・101 件以上・5,000 文字超 → 400。正常 → `{"data":"<5 桁>"}`。`X-Content-Type-Options: nosniff`。null/空要素は許容。`@RequestMapping`（メソッド無指定）である点も維持。
- `ApiExceptionHandler` の適用範囲は `MainController` と `SpecialVillageController` のみ。`/callback` の署名検証結果に影響させない。

### 3.8 ログとセキュリティ

- 受信イベント本体・userId・お題を**ログに出さない**。出すのはイベント種別と処理結果まで。
- グループ/ルームで `userId` が null のイベントは状態を変更せず `ERR_UNIDENTIFIED_USER` を返す。ただしお題候補取得（postback 0〜9）は userId 不要なので応答する。
- 公開 API は無認証・レート制限なし（既知。本指示書では扱わない）。

### 3.9 並行性

`Village` の状態遷移（人数確定 + 配役抽選 + ランダム村オーナー着席、お題設定、逆村切替、参加）はインスタンスモニタ上で不可分。レジストリの採番・登録・検索はクラスロック上。**ロックの粒度と順序を変えない**（`VillageTest.concurrentJoinAndReverseSwitchNeverMixesRoles` が 200 回試行で検証）。

### 3.10 運用上の契約

- 役職画像カタログの取得失敗時は前回のマップを保持し、マップが無い役職は `MessageConst` の既定画像へフォールバック。
- `word.csv` は起動時に一度だけ読み込む。難易度ごとに引ける範囲は現行どおり（初心者: 1〜5084 行目、上級者: 955〜7646、変態: 7647〜8436、指定なし: 1〜7646）。Q3 承認により境界は CSV の 2 列目から導出する実装へ変えるが、**現在の CSV では算出結果が上記と同一**であることをテストで固定する。難易度 1（先頭 954 語）の経路は Q4 承認により削除する。

### 3.11 既知の docs と実装の差分（触らない。報告のみ）

レビューで判明した差分。いずれも**実装の挙動を維持**する。docs 側は 2026-09-07 にオーナーの指示で実装に合わせて修正済み（`docs/interfaces.md`、`docs/operations.md`、`docs/architecture.md`、`sample-spring-boot-echo/README.md`）。実装担当モデルは docs の記述を契約として扱い、これらを 500 や既定画像へ「直す」ことはしない。

- `POST /specialvillage` の内部エラー: `SpecialVillageController.create` は村作成を含む全体を `catch (Exception)` で 400 に丸めている。`ApiExceptionHandler` の 500 が実際に出るのは `/callapi` だけ。docs はこの挙動を記述するよう修正済み。
- 役職画像カタログの取得失敗: `CommonModule.createMap` は前回取得したマップを保持し、その中に対象役職の画像がない、または抽選できない場合に既定画像を使う。docs はこの挙動を記述するよう修正済み。本指示書 3.10 はこの実装挙動を維持対象としている。

## 4. Non-Negotiables

1. **最初に `git status` を確認**し、未コミット変更があれば自分の変更と混ぜない（別ブランチまたは stash の判断を報告してから進める）。
2. **編集前に第 6 章の Baseline Commands を実行し、結果を記録**する。ベースラインが失敗するなら、その原因を修正せずに報告して止まる。
3. 変更は**小さく戻しやすい単位**（1 フェーズ = 1 コミット以上、1 コミット = 1 目的）。各コミット後に第 9 章の検証を通す。
4. **無関係な整形・ついでのリファクタリングをしない**。触っていないファイルのフォーマットや import 順を変えない。SDK モジュール（`line-bot-model`/`api-client`/`servlet`/`spring-boot`）の**コード**は触らない。例外として、それらの `README.md` が Bot 本体のクラス名・メソッド名を参照している箇所（`line-bot-api-client/README.md` の `EchoApplication#reply`、`line-bot-spring-boot/README.md` の `EchoApplication` の例とログ）は、改名・移動したコミットで参照だけ更新する。`docs/superpowers/specs/` の過去の設計書は履歴なので更新しない。`line-bot-cli` は Phase 3b で丸ごと削除する以外に手を入れない。
5. **既存挙動を勝手に変えない**。応答文字列、数値境界、上限値、乱数の使い方（`Random` からの呼び出し回数と順序を含む。テストが `FixedRandom` で順序を固定している）を変えない。例外は第 10 章で承認された変更だけで、それぞれ独立コミットとテストを伴う。
6. **正しさが不明なら実装を止めて質問する**（第 5 章）。
7. `-Werror -Xlint:all` が有効。警告を抑制アノテーションで黙らせて通さない（既存の `@SuppressWarnings` は維持してよい）。
8. Java 8 ソース互換を維持する（`var`、`List.of`、`String.repeat` などを使わない。本番は Java 8 で稼働）。
9. 新しい依存を追加しない。Lombok は既に使えるが、`@Value`/`@Data` を状態クラスへ新規適用して同期化を崩さない。
10. 秘密情報（userId、お題、イベント本体）をログ・例外メッセージ・HTTP 応答に載せるコードを書かない。
11. `docs/` は契約。コードを docs に合わせるのが原則で、docs を書き換えるのは第 10 章で承認された範囲（Q3 の辞書境界、Q6 のビルド設定、Q10 の CLI 削除、Q1 の trim 統一の追記）だけ。docs の更新は対応するコード変更と同じコミットで行う。
12. ファイルを移動・改名したら `sample-spring-boot-echo/README.md` の該当行を同じコミットで更新する。

## 5. Stop And Ask Conditions

次に該当したら、そのフェーズを中断し、状況と選択肢を報告して回答を待つ。

- 既存テストの期待値を変えないと通らない変更が必要になった。
- `docs/` と実装が食い違っていることに気づいた（第 10 章で判断済みの Q2、Q5、Q8 を除く新発見）。
- 削除しようとしているコードが本当に未使用か確信できない（リフレクション、Jackson のシリアライズ対象、SDK からの呼び出しの可能性を含む）。
- `/callapi`、`/specialvillage`、`/callback` の応答形式・ステータス・ヘッダに影響する可能性がある。
- `Village`/`SpecialVillage`/レジストリのロック粒度、`synchronized` の有無、`Random` の呼び出し順に触れる必要が出た（D7 の読み取りメソッドの `synchronized` 追加と、Phase 8 の `static synchronized` → `synchronized` は計画済みなので除く）。
- Phase 3b で定めた範囲、および Phase 8 の `maxParallelForks = 1` 削除を超えて `build.gradle`、CI、`application.yml`、`Procfile` を変える必要が出た。
- Phase 8（DI 化）で、Spring コンテキスト起動、`@Scheduled`、`LineMessageHandlerSupport` のハンドラ探索、ロックの契約のいずれかに影響が出た、または 4 コミットで収まらない見込みになった。
- 変更が 1 コミットで 300 行を超えそう、または 5 ファイル以上にまたがる。ただし Phase 3b-1（`line-bot-cli` の削除）、Phase 3b-2（ビルド設定の整理）、Phase 8 の各コミットは性質上これを超えるので対象外とし、代わりに各フェーズに書いた分割単位を守る。
- ベースラインの `check` が失敗している。

## 6. Baseline Commands

このリポジトリには checkstyle/spotbugs は**適用されていない**（`config/checkstyle/` は残骸で、どの `build.gradle` からも参照されていない）。実質的な lint は `compileJava` の `-Xlint:all -Werror`。

```bash
# この Mac では java が PATH にない。Homebrew の JDK 11 を使う（本番・CI は JDK 8）
export JAVA_HOME=/opt/homebrew/opt/openjdk@11
export PATH="$JAVA_HOME/bin:$PATH"
java -version
```

```bash
git status --short && git log --oneline -3
```

```bash
# Bot 本体のテスト（2026-09-06 のベースライン: 11 クラス 73 テスト成功、約 10 秒）
./gradlew --no-daemon :sample-spring-boot-echo:test
```

```bash
# CI と同じ全体チェック（ベースライン: 5 モジュール計 201 テスト成功。model 41 / api-client 64 / cli 5 / spring-boot 18 / echo 73）
./gradlew --no-daemon check
```

```bash
# CI と同じ成果物生成（ベースライン: sample-spring-boot-echo/build/libs/sample-spring-boot-echo-2.7.0-SNAPSHOT.jar 生成）
./gradlew --no-daemon :sample-spring-boot-echo:bootJar
```

テスト結果の確認先: `sample-spring-boot-echo/build/test-results/test/*.xml`（`tests=` `failures=` `errors=`）。

Phase 3b で `line-bot-cli` を削除すると `check` の合計は 196 テスト（201 − cli の 5）になる。それ以降はこの値を新しいベースラインとして報告する。

注意: `sample-spring-boot-echo/build.gradle` で `maxParallelForks = 1`。static レジストリを `@Before` の `clear()` で共有しているため、テストの並列化はしない。CI（`.github/workflows/ci.yml`）は `3.0`/`develop` への push と PR で JDK 8 により `check` と `bootJar` を実行する。`future` ブランチへの push では走らないので、PR を作るまでローカル検証が唯一の安全網。

## 7. Debt Map

各項目: 根拠 / なぜ負債か / 影響範囲 / 変更リスク / 改善案 / 検証 / 判定（**実装可** = 実装してよい、**条件付き実装可** = 指定フェーズの着手条件を満たしたら実装、**提案のみ** = 実装しない）。第 10 章の質問はすべて回答済みで、判定はそれを反映している。

### D1. テキストコマンド解釈の二重実装

- 根拠: `EchoApplication.replyMessage`（`お題`/`題`/`神`/`ランダム`/`@…` の判定）と `MainController.nonNumberMessage`（`お題`/`題`/`神`/`ランダム`）。数値境界も両方にハードコード（`> 9999`、`> 100` / `> 999`）。
- なぜ負債か: コマンド追加時に片方だけ直すと経路間で規則が分岐し、`docs/architecture.md` が指摘するとおり誰も気付けない。境界値の差は意図的だが、コマンド文字列の差は意図的ではない（`@` コマンドが `/callapi` で使えないのは記録済みの制約）。
- 影響範囲: 両アダプタ。
- 変更リスク: 中。文字列一致の順序と `trim()` の有無を変えると挙動が変わる。
- 改善案: `spring/game` に純粋な解釈器（例 `MessageInterpreter`）を置き、「入力文字列 + 経路ごとの境界値」→「操作の種別と引数」を返す。`@` コマンドは LINE 専用として解釈器の別メソッドか、LINE アダプタ側に残す。両アダプタは解釈結果を `VillageService` へ渡すだけにする。境界値は各アダプタが定数として渡す（統一しない）。
- 検証: `MainControllerTest` 全件 + Phase 2 で追加する LINE テキスト経路テスト。
- 判定: **実装可**（Phase 4）。Phase 3b-4 で `/callapi` の数値判定は trim 後に統一済みなので、解釈器は「trim した文字列で数値判定とコマンド判定を行い、お題として渡す文字列は未 trim のまま」とする（両経路で同一）。

### D2. `EchoApplication` の責務混在

- 根拠: 1 クラスに `@SpringBootApplication`（起動）、`@LineMessageHandler`（受け口）、返信送信 2 種（`reply` と `replyDefoltMessage` が同じ try/catch を重複）、お題取得 UI（`getOdaiDetail`）、`@配布`/`@特殊` のメッセージ組み立て、`@Scheduled` 定期ジョブが同居。`lineMessagingClient` はフィールド注入で、テストは `ReflectionTestUtils.setField` で差し込んでいる。
- なぜ負債か: LINE 固有のメッセージ組み立てとゲーム規則呼び出しが同じメソッドにあり、テストがモッククライアント経由でしか書けない。
- 影響範囲: `EchoApplication` と 2 つの既存テスト。
- 変更リスク: 中。`@EventMapping` の探索は `@LineMessageHandler` を付けた Bean の宣言メソッドを走査するため、ハンドラを別 Bean に移す場合はそのクラスに `@LineMessageHandler` を付け、`EchoApplication` からは外す（`LineMessageHandlerSupport` は優先度順の候補から `findFirst()` で 1 つだけ選ぶので、両方に付けると意図しない方のハンドラが選ばれ得る）。**Bean のスキャン範囲に注意**: `@SpringBootApplication` は起動クラスのパッケージ `com.example.bot.spring.echo` 配下しか走査しない。`common` や `spring.game` に `@Component` を置くなら `@SpringBootApplication(scanBasePackages = "com.example.bot")` を指定する（Phase 8 でも同じ）。
- 改善案: (a) 返信送信を 1 メソッドに統合し、既定応答は `List<Message>` を組み立てて同じ送信経路に通す。(b) LINE ハンドラを `LineEventHandler`（仮）として分離し、コンストラクタ注入にする。`EchoApplication` は `main` と `@EnableScheduling` だけ残す。(c) 定期ジョブは `CommonModule` 側か小さな `@Component` へ。
- 検証: 既存 `EchoApplicationGroupEventTest`/`EchoApplicationPostbackTest` をコンストラクタ注入へ書き換え（期待値は不変）+ Phase 2 の追加テスト。Spring コンテキスト起動テストを 1 本追加（`@SpringBootTest`。ダミーの `line.bot.channel-token`/`channel-secret` をプロパティで与え、`LineMessagingClient` を `@MockBean` にし、`@Scheduled` を持つカタログ取得コンポーネントも `@MockBean` にして**外部 HTTP を出さない**。実際に取得すると static マップが埋まり、既定画像を期待する `CommonModuleTest`/`VillageServiceTest` が同じ JVM で壊れる）。`LineMessageHandlerSupport` の登録数が 4 で、テキスト・ポストバック・スタンプ・既定の各イベントに対して選ばれるハンドラの型が新クラスであることを確認する。SDK の `IntegrationTest` は `line.bot.handler.enabled=false` でハンドラを無効化しているので流用しない。
- 判定: **実装可**（Phase 4〜5）。

### D3. `Village.setInsiderRole` の重複ループと再検索

- 根拠: 逆村用と通常村用にほぼ同一の for ループが 2 本。`join` が `roleList.add` 直後に同じ userId を線形探索して席番号を確定する。
- なぜ負債か: 配役規則の変更点が 2 箇所に散り、逆村の規則が「通常村の役職名を入れ替えたもの」であることがコードから読めない。
- 影響範囲: `Village` のみ。
- 変更リスク: 低〜中。`VillageTest` が席順・逆村・神モード・ランダム村・並行性を網羅している。
- 改善案: `join` で席番号を確定し、`roleFor(int seat)` のような私的関数で「席 → 役職名」を 1 箇所で決める。**席番号は 1 始まり**（`insiderNum`/`gmNum` は `nextInt(size) + 1` で 1 始まり、現行比較は `i + 1`）。add 前の `roleList.size()` は 0 始まりの添字なので、`seat = roleList.size() + 1` として渡すか関数内で補正する。1 ずれるとインサイダー不在の村ができる。逆村はインサイダーと村人の入れ替えとして表現する。`synchronized` の範囲は変えない。
- 検証: `VillageTest`、`VillageServiceTest` 全件。
- 判定: **実装可**（Phase 4）。

### D4. `gmNum` のセンチネルによる「神モード」表現

- 根拠: `MessageConst.DEFAULT_GMNUM = 999` を「神モードだが人数未確定」の印として使い、人数確定後は席番号で上書き（`Village.configure`）。判定が `== DEFAULT_GMNUM`（`VillageService.setVillageSize`/`setOdai`）と `!= 0`（`WereWordEvent`）に分かれている。
- なぜ負債か: 同じ概念に 2 つのエンコーディングがあり、読み手が「999」の意味を追わないと理解できない。人数確定後にお題を設定すると `setOdai` は `OWNER_NUMSETMESSAGE`（人数入力を促す文）を返す（Q8）。
- 影響範囲: `Village`、`VillageService`、`WereWordEvent`。
- 変更リスク: 中。**現在の判定結果を変えてはならない**。
- 改善案: `Village` に意味を名前で表す問い合わせを追加する（例 `isGodMode()` = 作成時に神モード指定あり、`isGodModeAwaitingSize()` = 現在の `gmNum == 999` 判定と同値）。呼び出し側は**現在と同じ条件**をその名前で呼ぶだけにし、条件自体は変えない（Q8 で現状許容と確定）。`DEFAULT_GMNUM` の値と `configure` の抽選ロジックは触らない。
- 検証: `VillageServiceTest.godModeUsesTheGodIllustrationAndNormalModeUsesGm` ほか全件。Phase 2 で「人数→お題の順に設定した場合の応答文」を現状のまま固定するテストを追加してから着手。
- 判定: 名前付け部分は**実装可**（Phase 5）。**Q8 で「現状の文言は許容」と確定**したため、条件（どの文言を返すか）は変えない。

### D5. Werewords 経路の検証重複と到達不能分岐

- 根拠: `EchoApplication` が `village != null && getVillageSize() > 2 && getOdai() != null` を判定した後に `WereWordEvent.branch` が同じ判定を繰り返す。したがって `WereWordEvent` 内の `MessageConst.WEREWORD_ERR` 分岐は到達不能。`MessageConst.WEREWORD_DEFOLT`、`WereWordEvent.DEFOLT_MESSAGE` は参照ゼロ。
- なぜ負債か: 「対象の村がない」を `null` で返して呼び出し側が既定応答へ変換するという他操作と同じ規約に乗っていない。
- 影響範囲: `EchoApplication`、`WereWordEvent`、`CreatWereWordsLogic`。
- 変更リスク: 低。
- 改善案: `VillageService.convertToWerewords(userId)`（仮）を追加し、`findLatestOwned(userId, 参加者なし)` → 条件不足なら `null` → 変換して案内文を返す、を 1 箇所に集約。`WereWordEvent` は削除。案内文（神モード/非神モードの 2 種）は現行文字列を維持。
- 検証: Phase 2 で `@わーわーず` の LINE テスト（対象なし → 既定応答、条件不足 → 既定応答、神モード → 人数分、非神 → 人数 + 1 通、元の村は残る、新番号は 5 桁）を追加してから着手。
- 判定: **実装可**（Phase 4）。ただし `CommonSubLogic` の文言は Q5 の回答まで変えない。

### D6. 未使用・到達不能コード

- 根拠（参照ゼロを grep で確認済み）:
  - `MessageConst.WEREWORD_DEFOLT`、`MessageConst.OWNER_CONFMESSAGE`、`WereWordEvent.DEFOLT_MESSAGE`
  - `InsiderRole.checkFlg`（getter/setter のみ）、`InsiderRole.index`（`setIndex` は呼ばれるが `getIndex` の参照ゼロ）
  - `Village.getRoleMessage` の `searchWord` 計算（コメントアウトされた「ググる」ボタンの残骸。5 行）
  - `SpecialVillage.ownerId`（`"DEFOLT"` を set するだけで読み取りゼロ）
  - `WordGetter.getWord` の `rank == 1` 分岐（UI から送られる rank は 0、2、3、4、10 のみ。ただし公開 static メソッドの引数なので、`WordGetterTest.everyDifficultyDrawsAWord` が rank 1 を呼んでいる）
  - `CommonModule.getIllustUrl` の `catch (Exception)`: 初回 `createMap` 前は `illustrationRtioMap` が null で NPE を経由してフォールバックしている
- なぜ負債か: 読み手が「使われているかもしれない」と追跡するコストを毎回払う。
- 変更リスク: 低。ただし `InsiderRole` は Jackson でシリアライズされないことを確認済み（`Message` 型にはならない）。`WordGetter` の rank 1 はテストが呼ぶので、削除するならテストも合わせる（テストの期待を変えるのではなく、rank 1 のケースを削る）。
- 改善案: 上記を削除。`CommonModule` は 2 つのマップを空の `HashMap` で初期化し、NPE 経由をやめる（`containsKey` が false になり同じ既定画像へ落ちるので挙動同一）。
- 検証: `:sample-spring-boot-echo:test`、`CommonModuleTest.fallsBackToTheFixedIllustrationWhileTheCatalogIsUnavailable`。
- 判定: **実装可**（Phase 3）。`WordGetter` の rank 1 経路も **Q4 で削除が承認済み**。`WordGetterTest.everyDifficultyDrawsAWord` の rank 1 ケースを削り、`getWord` の Javadoc から「1」を消す。

### D7. `SpecialVillage` の脆い契約

- 根拠: 状態を setter で注入（`setMessageList`、`setUserList`）し、`userList` を `CopyOnWriteArrayList` にするのは呼び出し側（`CreatVillage`、テスト 2 箇所）の責任。`setUserList` を忘れると `join` で NPE。`join` は `synchronized` だが `getRoleMessage`/`getStatusMessage`/`hasMember` は非同期化（現状は COW リストのため実害なし）。`Village` とは対照的。
- なぜ負債か: 不変条件（`userList.size() <= messageList.size()`、添字対応）がクラス外に漏れている。
- 影響範囲: `SpecialVillage`、`CreatVillage`、`SpecialVillageTest`、`SpecialVillageControllerTest`、`CreatWereWordsLogicTest`。
- 変更リスク: 低〜中。
- 改善案: `SpecialVillage(List<String> messages)` コンストラクタで**コピーした**メッセージ列を保持し、`userList` を内部で初期化。`setMessageList`/`setUserList`/`setOwnerId`/`getOwnerId` を削除。`villageNum` はレジストリが採番して set するので `setVillageNum` は残す。読み取りメソッドも `synchronized` にして `Village` と揃える（ロックは同じインスタンスモニタなので粒度は変わらない）。
- 検証: 上記テスト全件（`villageOf` ヘルパをコンストラクタ利用に書き換える。期待値は不変）。
- 判定: **実装可**（Phase 5）。

### D8. `CreatVillage.createNewVillage` が呼び出し元のリストを破壊的にシャッフル

- 根拠: `Collections.shuffle(messageList)` を引数に直接適用（Javadoc に明記あり）。`SpecialVillageController` は Jackson が作ったリストを渡すので現状は無害。
- 改善案: `new ArrayList<>(messageList)` を作ってからシャッフル。D7 と同時に行う。
- 検証: `SpecialVillageControllerTest`、`CreatWereWordsLogicTest`。
- 判定: **実装可**（Phase 3）。

### D9. `CommonModule` の堅牢性

- 根拠: (a) raw `Map`/`List` を `@SuppressWarnings` で握る。(b) 要素ループ内の `Integer.parseInt(fileNameArray[1])` が 1 件でも失敗すると `catch (Exception)` で**カタログ全体の更新を中断**し、前回のマップを維持する。`docs/interfaces.md` は「この形式でない要素は無視される」と書いている（Q2）。(c) `RestTemplate` に接続・読み取りタイムアウトがなく、Apps Script が応答しないとスケジューラスレッドが張り付く。(d) `illustrationRtioMap` は「重みぶん添字を並べたリスト」で、抽選は `nameList.get(random.nextInt(size))`。
- 影響範囲: `CommonModule` のみ。
- 変更リスク: (b) は挙動変更だが Q2 で承認済み。(c) は失敗モードの変更のみ（成功時の挙動は不変）で、値は Q7 で確定済み。(a)(d) の型付け・データ構造整理は挙動不変。
- 改善案: 型付き DTO（`files[].name`, `files[].url`）で読み、重みは `Map<String, List<WeightedUrl>>` のような形にして抽選は重みの累積で行う。**抽選の確率分布は現在と同一に保つ**: 重み w > 0 のファイルが選ばれる確率 = w / Σw。現行は `for (i < weight)` で枠を並べるため、**重み 0 と負数は枠を 1 つも持たず選ばれない**。その役職の総重みが 0 なら `nextInt(0)` の例外経由で既定画像に落ちているので、新実装でも総重み 0 は既定画像とする。static 可変マップは `volatile` な不変マップ参照に置き換える（現在の「更新は差し替え」方式を明示化。挙動同一）。
- 検証: `CommonModuleTest` 全件 + 「name が 3 部構成でない要素は無視」「重み 1 と 3 のとき分布が 1:3 に近い」を `Random` 差し替えで検証する単体テストを追加。
- 判定: (a)(d) は**実装可**（Phase 5）。(b)(c) も**承認済み**（Q2、Q7）で Phase 5 で実施する。
  - (b) 重みが整数でない、または `name`/`url` が欠けた要素は**その要素だけ**スキップして残りを取り込む（`docs/interfaces.md` の記述どおり）。スキップした件数を `log.warn` に出す（ファイル名は画像名なので出してよい。userId・お題は含まれない）。
  - (c) `RestTemplate` を `SimpleClientHttpRequestFactory` で構築し、**接続タイムアウト 15 秒、読み取りタイムアウト 30 秒**を設定する（新規依存なし）。タイムアウト時は既存の `catch` で `log.warn` して前回のマップを維持し、次回（5 分後）の再取得に進む。`@Scheduled(fixedDelay=300000)` は前回の完了から 5 分後に起動する。読み取りタイムアウトは 1 回の読み取り待ちに対する上限なので通信全体の上限にはならないが、無応答で永久に固まる事態は防げる。
  - テスト: 解析部分を HTTP から切り離した関数（`Map` を受け取る）にし、「3 部構成でない要素は無視」「重みが整数でない要素だけ無視して他は取り込む」「重み 1:3 の分布（固定乱数で境界を検証）」「重み 0・負数は選ばれない」「総重み 0 と空カタログは既定画像」を単体テストで固定。タイムアウト値は定数として公開し、テストで値を固定する。

### D10. `WordGetter` の行番号依存

- 根拠: 難易度境界が `SECOND_LINE=954`、`THIRD_LINE=5084`、`FOURTH_LINE=7646`、`FIFTH_LINE=8436` の行番号定数。`docs/architecture.md` が「お題辞書が行番号に依存する」を既知の制約として記載。一方 `word.csv` の 2 列目には難易度（1〜5）が入っており、**2 列目の値が切り替わる行（61/62、954/955、5084/5085、7646/7647、末尾 8436）が上記定数と完全に一致する**ことを確認済み（rank1: 61 語、rank2: 893 語、rank3: 4130 語、rank4: 2562 語、rank5: 790 語）。
- なぜ負債か: 辞書の行を増減すると境界がずれるが、辞書には既に境界の情報がある。
- 改善案: 読み込み時に 2 列目から難易度ごとの終端行を算出し、定数を置き換える。現在の CSV では算出値が定数と一致するため**外部挙動は同一**。`WordGetterTest` に「算出した境界が 954/5084/7646/8436 である」テストを追加して等価性を固定する。`docs/architecture.md`・`docs/interfaces.md` の「行番号に依存する」記述の更新が伴う。
- 変更リスク: 低（等価性テストで固定できる）。
- 判定: **実装可**（Phase 3b。**Q3 で承認済み**）。実装上の要件: 読み込み時に 2 列目を整数として読み、CSV 難易度の値ごとに最終行番号を記録する。**公開 rank と CSV 難易度は同じ番号ではない**。対応は次のとおりで、これを変えない。

  | 公開 rank（ボタン/引数） | 現在の範囲（行） | CSV 難易度での表現 |
  | --- | --- | --- |
  | 2（初心者） | 1〜5084 | 難易度 1〜3 の全行 |
  | 3（上級者） | 955〜7646 | 難易度 3〜4 の全行 |
  | 4（変態） | 7647〜8436 | 難易度 5 の全行 |
  | それ以外（0、10 など。指定なし） | 1〜7646 | 難易度 1〜4 の全行 |

  つまり必要な境界は「難易度 2 の最終行（954）」「難易度 3 の最終行（5084）」「難易度 4 の最終行（7646）」「難易度 5 の最終行（8436）」。CSV は難易度の昇順である前提とし、**その前提はテストで保証する**（`WordGetterTest` に「2 列目が単調非減少」「算出した 4 境界 = 954/5084/7646/8436」「語数 = 8436」「各 rank の抽選区間が上表と一致」を追加）。実行時に 2 列目が整数でない行や昇順を破る行があれば、**新しい補完処理は入れず**、`log.error` して空リストにする（`getWord` は `null` を返す）。これは**今回新設する契約**で、現在リソースが見つからない場合に空リストを返す経路と同じ結果になる。既存の「途中で `IOException` が起きたら読み込めた行まで返す」挙動は変えない。`WordGetterTest.csvWords(WordGetter.THIRD_LINE)` が定数を参照しているので、境界取得の新 API へ置き換える。`docs/architecture.md` の既知の制約「お題辞書が行番号に依存する」を削除し、`docs/interfaces.md` の「行を追加・削除・並べ替えると難易度の境界がずれる」を「2 列目の難易度で範囲が決まる。難易度の昇順に並べる必要がある」へ書き換える。`docs/game-spec.md` の語数表は現在の CSV の実測値なので変更不要。

### D11. static レジストリと static サービス

- 根拠: `VillageList`、`SpecialVillageList` が static 状態、`VillageService` が全 static。`sample-spring-boot-echo/build.gradle` のコメントが「並列化するにはレジストリをインスタンス化して DI する必要がある」と自認。
- なぜ負債か: テストの直列化強制、コンテキストごとの隔離不可、依存の隠蔽。
- 変更リスク: 高。全アダプタ・全テストに波及し、Spring Bean のライフサイクルと `@Scheduled`、`LineMessageHandlerSupport` の探索との相互作用を検証する必要がある。
- 改善案（提案のみ）: `VillageRegistry`/`SpecialVillageRegistry` を `@Component` 化し、`VillageService` をインスタンス化して両アダプタへコンストラクタ注入。テストは `new` で隔離。移行中は同期化の契約（クラスロック → インスタンスロック）が保たれることを `VillageListTest` 相当で確認。
- 判定: **条件付き実装可**（Phase 8。**Q9 で「問題なければ検討したい」との回答**）。Phase 1〜6 がすべて完了し検証が通った後に、独立したコミット列で行う。Phase 4〜5 の設計は、この変更を**妨げない**形（`VillageService` の呼び出しを 1 箇所に集める、レジストリへの直接アクセスをアダプタから減らす）にしておく。詳細は Phase 8 を参照。

### D12. 命名の誤記と揺れ

- 根拠: `DEFOLT`/`DEFAILT`/`Creat*`/`Rtio`/`umeji`/`Messe` など 31 箇所（main のみ）。`replyDefoltMessage`、`CreatVillage`、`CreatWereWordsLogic`、`illustrationRtioMap`。
- なぜ負債か: 検索性が落ち、新規参加者が typo を仕様と誤解する。
- 変更リスク: 低（IDE のリネーム相当。ただし公開 API・JSON・ログ文には現れない）。
- 改善案: **責務分離で触るクラスに限って**その機会に改名する。改名だけのコミットを作らない（Non-Negotiable 4）。クラス改名・移動時は `sample-spring-boot-echo/README.md` と、SDK モジュールの README にある参照（Non-Negotiable 4 の例外）を同じコミットで更新。
- 判定: **実装可**（Phase 4〜5 の中で。単独では行わない）。

### D13. ビルド設定と補助ファイルの残骸

- 根拠: ルート `build.gradle` に SDK 公開用の `maven-publish`/Sonatype 設定、Kotlin プラグイン classpath、`gradle-versions-plugin`、`codeCoverageReport`（**`sample-spring-boot-echo` を集計対象から除外**）。`.codecov.yml`、`config/checkstyle/`、`config/findbugs/` はどこからも参照されない。`.gitignore` が `*.json`/`*.yaml`/`*.script`/`*.bin` を無視する一方で `app.json` や SDK のテストリソース JSON は追跡済み（強制追加の履歴）。`app.json` の説明が「echo bot sample」のまま。`.github/ISSUE_TEMPLATE*` は上流の残骸。
- なぜ負債か: 「何が生きている設定か」を毎回調べる必要がある。
- 変更リスク: 低〜中。ビルド設定はローカル JDK 11 と CI JDK 8 の両方で確認が必要。
- 判定: **実装可**（Phase 3b。**Q6 で承認済み**）。実施範囲は Phase 3b に列挙したものに限る。ローカル JDK 11 で `check` と `bootJar` が通ることを各コミットで確認し、JDK 8 での確認は PR の CI に委ねる旨を報告に書く。

### D14. テストの薄い箇所と重複

- 根拠: LINE テキスト経路（`EchoApplication.replyMessage`）の正常系（村作成 → お題 → 人数 → 参加 → 配布状況、`@逆村`、`@わーわーず`、`@取得`、`@配布`、`@特殊`、101〜999 の既定応答、スタンプ）を直接固定するテストがない。`/callapi` 経由（`MainControllerTest`）で `VillageService` は覆われているが、LINE 側の分岐とメッセージ組み立ては未固定。`FixedRandom` が `VillageTest`/`VillageServiceTest` に、`repeat` が `SpecialVillageTest`/`SpecialVillageControllerTest` に重複。
- 改善案: Phase 2 で `EchoApplicationTextCommandTest`（仮）を追加。モック `LineMessagingClient` で `ReplyMessage` を捕捉し、返信の型・件数・altText/本文・ボタンの `data`/`text` を検証する。テストヘルパは `src/test/java/com/example/bot/testing/`（仮）へ 1 つずつ集約。
- 判定: **実装可**（Phase 2）。

### D15. 数値境界のハードコード

- 根拠: `EchoApplication`（`> 9999`、`> 100`、postback の `< 10`、`< 10000`）、`MainController`（`> 9999`、`> 999`）、レジストリ（`8999 + 1000`、`89999 + 10000`、`<= 99998`）。
- 改善案: 意味のある名前の定数へ（例: LINE 側 `MAX_SIZE_INPUT = 100`、API 側 `MAX_SIZE_INPUT = 999`、`MIN_SPECIAL_VILLAGE_NUM = 10000`）。**値は変えない**。D1 の解釈器導入時に一緒に行う。
- 判定: **実装可**（Phase 3〜4）。

### D16. パッケージ配置の不揃い

- 根拠: 通常村のレジストリは `staticdata/VillageList`、特殊村のレジストリは `game/SpecialVillageList`。状態クラスも `entity/Village` と `game/SpecialVillage` に分かれる。`MessageConst` が `staticdata` にある。
- 改善案: 状態とレジストリを同じパッケージ（例 `game`）へ純粋移動。挙動不変。
- 変更リスク: 低。ただし差分が大きくレビュー負荷になるため、他の変更と混ぜない独立コミットにする。
- 判定: **実装可**（Phase 5 の最後、任意）。

### D18. Werewords の GM 向け「欠け」表記の不具合（Q5 で不具合と確定）

- 根拠: `CommonSubLogic.WEREWORDS_ROLE_MAP` が `{ "", "占師", "インサイダー", "あなたの役職は村人です", "村人", "GM" }`。`CreatWereWordsLogic.getMessages` は役職番号 1・2・3 のリストをシャッフルし、先頭の番号を `getWereRole` に渡す。先頭が 3（村人）のとき GM 向けメッセージは `役職は「あなたの役職は村人です」が欠けています。` になる。添字 4 と 5 は参照ゼロ。
- 影響範囲: 神モードから変換した Werewords 村の GM 向けメッセージ 1 通のみ。
- 変更リスク: 低。
- 改善案: テーブルを `{ "", "占師", "インサイダー", "村人" }` に修正し、未使用の添字を削除。`getWereMesse`/`getWereRole` の番号体系（1 占師、2 インサイダー、3 村人、4 GM）は変えない。
- 検証: `CreatWereWordsLogicTest` に「`getWereRole(3)` が `村人`」「神モードの先頭メッセージに `あなたの役職は` が 2 回現れない」を追加。シャッフルに seam がないため、先頭が 3 になるまで `getMessages` を繰り返して確認するか、`getWereRole(3)` を直接検証する。
- 判定: **実装可**（Phase 3b）。

### D19. `line-bot-cli` の削除（Q10 で承認）

- 根拠: 運用で使っていないとの回答。Bot の実行に不要（`sample-spring-boot-echo` は `line-bot-spring-boot` にのみ依存）。ルート `build.gradle` の `codeCoverageReport` と `settings.gradle`、`README.md`、`docs/architecture.md`（モジュール構成の図と「CLI は運用ツール」の記述）が参照している。
- 影響範囲: ビルド構成とドキュメント。Bot の実行時挙動には影響なし。
- 改善案: `line-bot-cli/` ディレクトリを `git rm -r` し、`settings.gradle` の `include 'line-bot-cli'`、`codeCoverageReport` の `':line-bot-cli'`、README と docs の記述を同じコミットで削除。`line-bot-cli/build.gradle` だけが使っていた `signing` プラグインと `launch.script` も一緒に消える。
- 検証: `./gradlew --no-daemon check`（直前の件数 − 5）と `bootJar`。残存参照の確認は `grep -rn 'line-bot-cli' settings.gradle build.gradle README.md docs/*.md sample-spring-boot-echo/README.md` がゼロ件であること（本指示書、`LEARNINGS.md`、`docs/superpowers/specs/` の過去の設計書、`build/` は履歴・生成物なので対象外）。
- 判定: **実装可**（Phase 3b）。

### D17. 触らない（記録のみ）

- 返信の同期送信と LINE の 2 秒制限（`docs/roadmap.md` で計画なし）。
- 公開 API の無認証・レート制限なし。
- 経路ごとの数値境界の不一致。
- 単一プロセス前提。
- `line-bot-model`/`api-client`/`servlet`/`spring-boot` の SDK モジュール（`line-bot-cli` は Phase 3b で削除する）。
- `MessageConst` の画像 URL が branch `3.0` を指すこと（`future` の `Image/` には既定画像 5 枚のみ。運用上の事実として記録）。
- 神モードで人数→お題の順に設定したときの応答文（Q8 で許容と確定。Phase 2 で現状を固定する）。

## 8. Implementation Phases

各フェーズは前のフェーズの検証が通ってから始める。フェーズ内でも 1 目的 1 コミット。

### Phase 1: 現在状態と検証コマンドの確認（変更なし）

1. `git status --short`、`git branch --show-current` を記録。未コミット変更があれば報告して指示を待つ。
2. 第 6 章の 3 コマンドを実行し、テスト数・成否・所要時間を記録。第 6 章のベースライン（73 / 201 / jar 生成）と一致しなければ止まる。
3. `docs/game-spec.md`、`docs/interfaces.md`、`docs/architecture.md`、`sample-spring-boot-echo/README.md` を読む。

### Phase 2: 安全網の追加（テストのみ。main は触らない）

1. `EchoApplicationTextCommandTest`（仮）を追加。モック `LineMessagingClient` を使い、以下を固定する。
   - `お題`/`題`/`神` → 4 桁の村番号を含む `TemplateMessage`、ボタン 1 つ（`PostbackAction` data `"0"`）。`ランダム` → `TextMessage` で `RANDOM_NUMSETMESSAGE` を含む。
   - お題設定 → 文言。神モードなら `GOD_NUMSETMESSAGE`、通常なら `OWNER_NUMSETMESSAGE`。**人数を先に設定してからお題を設定した場合の文言も現状のまま固定**（Q8 の証拠にもなる）。
   - 人数設定（2、1、100、101）。101 は既定応答（`ConfirmTemplate`、ボタンは `GM`/`神` の 2 つ）。
   - 参加、再参加同一、満員、オーナーの配布状況、ランダム村オーナーの役職再表示。
   - `@逆村`（成功 / 参加者あり / ランダム村 / 対象なし）。
   - `@わーわーず`（応答は `TextMessage` 1 通で、神モードなら `伝えてください。` で終わり、非神モードなら `あなたはGMです。` を含む。登録された特殊村のメッセージ数が神モードで人数分、非神で人数 + 1。GM 向け文言が神モードで 1 通・非神で 0 通含まれる。**配布順はシャッフル後なので先頭の役職は検証しない**。条件不足と対象なしは既定応答、元の通常村が残る、新番号は 10000〜99998）。
   - `@取得` → `TemplateMessage` にボタン 4 つ（`確定` は `MessageAction`、他 3 つは `PostbackAction` data `"2"`,`"3"`,`"4"`）。
   - `@配布` → 3 通（Image、Text、Text）。`@特殊` → 1 通。スタンプ → `TemplateMessage`。
   - 全角 `＠` の同等性。
   - postback 10〜9999 / 10000 以上で村が存在する場合の入室状況文言。
2. `CommonModule` に対して「name が 3 部構成でない要素は無視される」現行挙動をローカルで固定できる範囲で追加（HTTP は叩かない。`createMap` の解析部分を private 静的関数へ抽出する**最小限の変更は許可**するが、公開挙動は変えない）。
3. 検証: `:sample-spring-boot-echo:test` が全件成功し、追加テスト数を報告。
4. 注意: Phase 2 は**現状の挙動**を固定する。第 10 章で承認済みの挙動変更に該当する箇所（`/callapi` の空白付き数値、Werewords の欠け表記が村人の場合、難易度 1、カタログの不正要素）は Phase 2 では**テストを書かず**、Phase 3b・Phase 5 で変更と同時にテストを追加する。

### Phase 3: 明らかに安全な整理

1. D6 の未使用シンボル削除（`WordGetter` rank 1 は Q4 の回答まで残す）。
2. D8: `CreatVillage` でリストをコピーしてからシャッフル。
3. D6: `CommonModule` の 2 マップを空マップで初期化し、`getIllustUrl` の NPE 経由を解消（`catch` は残してよい）。
4. D15: 数値境界を名前付き定数に（値不変。LINE と API の定数は別に持つ）。
5. `EchoApplication` の `reply` と `replyDefoltMessage` の送信部分を 1 つの private メソッドに統合（既定応答の内容は不変）。
6. 各項目を独立コミット。検証: `:sample-spring-boot-echo:test` + `check`。

### Phase 3b: 承認済みの挙動変更と構成整理（各項目を独立コミットで）

いずれも第 10 章で承認済み。変更と同じコミットでテストと docs を更新する。

1. **D19 `line-bot-cli` の削除**（Q10）: `git rm -r line-bot-cli`、`settings.gradle` の include、`build.gradle` の `codeCoverageReport` の `':line-bot-cli'`、`README.md` のリポジトリ構成、`docs/architecture.md` のモジュール構成図と CLI の記述を削除。検証: `check`（直前の件数 − 5）、`bootJar`、D19 に書いた範囲の grep がゼロ。
2. **D13 ビルド設定の整理**（Q6）。範囲は次に限る。
   - ルート `build.gradle`: `publishing { ... }` ブロック、`apply plugin: 'maven-publish'`、`isDevBuild`/`isReleaseBuild`/`sonatypeRepositoryUrl` と `hasProperty('release')`/`hasProperty('ci')` の分岐（`version` は `2.7.0-SNAPSHOT` のまま固定にする。`Procfile` が `sample-spring-boot-echo-*.jar` をワイルドカードで拾うため jar 名の変更は許容されるが、変えない方が安全）、`kotlin_version` と `kotlin-gradle-plugin` の classpath、`gradle-versions-plugin` の classpath と `apply plugin: 'com.github.ben-manes.versions'`、`if (project.name != 'test-boot1-compatibility')` の分岐（該当プロジェクトは存在しない）、`codeCoverageReport` タスク（jacoco プラグイン適用も含めて削除。`.codecov.yml` を消すので集計先がない）。`gradle-git-properties`、`gradle-lombok`、`dependency-management`、`spring-boot-gradle-plugin`、`grgit-core` は**残す**（`grgit-core` はコミット履歴上ビルド不具合の暫定対策として追加されており、外すとビルドが壊れる可能性がある。外したい場合は別コミットで試し、失敗したら戻す）。
   - 削除: `.codecov.yml`、`config/`（checkstyle と findbugs）、`.github/ISSUE_TEMPLATE.md`、`.github/ISSUE_TEMPLATE/`。
   - `.gitignore`: `*.json`、`*.yaml`、`*.script`、`*.bin`、`*.kt`、`.gitignore` 自身、`*.class` の重複行を削除（追跡済みファイルへの影響はない。`git status` が変わらないことを確認）。
   - `app.json`: `name` と `description` をこの Bot の説明（例: `インサイダーゲーム Bot` / `インサイダーゲームと Werewords の役職・お題を LINE で配る Bot`）に変える。`env` は変えない。
   - `docs/operations.md` の CI の記述に変更が出ないことを確認（テスト実行と jar 生成は維持されるので変更不要のはず）。
   - 検証: 各コミットで `check` と `bootJar`。`sample-spring-boot-echo/build/libs/sample-spring-boot-echo-*.jar` が生成され、`Procfile` のワイルドカードに一致すること。
3. **D18 Werewords の欠け表記の修正**（Q5）: `CommonSubLogic.WEREWORDS_ROLE_MAP` を `{ "", "占師", "インサイダー", "村人" }` にし、テストを追加。
4. **Q1 `/callapi` の trim 統一**: `MainController.messageController` で `Integer.parseInt(message.trim())` にする。`nonNumberMessage` へ渡す `message` と `VillageService.setOdai` へ渡す文字列は現状（未 trim）のまま。`MainControllerTest` に「`" 5"` が人数設定として扱われる」を追加。`docs/interfaces.md` の「数値の解釈」に「両経路とも前後の空白を除いてから数値判定する」を 1 文追記。
5. **D6 難易度 1 の削除**（Q4。独立コミット）: `getWord` の `rank == 1` 分岐と `SECOND_LINE` を参照するその経路を削除し、`WordGetterTest.everyDifficultyDrawsAWord` の rank 1 ケースを削る。Javadoc から「1」を消す。
6. **D10 お題辞書の境界導出**（Q3。独立コミット）: D10 の要件どおり実装し、`WordGetter.SECOND_LINE`〜`FIFTH_LINE` の定数を境界取得 API に置き換え、`WordGetterTest` の `THIRD_LINE` 参照を更新し、境界の等価性テストと rank 別区間のテストを追加。`docs/architecture.md`・`docs/interfaces.md` を D10 の記述どおり同じコミットで更新。
7. 検証: Phase 3b 完了時に `check`（Phase 2 完了時の件数 − 5 + Phase 3b の追加分）と `bootJar`。Phase 2 のテストのアサーションが変わっていないこと。

### Phase 4: 小さな責務分離

1. D5: `VillageService.convertToWerewords(userId)` を追加し、`EchoApplication` の `@わーわーず` 分岐をそれに置き換え、`WereWordEvent` を削除。文言不変。
2. D3: `Village.setInsiderRole` を「席 → 役職」の単一関数に整理。`synchronized` の範囲・`Random` の呼び出しは不変。
3. D1: 解釈器を導入し、`EchoApplication.replyMessage` と `MainController.messageController/nonNumberMessage` をそれに載せ替える。trim の有無・境界値・`@` コマンドの扱いは現状維持。
4. 検証: 全テスト + Phase 2 の追加テスト。`MainControllerTest` の期待値は一切変えない。

### Phase 5: 境界とインターフェースの明確化

1. D2: LINE ハンドラを `EchoApplication` から分離し、コンストラクタ注入にする。`@LineMessageHandler` は新クラスだけに付ける。`@SpringBootTest` でハンドラ登録数（`LineMessageHandlerSupport` の INFO ログ `count = 4`）が変わらないことを確認するテストを 1 本追加。
2. D7: `SpecialVillage` をコンストラクタ生成に変え、setter を削除、読み取りを `synchronized` に揃える。
3. D4: `Village` に `isGodModeAwaitingSize()` 等の**現在の条件と同値**な問い合わせを追加し、`VillageService`/旧 `WereWordEvent` 相当の判定をそれに置き換える（条件は変えない）。
4. D9 を 3 コミットに分ける。(4a) 解析部分を HTTP から切り離し、型付き DTO と不変マップ差し替えに整理する（挙動不変。確率分布・非正重み・総重み 0 の扱いを固定するテストを同じコミットで追加）。(4b) 不正要素の要素単位スキップ（Q2。テスト同梱）。(4c) 接続 15 秒・読み取り 30 秒のタイムアウト（Q7。定数値のテスト同梱）。
5. D12: 上記で触ったクラスに限り typo を改名し、`sample-spring-boot-echo/README.md` を更新。
6. D16（任意）: 状態とレジストリのパッケージ統一を独立コミットで。
7. 検証: 全テスト + `bootJar` + 可能なら `bootRun` を起動して `GET /callapi?message=お題&userId=x` が 200 で JSON を返すことを確認（LINE 資格情報はダミーでよい。`LINE_BOT_CHANNEL_TOKEN`/`SECRET` を空以外にする）。

### Phase 6: テストしやすい構造（Phase 5 と並行可）

1. D14: `FixedRandom`/`repeat` ヘルパを 1 箇所に集約。
2. 検証: 全テスト。（D9 のテストは Phase 5 の各コミットに同梱するので、ここでは追加しない。）

### Phase 7: 大きな設計変更は提案のみ

返信の非同期化、認証の導入、SDK モジュール（`line-bot-model`/`api-client`/`servlet`/`spring-boot`）の整理は**実装しない**。最終報告に「提案」として、動機・影響範囲・移行手順・検証方法を 10 行以内で書く。

### Phase 8: レジストリとサービスの DI 化（条件付き。Q9）

**着手条件**: Phase 1〜6 が完了し、`check` と `bootJar` が通り、Phase 2 のテストの入力と期待結果の意味が不変であること（第 9 章の基準）。着手前に「Phase 8 を開始する」と報告する。

**目的**: `VillageList`/`SpecialVillageList` の static 状態と `VillageService` の static メソッドをやめ、Spring の Bean としてコンストラクタ注入する。テストごとに新しいインスタンスで隔離できるようにし、`maxParallelForks = 1` の制約を外せる状態にする。

**やること**（最大 4 コミット。各コミットはコンパイルとテストが通る単位にする。static のレジストリを先に消すと `VillageService` とテストの static 呼び出しがコンパイルできなくなるので、**レジストリのインスタンス化とサービスのインスタンス化、それらを使うテストの書き換えは 1 コミットにまとめてよい**）:

1. `VillageList` を `VillageRegistry`（仮）としてインスタンス化。static フィールドをインスタンスフィールドに、`static synchronized` を `synchronized` に置き換える（ロックの対象がクラスからインスタンスに変わるだけで粒度は同じ。プロセス内に 1 インスタンスしか作らないことを `@Component` のシングルトンで保証）。`SpecialVillageList` も同様。`clear()` は不要になるので削除し、テストは `new` で隔離する。同じコミットで `VillageService` をインスタンス化し、2 つのレジストリをコンストラクタで受け取る `@Service` にする。`Random` は現状どおりメソッド内で `new Random()`（テスト用 seam のオーバーロードも維持）。**同じコミットで本番の呼び出し元もすべて切り替える**: `MainController`、`SpecialVillageController`、LINE ハンドラ（Phase 5 で分離済み）、`CreatVillage`/`CreatWereWordsLogic`（Phase 4 で `VillageService` へ寄せた部分）をコンストラクタ注入にし、`@SpringBootApplication(scanBasePackages = "com.example.bot")` を指定する（指定しないと `spring.game`/`staticdata` の Bean は検出されない。D2 参照）。static 呼び出しが 1 つでも残るとコンパイルできないため、このコミットは大きくなるが分割しない。
2. `sample-spring-boot-echo/build.gradle` の `maxParallelForks = 1` は、全テストが static 状態に依存しなくなったこの時点で外す。
3. 確認コミット（変更がなければ省略）: `EchoApplication` の `@Scheduled` を持つ処理も `@Component` 化されているはずなので、static 参照が残っていないことを `grep -rn 'static' sample-spring-boot-echo/src/main` で確認する（`MessageConst` の定数、`WordGetter` の辞書、`CommonSubLogic` のテーブルは static のままでよい。`CommonModule` の可変マップは Phase 5 で `volatile` 不変参照になっているので、Phase 8 では触らない）。
4. `sample-spring-boot-echo/README.md` の「static レジストリ」の記述を更新。`docs/architecture.md` の「排他の粒度」は「レジストリごと」のままで内容は変わらないので更新不要。

**守ること**:

- 採番範囲、上限、FIFO 削除、`findLatestOwned` の探索順、ロックの粒度はすべて不変。`VillageListTest` は `new VillageRegistry()` に書き換えるだけで期待値を変えない。
- `@SpringBootTest` の起動テスト（Phase 5 で追加）が通り、`LineMessageHandlerSupport` のハンドラ登録数が変わらないこと。
- 既存の全テストの期待値を変えない。テストの `@Before` の `clear()` は「新しいインスタンスを作る」に置き換える。
- コミット数は最大 4（第 5 章）。1 コミット目が「レジストリ + サービス + 呼び出し元 + テスト」で大きくなるのは想定内で、ファイル数の制限は適用しない。それでもコンパイルが通る単位に切れないほど絡み合っていると分かった時点で止まって報告する。

**検証**: `check`、`bootJar`、可能なら `bootRun` で次の順に叩く。`GET /callapi?message=お題&userId=x` → 200（村番号を含む）、`GET /callapi?message=すいか&userId=x`（お題設定）、`GET /callapi?message=3&userId=x`（人数設定。**人数未設定のまま参加すると `村がいっぱいです。` になる**）、`GET /callapi?message=<村番号>&userId=y` → 役職メッセージ。これでレジストリが単一インスタンスで共有されていることを確認する。

## 9. Verification Requirements

- 各コミット後に `./gradlew --no-daemon :sample-spring-boot-echo:test` が全件成功すること。
- 各フェーズ終了時に `./gradlew --no-daemon check` と `./gradlew --no-daemon :sample-spring-boot-echo:bootJar` が成功すること。
- テストの**期待値を変更しない**（下記の承認済み 3 件を除く）。テストの構造変更（ヘルパ集約、注入方法の変更）は許可するが、アサーションの文字列・件数・型は不変。
- Phase 2 で追加したテストと既存テストは、**入力と期待結果の意味を変えない**。ファイル差分ゼロは要求しない。許されるのは「対象の生成方法・注入方法・参照先の変更」（例: `new EchoApplication()` + `ReflectionTestUtils` → 新ハンドラのコンストラクタ注入、`SpecialVillage` の setter → コンストラクタ、`WordGetter.THIRD_LINE` → 新しい境界取得 API、`clear()` → 新インスタンス生成）だけ。アサーションの文字列・件数・型・順序を変える差分は禁止。各フェーズ末に `git diff <phase2-commit> -- sample-spring-boot-echo/src/test` を読み、変更されたアサーションがあれば「参照先の置換のみで意味は不変」であることを 1 行ずつ報告する。
- 既存テストで期待値の意味を変える変更が許されるのは、承認済み変更に対応する次の 3 件だけ: `WordGetterTest` の rank 1 ケース削除（Q4）、`MainControllerTest` への trim ケース追加（Q1）、`CreatWereWordsLogicTest` への欠け表記ケース追加（Q5）。各フェーズで明示した**新しいアサーションの追加**（境界の等価性、重み分布、ハンドラ登録数など）は既存の期待値を変えないので常に許可。
- 承認済みの挙動変更（第 10 章）は、それぞれ変更前に失敗し変更後に成功するテストを同じコミットに含めること。
- コンパイル警告ゼロ（`-Werror` で自動的に保証される）。
- `git status` が各コミット後にクリーンであること（`build/` は `.gitignore` 済み）。
- 可能なら Phase 5 の最後に `bootRun` での疎通確認を行い、結果を報告する。行えない場合はその理由を報告する。

## 10. 確認済みの判断（2026-09-06 回答済み）

すべての質問に回答が得られた。実装担当モデルはここに書かれた判断をそのまま採用し、再度質問しない。新たな疑問が出た場合だけ第 5 章に従って止まる。

| # | 論点 | 判断 | 反映先 |
| --- | --- | --- | --- |
| Q1 | `/callapi` が `message` を trim せずに数値判定し、`" 5"` がお題になる | **LINE 側に統一する**。`/callapi` も trim 後に数値判定 | Phase 3b-4、`docs/interfaces.md` に 1 文追記 |
| Q2 | 役職画像カタログで重みが数値でない要素が 1 件でもあると、その回の取り込み全体を中断して前回の一覧を使い続ける | **その要素だけ無視して残りは取り込む**（`docs/interfaces.md` の記述どおり） | Phase 5（D9 (b)） |
| Q3 | お題辞書の難易度境界が行番号定数で、`word.csv` 2 列目の難易度と完全一致している | **2 列目から導出する実装に変え、docs を更新する** | Phase 3b-5（D10） |
| Q4 | UI から到達不能な難易度 1 の経路 | **削除する**（テストの rank 1 ケースも削る） | Phase 3b-5（D6） |
| Q5 | Werewords で欠けた役職が村人のとき GM 向け文言が `役職は「あなたの役職は村人です」が欠けています。` になる | **不具合。`村人` に修正する** | Phase 3b-3（D18） |
| Q6 | SDK 公開設定、未参照の `.codecov.yml`・`config/`、`app.json` の説明文など | **整理する**。誰も SDK を publish していない | Phase 3b-2（D13） |
| Q7 | Apps Script への HTTP 取得にタイムアウトがなく、無応答時に定期取得スレッドが固まる | **接続 15 秒・読み取り 30 秒**。固まった場合は WARN を出して次回の再取得に進む | Phase 5（D9 (c)） |
| Q8 | 神モードで人数→お題の順に設定すると、お題設定応答が人数入力を促す文になる | **許容している挙動。変えない** | Phase 2 で現状を固定。D4 は名前付けのみ |
| Q9 | レジストリとサービスの static をやめる DI 化 | **問題なければ実施**。全フェーズ完了後に条件付きで行う | Phase 8（D11） |
| Q10 | `line-bot-cli` の要否 | **使っていない。削除する** | Phase 3b-1（D19） |

Q2 と Q7 の影響範囲は「役職メッセージに添える画像の選択」だけで、配役・お題・webhook 応答には関わらない。取り込みは 5 分ごとの専用スレッドで動き、webhook の処理スレッドとは別。

## 11. Reporting Format

最終報告は次の構成で、この順に書く。

1. **結果の要約**（3 行以内）: 完了したフェーズ、未着手のフェーズとその理由。
2. **実行したコマンドと結果**: 第 6 章の各コマンドについて、ベースライン時と最終時の「テスト数 / 成功数 / 失敗数 / 所要時間」を表で。失敗があればその出力を貼る。
3. **コミット一覧**: ハッシュ、1 行要約、対応する Debt ID、触ったファイル数。
4. **挙動が変わっていないことの根拠**: Phase 2 以降のテスト差分（`git diff <phase2-commit> -- sample-spring-boot-echo/src/test`）を読み、変更されたアサーションが参照先の置換だけであること、期待値の意味を変えた箇所が第 9 章の 3 件に限られることを示す。
5. **止まった箇所・質問**: 第 5 章に該当して中断したものと、第 10 章に新たに追加すべき質問。
6. **提案（実装していないもの）**: Phase 7 の内容。
7. **未実施・スキップした項目**: 理由付きで。

「完了」と書けるのは、第 9 章の検証がすべて通り、その出力を確認した項目だけ。

## 12. Out-of-scope Items

- `line-bot-model`、`line-bot-api-client`、`line-bot-servlet`、`line-bot-spring-boot` の変更（SDK フォークの独自クラス 3 つも含む）。`line-bot-cli` は削除のみ行い、中身には手を入れない。
- 返信の非同期化、webhook 2 秒制限への対策。
- `/callapi`・`/specialvillage` への認証・レート制限の追加。
- LINE と `/callapi` の数値境界（100 / 999）の統一。trim の統一（Q1）だけが対象。
- 状態の永続化、複数プロセス対応。
- `word.csv` の内容変更、役職画像の変更、Apps Script 側の変更。
- Heroku から Oracle Cloud への移行（`docs/roadmap.md`、`docs/superpowers/specs/`）。
- Gradle/Spring Boot/JDK のバージョンアップ（`grgit-core` など残すと決めたビルド依存の整理も含む）。
- 第 10 章で承認された範囲を超える `docs/` の内容変更。
- 外部フォーム（`insidergametool.netlify.app`）側の変更。
- Q8 の応答文の変更。
