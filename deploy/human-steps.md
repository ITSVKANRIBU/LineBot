# 画面操作の手順書 (人間がやること)

Heroku から Oracle Cloud への移行で、**ブラウザの画面操作やアカウント登録が必要な作業**だけを時系列に並べたもの。ターミナルで打つコマンドは [setup.md](setup.md) と [cutover.md](cutover.md) にあり、ここでは「どの画面で何を入れるか」だけを書く。

この 2 つは役割が違う。

| 文書 | 何が書いてあるか | 誰が読むか |
| --- | --- | --- |
| **この文書** | ブラウザでの画面操作、アカウント登録、値の記録 | 画面の前にいるとき |
| [setup.md](setup.md) | VM に SSH して打つコマンド (§1〜§15) | ターミナルの前にいるとき |
| [cutover.md](cutover.md) | 切替前検証 14 項目とその期待値 | 切替の判断をするとき |

コンソールのメニュー名は各サービスの改修でよく変わる。見つからないときは、この文書の名前で画面内を検索すれば大抵たどれる。

---

## 触るサービスの一覧

| サービス | アカウント | ここでやること | 費用 |
| --- | --- | --- | --- |
| Oracle Cloud | 新規作成が必要 | VM を作る、通信を開ける、監視とメール通知を設定する | $0 (Always Free) |
| ドメイン登録事業者 | 新規作成が必要 | ドメインを取る、A レコードを VM の IP へ向ける | 年 $10 前後 |
| GitHub | 既にある | Secrets を 3 つ登録する、最後にデフォルトブランチを変える | $0 |
| LINE Developers | 既にある | Webhook URL を差し替える | $0 |
| 公開フォームのリポジトリ | 既にある | API 接続先を差し替える | $0 |
| 外形監視サービス | 新規作成が必要 | Bot が生きているかを 5 分ごとに見てメールで知らせる | $0 |
| Heroku | 既にある | 最後に解約する | 現 $5/月 → $0 |

---

## 記録しておく値

進めながらここに書き込む。VM を作り直すときに全部必要になる。**トークンとシークレットはこの表ではなくパスワードマネージャへ。**

| 項目 | 値 | いつ決まるか |
| --- | --- | --- |
| VM の公開 IP | | Phase 2 |
| ドメイン (`bot.` を付けない部分) | | Phase 1 |
| Bot の URL | `https://bot.` + 上のドメイン | Phase 3 |
| 切替日 | | Phase 6 |
| Heroku 解約予定日 | 切替日 + 1 か月 | Phase 6 |

---

## 全体の流れ

```
Phase 1  準備          ドメインを取る / Oracle アカウントを作る       ← VM が取れなくても進められる
Phase 2  VM を作る      A1 インスタンス + 通信の開放                  ← ここが取れるまで Heroku のまま
Phase 3  DNS を向ける    A レコード
Phase 4  配備           GitHub Secrets → 初回配備                    (setup.md §7〜§11)
Phase 5  監視の下地      Monitoring 確認 + メール通知 + 外形監視
Phase 6  切替           フォーム / LINE / 記録                        ← 戻せる最後の地点
Phase 7  1 か月の様子見
Phase 8  解約           Heroku + デフォルトブランチ

別枠  ロールバック (戻したいとき) / PAYG へ上げる (アイドル通知が来たとき)
```

---

# Phase 1: 準備

VM が取れる前でも進められる。先に済ませておくと Phase 2 以降が止まらない。

## 1-1. ドメインを取る

**なぜ**: Heroku のホスト名 (`insidergamehelper.herokuapp.com`) が使えなくなるので、自分のドメインが必要。LINE の Webhook は HTTPS 必須で、証明書は VM 上の Caddy が自動で取る。

1. 好きな登録事業者でドメインを 1 つ取る (年 $10 前後のもので十分)
2. **Cloudflare Registrar を使う場合の注意**: DNS 設定でそのレコードの **Proxy を OFF (DNS only、灰色の雲)** にする。ON (オレンジの雲) だと Cloudflare が TLS を終端してしまい、VM 上の Caddy が証明書を取れない

