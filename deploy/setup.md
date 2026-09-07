# VM セットアップ手順書

Oracle Cloud Always Free の A1 VM に LineBot を載せる手順。VM が停止・削除されたときの再作成にもこのまま使う。設計と根拠は [設計書](../docs/superpowers/specs/2026-08-22-linebot-oracle-cloud-migration-design.md) にあり、ここでは繰り返さない。

記法: `[Mac]` は手元の Mac で、`[VM]` は VM に SSH した上で実行する。`<...>` は実行時に決まる値。

## 0. ユーザーが先に用意するもの

| 項目 | 値 | 備考 |
| --- | --- | --- |
| Oracle Cloud アカウント | ホームリージョン **ap-tokyo-1** | サインアップ時に決まり、後から変更できない |
| A1 インスタンス | VM.Standard.A1.Flex、2 OCPU / 12 GB、Ubuntu 24.04 (aarch64)、ブートボリューム 50 GB | `Out of host capacity` なら時間を置いてリトライ。取れるまで Heroku のまま |
| VCN の security list | ingress 22 / 80 / 443 (0.0.0.0/0)、egress は既定 (全許可) のまま | egress 443 は `api.line.me` と `script.google.com` に必要 |
| SSH 鍵 | インスタンス作成時に登録した公開鍵 (`ubuntu` ユーザー用) | |
| ドメイン | `bot.<domain>` の A レコードを VM の公開 IP へ | Cloudflare Registrar の場合は DNS の Proxy を **OFF** (DNS only) にする。Caddy が直接 TLS を終端するため |
| デプロイ鍵 | `[Mac] ssh-keygen -t ed25519 -f ~/.ssh/linebot-deploy -C linebot-deploy -N ''` | 公開鍵は §6、秘密鍵は §10 で使う |

以降、`<IP>` は VM の公開 IP、`<domain>` は取得したドメイン。

## 1. 接続と OS の初期設定

```bash
[Mac] ssh ubuntu@<IP>
[VM]  sudo apt-get update && sudo apt-get -y upgrade
[VM]  sudo apt-get install -y unattended-upgrades curl
[VM]  sudo dpkg-reconfigure -f noninteractive unattended-upgrades
[VM]  sudo timedatectl set-timezone Asia/Tokyo
```

## 2. SSH を公開鍵のみにする

```bash
[VM] sudo tee /etc/ssh/sshd_config.d/99-linebot.conf > /dev/null <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
EOF
[VM] sudo sshd -t && sudo systemctl reload ssh
```

## 3. iptables で 80 / 443 を開ける

OCI の Ubuntu イメージは security list とは別に OS 側の iptables が 22 以外を塞いでいる。ここを忘れると security list を開けても疎通しない。

```bash
[VM] sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT
[VM] sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
[VM] sudo netfilter-persistent save
[VM] sudo iptables -L INPUT -n --line-numbers | head -12
```

期待: 80 と 443 の ACCEPT が `REJECT` 行より **上**にある。

## 4. Temurin 8 (aarch64) を /opt/java/temurin8 に固定配置する

Ubuntu 24.04 に openjdk-8 のパッケージはない。Adoptium の tarball をバージョン固定で置く。

```bash
[VM] curl -s 'https://api.adoptium.net/v3/info/release_names?release_type=ga&version=%5B8%2C9%29&architecture=aarch64&os=linux&image_type=jdk&vendor=eclipse' | head
```

出力の先頭 (最新 GA) のリリース名を `<release>` にする (例: `jdk8u462-b08`)。**使った値をこの手順書の下の表に記録する。**

```bash
[VM] release=<release>
[VM] curl -fL -o /tmp/temurin8.tar.gz "https://api.adoptium.net/v3/binary/version/${release}/linux/aarch64/jdk/hotspot/normal/eclipse"
[VM] sudo mkdir -p /opt/java/temurin8
[VM] sudo tar -xzf /tmp/temurin8.tar.gz -C /opt/java/temurin8 --strip-components=1
[VM] /opt/java/temurin8/bin/java -version
```

期待: `openjdk version "1.8.0_..."` と `Temurin` の表示。

| 配置日 | リリース名 |
| --- | --- |
| (Task 10 で記入) | |

