# 切替前検証・カットオーバー・ロールバック

[setup.md](setup.md) が完了し、`master` の最新が VM で動いている状態から始める。以下は [設計書](../docs/superpowers/specs/2026-08-22-linebot-oracle-cloud-migration-design.md) の「検証方法」「カットオーバーとロールバック」を実行手順に落としたもの。

## A. 切替前の検証 (14 項目)

### 準備: 返信をスタブへ向ける

切替前は LINE へ実際に送らず、返信内容をスタブで読む。

```bash
[VM] echo 'LINE_BOT_API_END_POINT=http://127.0.0.1:18080/' | sudo tee -a /etc/linebot.env > /dev/null
[VM] sudo systemctl restart linebot
[VM] python3 /tmp/line_api_stub.py 18080     # 別ターミナルで開いたままにする (scp deploy/verify/line_api_stub.py ubuntu@<IP>:/tmp/ で置く)
```

`[Mac]` 側では `export B=https://bot.<domain>` と `export LINE_BOT_CHANNEL_SECRET=<シークレット>` を済ませておく。

### 1. `/callapi` で通常村の一連の操作と `@` コマンド

```bash
[Mac] curl -s "$B/callapi?message=%E3%81%8A%E9%A1%8C&userId=owner"          # お題 → 4 桁の村番号が返る
[Mac] curl -s "$B/callapi?message=%E3%81%99%E3%81%84%E3%81%8B&userId=owner"  # すいか (お題設定)
[Mac] curl -s "$B/callapi?message=3&userId=owner"                             # 人数 3
[Mac] curl -s "$B/callapi?message=<村番号>&userId=member1"                    # 参加 → 役職
[Mac] curl -s "$B/callapi?message=<村番号>&userId=member2"
[Mac] for c in %40%E5%8F%96%E5%BE%97 %40%E9%85%8D%E5%B8%83 %40%E7%89%B9%E6%AE%8A %40%E9%80%86%E6%9D%91 %40%E3%82%8F%E3%83%BC%E3%82%8F%E3%83%BC%E3%81%9A; do curl -s "$B/callapi?message=$c&userId=owner"; echo; done
```

期待: [game-spec.md](../docs/game-spec.md) どおりの応答。`@取得` `@配布` `@特殊` `@逆村` `@わーわーず` がそれぞれ期待どおりのメッセージを返す。

### 2. 署名付き `/callback` で同じ内容がスタブへ届く

```bash
[Mac] python3 deploy/verify/post_callback.py "$B/callback" text owner2 'お題'
[Mac] python3 deploy/verify/post_callback.py "$B/callback" text owner2 'すいか'
[Mac] python3 deploy/verify/post_callback.py "$B/callback" text owner2 '3'
[Mac] python3 deploy/verify/post_callback.py "$B/callback" text member3 '<村番号>'
```

期待: すべて `status=200`。スタブ側に届いた `ReplyMessage` の `messages` が、1 の `/callapi` の応答と **同じ種類・同じ通数** (村作成の案内、お題設定の確認、人数設定の確認、参加時の役職)。村番号は経路ごとに採番され、**役職の文面と役職画像は席の抽選で決まる**ので、参加時の応答は文面まで一致しなくてよい (`RouteParityTest` と同じ扱い)。村作成・お題設定・人数設定の 3 つは村番号以外が一致する。

### 3. ポストバックとスタンプ

```bash
[Mac] python3 deploy/verify/post_callback.py "$B/callback" text owner2 '@取得'
[Mac] python3 deploy/verify/post_callback.py "$B/callback" postback owner2 '0'
[Mac] python3 deploy/verify/post_callback.py "$B/callback" sticker owner2
```

期待: `status=200`。スタブに、ポストバック `0` の応答 (お題候補) と、スタンプへの製作者情報が届く。

### 4. 不正署名の拒否

```bash
[Mac] python3 deploy/verify/post_callback.py "$B/callback" text owner2 'お題' --bad-signature
```

期待: `status=400` (SDK の `LineBotCallbackRequestParser` が署名不一致を拒否)。スタブに何も届かない。

### 5. `/callback` の所要時間

```bash
[Mac] for i in 1 2 3 4 5 6 7 8 9 10; do python3 deploy/verify/post_callback.py "$B/callback" text "timing$i" 'お題'; done
```

期待: `elapsed_ms` が **すべて 500 ms 未満** (LINE の 2 秒制限に対して 4 倍以上のマージン)。数値を下の記録表に残す。

### 6. `POST /specialvillage`

```bash
[Mac] curl -s -X POST "$B/specialvillage" -H 'Content-Type: application/json' -d '{"message":["A さんへ","B さんへ"]}'
```

期待: `{"data":"<5 桁>"}`。続けて `curl -s "$B/callapi?message=<5 桁>&userId=member4"` で `A さんへ` または `B さんへ` が返る。

### 7. レート制限と CORS プリフライト

