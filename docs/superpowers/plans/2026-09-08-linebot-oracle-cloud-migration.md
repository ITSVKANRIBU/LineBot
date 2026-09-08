# LineBot Oracle Cloud Always Free 移行 実装計画

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Heroku ($5/月) で動いている LineBot を Oracle Cloud Always Free の A1 VM へ移し、`master` への push だけで本番反映が完了する自動デプロイを成立させ、1 か月の並行運用後に Heroku を解約する。

**Architecture:** VM 上で Caddy が TLS を終端し、4 パスだけを loopback の Spring Boot (systemd 管理、Temurin 8 aarch64) へ流す。GitHub Actions が `master` への push で jar と sha256 を artifact 化し、ホスト鍵を固定した SSH で VM へ送り、VM 上の `flock` 付き更新スクリプトが検証・昇格・再起動・ヘルスチェック・自動復帰を行う。Heroku は `3.0` ブランチと GitHub 連携のまま 1 か月保持し、ロールバック先にする。

**Tech Stack:** Spring Boot 2.1.5 / Temurin 8 (aarch64) / systemd / Caddy 2 + `github.com/mholt/caddy-ratelimit` / GitHub Actions / bash / Python 3 (検証スクリプト) / OCI Monitoring + Notifications

**Spec:** [docs/superpowers/specs/2026-08-22-linebot-oracle-cloud-migration-design.md](../specs/2026-08-22-linebot-oracle-cloud-migration-design.md)

## スペックからの逸脱 (ユーザー指示による)

スペックは本番ブランチを `3.0` としているが、ユーザーの指示で **本番ブランチは新設する `master`** とする。

- `master` は `origin/3.0` から切り、移行に関する変更 (Actuator、CI の deploy job、`deploy/` 一式) はすべて `master` に載せる
- `3.0` は Heroku の GitHub 連携が向いているブランチとして **1 か月の保持期間中は触らない**。Heroku 側の jar は `3.0` のまま動き続ける
- deploy job のトリガー、`concurrency` 後の SHA 再確認 (`git ls-remote origin refs/heads/master`) はすべて `master` を見る
- Heroku 解約後にリポジトリのデフォルトブランチを `master` へ切り替える (Task 12)

スペック内の「`3.0` への push」はすべて「`master` への push」と読み替える。

## スペックからの逸脱 (レビューで判明した事実誤認の修正)

- **`/specialvillage` のボディ上限は 1 MB ではなく 2 MB。** スペックは「1 村 100 メッセージ × 各 5000 文字」を 1 MB に収まると見ていたが、`String.length()` は UTF-16 単位で数え、日本語は UTF-8 で 1 単位 3 バイトになる。「あ」5000 文字 × 100 件の JSON は実測 1,500,313 バイトで、1 MB では既存 API が受理する入力を Caddy が拒否する。上限は 500,000 単位 × 3 バイト = 1.5 MB に JSON の枠を足した **2 MB** とする
- **転送先は `/opt/linebot/incoming/<commit-sha>/`、稼働 jar は `/opt/linebot/releases/<commit-sha>/`。** スペックは転送先と保持先を同じ `releases/<commit-sha>/` としていたが、同じ commit の workflow を re-run すると稼働中の jar へ直接 scp してしまい、転送中断で本番を壊せる。検証前の隔離 (incoming) と検証後の保持 (releases) を分け、更新スクリプトが検証済みの jar だけを `mv` で releases へ移す
- **世代管理は「直近 5 世代 + `current` / `previous` が指す世代」。** 戻し先を消さないための例外で、5 を超えることがある

## Global Constraints

スペック全体にかかる制約。各タスクの要件に暗黙に含まれる。

- **AGENTS.md**: 後方互換を保たない。フォールバック・移行処理・将来のための抽象化を足さない。動く最小構成から層を重ねる
- **日本語ドキュメントはハードラップしない** (桁数で折り返さない)
- ビルド JDK は **Temurin 8**。ローカルの Gradle は `JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew ...` で実行する (この Mac は java が PATH にない)。`check` のベースラインは 4 モジュール計 259 テスト、`:insider-game-bot:test` は 136 テスト
- 本番 JDK は **Temurin 8 aarch64** を `/opt/java/temurin8` にバージョン固定で配置
- VM: **VM.Standard.A1.Flex / ap-tokyo-1 / 2 OCPU / 12 GB / Ubuntu 24.04 LTS (aarch64) / ブートボリューム 50 GB**。1 台構成、シェイプ拡大なし
- JVM 起動引数: **`-Xms3g -Xmx3g -XX:+AlwaysPreTouch -XX:+UseG1GC -XX:MaxGCPauseMillis=200`**、アプリ引数 **`--server.address=127.0.0.1 --server.port=8081`**
- 実行ユーザー **`linebot`** (非 root)、`EnvironmentFile=/etc/linebot.env` (`root:root`, `0600`)、`Restart=always` / `RestartSec=5`、journald `SystemMaxUse=200M`
- ingress は **22 / 80 / 443** のみ、egress 443 は開けたまま (`api.line.me`、`script.google.com`)。SSH は公開鍵のみ、root ログイン禁止
- Caddy が Spring Boot へ流すのは **`/callback` `/callapi` `/specialvillage` `/actuator/health` の 4 つ**、それ以外は 404
- レート制限 (`mholt/caddy-ratelimit`): **`/callapi` 30 req/分/IP、`/specialvillage` 10 req/分/IP、超過は 429**。`/callback` には掛けない。閾値はフォーム実操作 (OPTIONS 込み) で確定する暫定値
- ボディ上限: **全パス 2 MB** (レート超過の 429 とは別の契約。上の「逸脱」節)。`/callback` は署名検証より前に本文全体を `byte[]` へ読むので、公開・無認証のこのパスにも上限が要る。超過時の応答は **413 または 502**: Caddy の `request_body` は Content-Length で事前に弾かず、下流が本文を読んだ時点で打ち切り、その下流の `reverse_proxy` は本文の読み取りエラーも 502 に丸める。どちらでも Spring には届かない
- ヘルスチェック契約: `GET http://127.0.0.1:8081/actuator/health` が **HTTP 200 かつボディ `{"status":"UP"}`** (HTTP ステータスと curl の成否も見る)。`systemctl restart` 後 **2 秒間隔で、restart から実時間で最長 60 秒**。`systemctl restart` 自体の失敗もヘルスチェック失敗として扱う。失敗時は `previous` へ戻して再確認、戻し先がなければ `systemctl stop linebot`
- デプロイ: `concurrency` group で直列化 (**`cancel-in-progress: false`**)、排他後に `master` の最新 SHA と一致しなければ skip、jar は `/opt/linebot/incoming/<commit-sha>/` へ転送し `sha256sum -c` 後に `/opt/linebot/releases/<commit-sha>/` へ `mv` してから `current.jar` を原子的に差し替え、直近 **5 世代** (+ `current` / `previous` が指す世代) を保持、`StrictHostKeyChecking=yes`
- GitHub Secrets には **SSH 秘密鍵・接続先ホスト・VM の SSH ホスト公開鍵のみ**。LINE の資格情報は置かない
- `PORT` 環境変数は使わない。`LOGGING_LEVEL_INSIDERGAME` は既定 (INFO) なら設定しない
- Heroku は切替後 **1 か月**保持。`Procfile` / `app.json` / `system.properties` の撤去は **解約後**
- 移行を始める条件は「ap-tokyo-1 で A1 インスタンスが起動していること」。それまで Heroku 側に触らない

## ファイル構成

| パス | 責務 | タスク |
| --- | --- | --- |
| `.github/workflows/ci.yml` | build job で jar + sha256 を artifact 化、シェル/Python テストの実行、`master` push で deploy job | 1, 3, 6, 7 |
| `insider-game-bot/build.gradle` | `spring-boot-starter-actuator` の依存追加 | 2 |
| `insider-game-bot/src/test/java/insidergame/HealthEndpointTest.java` | `/actuator/health` の契約を固定 | 2 |
| `deploy/linebot-release.sh` | VM 上の更新スクリプト (検証・昇格・再起動・ヘルスチェック・復旧・世代管理、`flock`) | 3 |
| `deploy/test/release_test.sh` | 更新スクリプトのテスト (偽の `systemctl` / `curl`) | 3 |
| `deploy/linebot.service` | systemd unit | 4 |
| `deploy/journald-linebot.conf` | journald の上限 | 4 |
| `deploy/linebot.env.example` | `/etc/linebot.env` の雛形 | 4 |
| `deploy/sudoers-linebot` | `linebot` が更新スクリプトだけを root で実行できる sudoers | 4 |
| `deploy/Caddyfile` | 公開ルート・レート制限・ボディ上限・404 | 5 |
| `deploy/verify/line_api_stub.py` | LINE 返信 API のスタブ。受け取った ReplyMessage を表示 | 6 |
| `deploy/verify/post_callback.py` | 署名付き `POST /callback` を組み立て、応答と所要時間を出す | 6 |
| `deploy/verify/test_post_callback.py` | 署名・イベント組み立ての単体テスト | 6 |
| `deploy/verify/ratelimit_check.sh` | レート制限の実測 | 6 |
| `deploy/setup.md` | VM セットアップ手順書 (再作成にそのまま使える粒度) | 8 |
| `deploy/cutover.md` | 切替前検証 14 項目・カットオーバー・ロールバック・監視・PAYG 判断 | 9 |
| `Procfile` / `app.json` / `system.properties` | Heroku 固有。解約後に削除 | 12 |
| `docs/operations.md` / `docs/roadmap.md` / `insider-game-bot/README.md` / `build.gradle` (コメント) | 解約後に OCI 構成へ書き換え | 12 |

`.gitignore` に `*/bin/*` があるため、`deploy/bin/` は使わない (無視されてコミットされない)。

## タスク一覧

