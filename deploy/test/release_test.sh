#!/usr/bin/env bash
# deploy/linebot-release.sh の振る舞いを、systemctl と curl を偽物に差し替えて確かめる.
# Linux 専用 (flock / sha256sum を使う)。CI の build job と VM 上で実行する。
#
# jar は中身がそのまま「振る舞い」を表すテキストファイル:
#   GOOD    起動して 200 {"status":"UP"} を返す
#   BROKEN  起動しない (curl が失敗する)
#   HALFUP  本文は UP だが HTTP 503 (ステータスを見ていないと健康と誤判定する)
#   NOSTART systemctl restart 自体が失敗する
#   FLAKY   本文も HTTP 200 も正常だが curl 自体が失敗する (終了コードを見ていないと健康と誤判定する)
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
  FLAKY)  printf '{"status":"UP"}' > "$out"; printf 200; exit 7 ;;
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

# 更新スクリプトは revision を 40 桁の小文字 hex に限っているので、偽 SHA も 40 桁にする。
# rep40 は 1 文字を 40 回並べる (どのケースの世代かを一目で分かるようにするため)。
# 世代管理のテストは番号順が要るので、連番を 40 桁へゼロ詰めした seq_sha を使う。
rep40() { printf '%040d' 0 | tr 0 "$1"; }
seq_sha() { printf '%040d' "$1"; }

aaa="$(rep40 a)"      # 初回配備
bbb="$(rep40 b)"      # 2 回目の配備 (以降の戻し先)
ccc="$(rep40 c)"      # 起動しない jar
ddd="$(rep40 d)"      # 戻し先が無い状態の壊れた jar
eee="$(rep40 e)"      # チェックサム不一致
halfup="$(rep40 f)"   # 本文は UP だが HTTP 503
nostart="$(rep40 1)"  # systemctl restart 自体が失敗する
zzz="$(rep40 9)"      # 6 世代ある状態でのチェックサム不一致
exc="$(rep40 2)"      # 保持数を超えても current / previous が残ることの検証
brk="$(rep40 3)"      # current のリンク先が失われた後の配備
flaky="$(rep40 4)"    # 本文も 200 も正常だが curl 自体が失敗する

released() { echo "$LINEBOT_HOME/releases/$1/insider-game-bot.jar"; }
current() { readlink "$LINEBOT_HOME/current.jar" 2>/dev/null || echo none; }
previous() { readlink "$LINEBOT_HOME/previous.jar" 2>/dev/null || echo none; }
systemctl_log() { cat "$LINEBOT_HOME/systemctl.log" 2>/dev/null | tr '\n' ';' || true; }
incoming_count() { ls -1 "$LINEBOT_HOME/incoming" | wc -l | tr -d ' '; }

# sudoers は linebot-release.sh の引数を制限できないので、40 桁 hex 以外を拒むのはこの検証だけ。
# 拒否せずに進むと incoming_dir が任意のディレクトリを指し、チェックサム失敗時の
# rm -rf がそこへ効く (root 実行なので /etc を消せてしまう)。
echo "# 40 桁 hex 以外の revision は何もせずに拒否する"
for bad in ../../../etc aaa; do
  fresh_home
  run_release "$bad"
  assert_eq "終了コードは 2 [$bad]" 2 "$status"
  assert_eq "releases に何も作らない [$bad]" "" "$(ls -1 "$LINEBOT_HOME/releases")"
  assert_eq "systemctl を呼ばない [$bad]" "" "$(systemctl_log)"
done

echo "# 初回配備"
fresh_home
add_release "$aaa" GOOD
run_release "$aaa"
assert_eq "初回は成功する" 0 "$status"
assert_eq "current が releases 配下の新 jar を指す" "$(released "$aaa")" "$(current)"
assert_eq "previous は無い" none "$(previous)"
assert_eq "incoming は空になる" 0 "$(incoming_count)"
assert_eq "restart を 1 回だけ呼ぶ" "restart linebot;" "$(systemctl_log)"

echo "# 2 回目の配備"
add_release "$bbb" GOOD
run_release "$bbb"
assert_eq "成功する" 0 "$status"
assert_eq "current が bbb" "$(released "$bbb")" "$(current)"
assert_eq "previous が aaa" "$(released "$aaa")" "$(previous)"

