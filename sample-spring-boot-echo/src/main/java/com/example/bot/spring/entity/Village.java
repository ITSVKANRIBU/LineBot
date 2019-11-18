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

import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

public class Village {

  private int villageNum;
  private String ownerId;
  private String odai;
  private ArrayList<InsiderRole> roleList;

  private int insiderNum;
  private int gmNum;
  private int villageSize;

  public Village() {
    roleList = new ArrayList<InsiderRole>();
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

  public ArrayList<InsiderRole> getRoleList() {
    return roleList;
  }

  public void setRoleList(ArrayList<InsiderRole> roleList) {
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

  public List<Message> getMessageOwner() {

    String message = villageNum + "村："
        + roleList.size() + "/" + villageSize + "人にお題を配りました。お題は『" + odai + "』です。";

    ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
        message, Collections.singletonList(
            new MessageAction("再確認", String.valueOf(villageNum))));

    return Collections.singletonList(new TemplateMessage(message, buttons));

  }

  public List<Message> getRoleMessage(String userId) {

    InsiderRole role = roleList.stream().filter(dao -> userId.equals(dao.getUserId())).findFirst()
        .orElse(new InsiderRole());

    List<Message> messages = null;
    String message = null;

    if (MessageConst.INSIDER_ROLE.equals(role.getRole())) {
      message = "あなたの役職は" + MessageConst.INSIDER_ROLE + "です。お題は『" + odai + "』です。";
      messages = Collections.singletonList(new TextMessage(message));

    } else if (MessageConst.VILLAGE_ROLE.equals(role.getRole())) {
      message = "あなたの役職は" + MessageConst.VILLAGE_ROLE + "です。";
      messages = Collections.singletonList(new TextMessage(message));

    } else if (MessageConst.GAMEMASTER_ROLE.equals(role.getRole())) {
      message = "あなたの役職は" + MessageConst.GAMEMASTER_ROLE + "です。\n"
          + roleList.size() + "/" + villageSize + "人にお題を配りました。お題は『" + odai + "』です。";

      ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
          message, Collections.singletonList(
              new MessageAction("再確認", String.valueOf(villageNum))));
      messages = Collections.singletonList(new TemplateMessage(message, buttons));
    } else {
      message = MessageConst.DEFAILT_MESSAGE;
      messages = Collections.singletonList(new TextMessage(message));
    }

    return messages;
  }

}
