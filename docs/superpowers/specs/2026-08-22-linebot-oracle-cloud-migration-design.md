# LineBot を Oracle Cloud Always Free へ移行する設計

## 背景と目的

現在 LineBot と BoardGame backend はそれぞれ別の Heroku アカウントで Eco プラン
($5/月) を使い、スリープさせずに運用している。1,000 dyno 時間/月のプールを共有
すると 2 アプリ分 (1,460 時間) に足りないため、アカウントを 2 つに分けており、
合計 $10/月かかっている。

このコストをゼロに近づける。本設計では **LineBot 1 本だけ**を Oracle Cloud
Always Free の ARM VM へ移す。BoardGame backend は同じ VM に後から載せる前提で
構成を決めるが、本設計の作業対象には含めない。

### 非目的

- BoardGame backend の移行 (同じ VM に載せる余地は残すが、本設計では実施しない)
- ゲーム状態の永続化。状態はプロセス内メモリのままとする
- 2 プロジェクトのコード統合。VM は課金単位がマシンなので統合の動機が消える
- アプリケーションコードのリファクタリング

## 前提の検証結果

移行の成否を左右する事実は、以下のとおり一次情報で確認済み。

| 項目 | 確認結果 | 出典 |
| --- | --- | --- |
| ARM (aarch64) で Java 8 が動くか | Temurin JDK 8 は aarch64 を正式サポート。TCK 検証済みで、セキュリティパッチは少なくとも 2029 年 3 月まで | Adoptium |
| 無料枠の永続性 | 「30 日トライアル終了後もアカウントは有効なまま。Always Free リソースの可用性に中断はない」 | Oracle 公式 docs |
| 意図しない課金 | 「アカウントをアップグレードしない限りクレジットカードに請求されることはない」。カードは本人確認用 | Oracle 公式 docs |
| Always Free の枠 | 1,500 OCPU 時間 + 9,000 GB 時間/月 = 2 OCPU / 12 GB を常時起動。ブロックストレージ 200 GB、ブートボリューム最小 47 GB | Oracle 公式 docs |
| ホームリージョン | サインアップ時に決定し、**後から変更不可**。Always Free のコンピュートはホームリージョンにしか作れない | Oracle 公式 docs |
| 下り通信量 | 10 TB/月 (二次情報。公式 docs には記載がないため、実運用で監視する) | 二次情報 |
| 東京での A1 確保 | US リージョンは `Out of host capacity` が数時間〜数日続く一方、Tokyo を含む APAC は通常 5 分以内にプロビジョニングされるとの報告 | 二次情報 |
| LINE webhook の制約 | 2 秒以内に 2xx を返さないとタイムアウト扱い | LINE 公式 docs |

Spring Boot 2.1.5 はアーキテクチャ依存のネイティブライブラリを使っていない
(Tomcat / OkHttp / Retrofit / Jackson はいずれも純 JVM)。aarch64 の JDK が載れば
動作する。

## 移行後の構成

```
Internet
  │ HTTPS 443
  ▼
Caddy (自動 TLS, Let's Encrypt)
  ├─ bot.<domain>  ──▶ 127.0.0.1:8081  linebot.service    Spring Boot 2.1.5 / Temurin 8 aarch64
  └─ api.<domain>  ──▶ 127.0.0.1:8082  boardgame.service  (2 個目。本設計では作らない)
```

### VM

| 項目 | 値 | 理由 |
| --- | --- | --- |
| シェイプ | VM.Standard.A1.Flex | Always Free で常時起動でき、12 GB を使える唯一のシェイプ |
| リージョン | **ap-tokyo-1** | 不可逆な選択。2 個目に載せる BoardGame の WebSocket レイテンシを国内に収めるため。ap-osaka-1 への切り替えは**サインアップ後には不可能**なので代替にならない (後述) |
| OCPU / メモリ | 2 OCPU / 12 GB | Always Free の上限をそのまま使う |
| OS | Ubuntu 24.04 LTS (aarch64) | 標準サポートが 2029 年まで。22.04 は 2027 年 4 月で切れる |
| ブートボリューム | 50 GB | 最小 47 GB を満たし、200 GB の枠に十分収まる |

### A1 容量が確保できない場合の方針

`Out of host capacity` は現実に起こり得る。ここで成立しない代替を並べておくと、
不可逆なサインアップの後で行き場がなくなるため、あらかじめ決めておく。

**成立しない代替 (採用しない):**

- **可用性ドメインの変更** — ap-tokyo-1 の availability domain は **1 つだけ**
  (ap-osaka-1 も同様)。切り替える先が存在しない