レート制限の窓は 1 分で、鍵は接続元 IP。1〜6 で消費した枠が残っているので、**各計測の前に 60 秒空ける**。サイズ超過の確認は `/specialvillage` の枠を使い切る前に行う (枠を使い切った後は 413 に到達せず 429 になる)。

```bash
[Mac] sleep 60
[Mac] bash deploy/verify/ratelimit_check.sh "$B" '/callapi?message=x&userId=verify' 35
[Mac] sleep 60
[Mac] bash deploy/verify/ratelimit_check.sh "$B" '/callapi?message=x&userId=verify' 35 OPTIONS
[Mac] sleep 60
[Mac] curl -s -o /dev/null -w '%{http_code}\n' -X POST "$B/specialvillage" -H 'Content-Type: application/json' --data-binary @<(head -c 2200000 /dev/zero | tr '\0' 'a')
[Mac] sleep 60
[Mac] bash deploy/verify/ratelimit_check.sh "$B" '/specialvillage' 12 OPTIONS
```

期待: `/callapi` は `30 200` + `5 429`、OPTIONS も同じ配分 (プリフライトも数えられている)、2 MB 超は `413`、`/specialvillage` は `429` が 2 件で残り 10 件は 429 以外 (OPTIONS への応答は Spring の CORS 処理が返す 200)。

続けて **実際のブラウザ**で検証する。公開フォームのリポジトリでローカル起動し、API 接続先を `$B` に向けて、特殊村の作成 → `/callapi` の操作を普通の速さで一通り行う。DevTools の Network で `OPTIONS` と本リクエストの両方が **429 なし**で通ることを確認する。閾値 30 / 10 が窮屈なら `deploy/Caddyfile` の `events` を上げ、`/etc/caddy/Caddyfile` を差し替えて `sudo systemctl reload caddy`。確定値を記録表に書く。

### 8. health と 404

```bash
[Mac] curl -si "$B/actuator/health" | sed -n '1p;$p'
[Mac] for p in / /actuator /actuator/info /actuator/env /callback/ /index.html; do printf '%s ' "$p"; curl -s -o /dev/null -w '%{http_code}\n' "$B$p"; done
```

期待: health が `HTTP/2 200` と `{"status":"UP"}`。他はすべて `404`。

### 9. OCI Monitoring に `MemoryUtilization` が出ていて 20% を上回る

OCI コンソール → インスタンス → Metrics → `Memory Utilization`。期待: 直近 1 時間が **25% 以上**で安定 (3 GB / 12 GB = 25%、RSS 込みで 27〜28%)。

```bash
[VM] ps -o rss= -C java | awk '{printf "%.1f GB\n", $1/1024/1024}'
```

期待: `3.0 GB` 以上 (`AlwaysPreTouch` が効いている)。**指標が出ていなければ、この対策は無いものとして扱い、E の PAYG 判断へ進む。**

### 10. 役職イラストのカタログ取得 (egress)

```bash
[VM] sudo journalctl -u linebot --since '-10 min' --no-pager | grep -i 'catalog\|カタログ'
```

期待: 起動から 5 分以内に INFO でカタログ更新のログがあり、WARN の取得失敗がない。

### 11. `systemctl restart` 後の自動復帰

```bash
[VM] sudo systemctl restart linebot && sleep 20 && curl -s http://127.0.0.1:8081/actuator/health
```

期待: `{"status":"UP"}`。

### 12. VM 再起動後の自動起動

```bash
[VM] sudo reboot
[Mac] sleep 90; curl -s "$B/actuator/health"
```

期待: `{"status":"UP"}` (caddy と linebot が enable されている)。

### 13. デプロイのヘルスチェック失敗時に直前の jar へ戻る

VM 上で壊れたリリースを直接置いて更新スクリプトを呼ぶ。

`/opt/linebot` は `linebot` 所有で `ubuntu` からは入れないので、`sudo -u linebot` で操作する。

```bash
[VM] sudo -u linebot bash -c 'mkdir -p /opt/linebot/incoming/broken && cd /opt/linebot/incoming/broken && printf BROKEN > insider-game-bot.jar && sha256sum insider-game-bot.jar > insider-game-bot.jar.sha256'
[VM] sudo /usr/local/bin/linebot-release.sh broken; echo "exit=$?"
[VM] sudo readlink /opt/linebot/current.jar; curl -s http://127.0.0.1:8081/actuator/health
```

期待: `rolling back to ...` のログ、`exit=1`、`current.jar` が元の commit の jar を指し、health が `UP`。restart から戻しの判断まで 60 秒強で終わる。後始末: `sudo rm -rf /opt/linebot/releases/broken`。

### 14. 古い commit の run を re-run すると deploy が skip する

GitHub Actions で `master` の 1 つ前の commit の run を `gh run rerun <run-id>` し、deploy job のログに `skipping deploy of` が出て成功終了することを確認する。

### 後始末: スタブを外す

```bash
[VM] sudo sed -i '/^LINE_BOT_API_END_POINT=/d' /etc/linebot.env
[VM] sudo systemctl restart linebot && sleep 20 && curl -s http://127.0.0.1:8081/actuator/health
[VM] sudo grep -c API_END_POINT /etc/linebot.env
```

