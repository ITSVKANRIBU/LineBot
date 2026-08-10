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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

/**
 * 特殊村の状態.
 *
 * <p>配布メッセージはフォーム入力とワーワーズのお題の双方に由来し、
 * どちらも長さの上限がない。{@code ButtonsTemplateNonURL}のtextは
 * 画像・タイトルなしで160文字までのため、超過分はテキストへ振り分ける。
 */
public class SpecialVillage {

  /** 画像・タイトルなしのボタンテンプレートに収まるtextの上限. */
  private static final int BUTTONS_TEMPLATE_TEXT_MAX = 160;

  private int villageNum;
  private String ownerId;
  private List<String> userList;
  private List<String> messageList;

  public int getVillageNum() { return villageNum; }
  public void setVillageNum(int villageNum) { this.villageNum = villageNum; }
  public String getOwnerId() { return ownerId; }
  public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
  public List<String> getUserList() { return userList; }
  public void setUserList(List<String> userList) { this.userList = userList; }
  public List<String> getMessageList() { return messageList; }
  public void setMessageList(List<String> messageList) { this.messageList = messageList; }

  public List<Message> getRoleMessage(String userId) {
    String message = null;
    for (int i = 0; i < userList.size(); i++) {
      if (userId.equals(userList.get(i))) {
        message = messageList.get(i);
        if (message == null || message.isEmpty()) {
          message = "メッセージは特にありません。";
        }
      }
    }
    if (message == null) {
      return null;
    }

    if (message.length() > BUTTONS_TEMPLATE_TEXT_MAX) {
      // ボタンに収まらない場合はテキストで送り、入室状況を続けて伝える
      List<Message> messages = new ArrayList<Message>();
      messages.add(new TextMessage(message));
      messages.add(getStatusMessage(userId).get(0));
      return messages;
    }

    ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(message,
        Collections.singletonList(new PostbackAction("入室状況確認", String.valueOf(villageNum))));
    return Collections.singletonList(new TemplateMessage(message, buttons));
  }

  public List<Message> getStatusMessage(String userId) {
    int inNum = 0;
    for (int i = 0; i < userList.size(); i++) {
      if (userId.equals(userList.get(i))) {
        inNum = i + 1;
      }
    }
    String message = "あなたは" + inNum + "番目の参加者です。"
        + "\n　入室状況：" + userList.size() + "/" + messageList.size() + "人";
    return Collections.singletonList(new TextMessage(message));
  }

  public boolean hasMember(String userId) {
    for (String member : userList) {
      if (userId.equals(member)) {
        return true;
      }
    }
    return false;
  }

  /** Atomically joins a user while preserving the message order. */
  public synchronized boolean join(String userId) {
    if (hasMember(userId)) {
      return true;
    }
    if (userList.size() >= messageList.size()) {
      return false;
    }
    userList.add(userId);
    return true;
  }
}