- **ap-osaka-1 への変更** — Always Free のコンピュートはホームリージョンにしか
  作れず、ホームリージョンはサインアップ時に確定して変更できない。容量の有無が
  分かるのはサインアップ後なので、これは代替として機能しない
- **VM.Standard.E2.1.Micro (x86 / 1 GB / 2 台)** — 3 つの理由で本設計と両立しない。
  (1) x86 なので aarch64 の Temurin 8 が動かず、JDK の指定が崩れる。
  (2) 1 GB に `-Xms3g` は載らず、Spring Boot 2.1.5 を動かす余地もほとんどない。
  (3) **アイドル回収のメモリ条件は A1 シェイプ限定**であり、E2 では CPU と
  ネットワークだけが判定対象になる。静かな Bot は両方とも閾値を下回るため、
  メモリというレバーが存在せず、回収を避ける手段がなくなる

**採用する方針:**

ap-tokyo-1 で A1 の確保をリトライする。取得できるまでは **Heroku のまま運用を
続ける**。移行を始める条件は「A1 インスタンスが起動していること」であり、それが
満たされないうちは Heroku 側に一切手を触れない。一定期間試して取得できない場合、
本設計は「保留」で終わらせる。動いている製品を、成立しない構成と引き換えにしない。

### JDK

Temurin 8 (aarch64) の tarball を `/opt/java/temurin8` に**バージョン固定で配置**
する。Ubuntu 24.04 の archive には openjdk-8 が存在せず、ディストリのパッケージに
依存できない。systemd unit から絶対パスで起動するため、`update-alternatives` で
システム既定を切り替える必要はなく、2 個目 (Temurin 11) と無条件に共存できる。

JDK の更新は四半期ごとの Temurin リリースに合わせて tarball を差し替える。手順は
セットアップ手順書に含める。

### プロセス管理

`linebot.service` を systemd unit として定義する。

- `Restart=always` / `RestartSec=5`
- 専用の非 root ユーザー `linebot` で実行
- `EnvironmentFile=/etc/linebot.env`
- ヒープ: **`-Xms3g -Xmx3g -XX:+AlwaysPreTouch -XX:+UseG1GC -XX:MaxGCPauseMillis=200`**

ヒープ値の根拠は次節のとおり。

### アイドル回収への対策

Always Free インスタンスは、CPU (95 パーセンタイル)・ネットワーク・メモリの
**3 条件すべて**が 7 日連続で閾値を下回ると回収対象になる (公式 docs の表記は
20% 未満。二次情報では 10% とするものもあるため、厳しい側の 20% を基準にする)。

メモリを唯一の確実なレバーとして使う。12 GB の 20% は 2.4 GB なので、`-Xms3g`
で 3 GB を確保して条件から外す (約 25%、閾値に対して十分なマージンを取る)。
`-Xms` だけでは JVM が仮想アドレス空間を予約するだけで RSS が遅延して伸びるため、
**`-XX:+AlwaysPreTouch` を必ず併記して起動時に実メモリを触らせる**。これがないと
OS から見た使用量が上がらず、対策として機能しない。

3 GB のヒープに対して既定の Parallel GC を使うと、フル GC のポーズが LINE の 2 秒
制限を脅かし得る。**`-XX:+UseG1GC -XX:MaxGCPauseMillis=200` を明示してポーズを
抑える** (G1 は Java 8u40 以降で本番利用可能)。実際にはライブデータが数十 MB
しかないためフル GC はまず起きないが、無料枠の都合で不自然に大きいヒープを与える
という判断の副作用を、応答時間の制約に持ち込まないための保険である。

2 個目を載せた段階では、両 JVM に `-Xms1600m -Xmx1600m -XX:+AlwaysPreTouch`
(合計 3.2 GB) を割り当てて同じ条件を満たす。

CPU を焚く cron や、ネットワークを流すためだけの外部監視は不要。CPU 条件を外すには
2 コアの 20% を 95 パーセンタイルで焚き続ける必要があり無駄が大きい。ネットワーク
条件も、数回の ping では回線容量の 20% に到底届かないため成立しない。メモリが唯一
妥当なレバーである。

### ネットワーク

- OCI の security list で ingress を 22 / 80 / 443 に限定する
- **OCI の Ubuntu イメージは iptables がデフォルトで塞いでいる**ため、OS 側でも
  80 / 443 を明示的に開ける。ここを忘れると security list を開けても疎通しない
- SSH は公開鍵のみ。パスワード認証と root ログインを無効化する

### 公開とTLS

