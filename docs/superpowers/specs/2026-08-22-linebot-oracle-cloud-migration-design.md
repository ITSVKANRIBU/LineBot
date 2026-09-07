# LineBot を Oracle Cloud Always Free へ移行する設計

## 背景と目的

LineBot は Heroku の Eco プラン (**$5/月 = $60/年**) で、スリープさせずに稼働して
いる。Eco dyno は 30 分無通信でスリープするため、LINE の webhook を受け続けるには
プールを使い切らないよう 1 プロセスを常時起動させておく運用になっている。

**この $5/月をなくす。** LineBot を Oracle Cloud Always Free の ARM VM へ移す。

### 非目的

- ゲーム状態の永続化。状態はプロセス内メモリのままとする
- アプリケーションコードのリファクタリング
- 公開 API への認証の追加

## Oracle Cloud Always Free の妥当性評価

移行先の前提が崩れると、$5 を節約する代わりに動いている製品を失う。**「本当に
24 時間止まらないのか」「無料プランで妥当か」を先に決着させる。**

### 1. 24 時間止まり続けるか

**止まる仕組みは「アイドル回収」だけで、Heroku Eco のようなスリープは存在しない。**
Always Free の Compute は無通信で自動停止しない。

枠は月あたり **1,500 OCPU 時間 + 9,000 GB 時間**で、Oracle 自身がこれを
「Always Free テナンシでは 2 OCPU / 12 GB 相当」と説明している。24 時間 × 31 日で
使い切ると次のとおりで、**最長の月でも枠内に収まる**。

| 期間 | OCPU 時間 (2 OCPU) | GB 時間 (12 GB) |
| --- | --- | --- |
| 30 日 (720 時間) | 1,440 / 1,500 | 8,640 / 9,000 |
| 31 日 (744 時間) | **1,488 / 1,500** | **8,928 / 9,000** |

常時起動は成立する。ただし**余裕は 31 日の月で 1.6% しかない**。これは次の 2 つを
意味する。

- **2 台目の A1 インスタンスは立てられない。**枠はテナンシ全体の合計で、A1 を
  2 台並べた瞬間に超過する。本設計が 1 台構成なのは好みではなく、枠の制約である
- **シェイプの一時的な拡大もできない。**リサイズして数時間動かすだけで枠を超える

### 2. アイドル回収の実態

**回収は実在する。**ただし「予告なく削除される」ではない。

| 項目 | 確認結果 |
| --- | --- |
| 条件 | 7 日間で ① CPU (95 パーセンタイル) ② ネットワーク ③ メモリ (**A1 シェイプのみ**) の **3 つすべて**が閾値を下回ること |
| 閾値 | Oracle のドキュメントに **20% 版と 10% 版の記述が混在**している。本設計は厳しい側の **20%** を基準にする |
| 手順 | 7 日アイドルでまず**メール通知**が届き、その **1 週間後に停止**される |
| 停止後 | **削除ではなく停止**。シェイプの在庫があれば再起動できる |
| 公式の回避手段 | **Pay As You Go (PAYG) へアップグレードすると回収の対象外**になる。PAYG でも Always Free 枠内の利用は課金されない |

3 条件が AND であることが設計の余地になる。この Bot の CPU とネットワークは
どうやっても 20% に届かないため、**外せるのはメモリ条件だけ**である (対策は後述)。

「通知 → 1 週間後に停止 → 再起動可能」という手順は、回収を**気付いてから対応できる
事象**に変える。設計書の想定を「即時削除」から「猶予付きの停止」へ改める。

### 3. 無料枠そのものが縮小され得る

**これが最大のリスクで、しかも実例がある。**

2026 年 6 月 15 日付で、Always Free の Ampere A1 枠が **4 OCPU / 24 GB から
2 OCPU / 12 GB へ半減**した (3,000 OCPU 時間 + 18,000 GB 時間 → 1,500 + 9,000)。
メールもコンソール通知もなく、ドキュメントが差し替わっただけだった。**8 月 18 日
から強制適用され、新しい枠を超えていたインスタンスは停止・終了された。**

本設計の 2 OCPU / 12 GB はこの改定後の枠である。したがって:

- **Oracle が守るのは「Always Free という枠の継続」であり、「枠の大きさ」でも
  「この VM 個体の可用性」でもない。**3 つを区別する
- 次の半減 (1 OCPU / 6 GB) があれば `-Xms3g` は載らず、本設計のメモリ対策は崩れる。
  そのときは PAYG へ上げるか、Heroku を含む別の移行先を検討し直す判断になる

### 4. 総合判断

**移行して妥当。**ただし無料枠を「契約」ではなく「予告なく変わる好意」として扱う。

- 常時起動は枠の計算上成立する (§1)
- 止まる要因はアイドル回収だけで、猶予付きの通知があり、公式の回避手段もある (§2)
- 枠は縮小され得るが、そのときも Heroku へ戻す道は残る (§3、ロールバック節)

$5/月に対して、この不確実性を引き受ける。**代わりに「VM は失われ得るもの」として
扱い、再作成手順と秘密情報の復元手段を設計に含める。**

### PAYG へ上げるかどうか

**初期構成は Always Free のままとし、PAYG は回収通知が届いたときのエスカレーション
先として設計に置く。** 順序を逆にしない。

