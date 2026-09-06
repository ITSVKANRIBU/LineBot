# Project Learnings

<!--
記法ルール:
- 1項目1洞察。複数の学びを1行に詰めない
- 各項目の先頭に日付を必ず入れる（例: - 2026-08-24: ...）
- 上4セクションは「生の観察」の置き場。Consolidated Principles には
  統合パスで抽出した原則だけを置く。両者を混ぜない
-->

## Patterns That Work
（効いたやり方・型）
- 2026-09-07: 挙動不変のリファクタリングでは、先に「応答を丸ごと捕捉して固定するテスト」を1本入れてから構造を動かすと、以降の全コミットで挙動不変を機械的に確認できる。LINE経路は `LineMessagingClient` をmockして `ArgumentCaptor` で `ReplyMessage` を捕まえ、altText・ボタン構成・通数まで固定した。
- 2026-09-07: 2つの経路が同じ結果を返すことは、代表的な入力の並びを両経路へ流して応答を突き合わせるテスト1本で守れる。経路ごとに変わる値（村番号）は正規表現で伏せ、意図的に残す経路差（村なし応答）は同じ印へ正規化すると、差分がそのまま「意図しない分岐」になる。
- 2026-09-07: staticをやめてDI化するとき、テスト側に本番と同じ依存関係で組み立てるfixtureクラスを1つ置くと、各テストの `@Before` が1行で済む。`clear()` の共有をやめた証拠は、一時的に `maxParallelForks` を上げて全件通ることで取れる。
- 2026-09-07: 定数を導入して式を書き換えるときは、展開後が元の式と文字通り同一になるかを確認する。村番号の抽選は `nextInt(8999)+1000` と `nextInt(89999)+10000` で、前者は `MAX-MIN`、後者は `MAX-MIN+1` と非対称だった。
- 2026-09-07: 実装指示書は書いた後に Codex へ「コードと突き合わせてレビュー」させると、事実誤認や自己矛盾（Bean スキャン範囲、テスト無変更ルールとリファクタの両立不能、シャッフル前後の順序混同）を拾える。指摘反映後にもう 1 巡させると、反映で生じた新しい矛盾が見つかる。
- 2026-09-06: この Mac は java が PATH にない。Gradle は `JAVA_HOME=/opt/homebrew/opt/openjdk@11` を付けて実行する（本番・CI は JDK 8）。`check` は 5 モジュール計 201 テスト、`:sample-spring-boot-echo:test` は 73 テストが 2026-09-06 時点のベースライン。
- 2026-09-06: リポジトリに checkstyle/spotbugs は適用されていない（`config/checkstyle/` は未参照の残骸）。実質的な lint は `compileJava` の `-Xlint:all -Werror`。

## Mistakes to Avoid
（失敗と再発防止策）
- 2026-09-07: 一括置換で識別子を書き換えるときは、長い名前を先に処理する。`VillageList`→`villages` を先に当てたため `SpecialVillageList` が `Specialvillages` になった。接頭辞が共通する識別子は同じ正規表現に巻き込まれる。
- 2026-09-07: `awk 'length > 100'` はUTF-8を**バイト数**で数えるため、日本語コメントが軒並み長すぎると誤検出される。行長を見るなら文字数で数えるか、コード行だけに絞る。
- 2026-09-06: zsh で `grep -r ... --include=*.java` を引用符なしで書くと glob 展開で `no matches found` になり grep 自体が走らない。`--include='*.java'` と引用する。
- 2026-09-06: Claude Code はスキルをセッション開始時に読み込むため、セッション中に新規作成した `.claude/skills/` 配下のスキルは同一セッションでは呼び出せない。作成直後の動作確認は次セッションで行う。

## Domain Knowledge
（業務・仕様に関する事実）
- 2026-09-07: `word.csv` の2列目から難易度境界を導出すると、途中で読み込みが途切れた辞書の境界が不整合になり `Random.nextInt` が負の上限で例外になる。読み込み後に全難易度の境界が揃っているか検証し、揃わなければ辞書を捨てる必要がある。これは `docs/interfaces.md` の「読み込みに失敗した場合、お題の自動取得は何も返しません」と一致する。
- 2026-09-07: `@LineMessageHandler` は `@Component` のメタannotationを持つため、受け口クラスをコンポーネントスキャン範囲に置くだけでBean登録される。`@Bean` メソッドは不要。
- 2026-09-07: `LineMessageHandlerSupport.eventConsumerList` はpackage-privateだが `ReflectionTestUtils.getField` で覗ける。`@SpringBootTest` でハンドラ登録数と、各イベントに選ばれるハンドラの所有Beanを検証できる。
- 2026-09-07: オーナー確認済み: LINE と `/callapi` の処理は LINE を正として共通化する（数値境界 100、`@` コマンドの解釈を API にも適用）。経路差として残すのは「対象の村がないときの応答」だけで、API は `村が作成されていません` テキストを維持する。
- 2026-09-07: `EchoApplication` の `@SpringBootApplication` は `com.example.bot.spring.echo` 配下しかスキャンしない。`common`/`spring.game`/`staticdata` に Bean を置くなら `scanBasePackages = "com.example.bot"` が必要。
- 2026-09-07: `SpecialVillageController` は村作成を含む全体を `catch (Exception)` で 400 に丸めるため、`docs/interfaces.md` の「内部エラー 500」は `/callapi` にしか当たらない。`CreatVillage` は登録時に全メッセージをシャッフルするので、`getMessages` の先頭役職は配布順を意味しない。
- 2026-09-06: `word.csv` の 2 列目（難易度 1〜5）の切り替わり行は `WordGetter` の行番号定数（954/5084/7646/8436）と完全に一致する。難易度境界は 2 列目から導出できる。
- 2026-09-06: `docs/` は外部仕様の一次資料で、既存テストは応答文を完全一致で検証している。応答文字列・数値境界・`Random` の呼び出し順は契約として扱う。
- 2026-09-06: オーナー確認済み: Werewords の欠け表記 `WEREWORDS_ROLE_MAP[3]` は不具合（`村人` が正）。神モードで人数→お題の順に設定したときの人数入力を促す応答は許容仕様。役職画像カタログの形式外要素は docs どおり要素単位で無視し、HTTP は接続 15 秒・読み取り 30 秒でタイムアウトさせる。`line-bot-cli` は未使用で削除可。
- 2026-09-06: CLAUDE.md は `@AGENTS.md` の1行参照のみで、プロジェクト共通指示の実体は AGENTS.md 側にある。指示の追記は AGENTS.md に行う。

## Open Questions
（未解決・要調査）
- 2026-09-07: `InsiderRole.setUserId` は参照ゼロだが、指示書のD6の削除リストに載っていなかったため残してある。次に `InsiderRole` を触るときに削除してよいか。

## Consolidated Principles
（統合パス専用。通常の更新処理から直接追記しない）
