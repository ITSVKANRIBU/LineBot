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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.postback.PostbackContent;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.response.BotApiResponse;

/**
 * 「入室状況確認」ボタンのpostback処理を固定する.
 *
 * <p>村はFIFOで削除されるため、既に存在しない村番号のpostbackが届き得る。
 * 通常村・特殊村のどちらでもNPEにせず、既定メッセージを返すこと。
 */
public class LineEventHandlerPostbackTest {

  private LineEventHandler handler;
  private LineMessagingClient lineMessagingClient;

  @Before
  public void setUp() {
    VillageList.clear();
    SpecialVillageList.clear();

    // BotApiResponseはfinal（Lombok @Value）のためmockではなく実インスタンスを使う
    lineMessagingClient = mock(LineMessagingClient.class);
    when(lineMessagingClient.replyMessage(any(ReplyMessage.class)))
        .thenReturn(CompletableFuture.completedFuture(new BotApiResponse("ok", null)));

    handler = new LineEventHandler(lineMessagingClient);
  }

  @Test
  public void postbackForEvictedSpecialVillageRepliesWithTheDefaultMessage() {
    handler.handlePostbackEvent(postback("99999"));

    assertEquals(MessageConst.DEFAULT_MESSAGE, repliedAltText());
  }

  @Test
  public void postbackForEvictedVillageRepliesWithTheDefaultMessage() {
    handler.handlePostbackEvent(postback("1234"));

    assertEquals(MessageConst.DEFAULT_MESSAGE, repliedAltText());
  }

  @Test
  public void postbackWithRetiredNonNumericDataRepliesWithTheDefaultMessage() {
    // 旧DBのお題登録用ポストバックが古い端末から届いた場合
    handler.handlePostbackEvent(postback("すいか"));

    assertEquals(MessageConst.DEFAULT_MESSAGE, repliedAltText());
  }

  @Test
  public void defaultMessageShowsOnlyTheTwoStandardVillageCommands() {
    handler.handlePostbackEvent(postback("1234"));

    ConfirmTemplate confirm = (ConfirmTemplate) repliedTemplate().getTemplate();
    assertEquals(2, confirm.getActions().size());
    assertEquals(new MessageAction("GM", "お題"), confirm.getActions().get(0));
    assertEquals(new MessageAction("神", "神"), confirm.getActions().get(1));
  }

  private PostbackEvent postback(String data) {
    return new PostbackEvent("reply-token", new UserSource("user"),
        new PostbackContent(data, null), Instant.now());
  }

  private String repliedAltText() {
    return repliedTemplate().getAltText();
  }

  private TemplateMessage repliedTemplate() {
    ArgumentCaptor<ReplyMessage> captor = ArgumentCaptor.forClass(ReplyMessage.class);
    verify(lineMessagingClient).replyMessage(captor.capture());
    return (TemplateMessage) captor.getValue().getMessages().get(0);
  }
}
