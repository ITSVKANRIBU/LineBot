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

import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.example.bot.common.CommonModule;
import com.example.bot.spring.entity.Village;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;

import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

/**
 * LINE webhookと{@code /callapi}で共通のゲーム操作.
 *
 * <p>対象の村が見つからない場合はいずれのメソッドも{@code null}を返す。
 * 呼び出し側は{@code null}を「村が作成されていません」相当の応答へ変換する。
 */
public final class VillageService {

  private VillageService() {
  }

  /**
   * 通常村を作成する.
   *
   * @param userId オーナーのユーザーID
   * @param godMode GM（神）モードで作成する場合true
   * @return 作成完了メッセージ
   */
  public static List<Message> createVillage(String userId, boolean godMode) {
    Village village = new Village();
    village.setOwnerId(userId);

    if (godMode) {
      village.setGmNum(MessageConst.DEFAULT_GMNUM);
    }

    int villageNum = VillageList.addVillage(village, new Random());

    String message = villageNum + "村 を新しく作成しました。" + MessageConst.OWNER_ODAIMESSAGE;

    ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
        message + "\nお題の自動取得もできます。", Collections.singletonList(
            new PostbackAction("お題の自動取得", String.valueOf(0))));

    return Collections.singletonList(new TemplateMessage(message, buttons));
  }

  /**
   * 参加人数を設定し、インサイダーとGMの位置を抽選する.
   *
   * @param userId オーナーのユーザーID
   * @param number 参加人数
   * @return 設定完了メッセージ。人数未設定の自分の村がない場合はnull
   */
  public static List<Message> setVillageSize(String userId, int number) {
    Village village = VillageList.findLatestOwned(userId, target -> 0 == target.getVillageSize());

    if (village == null) {
      return null;
    }

    if (number <= 1) {
      return Collections.singletonList(new TextMessage(MessageConst.ERR_NUMSETMESSAGE));
    }

    Random random = new Random();

    // 村人数設定
    village.setVillageSize(number);

    // インサイダー位置設定
    int insiderNum = random.nextInt(number) + 1;
    String roleUrl = CommonModule.getIllustUrl("GM");
    village.setInsiderNum(insiderNum);
    if (village.getGmNum() == MessageConst.DEFAULT_GMNUM) {
      roleUrl = CommonModule.getIllustUrl("GOD");
      int gmNum = random.nextInt(number) + 1;
      while (gmNum == insiderNum) {
        gmNum = random.nextInt(number) + 1;
      }
      village.setGmNum(gmNum);
    }

    String villageNumStr = String.valueOf(village.getVillageNum());
    String message = "人数を『" + number
        + "人』に設定しました。"
        + "\n皆さんに村番号を伝えてください。";

    ButtonsTemplate buttons = new ButtonsTemplate(
        roleUrl,
        villageNumStr + "村", message, Collections.singletonList(
            new MessageAction("確認", villageNumStr)));

    return Collections.singletonList(
        new TemplateMessage(message + "配布状況の確認は村番号を入力してください。", buttons));
  }

  /**
   * お題を設定する.
   *
   * @param userId オーナーのユーザーID
   * @param odai お題
   * @return 設定完了メッセージ。お題未設定の自分の村がない場合はnull
   */
  public static List<Message> setOdai(String userId, String odai) {
    Village village = VillageList.findLatestOwned(userId, target -> null == target.getOdai());

    if (village == null) {
      return null;
    }

    village.setOdai(odai);

    String message = village.getVillageNum() + "村 のお題を『" + odai + "』に設定しました。\n";
    if (village.getGmNum() == MessageConst.DEFAULT_GMNUM) {
      message = message + MessageConst.GOD_NUMSETMESSAGE;
    } else {
      message = message + MessageConst.OWNER_NUMSETMESSAGE;
    }

    return Collections.singletonList(new TextMessage(message));
  }

  /**
   * 通常村へ参加する。オーナーの場合は配布状況を返す.
   *
   * @param userId ユーザーID
   * @param villageNum 村番号
   * @return 役職メッセージまたは配布状況。村がない場合はnull
   */
  public static List<Message> joinVillage(String userId, int villageNum) {
    Village village = VillageList.getVillage(villageNum);

    if (village == null) {
      return null;
    }

    if (userId.equals(village.getOwnerId())) {
      // オーナーの場合
      return village.getMessageOwner();
    }

    // 参加者の場合。既に参加済みならjoinは呼ばずに役職を再表示する
    if (village.getMemberRole(userId) == null && village.join(userId) == null) {
      return Collections.singletonList(new TextMessage("村がいっぱいです。"));
    }

    return village.getRoleMessage(userId);
  }

  /**
   * 特殊村へ参加する.
   *
   * @param userId ユーザーID
   * @param villageNum 村番号
   * @return 役職メッセージ。村がない場合はnull
   */
  public static List<Message> joinSpecialVillage(String userId, int villageNum) {
    SpecialVillage village = SpecialVillageList.getVillage(villageNum);

    if (village == null) {
      return null;
    }

    // joinは参加済みユーザーに対して冪等
    if (!village.join(userId)) {
      return Collections.singletonList(new TextMessage("村がいっぱいです。"));
    }

    return village.getRoleMessage(userId);
  }
}
