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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.bot.common.CommonModule;
import com.example.bot.common.WordGetter;
import com.example.bot.spring.entity.Village;
import com.example.bot.spring.game.SpecialVillage;
import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.spring.game.VillageService;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.Action;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import lombok.NonNull;

@SpringBootApplication
@EnableScheduling
@LineMessageHandler
public class EchoApplication {
  @Autowired
  private LineMessagingClient lineMessagingClient;

  public static void main(String[] args) {
    SpringApplication.run(EchoApplication.class, args);
  }

  @Scheduled(fixedDelay = 300000)
  public static void createMap() {
    System.out.println("画像取得");
    CommonModule.createMap();
  }

  @EventMapping
  public void handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
    System.out.println("event: " + event);

    String userId = event.getSource().getUserId();
    String userMessage = event.getMessage().getText();

    // messageの送信
    replyMessage(event.getReplyToken(), userId, userMessage);
  }

  @EventMapping
  public void handlePostbackEvent(PostbackEvent event) {
    System.out.println("event: " + event);

    String userId = event.getSource().getUserId();
    String data = event.getPostbackContent().getData();

    try {
      int dataInt = Integer.parseInt(data);
      if (dataInt >= 0 && dataInt < 10) {
        // お題詳細取得
        getOdaiDetail(event.getReplyToken(), dataInt);

      } else if (dataInt < 10000) {
        // 村番号の場合
        Village village = VillageList.getVillage(dataInt);
        if (village == null) {
          replyDefoltMessage(event.getReplyToken());
        } else {
          reply(event.getReplyToken(), village.getStatusMessage(userId));
        }
      } else {
        // 特殊村番号の場合
        SpecialVillage village = SpecialVillageList.getVillage(dataInt);
        if (village == null) {
          replyDefoltMessage(event.getReplyToken());
        } else {
          reply(event.getReplyToken(), village.getStatusMessage(userId));
        }
      }

    } catch (NumberFormatException e) {
      // 旧DBのお題登録用ポストバックは廃止済み。安全な既定応答だけ返す。
      replyDefoltMessage(event.getReplyToken());
    }

  }

  @EventMapping
  public void handleStickerMessageEvent(MessageEvent<StickerMessageContent> event) {
    System.out.println("event: スタンプイベント");
    EchoImageEvent logic = new EchoImageEvent();
    reply(event.getReplyToken(), logic.echo());
  }

  @EventMapping
  public void handleDefaultMessageEvent(Event event) {
    System.out.println("event: " + event);
  }

  private void replyDefoltMessage(@NonNull String replyToken) {
    ConfirmTemplate confirmTemplate = new ConfirmTemplate("村の作成をしますか？",
        new MessageAction("GM", "お題"),
        new MessageAction("神", "神"));

    try {
      lineMessagingClient
          .replyMessage(new ReplyMessage(replyToken,
              new TemplateMessage(MessageConst.DEFAILT_MESSAGE, confirmTemplate)))
          .get();
    } catch (InterruptedException | ExecutionException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      e.printStackTrace();
    }

  }

  private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
    try {
      lineMessagingClient
          .replyMessage(new ReplyMessage(replyToken, messages))
          .get();
    } catch (InterruptedException | ExecutionException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      e.printStackTrace();
    }
  }

  private void getOdaiDetail(String replyToken, int difficulty) {
    String odai = WordGetter.getWord(difficulty);

    List<Message> messages = null;

    String message = "お題は「" + odai + "」です。確定しますか？";

    List<Action> actionList = new ArrayList<Action>();
    actionList.add(new MessageAction("確定", odai));
    actionList.add(new PostbackAction("初心者", String.valueOf(2)));
    actionList.add(new PostbackAction("上級者", String.valueOf(3)));
    actionList.add(new PostbackAction("変態", String.valueOf(4)));

    ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
        message, actionList);
    messages = Collections.singletonList(new TemplateMessage(message, buttons));

    reply(replyToken, messages);
  }

  private void replyMessage(String replyToken, String userId, String userMessage) {

    List<Message> messages = null;

    try {
      int number = Integer.parseInt(userMessage.trim());

      // 特殊村の場合
      if (number > 9999) {
        replyMessageSpecialVillage(replyToken, userId, number);
        return;
      }

      if (number > 100) {
        // 村番号の場合
        replyMessageVillageNum(replyToken, userId, number);
        return;

      } else {
        // 人数設定
        messages = VillageService.setVillageSize(userId, number);
      }

    } catch (NumberFormatException e) {
      if ("お題".equals(userMessage.trim()) || "題".equals(userMessage.trim())
          || "神".equals(userMessage.trim())) {
        messages = VillageService.createVillage(userId, "神".equals(userMessage.trim()));

      } else if ("@配布".equals(userMessage.trim()) || "＠配布".equals(userMessage.trim())) {
        messages = new ArrayList<Message>();
        String imageUrl = MessageConst.ILLUSTRATION_URL_PREFIX + "966mpnqz.png";
        Message imageMessage = new ImageMessage(imageUrl, imageUrl);
        messages.add(imageMessage);
        Message textMessage = new TextMessage("https://line.me/R/ti/p/%40966mpnqz");
        messages.add(textMessage);
        Message textMessage2 = new TextMessage("お友達ID\n@966mpnqz");
        messages.add(textMessage2);

      } else if ("@特殊".equals(userMessage.trim()) || "＠特殊".equals(userMessage.trim())) {
        messages = Collections
            .singletonList(new TextMessage("https://insidergametool.netlify.app/form.html"));

      } else if ("@取得".equals(userMessage.trim()) || "＠取得".equals(userMessage.trim())) {
        getOdaiDetail(replyToken, 10);
        return;

      } else if ("@逆村".equals(userMessage.trim()) || "＠逆村".equals(userMessage.trim())) {
        Village village = VillageList.findLatestOwned(userId, target -> target.getRoleList().isEmpty());
        if (village != null) {
          // フラグ設定
          village.setSpecialFlg(10);
          String message = village.getVillageNum() + "村 を『逆村』に設定しました。\n"
              + "お題を知らない村人が1人となります。";
          messages = Collections.singletonList(new TextMessage(message));
        }

      } else if ("@わーわーず".equals(userMessage.trim()) || "＠わーわーず".equals(userMessage.trim())) {
        Village village = VillageList.findLatestOwned(userId, target -> target.getRoleList().isEmpty());
        // 人数、お題チェック
        if (village != null && village.getVillageSize() > 2 && village.getOdai() != null) {
          WereWordEvent logic = new WereWordEvent();
          messages = logic.branch(village);
        }

      } else {
        messages = VillageService.setOdai(userId, userMessage);
        if (messages != null) {
          // replyして処理終了とする。
          reply(replyToken, messages);
          return;
        }
      }

    }
    // メッセージの設定がない場合
    if (null == messages) {
      replyDefoltMessage(replyToken);
    } else {
      reply(replyToken, messages);
    }
  }

  private void replyMessageVillageNum(String replyToken, String userId, int number) {
    List<Message> messages = VillageService.joinVillage(userId, number);

    // メッセージの設定がない場合
    if (null == messages) {
      replyDefoltMessage(replyToken);
    } else {
      reply(replyToken, messages);
    }
  }

  private void replyMessageSpecialVillage(String replyToken, String userId, int number) {
    List<Message> messages = VillageService.joinSpecialVillage(userId, number);

    // メッセージの設定がない場合
    if (null == messages) {
      replyDefoltMessage(replyToken);
    } else {
      reply(replyToken, messages);
    }
  }
}