| | Always Free | PAYG |
| --- | --- | --- |
| アイドル回収 | 対象。メモリ対策で外す (保証なし) | **対象外 (公式)** |
| カードへの実請求 | **起こらない**。カードは本人確認用 | **枠を超えれば起こる** |

$5/月をなくすことが目的である。最初から上限のない課金関係を結ぶのは目的を反転
させる。メモリ対策 (systemd unit の 1 行) で足りるなら、それで足りる。

**PAYG へ上げる条件**: アイドル判定のメール通知が届いた、または実際に停止された
場合。上げるときは同時に OCI Budget で $1 超過のメール通知を設定する。

## 前提の検証結果

移行の成否を左右する事実は、以下のとおり確認済み。

| 項目 | 確認結果 | 出典 |
| --- | --- | --- |
| ARM (aarch64) で Java 8 が動くか | Temurin JDK 8 は aarch64 を正式サポート。TCK 検証済みで、セキュリティパッチは少なくとも 2029 年 3 月まで | Adoptium |
| 無料枠の永続性 | 「トライアル終了後もアカウントは有効なまま。Always Free リソースの可用性に中断はない」「サービスを使い続ける限り無期限」。後半の条件節がアイドル回収に対応する | Oracle 公式 docs |
| 意図しない課金 | 「Free Tier / Free Trial アカウントを有料アカウントへアップグレードしない限り課金されない」。カードは本人確認用 | Oracle 公式 docs |
| Always Free の枠 | 1,500 OCPU 時間 + 9,000 GB 時間/月 = 2 OCPU / 12 GB を常時起動。ブロックストレージ 200 GB、ブートボリューム最小 47 GB | Oracle 公式 docs |
| 枠の縮小実例 | 2026-06-15 付で ARM 枠が 4 OCPU / 24 GB → 2 OCPU / 12 GB へ半減。告知はドキュメント更新のみ。2026-08-18 から強制適用 | Oracle docs の改定とその報道 |
| アイドル回収の条件 | 7 日間で CPU (95 パーセンタイル) / ネットワーク / メモリ (A1 のみ) の 3 つすべてが閾値未満。閾値の表記は 20% と 10% が混在 | Oracle 公式 docs |
| 回収の手順 | メール通知 → 1 週間後に停止。削除ではなく、在庫があれば再起動できる | Oracle 公式 docs |
| 回収の公式な回避 | PAYG へ変換すると対象外。PAYG でも Always Free 枠内は課金されない | Oracle 公式 docs |
| ホームリージョン | サインアップ時に決定し、**後から変更不可**。Always Free のコンピュートはホームリージョンにしか作れない | Oracle 公式 docs |
| 下り通信量 | 月 10 TB の下り通信を Always Free の一部として利用できる | Oracle 公式 docs |
| Caddy のレート制限 | 標準配布物には含まれない。`http.ratelimit` は非標準モジュールで、xcaddy または `caddy add-package` で追加する | Caddy 公式 docs |
| GitHub Actions `concurrency` の順序 | 「待ち始めた時刻順の FIFO であり、実際の開始時刻は変動するため順序は保証されない」 | GitHub 公式 docs |
| 外部フォームの接続先 | 公開フォーム (insidergametool.netlify.app) が読み込む設定 JavaScript は API 接続先を `https://insidergamehelper.herokuapp.com` に固定している | 配信中の `assets/config-*.js` を確認 |
| 東京での A1 確保 | `Out of host capacity` は ap-tokyo-1 でも報告がある。一方で APAC は US より確保しやすいとの報告が多い | 二次情報 |
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
  └─ bot.<domain>  ──▶ 127.0.0.1:8081  linebot.service
                        Spring Boot 2.1.5 / Temurin 8 aarch64

