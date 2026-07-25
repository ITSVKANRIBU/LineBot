# Refactoring Instructions

## Objective

既存のLINE Bot、公開HTTPエンドポイント、SDK/CLIの契約を壊さずに、変更容易性・テスト容易性・運用安全性を高める。

この作業の目的は見た目を整えることでも、全面的に書き直すことでもない。証拠のある負債を、小さく戻しやすい単位で減らすこと。仕様が不明な挙動、外部契約、保存済みデータ、認証・通知・外部連携に関わる変更は、勝手に決めず、必ず停止して質問すること。

優先対象は、このフォーク固有のゲームアプリケーション部分である。このリポジトリの主目的はゲームBot運用であり、SDK成果物とCLIの外部利用者はいない。ただしゲームBot自身がSDK moduleへ依存するため、必要なSDK機能まで一括削除・全面置換してはならない。固有コードのmodule移動や不要な公開拡張の整理は、契約testを先に置いたうえで段階的に行う。

## Resolved Product And Operations Decisions

以下は人間が明示的に回答済みの決定であり、再質問せず実装へ反映する。

1. 現在の主目的はインサイダーゲームBotの運用である。SDK成果物とCLIを利用する外部利用者はいない。
2. PostgreSQLを使う機能はすべて廃止済みで、DB関連コード、`/Insider`画面、DAO/logic/entity、JDBC dependency、非数値postbackからのDB登録は不要である。保存データのmigrationは不要。
3. repositoryに記録されたDB資格情報は無効化済みである。working treeから削除するだけでなく、Git履歴からも除去することが承認済みである。
4. `GET /callapi`と`/specialvillage`は現役である。今回のrefactorではpath、method、request/response shape、CORS、caller-supplied `userId`の扱いを維持する。認証/CORS再設計は別提案に留める。
5. 不正入力は、存在しない村番号なら「村が作成されていません」、必須parameter不足ならHTTP 400、満員なら現行どおり「村がいっぱいです。」、内部errorなら個人情報を含まない一般的なerror応答とする。
6. 長文での例外、存在しない村のnull参照、広すぎる`catch (Throwable)`など、D11に列挙した明らかな不具合はtest付きで修正してよい。構造変更とは別の小さなchangeにする。
7. Google Apps Scriptへのお題・LINE user IDの履歴送信は不要で、関連class/config/呼び出しを削除する。Google側からの画像一覧取得は維持する。
8. 通常村50件・特殊村30件のFIFO eviction、process memoryだけでの保持、再起動時消失は意図した仕様である。
9. お題難易度とWerewordsに別仕様資料はない。今回は現行の抽選範囲・配役・表示をcharacterization testで固定し、「推測による修正」はしない。
10. productionはHeroku、Java 8で稼働している。今回Java major、Gradle、Spring Bootを更新しない。

Git履歴書き換えは通常のsource refactorとは分離し、全source変更と検証完了後の専用phaseで行う。履歴書き換えによりcommit SHAが変わり、共同作業者の再cloneが必要になる。資格情報は失効済みだが、値をcommand outputやreportへ再掲しないこと。

## Project Understanding

### Repository evidence

- `AGENTS.md`、`CLAUDE.md`、その他のエージェント向け指示ファイルは存在しない。
- ルート`README.md`は、元来このリポジトリをLINE Messaging API SDK for Javaとして説明している。
- `settings.gradle`が登録するのは5つのSDK/CLIモジュールと`sample-spring-boot-echo`である。
- 現ブランチ`3.0`の近年の変更はゲームBot固有の機能であり、`sample-spring-boot-echo`だけでなく`line-bot-model`と`line-bot-spring-boot`にも固有コードが混入している。
- 追跡対象外の`sample-spring-boot-kitchensink`、`sample-spring-boot-echo-kotlin`、`test-boot1-compatibility`等がローカルには見えるが、現在のGit管理対象・Gradle設定対象ではない。勝手に取り込まないこと。
- DB schema、migration、正式な運用runbookは存在しない。`ProblemDao`内のSQLだけが`PROBLEM(ID, THEME, NAME, DIFFICULTY, OTHER)`を暗黙に示す。

### What the project does

このリポジトリには二つの性格が共存する。

1. Java 8向けLINE Messaging API SDK:
   - LINE webhook modelのJSON serialize/deserialize
   - LINE API/OAuth/LIFF/Rich MenuのHTTPクライアント
   - Servletでの署名検証とwebhook parse
   - Spring Bootのauto-configurationと`@EventMapping` dispatch
   - SDK操作用CLI
2. インサイダーゲーム／Werewords支援アプリ:
   - LINEメッセージで通常村を作り、お題・人数を設定し、参加順に役職を配る
   - 「神」モード、逆村、Werewords特殊村を作る
   - CSVからランダムにお題を取得する
   - 公開REST APIから村へ参加し、LINE message modelのJSONを返す
   - 特殊村を外部フォームから作る
   - 画像候補を外部Google Apps Scriptから定期取得する
   - お題履歴をGoogle Apps Scriptへ送る
   - PostgreSQLを使うお題登録画面の残存コードを含む

### Main user workflows

#### LINE webhook workflow

1. LINEが署名付きで`POST /callback`へイベントを送る。
2. `LineBotServerInterceptor`と`LineBotCallbackRequestParser`が`X-Line-Signature`を検証し、`CallbackRequest`へ変換する。
3. `LineMessageHandlerSupport`が`@EventMapping`の引数型とpriorityでhandlerを選ぶ。
4. `EchoApplication`がtext、postback、sticker、default eventを処理する。
5. 通常村と特殊村の状態をプロセス内static listで参照・更新する。
6. `LineMessagingClient`がLINE Reply APIへ応答する。

#### 通常村

