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
if ! (cd "$incoming_dir" && sha256sum -c --quiet insider-game-bot.jar.sha256); then
  log "checksum verification failed for $sha"
  rm -rf "$incoming_dir"
  prune
  exit 1
fi

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