echo "# 同じ commit の再実行は previous を動かさない"
add_release "$bbb" GOOD
run_release "$bbb"
assert_eq "成功する" 0 "$status"
assert_eq "current は bbb のまま" "$(released "$bbb")" "$(current)"
assert_eq "previous は aaa のまま (bbb 自身にならない)" "$(released "$aaa")" "$(previous)"

echo "# 起動しない jar は直前へ戻す"
add_release "$ccc" BROKEN
run_release "$ccc"
assert_eq "job は失敗する" 1 "$status"
assert_eq "current が bbb に戻る" "$(released "$bbb")" "$(current)"
assert_eq "この home での restart は aaa, bbb, bbb 再実行, ccc, 戻しの 5 回。stop はしない" \
  "restart linebot;restart linebot;restart linebot;restart linebot;restart linebot;" "$(systemctl_log)"

echo "# 本文が UP でも HTTP 200 でなければ失敗として戻す"
add_release "$halfup" HALFUP
run_release "$halfup"
assert_eq "job は失敗する" 1 "$status"
assert_eq "current が bbb に戻る" "$(released "$bbb")" "$(current)"

echo "# systemctl restart 自体の失敗も戻す"
add_release "$nostart" NOSTART
run_release "$nostart"
assert_eq "job は失敗する" 1 "$status"
assert_eq "current が bbb に戻る" "$(released "$bbb")" "$(current)"

echo "# 戻し先が無ければ停止する"
fresh_home
add_release "$ddd" BROKEN
run_release "$ddd"
assert_eq "job は失敗する" 1 "$status"
assert_eq "restart の後に stop を呼ぶ" "restart linebot;stop linebot;" "$(systemctl_log)"

echo "# チェックサム不一致は何もしない"
fresh_home
add_release "$eee" GOOD
printf '%064d  insider-game-bot.jar\n' 0 > "$LINEBOT_HOME/incoming/$eee/insider-game-bot.jar.sha256"
run_release "$eee"
assert_eq "失敗する" 1 "$status"
assert_eq "current は作られない" none "$(current)"
assert_eq "releases に移されない" "" "$(ls -1 "$LINEBOT_HOME/releases")"
assert_eq "restart しない" "" "$(systemctl_log)"

echo "# 直近 5 世代だけ残す (current / previous は例外)"
fresh_home
for n in 1 2 3 4 5 6 7; do  # r1..r7
  add_release "$(seq_sha "$n")" GOOD
  run_release "$(seq_sha "$n")"
done
assert_eq "7 回目も成功" 0 "$status"
assert_eq "残るのは 5 世代" 5 "$(ls -1 "$LINEBOT_HOME/releases" | wc -l | tr -d ' ')"
assert_eq "current の r7 が残る" "$(released "$(seq_sha 7)")" "$(current)"
assert_eq "previous の r6 が残る" "$(released "$(seq_sha 6)")" "$(previous)"
assert_eq "current のリンク先が実在する" yes "$([[ -e "$LINEBOT_HOME/current.jar" ]] && echo yes || echo no)"
assert_eq "previous のリンク先が実在する" yes "$([[ -e "$LINEBOT_HOME/previous.jar" ]] && echo yes || echo no)"
if [[ -d "$LINEBOT_HOME/releases/$(seq_sha 1)" ]]; then r1=kept; else r1=gone; fi
assert_eq "最古の r1 は消える" gone "$r1"

echo "# releases が既に 6 世代ある状態でのチェックサム不一致も世代管理と incoming の後始末を行う"
fresh_home
export LINEBOT_KEEP_RELEASES=100
for n in 1 2 3 4 5 6; do  # q1..q6
  add_release "$(seq_sha "$n")" GOOD
  run_release "$(seq_sha "$n")"
done
assert_eq "6 回目も成功" 0 "$status"
assert_eq "prune を無効化した間は 6 世代とも残る" 6 "$(ls -1 "$LINEBOT_HOME/releases" | wc -l | tr -d ' ')"
unset LINEBOT_KEEP_RELEASES
before_log="$(systemctl_log)"
add_release "$zzz" GOOD
printf '%064d  insider-game-bot.jar\n' 0 > "$LINEBOT_HOME/incoming/$zzz/insider-game-bot.jar.sha256"
run_release "$zzz"
assert_eq "失敗する" 1 "$status"
assert_eq "current は q6 のまま (失敗した zzz にならない)" "$(released "$(seq_sha 6)")" "$(current)"
assert_eq "5 世代 + current/previous の例外まで刈られる" 5 "$(ls -1 "$LINEBOT_HOME/releases" | wc -l | tr -d ' ')"
assert_eq "incoming の zzz は残留せず消える" 0 "$(incoming_count)"
assert_eq "restart は呼ばれない (systemctl ログが変わらない)" "$before_log" "$(systemctl_log)"