- 「お題」または「題」で通常村を作成する。
- 「神」でGMも参加者として割り当てるモードの通常村を作成する。
- オーナーが自由文でお題、100以下の整数で人数を設定する。コード上はどちらを先に送っても成立し得る。
- 1000番台から9998までの4桁村番号を参加者が送信すると、満員でなければ参加順に役職が割り当てられる。
- オーナーが村番号を送ると配布状況とお題を確認する。
- `@逆村`は、参加開始前の最新の自分の村を逆村にする。
- `@わーわーず`は、設定済みの通常村を元に特殊村を作る。
- `@取得`はCSVからお題候補を取得する。
- `@配布`、`@特殊`は案内リンクを返す。

#### 特殊村

- `/specialvillage`へ`{"message":[...]}`相当のJSONを送ると、メッセージをshuffleし、10000から99998の5桁村番号を返す。
- 参加者がその番号をLINEまたは`/callapi`へ送ると、参加順に対応するメッセージを受け取る。

#### Public REST workflow

- `GET /callapi?message=<村番号>&userId=<ID>`は、通常村または特殊村へ参加し、`List<Message>`をJSONで返す。
- 村がない、数値でない等の場合は、現在は多くの場合「村が作成されていません」というtext messageを200で返す。ただしnullや内部例外で500になり得るため、正確な現状はcharacterization testで記録すること。

#### Retired topic registration workflow

- 旧コードには`/Insider`、Thymeleafの`Entry`/`List`、`PROBLEM`テーブルの登録・一覧・表示・更新処理が残っている。
- このworkflowは廃止済みであり、DB関連コード・resource・dependency・routeを削除対象とする。削除前に参照inventoryを取り、ゲームBotの起動と現役APIから切り離されていることをtestする。

### Main entry points

- Bot executable: `sample-spring-boot-echo/src/main/java/com/example/bot/spring/echo/EchoApplication.java:64-73`
- Webhook endpoint: `line-bot-spring-boot/src/main/java/com/linecorp/bot/spring/boot/support/LineMessageHandlerSupport.java:236-239`
- Public REST: `sample-spring-boot-echo/src/main/java/com/example/bot/spring/echo/MainController.java:17-27`
- Legacy web/API: `LineMessageHandlerSupport.java:115-163`
- Spring Boot auto-configuration: `line-bot-spring-boot/src/main/resources/META-INF/spring.factories`
- CLI executable: `line-bot-cli/src/main/java/com/linecorp/bot/cli/Application.java`
- Deployment command: `Procfile`

### Modules and responsibilities

| Module | Intended responsibility | Current noteworthy coupling |
| --- | --- | --- |
| `line-bot-model` | LINE API event/message/action/profile/rich-menu/LIFF modelとJackson契約 | ゲーム専用の`ButtonsTemplateNonURL`、`ButtonsTemplateNonTitle`、`URIActionNonAltUri`も公開packageに存在する |
| `line-bot-api-client` | Retrofit/OkHttpを使ったLINE Messaging API、OAuth、LIFF client | `line-bot-model`へ依存。wiremock/mock server testsあり |
| `line-bot-servlet` | webhook body読込、署名検証、parse | validatorのため`line-bot-api-client`へ依存 |
| `line-bot-spring-boot` | SDKのauto-configuration、webhook dispatch、reply adapter | ゲームの特殊村、DB、Thymeleaf画面、PostgreSQL driver、公開APIまで抱えている |
| `line-bot-cli` | LIFF、Rich Menu、message push操作 | clientではなく`line-bot-spring-boot`全体へ依存し、不要なWeb/DB/game依存を引き込む |
| `sample-spring-boot-echo` | 実運用されているゲームBot | 470行の`EchoApplication`、static state、外部Apps Script、CSV、公開RESTを持つ。固有testは0件 |

### Dependency direction

`line-bot-model <- line-bot-api-client <- line-bot-servlet <- line-bot-spring-boot <- sample-spring-boot-echo`

`line-bot-cli -> line-bot-spring-boot`

本来アプリ側にあるべきゲームdomain、DB、HTML endpointが`line-bot-spring-boot`へ逆流しているため、SDKとアプリの所有境界が崩れている。

### Data flow and state

- LINE input: webhook JSON -> signature validation -> Jackson model -> event dispatch -> game routing -> static registry mutation -> LINE reply model -> LINE API。
- REST input: query parameters -> duplicated participation logic -> static registry mutation -> Jacksonによるmessage model response。
- Special village input: raw request bodyの先頭1行 -> untyped `Map<String,Object>` -> castしたmessage list -> shuffle -> static registry -> 手組みJSON response。
- Topic input: `word.csv`を都度ファイル先頭から走査、またはlegacy PostgreSQL query。
- Image input: 5分ごとのscheduled GET -> untyped map -> filename規約`ROLE_WEIGHT_ID`を解析 -> static map差し替え。
- History output: topicと`villageNum + LINE userId`をGoogle Apps ScriptへPOSTする残存処理があるが、これは廃止対象。
- 通常村は最大50件、特殊村は最大30件。いずれもstatic memoryのみで、FIFO eviction、再起動消失、インスタンス間非共有。このsemanticsは意図された仕様として維持する。

### External dependencies and boundaries

- LINE Messaging API / OAuth / LIFF: `https://api.line.me/`
- LINE webhook signature: channel secret、`X-Line-Signature`
- Google Apps Script / Googleusercontent: topic historyは廃止対象、illustration metadata取得は維持対象
- GitHub Raw: `ITSVKANRIBU/LineBot/3.0/Image/`。ブランチ名・path・画像名が外部URL契約
- Netlifyの既存サイトと特殊村フォーム
- Wikipedia REST APIとGoogle Search: legacy HTML UI
- PostgreSQL: hard-coded remote endpoint and credentials、schema/migrationなし
- Maven Central、Gradle Plugin Portal、Spring plugin repository、Sonatype、Codecov
- Herokuの`PORT` / `JAVA_OPTS` environment。production Javaは8
- 課金、queue、object storage、独立した認証基盤はコード上に見つからない

### Build and test configuration

