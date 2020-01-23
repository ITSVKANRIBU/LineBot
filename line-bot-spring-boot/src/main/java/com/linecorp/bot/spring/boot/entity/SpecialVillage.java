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

package com.linecorp.bot.spring.boot.entity;

import java.util.Collections;
import java.util.List;

import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

public class SpecialVillage {

  private int villageNum;
  private String ownerId;
  private List<String> userList;
  private List<String> messageList;

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

  public List<String> getUserList() {
    return userList;
  }

  public void setUserList(List<String> userList) {
    this.userList = userList;
  }

  public List<String> getMessageList() {
    return messageList;
  }

  public void setMessageList(List<String> messageList) {
    this.messageList = messageList;
  }

  /**
   * メッセージ取得.
   * 参加していない場合、null
   * @param userId ユーザーID
   * @return
   */
  public List<Message> getRoleMessage(String userId) {
    List<Message> messages = null;
    String message = null;

    for (int i = 0; i < userList.size(); i++) {
      if (userId.equals(userList.get(i))) {
        message = this.messageList.get(i);
        if (message == null || "".equals(message)) {
          message = "メッセージは特にありません。";
        }
      }
    }
    if (message != null) {

      ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
          message, Collections.singletonList(
              new PostbackAction("入室状況確認", String.valueOf(getVillageNum()))));
      messages = Collections.singletonList(new TemplateMessage(message, buttons));

      return messages;
    }

    return null;

  }

  /**
   * ステータス確認.
   * @param userId ユーザーID
   * @return List メッセージリスト
   */
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

  /**
   * 参加確認.
   * @param userId ユーザーID
   * @return boolean
   */
  public boolean hasMember(String userId) {
    boolean returnFlg = false;
    for (int i = 0; i < userList.size(); i++) {
      if (userId.equals(userList.get(i))) {
        returnFlg = true;
        break;
      }
    }
    return returnFlg;

  }
}
