# line-bot-spring-boot

LINE Messaging APIをSpring Bootへ組み込むためのauto-configurationです。

webhookのエンドポイント、署名検証、イベントのparse、handlerメソッドへの振り分け、
`LineMessagingClient` beanの生成までをこのモジュールが受け持ちます。
アプリ側はhandlerメソッドを書くだけで済みます。

## 使い方

`@LineMessageHandler`を付けたクラスの中で、`@EventMapping`を付けたメソッドが
イベントhandlerとして扱われます。

```java
@LineMessageHandler
public class LineEventHandler {
    @EventMapping
    public TextMessage handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
        return new TextMessage(event.getMessage().getText());
    }

    @EventMapping
    public void handleDefaultMessageEvent(Event event) {
        // 上のhandlerに当たらないイベントはここへ来る
    }
}
```

- handlerメソッドの引数は、`Event`を実装した型ひとつだけにします。
- webhookを受信すると、SDKが引数の型を見て呼ぶメソッドを決めます。
  より具体的な型のhandlerが優先され、`Event`型のhandlerが既定の受け皿になります。
  優先順位を明示したい場合は`@EventMapping(priority = ...)`で指定します。
- 戻り値に`Message`または`List<Message>`を返すと、SDKがそのままreplyします。
  `void`を返す場合は、アプリ側で`LineMessagingClient`を使って返信します。

検出されたhandlerは起動時にlogへ出力されます。

```text
c.l.b.s.b.s.LineMessageHandlerSupport    : Mapped "[MessageEvent<TextMessageContent>]" onto public com.linecorp.bot.model.message.TextMessage insidergame.adapter.LineEventHandler.handleTextMessageEvent(...)
c.l.b.s.b.s.LineMessageHandlerSupport    : Mapped "[Event]" onto public void insidergame.adapter.LineEventHandler.handleDefaultMessageEvent(...)
```

## 設定

設定値はSpring Bootの外部設定（`application.yml`、システムproperty、環境変数）から読み込みます。

| プロパティ | 説明 |
| ----- | ------ |
| `line.bot.channelToken` | チャネルアクセストークン |
| `line.bot.channelSecret` | チャネルシークレット |
| `line.bot.channelTokenSupplyMode` | トークンの供給方法（既定: `FIXED`）<br>LINE Partnersは`SUPPLIER`にして、独自の`ChannelTokenSupplier` beanを用意します |
| `line.bot.connectTimeout` | 接続timeout（ミリ秒） |
| `line.bot.readTimeout` | 読み込みtimeout（ミリ秒） |
| `line.bot.writeTimeout` | 書き込みtimeout（ミリ秒） |
| `line.bot.handler.enabled` | `@EventMapping`の仕組みを有効にする（既定: true） |
| `line.bot.handler.path` | webhookを待ち受けるpath（既定: `/callback`） |

このリポジトリのBot本体（`insider-game-bot`）では、
`src/main/resources/application.yml`で次のように設定しています。
秘密情報はソースへ書かず、環境変数から注入します。

```yaml
line.bot:
  channel-token: ${LINE_BOT_CHANNEL_TOKEN}
  channel-secret: ${LINE_BOT_CHANNEL_SECRET}
  handler.path: /callback
```

## 構成

| クラス | 役割 |
| --- | --- |
| `LineBotAutoConfiguration` | `LineMessagingClient`などのbean定義 |
| `LineBotProperties` | `line.bot.*`のbinding |
| `BotPropertiesValidator` | 供給モードとトークン設定の整合性チェック |
| `LineBotWebMvcConfigurer` / `LineBotWebMvcBeans` | webhook受信のためのMVC設定 |
| `support.LineMessageHandlerSupport` | `@EventMapping`メソッドの検出と振り分け |
| `support.ReplyByReturnValueConsumer` | handlerの戻り値をreplyへ変換 |
| `interceptor.LineBotServerInterceptor` | 署名検証を通したリクエストのみを通す |

署名検証とwebhook本文のparse自体は`line-bot-servlet`の
`LineBotCallbackRequestParser`が担当します。