- Required by repository docs/config: Java 8以上。ただし`system.properties`はJava 8を固定し、Gradle wrapperは5.4.1。
- CI config: `.travis.yml`でOpenJDK 8、`./gradlew check`、`./gradlew codeCoverageReport`。
- Java compilationには`-Xlint:all -Xlint:deprecation -Werror`が設定されている。
- Checkstyle/SpotBugsは2022年のコミット`4d6ad9b`で無効化されたが、設定ファイルだけ残る。同じコミットで多数の契約testとJavadoc artifact生成も削除された。
- `sample-spring-boot-echo`にはtest sourceがない。
- 現調査環境ではJava Runtimeが存在せず、`java -version && ./gradlew --version && ./gradlew check`は`Unable to locate a Java Runtime`で開始前に失敗した。
- `build/`には2024-05-13の古い成果物と、138 tests / failures 0 / errors 0を示す古いXMLがあるが、現在のclean baselineとして扱ってはならない。

## Behaviors To Preserve

回答済み決定で変更を承認された箇所以外は、少なくとも以下を変えないこと。明示承認済みの不具合は、先に再現testを作り、構造変更と分離して修正する。

1. `POST /callback`でのLINE署名検証。header欠落・不正署名を400にすること、署名検証前のbody改変禁止。
2. `@LineMessageHandler` / `@EventMapping`の型別dispatchとpriority順序、return value reply契約。
3. text、postback、sticker、default eventの受付と、既存の日本語文言、button label、alt text、action type。
4. 「お題」「題」「神」、人数、村番号、`@配布`、`@特殊`、`@取得`、`@逆村`、`@わーわーず`、全角`＠` variantの認識。
5. 通常村と特殊村の番号範囲、オーナー/参加者の分岐、満員応答、同じuser IDの再入室時に同じ役割を返すこと。
6. 通常村、神モード、逆村、Werewordsの役割配布結果。正しい仕様が確認できるまでは現行algorithmを変更しない。
7. `GET /callapi`のpath、query parameter名、CORS、成功時の`List<Message>` JSON shape。現役APIなのでmethod/auth/originを変更しない。
8. `/specialvillage`のpath、受理するrequest shape、返却する`{"data":"<number>"}` shape、CORS。現役APIなので維持する。
9. 不正入力の承認済み契約: 存在しない村は「村が作成されていません」、必須parameter不足は400、満員は「村がいっぱいです。」、内部errorは個人情報を含まない一般的なerror。
10. `word.csv`の内容と、利用者が観測する現行のお題分布。仕様資料がないため、分布を「修正」しない。
11. JSON field名、enum値、Jackson polymorphism、constructor/builder、public method signature、exception mappingなど`com.linecorp.bot.*`の公開契約。
12. CLI command名、argument名、payloadのJSON/YAML/data入力、生成artifact名。
13. 通常村50件・特殊村30件の上限、FIFO eviction、restart時消失。これは確定仕様として保持する。
14. GitHub Raw画像のpath・filename規約とfallback画像。重複画像はweight/fallbackのデータである可能性があるため、単なる重複として削除しない。
15. Google画像一覧取得とGitHub Raw fallback画像は維持する。LINE user IDを含むGoogle Apps Script履歴送信は削除する。それ以外の外部連携は承認なしに変えない。

## Non-Negotiables

1. 最初に`git status --short --branch`を実行し、出力を記録する。
2. 既存の未コミット・未追跡・ignoredファイルをユーザー所有物として扱う。この調査時点では未追跡`.DS_Store`が存在する。削除・編集・stageしない。
3. 既存変更と自分の変更を混ぜない。重なる場合は停止して確認する。
4. 編集前にBaseline Commandsを実行し、成功・失敗・未実行理由をそのまま記録する。環境不足をテスト成功として扱わない。
5. 変更は小さく、review可能で、戻しやすい単位にする。各phaseでdiffを確認し、各phaseで検証する。
6. 無関係なformatting、改行コード変換、import順の全面変更、命名一括変更、生成物更新をしない。
7. 承認済みのDB/history削除と不具合修正以外は、既存挙動、文言、公開API、JSON、外部payloadを勝手に変えない。
8. 外部SDK consumerはいないが、ゲームBotが使う`com.linecorp.bot.*` APIを一括削除しない。固有classのmove/deleteは参照inventory、契約test、app buildを伴う専用phaseにする。
9. credential値、LINE user ID、topicなどの機密・個人データを新たなlog、test fixture、reportへコピーしない。
10. repositoryにある実資格情報をtestへ使用しない。LINE、Google、PostgreSQLなど実サービスを検証中に呼ばない。
11. 正しさが不明なら実装を止め、具体的な証拠、選択肢、互換性影響を添えて質問する。
12. 大きなdependency upgrade、Java/Spring/Gradle migration、認証追加、永続化追加は行わない。Heroku / Java 8を維持する。
13. DB secret値を別ファイルへ移すだけで終わらせない。DB機能とworking tree上の値を削除し、最後に承認済みのGit履歴purgeを専用phaseで実行する。
14. 最後に実行した全commandと結果、残るfailure、未検証事項、最終`git status`を報告する。

## Stop And Ask Conditions

以下を検出したら、その変更を開始または継続せず質問すること。

- 実装とtest、README、実運用観測が矛盾する。
- 削除候補がreflection、Spring component scan、Jackson subtype、古いLINE message/postback、外部クライアントから参照され得る。
- 承認済み範囲外でpublic API、package、JSON property、HTTP route/method/status、CLI syntax、画像URLへ影響する。
- 認証、CORS、LINE署名、画像取得、外部通知を変更する。DBとLINE user ID履歴送信の削除は承認済み。
- timeout、retry、同期/非同期、並行処理順序、random distributionを変える。
- 通常村/特殊村の上限、番号範囲、eviction、restart semanticsを変える。
- D11とResolved Decisionsにないbug fixで、利用者可視の挙動を変える。
- 既存未コミット変更と必要な編集箇所が重なる。
- baselineが失敗し、変更起因かbaseline起因か判定できない。
- dependency取得や外部接続が必要になり、オフライン/権限制約を回避しようとする。
- 複数の妥当な設計があり、product/security/operations判断が必要。

