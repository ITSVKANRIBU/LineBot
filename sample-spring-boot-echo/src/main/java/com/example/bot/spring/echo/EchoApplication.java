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

import com.example.bot.spring.entity.Village;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;

import com.linecorp.bot.model.event.Event;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.annotation.EventMapping;
import com.linecorp.bot.spring.boot.annotation.LineMessageHandler;

import java.util.Random;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@LineMessageHandler
public class EchoApplication {
  public static void main(String[] args) {
    SpringApplication.run(EchoApplication.class, args);
  }

  @EventMapping
  public Message handleTextMessageEvent(MessageEvent<TextMessageContent> event) {
    System.out.println("event: " + event);

    String userId = event.getSource().getUserId();
    String userMessage = event.getMessage().getText();

    // messageの取得
    String message = getMessage(userId, userMessage);

    return new TextMessage(message);
  }

  @EventMapping
  public void handleDefaultMessageEvent(Event event) {
    System.out.println("event: " + event);
  }

  private String getMessage(String userId, String userMessage) {
    String message = MessageConst.DEFAILT_MESSAGE;

    Random random = new Random();

    try {
      int number = Integer.parseInt(userMessage.trim());

      if (number < 100) {
        // 村番号の場合
        return getMessageVillageNum(userId, number);
      } else {

        // 人数が0のものを探す
        for (int i = VillageList.villageList.size() - 1; i >= 0; i--) {
          if (0 == VillageList.get(i).getVillageSize()
              && userId.contentEquals(VillageList.get(i).getOwnerId())) {
            VillageList.get(i).setVillageSize(number);
            int insiderNum = random.nextInt(number) + 1;

            VillageList.get(i).setInsiderNum(insiderNum);
            message = VillageList.get(i).getVillageNum() + "村 の人数を『" + number
                + "人』に設定しました。皆さんに村番号を伝えてください。";
            break;
          }
        }
      }

    } catch (Exception e) {
      if ("お題".equals(userMessage.trim()) || "題".equals(userMessage.trim())) {
        int villageNum = random.nextInt(8999) + 1000;

        Village newVillage = new Village();
        newVillage.setOwnerId(userId);
        newVillage.setVillageNum(villageNum);

        VillageList.addVillage(newVillage);

        message = villageNum + "村 を新しく作成しました。\n" + MessageConst.OWNER_ODAIMESSAGE;

      } else {
        for (int i = VillageList.villageList.size() - 1; i >= 0; i--) {
          if (null == VillageList.get(i).getOdai()
              && userId.contentEquals(VillageList.get(i).getOwnerId())) {
            VillageList.get(i).setOdai(message);
            message = VillageList.get(i).getVillageNum() + "村 のお題を『" + message + "』に設定しました。\n"
                + MessageConst.OWNER_NUMSETMESSAGE;
            break;
          }
        }
      }

    }

    return message;
  }

  private String getMessageVillageNum(String userId, int number) {
    String message = MessageConst.DEFAILT_MESSAGE;

    Village village = VillageList.getVillage(number);

    if (userId.equals(village.getOwnerId())) {
      // オーナーの場合
      message = village.getMessageOwner();
    } else {

      // 参加書の場合
      String memberRole = village.getMemberRole(userId);
      if (memberRole == null) {

        // 配役の設定
        village.addRoleList(
            village.getInsiderNum() == village.getRoleList().size() - 1 ? "インサイダー" : "村", userId);

        message = village.getRoleMessage(userId);

      } else {
        message = village.getRoleMessage(userId);
      }

    }

    return message;
  }

}