| # | 内容 | 実施者 |
| --- | --- | --- |
| 1 | `master` ブランチ作成、build job で jar + sha256 を artifact 化 | 実装 |
| 2 | Actuator health endpoint (TDD) | 実装 |
| 3 | VM 更新スクリプトとそのテスト、CI への組み込み | 実装 |
| 4 | systemd unit / journald / env 雛形 / sudoers | 実装 |
| 5 | Caddyfile | 実装 |
| 6 | 検証スクリプト (LINE API スタブ、署名付き callback、レート制限計測) | 実装 |
| 7 | deploy job | 実装 |
| 8 | セットアップ手順書 `deploy/setup.md` | 実装 |
| 9 | カットオーバー手順書 `deploy/cutover.md` | 実装 |
| 10 | VM プロビジョニングと初回配備、切替前検証 14 項目 | **ユーザー** + 実装 |
| 11 | カットオーバーと 1 か月の監視 | **ユーザー** + 実装 |
| 12 | Heroku 解約、デフォルトブランチ切替、Heroku 固有ファイル撤去、docs 更新 | **ユーザー** + 実装 |

Task 1〜9 はコードと文書で、VM がなくても完了できる。Task 10 以降は A1 インスタンスが起動していることが前提で、それまで着手しない。

---

### Task 1: `master` ブランチの作成と build job の artifact 化

**Files:**
- Modify: `.github/workflows/ci.yml`
- Add: `docs/superpowers/plans/2026-09-08-linebot-oracle-cloud-migration.md` (この計画。`master` の最初のコミットに含める)

**Interfaces:**
- Produces: artifact 名 `insider-game-bot-jar`、中身は `dist/insider-game-bot.jar` と `dist/insider-game-bot.jar.sha256` (`sha256sum` 形式: `<hash>  insider-game-bot.jar`)。Task 7 の deploy job と Task 3 の更新スクリプトはこのファイル名に依存する

- [ ] **Step 1: `master` を `origin/3.0` から切る**

`future` と `origin/3.0` は同じコミット (`git log --oneline origin/3.0..future` が空) であることを確認してから切る。作業ツリーの未コミット変更 (`LEARNINGS.md`) はそのまま持ち越してよいが、この Task のコミットには含めない。

```bash
git fetch origin
git log --oneline origin/3.0..future
git switch -c master origin/3.0
```

- [ ] **Step 2: 計画をコミットする**

```bash
git add docs/superpowers/plans/2026-09-08-linebot-oracle-cloud-migration.md
git commit -m "docs: Oracle Cloud 移行の実装計画を追加する"
```

- [ ] **Step 3: ci.yml を書き換える**

トリガーを `master` と `develop` にし (この workflow ファイルは `master` 上のものなので `3.0` を残す理由がない。`3.0` への push は `3.0` 自身の ci.yml で動く)、ステップ名から Heroku を外し、jar + sha256 を artifact に保存する。ファイル全体を次の内容にする。

```yaml
name: CI

on:
  push:
    branches: [ master, develop ]
  pull_request:

permissions:
  contents: read

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v5

      # 本番は Temurin 8 (aarch64) で稼働している。
      # ビルドJDKを揃えないと、sourceCompatibility=1.8でも実行時の差分を拾えない。
      - name: Set up JDK 8
        uses: actions/setup-java@v5
        with:
          distribution: temurin
          java-version: '8'

      - name: Cache Gradle
        uses: actions/cache@v5
        with:
          path: |
            ~/.gradle/caches
            ~/.gradle/wrapper
          key: gradle-${{ hashFiles('**/*.gradle', 'gradle/wrapper/gradle-wrapper.properties') }}
          restore-keys: gradle-

      - name: Check
        run: ./gradlew --no-daemon check

      - name: Build executable jar
        run: ./gradlew --no-daemon :insider-game-bot:bootJar

      # deploy job と VM 上の更新スクリプトはこのファイル名に依存する
      - name: Package release
        run: |
          mkdir -p dist
          cp insider-game-bot/build/libs/insider-game-bot-*.jar dist/insider-game-bot.jar
          (cd dist && sha256sum insider-game-bot.jar > insider-game-bot.jar.sha256)

      - name: Upload release
        uses: actions/upload-artifact@v7
        with:
          name: insider-game-bot-jar
          path: dist/
          retention-days: 7

      - name: Upload test reports
        if: always()
        uses: actions/upload-artifact@v7
        with:
          name: test-reports
          path: '**/build/reports/tests/test'
          if-no-files-found: ignore
```

- [ ] **Step 4: ローカルで Package release と同じ手順が通ることを確認する**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew --no-daemon :insider-game-bot:bootJar
ls insider-game-bot/build/libs/
```

Expected: `build/libs` に jar が **1 つだけ**ある (`-plain.jar` がないこと。Spring Boot 2.1 の bootJar は `jar` タスクを無効化する)。1 つでなければ `cp ... dist/insider-game-bot.jar` のワイルドカードが複数に展開されて失敗するので、Package release の前提が崩れている。

- [ ] **Step 5: コミットして push し、Actions で artifact を確認する**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: master への push で jar と sha256 を artifact に保存する"
git push -u origin master
gh run watch
gh api "repos/{owner}/{repo}/actions/runs/$(gh run list --branch master --limit 1 --json databaseId --jq '.[0].databaseId')/artifacts" --jq '.artifacts[].name'
```

Expected: `insider-game-bot-jar` と `test-reports` が並ぶ (`gh run view --json` に `artifacts` フィールドはないので REST API で見る)。

---

### Task 2: Actuator health endpoint

**Files:**
- Modify: `insider-game-bot/build.gradle`
- Test: `insider-game-bot/src/test/java/insidergame/HealthEndpointTest.java`

**Interfaces:**
- Produces: `GET /actuator/health` → HTTP 200、ボディ厳密に `{"status":"UP"}`。Task 3 の更新スクリプトと Task 5 の Caddyfile はこの URL と本文に依存する
- 自前の endpoint は書かない。Spring Boot 2.1 は `health` と `info` を既定で HTTP 公開し、`show-details` の既定は `never` なので詳細は出ない

- [ ] **Step 1: 失敗するテストを書く**

`insider-game-bot/src/test/java/insidergame/HealthEndpointTest.java`:

```java
/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package insidergame;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import insidergame.adapter.IllustrationCatalogJob;

/**
 * デプロイのヘルスチェックが依存する {@code /actuator/health} の契約を固定する.
 *
 * <p>VM 上の更新スクリプトは「HTTP 200 かつ本文が {@code {"status":"UP"}}」で起動完了を
 * 判定する。詳細 (diskSpace 等) が本文に混ざると文字列一致が壊れるため、本文まで固定する。
 *
 * <p>{@link IllustrationCatalogJob} は mock に差し替える。実際に取得すると staticなカタログが
 * 埋まり、既定画像を期待する他のテストが同じ JVM 上で壊れるため。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "line.bot.channel-token=dummy-channel-token",
    "line.bot.channel-secret=dummy-channel-secret",
})
public class HealthEndpointTest {

  @MockBean
  private IllustrationCatalogJob illustrationCatalogJob;

  @Autowired
  private TestRestTemplate rest;

  @Test
  public void healthReportsUpWithoutDetails() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("{\"status\":\"UP\"}", response.getBody());
  }

  /** 公開するのは health だけ。env や beans が HTTP に出ていないことを固定する. */
  @Test
  public void otherActuatorEndpointsAreNotExposed() {
    assertEquals(HttpStatus.NOT_FOUND,
        rest.getForEntity("/actuator/env", String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND,
        rest.getForEntity("/actuator/beans", String.class).getStatusCode());
  }
}
```

- [ ] **Step 2: テストが失敗することを確認する**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew --no-daemon :insider-game-bot:test --tests insidergame.HealthEndpointTest
```

Expected: FAIL。`healthReportsUpWithoutDetails` が `expected:<200> but was:<404>` (Actuator がないので `/actuator/health` は存在しない)。

- [ ] **Step 3: 依存を追加する**

`insider-game-bot/build.gradle` の `dependencies` を次にする。バージョンはルートの dependency-management (Spring Boot 2.1.5 の BOM) が決めるので書かない。

```groovy
dependencies {
    implementation project(':line-bot-spring-boot')
    // デプロイのヘルスチェック (GET /actuator/health) に使う。自前の endpoint は書かない
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
}
```

- [ ] **Step 4: テストが通ることを確認する**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew --no-daemon :insider-game-bot:test --tests insidergame.HealthEndpointTest
```

Expected: PASS (2 tests)。

- [ ] **Step 5: 全テストが通ることを確認する**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew --no-daemon check
```

Expected: BUILD SUCCESSFUL。`:insider-game-bot:test` が 138 テスト (136 + 2)。`compileJava` の `-Werror` に警告が出ないこと。

- [ ] **Step 6: コミット**

```bash
git add insider-game-bot/build.gradle insider-game-bot/src/test/java/insidergame/HealthEndpointTest.java
git commit -m "feat: デプロイのヘルスチェック用に /actuator/health を公開する"
```

---

### Task 3: VM 上の更新スクリプトとテスト

**Files:**
- Create: `deploy/linebot-release.sh`
- Test: `deploy/test/release_test.sh`
- Modify: `.github/workflows/ci.yml` (テスト実行ステップの追加)

**Interfaces:**
- Consumes: `/opt/linebot/incoming/<commit-sha>/insider-game-bot.jar` と同ディレクトリの `insider-game-bot.jar.sha256` (Task 1 の artifact を Task 7 の deploy job がそのまま置いたもの)。`GET http://127.0.0.1:8081/actuator/health` の契約 (Task 2)
- Produces: `/usr/local/bin/linebot-release.sh <commit-sha>`。検証済みの jar を `/opt/linebot/releases/<commit-sha>/` へ移し、incoming 側を消す。終了コード 0 = 新版が稼働、1 = 失敗 (旧版へ戻したか停止した)。`/opt/linebot/current.jar` と `/opt/linebot/previous.jar` は releases 配下の jar への **シンボリックリンク**。同じ jar を再配備しても `previous` は動かさない。Task 4 の unit は `current.jar` を起動し、Task 7 の deploy job は `sudo /usr/local/bin/linebot-release.sh $GITHUB_SHA` を呼ぶ
- 世代管理: 直近 5 世代を残す。`current` / `previous` が指す世代は例外として残す。成功・失敗どちらの経路でも最後に実行する
- 環境変数で場所を差し替えられる (テスト用): `LINEBOT_HOME` (既定 `/opt/linebot`)、`LINEBOT_HEALTH_URL`、`LINEBOT_HEALTH_TIMEOUT` (既定 60 秒)、`LINEBOT_HEALTH_INTERVAL` (既定 2 秒)、`LINEBOT_KEEP_RELEASES` (既定 5)