質問するときは「対象」「現状の証拠」「選択肢」「各選択肢の互換性・運用影響」「推奨」を短く提示すること。

## Baseline Commands

リポジトリrootで、編集前に順番に実行する。

```bash
git status --short --branch
git diff --check
java -version
./gradlew --version
./gradlew check
./gradlew codeCoverageReport
./gradlew :sample-spring-boot-echo:bootJar
```

注意:

- 現調査環境ではJava Runtime不在のため、`java -version`時点で失敗した。実装担当環境でも同じなら、Javaを勝手に広範囲へinstall/upgradeせず、使用すべきJDKと権限を確認する。
- clean buildを実行できる環境では、既存のignored `build/`をbaseline根拠にせず、最終的に次も実行する。

```bash
./gradlew clean check codeCoverageReport :sample-spring-boot-echo:bootJar
```

- 現在の独立したlint/typecheck taskは存在しない。Java compileは`check`内で行われ、`-Werror`が有効。
- `dependencyUpdates`は変更ではなく調査用である。実行するなら結果を提案資料に使い、同じphaseで一括upgradeしない。

```bash
./gradlew dependencyUpdates -Drevision=release
```

## Debt Map

### D1. Application code embedded in published SDK modules

- 根拠: `line-bot-spring-boot/src/main/java/com/linecorp/bot/spring/boot/{common,dao,entity,logic,servlet}`、`LineMessageHandlerSupport.java:110-163`、`line-bot-spring-boot/build.gradle:21-24`。
- なぜ負債か: generic Spring Boot integrationがゲームdomain、DB、HTML、special-village APIを所有し、sample appがその内部固有機能へ依存している。CLIまでWeb/DB/game依存を引き込む。
- 影響範囲: SDK artifact、CLI、Bot起動、Spring component scan、公開package。
- 変更リスク: 高。class move/deleteはbinary/source compatibilityとauto-configurationを壊し得る。
- 改善案: まずapp側interfaceを作り、固有機能を`sample-spring-boot-echo`へ段階移動する。外部consumerはいないため、appのcompile/runtime testで安全を証明できた固有classは旧packageから削除できる。CLIは必要なclient auto-configだけへ依存させる。
- 検証: public API inventory、jar内容比較、Spring context test、CLI startup、全test、Bot endpoint contract test。
- 実装判断: test後に実装してよい。SDK全体の削除・置換はしない。

### D2. Monolithic routing and duplicated participation logic

- 根拠: `EchoApplication.java`が470行でevent handling、command parse、state mutation、random、LINE I/O、DB、historyを担当。`MainController.java:51-118`は`EchoApplication.java:405-469`と通常/特殊村参加処理を重複実装。
- なぜ負債か: LINEとRESTでfixが乖離し、private methodと直接I/Oのためunit testが難しい。
- 影響範囲: `/callback`、`/callapi`、役職配布、応答文。
- 変更リスク: 中から高。分岐順やfallbackの微差が利用者可視。
- 改善案: 先に両経路をcharacterizeし、`VillageParticipationService`、`CommandRouter`、`ReplyGateway`相当へ小分けする。controller/handlerはtransport変換だけにする。同じserviceをLINEとRESTで共有する。
- 検証: table-driven command tests、LINE/REST parity tests、同一状態から同一message modelが得られること、exact text/action JSON。
- 実装判断: test追加後、小さなextract method/classは実装してよい。分岐・文言変更は禁止。

### D3. No tests for the game application and removed contract tests

- 根拠: `sample-spring-boot-echo`に`src/test`がない。2022年commit`4d6ad9b`で`CallbackRequestTest`から406行、LIFF testから25行、message reconstruction testから41行、integration testから32行が削除された。現在の`CallbackRequestTest`と`LiffModelSerializeDeserializeTest`には`@Test` methodがない。callback fixture 20個が未参照。
- なぜ負債か: 最も変更される固有機能に安全網がなく、SDK JSON契約も一部無検証。
- 影響範囲: 全refactor、LINE event parse、role assignment、public endpoints。
- 変更リスク: test追加自体は低。過去testを無条件復活すると古い仕様を固定するリスクは中。
- 改善案: current behaviorのcharacterizationを先に追加。未参照fixtureを使ったparse tests、フォーク固有modelのexact JSON tests、game entity/service tests、MockMvc route testsを追加する。過去testは参考にし、期待値は現在の契約根拠と照合する。
- 検証: 追加testを変更前production codeで通す。random、clock、networkはfake/injected dependencyで決定的にする。
- 実装判断: 今すぐ実装してよい。仕様不明な例外挙動は期待値を決めず質問する。

### D4. Unsafe shared mutable in-memory state

- 根拠: `VillageList`と`SpecialVillageList`がstatic `ArrayList`を直接返す。通常村role listと特殊村user listの「存在確認 -> size確認 -> add」は複合操作。`CopyOnWriteArrayList`でも複合操作はatomicではない。ID重複checkとaddも分離される。
- なぜ負債か: webhookと`/callapi`の同時参加で重複参加、定員超過、役職index競合、duplicate ID、iteration中mutationが起こり得る。
- 影響範囲: 全game state、role secrecy、公平性、可用性。
- 変更リスク: 高。lockingやcollection変更は順序・throughput・observed stateを変える。
- 改善案: mutable listを返さないregistryへ包み、create/join/find/evictをatomic operationにする。まず現行上限・順序を保持する。将来のpersistent repositoryは別設計。
- 検証: concurrent join test、同一user idempotency、capacity boundary、FIFO eviction、ID uniqueness、owner access。
- 実装判断: encapsulationとtestは実装してよい。locking semantics変更はtest後。永続化・上限変更は提案のみ。

### D5. External calls are static, untyped, and operationally ambiguous