Bot ──HTTPS 443──▶ api.line.me            (返信 API。送りっぱなし)
Bot ──HTTPS 443──▶ script.google.com      (役職イラストのカタログ。5 分ごと)
```

### VM

| 項目 | 値 | 理由 |
| --- | --- | --- |
| シェイプ | VM.Standard.A1.Flex | Always Free で常時起動でき、12 GB を使える唯一のシェイプ |
| リージョン | **ap-tokyo-1** | 不可逆な選択。LINE プラットフォームと利用者が国内にあり、往復を短く保つため |
| OCPU / メモリ | 2 OCPU / 12 GB | Always Free の枠をそのまま使う。余らせても繰り越せない。§1 のとおりこれで枠を使い切る |
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
システム既定を切り替える必要はない。

JDK の更新は四半期ごとの Temurin リリースに合わせて tarball を差し替える。手順は
セットアップ手順書に含める。

### プロセス管理

`linebot.service` を systemd unit として定義する。

- `Restart=always` / `RestartSec=5`
- 専用の非 root ユーザー `linebot` で実行
- `EnvironmentFile=/etc/linebot.env`
- ヒープ: **`-Xms3g -Xmx3g -XX:+AlwaysPreTouch -XX:+UseG1GC -XX:MaxGCPauseMillis=200`**
- アプリ引数: `--server.address=127.0.0.1 --server.port=8081`。Tomcat を loopback にのみ bind し、Caddy を経由しない直接アクセスを OS レベルで不可能にする。iptables の設定に依存しない
- ログは journald に任せ、`/etc/systemd/journald.conf.d/` で `SystemMaxUse=200M` を上限にする。アプリ側のファイルログは持たない

ヒープ値の根拠は次節のとおり。

### アイドル回収への対策

外せるのはメモリ条件だけである (§2)。12 GB の 20% は 2.4 GB なので、**`-Xms3g`
で 3 GB を確保して条件から外す** (ヒープだけで約 25%。metaspace とスレッドスタックを
含めた RSS では 27〜28% になる)。

- **`-XX:+AlwaysPreTouch` を必ず併記する。** `-Xms` だけでは JVM が仮想アドレス
  空間を予約するだけで RSS が遅延して伸びるため、OS から見た使用量が上がらず、
  対策として機能しない
- **`-XX:+UseG1GC -XX:MaxGCPauseMillis=200` を明示する。** 3 GB のヒープに既定の
  Parallel GC を使うと、フル GC のポーズが応答時間を脅かし得る。実際にはライブ
  データが数十 MB しかないためフル GC はまず起きないが、無料枠の都合で不自然に
  大きいヒープを与えるという判断の副作用を、応答時間に持ち込まないための保険である
- **CPU を焚く cron や、ネットワークを流すためだけの外部監視は置かない。** CPU 条件を
  外すには 2 コアの 20% を 95 パーセンタイルで焚き続ける必要があり無駄が大きい。
  ネットワーク条件も、数回の ping では回線容量の 20% に到底届かない

#### この対策が機能する前提

**Oracle が判定に使うのは Oracle 側から見えるメモリ指標である。** VM 上で RSS が
3 GB あっても、それが Oracle に報告されていなければ対策は何も効かない。したがって:

- **`MemoryUtilization` が OCI Monitoring に現れることを、切替前に確認する。**
  A1 の Ubuntu イメージでは Compute Instance Monitoring プラグインが既定で有効だが、
  有効であることを前提にせず実際に指標を見る。**指標が出ていなければ、この対策は
  存在しないものとして扱い、PAYG へのアップグレードを検討する**
- 切替後 1 週間は `MemoryUtilization` が 20% を安定して上回っていることを毎日確認する
- 20% を割る、または**指標が欠測する** (エージェント停止や VM 停止) 場合は OCI Alarm で
  メール通知する。Monitoring と Notifications は Always Free の範囲内

#### 対策の限界

**これは設計上の成立であって保証ではない。** `AlwaysPreTouch` で RSS が上がることも、
1 週間回収されなかったことも、将来 Oracle が回収しないことを約束しない。

- **回収通知のメールが届いた場合**: 対策が効いていないということなので、猶予の
  1 週間のうちに PAYG へ上げる (§PAYG へ上げるかどうか)
- **外形監視**: 無料の外部 uptime 監視から `GET https://bot.<domain>/actuator/health` を
  5 分間隔で叩き、失敗時に通知する。エンドポイントの契約は「デプロイ」節で定める
- **再作成手順**: VM が停止・削除された場合は、セットアップ手順書に従って同じ構成を
  作り直す。手順書はこの再作成を前提に、コピー&ペーストで完了する粒度で書く。
  `/etc/linebot.env` の内容 (LINE のチャネルトークンとシークレット) は利用者の
  パスワードマネージャに保管し、そこから復元する。トークンは LINE Developers
  コンソールで再発行もできる
- 復旧後は「カットオーバー」節の切替後確認をやり直す

### ネットワーク

- OCI の security list で ingress を 22 / 80 / 443 に限定する
- **egress は 443 を開けたままにする。**アプリは 2 か所へ外向きの HTTPS を出す。
  - `api.line.me` — 返信 API
  - `script.google.com` — 役職イラストのカタログを **5 分ごと**に取得する
    ([IllustrationCatalogJob.java:37-39](../../../insider-game-bot/src/main/java/insidergame/adapter/IllustrationCatalogJob.java))

  この定期取得の通信量はごく小さく、アイドル回収のネットワーク条件 (20%) には
  まったく届かない。回収対策として数えない
- **OCI の Ubuntu イメージは iptables がデフォルトで塞いでいる**ため、OS 側でも
  80 / 443 を明示的に開ける。ここを忘れると security list を開けても疎通しない
- SSH は公開鍵のみ。パスワード認証と root ログインを無効化する

### 公開とTLS

独自ドメインを取得し (Cloudflare Registrar の .com が $10.44/年)、`bot.<domain>` の
A レコードを VM の公開 IP に向ける。Caddy が Let's Encrypt 証明書を取得し、自動更新する。

DuckDNS などの無料サブドメインは採用しない。webhook URL は LINE プラットフォーム
側に登録する外部契約であり、提供元が停止したときに LINE コンソールと外部フォームの
接続先設定を触り直すことになる。年 $10.44 で URL を自己所有する方が長期的に安定する。

**コスト**: Heroku $60/年 → ドメイン $10.44/年。**削減額 $49.56/年 (約 83%)**。
$0 にはならない。TLD と登録先は利用者が選ぶ。

### 公開ルートの方針

Caddy を「全パスをそのまま Spring Boot へ流す」設定にはしない。このアプリは
**署名検証のない状態変更 API を公開している**ためである。

- `/callapi` ([MainController.java:63-64](../../../insider-game-bot/src/main/java/insidergame/adapter/MainController.java)) は
  `@CrossOrigin` かつ無認証で、`userId` を呼び出し側が自由に指定できる。署名付き
  の LINE callback と**同じレジストリを変更する**