## 5. linebot ユーザーと配置先

```bash
[VM] sudo useradd --system --create-home --home-dir /opt/linebot --shell /bin/bash linebot
[VM] sudo -u linebot mkdir -p /opt/linebot/releases /opt/linebot/incoming /opt/linebot/.ssh
[VM] sudo chmod 700 /opt/linebot/.ssh
```

## 6. デプロイ鍵を linebot に登録する

`restrict` で pty と転送を禁止する (コマンド実行と scp は通る)。

```bash
[Mac] cat ~/.ssh/linebot-deploy.pub
[VM]  echo 'restrict <linebot-deploy.pub の内容>' | sudo -u linebot tee /opt/linebot/.ssh/authorized_keys > /dev/null
[VM]  sudo chmod 600 /opt/linebot/.ssh/authorized_keys
[Mac] ssh -i ~/.ssh/linebot-deploy linebot@<IP> 'echo ok'
```

期待: `ok`。

## 7. リポジトリのファイルを VM へ置く

`[Mac]` でリポジトリのルートから実行する。

```bash
[Mac] scp deploy/linebot-release.sh deploy/linebot.service deploy/journald-linebot.conf deploy/sudoers-linebot deploy/Caddyfile ubuntu@<IP>:/tmp/
[VM]  sudo install -o root -g root -m 0755 /tmp/linebot-release.sh /usr/local/bin/linebot-release.sh
[VM]  sudo install -o root -g root -m 0440 /tmp/sudoers-linebot /etc/sudoers.d/linebot
[VM]  sudo visudo -cf /etc/sudoers.d/linebot
[VM]  sudo install -o root -g root -m 0644 /tmp/linebot.service /etc/systemd/system/linebot.service
[VM]  sudo systemctl daemon-reload
[VM]  sudo systemctl enable linebot
[VM]  sudo mkdir -p /etc/systemd/journald.conf.d
[VM]  sudo install -o root -g root -m 0644 /tmp/journald-linebot.conf /etc/systemd/journald.conf.d/linebot.conf
[VM]  sudo systemctl restart systemd-journald
[VM]  sudo systemd-analyze verify /etc/systemd/system/linebot.service
```

期待: `visudo -cf` が `parsed OK`、`systemd-analyze verify` が何も出力しない (jar がまだ無いことによる警告は出てよい)。`enable` は jar が無くても成功する (VM 再起動時の自動起動を張るだけ)。ここで済ませておくのは、初回配備が失敗したときに未 enable のまま残さないため。

## 8. /etc/linebot.env

値は Heroku の Config Vars (`heroku config -a insidergamehelper`) と同じ。**パスワードマネージャにも保管する。**

```bash
[VM] sudo install -o root -g root -m 0600 /dev/null /etc/linebot.env
[VM] sudo tee /etc/linebot.env > /dev/null <<'EOF'
LINE_BOT_CHANNEL_TOKEN=<チャネルアクセストークン>
LINE_BOT_CHANNEL_SECRET=<チャネルシークレット>
EOF
[VM] sudo chmod 0600 /etc/linebot.env && ls -l /etc/linebot.env
```

期待: `-rw------- 1 root root`。切替前の検証中だけ、これに `LINE_BOT_API_END_POINT=http://127.0.0.1:18080/` を足す ([cutover.md](cutover.md))。

## 9. Caddy とレート制限モジュール

```bash
[VM] sudo apt-get install -y debian-keyring debian-archive-keyring apt-transport-https
[VM] curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
[VM] curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list > /dev/null
[VM] sudo apt-get update && sudo apt-get install -y caddy
[VM] sudo caddy add-package github.com/mholt/caddy-ratelimit
[VM] sudo apt-mark hold caddy
[VM] caddy list-modules | grep rate_limit
```

期待: `http.handlers.rate_limit` が出る。`apt-mark hold` により unattended-upgrades もモジュールなしのバイナリで上書きしない。

```bash
[VM] sudo sed "s/bot.example.com/bot.<domain>/" /tmp/Caddyfile | sudo tee /etc/caddy/Caddyfile > /dev/null
[VM] sudo caddy validate --config /etc/caddy/Caddyfile
[VM] sudo systemctl enable caddy
[VM] sudo systemctl restart caddy
[VM] sudo journalctl -u caddy -n 20 --no-pager
```