- 根拠: `CommonModule`、`Request`がstatic `RestTemplate`とhard-coded URLを持つ。Google responseはraw `Map`/`List` cast。`Request.run`はstatic direct callで、static methodの`@Async`はSpring proxy対象にならず、実質同期実行の可能性が高い。明示timeoutなし。
- なぜ負債か: webhook latencyが外部障害に支配され、testで実サービスを避けにくい。失敗1回でhistory送信をprocess lifetime中無効化する。
- 影響範囲: topic設定応答、scheduled job、image selection、privacy。
- 変更リスク: 高。async化、timeout、retryは観測可能な順序と障害動作を変える。
- 改善案: history送信と関連DTO/configを削除する。`IllustrationCatalogClient`はinstance dependencyとして抽象化し、typed DTO、fake、構成化URLを導入する。
- 検証: repository全体でhistory URL/呼び出し/LINE user ID送信が残らないこと、画像取得のsuccess/malformed/timeout/failure tests、scheduler test、webhookがtest中に実networkを呼ばないこと。
- 実装判断: history削除と画像取得のseam/DTO/fakeは実装してよい。画像取得のtimeout/retry policy変更は提案のみ。

### D6. Error handling and logging are inconsistent

- 根拠: `EchoApplication`は`catch (Throwable)`、複数の`.get()`、`printStackTrace`を使用。`InterruptedException`後にinterruptを復元しない。`FrontServlet`は`SQLException | NumberFormatException | NullPointerException`を同じUI errorにする。`LineMessageHandlerSupport.dispatch`は例外をlogしてcallbackを完了する。full eventとhistory requestをstdoutへ出す。
- なぜ負債か: programming errorがcommand fallbackへ化け、LINE retry semanticsが不明、PIIがlogへ出る。障害分類と運用検知ができない。
- 影響範囲: webhook response、LINE reply、REST 500、運用log。
- 変更リスク: 中から高。status/retry/fallbackを変えると外部挙動が変わる。
- 改善案: transport/domain/external errorを分類し、structured loggerへ統一し、機密値をredactする。interruptを復元する。例外を握りつぶすか再throwするかはendpoint contractごとに決める。
- 検証: exception-path tests、HTTP status、LINE client failure、log captureでsecret/user input非出力を確認。
- 実装判断: redaction、logger統一、interrupt復元、承認済みの不正入力契約は小単位で実装可。LINE retry policy変更は提案のみ。

### D7. Retired DB code and credentials remain in source and history

- 根拠: `Properties.java:20-29`にremote PostgreSQL endpoint、username、passwordがliteralで存在。`config/findbugs/excludeFilter.xml`は`DMI_CONSTANT_DB_PASSWORD`を明示除外。driver jarもrepositoryへcommit。schema/migrationなし。
- なぜ負債か: credential漏えい、rotation困難、再現不能なschema、環境差分、artifactへのsecret混入。
- 影響範囲: security、`/Insider`、非数値postback、公開SDK jar、Git commit SHA。
- 変更リスク: source削除は中、履歴書き換えは最高。履歴purgeは全SHAを変え、force-pushと再cloneを必要とする。
- 改善案: DB route、template、DAO/logic/entity、credential holder、JDBC jar/dependency、unused suppression、非数値postback DB登録を削除する。その後、専用phaseで全remote refからDB host/user/passwordをpurgeする。
- 検証: app/SDK/CLI clean build、`/Insider`が存在しないこと、DB class/resource/dependencyがartifactにないこと、全ref secret scan、remote再fetch後の再scan。
- 実装判断: source削除と履歴purgeはいずれも明示承認済み。履歴purgeだけは最後の専用phaseで行う。

### D8. Unauthenticated mutable public APIs and wildcard CORS

- 根拠: `MainController.java:20-22`の`@CrossOrigin`付きGET、`LineMessageHandlerSupport.java:144-157`の`@CrossOrigin`付き`@RequestMapping`。caller-supplied `userId`で状態変更。CSRF/auth/rate limitなし。
- なぜ負債か: 任意siteが他人のuser IDを名乗り、村枠や役職を消費できる。GETがstateを変更する。
- 影響範囲: public clients、game integrity、privacy、availability。
- 変更リスク: 高。認証、method、CORS変更は既存clientを壊す。
- 改善案: 現役clientを壊さないよう、今回path/method/CORS/identity semanticsを維持する。allowlist CORS、authenticated identity、POST化、rate limitは別提案にする。
- 検証: allowed/disallowed origin、spoofing、auth missing/invalid、CSRF/method tests、backward compatibility plan。
- 実装判断: characterization testと内部責務分離は実装してよい。security contract変更は提案のみ。

### D9. Weak request/schema contracts

- 根拠: `/specialvillage`はbodyの先頭1行だけ読み、raw mapを`ArrayList<String>`へcastし、string連結でJSON responseを作る。`/callapi`のparameter annotation/validationなし。`ProblemDao`はschemaをコード内SQLだけで仮定。
- なぜ負債か: malformed inputの挙動が偶然のexception型に依存し、契約が文書化・検証されない。
- 影響範囲: public HTTP API、client compatibility。
- 変更リスク: 中から高。validation追加でstatus/bodyが変わる。
- 改善案: exact current behaviorを記録後、typed request/response DTOとboundary validationを導入する。Jackson shapeを固定し、承認済みの400/not-found/internal-error契約を実装する。
- 検証: valid、empty、multiline、wrong type、missing field、oversized、Unicode inputのMockMvc tests。
- 実装判断: DTO化と承認済みerror contractはtest付きで実装可。

### D10. Word lookup is path-dependent and O(file size) per request

- 根拠: `WordGetter.java:28-51`のhard-coded line boundsとrepository-relative filesystem path、`50-67`で毎回先頭から走査。`word.csv`は8436行。`FIRST_LINE`は未使用。
- なぜ負債か: executable jarを別working directoryから起動すると失敗し、取得ごとにI/Oと線形scanが発生。randomとI/Oがstaticでtest困難。
- 影響範囲: automatic topic retrieval、Heroku/packaged runtime。
- 変更リスク: 中。load方法や境界修正はtopic distributionを変え得る。
- 改善案: classpath resourceとして一度loadするrepository、injected RNG、明示的rank rangesを導入する。現行分布をcharacterizationして同一index selectionを維持する。
- 検証: 8436行count、各rankの最小/最大index、jar classpath test、seeded random test、missing/malformed resource。
- 実装判断: 現行分布を固定するtest後、path/I/O抽象化は実装可。境界値・分布の推測修正は禁止。

