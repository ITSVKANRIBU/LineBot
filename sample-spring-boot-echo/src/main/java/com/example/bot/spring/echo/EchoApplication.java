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

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.example.bot.common.CommonModule;
import com.example.bot.common.Request;
import com.example.bot.common.WordGetter;
import com.example.bot.spring.entity.Village;
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
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;
import com.linecorp.bot.spring.boot.common.SpecialVillageList;
import com.linecorp.bot.spring.boot.entity.SpecialVillage;
import com.linecorp.bot.spring.boot.logic.InsertLogic;

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
        reply(event.getReplyToken(), village.getStatusMessage(userId));
      } else {
        // 特殊村番号の場合
        SpecialVillage village = SpecialVillageList.getVillage(dataInt);
        reply(event.getReplyToken(), village.getStatusMessage(userId));
      }

    } catch (NumberFormatException e) {
      // お題登録
      putOdai(event.getReplyToken(), data);
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
      e.printStackTrace();
    }

  }

  private void reply(@NonNull String replyToken, @NonNull List<Message> messages) {
    try {
      lineMessagingClient
          .replyMessage(new ReplyMessage(replyToken, messages))
          .get();
    } catch (InterruptedException | ExecutionException e) {
      e.printStackTrace();
    }
  }

  /**
   * 現在廃止中
   * データベース連携処理
   * 
   * @param replyToken
   * @param difficulty
   */
  @SuppressWarnings("unused")
  private void getOdai(String replyToken, int difficulty) {
    InsertLogic logic = new InsertLogic();
    String odai = logic.getRandomProblem(difficulty);

    ConfirmTemplate confirmTemplate = new ConfirmTemplate("お題は「" + odai + "」です。確定しますか？",
        new MessageAction("確定", odai),
        new PostbackAction("再取得", String.valueOf(8)));

    try {
      lineMessagingClient
          .replyMessage(new ReplyMessage(replyToken,
              new TemplateMessage("「" + odai + "」を取得しました。", confirmTemplate)))
          .get();
    } catch (InterruptedException | ExecutionException e) {
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

  private void putOdai(String replyToken, String odai) {
    InsertLogic logic = new InsertLogic();
    try {
      logic.addProblem(odai, "インサイダーツール", 0, "インサイダーツールでの追加");
    } catch (SQLException e1) {
      e1.printStackTrace();
    }

    String message = "ありがとうございます！ 「" + odai + "」を追加しました。";

    List<Action> actionList = new ArrayList<Action>();
    
    // DB連携で廃止処理
    //actionList.add(new URIActionNonAltUri("他に投稿してみる", MessageConst.URI_INSIDER));

    ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
        message, actionList);

    List<Message> messages = Collections.singletonList(new TemplateMessage(message, buttons));

    reply(replyToken, messages);
  }

  private void replyMessage(String replyToken, String userId, String userMessage) {

    List<Message> messages = null;

    Random random = new Random();

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
        // 人数が0のものを探す
        for (int i = VillageList.getVillageList().size() - 1; i >= 0; i--) {
          if (0 == VillageList.get(i).getVillageSize()
              && userId.equals(VillageList.get(i).getOwnerId())) {
            if (number <= 1) {
              messages = Collections.singletonList(new TextMessage(MessageConst.ERR_NUMSETMESSAGE));
              break;
            }

            // 村人数設定
            VillageList.get(i).setVillageSize(number);

            // インサイダー位置設定
            int insiderNum = random.nextInt(number) + 1;
            String roleUrl = CommonModule.getIllustUrl("GM");
            VillageList.get(i).setInsiderNum(insiderNum);
            if (VillageList.get(i).getGmNum() == MessageConst.DEFAULT_GMNUM) {
              roleUrl = CommonModule.getIllustUrl("GOD");
              int gmNum = random.nextInt(number) + 1;
              while (gmNum == insiderNum) {
                gmNum = random.nextInt(number) + 1;
              }
              VillageList.get(i).setGmNum(gmNum);
            }
            String villageNumStr = String.valueOf(VillageList.get(i).getVillageNum());
            String message = "人数を『" + number
                + "人』に設定しました。"
                + "\n皆さんに村番号を伝えてください。";

            ButtonsTemplate buttons = new ButtonsTemplate(
                roleUrl,
                villageNumStr + "村", message, Collections.singletonList(
                    new MessageAction("確認", villageNumStr)));

            messages = Collections.singletonList(
                new TemplateMessage(message + "配布状況の確認は村番号を入力してください。", buttons));

            break;
          }
        }
      }

    } catch (Throwable e) {
      if ("お題".equals(userMessage.trim()) || "題".equals(userMessage.trim())
          || "神".equals(userMessage.trim())) {
        int villageNum = random.nextInt(8999) + 1000;

        // 重複しない番号取得（防止のため、100回まで）
        for (int i = 0; i < 100; i++) {
          boolean breakFlg = true;
          for (Village dao : VillageList.getVillageList()) {
            if (villageNum == dao.getVillageNum()) {
              villageNum = random.nextInt(8999) + 1000;
              breakFlg = false;
              break;
            }
          }
          if (breakFlg) {
            break;
          }
        }

        Village newVillage = new Village();
        newVillage.setOwnerId(userId);
        newVillage.setVillageNum(villageNum);

        if ("神".equals(userMessage.trim())) {
          newVillage.setGmNum(MessageConst.DEFAULT_GMNUM);
        }

        VillageList.addVillage(newVillage);

        String message = villageNum + "村 を新しく作成しました。" + MessageConst.OWNER_ODAIMESSAGE;

        ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
            message + "\nお題の自動取得もできます。", Collections.singletonList(
                new PostbackAction("お題の自動取得", String.valueOf(0))));

        messages = Collections.singletonList(new TemplateMessage(message, buttons));

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
            .singletonList(new TextMessage("https://insidergametool.netlify.com/webcontent/form.html"));

      } else if ("@取得".equals(userMessage.trim()) || "＠取得".equals(userMessage.trim())) {
        getOdaiDetail(replyToken, 10);
        return;

      } else if ("@逆村".equals(userMessage.trim()) || "＠逆村".equals(userMessage.trim())) {
        for (int i = VillageList.getVillageList().size() - 1; i >= 0; i--) {
          if (VillageList.get(i).getRoleList().size() == 0
              && userId.equals(VillageList.get(i).getOwnerId())) {
            // フラグ設定
            VillageList.get(i).setSpecialFlg(10);
            String message = VillageList.get(i).getVillageNum() + "村 を『逆村』に設定しました。\n"
                + "お題を知らない村人が1人となります。";
            messages = Collections.singletonList(new TextMessage(message));
            break;
          }
        }

      } else if ("@わーわーず".equals(userMessage.trim()) || "＠わーわーず".equals(userMessage.trim())) {
        for (int i = VillageList.getVillageList().size() - 1; i >= 0; i--) {
          if (VillageList.get(i).getRoleList().size() == 0
              && userId.equals(VillageList.get(i).getOwnerId())) {
            // 人数、お題チェック
            if (VillageList.get(i).getVillageSize() > 2
                && VillageList.get(i).getOdai() != null) {
              WereWordEvent logic = new WereWordEvent();
              messages = logic.branch(VillageList.get(i));
            }
            break;
          }
        }
      } else {
        for (int i = VillageList.getVillageList().size() - 1; i >= 0; i--) {
          if (null == VillageList.get(i).getOdai()
              && userId.equals(VillageList.get(i).getOwnerId())) {
            VillageList.get(i).setOdai(userMessage);
            String message = VillageList.get(i).getVillageNum() + "村 のお題を『" + userMessage + "』に設定しました。\n";
            if (VillageList.get(i).getGmNum() == MessageConst.DEFAULT_GMNUM) {
              message = message + MessageConst.GOD_NUMSETMESSAGE;
            } else {
              message = message + MessageConst.OWNER_NUMSETMESSAGE;
            }
            messages = Collections.singletonList(new TextMessage(message));

            // replyして処理終了とする。
            reply(replyToken, messages);
            // 履歴取得
            Request.run(userMessage, VillageList.get(i).getVillageNum() + "村：" + userId);
            return;
          }
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
    List<Message> messages = null;

    Village village = VillageList.getVillage(number);

    if (userId.equals(village.getOwnerId())) {
      // オーナーの場合
      messages = village.getMessageOwner();

    } else {

      // 参加者の場合
      String memberRole = village.getMemberRole(userId);
      if (memberRole == null) {

        if (village.getRoleList().size() >= village.getVillageSize()) {
          messages = Collections.singletonList(new TextMessage("村がいっぱいです。"));
        } else {
          // 配役の設定
          village.addRoleList(null, userId);
          village.setInsiderRole(userId);

          messages = village.getRoleMessage(userId);

        }
      } else {
        messages = village.getRoleMessage(userId);
      }

    }
    reply(replyToken, messages);
  }

  private void replyMessageSpecialVillage(String replyToken, String userId, int number) {
    List<Message> messages = null;

    SpecialVillage village = SpecialVillageList.getVillage(number);

    if (village != null) {
      // 参加者フラグ
      boolean sankaFlg = village.hasMember(userId);

      // 参加している場合
      if (sankaFlg) {
        messages = village.getRoleMessage(userId);

      } else { //参加者の場合

        if (village.getUserList().size() >= village.getMessageList().size()) {
          messages = Collections.singletonList(new TextMessage("村がいっぱいです。"));
        } else {
          // 配役の設定
          village.getUserList().add(userId);
          messages = village.getRoleMessage(userId);
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
}