期待: `UP` と `0`。**これを忘れると切替後の返信がスタブへ吸われて利用者に何も届かない。**

### 記録表

| 項目 | 結果 | 日付 |
| --- | --- | --- |
| 5. `/callback` 所要時間 (10 回の最大) | ms | |
| 7. レート制限の確定値 (`/callapi` / `/specialvillage`) | / req/分/IP | |
| 9. MemoryUtilization (直近 1 時間の最小) | % | |

## B. カットオーバー

切替対象は **2 か所**: LINE の webhook URL と、公開フォーム (insidergametool.netlify.app) の API 接続先。片方だけ切り替えると特殊村がフォームからも LINE からも成立しない。

1. A がすべて通り、スタブが外れていることを確認する (`sudo grep -c API_END_POINT /etc/linebot.env` が `0`)
2. プレイヤーがいない時間帯を選ぶ (進行中の村は切替の瞬間に消える)
3. **[ユーザー]** 公開フォームのリポジトリで API 接続先 `https://insidergamehelper.herokuapp.com` を `https://bot.<domain>` に変え、デプロイする
4. **[ユーザー]** LINE Developers コンソール → Messaging API → Webhook URL を `https://bot.<domain>/callback` にして「検証」を押す。期待: 成功
5. 実機の LINE から `お題` → お題設定 → 人数 → 別アカウントで参加 → 役職が届く。`@わーわーず` で Werewords も一通り
6. **公開フォームで特殊村を作成 → LINE からその番号で参加 → `@配布`** が通る (フォーム側の切替を検証する唯一の経路)
7. 外形監視を登録する **[ユーザー]**: 無料の uptime 監視 (5 分間隔の HTTPS 監視とメール通知ができるもの) に `GET https://bot.<domain>/actuator/health` を登録し、キーワード `UP` を条件にする
8. 切替日を記録する: 切替日 = ____。Heroku 解約日 = 切替日 + 1 か月

## C. 1 か月の監視

毎日 (最初の 1 週間) → 週 1 回 (以降):

- OCI の `MemoryUtilization` が 20% を上回っている (A-9 と同じ場所)
- Oracle からアイドル判定のメールが **届いていない**
- 外形監視の失敗通知がない
- `[VM] sudo journalctl -u linebot --since '-7 days' --no-pager | grep -E ' (WARN|ERROR) '` に想定外の WARN / ERROR がない (アプリは標準出力へ書くので journald の優先度はすべて info。`-p warning` では拾えず、ログ本文のレベル文字列で絞る)
- 応答遅延の報告がない

## D. ロールバック (Heroku へ戻す)

**破壊的操作。** OCI 上で作られた村はすべて消え、Heroku 側の古い村が残っていると番号が混乱する。順序を守る (Heroku の再起動を先に)。

1. プレイヤーがいないことを確認する。やむを得ず進行中に戻すなら村が失われることを告知する
2. `[Mac] heroku restart -a insidergamehelper` で切替前の古い村を破棄する
3. `[Mac] curl -s 'https://insidergamehelper.herokuapp.com/callapi?message=%E3%81%8A%E9%A1%8C&userId=rollback-check'` で JSON が返る (起動確認)
4. **[ユーザー]** フォームの API 接続先を `https://insidergamehelper.herokuapp.com` に戻してデプロイする
5. **[ユーザー]** LINE Developers コンソールの Webhook URL を Heroku のものへ戻す
6. 実機で `お題` が返ることを確認する

## E. アイドル回収の通知が届いたとき (PAYG へ上げる判断)

メモリ対策が効いていないということなので、猶予の 1 週間のうちに:

1. **[ユーザー]** OCI コンソール → Billing → Upgrade and Manage Payment → Pay As You Go へアップグレード (Always Free 枠内は課金されない。アイドル回収の対象外になる)
2. **[ユーザー]** Billing → Budgets → Create Budget: 月 $1、閾値 100% でメール通知
3. 停止されてしまった場合はコンソールから Start し、A-9 と B-5 を再確認する

A-9 で `MemoryUtilization` がそもそも出ていなかった場合も、切替前にこの手順で PAYG へ上げるかを決める。

## F. Heroku の解約 (切替日 + 1 か月)

C の 1 か月で問題がなければ:

1. **[ユーザー]** Heroku ダッシュボード → `insidergamehelper` → Settings → Delete app
2. **[ユーザー]** Heroku の Account settings → Billing → Eco dynos の **Unsubscribe**。Eco はアプリ単位ではなくアカウント単位の月額契約なので、**アプリを削除しただけでは $5/月は止まらない**。翌月の請求が $0 になっていることを確認する
3. **[ユーザー]** GitHub のリポジトリ設定でデフォルトブランチを `master` にする (`gh repo edit --default-branch master`)
4. 実装計画 Task 12 に従って Heroku 固有ファイルを撤去し、docs を更新する