このテストは **Linux 専用** (`flock` と `sha256sum` を使う)。macOS には `flock` がないので、CI の build job と VM 上で実行する。ローカルで回すなら `brew install discoteq/discoteq/flock` を入れる。

- [ ] **Step 1: 失敗するテストを書く**

`deploy/test/release_test.sh`:

```bash
#!/usr/bin/env bash
# deploy/linebot-release.sh の振る舞いを、systemctl と curl を偽物に差し替えて確かめる.
# Linux 専用 (flock / sha256sum を使う)。CI の build job と VM 上で実行する。
#
# jar は中身がそのまま「振る舞い」を表すテキストファイル:
#   GOOD    起動して 200 {"status":"UP"} を返す
#   BROKEN  起動しない (curl が失敗する)
#   HALFUP  本文は UP だが HTTP 503 (ステータスを見ていないと健康と誤判定する)
#   NOSTART systemctl restart 自体が失敗する
set -euo pipefail

script="$(cd "$(dirname "$0")/.." && pwd)/linebot-release.sh"
failures=0

fake_bin="$(mktemp -d)"
cat > "$fake_bin/systemctl" <<'EOF'
#!/usr/bin/env bash
echo "$*" >> "$LINEBOT_HOME/systemctl.log"
jar="$(readlink "$LINEBOT_HOME/current.jar" 2>/dev/null || true)"
if [[ "$1" == restart && -n "$jar" && "$(cat "$jar")" == NOSTART ]]; then
  exit 1
fi
EOF
cat > "$fake_bin/curl" <<'EOF'
#!/usr/bin/env bash
# 本物と同じく -o <file> に本文を書き、-w の代わりに HTTP ステータスを標準出力へ出す
out=/dev/null
while [[ $# -gt 0 ]]; do
  case "$1" in
    -o) out="$2"; shift 2 ;;
    -w) shift 2 ;;
    *) shift ;;
  esac
done
jar="$(readlink "$LINEBOT_HOME/current.jar" 2>/dev/null || true)"
kind="$( [[ -n "$jar" ]] && cat "$jar" || true )"
case "$kind" in
  GOOD)   printf '{"status":"UP"}' > "$out"; printf 200; exit 0 ;;
  HALFUP) printf '{"status":"UP"}' > "$out"; printf 503; exit 0 ;;
  *)      exit 7 ;;
esac
EOF
chmod +x "$fake_bin/systemctl" "$fake_bin/curl"
export PATH="$fake_bin:$PATH"
export LINEBOT_HEALTH_TIMEOUT=0 LINEBOT_HEALTH_INTERVAL=0

fresh_home() {
  LINEBOT_HOME="$(mktemp -d)"
  export LINEBOT_HOME
  mkdir -p "$LINEBOT_HOME/releases" "$LINEBOT_HOME/incoming"
}

# add_release <sha> <jar の中身> : CI が転送した状態 (incoming/<sha>/ に jar と正しい sha256) を作る
add_release() {
  local dir="$LINEBOT_HOME/incoming/$1"
  mkdir -p "$dir"
  printf '%s' "$2" > "$dir/insider-game-bot.jar"
  (cd "$dir" && sha256sum insider-game-bot.jar > insider-game-bot.jar.sha256)
  sleep 0.01 # ls -t で世代順を区別できるように mtime をずらす
}

# run_release <sha> : 終了コードを $status に入れる
run_release() {
  set +e
  "$script" "$1" > "$LINEBOT_HOME/out.log" 2>&1
  status=$?
  set -e
}

# assert_eq <説明> <期待> <実際>
assert_eq() {
  if [[ "$2" == "$3" ]]; then
    echo "ok   - $1"
  else
    echo "FAIL - $1: expected [$2] got [$3]"
    failures=$((failures + 1))
  fi
}

released() { echo "$LINEBOT_HOME/releases/$1/insider-game-bot.jar"; }
current() { readlink "$LINEBOT_HOME/current.jar" 2>/dev/null || echo none; }
previous() { readlink "$LINEBOT_HOME/previous.jar" 2>/dev/null || echo none; }
systemctl_log() { cat "$LINEBOT_HOME/systemctl.log" 2>/dev/null | tr '\n' ';' || true; }
incoming_count() { ls -1 "$LINEBOT_HOME/incoming" | wc -l | tr -d ' '; }

echo "# 初回配備"
fresh_home
add_release aaa GOOD
run_release aaa
assert_eq "初回は成功する" 0 "$status"
assert_eq "current が releases 配下の新 jar を指す" "$(released aaa)" "$(current)"
assert_eq "previous は無い" none "$(previous)"
assert_eq "incoming は空になる" 0 "$(incoming_count)"
assert_eq "restart を 1 回だけ呼ぶ" "restart linebot;" "$(systemctl_log)"

echo "# 2 回目の配備"
add_release bbb GOOD
run_release bbb
assert_eq "成功する" 0 "$status"
assert_eq "current が bbb" "$(released bbb)" "$(current)"
assert_eq "previous が aaa" "$(released aaa)" "$(previous)"

echo "# 同じ commit の再実行は previous を動かさない"
add_release bbb GOOD
run_release bbb
assert_eq "成功する" 0 "$status"
assert_eq "current は bbb のまま" "$(released bbb)" "$(current)"
assert_eq "previous は aaa のまま (bbb 自身にならない)" "$(released aaa)" "$(previous)"

echo "# 起動しない jar は直前へ戻す"
add_release ccc BROKEN
run_release ccc
assert_eq "job は失敗する" 1 "$status"
assert_eq "current が bbb に戻る" "$(released bbb)" "$(current)"
assert_eq "この home での restart は aaa, bbb, bbb 再実行, ccc, 戻しの 5 回。stop はしない" \
  "restart linebot;restart linebot;restart linebot;restart linebot;restart linebot;" "$(systemctl_log)"

echo "# 本文が UP でも HTTP 200 でなければ失敗として戻す"
add_release hhh HALFUP
run_release hhh
assert_eq "job は失敗する" 1 "$status"
assert_eq "current が bbb に戻る" "$(released bbb)" "$(current)"

echo "# systemctl restart 自体の失敗も戻す"
add_release nnn NOSTART
run_release nnn
assert_eq "job は失敗する" 1 "$status"
assert_eq "current が bbb に戻る" "$(released bbb)" "$(current)"

echo "# 戻し先が無ければ停止する"
fresh_home
add_release ddd BROKEN
run_release ddd
assert_eq "job は失敗する" 1 "$status"
assert_eq "restart の後に stop を呼ぶ" "restart linebot;stop linebot;" "$(systemctl_log)"

echo "# チェックサム不一致は何もしない"
fresh_home
add_release eee GOOD
printf '%064d  insider-game-bot.jar\n' 0 > "$LINEBOT_HOME/incoming/eee/insider-game-bot.jar.sha256"
run_release eee
assert_eq "失敗する" 1 "$status"
assert_eq "current は作られない" none "$(current)"
assert_eq "releases に移されない" "" "$(ls -1 "$LINEBOT_HOME/releases")"
assert_eq "restart しない" "" "$(systemctl_log)"

echo "# 直近 5 世代だけ残す (current / previous は例外)"
fresh_home
for sha in r1 r2 r3 r4 r5 r6 r7; do
  add_release "$sha" GOOD
  run_release "$sha"
done
assert_eq "7 回目も成功" 0 "$status"
assert_eq "残るのは 5 世代" 5 "$(ls -1 "$LINEBOT_HOME/releases" | wc -l | tr -d ' ')"
assert_eq "current の r7 が残る" "$(released r7)" "$(current)"
assert_eq "previous の r6 が残る" "$(released r6)" "$(previous)"
if [[ -d "$LINEBOT_HOME/releases/r1" ]]; then r1=kept; else r1=gone; fi
assert_eq "最古の r1 は消える" gone "$r1"

if [[ "$failures" -ne 0 ]]; then
  echo "$failures failure(s)"
  exit 1
fi
echo "all passed"
```

- [ ] **Step 2: テストが失敗することを確認する**

Linux 環境で実行する。ローカル Mac に `flock` がなければこのステップは CI で確認する (Step 6 の push 後に `gh run watch`)。

```bash
chmod +x deploy/test/release_test.sh
bash deploy/test/release_test.sh
```

Expected: `linebot-release.sh: No such file or directory` で失敗する。

- [ ] **Step 3: 更新スクリプトを書く**

`deploy/linebot-release.sh`:

```bash
#!/usr/bin/env bash
# 使い方: linebot-release.sh <commit-sha>
#
# /opt/linebot/incoming/<commit-sha>/ に転送された insider-game-bot.jar を検証し、
# /opt/linebot/releases/<commit-sha>/ へ移して current へ昇格し、linebot.service を再起動して
# ヘルスチェックが通るまで待つ。通らなければ直前の jar へ戻す。戻し先がなければ停止する
# (壊れた状態で Restart=always が空回りするのを止める)。
#
# 転送先 (incoming) と稼働中の jar (releases) を分けているのは、同じ commit の workflow を
# 再実行したときに稼働中の jar へ直接 scp して、転送中断で壊すのを防ぐため。
# GitHub Actions の runner が途中で消えても、この 1 本が VM 上で完走するように
# flock で排他し、途中で打ち切られない単位にまとめている。
set -euo pipefail

HOME_DIR="${LINEBOT_HOME:-/opt/linebot}"
HEALTH_URL="${LINEBOT_HEALTH_URL:-http://127.0.0.1:8081/actuator/health}"
HEALTH_TIMEOUT="${LINEBOT_HEALTH_TIMEOUT:-60}"  # 秒。restart からこの時間まで待つ
HEALTH_INTERVAL="${LINEBOT_HEALTH_INTERVAL:-2}" # 秒。確認の間隔
KEEP_RELEASES="${LINEBOT_KEEP_RELEASES:-5}"
SERVICE=linebot

INCOMING="$HOME_DIR/incoming"
RELEASES="$HOME_DIR/releases"
CURRENT="$HOME_DIR/current.jar"
PREVIOUS="$HOME_DIR/previous.jar"

sha="${1:?usage: $0 <commit-sha>}"
incoming_dir="$INCOMING/$sha"
release_dir="$RELEASES/$sha"
jar="$release_dir/insider-game-bot.jar"

log() { echo "[release] $*"; }

# 契約: HTTP 200 かつ本文が {"status":"UP"} (設計書「ヘルスチェックの契約」)。
# 本文だけでなく HTTP ステータスと curl の成否も見る。
check_health() {
  local body_file status
  body_file="$(mktemp)"
  status="$(curl -s --max-time 2 -o "$body_file" -w '%{http_code}' "$HEALTH_URL" || true)"
  if [[ "$status" == 200 && "$(cat "$body_file")" == '{"status":"UP"}' ]]; then
    rm -f "$body_file"
    return 0
  fi
  rm -f "$body_file"
  return 1
}

# restart から HEALTH_TIMEOUT 秒以内に check_health が通るまで、HEALTH_INTERVAL 秒間隔で待つ。
# 実時間の期限で打ち切るので、curl の待ち時間を含めても HEALTH_TIMEOUT を超えない。
healthy() {
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while :; do
    if check_health; then
      return 0
    fi
    if (( SECONDS >= deadline )); then
      return 1
    fi
    sleep "$HEALTH_INTERVAL"
  done
}

# systemctl restart 自体の失敗も、ヘルスチェック失敗と同じに扱う
restart_and_check() {
  if ! systemctl restart "$SERVICE"; then
    log "systemctl restart $SERVICE failed"
    return 1
  fi
  healthy
}

# link <jar の絶対パス> <リンク先> : 一時名で作って mv するので、リンクの差し替えは原子的
link() {
  ln -sf "$1" "$2.tmp"
  mv -f "$2.tmp" "$2"
}

# 直近 KEEP_RELEASES 世代を残して古い世代を削る。current / previous が指す世代は世代数に
# 関わらず残す (戻し先を消さない)。成功・失敗のどちらの経路でも最後に呼ぶ。
prune() {
  local keep=() old dir k
  [[ -L "$CURRENT" ]] && keep+=("$(readlink "$CURRENT")")
  [[ -L "$PREVIOUS" ]] && keep+=("$(readlink "$PREVIOUS")")
  ls -1t "$RELEASES" | tail -n "+$((KEEP_RELEASES + 1))" | while read -r old; do
    dir="$RELEASES/$old"
    for k in "${keep[@]}"; do
      [[ "$k" == "$dir/"* ]] && continue 2
    done
    log "prune $dir"
    rm -rf "$dir"
  done
}

exec 9>"$HOME_DIR/release.lock"
flock 9

log "verify $sha"
(cd "$incoming_dir" && sha256sum -c --quiet insider-game-bot.jar.sha256)

# 検証済みの jar だけを releases へ移す。同じファイルシステム内の mv なので原子的。
# 同じ commit の再実行なら、同一内容の jar で置き換わるだけ。
mkdir -p "$release_dir"
mv -f "$incoming_dir/insider-game-bot.jar.sha256" "$release_dir/insider-game-bot.jar.sha256"
mv -f "$incoming_dir/insider-game-bot.jar" "$jar"
rm -rf "$incoming_dir"

# 同じ jar を再配備するときは previous を動かさない (戻し先を失わない)
if [[ -L "$CURRENT" && "$(readlink "$CURRENT")" != "$jar" ]]; then
  link "$(readlink "$CURRENT")" "$PREVIOUS"
fi
link "$jar" "$CURRENT"
log "restart $SERVICE with $sha"

if restart_and_check; then
  log "healthy: $sha"
  prune
  exit 0
fi

log "health check failed for $sha"
if [[ -L "$PREVIOUS" ]]; then
  previous_jar="$(readlink "$PREVIOUS")"
  log "rolling back to $previous_jar"
  link "$previous_jar" "$CURRENT"
  if restart_and_check; then
    log "rolled back; production is running $previous_jar"
    prune
    exit 1
  fi
  log "rollback did not become healthy either"
fi

log "stopping $SERVICE: no healthy release"
systemctl stop "$SERVICE" || true
prune
exit 1
```

```bash
chmod +x deploy/linebot-release.sh
```

- [ ] **Step 4: テストが通ることを確認する**

```bash
bash deploy/test/release_test.sh
```

Expected: すべて `ok` で `all passed`。

- [ ] **Step 5: CI にテストを組み込む**

`.github/workflows/ci.yml` の `Build executable jar` の直後に追加する。

```yaml
      - name: Test the release script
        run: bash deploy/test/release_test.sh
```

- [ ] **Step 6: コミットして push し、CI で通ることを確認する**

```bash
git add deploy/linebot-release.sh deploy/test/release_test.sh .github/workflows/ci.yml
git commit -m "feat: VM 上で検証・昇格・再起動・自動復帰を行う更新スクリプトを追加する"
git push
gh run watch
```

Expected: `Test the release script` ステップが `all passed` で成功。

---

### Task 4: systemd unit / journald / env 雛形 / sudoers

**Files:**
- Create: `deploy/linebot.service`
- Create: `deploy/journald-linebot.conf`
- Create: `deploy/linebot.env.example`
- Create: `deploy/sudoers-linebot`

**Interfaces:**
- Consumes: `/opt/linebot/current.jar` (Task 3)、`/opt/java/temurin8/bin/java` (Task 8 で配置)、`/etc/linebot.env`
- Produces: `linebot.service` (Task 3 が `systemctl restart linebot` する対象)。`linebot` ユーザーが `sudo /usr/local/bin/linebot-release.sh` だけをパスワードなしで実行できる (Task 7 の deploy job が依存)

静的なファイルなので、ここではコミットまでを行い、実際の検証 (`systemd-analyze verify`、`visudo -c`) は Task 10 で VM 上で実施する。

- [ ] **Step 1: systemd unit を書く**

`deploy/linebot.service`:

```ini
[Unit]
Description=Insider Game LINE Bot (Spring Boot)
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=linebot
Group=linebot
EnvironmentFile=/etc/linebot.env
WorkingDirectory=/opt/linebot
# -Xms3g -XX:+AlwaysPreTouch: Always Free のアイドル回収 (メモリ 20% 未満) から外すため、
# 起動時に 3 GB を実際に触って RSS を確保する。-Xms だけでは予約に留まり RSS が伸びない。
# G1 + MaxGCPauseMillis: 不自然に大きいヒープの副作用 (フル GC のポーズ) を応答時間へ持ち込まない保険。
# --server.address=127.0.0.1: Tomcat を loopback にだけ bind し、Caddy を経由しない直接アクセスを不可能にする。
ExecStart=/opt/java/temurin8/bin/java \
  -Xms3g -Xmx3g -XX:+AlwaysPreTouch -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -jar /opt/linebot/current.jar \
  --server.address=127.0.0.1 --server.port=8081
SuccessExitStatus=143
Restart=always
RestartSec=5
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=full
ProtectHome=yes

[Install]
WantedBy=multi-user.target
```

`ProtectHome=yes` は `/home` を隠すだけで `/opt/linebot` には影響しない。`/etc/linebot.env` は PID 1 が root で読んでからユーザーを落とすので、`root:root 0600` のままでよい。

- [ ] **Step 2: journald の上限を書く**

`deploy/journald-linebot.conf`:

```ini
# /etc/systemd/journald.conf.d/linebot.conf
# アプリはファイルログを持たず journald に任せる。ディスクを食い尽くさないよう上限を置く。
[Journal]
SystemMaxUse=200M
```

- [ ] **Step 3: env の雛形を書く**

`deploy/linebot.env.example`:

```bash
# /etc/linebot.env (root:root, 0600) に置く。実際の値はパスワードマネージャに保管し、VM 再作成時はそこから復元する。
# トークンとシークレットは LINE Developers コンソールで再発行もできる。
LINE_BOT_CHANNEL_TOKEN=
LINE_BOT_CHANNEL_SECRET=
# ログレベルを引き上げるときだけ書く (既定は INFO)
# LOGGING_LEVEL_INSIDERGAME=DEBUG
# 切替前の検証で LINE 返信 API をスタブへ向けるときだけ書く。切替時に必ず消す (deploy/cutover.md)
# LINE_BOT_API_END_POINT=http://127.0.0.1:18080/
```

- [ ] **Step 4: sudoers を書く**

`deploy/sudoers-linebot`:

```text
# /etc/sudoers.d/linebot (root:root, 0440)
# デプロイは linebot ユーザーで SSH し、更新スクリプトだけを root で実行する。
# スクリプト本体は root 所有 0755 で /usr/local/bin に置く (linebot が書き換えられると権限昇格になる)。
linebot ALL=(root) NOPASSWD: /usr/local/bin/linebot-release.sh
```