独自ドメインを取得し (Cloudflare Registrar の .com が $10.44/年、卸値のまま。
更新時の値上げなし)、`bot.<domain>` の A レコードを VM の公開 IP に向ける。
Caddy が Let's Encrypt 証明書を取得し、自動更新する。

DuckDNS などの無料サブドメインは採用しない。webhook URL は LINE プラットフォーム
側に登録する外部契約であり、提供元が停止したときに LINE コンソール (将来は
Vercel の環境変数とフロントのハードコード既定値も) を触り直すことになる。年
$10.44 で URL を自己所有する方が長期的に安定する。$120/年 から $10.44/年で、
削減率は 91%。

### 公開ルートの方針

Caddy を「全パスをそのまま Spring Boot へ流す」設定にはしない。このアプリは
**署名検証のない状態変更 API を公開している**ためである。

- `/callapi` ([MainController.java:43-44](../../../sample-spring-boot-echo/src/main/java/com/example/bot/spring/echo/MainController.java)) は
  `@CrossOrigin` かつ無認証で、`userId` を呼び出し側が自由に指定できる。署名付き
  の LINE callback と**同じレジストリを変更する**
- `/specialvillage` ([SpecialVillageController.java:62-63](../../../sample-spring-boot-echo/src/main/java/com/example/bot/spring/echo/SpecialVillageController.java)) も
  `@CrossOrigin` かつ無認証
- `VillageList` は上限 50 件、`SpecialVillageList` は上限 30 件で、超過すると
  `villageList.remove(0)` で**古い村からFIFOで消える**。したがって無認証の
  リクエストを 50 回 / 30 回送るだけで、進行中の村を全部追い出せる

これは Heroku 上に既に存在する問題で、移行が作り出すものではない。ただしリバース
プロキシの設定を今書くので、プロキシ側で閉じられる範囲は本設計に含める。

- Caddy で `/callapi` と `/specialvillage` に**レート制限**をかける
- リクエストボディのサイズ上限を設定する (`/specialvillage` は 1 村 100 メッセージ
  × 各 5000 文字が仕様上の最大)
- `/callback` は LINE の署名検証があるため、レート制限は誤遮断を避ける水準に留める

認証の追加はアプリ側の変更かつプロダクト判断 (外部フォームの改修を伴う) なので、
本設計の対象外とする。「範囲外として記録する既存の問題」に残す。

### シークレット

Heroku の環境変数 `LINE_BOT_CHANNEL_TOKEN` / `LINE_BOT_CHANNEL_SECRET` を
`/etc/linebot.env` (`root:root`, `0600`) へ移し、systemd の `EnvironmentFile=`
で読む。GitHub Secrets には SSH 秘密鍵とホスト情報のみを置き、LINE の資格情報は
置かない。

`PORT` は Heroku 固有の仕組みなので使わず、`--server.port=8081` を固定で渡す。

### デプロイ

既存の `.github/workflows/ci.yml` に deploy job を追加する。

- トリガー: `3.0` への push (本番ブランチ)
- ビルド JDK: 現行どおり Temurin 8。本番の JVM と揃える意図を維持する
- deploy job は `check` job の成功を条件にする

`scp` して `systemctl restart` するだけでは、次の 3 つの事故で「CI は緑なのに Bot
は死んでいる」状態を作れる。いずれも安価に塞げるので本設計に含める。

| 事故 | 対策 |
| --- | --- |
| 連続 push で workflow が並走し、**古い commit が最後に着地する** | GitHub Actions の `concurrency` group を本番デプロイに設定し、直列化する |
| 転送が中断して**壊れた jar が本番の位置に残る** | バージョン付きの一時パスへ転送 → `sha256sum -c` で検証 → 検証済みのものだけを `mv` で原子的に昇格 |
| `systemctl restart` はアプリの起動完了前に返るため、**起動失敗を検知できない** | 再起動後に HTTP ヘルスチェックを成功までポーリングし、失敗したら job を落とす。併せて直前の jar を保持し、ヘルスチェックが通らなければ自動で戻して再起動する |

手順:

1. `./gradlew :sample-spring-boot-echo:bootJar`
2. jar と sha256 を `/opt/linebot/releases/<commit-sha>/` へ転送
3. VM 上で `sha256sum -c` を検証
4. 現行 jar を `previous` として退避し、新 jar を `current` へ `mv` で昇格
5. `systemctl restart linebot`
6. ヘルスチェックが通るまでポーリング。通らなければ `previous` へ戻して再起動し、
   job を失敗させる

Heroku の GitHub 連携は移行完了を確認するまで生かしたままにする。

## 検証方法

