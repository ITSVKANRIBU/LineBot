# line-bot-cli

`line-bot-api-client`を使ったコマンドラインツールです。
リッチメニュー、LIFFアプリ、pushメッセージを、Botを起動せずに操作できます。

Bot本体（`sample-spring-boot-echo`）の動作には必要ありません。
チャネルの設定作業を手元から行うための運用ツールです。

## ビルド

```bash
../gradlew clean build
```

実行可能jarは`./build/libs/line-bot-cli-2.7.0-SNAPSHOT-exec.jar`に生成されます。
launch script付きのため、そのまま実行できます。

```bash
./build/libs/line-bot-cli-2.7.0-SNAPSHOT-exec.jar --command=liff-list
```

## 事前準備

`line.bot.channel-token`と`line.bot.channel-secret`をCLIへ渡す必要があります。
Spring Bootの[Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html)の規則に従います。

### application.ymlで渡す（推奨）

カレントディレクトリに`application.yml`を置きます。

```yaml
line.bot:
  channel-token: 'your token'
  channel-secret: 'your secret'
```

```bash
./line-bot-cli.jar --command=liff-list
# ./application.yml から設定が読み込まれる
```

### 環境変数で渡す

```bash
export LINE_BOT_CHANNEL_TOKEN='your token'
export LINE_BOT_CHANNEL_SECRET='your secret'
./line-bot-cli.jar --command=liff-list
```

## 共通引数

| 引数 | 説明 |
| -------- | ---- |
| `--command` | 実行するコマンド名。未指定または該当なしの場合は、利用可能なコマンド一覧をlogへ出力します |
| `--liff-id` | 削除・更新対象のLIFFアプリID |
| `--rich-menu-id` | 操作対象のリッチメニューID |
| `--user-id` | 操作対象のLINEユーザーID |
| `--json` | 送信するJSONファイルのpath。`--data`・`--yaml`のいずれかひとつを使います |
| `--yaml` | 送信するYAMLファイルのpath |
| `--data` | 送信するJSONを直接指定 |

## コマンド

### message-push

指定したユーザーへメッセージをpushします。`to`を複数指定した場合はmulticast APIを使います。

```bash
cat message-push.json
```

```json
{
  "to": [ "Ue87f273e325cd42ad2dd65946347f07f" ],
  "messages": [
    { "type": "text", "text": "Hello, World!" }
  ]
}
```

```bash
./line-bot-cli.jar --command=message-push --json=message-push.json
```

```text
18:47:47  INFO - c.l.bot.client.wire  : <-- 200 https://api.line.me/v2/bot/message/multicast (367ms)
```

### LIFFアプリ

| コマンド | 説明 |
| --- | --- |
| `liff-create` | LIFFアプリを作成する。`--json`でviewを指定 |
| `liff-list` | LIFFアプリの一覧を表示する |
| `liff-update` | `--liff-id`のLIFFアプリを更新する |
| `liff-delete` | `--liff-id`のLIFFアプリを削除する |

```bash
cat liff.json
```

```json
{
    "type": "full",
    "url": "https://example.com"
}
```

```bash
./line-bot-cli.jar --command=liff-create --json=liff.json
```

```text
16:37:40  INFO - .c.LiffCreateCommand : Successfully finished. Response : LiffAppAddResponse(liffId=1506753437-Xx5J85Ky)
```

```bash
./line-bot-cli.jar --command=liff-list
```

```text
16:40:05  INFO - .b.c.LiffListCommand : Successfully finished.
16:40:05  INFO - .b.c.LiffListCommand : You have 1 LIFF apps.
16:40:05  INFO - .b.c.LiffListCommand : LiffApp(liffId=1506753437-Xx5J85Ky, view=LiffView(type=FULL, url=https://example.com))
```

```bash
./line-bot-cli.jar --command=liff-delete --liff-id=1506753437-Xx5J85Ky
```

### リッチメニュー

| コマンド | 説明 |
| --- | --- |
| `richmenu-create` | リッチメニューを作成する。`--json`または`--yaml`で定義を指定 |
| `richmenu-get` | `--rich-menu-id`の定義を表示する |
| `richmenu-list` | リッチメニューの一覧を表示する |
| `richmenu-delete` | `--rich-menu-id`を削除する |
| `richmenu-upload` | `--image`の画像をリッチメニューへ登録する |
| `richmenu-download` | 登録済み画像を`--out`へ保存する |
| `richmenu-link` | `--user-id`へリッチメニューを紐付ける |
| `richmenu-unlink` | `--user-id`の紐付けを解除する |
| `richmenu-getrichmenuidofuser` | `--user-id`に紐付いたリッチメニューIDを表示する |

```bash
cat richmenu-create.yml
```

```yaml
size:
  width: 2500
  height: 1686
selected: false
name: From CLI
chatBarText: CHAT
areas:
  - bounds: {x: 0, y: 0, width: 2500, height: 1686}
    action: {type: message, label: LABEL, text: TEXT}
```

```bash
./line-bot-cli.jar --command=richmenu-create --yaml=richmenu-create.yml
```

```text
21:06:57  INFO - ichMenuCreateCommand : Successfully finished. RichMenuIdResponse(richMenuId=richmenu-0591a1ce01bda78f85213d347f0a966f)
```

```bash
./line-bot-cli.jar --command=richmenu-upload --rich-menu-id=richmenu-00e97da3ae27b54bd603cf42b9fc7672 --image=image.jpeg
```

```bash
./line-bot-cli.jar --command=richmenu-download --rich-menu-id=richmenu-00e97da3ae27b54bd603cf42b9fc7672 --out=out.jpeg
```

```bash
./line-bot-cli.jar --command=richmenu-link --rich-menu-id=richmenu-00e97da3ae27b54bd603cf42b9fc7672 --user-id=Ue87f273e325cd42ad2dd65946347f07f
```

## Tips

### 複数のBotを扱う

ひとつの設定ファイルに、複数Botの設定をprofileで分けて書けます。

```yaml
line.bot:
  channel-token: 'dev token'
  channel-secret: 'dev secret'

---
spring.profiles: production
line.bot:
  channel-token: 'production token'
  channel-secret: 'production secret'
```

既定は開発環境です。`--spring.profiles.active=production`を付けると本番設定へ切り替わります。
詳細はSpring Bootの[Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html)を参照してください。
