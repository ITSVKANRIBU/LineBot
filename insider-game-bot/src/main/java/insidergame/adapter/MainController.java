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

package insidergame.adapter;

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import insidergame.game.TextCommandHandler;

import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;

/**
 * {@code /callapi}のHTTPアダプタ.
 *
 * <p>入力の解釈は{@link TextCommandHandler}が持つ。この経路が固有に持つのは、
 * パラメータの取り出しと、対象の村がないときの応答だけ。
 */
@RestController
public class MainController {

  /**
   * 対象の村がないときの応答.
   *
   * <p>LINEは村の作成を促す確認テンプレートを返すが、外部フォームが
   * テンプレートを表示できるか分からないため、この経路だけテキストで返す。
   */
  private static final String NO_VILLAGE_MESSAGE = "村が作成されていません";

  private final TextCommandHandler textCommandHandler;

  public MainController(TextCommandHandler textCommandHandler) {
    this.textCommandHandler = textCommandHandler;
  }

  /**
   * 村への参加・作成・設定をLINEメッセージ形式のJSONで返す.
   *
   * @param message LINEのトークへ送るのと同じ内容。村番号、コマンド、人数、お題
   * @param userId 呼び出し元が指定する参加者識別子
   * @return LINE Message APIのJSON配列。必須parameter不足はHTTP 400
   */
  @GetMapping("/callapi")
  @CrossOrigin
  public ResponseEntity<List<Message>> index(
      @RequestParam(value = "message", required = false) String message,
      @RequestParam(value = "userId", required = false) String userId) {
    if (message == null || message.trim().isEmpty()
        || userId == null || userId.trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    List<Message> messages = textCommandHandler.handle(userId, message);
    if (messages == null) {
      messages = Collections.<Message>singletonList(new TextMessage(NO_VILLAGE_MESSAGE));
    }
    return ResponseEntity.ok(messages);
  }
}
