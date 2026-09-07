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