- `/specialvillage` ([SpecialVillageController.java:68-69](../../../insider-game-bot/src/main/java/insidergame/adapter/SpecialVillageController.java)) も
  `@CrossOrigin` かつ無認証
- `VillageRegistry` は上限 50 件 ([VillageRegistry.java:39](../../../insider-game-bot/src/main/java/insidergame/game/VillageRegistry.java))、
  `SpecialVillageRegistry` は上限 30 件 ([SpecialVillageRegistry.java:36](../../../insider-game-bot/src/main/java/insidergame/game/SpecialVillageRegistry.java)) で、
  超過すると `villageList.remove(0)` で**古い村からFIFOで消える**。したがって無認証の
  リクエストを 50 回 / 30 回送るだけで、進行中の村を全部追い出せる

これは Heroku 上に既に存在する問題で、移行が作り出すものではない。ただしリバース
プロキシの設定を今書くので、プロキシ側で閉じられる範囲は本設計に含める。

- Caddy で `/callapi` と `/specialvillage` に**レート制限**をかける
- リクエストボディのサイズ上限を設定する (`/specialvillage` は 1 村 100 メッセージ
  × 各 5000 文字が仕様上の最大)
- `/callback` は LINE の署名検証があるため、レート制限はかけない
- Caddy が Spring Boot へ流すパスは `/callback` `/callapi` `/specialvillage` `/actuator/health` の 4 つに限定する。それ以外は Caddy が 404 を返す

#### レート制限の実現方法

レート制限は Caddy の標準機能ではない。`github.com/mholt/caddy-ratelimit` モジュールを使う。

| 項目 | 決定 |
| --- | --- |
| 導入 | Caddy 公式 apt リポジトリから `caddy` を入れた後、`caddy add-package github.com/mholt/caddy-ratelimit` でモジュールを組み込んだバイナリに差し替える |
| 更新 | apt がモジュールなしのバイナリで上書きしないよう `apt-mark hold caddy` する。Caddy の更新は `caddy upgrade` で行う (組み込み済みモジュールを維持したまま最新版へ入れ替わる)。頻度は JDK と同じ四半期ごと |
| 制限単位 | クライアント IP (`{http.request.remote.host}`) ごと。Caddy は VM 上で TLS を終端しているので、`X-Forwarded-For` ではなく接続元 IP をそのまま使える |
| `/callapi` | 1 IP あたり 30 リクエスト / 分 |
| `/specialvillage` | 1 IP あたり 10 リクエスト / 分。ボディ上限 1 MB |
| 超過時 | HTTP 429 |

閾値は「フォームを普通に操作する 1 人が届かない値」として置いた。数値は実装計画で
公開フォームの実操作を計測して確定する。

**CORS プリフライト**: ブラウザ上のフォームは本リクエストの前に `OPTIONS` を送り、
これもレート制限に数えられる。1 操作が 2 リクエスト消費する前提で閾値を決め、検証では
実際のブラウザからフォームを操作して `OPTIONS` → 本リクエストの両方が 429 なしで
通ることを確認する。

**残存リスク**: レート制限は無認証の状態変更を**防がない**。上限 50 / 30 の FIFO
追い出しを、1 IP からは数分かかるように遅らせるだけであり、複数 IP から送れば遅延
すらしない。進行中の村を第三者が消せる状態は移行後も残る。解消には認証の追加が必要で、
それはアプリ側の変更かつプロダクト判断 (外部フォームの改修を伴う) なので、本設計の
対象外とする。「範囲外として記録する既存の問題」に残す。

### シークレット

Heroku の環境変数を `/etc/linebot.env` (`root:root`, `0600`) へ移し、systemd の
`EnvironmentFile=` で読む。

| 環境変数 | 移行後の扱い |
| --- | --- |
| `LINE_BOT_CHANNEL_TOKEN` | `/etc/linebot.env` へ移す |
| `LINE_BOT_CHANNEL_SECRET` | `/etc/linebot.env` へ移す |
| `LOGGING_LEVEL_INSIDERGAME` | 既定 (INFO) のままなら設定しない。引き上げるときに `/etc/linebot.env` へ書く |
| `PORT` | **使わない。** Heroku 固有の仕組みなので、`--server.port=8081` を固定で渡す |

GitHub Secrets には SSH 秘密鍵・接続先ホスト・**VM の SSH ホスト公開鍵** のみを置き、
LINE の資格情報は置かない。deploy job はホスト公開鍵を `known_hosts` に書き出し、
`StrictHostKeyChecking=yes` で接続する。ホスト鍵を固定しないと、DNS や経路を
乗っ取られた場合に jar を偽の VM へ送り込まれ、秘密鍵を使った認証が意味を失う。

### デプロイ

既存の `.github/workflows/ci.yml` に deploy job を追加する。

#### push で自動反映する

- トリガー: `3.0` への push (本番ブランチ)。deploy job は `needs: build` で、同じ workflow 内の `build` job が通った成果物だけを配備する。`develop` と PR では deploy job を走らせない
- ビルド JDK: 現行どおり Temurin 8。本番の JVM と揃える意図を維持する

`systemctl restart` のたびに進行中の村は全部消える。これは**利用者が少ないため
受け入れる**。push のたびに利用状況を確認して手動で反映する運用は、その手間に
見合う利益がない。本番反映は push だけで完了することを優先し、手動の承認ステップや
`workflow_dispatch` は設けない。永続化は非目的であり、この判断を変えるならそちらを
見直す。

