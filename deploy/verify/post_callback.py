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
