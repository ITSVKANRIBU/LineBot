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
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.bot.common.CommonModule;
import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.model.action.Action;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.action.URIActionNonAltUri;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonTitle;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

public class Village {

  private int villageNum;
  private String ownerId;
  private String odai;
  private List<InsiderRole> roleList;

  private int insiderNum;
  private int gmNum;
  private int villageSize;

  public Village() {
    roleList = new CopyOnWriteArrayList<InsiderRole>();
  }

  public int getVillageNum() {
    return villageNum;
  }

  public void setVillageNum(int villageNum) {
    this.villageNum = villageNum;
  }

  public String getOwnerId() {
    return ownerId;
  }

  public void setOwnerId(String ownerId) {
    this.ownerId = ownerId;
  }

  public String getOdai() {
    return odai;
  }

  public void setOdai(String odai) {
    this.odai = odai;
  }

  public List<InsiderRole> getRoleList() {
    return roleList;
  }

  public void setRoleList(List<InsiderRole> roleList) {
    this.roleList = roleList;
  }

  public void addRoleList(String role, String userId) {
    roleList.add(new InsiderRole(role, userId));
  }

  public boolean hasOwner(String userId) {
    return ownerId.equals(userId);
  }

  public int getInsiderNum() {
    return insiderNum;
  }

  public void setInsiderNum(int insiderNum) {
    this.insiderNum = insiderNum;
  }

  public int getGmNum() {
    return gmNum;
  }

  public void setGmNum(int gmNum) {
    this.gmNum = gmNum;
  }

  public int getVillageSize() {
    return villageSize;
  }

  public void setVillageSize(int villageSize) {
    this.villageSize = villageSize;
  }

  // 持ってなかったらnullを返却
  public String getMemberRole(String userId) {
    return roleList.stream()
        .filter(dao -> userId.equals(dao.getUserId())).findFirst().orElse(new InsiderRole())
        .getRole();
  }

  // 役職の設定処理
  public synchronized InsiderRole setInsiderRole(String userId) {
    InsiderRole returnRole = null;
    for (int i = 0; i < roleList.size(); i++) {
      if (userId.equals(roleList.get(i).getUserId())) {
        // 順番設定
        roleList.get(i).setIndex(i);
        // 役職設定
        if (i + 1 == insiderNum) {
          roleList.get(i).setRole(MessageConst.INSIDER_ROLE);
        } else if (i + 1 == gmNum) {
          roleList.get(i).setRole(MessageConst.GAMEMASTER_ROLE);
        } else {
          roleList.get(i).setRole(MessageConst.VILLAGE_ROLE);
        }
        returnRole = roleList.get(i);
        break;
      }
    }
    return returnRole;
  }

  public List<Message> getMessageOwner() {

    List<Message> messages = null;

    String message = villageNum + "村："
        + roleList.size() + "/" + villageSize + "人にお題を配りました。お題は『" + odai + "』です。";

    List<Action> actionList = new ArrayList<Action>();
    actionList.add(new MessageAction("再確認", String.valueOf(villageNum)));
    actionList.add(new PostbackAction("お題を投稿", odai));

    if (message.length() <= 160) {
      ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
          message, actionList);
      messages = Collections.singletonList(new TemplateMessage(message, buttons));
    } else {
      messages = Collections.singletonList(new TextMessage(message));

    }
    return messages;

  }

  public List<Message> getRoleMessage(String userId) {

    InsiderRole role = roleList.stream().filter(dao -> userId.equals(dao.getUserId())).findFirst()
        .orElse(new InsiderRole());

    List<Message> messages = null;
    String message = null;
    List<Action> actionList = new ArrayList<Action>();
    String searchWord = null;
    if (odai != null) {
      searchWord = odai.trim().replace("\r", "").replace("\n", "");
      searchWord = searchWord.replace(" ", "").replace("　", "").replace("\t", "");
    }
    if (MessageConst.INSIDER_ROLE.equals(role.getRole())) {
      message = "あなたの役職は" + MessageConst.INSIDER_ROLE + "です。お題は『" + odai + "』です。";
      if (message.length() > 60) {
        messages = Collections.singletonList(new TextMessage(message));
        messages.add(getStatusMessage(userId).get(0));
      } else {
        actionList.add(new PostbackAction("入室状況確認", String.valueOf(villageNum)));
        actionList.add(new URIActionNonAltUri("ググる",
            "https://www.google.com/search?q=" + searchWord));
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
      actionList.add(new URIActionNonAltUri("ググる",
          "https://www.google.com/search?q=" + searchWord));

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
        messages = Collections.singletonList(new TextMessage(message));
        messages.add(getStatusMessage(userId).get(0));
      }
    } else {
      messages = Collections.singletonList(new TextMessage(MessageConst.DEFAILT_MESSAGE));
    }

    return messages;
  }

  public List<Message> getStatusMessage(String userId) {
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