### D11. Known latent defects are mixed with refactoring candidates

- 根拠:
  - `Village.java:218-220`と`260-262`は`Collections.singletonList`へ`add`する。
  - 廃止対象の`FrontServlet.java:95`と`140`はdifficultyへnameを設定する。
  - `EchoApplication.java:408-410`は存在しない村のnull guardがない。
  - `EchoApplication.java:292`は全`Throwable`を非数値command処理へ流す。
  - `CommonSubLogic.java:30-36`のrole表示が不整合に見えるが、仕様資料がない。
  - ID生成は100回後もcollisionを拒否しない。
- なぜ負債か: refactor中に「ついでに直したくなる」が、各修正は利用者可視の挙動を変える。
- 影響範囲: long topic、legacy UI、invalid village、Werewords、公平性。
- 変更リスク: 中から高。
- 改善案: 長文singleton mutation、null village、`catch (Throwable)`、ID collisionを独立再現test後に1件ずつ直す。`FrontServlet`はDB機能ごと削除する。Werewords role表示とお題分布は現行を固定し、推測修正しない。
- 検証: 各再現test、before/after expected result、route/message snapshot。
- 実装判断: 上記の承認済みbugは実装してよい。Werewords表示・お題分布の変更は提案のみ。

### D12. Build, CI, dependency, and quality tooling are stale and entangled

- 根拠: Java 8、Gradle 5.4.1、Spring Boot 2.1.5、2019-era plugins、deprecated configurations (`compile`, `testCompile`)、Travis、checked-in PostgreSQL 42.2.8 jar。Checkstyle/SpotBugsが無効でorphan configが残る。root READMEは元SDKのままで実アプリを説明しない。
- なぜ負債か: 現代JDKでbuildできない可能性、supply-chain管理不足、CI再現性とonboarding低下。
- 影響範囲: 全module、artifact publishing、runtime。
- 変更リスク: 最高。一括upgradeはJava/Spring/Jackson/Gradle/public APIを同時に変える。
- 改善案: Heroku / Java 8でgreen baselineを作る。dependency lock/verificationを検討する。checked PostgreSQL jarはDBコードとともに削除する。Java/Gradle/Spring upgradeは別計画にする。
- 検証: clean matrix build、artifact diff、integration tests、dependency report、startup smoke。
- 実装判断: このrefactorではbaseline docsとtest整備まで。major upgradeは提案のみ。

### D13. Public model extensions lack contract tests

- 根拠: `ButtonsTemplateNonURL`、`ButtonsTemplateNonTitle`、`URIActionNonAltUri`は`com.linecorp.bot.model.*`のpublic classでgame codeが利用するが、専用testがない。
- なぜ負債か: Jackson property shapeや`type` discriminatorがLINE APIへ送るwire contractなのに無検証。既存upstream classとの重複抽象化でもある。
- 影響範囲: LINE reply payload、外部SDK consumers。
- 変更リスク: 高（rename/delete）、低（test追加）。
- 改善案: exact serialization testsを追加する。外部利用確認前は統合・renameしない。将来はapp-owned factoryで標準modelを組み立てられるか提案する。
- 検証: exact JSON fixture、round-tripが仕様対象ならround-trip、LINE request mock。
- 実装判断: test追加は今すぐ可。外部consumerはいないが、game usageを移行・検証してから固有public extensionを整理する。

### D14. Overbroad ignore rules and orphaned generated/local state

- 根拠: `.gitignore:55-63`が全`*.json`、`*.yaml`、`*.kt`、`*.script`等をrepository全体でignoreする。ローカルには大量のignored build/bin/IDE成果物がある。
- なぜ負債か: 新しいtest fixture、CI config、Kotlin/source scriptが意図せず未追跡になり、reviewから漏れる。
- 影響範囲: tooling、fixtures、developer workflow。
- 変更リスク: 中。ignoreを狭めると既存ローカル生成物や秘密configが大量にuntracked表示され得る。
- 改善案: 既存ignoredファイルをinventoryし、directory-specific ruleへ段階的に置換する。local secret patternは明示する。
- 検証: 変更前後`git status --short --ignored`、代表fixtureの`git check-ignore -v`、secret scan。
- 実装判断: inventoryは今すぐ可。rule変更は既存local fileを誤ってstageしないことを確認して独立phaseで実装。

### D15. Naming, magic values, and ownership are unclear

- 根拠: `CreatVillage` / `CreatWereWordsLogic` / `replyDefoltMessage`等のtypo、roleを1/2/3/4、special flagを10、GM sentinelを999で表現。`SpecialVillage.ownerId`は`"DEFOLT"`。domain entityがLINE presentation modelを直接生成する。
- なぜ負債か: domain invariantが型で表現されず、typo修正がpublic class renameと混同される。entityがtransport/presentationを所有する。
- 影響範囲: role logic、public package、message rendering。
- 変更リスク: 中。public renameとserialization変更の危険。
- 改善案: まずprivate constants/value typesとrendererを導入し、magic valueの意味をtestで固定する。既存public typo classはrenameせず、必要なら新名称wrapper/deprecationを提案する。
- 検証: role matrix、message snapshot、public API diff、serialization。
- 実装判断: private/internal extractionはtest後に可。public typo classは外部consumerがいなくても一括renameせず、game usage移行と同じ小changeで扱う。

## Implementation Phases

各phase終了時にtargeted tests、`./gradlew check`（実行可能なら）、`git diff --check`、`git status --short`を実行し、結果を記録する。phaseを跨いだ巨大diffを作らない。

### Phase 0: Establish and record the baseline

