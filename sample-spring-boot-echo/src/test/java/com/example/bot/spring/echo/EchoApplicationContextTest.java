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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.bot.spring.game.SpecialVillageRegistry;
import com.example.bot.spring.game.VillageRegistry;
import com.example.bot.spring.game.VillageService;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.BeaconEvent;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.beacon.BeaconContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.postback.PostbackContent;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.response.BotApiResponse;
import com.linecorp.bot.spring.boot.support.LineMessageHandlerSupport;

/**
 * Springコンテキストが起動し、LINEイベントが分離後のハンドラへ届くことを固定する.
 *
 * <p>{@code @EventMapping}の探索は{@code @LineMessageHandler}を付けたBeanの宣言メソッドを
 * 走査する。受け口を別Beanへ移したとき、annotationの付け替えを間違えると
 * webhookが起動時に無言で壊れる。単体テストでは検知できないため、
 * ここでコンテキストごと起動して確認する。
 *
 * <p>{@link IllustrationCatalogJob}はmockに差し替える。実際に取得すると
 * staticなカタログが埋まり、既定画像を期待する他のテストが同じJVM上で壊れるため。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(properties = {
    "line.bot.channel-token=dummy-channel-token",
    "line.bot.channel-secret=dummy-channel-secret",
})
public class EchoApplicationContextTest {

  /** テキスト・ポストバック・スタンプ・既定の4つ. */
  private static final int EXPECTED_HANDLER_COUNT = 4;

  @MockBean
  private LineMessagingClient lineMessagingClient;

  @MockBean
  private IllustrationCatalogJob illustrationCatalogJob;

  @Autowired
  private LineMessageHandlerSupport handlerSupport;

  @Autowired
  private LineEventHandler lineEventHandler;

  @Autowired
  private VillageRegistry villages;

  @Autowired
  private SpecialVillageRegistry specialVillages;

  @Autowired
  private VillageService villageService;

  @Before
  public void setUp() {
    when(lineMessagingClient.replyMessage(any(ReplyMessage.class)))
        .thenReturn(CompletableFuture.completedFuture(new BotApiResponse("ok", null)));
  }

  @Test
  public void everyEventMappingIsRegisteredOnTheDedicatedHandler() {
    List<?> registered = registeredHandlers();

    assertEquals(EXPECTED_HANDLER_COUNT, registered.size());
    for (Object handlerMethod : registered) {
      assertSame("受け口以外のBeanにハンドラが登録されている",
          lineEventHandler, ReflectionTestUtils.getField(handlerMethod, "object"));
    }
  }

  @Test
  public void eachEventKindSelectsTheDedicatedHandler() {
    assertNotNull(dispatch(textEvent()));
    assertNotNull(dispatch(postbackEvent()));
    assertNotNull(dispatch(stickerEvent()));
    assertNotNull(dispatch(beaconEvent()));
  }

  @Test
  public void aTextEventReachesTheHandlerAndIsAnswered() {
    dispatch(textEvent());

    verify(lineMessagingClient).replyMessage(any(ReplyMessage.class));
  }

  /** 対応しないイベントは受け取るが何も返さない. */
  @Test
  public void anUnhandledEventIsAcceptedWithoutAReply() {
    dispatch(beaconEvent());

    verify(lineMessagingClient, never()).replyMessage(any(ReplyMessage.class));
  }

  /** {@code LineMessageHandlerSupport}と同じ順序でイベントに対応するハンドラを選ぶ. */
  private Object dispatch(Event event) {
    for (Object handlerMethod : registeredHandlers()) {
      @SuppressWarnings("unchecked")
      Predicate<Event> supportType =
          (Predicate<Event>) ReflectionTestUtils.getField(handlerMethod, "supportType");

      if (supportType.test(event)) {
        ReflectionTestUtils.invokeMethod(handlerSupport, "dispatch", event);
        return ReflectionTestUtils.getField(handlerMethod, "object");
      }
    }
    return null;
  }

  private List<?> registeredHandlers() {
    return (List<?>) ReflectionTestUtils.getField(handlerSupport, "eventConsumerList");
  }

  private MessageEvent<TextMessageContent> textEvent() {
    return new MessageEvent<TextMessageContent>("reply-token", new UserSource("user"),
        new TextMessageContent("message-id", "お題"), Instant.now());
  }

  private MessageEvent<StickerMessageContent> stickerEvent() {
    return new MessageEvent<StickerMessageContent>("reply-token", new UserSource("user"),
        new StickerMessageContent("message-id", "1", "1"), Instant.now());
  }

  private PostbackEvent postbackEvent() {
    return new PostbackEvent("reply-token", new UserSource("user"),
        new PostbackContent("1234", null), Instant.now());
  }

  private BeaconEvent beaconEvent() {
    return new BeaconEvent("reply-token", new UserSource("user"), Instant.now(),
        new BeaconContent("hwid", "enter", null));
  }
}