#### 事故の防止

`scp` して `systemctl restart` するだけでは、次の事故で「CI は緑なのに Bot は
死んでいる」状態を作れる。いずれも安価に塞げるので本設計に含める。

| 事故 | 対策 |
| --- | --- |
| 連続 push で workflow が並走し、**古い commit が最後に着地する** | `concurrency` group で直列化する (`cancel-in-progress: false`)。ただし GitHub は group 内の実行順序を保証しないため、これだけでは不足。排他を取った**後に** `git ls-remote origin refs/heads/3.0` で `3.0` の最新 SHA を取り、自分がビルドした SHA と一致しなければ何もせず成功終了 (skip) する |
| 転送が中断して**壊れた jar が本番の位置に残る** | バージョン付きの一時パスへ転送 → `sha256sum -c` で検証 → 検証済みのものだけを `mv` で原子的に昇格 |
| `systemctl restart` はアプリの起動完了前に返るため、**起動失敗を検知できない** | 再起動後にヘルスチェックを成功までポーリングし、失敗したら job を落とす。直前の jar を保持し、ヘルスチェックが通らなければ自動で戻して再起動する |
| VM 側の更新処理が**途中で打ち切られる** | 転送後の「検証 → 昇格 → 再起動 → ヘルスチェック → 復旧」は VM 上の 1 本のスクリプトにまとめ、`flock` で排他する。GitHub 側は実行中の job を cancel しない (`cancel-in-progress: false`)。runner が消えてもスクリプトは VM 上で完走する |

#### ヘルスチェックの契約

現行コードに health endpoint はなく、Actuator の依存もない。
`spring-boot-starter-actuator` を `insider-game-bot/build.gradle` の依存に追加し、
`GET /actuator/health` を使う。自前で endpoint を書かない (Spring Boot 2.x は
`health` を既定で HTTP 公開する)。

| 項目 | 契約 |
| --- | --- |
| URL | `http://127.0.0.1:8081/actuator/health` (VM 内から。Caddy 経由でも `https://bot.<domain>/actuator/health` で外形監視から到達できる) |
| 期待 | HTTP 200 かつボディが `{"status":"UP"}` |
| 副作用 | なし。村レジストリには一切触れない |
| ポーリング | `systemctl restart` 後、2 秒間隔で最長 60 秒。Spring Boot 2.1.5 の起動は数秒〜十数秒なので余裕を持たせた値 |
| 失敗時 | `previous` を `current` に戻して再起動し、**同じ契約で再確認**する。戻して通れば job を失敗させて終了 (本番は旧版で動いている)。戻しても通らなければ `systemctl stop linebot` して job を失敗させる (壊れた状態で `Restart=always` が空回りするのを止める) |
| `previous` が無い初回配備 | 戻し先がないので、ヘルスチェック失敗時は `systemctl stop linebot` して job を失敗させる。初回は切替前で LINE からの流入がないため、停止していて構わない |

ヘルスチェックが確認するのは**プロセスが起動して HTTP を受け付けること**だけである。
webhook ハンドラは処理中の例外をログに記録して吸収し、常に 200 を返すため、
**HTTP 200 は返信成功を意味しない**。返信内容の確認は「検証方法」節の役割であり、
ヘルスチェックとは分ける。

#### 手順

1. (`build` job) `./gradlew check` → `./gradlew :insider-game-bot:bootJar` → jar と sha256 を artifact に保存
2. (deploy job、`3.0` への push 時のみ) `concurrency` group を取得
3. `git ls-remote` で `3.0` の最新 SHA を確認。ビルドした SHA と異なれば skip
4. jar と sha256 を `/opt/linebot/releases/<commit-sha>/` へ転送 (ホスト鍵を固定した SSH)
5. VM 上の更新スクリプトを `flock` 付きで実行:
   1. `sha256sum -c` を検証
   2. 現行の `current` を `previous` として退避し、新 jar を `current` へ `mv` で昇格
   3. `systemctl restart linebot`
   4. ヘルスチェックの契約どおりポーリング。失敗時は上表のとおり復旧
   5. `/opt/linebot/releases/` は直近 5 世代だけ残し、古いものを削除する
6. スクリプトの終了コードで job の成否を決める

Heroku の GitHub 連携は移行完了を確認するまで生かしたままにする。したがって
`Procfile` などの Heroku 固有ファイルは、この段階では削除しない (「撤去するもの」節)。

## 検証方法

### 経路ごとに何を確かめられるか

リファクタリングによって入力の解釈が `TextCommandHandler`
([TextCommandHandler.java:70](../../../insider-game-bot/src/main/java/insidergame/game/TextCommandHandler.java))
へ集約され、`/callback` と `/callapi` はどちらもここを通る。`RouteParityTest` が
両経路の応答一致を固定している。**したがって `/callapi` はゲーム規則の検証に使える。**
経路差は次の 3 点だけである。

| 確かめたいこと | `/callapi` | 署名付き `/callback` |
| --- | --- | --- |
| 村の作成・人数・お題・参加・配布 | ○ | ○ |
| `@取得` / `@配布` / `@特殊` / `@逆村` / `@わーわーず` | ○ | ○ |
| 署名検証 (不正署名の拒否) | × | ○ |
| ポストバックとスタンプ | × (入口がない) | ○ |
| 返信 API へ実際に送っていること | × (LINE API を呼ばない) | ○ |
| 対象の村がないときの応答 | テキスト `村が作成されていません` | 確認テンプレート |