Bot が使うのは `bot.<取ったドメイン>` の 1 つだけ。残りは自由に使える。

## 1-2. Oracle Cloud のアカウントを作る

**先に読む**: ここで選ぶ**ホームリージョンは後から変更できない**。必ず **日本東部 (東京) / ap-tokyo-1** を選ぶ。

1. Oracle Cloud のサインアップページで「無料で始める」
2. 国 = 日本、**ホームリージョン = Japan East (Tokyo)**
3. 本人確認でクレジットカードの登録を求められる。**Always Free の範囲では請求されない** (本人確認用)
4. 登録完了後、コンソールにログインできることを確認する

> **カードについて**: この段階の登録は本人確認用で、Always Free 枠を超えた利用は自動では発生しない。「Pay As You Go へのアップグレード」をした時点で初めて実請求先になる。アップグレードは Phase 別枠 (アイドル通知が来たとき) の話で、今はしない。

## 1-3. デプロイ用の SSH 鍵を作る

これはターミナルでの 1 コマンド。GitHub Actions が VM へ入るための鍵で、普段使いの鍵とは別に作る。

```bash
ssh-keygen -t ed25519 -f ~/.ssh/linebot-deploy -C linebot-deploy -N ''
```

できた 2 つのファイルの役割:

| ファイル | 用途 |
| --- | --- |
| `~/.ssh/linebot-deploy.pub` (公開鍵) | VM 側に登録する (setup.md §6) |
| `~/.ssh/linebot-deploy` (秘密鍵) | GitHub Secrets に登録する (Phase 4) |

## 1-4. LINE のトークンとシークレットを控える

`/etc/linebot.env` に書く値。**パスワードマネージャに保管する。**

LINE Developers コンソール → 対象のプロバイダー → 対象のチャネル:

| 値 | 場所 |
| --- | --- |
| チャネルアクセストークン | **Messaging API 設定** タブ → 「チャネルアクセストークン (長期)」 |
| チャネルシークレット | **チャネル基本設定** タブ → 「チャネルシークレット」 |

Heroku 側の現行値を使ってもよい (`heroku config -a insidergamehelper`)。どちらでも同じ値。

---

# Phase 2: VM を作る

**ここが移行の関門。** `Out of host capacity` で作れないことが多く、取れるまでは Heroku のまま運用を続ける。以降の Phase には進まない。

## 2-1. A1 インスタンスを作る

OCI コンソール → 左上のメニュー → **Compute** → **Instances** → **Create instance**

| 画面の項目 | 入れる値 |
| --- | --- |
| Name | 何でもよい (例 `linebot`) |
| Placement → Availability domain | 既定のまま (作れなければ別の AD も試す) |
| Image and shape → **Change image** | Canonical **Ubuntu** → **24.04** (aarch64 用が選ばれる) |
| Image and shape → **Change shape** | **Ampere** → **VM.Standard.A1.Flex** → OCPU **2**、Memory **12** GB |
| Networking | 新しい VCN を作らせる。**Assign a public IPv4 address = はい** |
| Add SSH keys | **Paste public keys** に `~/.ssh/id_ed25519.pub` など**普段使いの公開鍵**を貼る (デプロイ鍵ではない) |
| Boot volume | **Specify a custom boot volume size** にチェック → **50** GB |

**確認**: shape と image の欄に **Always Free eligible** のバッジが出ていること。2 OCPU / 12 GB / 50 GB は Always Free 枠のちょうど上限。

> **`Out of host capacity` と出たら**: 在庫切れで、設定の誤りではない。時間帯を変えて (深夜・早朝が通りやすいと言われる)、別の Availability domain も試しながら日をまたいでリトライする。**大阪リージョンや E2.1.Micro は代替にならない** (前者はホームリージョンが東京だと枠外、後者はメモリが足りない)。取れるまで Heroku のまま。

