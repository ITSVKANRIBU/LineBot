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

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import org.junit.Before;
import org.junit.Test;

import com.example.bot.testing.GameFixture;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.response.BotApiResponse;

/**
 * 返信APIの完了を待たずにwebhookの処理を終えることを固定する.
 *
 * <p>LINEプラットフォームはwebhookに2秒以内の応答を求める。返信APIの所要時間は
 * こちらで制御できないため、完了を待つと返信APIの遅延がそのままwebhookの
 * 応答遅延になる。以前は{@code get()}で待っており、返信APIが固まると
 * webhookのスレッドも固まっていた。
 */
public class LineEventHandlerReplyTest {

  /** 待ってしまう実装に戻った場合、テストを固まらせずに失敗させる. */
  private static final int NO_HANG_TIMEOUT_MILLIS = 5000;

  private static final String OWNER = "owner-user";

  private LineEventHandler handler;
  private LineMessagingClient lineMessagingClient;

  @Before
  public void setUp() {
    lineMessagingClient = mock(LineMessagingClient.class);
    GameFixture fixture = new GameFixture();
    handler = new LineEventHandler(
        lineMessagingClient, fixture.textCommandHandler, fixture.villageService);
  }

  /** 返信APIがまだ応答していなくても、webhookの処理は返る. */
  @Test(timeout = NO_HANG_TIMEOUT_MILLIS)
  public void theWebhookThreadDoesNotWaitForTheReplyApi() {
    CompletableFuture<BotApiResponse> pending = new CompletableFuture<BotApiResponse>();
    when(lineMessagingClient.replyMessage(any(ReplyMessage.class))).thenReturn(pending);

    handler.handleTextMessageEvent(textEvent("お題"));

    verify(lineMessagingClient).replyMessage(any(ReplyMessage.class));
    assertFalse("返信の完了を待ってはいけない", pending.isDone());
  }

  /** 返信に失敗しても、webhookの処理は例外を投げずに終わる. */
  @Test(timeout = NO_HANG_TIMEOUT_MILLIS)
  public void aFailedReplyDoesNotBreakTheWebhook() {
    CompletableFuture<BotApiResponse> failed = new CompletableFuture<BotApiResponse>();
    failed.completeExceptionally(new IllegalStateException("reply API is unavailable"));
    when(lineMessagingClient.replyMessage(any(ReplyMessage.class))).thenReturn(failed);

    handler.handleTextMessageEvent(textEvent("お題"));

    verify(lineMessagingClient).replyMessage(any(ReplyMessage.class));
  }

  private MessageEvent<TextMessageContent> textEvent(String text) {
    return new MessageEvent<TextMessageContent>("reply-token", new UserSource(OWNER),
        new TextMessageContent("message-id", text), Instant.now());
  }
}