### 2 秒制限について

**`LineEventHandler.reply` は返信 API の完了を待たない**
([LineEventHandler.java:178-186](../../../insider-game-bot/src/main/java/insidergame/adapter/LineEventHandler.java))。
`whenComplete` でログを残すだけなので、**LINE API の所要時間は webhook の応答時間に
入らない**。webhook の応答時間はレジストリ操作とメッセージ組み立てだけで決まる。

これは 2 秒制限に対する見通しを大きく変える。かつては返信 API の 10 秒既定タイムアウト
([LineClientConstants](../../../line-bot-api-client/src/main/java/com/linecorp/bot/client/LineClientConstants.java))
が webhook の応答時間に直結していたが、その経路は存在しない。**それでも計測はする**
— Caddy の 1 ホップと ARM の JVM という新しい条件が入るためで、根拠が「同期待ちが
あるから」から「環境が変わるから」に変わっただけである。

### 返信内容をどう見るか

**LINE API のエンドポイントをスタブへ差し替える。** SDK は `line.bot.api-end-point`
で接続先を変更できるので、検証用チャンネルを新設する必要はない。スタブが受け取った
`ReplyMessage` の本文を読めば、返信内容をそのまま確認できる。

実際の資格情報とネットワーク経路は、切替後に実機の LINE から操作して確かめる
(「カットオーバー」節)。**スタブは切替前の機能・時間の検証、実機は切替後の疎通確認**
という分担にする。

### 切替前に確認する項目

1. `/callapi` を Caddy 経由で送り、通常村の作成 → 人数設定 → お題設定 → 参加 →
   役職配布と、`@` で始まる各コマンドが期待どおりのメッセージを返す
2. 署名付き `POST /callback` を Caddy 経由で送り、スタブに届いた `ReplyMessage` が
   1 と同じ内容であることを確認する
3. ポストバック (`@取得` の応答のボタン) とスタンプを `/callback` で確認する
4. 不正署名の `POST /callback` を拒否する
5. **`/callback` の応答完了までの所要時間**を計測し、2 秒制限に対するマージンを確認する
6. `POST /specialvillage` が 5 桁の村番号を返す
7. `/callapi` と `/specialvillage` のレート制限が意図どおり効く。あわせて、実際の
   ブラウザから接続先を OCI に向けたフォームを操作し、CORS プリフライト (`OPTIONS`) と
   本リクエストが 429 なしで通る
8. `GET /actuator/health` が `{"status":"UP"}` を返し、上記 4 パス以外は Caddy が 404 を返す
9. **OCI Monitoring に `MemoryUtilization` が現れ、20% を上回っている**
10. 役職イラストのカタログを取得できている (起動から 5 分以内に INFO ログ。egress の確認)
11. `systemctl restart` 後に自動復帰する
12. VM 再起動後に systemd が自動起動する
13. デプロイのヘルスチェック失敗時に、自動で直前の jar へ戻る
14. 古い commit の workflow run を GitHub 上で re-run したとき、deploy job が skip する

## カットオーバーとロールバック

進行中の村はメモリ上にしか存在せず、切替の瞬間に消える。**プレイヤーがいない
時間帯に実施する**。これは受け入れる副作用であり、回避しない (状態の永続化は
非目的)。

### 切替対象は 2 つある

Bot への入口は LINE の webhook だけではない。公開フォーム (insidergametool.netlify.app)
は API 接続先を `https://insidergamehelper.herokuapp.com` に固定しており、
`GET /callapi` と `POST /specialvillage` をそこへ送る。webhook だけを OCI に向けると、
**フォームで作った特殊村は Heroku 側のメモリに保存され、OCI 側の Bot からは
「村が作成されていません」になる**。特殊村の機能が丸ごと壊れる。

したがって切替は **LINE の webhook URL と、フォームの API 接続先の 2 か所を同じ
タイミングで**行う。切り戻しも同様に 2 か所を戻す。フォームの接続先は別リポジトリの
設定値であり、変更とデプロイは利用者が行う。

### 手順

1. VM をプロビジョニングし、Caddy / JDK / systemd を構成する
2. 上記の検証項目をすべて通す
3. プレイヤー不在を確認する
4. フォームの API 接続先を `https://bot.<domain>` に変えてデプロイする
5. LINE Developers コンソールの webhook URL を `https://bot.<domain>/callback` へ切り替える
6. 実機の LINE から通常村・Werewords を一通り操作して確認する。加えて**公開フォームで
   特殊村を作成 → LINE から参加 → `@配布`** が通ることを確認する。これがフォーム側の
   切替を検証する唯一の経路
7. 1 か月、応答時間と OCI のメモリ指標、外形監視を見る。**アイドル判定のメールが
   届かないことも確認対象に含める**
8. 問題がなければ Heroku を解約する
9. Heroku 固有ファイルを撤去する (「撤去するもの」節)

### ロールバック

Heroku アプリは移行後 **1 か月**維持してロールバック先として確保する。解約時期と
保持期間はこの 1 か月で統一する。

**ロールバックは操作が速いだけで、状態としては破壊的**である。OCI と Heroku は
独立したメモリ上のレジストリを持つため、