1. Non-Negotiablesに従ってworking tree、JDK、Gradle、test、coverage、bootJarを確認する。
2. baseline failureを「環境」「既存production/test」「外部dependency取得」に分類する。
3. 現在のpublic class、HTTP route、CLI command、Jackson subtype、Spring auto-configurationをinventoryする。
4. Heroku / Java 8をbaselineとし、major runtime/toolchain upgradeを混ぜない。
5. baselineを直すためにproduction behaviorを変更しない。

### Phase 1: Build safety nets before structural changes

1. `sample-spring-boot-echo`にtest source setを追加する。
2. 現行の通常村、神モード、逆村、特殊村のrole assignment、capacity、repeat join、owner statusをcharacterizeする。
3. `GET /callapi`、`/specialvillage`、`POST /callback`のvalid contractをMockMvcで固定する。承認済みのinvalid input契約も再現testにする。
4. custom public message/action modelのexact JSON testを追加する。
5. 未参照callback fixturesを使い、主要event subtypeのparseを再度coverする。過去commitのtestは参考にしてよいが、盲目的に戻さない。
6. external LINE/Googleはmock/fakeにし、testから実networkへ出ないことを保証する。DBは利用しない。
7. random-dependent testはproduction乱数の統計へ依存させず、最小のinjection seamまたはdeterministic collaboratorを使う。seam追加でproduction outputを変えない。

### Phase 2: Perform obviously safe cleanup

1. unused import、到達不能でないことが証明されたprivate helper内の明白な重複、comment typoなど、runtime/APIへ影響しない変更だけを行う。
2. `System.out` / `printStackTrace`をloggerへ小分けで移す。ただしlog level、PII、exception propagationをtest/reviewする。
3. interruptをcatchした箇所ではinterrupt statusを復元する。この変更をLINE reply error testで確認する。
4. magic string/valueへprivate constantを付ける。値そのものは変えない。
5. `.gitignore`はinventory後、独立した小変更としてのみ扱う。
6. 一般のdead codeは「参照が見つからない」だけで削除しない。Spring/Jackson/reflection、古いpostbackを確認する。DB/history関連は明示的な削除対象としてPhase 5で扱う。

### Phase 3: Separate small responsibilities inside the application

1. `EchoApplication`から、command parse、village creation、participation、message rendering、LINE reply I/Oを一つずつ抽出する。
2. 一度に一つの分岐群だけ移し、before/after testsを通す。
3. `MainController`とLINE handlerの重複参加処理を同じapplication serviceへ委譲する。
4. domain resultとLINE `Message` renderingを分ける場合、文言・action・JSONをsnapshot/contract testで同一に保つ。
5. constructor injectionを優先し、static global accessを新たに増やさない。

### Phase 4: Clarify state and external boundaries

1. `VillageRegistry` / `SpecialVillageRegistry`相当でstatic collectionsを包み、mutable collectionを外へ返さない。
2. create/find/join/evictを明示operationにする。現行上限・FIFO・番号範囲は保持する。
3. concurrent joinをatomicにし、同一userのidempotencyをtestする。順序変更が起きる場合は停止して確認する。
4. illustration、word source、LINE replyをinterface越しにし、fake可能にする。history送信は抽象化せず削除する。
5. 維持するexternal request/responseをtyped DTOにする。production URL、payload、failure policyは変えない。

### Phase 5: Remove retired DB/history code and correct module ownership

1. DB関連のroute `/Insider`、Thymeleaf template/static UI、`FrontServlet`、`InsertLogic`、`ProblemDao`、`ConnectionManager`、`Problem`、credential `Properties`、PostgreSQL jar/dependency、DB専用suppressionを参照順に削除する。
2. `EchoApplication`の非数値postback DB登録、unused `getOdai`、DB専用constant/importを削除する。古いpostbackが届いてもDBへ接続しないことをtestする。
3. `Request`、`EntryOdai`、history URL/flag、topic設定時の`Request.run`を削除し、LINE user IDがGoogle Apps Scriptへ送られないことをtestする。
4. Google画像一覧取得、scheduled refresh、GitHub Raw fallbackは維持し、画像取得testを通す。
5. game/web固有classを`sample-spring-boot-echo`へ移し、`LineMessageHandlerSupport`はevent dispatchだけを担当させる。現役`/specialvillage` controllerはapp側で同一契約を維持する。
6. `line-bot-cli`の依存縮小はCLI context/commands/artifactを保持する専用changeにする。CLI自体を無関係に削除しない。
7. moduleごとのdependency graph、jar contents、Heroku bootJar、現役routeを検証する。

### Phase 6: Address approved defects one at a time

1. D11の承認済みbugを独立test・独立changeとして扱う。
2. 存在しない村、必須parameter不足、満員、内部errorをResolved Decisionsの契約へ合わせる。
3. long messageで変更不能listへ追加する問題、`catch (Throwable)`、ID collisionを再現test後に修正する。
4. Werewords表示とお題抽選分布は変更しない。
5. bug fixをstructural refactorやformattingと混ぜない。
6. LINEの文言、action、status、old postback compatibilityに差が出る場合、before/afterを報告する。

### Phase 7: Propose large changes; do not implement without approval

以下は設計案、migration steps、risk、rollback、検証matrixを文書化するだけに留める。

- Java/Gradle/Spring Boot/Jackson/LINE SDKのmajor upgrade
- SDK forkを公式/current SDKへ置換
- persistent/shared game state、multi-instance化
- 認証、CORS、rate limit、API versioningの変更
- illustration serviceの廃止・置換
- public package/APIの削除・rename

### Phase 8: Purge revoked DB credentials from Git history

このphaseはsource refactorとclean verificationが完了し、必要な変更がcommitされた後にのみ実行する。DB資格情報の履歴purgeは明示承認済みだが、対象を推測して広範囲にforce-pushしてはならない。