- [ ] **Step 5: コミット**

```bash
git add deploy/linebot.service deploy/journald-linebot.conf deploy/linebot.env.example deploy/sudoers-linebot
git commit -m "feat: linebot.service と journald・env・sudoers の設定ファイルを追加する"
```

---

### Task 5: Caddyfile

**Files:**
- Create: `deploy/Caddyfile`

**Interfaces:**
- Consumes: Spring Boot が `127.0.0.1:8081` で待つこと (Task 4)、`/actuator/health` (Task 2)
- Produces: `https://bot.<domain>/{callback,callapi,specialvillage,actuator/health}` だけを流す公開面。`/callapi` 30 req/分/IP、`/specialvillage` 10 req/分/IP (超過 429) + ボディ 2 MB (超過 413)、それ以外 404

ドメインは未取得のため `bot.example.com` を書き、Task 8 の手順書で VM に置くときに `sed` で置き換える。ローカルに Caddy はないので、構文の検証 (`caddy validate`) は Task 10 で VM 上で行う。

- [ ] **Step 1: Caddyfile を書く**

`deploy/Caddyfile`:

```caddyfile
{
	# caddy-ratelimit は非標準ディレクティブなので、標準ディレクティブとの順序を宣言しないと使えない
	order rate_limit before basicauth
}

# /etc/caddy/Caddyfile に置くときに bot.example.com を取得したドメインへ置き換える (deploy/setup.md)
bot.example.com {
	# Spring Boot へ流すのはこの 4 パスだけ。他は Spring Boot に届かせず Caddy が 404 を返す
	@app path /callback /callapi /specialvillage /actuator/health
	@specialvillage path /specialvillage

	# /callapi と /specialvillage は署名検証のない状態変更 API。
	# 1 IP からの FIFO 追い出し (村レジストリ上限 50 / 30) を遅らせる。防止はできない。
	# /callback は LINE の署名検証があるので制限しない。
	# 閾値はブラウザのフォーム 1 人分の操作 (OPTIONS プリフライト込みで 1 操作 2 リクエスト) が届かない値。
	rate_limit {
		zone callapi {
			match {
				path /callapi
			}
			key {http.request.remote.host}
			events 30
			window 1m
		}
		zone specialvillage {
			match {
				path /specialvillage
			}
			key {http.request.remote.host}
			events 10
			window 1m
		}
	}

	# 1 村 100 メッセージ × 5000 文字 (UTF-16 単位) が仕様上の最大。日本語は UTF-8 で 1 単位 3 バイトなので
	# 本文は最大 1.5 MB になる (「あ」5000 文字 × 100 件の JSON は実測 1,500,313 バイト)。1 MB では既存 API が
	# 受理する入力を Caddy が拒否してしまうため 2 MB。超過は 413
	request_body @specialvillage {
		max_size 2MB
	}

	handle @app {
		reverse_proxy 127.0.0.1:8081
	}

	handle {
		respond 404
	}
}
```

- [ ] **Step 2: コミット**

```bash
git add deploy/Caddyfile
git commit -m "feat: 4 パスだけを流しレート制限を掛ける Caddyfile を追加する"
```

---

### Task 6: 検証スクリプト (LINE API スタブ、署名付き callback、レート制限計測)

**Files:**
- Create: `deploy/verify/line_api_stub.py`
- Create: `deploy/verify/post_callback.py`
- Test: `deploy/verify/test_post_callback.py`
- Create: `deploy/verify/ratelimit_check.sh`
- Modify: `.github/workflows/ci.yml` (Python テストの実行)

**Interfaces:**
- Consumes: `LINE_BOT_API_END_POINT` で Bot の返信先を差し替えられること (SDK の `line.bot.api-end-point`、Spring の relaxed binding で環境変数名は `LINE_BOT_API_END_POINT`)。署名は `X-Line-Signature: base64(HMAC-SHA256(channel secret, raw body))`
- Produces:
  - `python3 deploy/verify/line_api_stub.py [port=18080]` : `127.0.0.1:<port>` で `POST /v2/bot/message/reply` を受け、本文の JSON を標準出力へ整形表示、200 `{}` を返す
  - `LINE_BOT_CHANNEL_SECRET=... python3 deploy/verify/post_callback.py <url> text <userId> <本文>` / `postback <userId> <data>` / `sticker <userId>`、任意で末尾に `--bad-signature`。標準出力に `status=<code> elapsed_ms=<数値>`
  - `bash deploy/verify/ratelimit_check.sh <base-url> <path> <回数>` : 指定回数 GET/OPTIONS を送り、各ステータスの件数を出す
- Task 9 の手順書はこれらのコマンドをそのまま使う

- [ ] **Step 1: 失敗するテストを書く**

`deploy/verify/test_post_callback.py`:

```python
"""post_callback.py の署名とイベント組み立てを固定する.

実行: python3 -m unittest discover -s deploy/verify -p 'test_*.py'
"""
import unittest

from post_callback import build_body, sign


class SignTest(unittest.TestCase):
    def test_signature_is_base64_of_hmac_sha256_over_the_raw_body(self):
        # 既知ベクトル: HMAC-SHA256("secret", "body") を base64 したもの
        self.assertEqual("3EaYNVf+oSe0OvchRn65s/3iM4/j4U9RlSqoR4wT01U=", sign("secret", b"body"))

    def test_signature_changes_with_the_secret(self):
        self.assertNotEqual(sign("secret", b"body"), sign("secretx", b"body"))


class BuildBodyTest(unittest.TestCase):
    def test_text_event_carries_user_and_text(self):
        body = build_body("text", "U1", "お題")
        event = body["events"][0]
        self.assertEqual("message", event["type"])
        self.assertEqual("text", event["message"]["type"])
        self.assertEqual("お題", event["message"]["text"])
        self.assertEqual("U1", event["source"]["userId"])
        self.assertEqual("user", event["source"]["type"])
        self.assertTrue(event["replyToken"])

    def test_postback_event_carries_data(self):
        event = build_body("postback", "U1", "1234")["events"][0]
        self.assertEqual("postback", event["type"])
        self.assertEqual("1234", event["postback"]["data"])

    def test_sticker_event_has_sticker_ids(self):
        event = build_body("sticker", "U1", None)["events"][0]
        self.assertEqual("message", event["type"])
        self.assertEqual("sticker", event["message"]["type"])
        self.assertTrue(event["message"]["packageId"])
        self.assertTrue(event["message"]["stickerId"])

    def test_unknown_kind_is_rejected(self):
        with self.assertRaises(ValueError):
            build_body("image", "U1", None)


if __name__ == "__main__":
    unittest.main()
```

- [ ] **Step 2: テストが失敗することを確認する**

```bash
python3 -m unittest discover -s deploy/verify -p 'test_*.py'
```

Expected: `ModuleNotFoundError: No module named 'post_callback'`。

- [ ] **Step 3: post_callback.py を書く**

`deploy/verify/post_callback.py`:

```python
#!/usr/bin/env python3
"""署名付きの LINE webhook を組み立てて POST し、応答コードと所要時間を出す.

使い方 (環境変数 LINE_BOT_CHANNEL_SECRET が必要):
  post_callback.py <url> text <userId> <本文>        テキストメッセージ
  post_callback.py <url> postback <userId> <data>     ポストバック
  post_callback.py <url> sticker <userId>             スタンプ
末尾に --bad-signature を付けると署名を壊して送る (拒否されることの確認用)。

所要時間は接続開始から応答完了までで、LINE の 2 秒制限に対するマージンを見るために出す。
返信の中身は Bot の LINE_BOT_API_END_POINT を line_api_stub.py へ向けて、スタブ側で読む。
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time
import urllib.error
import urllib.request

REPLY_TOKEN = "verify-reply-token"


def sign(secret: str, body: bytes) -> str:
    digest = hmac.new(secret.encode("utf-8"), body, hashlib.sha256).digest()
    return base64.b64encode(digest).decode("ascii")


def build_body(kind: str, user_id: str, arg):
    event = {
        "replyToken": REPLY_TOKEN,
        "timestamp": int(time.time() * 1000),
        "source": {"type": "user", "userId": user_id},
        "mode": "active",
    }
    if kind == "text":
        event["type"] = "message"
        event["message"] = {"type": "text", "id": "1", "text": arg}
    elif kind == "postback":
        event["type"] = "postback"
        event["postback"] = {"data": arg}
    elif kind == "sticker":
        event["type"] = "message"
        event["message"] = {"type": "sticker", "id": "1", "packageId": "1", "stickerId": "1"}
    else:
        raise ValueError(f"unknown event kind: {kind}")
    return {"destination": "Uverify", "events": [event]}


def post(url: str, secret: str, body: bytes, bad_signature: bool = False):
    signature = sign(secret + ("x" if bad_signature else ""), body)
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "X-Line-Signature": signature},
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            status = response.status
    except urllib.error.HTTPError as error:
        status = error.code
    return status, (time.perf_counter() - started) * 1000


def main(argv):
    bad_signature = "--bad-signature" in argv
    args = [a for a in argv if a != "--bad-signature"]
    if len(args) < 3:
        print(__doc__, file=sys.stderr)
        return 2
    url, kind, user_id = args[0], args[1], args[2]
    arg = args[3] if len(args) > 3 else None
    secret = os.environ["LINE_BOT_CHANNEL_SECRET"]

    body = json.dumps(build_body(kind, user_id, arg), ensure_ascii=False).encode("utf-8")
    status, elapsed_ms = post(url, secret, body, bad_signature)
    print(f"status={status} elapsed_ms={elapsed_ms:.0f}")
    return 0 if status == 200 else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
```

- [ ] **Step 4: テストが通ることを確認する**