- 切替後に OCI 上で作られた村は、Heroku へ戻した瞬間に**すべて消える**
- Heroku 側には切替前の古い村が残っている可能性があり、番号の混乱を招く

初回の切替をプレイヤー不在の時間帯に行っても、それはその 1 回を守るだけで、1 か月の
保持期間中に起こるロールバックは守らない。手順は次のとおりとする。順序が重要で、
Heroku の再起動を**先に**行う。戻した後に再起動すると、戻した直後に Heroku 上で
作られた新しい村まで消してしまう。

1. 破壊的操作として扱い、実行前にプレイヤーがいないことを確認する
2. やむを得ず進行中に戻す場合は、村が失われることを利用者へ告知する
3. Heroku アプリを再起動し、切替前の古い村を破棄する
4. Heroku 側の起動を確認する (`GET /callapi` に適当な `message` と `userId` を渡して JSON が返る)
5. フォームの API 接続先を `https://insidergamehelper.herokuapp.com` に戻してデプロイする
6. LINE Developers コンソールの webhook URL を Heroku のものへ戻す

状態を保ったままの切り戻しは、レジストリを共有ストレージへ出さない限り成立しない。
それは本設計の非目的であり、ここでは**ロールバックは破壊的**と明記して受け入れる。

## 撤去するもの

**後方互換のために残さない。** Heroku 固有の構成は、解約を確認した後に削除する。
削除は解約後であることが重要で、それまでは Heroku がロールバック先である。

| 対象 | 内容 |
| --- | --- |
| `Procfile` | Heroku の dyno 定義。systemd unit が置き換える |
| `app.json` | Heroku のアプリ定義 |
| `system.properties` | Heroku へ JDK を指示するファイル (`java.runtime.version=1.8`) |
| `.github/workflows/ci.yml` の `Build Heroku artifact` | ステップ名から Heroku を外す |
| `docs/operations.md` | 稼働環境・`PORT`・デプロイの節を書き換える |
| `docs/roadmap.md` | 移行の節を「実施済み」として現状の文書へ移す |

## リスクと対策

| リスク | 影響 | 対策 |
| --- | --- | --- |
| ホームリージョンの誤選択 | 不可逆。日本から 100ms 超が乗る | サインアップ時に ap-tokyo-1 を選ぶ。**作業の最初に確定させる** |
| A1 の `Out of host capacity` | VM が作れない | ap-tokyo-1 でリトライする。AD 変更・大阪・E2.1.Micro はいずれも代替にならない。取得できるまで Heroku のまま運用する |
| アイドル回収 | 通知 → 1 週間後に停止 (削除ではない) | `-Xms3g -XX:+AlwaysPreTouch` でメモリ条件を外す。**`MemoryUtilization` が Oracle 側に見えていることを切替前に確認する。**保証ではないため、指標と欠測を Alarm で監視し、外形監視で停止を検知し、通知が届いたら PAYG へ上げる |
| **Always Free の枠が縮小される** | 構成が枠を超え、停止・終了され得る | 2026-06 に実例がある。予告はドキュメント更新のみと想定する。1 OCPU / 6 GB へ半減された場合は `-Xms3g` が載らないため、PAYG か別の移行先を検討し直す。**Heroku へ戻す道を残しておく** |
| フォームの接続先を切り替え忘れる | 特殊村がフォームからも LINE からも成立しない | webhook と接続先を 2 か所セットで切り替え、フォーム → LINE の経路を切替後確認に含める |
| 自動デプロイで進行中の村が消える | push のたびにゲームが中断する | 利用者が少ないため受け入れる。手動反映の手間より自動化を優先する |
| 2 秒制限に間に合わない | webhook タイムアウト | **返信 API の完了は待っていない**ため、応答時間はレジストリ操作とメッセージ組み立てだけで決まる。それでも Caddy のホップと ARM の JVM という新条件があるので、切替前に計測する |
| 自前運用の負荷 | OS / TLS / プロセスの管理 | Caddy で TLS を全自動化、systemd で自動再起動、unattended-upgrades で OS 更新を自動化する |
| 下り 10 TB/月の超過 | 課金 (PAYG の場合) | LINE Bot はテキスト応答のみで到達し得ない |
| 無認証 API で村を追い出される | 進行中のゲームが消える | Caddy でレート制限とボディサイズ上限をかけ、1 IP からの追い出しを遅らせる。**防止はできない**。認証の追加は範囲外 |
| デプロイ事故で Bot が停止 | 気付くまで無応答 | 直列化と SHA 再確認・チェックサム・原子的昇格・VM 側の `flock`・ヘルスチェックと自動復帰 |

## 範囲外として記録する既存の問題

移行のレビューで見つかったが、本設計では扱わない。いずれも **Heroku 上に既に
存在し、移行が作り出すものではない**。別の課題として扱う。

