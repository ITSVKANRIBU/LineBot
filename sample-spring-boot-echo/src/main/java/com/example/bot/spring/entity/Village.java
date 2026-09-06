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

package com.example.bot.spring.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.bot.common.CommonModule;
import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.model.action.Action;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonTitle;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

/**
 * 通常村の状態.
 *
 * <p>可変状態へ触れるメソッドはすべてインスタンスのモニタ上で実行する。
 * 人数設定と配役抽選、お題設定、逆村への切り替えは
 * それぞれ1つの操作として原子的に行う必要がある。
 * 途中経過が{@link #join(String)}から観測されると、
 * インサイダー不在の村や「村がいっぱいです。」の誤判定、
 * 通常村と逆村の配役の混在が起こるため。
 */
public class Village {

  private int villageNum;
  private String ownerId;
  private String odai;
  private final List<InsiderRole> roleList;

  private int insiderNum;
  private int gmNum;
  private int villageSize;

  /** 逆村。お題を知らない村人が1人だけになる. */
  private boolean reverseVillage;

  /** ランダム村。オーナーも参加者として配役される. */
  private boolean randomMode;

  public Village() {
    roleList = new CopyOnWriteArrayList<InsiderRole>();
  }

  public synchronized int getVillageNum() {
    return villageNum;
  }

  public synchronized void setVillageNum(int villageNum) {
    this.villageNum = villageNum;
  }

  public synchronized String getOwnerId() {
    return ownerId;
  }

  public synchronized void setOwnerId(String ownerId) {
    this.ownerId = ownerId;
  }

  public synchronized String getOdai() {
    return odai;
  }

  /**
   * お題を設定する.
   *
   * @param newOdai お題
   * @return 設定できた場合true。既に設定済みの場合false
   */
  public synchronized boolean applyOdai(String newOdai) {
    if (odai != null) {
      return false;
    }
    odai = newOdai;
    return true;
  }

  /** 参加者が1人以上いるか. */
  public synchronized boolean hasMembers() {
    return !roleList.isEmpty();
  }

  public synchronized int getMemberCount() {
    return roleList.size();
  }

  /**
   * 参加人数を確定し、インサイダーと（神モードなら）GMの位置を抽選する.
   *
   * <p>配役を先に決めてから{@code villageSize}を書くため、
   * 参加者が「人数は設定済みだが配役は未確定」の状態を観測することはない。
   *
   * <p>ランダム村ではオーナーも参加者に含めるため、人数公開と同じモニタ上で
   * オーナーを先頭に着席させる。別操作にすると、その間に{@link #join(String)}した
   * 参加者にオーナーの席を奪われ、オーナーが配役を受け取れない。
   *
   * @param size 参加人数
   * @param random 位置抽選に使う乱数
   * @return 設定できた場合true。既に人数設定済みの場合false
   */
  public synchronized boolean configure(int size, Random random) {
    if (villageSize != 0) {
      return false;
    }

    insiderNum = random.nextInt(size) + 1;

    if (gmNum == MessageConst.DEFAULT_GMNUM) {
      int candidate = random.nextInt(size) + 1;
      while (candidate == insiderNum) {
        candidate = random.nextInt(size) + 1;
      }
      gmNum = candidate;
    }

    // 配役確定後に人数を公開する
    villageSize = size;

    if (randomMode) {
      join(ownerId);
    }
    return true;
  }

  /** ユーザーを参加させ、現在の村の状態で配役する. */
  public synchronized InsiderRole join(String userId) {
    for (InsiderRole role : roleList) {
      if (userId.equals(role.getUserId())) {
        return role;
      }
    }
    if (roleList.size() >= villageSize) {
      return null;
    }

    InsiderRole seated = new InsiderRole(null, userId);
    roleList.add(seated);
    // 追加後のsizeが、この参加者の席番号（1始まり）になる
    seated.setRole(roleFor(roleList.size()));
    return seated;
  }

  public synchronized int getInsiderNum() {
    return insiderNum;
  }

  public synchronized int getGmNum() {
    return gmNum;
  }

  public synchronized void setGmNum(int gmNum) {
    this.gmNum = gmNum;
  }

  public synchronized int getVillageSize() {
    return villageSize;
  }

  public synchronized boolean isReverseVillage() {
    return reverseVillage;
  }

  public synchronized boolean isRandomMode() {
    return randomMode;
  }

  public synchronized void setRandomMode(boolean randomMode) {
    this.randomMode = randomMode;
  }

  /**
   * まだ誰も参加していない場合に限り、村を逆村へ切り替える.
   *
   * <p>参加者の有無の確認と切り替えを同一のモニタ上で行う。
   * 別操作にすると、その間に{@link #join(String)}された参加者だけが
   * 通常村の配役を受け取り、村の中で配役が混在する。
   *
   * @return 切り替えた場合true。既に参加者がいる場合false
   */
  public synchronized boolean applyReverseVillage() {
    if (!roleList.isEmpty()) {
      return false;
    }
    reverseVillage = true;
    return true;
  }