```bash
python3 -m unittest discover -s deploy/verify -p 'test_*.py'
```

Expected: `Ran 6 tests ... OK`。

- [ ] **Step 5: LINE API スタブを書く**

`deploy/verify/line_api_stub.py`:

```python
#!/usr/bin/env python3
"""LINE Messaging API の返信エンドポイントを模したスタブ.

Bot の /etc/linebot.env に LINE_BOT_API_END_POINT=http://127.0.0.1:18080/ を書いて再起動すると、
Bot が送る ReplyMessage はここへ届く。本文を整形して標準出力へ出し、200 {} を返す。
外部へは一切送らない。切替前の機能検証 (返信内容の確認) に使う。

使い方: python3 line_api_stub.py [port]   (既定 18080)
"""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer


class ReplyRecorder(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length)
        print(f"--- {self.command} {self.path}")
        try:
            print(json.dumps(json.loads(body), ensure_ascii=False, indent=2))
        except ValueError:
            print(body.decode("utf-8", errors="replace"))
        sys.stdout.flush()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(b"{}")

    def log_message(self, format, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18080
    print(f"listening on 127.0.0.1:{port}", flush=True)
    HTTPServer(("127.0.0.1", port), ReplyRecorder).serve_forever()
```

- [ ] **Step 6: スタブと post_callback を手元で突き合わせる**

スタブ自体の動作確認 (Bot なしで、スタブへ直接 POST する)。

```bash
python3 deploy/verify/line_api_stub.py 18080 &
stub_pid=$!
sleep 1
curl -s -X POST http://127.0.0.1:18080/v2/bot/message/reply -H 'Content-Type: application/json' -d '{"replyToken":"t","messages":[{"type":"text","text":"こんにちは"}]}'
kill "$stub_pid"
```

Expected: スタブ側に `--- POST /v2/bot/message/reply` と整形された JSON (`"text": "こんにちは"`) が出て、curl は `{}` を返す。

- [ ] **Step 7: レート制限計測スクリプトを書く**

`deploy/verify/ratelimit_check.sh`:

```bash
#!/usr/bin/env bash
# 使い方: ratelimit_check.sh <base-url> <path> <回数> [method=GET]
#   例: ratelimit_check.sh https://bot.example.com '/callapi?message=x&userId=verify' 35
#       ratelimit_check.sh https://bot.example.com '/callapi?message=x&userId=verify' 35 OPTIONS
# 指定回数だけ連続で送り、HTTP ステータスごとの件数を出す。
# /callapi は 30 req/分/IP なので 35 回送れば 200 が 30、429 が 5 になるはず。
# OPTIONS (CORS プリフライト) もレート制限に数えられることを同じ手順で確かめる。
set -euo pipefail

base="${1:?base-url}"
path="${2:?path}"
count="${3:?count}"
method="${4:-GET}"

for ((i = 1; i <= count; i++)); do
  curl -s -o /dev/null -w '%{http_code}\n' -X "$method" \
    -H 'Origin: https://insidergametool.netlify.app' \
    -H 'Access-Control-Request-Method: GET' \
    "$base$path"
done | sort | uniq -c
```

```bash
chmod +x deploy/verify/ratelimit_check.sh deploy/verify/post_callback.py deploy/verify/line_api_stub.py
```

- [ ] **Step 8: CI に Python テストを組み込む**

`.github/workflows/ci.yml` の `Test the release script` の直後に追加する。

```yaml
      - name: Test the verification scripts
        run: python3 -m unittest discover -s deploy/verify -p 'test_*.py'
```

- [ ] **Step 9: コミットして push**

```bash
git add deploy/verify .github/workflows/ci.yml
git commit -m "feat: 署名付き callback と LINE API スタブによる切替前検証スクリプトを追加する"
git push
gh run watch
```

Expected: `Test the verification scripts` が `OK`。

---

### Task 7: deploy job

**Files:**
- Modify: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: artifact `insider-game-bot-jar` (Task 1)、VM 上の `/usr/local/bin/linebot-release.sh` と sudoers (Task 3, 4)、GitHub Secrets `DEPLOY_HOST` (VM の公開 IP)、`DEPLOY_SSH_KEY` (ed25519 秘密鍵)、`DEPLOY_HOST_KEY` (`ssh-keyscan -t ed25519 <IP>` の出力 1 行。`known_hosts` 形式で、先頭のホスト名は `DEPLOY_HOST` と同じ IP)
- Produces: `master` への push で `build` → `deploy` が走り、job の成否 = VM 上の更新スクリプトの終了コード。古い SHA の run は skip して成功終了

Secrets が未登録の間 (Task 10 まで) deploy job は `Configure SSH` で失敗する。それでよい。この Task では YAML を完成させ、`master` への push で **`Confirm this commit is still the head of master` まで進むこと**を確認する。

- [ ] **Step 1: deploy job を追加する**

`.github/workflows/ci.yml` の `jobs:` 配下、`build` の後に追加する。

```yaml
  deploy:
    name: Deploy to OCI
    needs: build
    if: github.event_name == 'push' && github.ref == 'refs/heads/master'
    runs-on: ubuntu-latest
    # 連続 push で並走させない。実行中の job は cancel しない (VM 側の更新を途中で打ち切らせない)。
    # GitHub は group 内の順序を保証しないので、排他を取った後に master の最新 SHA を再確認する。
    concurrency:
      group: deploy-production
      cancel-in-progress: false
    steps:
      - name: Confirm this commit is still the head of master
        id: head
        run: |
          head="$(git ls-remote "https://github.com/${GITHUB_REPOSITORY}.git" refs/heads/master | cut -f1)"
          if [ "$head" != "$GITHUB_SHA" ]; then
            echo "master is now $head; skipping deploy of $GITHUB_SHA"
            echo "skip=true" >> "$GITHUB_OUTPUT"
          fi

      - name: Download release
        if: steps.head.outputs.skip != 'true'
        uses: actions/download-artifact@v8
        with:
          name: insider-game-bot-jar
          path: dist

      # ホスト鍵を固定する。固定しないと DNS や経路を乗っ取られたときに jar を偽の VM へ送り込まれる。
      - name: Configure SSH
        if: steps.head.outputs.skip != 'true'
        env:
          DEPLOY_SSH_KEY: ${{ secrets.DEPLOY_SSH_KEY }}
          DEPLOY_HOST_KEY: ${{ secrets.DEPLOY_HOST_KEY }}
        run: |
          test -n "$DEPLOY_SSH_KEY" && test -n "$DEPLOY_HOST_KEY"
          mkdir -p ~/.ssh
          chmod 700 ~/.ssh
          printf '%s\n' "$DEPLOY_SSH_KEY" > ~/.ssh/id_ed25519
          chmod 600 ~/.ssh/id_ed25519
          printf '%s\n' "$DEPLOY_HOST_KEY" > ~/.ssh/known_hosts
          chmod 644 ~/.ssh/known_hosts

      - name: Upload release to the VM
        if: steps.head.outputs.skip != 'true'
        env:
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
        run: |
          # 稼働中の jar (releases/) ではなく incoming/ へ置く。同じ commit の re-run でも本番の jar を上書きしない
          target="linebot@${DEPLOY_HOST}"
          ssh -o StrictHostKeyChecking=yes "$target" "rm -rf /opt/linebot/incoming/${GITHUB_SHA} && mkdir -p /opt/linebot/incoming/${GITHUB_SHA}"
          scp -o StrictHostKeyChecking=yes dist/insider-game-bot.jar dist/insider-game-bot.jar.sha256 "$target:/opt/linebot/incoming/${GITHUB_SHA}/"

      # 検証 → 昇格 → 再起動 → ヘルスチェック → 復旧 → 世代管理は VM 上の 1 本が flock 付きで完走する。
      # この step の終了コードがそのまま job の成否になる。
      - name: Release on the VM
        if: steps.head.outputs.skip != 'true'
        env:
          DEPLOY_HOST: ${{ secrets.DEPLOY_HOST }}
        run: ssh -o StrictHostKeyChecking=yes "linebot@${DEPLOY_HOST}" "sudo /usr/local/bin/linebot-release.sh ${GITHUB_SHA}"
```

- [ ] **Step 2: `actions/download-artifact@v8` が存在することを確認する**

```bash
gh api repos/actions/download-artifact/tags --jq '.[].name' | head -3
```

Expected: `v8` が含まれる。含まれなければ最新のメジャータグに直す。

- [ ] **Step 3: YAML の構文を確認する**

```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml')); print('ok')"
```

Expected: `ok` (この Mac の python3 には PyYAML が入っている)。

- [ ] **Step 4: コミットして push し、deploy job が SHA 確認まで進むことを見る**

```bash
git add .github/workflows/ci.yml
git commit -m "ci: master への push で VM へ配備する deploy job を追加する"
git push
gh run watch
```

Expected: `build` 成功。`deploy` は `Confirm this commit is still the head of master` を通り、`Download release` を通り、`Configure SSH` で `test -n` に失敗して止まる (Secrets 未登録のため)。Task 10 で Secrets を登録すると通る。

- [ ] **Step 5: skip の経路を確認する**

もう 1 つ空コミットを push し、**先の run** を re-run する。

```bash
git commit --allow-empty -m "ci: deploy job の skip 経路を確認するための空コミット"
git push
gh run list --branch master --limit 3
```

先の (1 つ前の) run の ID を控え、`gh run rerun <run-id>` を実行し、`gh run view <run-id> --log | grep 'skipping deploy'` に `master is now ...; skipping deploy of ...` が出ることを確認する。deploy job は成功終了する。

---

### Task 8: セットアップ手順書 `deploy/setup.md`

**Files:**
- Create: `deploy/setup.md`

**Interfaces:**
- Consumes: Task 3〜6 の全ファイル
- Produces: VM を 0 から再作成できる手順。Task 10 はこれを上から実行する。VM が失われた場合の再作成手順でもある

