package com.example.bot.spring.game;

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