echo "# current / previous が指す世代は、保持数を超えても残る"
# 上の「直近 5 世代」ケースでは current(r7) / previous(r6) がどちらも最新 5 世代に入るため、
# 例外の分岐が一度も効かない。保持数を 1 に絞って、例外がなければ消える状況を作る。
fresh_home
for n in 1 2 3 4 5 6 7; do
  add_release "$(seq_sha "$n")" GOOD
  run_release "$(seq_sha "$n")"
done
export LINEBOT_KEEP_RELEASES=1
add_release "$exc" GOOD
printf '%064d  insider-game-bot.jar\n' 0 > "$LINEBOT_HOME/incoming/$exc/insider-game-bot.jar.sha256"
run_release "$exc"
unset LINEBOT_KEEP_RELEASES
assert_eq "チェックサム不一致なので失敗する" 1 "$status"
assert_eq "保持数 1 でも current の r7 と previous の r6 の 2 世代が残る" 2 "$(ls -1 "$LINEBOT_HOME/releases" | wc -l | tr -d ' ')"
assert_eq "current のリンク先が実在する" yes "$([[ -e "$LINEBOT_HOME/current.jar" ]] && echo yes || echo no)"
assert_eq "previous のリンク先が実在する" yes "$([[ -e "$LINEBOT_HOME/previous.jar" ]] && echo yes || echo no)"

echo "# prune はこのスクリプトが作っていないエントリを消さない"
# ls の出力を行で分けているため、40 桁 hex 以外を削除対象にすると releases を丸ごと消しうる。
fresh_home
mkdir -p "$LINEBOT_HOME/releases/not-a-sha"
for n in 1 2 3 4 5 6 7; do
  add_release "$(seq_sha "$n")" GOOD
  run_release "$(seq_sha "$n")"
done
assert_eq "7 回目も成功" 0 "$status"
assert_eq "40 桁 hex でないエントリは消さない" yes "$([[ -d "$LINEBOT_HOME/releases/not-a-sha" ]] && echo yes || echo no)"
assert_eq "最古の r1 は消える" gone "$([[ -d "$LINEBOT_HOME/releases/$(seq_sha 1)" ]] && echo kept || echo gone)"

echo "# current のリンク先が失われているときは previous を上書きしない"
# 壊れた current を previous へ昇格させると、次に配備が失敗したときの戻し先が壊れた jar になり、
# 戻せたはずの旧版を失う。
fresh_home
add_release "$aaa" GOOD
run_release "$aaa"
add_release "$bbb" GOOD
run_release "$bbb"
assert_eq "この時点の previous は aaa" "$(released "$aaa")" "$(previous)"
rm -f "$(released "$bbb")"
add_release "$brk" GOOD
run_release "$brk"
assert_eq "成功する" 0 "$status"
assert_eq "previous は aaa のまま (リンク先を失った bbb を昇格させない)" "$(released "$aaa")" "$(previous)"
assert_eq "previous のリンク先が実在する" yes "$([[ -e "$LINEBOT_HOME/previous.jar" ]] && echo yes || echo no)"

echo "# 本文も HTTP 200 も正常でも curl 自体が失敗したら健康と見なさない"
# 契約は「本文だけでなく HTTP ステータスと curl の成否も見る」。終了コードを捨てていると、
# 転送エラーやタイムアウトで壊れた応答を配備成功として扱ってしまう。
fresh_home
add_release "$aaa" GOOD
run_release "$aaa"
add_release "$flaky" FLAKY
run_release "$flaky"
assert_eq "job は失敗する" 1 "$status"
assert_eq "current が aaa に戻る" "$(released "$aaa")" "$(current)"

if [[ "$failures" -ne 0 ]]; then
  echo "$failures failure(s)"
  exit 1
fi
echo "all passed"