- [ ] **Step 1: 手順書を書く**

`deploy/setup.md` を次の内容で作る。**ハードラップしない。** 手順は上から順にコピー & ペーストで完了する粒度で、VM 再作成時にそのまま使う。

````markdown
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
[VM]  sudo mkdir -p /etc/systemd/journald.conf.d
[VM]  sudo install -o root -g root -m 0644 /tmp/journald-linebot.conf /etc/systemd/journald.conf.d/linebot.conf
[VM]  sudo systemctl restart systemd-journald
[VM]  sudo systemd-analyze verify /etc/systemd/system/linebot.service
```

期待: `visudo -cf` が `parsed OK`、`systemd-analyze verify` が何も出力しない (jar がまだ無いことによる警告は出てよい)。

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
[VM]  sudo systemctl enable linebot
[VM]  sudo systemctl status linebot --no-pager
[VM]  curl -s http://127.0.0.1:8081/actuator/health
```

期待: deploy job が成功し、`status` が `active (running)`、health が `{"status":"UP"}`。`enable` は初回だけ (以降の再起動は更新スクリプトが行い、VM 再起動時は enable により自動起動する)。

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

§0 でインスタンスを作り直し (公開 IP が変わる)、§1〜§13 を上から実行する。`/etc/linebot.env` の値はパスワードマネージャから復元する。IP が変わるので §0 の A レコード、§10 の `DEPLOY_HOST` と `DEPLOY_HOST_KEY` を更新する。完了後に [cutover.md](cutover.md) の「切替後確認」をやり直す。
````

- [ ] **Step 2: リンクと参照ファイルが実在することを確認する**

```bash
grep -o '\](\.\./[^)]*\|\]([a-z./-]*\.md' deploy/setup.md
ls deploy/linebot-release.sh deploy/linebot.service deploy/journald-linebot.conf deploy/sudoers-linebot deploy/Caddyfile
```

Expected: 参照される `../docs/superpowers/specs/...` と `cutover.md` (Task 9 で作成) 以外に不明なリンクがなく、`ls` がすべて成功する。

- [ ] **Step 3: コミット**

```bash
git add deploy/setup.md
git commit -m "docs: VM を再作成できる粒度のセットアップ手順書を追加する"
```

---

### Task 9: カットオーバー手順書 `deploy/cutover.md`

**Files:**
- Create: `deploy/cutover.md`

**Interfaces:**
- Consumes: Task 6 の検証スクリプト、Task 8 の手順書
- Produces: 切替前検証 14 項目・カットオーバー・切替後確認・1 か月の監視・ロールバック・PAYG 判断・Heroku 解約の手順。Task 10〜12 はこれを実行する

- [ ] **Step 1: 手順書を書く**

`deploy/cutover.md` を次の内容で作る。ハードラップしない。

````markdown
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
````

- [ ] **Step 2: コマンド中の URL エンコードを検証する**

```bash
python3 -c "
import urllib.parse as u
for s in ['お題','すいか','@取得','@配布','@特殊','@逆村','@わーわーず']:
    print(s, u.quote(s))"
```

Expected: 出力が手順書 A-1 のエンコード済み文字列と一致する (`%E3%81%8A%E9%A1%8C` = お題、`%40%E5%8F%96%E5%BE%97` = @取得 など)。違えば手順書を直す。

- [ ] **Step 3: コミットして push**

```bash
git add deploy/cutover.md
git commit -m "docs: 切替前検証 14 項目とカットオーバー・ロールバック手順書を追加する"
git push
```

---

### Task 10: VM プロビジョニングと初回配備、切替前検証

**前提:** ap-tokyo-1 で A1 Flex が起動していること。起動していなければこの Task 以降は着手せず、Heroku のまま運用を続ける。

**Files:**
- Modify: `deploy/setup.md` (§4 の JDK リリース名の表)
- Modify: `deploy/cutover.md` (A の記録表)、`deploy/Caddyfile` (閾値が変わった場合)

**Interfaces:**
- Consumes: Task 8, 9 の手順書
- Produces: `https://bot.<domain>` で `master` の最新が動き、A の 14 項目が全部通っている VM。GitHub Secrets 3 つが登録済み

- [ ] **Step 1: [ユーザー] setup.md §0 を済ませる**

Oracle アカウント (ap-tokyo-1)、A1 インスタンス、security list、ドメインと A レコード、デプロイ鍵の生成。

- [ ] **Step 2: setup.md §1〜§9 を上から実行する**

各節の「期待」を満たしていることを 1 つずつ確認する。§4 で使った JDK リリース名を `deploy/setup.md` の表に書く。

- [ ] **Step 3: setup.md §10 で GitHub Secrets を登録し、§11 で初回配備する**

```bash
gh run watch
```

Expected: deploy job 成功、VM 上で `{"status":"UP"}`。

- [ ] **Step 4: setup.md §12〜§13 で Monitoring と Alarm を確認・設定する**

`MemoryUtilization` が出ていなければ、cutover.md E の判断をユーザーに求めてから進む。

- [ ] **Step 5: cutover.md A の 14 項目を実行し、記録表を埋める**

7 の実ブラウザ操作はユーザーが公開フォームのリポジトリで行う。閾値を変えた場合は `deploy/Caddyfile` も同じ値に直す。

- [ ] **Step 6: スタブを外し、手順書の記入分をコミットする**

```bash
sudo grep -c API_END_POINT /etc/linebot.env   # VM 上で 0
git add deploy/setup.md deploy/cutover.md deploy/Caddyfile
git commit -m "docs: 切替前検証の実測値と JDK のリリース名を記録する"
git push
gh run watch
```

Expected: push による deploy job も成功する (スタブ解除後の再起動込み)。

---

### Task 11: カットオーバーと 1 か月の監視

**Files:**
- Modify: `deploy/cutover.md` (B-8 の切替日)

- [ ] **Step 1: cutover.md B を実行する**

3, 4, 7 はユーザーが行う。6 の「フォームで特殊村 → LINE で参加 → `@配布`」まで通ったことを確認する。

- [ ] **Step 2: 切替日をコミットする**

```bash
git add deploy/cutover.md
git commit -m "docs: カットオーバーの実施日を記録する"
git push
```

- [ ] **Step 3: cutover.md C を 1 か月続ける**

異常があれば D (ロールバック) または E (PAYG) を実行する。

---

### Task 12: Heroku 解約、デフォルトブランチ切替、Heroku 固有ファイル撤去、docs 更新

**前提:** 切替日 + 1 か月が経過し、cutover.md C で問題がなかったこと。ユーザーが cutover.md F-1 (Heroku アプリ削除)、F-2 (Eco の Unsubscribe)、F-3 (デフォルトブランチを `master` へ) を済ませたこと。**解約前にこの Task のファイル削除をしてはいけない** (Heroku がロールバック先である間は `Procfile` が必要)。

**Files:**
- Delete: `Procfile`, `app.json`, `system.properties`
- Modify: `build.gradle:35` (Procfile へのコメント)
- Modify: `insider-game-bot/README.md:32-34`
- Modify: `docs/operations.md`
- Rewrite: `docs/roadmap.md`
- Modify: `deploy/setup.md` §8、`deploy/cutover.md` D (Heroku を参照している箇所)

- [ ] **Step 1: ユーザーの解約とデフォルトブランチ切替を確認する**

```bash
gh repo view --json defaultBranchRef --jq .defaultBranchRef.name
```

Expected: `master`。あわせてユーザーに、Heroku ダッシュボードでアプリが消えていること、Billing で Eco の subscription が **Unsubscribed** になっていることを確認してもらう (`heroku` CLI はこの Mac に入っていない)。

- [ ] **Step 2: Heroku 固有ファイルを削除する**

```bash
git rm Procfile app.json system.properties
```

- [ ] **Step 3: build.gradle のコメントを直す**

`build.gradle` の 35 行目 `// Procfileはbuild/libs/insider-game-bot-*.jarをワイルドカードで拾う` を次にする。

```groovy
// CI (.github/workflows/ci.yml) はbuild/libs/insider-game-bot-*.jarをワイルドカードで拾う
```

- [ ] **Step 4: insider-game-bot/README.md を直す**

32 行目の `ソースコードには書かず、環境変数（Herokuの場合はConfig Vars）で渡します。` を次にする。

```markdown
ソースコードには書かず、環境変数で渡します（本番では `/etc/linebot.env` を systemd が読みます）。
```

34 行目の `Herokuでは`Procfile`に従って`build/libs/insider-game-bot-*.jar`が起動します。` を次にする。

```markdown
本番では systemd の `linebot.service` が `/opt/linebot/current.jar` を起動します（[deploy/setup.md](../deploy/setup.md)）。
```

- [ ] **Step 5: docs/operations.md を書き換える**

「実行環境」「設定」「ビルドとデプロイ」「障害時の挙動」「デプロイ時の注意」「監視」の各節を次の内容に置き換える。「ローカルでの起動」「ログ」「役職画像カタログの URL」は変えない。

```markdown
## 実行環境

| 項目 | 値 |
| --- | --- |
| 言語ランタイム | Java 8（Temurin 8、aarch64） |
| フレームワーク | Spring Boot |
| ビルド | Gradle（マルチプロジェクト） |
| 稼働環境 | Oracle Cloud Always Free の VM.Standard.A1.Flex（ap-tokyo-1、2 OCPU / 12 GB、Ubuntu 24.04）1 台 |
| プロセス管理 | systemd（`linebot.service`、異常終了時は 5 秒後に再起動） |
| 公開 | Caddy が `https://bot.<domain>` で TLS を終端し、`/callback` `/callapi` `/specialvillage` `/actuator/health` だけを loopback の Spring Boot へ流す。他のパスは 404 |
| プロセス数 | **1（複数インスタンス不可）** |

