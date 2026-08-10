# line-bot-api-client

LINE Messaging APIを呼び出すHTTPクライアントです。Java 8以降で動作します。

## 位置づけ

このリポジトリのモジュール依存は次のとおりです。

```text
sample-spring-boot-echo（インサイダーゲームBot本体）
  └─ line-bot-spring-boot（Spring Bootへの組み込み）
       ├─ line-bot-api-client  ← このモジュール
       ├─ line-bot-servlet（webhookの署名検証とparse）
       └─ line-bot-model（メッセージ・イベントのデータ型）
```

Bot本体はこのモジュールを直接依存に持たず、`line-bot-spring-boot`が生成した
`LineMessagingClient` beanを`@Autowired`で受け取って使います。

## 使い方

```java
LineMessagingClient client = LineMessagingClient.builder("YOUR_CHANNEL_TOKEN").build();

client.replyMessage(new ReplyMessage(replyToken, new TextMessage("hello")))
      .get();
```

APIはすべて`CompletableFuture`を返す非同期形式です。同期的に結果が必要な場合は
`get()`で待ち合わせます（Bot本体の`EchoApplication#reply`もこの形です）。

## 主なクラス

| クラス | 役割 |
| --- | --- |
| `LineMessagingClient` | reply・push・multicast・broadcast、プロフィール取得、リッチメニュー操作 |
| `LineMessagingClientBuilder` | チャネルトークン、各種timeout、独自OkHttpClientの設定 |
| `ChannelTokenSupplier` / `FixedChannelTokenSupplier` | チャネルアクセストークンの供給方法 |
| `LineSignatureValidator` | webhookの`X-Line-Signature`検証 |
| `LineOAuthClient` | チャネルアクセストークンの発行・失効 |
| `ChannelManagementSyncClient` | LIFFアプリの作成・更新・削除・一覧（同期API） |

## 実装

HTTP層はOkHttp3 + Retrofit2、JSONのserialize/deserializeはJacksonです。
リクエスト・レスポンスのモデルは`line-bot-model`が提供します。

APIがエラーを返した場合は、`com.linecorp.bot.client.exception`配下の例外
（`LineMessagingException`のサブクラス）へ変換されます。