| 問題 | 現状 | なぜ範囲外か |
| --- | --- | --- |
| `/callapi` と `/specialvillage` が無認証 | `@CrossOrigin` かつ署名検証なし。`userId` を任意に指定でき、LINE callback と同じレジストリを変更する | 認証の追加は外部フォームの改修を伴うプロダクト判断。プロキシで閉じられるレート制限とサイズ上限は本設計に含めた |
| 村レジストリの FIFO 追い出し | 上限 50 / 30 を超えると `remove(0)` で古い村が消える | 意図された挙動としてコードにコメントがある。上限の見直しは製品仕様の議論 |
| 返信の失敗が利用者に伝わらない | `reply` は完了を待たず、失敗をログにだけ残す | 2 秒制限を守るための設計判断 ([architecture.md](../../architecture.md)「返信の完了を待たない」)。移行で変わらない |
| Spring Boot 2.1 系の保守終了 | Spring Boot 2.1.x は 2020 年 11 月に OSS サポートが終了している。OS と JDK を更新しても、フレームワークとその推移的依存 (Tomcat、Jackson 等) には修正が届かない | 依存更新はアプリの変更であり、非目的に挙げたリファクタリングに当たる。移行で状況が変わるわけでもない。「リスクと対策」の自前運用の項が扱うのは OS・TLS・プロセスの保守だけで、**フレームワークの保守終了はそれでは解消しない**ため、別課題としてここに明記する |

## 作業分担

**ユーザーが実施する** (カード認証・電話認証・外部コンソール操作が必要):

1. Oracle Cloud アカウントの作成 (**ホームリージョン = ap-tokyo-1**)
2. A1 Flex インスタンスのプロビジョニングと SSH 公開鍵の登録
3. ドメインの取得と `bot.<domain>` の A レコード設定
4. GitHub Secrets への SSH 秘密鍵・接続先ホスト・SSH ホスト公開鍵の登録
5. `/etc/linebot.env` の内容をパスワードマネージャへ保管
6. OCI Alarm の通知先メール設定と、外形監視サービスの登録
7. 公開フォーム (別リポジトリ) の API 接続先変更とデプロイ
8. LINE Developers コンソールでの webhook URL 切替
9. Heroku の解約
10. (回収通知が届いた場合) PAYG へのアップグレードと OCI Budget の設定

**こちらが用意する**:

1. セットアップ手順書 (OS 初期設定、iptables、JDK 配置、Caddy とレート制限モジュールの導入、ユーザー作成、journald の上限、Monitoring プラグインの確認。VM 再作成にそのまま使える粒度)
2. `Caddyfile`
3. `linebot.service` の systemd unit
4. VM 上の更新スクリプト (検証・昇格・再起動・ヘルスチェック・復旧・世代管理を `flock` 付きで行う)
5. `.github/workflows/ci.yml` への artifact 保存と deploy job 追加
6. `spring-boot-starter-actuator` の依存追加
7. 検証用のスクリプト (署名付き `POST /callback` を組み立て、`line.bot.api-end-point` を向けた LINE API スタブで返信内容を確認し、所要時間を計測する)
8. 切替完了後の Heroku 固有ファイルの撤去と、`docs/operations.md` / `docs/roadmap.md` の更新

## 未確定のパラメータ

以下は実装開始時に確定させる。設計上の判断は済んでいる。

| パラメータ | 状態 |
| --- | --- |
| ドメイン名 (`<domain>`) | 取得待ち。TLD と登録先はユーザーが選ぶ |
| VM の公開 IP | プロビジョニング後に確定 |
| SSH 鍵ペア | プロビジョニング時に生成 |
| レート制限の閾値 | 暫定値 30 / 10 req/分/IP。公開フォームの実操作 (プリフライト込み) を計測して確定 |
| 外形監視サービス | 無料枠で 5 分間隔の HTTPS 監視とメール通知ができるものを利用者が選ぶ |
| フォーム側の設定変更箇所 | 別リポジトリの API 接続先の定義。ビルド時定数か環境変数かは当該リポジトリで確認する |

## 完了条件

設計上の成立と、実機で測って初めて分かることを分ける。前者はこの文書で決着している。
後者は本設計の時点では**未検証**であり、実装計画の中で計測して初めて満たされる。

**実測で確認する (現時点では未検証)**:

- ap-tokyo-1 で A1 Flex が確保でき、Temurin 8 aarch64 上でアプリが起動する
- 署名付き `/callback` の所要時間が 2 秒制限に対して十分なマージンを持つ
- 実際のブラウザからフォームを操作したとき、CORS プリフライトを含めてレート制限に引っかからない
- **OCI Monitoring に `MemoryUtilization` が現れ、20% を安定して上回る**
- 1 か月の監視でアイドル判定のメール・停止・応答遅延の兆候がない

**構成として満たす**:

- LINE の webhook が `https://bot.<domain>/callback` を向き、**フォームの API 接続先が `https://bot.<domain>` を向いている**
- 実機から通常村・Werewords が操作でき、**公開フォームで作った特殊村に LINE から参加して配布できる**
- `/callapi` と `/specialvillage` にレート制限とボディサイズ上限がかかり、Caddy が 4 パス以外を 404 にする
- `3.0` への push でビルドから本番反映まで自動で通る。古い SHA の deploy job は skip され、ヘルスチェック失敗時には直前の jar へ自動で戻る
- VM 再起動後にアプリが自動復帰する
- メモリ指標の Alarm と外形監視が通知先に届く
- セットアップ手順書だけで VM を再作成でき、秘密情報をパスワードマネージャから復元できる
- **Heroku の課金が $5/月 から $0 になり、費用がドメインの $10.44/年だけになっている**
- Heroku 固有ファイル (`Procfile` / `app.json` / `system.properties`) が撤去され、`docs/operations.md` が OCI 上の構成を記述している