プロセスを 2 つ以上に増やすと、村がインスタンス間で共有されず、参加者が別のインスタンスへ振り分けられた時点で村を見つけられなくなります（[architecture.md](architecture.md) の「単一プロセス前提」参照）。**スケールアウトは構成として禁止です。**Always Free の枠（1,500 OCPU 時間 / 月）も 2 台目を許しません。

VM の構築手順は [deploy/setup.md](../deploy/setup.md)、切替とロールバックは [deploy/cutover.md](../deploy/cutover.md) にあります。

## 設定

秘密情報はソースコードに書かず、VM 上の `/etc/linebot.env`（`root:root`、`0600`）に置いて systemd が環境変数として渡します。値はパスワードマネージャにも保管し、VM 再作成時はそこから復元します。

| 環境変数 | 必須 | 説明 |
| --- | --- | --- |
| `LINE_BOT_CHANNEL_TOKEN` | ○ | チャネルアクセストークン |
| `LINE_BOT_CHANNEL_SECRET` | ○ | チャネルシークレット。webhook の署名検証に使う |
| `LOGGING_LEVEL_INSIDERGAME` | | ログレベル。既定は INFO |
| `LINE_BOT_API_END_POINT` | | 返信 API の接続先。切替前の検証でスタブへ向けるときだけ設定し、本番では**設定しない** |

待ち受けは `--server.address=127.0.0.1 --server.port=8081` を systemd unit が固定で渡します。LINE Developers コンソール側では、Webhook URL を `https://bot.<domain>/callback` に設定します。

## ビルドとデプロイ

CI は GitHub Actions で、`master` と `develop` への push、およびプルリクエストで実行されます。

- **本番と同じ Java 8 でビルドします。**ビルド JDK を揃えないと、ソース互換性の設定だけでは実行時の差分を拾えないためです。
- テストの実行、実行可能 jar と sha256 の生成、VM 上の更新スクリプトと検証スクリプトのテストを行います。
- テストレポートは成否にかかわらず成果物として保存されます。

**`master` への push は本番反映です。**`build` が通った jar を、ホスト鍵を固定した SSH で VM の `/opt/linebot/incoming/<commit>/` へ送り、VM 上の更新スクリプトが sha256 を検証してから `/opt/linebot/releases/<commit>/` へ昇格し、`current.jar` を差し替えて再起動し、`GET /actuator/health` が `{"status":"UP"}` を返すまで最長 60 秒待ちます。通らなければ直前の jar へ自動で戻します。同時に走った deploy は直列化され、`master` の最新でなくなった commit は何もせず成功終了します。手動の承認ステップはありません。

## 障害時の挙動

| 事象 | 挙動 | 対応 |
| --- | --- | --- |
| アプリケーションの再起動・再デプロイ | **すべての村が消える** | 利用者が村を作り直す。デプロイは利用者のいない時間帯に行う |
| デプロイした jar が起動しない | 更新スクリプトが直前の jar へ戻して再起動し、deploy job を失敗させる。戻し先がなければ停止する | Actions のログを見て修正し、再 push する |
| VM の停止（Always Free のアイドル回収） | 7 日間のアイドル判定でメール通知、その 1 週間後に停止。削除ではない | 通知が届いたら [deploy/cutover.md](../deploy/cutover.md) の PAYG の手順へ。停止後はコンソールから起動できる |
| VM の喪失 | Bot が無応答になり、外形監視が通知する | [deploy/setup.md](../deploy/setup.md) で再作成する。秘密情報はパスワードマネージャから復元する |
| 役職画像カタログの取得失敗 | 前回取得した一覧で配布を継続。一度も取得できていなければ既定画像。5 分後に再取得 | 継続して失敗する場合は URL の失効を疑う（下記）。再起動すると一覧が消え、次に取得できるまで既定画像になる |
| お題辞書の読み込み失敗 | お題の抽選が `null` を返し、**そのまま応答に現れる**。お題の自動取得は「お題は『null』です。」を返し、ランダム村はお題が `null` のまま作られる。エラーにはならないため利用者は気付けない | 起動時の ERROR ログで判別する。同梱リソースの欠落を疑い、ビルド成果物を確認する |
| 返信 API の失敗 | ログへ記録し、利用者へは何も返らない | 利用者は同じ操作を再送する。状態は変更済みの場合がある |
| 村が上限を超えた | 古い村から消える | 仕様。[architecture.md](architecture.md) 参照 |
| `/callapi` `/specialvillage` への大量リクエスト | Caddy が 1 IP あたり 30 / 10 req/分を超えた分に 429 を返す。防止ではなく遅延 | 複数 IP からの追い出しは防げない（[architecture.md](architecture.md) の「既知の制約」） |

## デプロイ時の注意

**進行中の村は再起動で消えます。**`master` への push はそのまま本番反映なので、ゲームが行われていない時間帯に push してください。これは受け入れている副作用であり、回避手段はありません（状態の永続化は非目的）。

## 監視

| 監視 | 内容 | 通知先 |
| --- | --- | --- |
| OCI Alarm | `MemoryUtilization` が 20% を下回る、または指標が欠測する（エージェント停止・VM 停止） | メール |
| 外形監視 | `GET https://bot.<domain>/actuator/health` を 5 分間隔で確認 | メール |
| Oracle からのメール | アイドル判定の通知。届いたら 1 週間以内に PAYG へ上げる（[deploy/cutover.md](../deploy/cutover.md)） | メール |

利用者からの報告（Bot の応答に含まれる意見フォーム）も引き続き検知経路です。
```

- [ ] **Step 6: docs/roadmap.md を書き換える**

移行の節を消し、残る課題だけにする。ファイル全体を次にする。

```markdown
# 今後の計画

このページは、**まだ実施していない**事柄を記録します。ここに書かれた内容はいずれも現状のシステムには反映されていません。現状は [overview.md](overview.md) 以下の各文書を参照してください。

Oracle Cloud Always Free への移行は実施済みで、現状は [operations.md](operations.md) に記述しています。設計の経緯は [2026-08-22 LineBot を Oracle Cloud Always Free へ移行する設計](superpowers/specs/2026-08-22-linebot-oracle-cloud-migration-design.md) に残しています。

## 課題として認識しているが、計画していないもの

[architecture.md](architecture.md) の「既知の制約」に挙げた次の項目は、問題として認識していますが、**着手の計画はありません**。

| 項目 | 解消に必要なこと |
| --- | --- |
| 公開 API が無認証 | 認証の導入。外部フォーム側の改修を伴うプロダクト判断。リバースプロキシのレート制限とリクエストサイズ上限は移行時に入れたが、防止ではなく遅延にとどまる |
| 単一プロセス前提 | 状態の外部化。永続化を持たないという設計判断の見直し |
| Spring Boot 2.1 系の保守終了 | フレームワークと推移的依存（Tomcat、Jackson 等）の更新。OS と JDK の更新では解消しない |
```

- [ ] **Step 7: 運用手順書から Heroku を外す**

`deploy/setup.md` §8 の冒頭の文 (Heroku の Config Vars と `heroku config` コマンドを参照している 1 行) を次にする。

```markdown
値はパスワードマネージャに保管してあるものを使う。トークンとシークレットは LINE Developers コンソールで再発行もできる。
```

`deploy/cutover.md` の `## D. ロールバック (Heroku へ戻す)` の直後に次の 1 行を足す。

```markdown
**Heroku は解約済みのため、この節は実行できない。** 移行時の記録として残す。
```

- [ ] **Step 8: Heroku への参照が残っていないことを確認する**

```bash
grep -rn -i 'heroku' --include='*.md' --include='*.gradle' --include='*.yml' --include='*.json' . | grep -v 'docs/superpowers\|LEARNINGS\|deploy/cutover.md\|deploy/human-steps.md\|deploy/setup.md\|docs/roadmap.md'
grep -n '`PORT`' docs/operations.md insider-game-bot/README.md README.md
```

Expected: どちらも 0 件。除外した 4 文書は Heroku を参照するのが正しいので対象外にしている。`deploy/cutover.md` は D 節 (ロールバック) と F 節 (解約手順)、`deploy/human-steps.md` は Phase 8 (解約) と別枠 (ロールバック)、`deploy/setup.md` は「A1 が取れるまで Heroku のまま」という移行の前提、`docs/roadmap.md` は移行の経緯と移行前後の対比表。なお `refactor-instructions.md` は 2026-09-08 に削除済みなので除外指定から外した。

- [ ] **Step 9: ビルドが通ることを確認する**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@11 ./gradlew --no-daemon check
```

Expected: BUILD SUCCESSFUL。

- [ ] **Step 10: コミットして push**

```bash
git add build.gradle insider-game-bot/README.md docs/operations.md docs/roadmap.md deploy/setup.md deploy/cutover.md
git status --short   # Procfile / app.json / system.properties が D (git rm 済み) で並ぶこと
git commit -m "chore: Heroku 解約に伴い Heroku 固有ファイルを撤去し docs を OCI 構成へ更新する"
git push
gh run watch
```

Expected: build と deploy が成功し、VM の Bot がこの commit の jar で動いている (`ssh linebot@<IP> readlink /opt/linebot/current.jar` に commit SHA が含まれる)。

- [ ] **Step 11: `3.0` ブランチの扱いを決める**

`3.0` は Heroku の GitHub 連携先だった。Heroku が消えた後は `master` と乖離した履歴を持つだけなので、ユーザーの判断で削除する。

**この制約は 2026-09-08 に解消済み。** 以前は `MessageConst` の画像 URL が `3.0` を焼き込んでいたため `3.0` を削除できなかったが、参照先を `master` へ移し、ブランチ名は `ILLUSTRATION_URL_PREFIX` の 1 箇所だけに書く形へ直した。`3.0` の削除は画像に影響しない。

```bash
git push origin --delete 3.0
```

以降の開発フローは `future` → PR → `master`。
