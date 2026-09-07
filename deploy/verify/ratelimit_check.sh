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