作成後、インスタンスの詳細画面に出る **Public IP address** を上の記録表に書く。

## 2-2. 通信を開ける (security list)

作った VM は 22 番以外が塞がっている。80 と 443 を開ける。

OCI コンソール → **Networking** → **Virtual cloud networks** → 作られた VCN → 左の **Security Lists** → **Default Security List** → **Add Ingress Rules**

2 本追加する:

| Source CIDR | IP Protocol | Destination Port Range |
| --- | --- | --- |
| `0.0.0.0/0` | TCP | `80` |
| `0.0.0.0/0` | TCP | `443` |

- 22 番は既定で開いているので触らない
- **Egress (送信) は既定の全許可のまま**。Bot が `api.line.me` (LINE への返信) と `script.google.com` (役職画像カタログ) へ出るのに必要

> **これだけでは通じない。** OCI の Ubuntu イメージは OS 側の iptables でも塞いでいる。そちらは [setup.md](setup.md) §3 で開ける。**片方だけだと疎通しない**ので、両方必要。

---

# Phase 3: DNS を向ける

ドメイン登録事業者の DNS 設定画面で、レコードを 1 本追加する。

| 項目 | 値 |
| --- | --- |
| タイプ | **A** |
| 名前 / ホスト | `bot` |
| 値 / IP アドレス | Phase 2 で記録した**公開 IP** |
| TTL | 既定のまま (自動でよい) |
| Proxy (Cloudflare の場合) | **OFF (DNS only)** |

反映を確認する:

```bash
dig +short bot.<ドメイン>
```

記録した公開 IP が返れば完了。返らなければ数分待つ。

**この時点で Bot の URL が確定する**: `https://bot.<ドメイン>` — 記録表に書いておく。

---

# Phase 4: 配備

ここは主にターミナル作業。[setup.md](setup.md) の §1 から §11 を上から実行する。画面操作が混じるのは Secrets だけ。

## 4-1. GitHub Secrets を 3 つ登録する

**コマンドで入れる場合** (setup.md §10 の手順):

```bash
ssh-keyscan -t ed25519 <IP> 2>/dev/null          # 出力の1行を控える
gh secret set DEPLOY_HOST --body '<IP>'
gh secret set DEPLOY_SSH_KEY < ~/.ssh/linebot-deploy
gh secret set DEPLOY_HOST_KEY --body '<ssh-keyscan の1行>'
```

**画面で入れる場合**: GitHub のリポジトリ → **Settings** → 左の **Secrets and variables** → **Actions** → **New repository secret**

| Name | Secret に入れる値 |
| --- | --- |
| `DEPLOY_HOST` | VM の公開 IP (例 `123.45.67.89`) |
| `DEPLOY_SSH_KEY` | `~/.ssh/linebot-deploy` の**中身をそのまま全部** (`-----BEGIN` から `-----END` の行まで) |
| `DEPLOY_HOST_KEY` | `ssh-keyscan -t ed25519 <IP>` の出力 1 行 (`<IP> ssh-ed25519 AAAA...`) |

- **LINE のトークンとシークレットは GitHub に置かない。** VM 上の `/etc/linebot.env` にだけ置く
- `DEPLOY_HOST_KEY` は「この IP のサーバはこの鍵を持っているはず」という指紋。これを固定しているので、経路を乗っ取られても偽のサーバへ jar を送り込まれない
- **VM を作り直すと IP と host key が変わる**ので、`DEPLOY_HOST` と `DEPLOY_HOST_KEY` を両方入れ直す

登録できたか確認:

```bash
gh secret list
```

期待: 3 つが並ぶ。**Secrets を登録するまで、`master` への push は毎回 `Configure SSH` で失敗する** (設計どおり。何が足りないかがログに出る)。

---

# Phase 5: 監視の下地

VM が動き出したら、止まったことに気付ける状態を作る。**Bot が黙っても利用者からの報告以外に検知手段がない**状態を避けるため。

## 5-1. メモリ指標が出ているか確認する (最重要)