1. `git status`がcleanであることを確認し、`origin`の全branch/tag/refをfetchして、credentialを含むrefをread-only scanで特定する。値そのものをterminal/reportへ出さない。
2. 現調査時点では`origin/3.0`と`origin/develop`が存在しtagはないが、実行時のremote状態を正とする。予期しないbranch、tag、open PR用ref、branch protectionがあればforce-push前に停止して報告する。
3. rewrite前のremote SHA、対象ref、実行tool/version、rollback手順を記録する。必要なら失効済みsecretを含むoffline mirror backupを暗号化・アクセス制限された場所へ作るが、そのbackup refをremoteへpushしない。
4. `git filter-repo`等のpurpose-built toolで、DB host、username、passwordを全到達可能commitから除去する。可能なら不要なcredential holder file自体をhistoryから除去し、他file/過去pathに同じ値がないこともscanする。
5. rewrite後に全local refをsecret scanし、source/build artifactにもDB値・JDBC jar・DB codeがないことを確認する。
6. 対象branch/tagだけを、記録した旧SHAをlease条件にしたforce-pushで`origin`へ反映する。単純な`--force`や対象不明のmirror pushを使わない。
7. remoteを新しい一時cloneへ再fetchし、全remote refを再scanする。Git hosting側にcache、PR ref、release artifactが残る場合は、追加purge手順を報告する。
8. 全共同作業者へ「旧cloneからpushしないこと」「再cloneすること」「旧commit SHAが無効になったこと」を伝えるための文面を最終報告に含める。
9. old-to-new SHA map、実行command、対象ref、remote verification結果を報告する。secret値そのものは記載しない。

## Verification Requirements

### Required on every phase

```bash
git diff --check
git diff --stat
git status --short
```

実行可能ならtargeted testに加えて:

```bash
./gradlew check
```

### Required final verification

```bash
./gradlew clean check codeCoverageReport :sample-spring-boot-echo:bootJar
git diff --check
git status --short --branch
```

### Contract verification checklist

- webhook missing/invalid signatureは現行testどおり400。
- valid callback eventが正しいhandlerへdispatchされる。
- text/postback/sticker/defaultの既存routingが維持される。
- 全command aliasと全角`＠` variantが維持される。
- 通常/神/逆村/Werewordsのrole matrixが承認済み期待値と一致する。
- same user再参加、capacity boundary、owner access、FIFO evictionが一致する。
- `/callapi`と`/specialvillage`のpath、method、CORS、query/body、status、JSON shapeが承認済み契約と一致する。
- 存在しない村、必須parameter不足、満員、内部errorがResolved Decisionsの契約と一致する。
- custom modelのJackson JSONがLINE wire contractと一致する。
- CLIの全command selection、payload mode、no-command startupを確認する。
- `word.csv`をpackaged jar/classpathから読み、rank distributionを変えていない。
- real LINE、Google、PostgreSQLへtest trafficを送っていない。
- `/Insider`、DB class/template/JDBC jar/dependency、DB接続文字列、history送信class/URL/call siteがsourceとartifactに残っていない。
- Google画像一覧取得とfallback画像が引き続き機能する。
- secret、channel token/secret、DB password、user ID、topicをlog/report/artifactへ新規漏えいさせていない。
- concurrency testでcapacity超過・duplicate join・role duplicationがない。
- build artifactに意図しないmodule/resource/API差分がない。

### Baseline-failure rule

- baselineですでに失敗したtestは、再現commandとfailureを変更前に保存する。
- 変更後のfailureが同一であることを証拠なく「既存」と決めつけない。
- 新規failureを既存failureに埋もれさせない。
- Java Runtimeやnetwork不足でfull verification不能なら、実行したtargeted verificationと未検証範囲を明記し、完遂と主張しない。

## Reporting Format

最終報告は以下の順で、具体的なfileとcommand resultを記載する。

1. **Baseline**
   - initial `git status`
   - Java/Gradle versions
   - baseline commandsのpass/fail/blocked
   - 既存failureと環境制約
2. **Questions and Decisions**
   - 質問した内容
   - 人間の回答
   - 採用した選択肢
   - 保留した変更
3. **Changes by Phase**
   - phaseごとの目的
   - 変更file
   - behaviorを保持した証拠
   - rollback単位
4. **Verification**
   - 実行したcommandを省略せず列挙
   - exit/result
   - targeted/contract/concurrency test結果
5. **Behavioral Differences**
   - 承認済みの差分だけをbefore/afterで記載
   - 差分がない場合は「なし」と明記
6. **Remaining Risks and Proposals**
   - 未回答質問
   - 未検証外部境界
   - out-of-scope proposal
7. **Final Working Tree**
   - 最終`git status --short --branch`
   - ユーザー既存変更を触っていないこと
8. **History Purge**
   - rewrite tool/versionと対象ref
   - old-to-new SHA map
   - lease付きforce-push結果
   - fresh cloneでのremote secret scan結果
   - 共同作業者向け再clone案内

「全test成功」と書くのは、clean final commandが実際に成功した場合だけにする。

## Out-of-scope Items

明示承認がない限り、以下は今回実装しない。

- SDK全体の全面書き換え、公式最新版への置換
- Java、Gradle、Spring Boot、Jackson、Retrofit/OkHttpの一括major upgrade
- ゲームBotが必要とするSDK全体の削除。外部consumerはいないため、game固有classの安全なmodule移動・整理は対象内
- game stateの永続化、cluster化、queue導入
- 認証方式、課金、rate limit、CORS policyのproduct決定
- LINE message文言、game rule、difficulty distribution、role balanceのproduct変更
- UI/CSS/HTMLのredesign
- 維持対象の画像一覧Google Apps Script、Netlify、GitHub Raw、Wikipedia側の変更。LINE user ID履歴送信の削除は対象内
- DB以外のcredential rotationやGit履歴書き換え。失効済みDB資格情報の履歴purgeは対象内
- tracked imageや`word.csv`の整理・圧縮・削除
- userの未追跡`.DS_Store`、ignored build/bin/IDE fileのcleanup
- 無関係なformatting、copyright、license header、改行コードの一括変更

完了条件は「大きく綺麗にした」ことではない。承認された範囲で安全網を増やし、重複と責務混在を小さく減らし、外部境界と状態所有を明確にし、既存仕様を検証可能な形で保持したことを証拠付きで示すことである。
