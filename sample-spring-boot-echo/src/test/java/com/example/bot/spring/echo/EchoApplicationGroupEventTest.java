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
import org.springframework.test.util.ReflectionTestUtils;

import com.example.bot.spring.entity.Village;
import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.postback.PostbackContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.response.BotApiResponse;

/**
 * グループ・ルームからのイベントでLINE user IDが取れない場合の扱いを固定する.
 *
 * <p>{@code GroupSource#getUserId()}は、利用者が公式アカウント利用規約に
 * 同意していない場合nullになる。userIdは村の所有者・参加者の同一性判定に使うため、
 * nullのまま状態を変更すると以降の操作がNPEになり、ユーザーへ返信できなくなる。
 */
public class EchoApplicationGroupEventTest {

  private EchoApplication application;
  private LineMessagingClient lineMessagingClient;

  @Before
  public void setUp() {
    VillageList.clear();
    SpecialVillageList.clear();

    lineMessagingClient = mock(LineMessagingClient.class);
    when(lineMessagingClient.replyMessage(any(ReplyMessage.class)))
        .thenReturn(CompletableFuture.completedFuture(new BotApiResponse("ok", null)));

    application = new EchoApplication();
    ReflectionTestUtils.setField(application, "lineMessagingClient", lineMessagingClient);
  }

  @Test
  public void villageCreationWithoutUserIdIsRejectedInsteadOfCreatingANullOwnerVillage() {
    application.handleTextMessageEvent(textEvent("お題"));

    assertEquals(MessageConst.ERR_UNIDENTIFIED_USER, repliedText());
  }

  @Test
  public void randomVillageCreationWithoutUserIdIsRejectedInsteadOfCreatingANullOwnerVillage() {
    application.handleTextMessageEvent(textEvent("ランダム"));

    assertEquals(MessageConst.ERR_UNIDENTIFIED_USER, repliedText());
  }

  @Test
  public void villageSizeWithoutUserIdIsRejectedInsteadOfFailing() {
    application.handleTextMessageEvent(textEvent("5"));

    assertEquals(MessageConst.ERR_UNIDENTIFIED_USER, repliedText());
  }

  @Test
  public void odaiWithoutUserIdIsRejectedInsteadOfFailing() {
    application.handleTextMessageEvent(textEvent("すいか"));

    assertEquals(MessageConst.ERR_UNIDENTIFIED_USER, repliedText());
  }

  @Test
  public void joiningAnExistingVillageWithoutUserIdIsRejectedInsteadOfFailing() {
    int villageNum = existingVillageNumber();

    application.handleTextMessageEvent(textEvent(String.valueOf(villageNum)));

    assertEquals(MessageConst.ERR_UNIDENTIFIED_USER, repliedText());
  }

  @Test
  public void reverseVillageWithoutUserIdIsRejectedInsteadOfFailing() {
    application.handleTextMessageEvent(textEvent("@逆村"));

    assertEquals(MessageConst.ERR_UNIDENTIFIED_USER, repliedText());
  }

  @Test
  public void statusPostbackWithoutUserIdIsRejectedInsteadOfFailing() {
    int villageNum = existingVillageNumber();

    application.handlePostbackEvent(postback(String.valueOf(villageNum)));

    assertEquals(MessageConst.ERR_UNIDENTIFIED_USER, repliedText());
  }

  /** お題取得のpostbackはuserIdを使わないため、識別できなくても応答する. */
  @Test
  public void odaiLookupPostbackStillWorksWithoutUserId() {
    application.handlePostbackEvent(postback("0"));

    verify(lineMessagingClient).replyMessage(any(ReplyMessage.class));
  }

  private int existingVillageNumber() {
    Village village = new Village();
    village.setOwnerId("owner");
    return VillageList.addVillage(village, new java.util.Random());
  }

  /** userIdを持たないグループイベント. */
  private MessageEvent<TextMessageContent> textEvent(String text) {
    return new MessageEvent<TextMessageContent>("reply-token",
        new GroupSource("group-id", null),
        new TextMessageContent("message-id", text), Instant.now());
  }

  private PostbackEvent postback(String data) {
    return new PostbackEvent("reply-token", new GroupSource("group-id", null),
        new PostbackContent(data, null), Instant.now());
  }

  private String repliedText() {
    ArgumentCaptor<ReplyMessage> captor = ArgumentCaptor.forClass(ReplyMessage.class);
    verify(lineMessagingClient).replyMessage(captor.capture());
    return ((TextMessage) captor.getValue().getMessages().get(0)).getText();
  }
}