**なぜ重要か**: Oracle は 7 日間アイドルの VM を停止する。判定は CPU・ネットワーク・メモリの **3 つすべてが閾値未満のとき**で、この Bot が唯一外せるのはメモリ条件。そのために JVM が起動時に 3 GB を確保している。**ただしこれは OCI 側にメモリ指標が報告されていないと全く効かない。**

OCI コンソール → **Compute** → **Instances** → 対象インスタンス:

1. **Oracle Cloud Agent** タブ → **Compute Instance Monitoring** が **Enabled** であること
2. **Metrics** タブ → **Memory Utilization** のグラフに**値が出ていること** (アプリ起動後 5〜10 分で現れる)
3. 値が **20% を上回っている**こと (3 GB / 12 GB = 25% が期待値)

**グラフに値が出ていなければ**、メモリ対策は存在しないものとして扱う。その場合は切替前に「PAYG へ上げるか」を決める (別枠を参照)。

## 5-2. メール通知を設定する

### まず通知先を作る

OCI コンソール → **Developer Services** → **Notifications** → **Topics** → **Create Topic**

| 項目 | 値 |
| --- | --- |
| Name | `linebot-alerts` |

作ったトピックを開き → **Create Subscription**

| 項目 | 値 |
| --- | --- |
| Protocol | **Email** |
| Email | 自分のアドレス |

**確認メールが届くので、本文のリンクを押して承認する。** 承認しないと通知が飛ばない (Status が `Pending` のままになる)。

### アラームを 2 つ作る

OCI コンソール → **Observability & Management** → **Monitoring** → **Alarm Definitions** → **Create Alarm**

**1 本目 — メモリが下がった (アイドル判定に近づいた)**

| 項目 | 値 |
| --- | --- |
| Alarm name | `linebot-memory-low` |
| Metric namespace | `oci_computeagent` |
| Metric name | `MemoryUtilization` |
| Interval | 5 minutes |
| Statistic | Mean |
| Dimension | `resourceId` = 対象インスタンス |
| Trigger rule → Operator | **less than** |
| Trigger rule → Value | `20` |
| Trigger delay minutes | `30` |
| Destination | Topic `linebot-alerts` |

**2 本目 — 指標が来なくなった (エージェント停止か VM 停止)**

1 本目と同じ設定で、Trigger rule の Operator を **Absent** にする。Trigger delay は `30`。

> 1 本目だけだと「VM が止まって指標ごと来なくなった」ケースを検知できない。**値の異常と欠測は別の事象**なので 2 本必要。

## 5-3. 外形監視を登録する

OCI の監視は「VM が動いているか」しか見ない。**アプリが応答しているか**は外から見る必要がある。

必要な条件を満たす無料の uptime 監視サービスを 1 つ選ぶ:

| 条件 | 値 |
| --- | --- |
| 監視 URL | `https://bot.<ドメイン>/actuator/health` |
| 間隔 | 5 分 |
| 成功の条件 | 応答本文に **`UP`** を含む (キーワード監視) |
| 通知 | メール |

UptimeRobot や Better Stack などが候補。**無料枠でキーワード監視ができるかは事前に確認する** (ステータスコードだけの監視だと、Spring Boot が「起動しているが不健全」な状態を見逃す)。

登録したら、一度 VM 上で `sudo systemctl stop linebot` してから通知が来ることを確かめると確実。確認後は `sudo systemctl start linebot` で戻す。

---

# Phase 6: 切替 (カットオーバー)

**戻せる最後の地点。** ここから先はロールバック手順が必要になる。

## 事前の確認

1. [cutover.md](cutover.md) の **A の 14 項目がすべて通っている**
2. スタブが外れている: `[VM] sudo grep -c API_END_POINT /etc/linebot.env` が **`0`**
3. **プレイヤーがいない時間帯を選ぶ** (進行中の村は切替の瞬間に消える)

## 6-1. 切替は 2 か所ある

