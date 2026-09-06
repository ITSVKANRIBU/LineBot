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

## Mistakes to Avoid
（失敗と再発防止策）
- 2026-09-06: Claude Code はスキルをセッション開始時に読み込むため、セッション中に新規作成した `.claude/skills/` 配下のスキルは同一セッションでは呼び出せない。作成直後の動作確認は次セッションで行う。

## Domain Knowledge
（業務・仕様に関する事実）
- 2026-09-06: CLAUDE.md は `@AGENTS.md` の1行参照のみで、プロジェクト共通指示の実体は AGENTS.md 側にある。指示の追記は AGENTS.md に行う。

## Open Questions
（未解決・要調査）

## Consolidated Principles
（統合パス専用。通常の更新処理から直接追記しない）
