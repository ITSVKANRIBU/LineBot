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

package com.example.bot.spring.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

/**
 * 特殊村の配布メッセージが、長さによらずLINEへ送れる形になることを固定する.
 *
 * <p>{@code ButtonsTemplateNonURL}のtextは画像・タイトルなしで160文字まで。
 * 超過するとLINE APIが送信を拒否し、参加者へ何も届かない。
 * メッセージはフォーム入力とワーワーズのお題の双方に由来し、
 * どちらも長さの上限がないため、描画側で振り分ける。
 */
public class SpecialVillageTest {

  @Test
  public void messageWithinTheTemplateLimitUsesButtons() {
    SpecialVillage village = villageOf("あなたの役職は村人です");

    List<Message> messages = village.getRoleMessage("user");

    assertEquals(1, messages.size());
    assertTrue(((TemplateMessage) messages.get(0)).getTemplate()
        instanceof ButtonsTemplateNonURL);
  }

  @Test
  public void messageAtTheTemplateLimitStillUsesButtons() {
    SpecialVillage village = villageOf(repeat("あ", 160));

    assertTrue(((TemplateMessage) village.getRoleMessage("user").get(0)).getTemplate()
        instanceof ButtonsTemplateNonURL);
  }

  @Test
  public void messageOverTheTemplateLimitFallsBackToTextAndStatus() {
    String longMessage = repeat("あ", 161);
    SpecialVillage village = villageOf(longMessage);

    List<Message> messages = village.getRoleMessage("user");

    assertEquals(2, messages.size());
    assertEquals(longMessage, ((TextMessage) messages.get(0)).getText());
    // 入室状況を続けて送り、ボタンの代わりに現在の人数を伝える
    assertTrue(((TextMessage) messages.get(1)).getText().contains("入室状況"));
  }

  @Test
  public void werewordsMessagesStayDeliverableWithALongTopic() {
    // お題はLINEのテキストメッセージ由来で長さの上限がない
    String longTopic = repeat("長いお題", 40);
    List<String> messages = new CreateWereWordsLogic().getMessages(true, 3, longTopic);

    SpecialVillage village = new SpecialVillage(messages);
    assertTrue(village.join("user"));

    for (Message message : village.getRoleMessage("user")) {
      if (message instanceof TemplateMessage) {
        ButtonsTemplateNonURL buttons =
            (ButtonsTemplateNonURL) ((TemplateMessage) message).getTemplate();
        assertTrue("ボタンテンプレートは160文字まで", buttons.getText().length() <= 160);
      }
    }
  }

  private SpecialVillage villageOf(String... messages) {
    SpecialVillage village = new SpecialVillage(Arrays.asList(messages));
    village.setVillageNum(12345);
    assertTrue(village.join("user"));
    return village;
  }

  private static String repeat(String unit, int times) {
    StringBuilder builder = new StringBuilder(unit.length() * times);
    for (int i = 0; i < times; i++) {
      builder.append(unit);
    }
    return builder.toString();
  }
}