**片方だけ切り替えると、特殊村がフォームからも LINE からも成立しない。** 必ず両方やる。

### (a) 公開フォームの API 接続先

公開フォーム (`insidergametool.netlify.app`) のリポジトリで、接続先を書き換えてデプロイする。

| | |
| --- | --- |
| 変更前 | `https://insidergamehelper.herokuapp.com` |
| 変更後 | `https://bot.<ドメイン>` |

設定 JavaScript (配信物では `assets/config-*.js`) に固定で入っている。リポジトリ側のソースを書き換え、Netlify のデプロイが完了するまで待つ。

### (b) LINE の Webhook URL

LINE Developers コンソール → 対象のチャネル → **Messaging API 設定** タブ:

1. **Webhook URL** の「編集」→ `https://bot.<ドメイン>/callback` → 「更新」
2. **「検証」ボタンを押す** → 期待: **成功**
3. **「Webhook の利用」が ON** になっていることを確認する (OFF だとイベントが届かない)

## 6-2. 実機で確かめる

1. 自分の LINE から `お題` → お題を設定 → 人数を設定
2. **別アカウント**でその村番号を送り、役職が届く
3. `@わーわーず` で Werewords も一通り
4. **公開フォームで特殊村を作成 → LINE からその番号で参加 → `@配布`** が通る

4 番は**フォーム側の切替を検証できる唯一の経路**なので飛ばさない。

## 6-3. 日付を記録する

記録表と [cutover.md](cutover.md) の B-8 に書く。

| | |
| --- | --- |
| 切替日 | |
| Heroku 解約予定日 | 切替日 + 1 か月 |

---

# Phase 7: 1 か月の様子見

最初の 1 週間は毎日、以降は週 1 回。[cutover.md](cutover.md) の C 節と同じ内容。

| 見るもの | どこで | 期待 |
| --- | --- | --- |
| メモリ指標 | OCI → Instances → 対象 → Metrics → Memory Utilization | **20% を上回っている** |
| アイドル判定のメール | 自分のメール | **届いていない** |
| 外形監視の失敗通知 | 自分のメール | **届いていない** |
| アプリのログ | `[VM] sudo journalctl -u linebot --since '-7 days' --no-pager \| grep -E ' (WARN\|ERROR) '` | 想定外のものがない |
| 利用者からの報告 | Bot の応答に含まれる意見フォーム | 応答遅延の報告がない |

**Oracle からアイドル判定のメールが届いた場合**: 即削除ではなく、**1 週間後に停止**という猶予付きの通知。その 1 週間のうちに別枠の「PAYG へ上げる」を判断する。

---

# Phase 8: 解約

Phase 7 で問題がなければ。**順序が大事**で、2 番を飛ばすと課金が止まらない。

## 8-1. Heroku アプリを削除する

Heroku ダッシュボード → `insidergamehelper` → **Settings** → 一番下の **Delete app**

## 8-2. Eco dynos を Unsubscribe する ← 忘れやすい

Heroku → 右上のアバター → **Account settings** → **Billing** → **Eco dynos** の **Unsubscribe**

> **Eco はアプリ単位ではなくアカウント単位の月額契約。** アプリを削除しただけでは **$5/月 は止まらない。** 翌月の請求が $0 になっていることを必ず確認する。

## 8-3. GitHub のデフォルトブランチを `master` に変える

**画面で**: GitHub のリポジトリ → **Settings** → **General** → **Default branch** → 切り替えボタン → `master` → **Update**

**コマンドで**:

```bash
gh repo edit --default-branch master
```

`3.0` は Heroku の連携先だったブランチ。Heroku が消えた後は `master` と乖離した履歴を持つだけなので、削除するかは自分の判断で決める。

## 8-4. 残りの後片付け

実装計画の Task 12 に従い、`3.0` を参照している記述や残った Heroku への言及を整理する。`Procfile` / `app.json` / `system.properties` は既に `master` から撤去済み。

---

# 別枠: ロールバック (Heroku へ戻したいとき)