`/callapi` は本番の入口ではない。**これを主要な安全弁にはしない。**

- `/callapi` を処理する `MainController` は、数値と `お題` / `題` / `神` しか
  分岐を持たない。`@取得` / `@配布` / `@特殊` / `@逆村` / `@わーわーず` の分岐は
  `EchoApplication` 側にしかなく、`/callapi` に送ると**お題文字列として登録されて
  しまう**。つまり `/callapi` では、これらのコマンドを検証できない
- `/callback` のハンドラは `lineMessagingClient.replyMessage(...).get()` で
  **LINE API の応答を同期的に待つ** ([EchoApplication.java:174](../../../sample-spring-boot-echo/src/main/java/com/example/bot/spring/echo/EchoApplication.java),
  `:189`)。`/callapi` は LINE API を呼ばないので、その所要時間を含まない。
  **`/callapi` の応答時間では 2 秒制限を満たすことを示せない**

したがって、切替前の検証は**署名付きの `POST /callback` を Caddy 経由で通す**
ことを軸にする。LINE 側の資格情報を使わずに済ませるため、検証用チャンネル、または
`api.line.me` を差し替えた制御可能なスタブのいずれかを用意する。どちらを採るかは
実装計画で決める。

切替前に確認する項目:

1. 署名付き `POST /callback` を Caddy 経由で送り、通常村の作成 → 人数設定 →
   お題設定 → 参加 → 役職配布が期待どおりのメッセージを返す
2. 同じ経路で `@取得` / `@配布` / `@特殊` / `@逆村` / `@わーわーず` が応答する
   (`/callapi` では検証できないため、必ず `/callback` で行う)
3. 不正署名の `POST /callback` を拒否する
4. **`/callback` の応答完了までの所要時間**を計測し、LINE API 呼び出しを含んだ
   状態で 2 秒制限に対するマージンを確認する
5. `POST /specialvillage` が 5 桁の村番号を返す
6. `/callapi` と `/specialvillage` のレート制限が意図どおり効く
7. `systemctl restart` 後に自動復帰する
8. VM 再起動後に systemd が自動起動する
9. デプロイのヘルスチェック失敗時に、自動で直前の jar へ戻る

## カットオーバーとロールバック

進行中の村はメモリ上にしか存在せず、切替の瞬間に消える。**プレイヤーがいない
時間帯に実施する**。これは受け入れる副作用であり、回避しない (状態の永続化は
非目的)。

1. VM をプロビジョニングし、Caddy / JDK / systemd を構成する
2. 上記の検証項目をすべて通す
3. LINE Developers コンソールの webhook URL を `https://bot.<domain>/callback`
   へ切り替える
4. 実機の LINE から一通り操作して確認する
5. 1 週間、応答時間とアイドル回収の兆候を監視する
6. 問題がなければ Heroku を解約する

### ロールバック

LINE Developers コンソールの webhook URL を Heroku のものへ戻す。**1 フィールド
の変更で即時に戻り、再デプロイは不要**。Heroku アプリは移行後 1 か月維持して
ロールバック先として確保する。

ただし**ロールバックは操作が速いだけで、状態としては破壊的**である。OCI と Heroku
は独立したメモリ上のレジストリを持つため、

- 切替後に OCI 上で作られた村は、Heroku へ戻した瞬間に**すべて消える**
- Heroku 側には切替前の古い村が残っている可能性があり、番号の混乱を招く

初回の切替をプレイヤー不在の時間帯に行っても、それはその 1 回を守るだけで、
1 週間の観察期間や 1 か月の保持期間中に起こるロールバックは守らない。したがって
ロールバックの手順は次のとおりとする。

1. 破壊的操作として扱い、実行前にプレイヤーがいないことを確認する
2. やむを得ず進行中に戻す場合は、村が失われることを利用者へ告知する
3. 戻した後、Heroku 側に残っている古い村を再起動で明示的に破棄する

状態を保ったままの切り戻しは、レジストリを共有ストレージへ出さない限り成立しない。
それは本設計の非目的であり、ここでは**ロールバックは破壊的**と明記して受け入れる。

## リスクと対策