期待: `validate` が `Valid configuration`。journal に `certificate obtained successfully` (A レコードが向いていれば数十秒で取れる)。`restart` は `add-package` で差し替えた新しいバイナリで起動し直すために必要 (`reload` では古いプロセスのまま)。

```bash
[Mac] curl -si https://bot.<domain>/nothing | head -1
```

期待: `HTTP/2 404` (Spring Boot はまだ動いていないが、4 パス以外は Caddy が 404 を返す)。

## 10. GitHub Secrets

```bash
[Mac] ssh-keyscan -t ed25519 <IP> 2>/dev/null
```

出力の 1 行 (`<IP> ssh-ed25519 AAAA...`) を `DEPLOY_HOST_KEY` にする。

```bash
[Mac] gh secret set DEPLOY_HOST --body '<IP>'
[Mac] gh secret set DEPLOY_SSH_KEY < ~/.ssh/linebot-deploy
[Mac] gh secret set DEPLOY_HOST_KEY --body '<ssh-keyscan の 1 行>'
[Mac] gh secret list
```

LINE の資格情報は GitHub に置かない。

## 11. 初回配備

`master` への push が唯一のトリガー (`workflow_dispatch` は設けていない) なので、空コミットを push して配備する。

```bash
[Mac] git commit --allow-empty -m "chore: VM への初回配備" && git push
[Mac] gh run watch
[VM]  sudo systemctl status linebot --no-pager
[VM]  curl -s http://127.0.0.1:8081/actuator/health
```

期待: deploy job が成功し、`status` が `active (running)`、health が `{"status":"UP"}`。以降の再起動は更新スクリプトが行い、VM 再起動時は §7 の `enable` により自動起動する。

## 12. Monitoring プラグインの確認

OCI コンソール → Compute → Instances → 対象インスタンス → **Oracle Cloud Agent** タブで **Compute Instance Monitoring** が Enabled であることを確認する。次に **Metrics** タブで `Memory Utilization` のグラフに値が出ていることを見る (アプリ起動後 5〜10 分で現れる)。

**値が出ていなければ、アイドル回収のメモリ対策は存在しないものとして扱う** ([cutover.md](cutover.md) の PAYG の節)。

## 13. OCI Alarm (メール通知)

1. Developer Services → Notifications → Topics → Create Topic (`linebot-alerts`) → Create Subscription (Email、自分のアドレス)。届いた確認メールを承認する
2. Observability → Monitoring → Alarm Definitions → Create Alarm:
   - Metric namespace `oci_computeagent`、Metric `MemoryUtilization`、Interval 5 minutes、Statistic Mean、Dimension `resourceId` = 対象インスタンス
   - Trigger rule: `less than` 20、Trigger delay 30 minutes
   - Destination: Topic `linebot-alerts`
3. もう 1 つ Alarm を作り、Trigger rule を **Absent** にする (指標の欠測 = エージェント停止または VM 停止)。Trigger delay 30 minutes

## 14. 四半期ごとの更新

| 対象 | 手順 |
| --- | --- |
| OS | unattended-upgrades が自動で当てる。カーネル更新後は `sudo reboot` (再起動後に linebot と caddy が自動で上がることを §11 の health で確認) |
| JDK | §4 を新しいリリース名でやり直す。`sudo systemctl stop linebot` → 差し替え → `sudo systemctl start linebot` → health 確認。表を更新する |
| Caddy | `sudo caddy upgrade` (組み込みモジュールを維持したまま最新へ) → `caddy list-modules | grep rate_limit` → `sudo systemctl restart caddy` |

## 15. VM が失われたときの再作成

§0 でインスタンスを作り直し (公開 IP が変わる)、§1〜§13 を上から実行する。`/etc/linebot.env` の値はパスワードマネージャから復元する。IP が変わるので §0 の A レコード、§10 の `DEPLOY_HOST` と `DEPLOY_HOST_KEY` を更新する。完了後に [cutover.md](cutover.md) の A (切替前の検証 14 項目) をやり直す。
