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
