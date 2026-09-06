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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.example.bot.spring.game.SpecialVillage;
import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.spring.game.TextCommandHandler;
import com.example.bot.spring.game.Village;
import com.example.bot.spring.game.VillageList;
import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * LINEイベントの受け口と返信.
 *
 * <p>入力の解釈は{@link TextCommandHandler}が持つ。この経路が固有に持つのは、
 * イベントからの入力の取り出し方、返信APIでの返し方、
 * 対象の村がないときの応答（村の作成を促す確認テンプレート）だけ。
 *
 * <p>webhook eventにはLINE user IDとユーザーが入力したお題が含まれるため、
 * event自体をlogへ出さない。記録するのはevent種別と処理結果までとする。
 */
@Slf4j
@LineMessageHandler
public class LineEventHandler {

  /** ポストバックdataがこの値未満なら、村番号ではなくお題候補の難易度. */
  private static final int ODAI_RANK_DATA_LIMIT = 10;

  /** ポストバックdataがこの値以上なら特殊村の番号. */
  private static final int MIN_SPECIAL_VILLAGE_NUMBER = 10000;

  private final LineMessagingClient lineMessagingClient;

  public LineEventHandler(LineMessagingClient lineMessagingClient) {
    this.lineMessagingClient = lineMessagingClient;
  }

  @EventMapping
  public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
    log.debug("Received text message event");

    String userId = event.getSource().getUserId();
    if (userId == null) {
      replyUnidentifiedUser(event.getReplyToken());
      return;
    }
    replyOrDefault(event.getReplyToken(),
        TextCommandHandler.handle(userId, event.getMessage().getText()));
  }

  @EventMapping
  public void handlePostbackEvent(PostbackEvent event) {
    log.debug("Received postback event");

    String userId = event.getSource().getUserId();
    String data = event.getPostbackContent().getData();

    try {
      int dataInt = Integer.parseInt(data);
      if (dataInt >= 0 && dataInt < ODAI_RANK_DATA_LIMIT) {
        // お題詳細取得。userIdを使わないため識別できなくても応答する
        reply(event.getReplyToken(), TextCommandHandler.odaiCandidate(dataInt));

      } else if (userId == null) {
        replyUnidentifiedUser(event.getReplyToken());

      } else if (dataInt < MIN_SPECIAL_VILLAGE_NUMBER) {
        // 村番号の場合
        Village village = VillageList.getVillage(dataInt);
        replyOrDefault(event.getReplyToken(),
            village == null ? null : village.getStatusMessage(userId));
      } else {
        // 特殊村番号の場合
        SpecialVillage village = SpecialVillageList.getVillage(dataInt);
        replyOrDefault(event.getReplyToken(),
            village == null ? null : village.getStatusMessage(userId));
      }

    } catch (NumberFormatException e) {
      // 旧DBのお題登録用ポストバックは廃止済み。安全な既定応答だけ返す。
      replyDefaultMessage(event.getReplyToken());
    }

  }

  @EventMapping
  public void handleStickerMessageEvent(MessageEvent<StickerMessageContent> event) {
    log.debug("Received sticker message event");
    EchoImageEvent logic = new EchoImageEvent();
    reply(event.getReplyToken(), logic.echo());
  }

  @EventMapping
  public void handleDefaultMessageEvent(Event event) {
    log.debug("Received unhandled event: {}", event.getClass().getSimpleName());
  }

  /**
   * LINE user IDが取れないイベントを、状態を変更せずに拒否する.
   *
   * <p>グループ・ルームのイベントは、利用者が公式アカウント利用規約に
   * 同意していない場合userIdを含まない。userIdは村の所有者と参加者の
   * 同一性判定に使うため、nullのまま処理を進めると以降の操作がNPEになり、
   * ユーザーへ何も返信できなくなる。
   */
  private void replyUnidentifiedUser(@NonNull String replyToken) {
    log.debug("Rejected an event without a LINE user ID");
    reply(replyToken, Collections.<Message>singletonList(
        new TextMessage(MessageConst.ERR_UNIDENTIFIED_USER)));
  }

  /** メッセージがあれば返信し、なければ村の作成を促す既定の応答を返す. */
  private void replyOrDefault(@NonNull String replyToken, List<Message> messages) {
    if (messages == null) {
      replyDefaultMessage(replyToken);
    } else {
      reply(replyToken, messages);
    }
  }

  /** 村の作成を促す既定の応答. 対象の村がない操作はすべてここへ落ちる. */
  private void replyDefaultMessage(@NonNull String replyToken) {
    ConfirmTemplate confirmTemplate = new ConfirmTemplate("村の作成をしますか？",
        new MessageAction("GM", "お題"),
        new MessageAction("神", "神"));

    reply(replyToken, Collections.<Message>singletonList(
        new TemplateMessage(MessageConst.DEFAULT_MESSAGE, confirmTemplate)));
  }

  /** 返信APIの唯一の送信口. 送信の失敗はログに残すだけで、呼び出し元へは伝えない. */
  private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
    try {
      lineMessagingClient
          .replyMessage(new ReplyMessage(replyToken, messages))
          .get();
    } catch (InterruptedException | ExecutionException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.error("Failed to send a reply", e);
    }
  }
}
