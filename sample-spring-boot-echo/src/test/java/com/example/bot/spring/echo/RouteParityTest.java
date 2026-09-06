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

package com.example.bot.spring.echo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.spring.game.VillageList;
import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;

/**
 * LINE経由と{@code /callapi}経由が同じ入力に同じ応答を返すことを固定する.
 *
 * <p>経路ごとにゲームの規則や入力の解釈を書くと、片方だけを直したときに
 * 配布結果が分岐する。分岐しても両経路の結果を突き合わせる利用者はいないため、
 * 誰も気付けない。ここで突き合わせておく。
 *
 * <p>経路差として残してよいのは「対象の村がないときの応答」だけ
 * （LINEは確認テンプレート、APIは`村が作成されていません`のテキスト）。
 */
public class RouteParityTest {

  private static final String OWNER = "owner-user";
  private static final String MEMBER = "member-user";

  /** 村番号は経路ごとに別々に採番されるため、突き合わせでは伏せる. */
  private static final String VILLAGE_NUMBER_PLACEHOLDER = "<村番号>";

  /** 実行時にその経路の村番号へ置き換わる入力. */
  private static final String CURRENT_VILLAGE = "<現在の村>";

  /** 対象の村がないときの応答。形は経路で違ってよいので、突き合わせでは同じ印にする. */
  private static final String MISSING_VILLAGE = "<村なし>";

  private static final String MISSING_VILLAGE_TEXT = "村が作成されていません";

  /** 配役の抽選を含まない、決定的な入力の並び. */
  private static final String[] SCRIPT = {
      "お題",
      "すいか",
      "3",
      CURRENT_VILLAGE,
      "@逆村",
      "＠わーわーず",
      "@特殊",
      "@配布",
      "101",
      "9999",
      " 3 ",
      "知らない文字列",
  };

  private LineEventHandler handler;
  private LineMessagingClient lineMessagingClient;
  private final MainController controller = new MainController();

  @Before
  public void setUp() {
    lineMessagingClient = mock(LineMessagingClient.class);
    when(lineMessagingClient.replyMessage(any(ReplyMessage.class)))
        .thenReturn(CompletableFuture.completedFuture(new BotApiResponse("ok", null)));

    handler = new LineEventHandler(lineMessagingClient);
  }

  @Test
  public void bothRoutesAnswerTheSameScriptTheSameWay() {
    List<String> throughLine = runScript(this::sendThroughLine);
    List<String> throughApi = runScript(this::sendThroughApi);

    assertEquals(SCRIPT.length, throughLine.size());
    for (int step = 0; step < SCRIPT.length; step++) {
      assertEquals("入力『" + SCRIPT[step] + "』への応答が経路で異なる",
          throughLine.get(step), throughApi.get(step));
    }
  }

  /**
   * 参加と満員の判定が経路で一致する.
   *
   * <p>役職は席の抽選で決まるため文面までは一致しない。どの操作として扱われるかを揃える。
   */
  @Test
  public void bothRoutesSeatParticipantsAndRejectTheSameOverflow() {
    for (int route = 0; route < 2; route++) {
      resetRegistries();
      boolean throughLine = route == 0;

      send(throughLine, OWNER, "お題");
      send(throughLine, OWNER, "すいか");
      send(throughLine, OWNER, "2");
      String villageNum = String.valueOf(currentVillageNumber());

      assertTrue("参加者に役職が配られない",
          altTextOf(send(throughLine, MEMBER, villageNum)).startsWith("あなたの役職は"));
      assertEquals("再参加で同じ応答にならない",
          send(throughLine, MEMBER, villageNum), send(throughLine, MEMBER, villageNum));
      assertTrue(altTextOf(send(throughLine, "second", villageNum)).startsWith("あなたの役職は"));
      assertEquals(new TextMessage("村がいっぱいです。"),
          send(throughLine, "third", villageNum).get(0));
    }
  }

  /** 対象の村がないときだけ、経路ごとに応答が違う. */
  @Test
  public void onlyTheMissingVillageReplyDiffersBetweenRoutes() {
    resetRegistries();
    assertEquals(new TextMessage(MISSING_VILLAGE_TEXT),
        sendThroughApi(MEMBER, "1234").get(0));

    resetRegistries();
    List<Message> throughLine = sendThroughLine(MEMBER, "1234");
    assertTrue("LINEは村の作成を促す確認テンプレートを返す",
        throughLine.get(0) instanceof TemplateMessage);
  }

  private List<String> runScript(Route route) {
    resetRegistries();

    List<String> answers = new ArrayList<String>();
    for (String input : SCRIPT) {
      String text = CURRENT_VILLAGE.equals(input)
          ? String.valueOf(currentVillageNumber()) : input;
      answers.add(describe(route.send(OWNER, text)));
    }
    return answers;
  }

  private List<Message> send(boolean throughLine, String userId, String text) {
    return throughLine ? sendThroughLine(userId, text) : sendThroughApi(userId, text);
  }

  private List<Message> sendThroughLine(String userId, String text) {
    handler.handleTextMessageEvent(new MessageEvent<TextMessageContent>("reply-token",
        new UserSource(userId), new TextMessageContent("message-id", text), Instant.now()));

    ArgumentCaptor<ReplyMessage> captor = ArgumentCaptor.forClass(ReplyMessage.class);
    verify(lineMessagingClient, atLeastOnce()).replyMessage(captor.capture());
    return captor.getValue().getMessages();
  }

  private List<Message> sendThroughApi(String userId, String text) {
    return controller.index(text, userId).getBody();
  }

  /**
   * 経路によらない形へ直す.
   *
   * <p>村番号は経路ごとに採番されるため、4桁以上の数字を伏せる。人数や入室状況の
   * 数字は3桁以下なので残る。対象の村がないときの応答だけは経路差を認めているので、
   * どちらの形でも同じ印にする。
   */
  private String describe(List<Message> messages) {
    if (isMissingVillageReply(messages)) {
      return MISSING_VILLAGE;
    }
    return messages.toString().replaceAll("\\d{4,}", VILLAGE_NUMBER_PLACEHOLDER);
  }

  private boolean isMissingVillageReply(List<Message> messages) {
    if (messages.size() != 1) {
      return false;
    }
    Message message = messages.get(0);
    if (message instanceof TextMessage) {
      return MISSING_VILLAGE_TEXT.equals(((TextMessage) message).getText());
    }
    return message instanceof TemplateMessage
        && MessageConst.DEFAULT_MESSAGE.equals(((TemplateMessage) message).getAltText());
  }

  private int currentVillageNumber() {
    return VillageList.findLatestOwned(OWNER, village -> true).getVillageNum();
  }

  private String altTextOf(List<Message> messages) {
    return ((TemplateMessage) messages.get(0)).getAltText();
  }

  private void resetRegistries() {
    VillageList.clear();
    SpecialVillageList.clear();
  }

  /** 経路ごとの送信手段. */
  private interface Route {
    List<Message> send(String userId, String text);
  }
}