  // 持ってなかったらnullを返却
  public synchronized String getMemberRole(String userId) {
    return roleList.stream()
        .filter(dao -> userId.equals(dao.getUserId())).findFirst().orElse(new InsiderRole())
        .getRole();
  }

  /**
   * 席番号から役職を決める. joinからのみ呼ばれる.
   *
   * <p>逆村は通常村の配役のうち、インサイダーと村人を入れ替えたもの。
   * 抽選されたインサイダーの席が村人になり、それ以外がインサイダーになる。
   * GMの席は逆村でも変わらない。
   *
   * @param seat 参加順の席番号（1始まり）。{@code insiderNum}・{@code gmNum}と同じ体系
   * @return 役職名
   */
  private String roleFor(int seat) {
    if (seat == insiderNum) {
      return reverseVillage ? MessageConst.VILLAGE_ROLE : MessageConst.INSIDER_ROLE;
    }
    if (seat == gmNum) {
      return MessageConst.GAMEMASTER_ROLE;
    }
    return reverseVillage ? MessageConst.INSIDER_ROLE : MessageConst.VILLAGE_ROLE;
  }

  public synchronized List<Message> getMessageOwner() {

    List<Message> messages = null;

    String message = villageNum + "村："
        + roleList.size() + "/" + villageSize + "人にお題を配りました。お題は『" + odai + "』です。";

    List<Action> actionList = new ArrayList<Action>();
    actionList.add(new MessageAction("再確認", String.valueOf(villageNum)));
    
    if (message.length() <= 160) {
      ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
          message, actionList);
      messages = Collections.singletonList(new TemplateMessage(message, buttons));
    } else {
      messages = Collections.singletonList(new TextMessage(message));

    }
    return messages;

  }

  public synchronized List<Message> getRoleMessage(String userId) {

    InsiderRole role = roleList.stream().filter(dao -> userId.equals(dao.getUserId())).findFirst()
        .orElse(new InsiderRole());

    List<Message> messages = null;
    String message = null;
    List<Action> actionList = new ArrayList<Action>();
    if (MessageConst.INSIDER_ROLE.equals(role.getRole())) {
      message = "あなたの役職は" + MessageConst.INSIDER_ROLE + "です。お題は『" + odai + "』です。";
      if (message.length() > 60) {
        messages = new ArrayList<Message>();
        messages.add(new TextMessage(message));
        messages.add(getStatusMessage(userId).get(0));
      } else {
        actionList.add(new PostbackAction("入室状況確認", String.valueOf(villageNum)));
        ButtonsTemplateNonTitle buttons = new ButtonsTemplateNonTitle(
            CommonModule.getIllustUrl("INSIDER"),
            message, actionList);
        messages = Collections.singletonList(new TemplateMessage(message, buttons));
      }

    } else if (MessageConst.VILLAGE_ROLE.equals(role.getRole())) {
      message = "あなたの役職は" + MessageConst.VILLAGE_ROLE + "です。";
      ButtonsTemplateNonTitle buttons = new ButtonsTemplateNonTitle(
          CommonModule.getIllustUrl("VILLAGERS"),
          message, Collections.singletonList(
              new PostbackAction("入室状況確認", String.valueOf(villageNum))));
      messages = Collections.singletonList(new TemplateMessage(message, buttons));

    } else if (MessageConst.GAMEMASTER_ROLE.equals(role.getRole())) {
      message = "役職は" + MessageConst.GAMEMASTER_ROLE + "です。\n"
          + roleList.size() + "/" + villageSize + "人にお題を配りました。お題は『" + odai + "』です。";

      // ボタン設定
      actionList.add(new PostbackAction("入室状況確認", String.valueOf(villageNum)));
      if (message.length() <= 60) {
        ButtonsTemplateNonTitle buttons = new ButtonsTemplateNonTitle(
            CommonModule.getIllustUrl("GM"),
            message, actionList);
        messages = Collections.singletonList(new TemplateMessage(message, buttons));

      } else if (message.length() <= 160) {
        ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
            message, actionList);
        messages = Collections.singletonList(new TemplateMessage(message, buttons));
      } else {
        //文字数が長い場合
        messages = new ArrayList<Message>();
        messages.add(new TextMessage(message));
        messages.add(getStatusMessage(userId).get(0));
      }
    } else {
      messages = Collections.singletonList(new TextMessage(MessageConst.DEFAILT_MESSAGE));
    }

    return messages;
  }

  public synchronized List<Message> getStatusMessage(String userId) {
    int inNum = 0;
    for (int i = 0; i < roleList.size(); i++) {
      if (userId.equals(roleList.get(i).getUserId())) {
        inNum = i + 1;
      }
    }
    String message = "あなたは" + inNum + "番目の参加者です。"
        + "\n　入室状況：" + roleList.size() + "/" + villageSize + "人";

    return Collections.singletonList(new TextMessage(message));
  }

}