**破壊的操作。** OCI 上で作られた村はすべて消える。Heroku 側に切替前の古い村が残っていると番号が混乱する。**Heroku の再起動を先に**行う。

詳細な手順とコマンドは [cutover.md](cutover.md) の D 節。画面操作は 2 つ。

1. プレイヤーがいないことを確認する (やむを得ず進行中に戻すなら、村が失われることを先に告知する)
2. `[Mac] heroku restart -a insidergamehelper` で古い村を破棄する
3. 起動を確認する (cutover.md D-3 の curl)
4. **公開フォームの API 接続先を `https://insidergamehelper.herokuapp.com` に戻してデプロイする**
5. **LINE Developers コンソールの Webhook URL を Heroku のものへ戻し、「検証」で成功を確認する**
6. 実機で `お題` が返ることを確認する

**VM は止めない。** 戻した後に原因を調べられる状態を残しておく。

---

# 別枠: PAYG へ上げる (アイドル通知が来たとき)

メモリ対策が効いていないということ。猶予の 1 週間のうちに決める。

**アップグレードすると Always Free の枠内は課金されないまま、アイドル回収の対象外になる。** ただしカードが本人確認用から**実請求先**に変わるので、コスト削減が目的なら最初から選ばず、ここまで来たときのエスカレーション先として使う。

## 手順

1. OCI コンソール → **Billing & Cost Management** → **Upgrade and Manage Payment** → **Pay As You Go** へアップグレード
2. 上げたら**必ず予算アラートを作る**: **Billing & Cost Management** → **Budgets** → **Create Budget**

| 項目 | 値 |
| --- | --- |
| Budget amount | `1` (USD) |
| Alert rule → Threshold | `100` % |
| 通知先 | 自分のメールアドレス |

月 $1 を超えた時点でメールが飛ぶので、枠外の課金が始まったら即座に気付ける。

3. すでに停止されていた場合は、コンソールの **Instances** → 対象 → **Start** で起動し、Phase 5-1 (メモリ指標) と Phase 6-2 (実機確認) をやり直す

---

# 困ったときの対応表

| 症状 | 疑うところ |
| --- | --- |
| インスタンスが作れない (`Out of host capacity`) | 在庫切れ。時間帯と Availability domain を変えてリトライ。取れるまで Heroku のまま |
| `https://bot.<ドメイン>` に繋がらない | ① DNS の A レコード ② OCI の security list (80/443) ③ VM の iptables ([setup.md](setup.md) §3)。**②と③は両方必要** |
| 証明書が取れない | Cloudflare の Proxy が ON になっていないか (OFF / DNS only にする)。A レコードが VM の IP を指しているか |
| `master` に push しても配備されない | GitHub Actions のログを見る。`Configure SSH` で失敗していれば Secrets 未登録。**VM を作り直した後は `DEPLOY_HOST` と `DEPLOY_HOST_KEY` の入れ直しが必要** |
| Bot が黙った (返信が来ない) | `/etc/linebot.env` に `LINE_BOT_API_END_POINT` が残っていないか (`sudo grep -c API_END_POINT /etc/linebot.env` が `0` であること)。検証用スタブへ返信が吸われている |
| LINE の Webhook 検証が失敗する | Webhook URL の末尾が `/callback` か。「Webhook の利用」が ON か。`https://bot.<ドメイン>/actuator/health` が `{"status":"UP"}` を返すか |
| メモリ指標が出ない | Oracle Cloud Agent の **Compute Instance Monitoring** が Enabled か。出ないならアイドル対策は無効として PAYG を検討する |
| フォームから作った村に LINE から参加できない | 切替の 2 か所のうち片方しか終わっていない (Phase 6-1)。フォーム側と LINE 側の接続先が同じホストを指しているか |
| VM が失われた | [setup.md](setup.md) §15。Phase 2 から作り直し、IP が変わるので A レコードと GitHub Secrets 2 つを更新して、[cutover.md](cutover.md) の A をやり直す |
