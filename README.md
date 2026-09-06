# インサイダーゲーム Bot

会話ゲーム「インサイダーゲーム」と「Werewords」で、**参加者それぞれに異なる秘密情報（役職とお題）を配る**ための LINE Bot です。

対面で遊ぶゲームのうち、機械が必要なのは配役の瞬間だけです。LINE の 1 対 1 トークを配布経路にすることで、各参加者は自分の情報だけを、いつでも自分の端末で確認できます。ゲームの進行と勝敗判定は対象外です。

## 使い方（利用者向け）

Bot とのトークで `お題` と送ると 4 桁の村番号が返ります。お題と参加人数を設定し、参加者へ村番号を伝えてください。参加者がその番号を送ると、参加順に役職が配られます。

詳細は [ゲームの外部仕様](docs/game-spec.md) を参照してください。

## 動かす（開発者向け）

Java 8 が必要です。

```bash
./gradlew :sample-spring-boot-echo:bootRun
```

環境変数 `LINE_BOT_CHANNEL_TOKEN` と `LINE_BOT_CHANNEL_SECRET` を設定してください。LINE Developers コンソールでは、Webhook URL を `https://<アプリのホスト>/callback` に設定します。

## ドキュメント

設計と仕様は [`docs/`](docs/README.md) にあります。

| 文書 | 内容 |
| --- | --- |
| [overview.md](docs/overview.md) | システムの目的、スコープ、設計原則 |
| [game-spec.md](docs/game-spec.md) | ゲームの外部仕様。村の種類、コマンド、役職決定規則 |
| [interfaces.md](docs/interfaces.md) | webhook と公開 HTTP API の契約、外部依存 |
| [architecture.md](docs/architecture.md) | コンポーネント境界、状態モデル、並行性、設計判断 |
| [operations.md](docs/operations.md) | 実行環境、設定、デプロイ、ログ方針 |
| [roadmap.md](docs/roadmap.md) | 今後の計画（検討中のもの） |

## リポジトリの構成

Gradle のマルチプロジェクトビルドです。`sample-spring-boot-echo` が Bot 本体、`line-bot-*` が LINE Messaging API SDK（line-bot-sdk-java 由来。同梱）、`line-bot-cli` はリッチメニューなどの運用ツールです。詳細は [architecture.md](docs/architecture.md) を参照してください。

## ライセンス

[LICENSE.txt](LICENSE.txt)（Apache License 2.0）
