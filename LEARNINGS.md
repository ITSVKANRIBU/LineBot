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
- 2026-09-07: 実装指示書は書いた後に Codex へ「コードと突き合わせてレビュー」させると、事実誤認や自己矛盾（Bean スキャン範囲、テスト無変更ルールとリファクタの両立不能、シャッフル前後の順序混同）を拾える。指摘反映後にもう 1 巡させると、反映で生じた新しい矛盾が見つかる。
- 2026-09-06: この Mac は java が PATH にない。Gradle は `JAVA_HOME=/opt/homebrew/opt/openjdk@11` を付けて実行する（本番・CI は JDK 8）。`check` は 5 モジュール計 201 テスト、`:sample-spring-boot-echo:test` は 73 テストが 2026-09-06 時点のベースライン。
- 2026-09-06: リポジトリに checkstyle/spotbugs は適用されていない（`config/checkstyle/` は未参照の残骸）。実質的な lint は `compileJava` の `-Xlint:all -Werror`。

## Mistakes to Avoid
（失敗と再発防止策）
- 2026-09-06: zsh で `grep -r ... --include=*.java` を引用符なしで書くと glob 展開で `no matches found` になり grep 自体が走らない。`--include='*.java'` と引用する。
- 2026-09-06: Claude Code はスキルをセッション開始時に読み込むため、セッション中に新規作成した `.claude/skills/` 配下のスキルは同一セッションでは呼び出せない。作成直後の動作確認は次セッションで行う。

## Domain Knowledge
（業務・仕様に関する事実）
- 2026-09-07: オーナー確認済み: LINE と `/callapi` の処理は LINE を正として共通化する（数値境界 100、`@` コマンドの解釈を API にも適用）。経路差として残すのは「対象の村がないときの応答」だけで、API は `村が作成されていません` テキストを維持する。
- 2026-09-07: `EchoApplication` の `@SpringBootApplication` は `com.example.bot.spring.echo` 配下しかスキャンしない。`common`/`spring.game`/`staticdata` に Bean を置くなら `scanBasePackages = "com.example.bot"` が必要。
- 2026-09-07: `SpecialVillageController` は村作成を含む全体を `catch (Exception)` で 400 に丸めるため、`docs/interfaces.md` の「内部エラー 500」は `/callapi` にしか当たらない。`CreatVillage` は登録時に全メッセージをシャッフルするので、`getMessages` の先頭役職は配布順を意味しない。
- 2026-09-06: `word.csv` の 2 列目（難易度 1〜5）の切り替わり行は `WordGetter` の行番号定数（954/5084/7646/8436）と完全に一致する。難易度境界は 2 列目から導出できる。
- 2026-09-06: `docs/` は外部仕様の一次資料で、既存テストは応答文を完全一致で検証している。応答文字列・数値境界・`Random` の呼び出し順は契約として扱う。
- 2026-09-06: オーナー確認済み: Werewords の欠け表記 `WEREWORDS_ROLE_MAP[3]` は不具合（`村人` が正）。神モードで人数→お題の順に設定したときの人数入力を促す応答は許容仕様。役職画像カタログの形式外要素は docs どおり要素単位で無視し、HTTP は接続 15 秒・読み取り 30 秒でタイムアウトさせる。`line-bot-cli` は未使用で削除可。
- 2026-09-06: CLAUDE.md は `@AGENTS.md` の1行参照のみで、プロジェクト共通指示の実体は AGENTS.md 側にある。指示の追記は AGENTS.md に行う。

## Open Questions
（未解決・要調査）

## Consolidated Principles
（統合パス専用。通常の更新処理から直接追記しない）