| リスク | 影響 | 対策 |
| --- | --- | --- |
| ホームリージョンの誤選択 | 不可逆。日本から 100ms 超が乗る | サインアップ時に ap-tokyo-1 を選ぶ。**作業の最初に確定させる** |
| A1 の `Out of host capacity` | VM が作れない | ap-tokyo-1 でリトライする。AD 変更・大阪・E2.1.Micro はいずれも代替にならない (「A1 容量が確保できない場合の方針」参照)。取得できるまで Heroku のまま運用する |
| アイドル回収 | インスタンス削除 | `-Xms3g -XX:+AlwaysPreTouch` でメモリ条件を外す (前述) |
| 2 秒制限に間に合わない | webhook タイムアウト | 切替前に**署名付き `/callback` の**所要時間を計測する (LINE API の同期待ちを含む)。TLS 終端は Caddy が VM 内で行うため追加ホップはない。Heroku (US) から東京へ移ることで LINE API への往復は短くなる方向 |
| 自前運用の負荷 | OS / TLS / プロセスの管理 | Caddy で TLS を全自動化、systemd で自動再起動、unattended-upgrades で OS 更新を自動化する |
| 下り 10 TB/月の超過 | 課金 | LINE Bot はテキスト応答のみで到達し得ない。2 個目を載せた後に監視する |
| 無認証 API で村を追い出される | 進行中のゲームが消える | Caddy でレート制限とボディサイズ上限をかける。認証の追加は範囲外 (後述) |
| デプロイ事故で Bot が停止 | 気付くまで無応答 | 直列化・チェックサム・原子的昇格・ヘルスチェックと自動復帰 (「デプロイ」参照) |

## 範囲外として記録する既存の問題

移行のレビューで見つかったが、本設計では扱わない。いずれも **Heroku 上に既に
存在し、移行が作り出すものではない**。別の課題として扱う。

| 問題 | 現状 | なぜ範囲外か |
| --- | --- | --- |
| `/callback` が LINE API の応答を同期的に待つ | `EchoApplication.java:174`, `:189` の `.replyMessage(...).get()`。`LineClientConstants` の connect / read / write 既定値はいずれも `10_000`ms で、2 秒制限を大きく超え得る | アプリの返信アーキテクチャの変更であり、非目的に挙げたリファクタリングそのもの。現に Heroku で動いており、東京へ移ると LINE API への往復は短くなる。ただし**設計上の時限爆弾なので別課題として残す** |
| `/callapi` と `/specialvillage` が無認証 | `@CrossOrigin` かつ署名検証なし。`userId` を任意に指定でき、LINE callback と同じレジストリを変更する | 認証の追加は外部フォームの改修を伴うプロダクト判断。プロキシで閉じられるレート制限とサイズ上限は本設計に含めた |
| 村レジストリの FIFO 追い出し | 上限 50 / 30 を超えると `remove(0)` で古い村が消える | 意図された挙動としてコードにコメントがある。上限の見直しは製品仕様の議論 |

## 作業分担

**ユーザーが実施する** (カード認証・電話認証・外部コンソール操作が必要):

1. Oracle Cloud アカウントの作成 (**ホームリージョン = ap-tokyo-1**)
2. A1 Flex インスタンスのプロビジョニングと SSH 公開鍵の登録
3. ドメインの取得と `bot.<domain>` の A レコード設定
4. GitHub Secrets への SSH 秘密鍵とホスト情報の登録
5. LINE Developers コンソールでの webhook URL 切替
6. Heroku の解約

**こちらが用意する**:

1. セットアップ手順書 (OS 初期設定、iptables、JDK 配置、ユーザー作成)
2. `Caddyfile`
3. `linebot.service` の systemd unit
4. `.github/workflows/ci.yml` への deploy job 追加
5. 検証用のスクリプト (**署名付き `POST /callback`** を組み立てて一連の流れを叩き、所要時間を計測する)

## 未確定のパラメータ

以下は実装開始時に確定させる。設計上の判断は済んでいる。

| パラメータ | 状態 |
| --- | --- |
| ドメイン名 (`<domain>`) | 取得待ち。TLD と登録先はユーザーが選ぶ |
| VM の公開 IP | プロビジョニング後に確定 |
| SSH 鍵ペア | プロビジョニング時に生成 |

## 完了条件

- LINE の webhook が `https://bot.<domain>/callback` を向き、実機から通常村・
  Werewords・特殊村が一通り操作できる
- 署名付き `/callback` の所要時間 (LINE API の同期待ちを含む) が 2 秒制限に対して
  十分なマージンを持つことを計測で確認済み
- `/callapi` と `/specialvillage` にレート制限とボディサイズ上限がかかっている
- `3.0` への push でビルドから再起動までが自動で通り、ヘルスチェック失敗時には
  直前の jar へ自動で戻る
- VM 再起動後にアプリが自動復帰する
- 1 週間の監視でアイドル回収の兆候がない
- Heroku の課金が $10/月 から $5/月 (BoardGame 分のみ) に下がっている
