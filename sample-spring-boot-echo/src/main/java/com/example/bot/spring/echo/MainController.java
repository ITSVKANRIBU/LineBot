package com.example.bot.spring.echo;

import java.util.Collections;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bot.spring.entity.Village;
import com.example.bot.staticdata.VillageList;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.common.SpecialVillageList;
import com.linecorp.bot.spring.boot.entity.SpecialVillage;

@RestController
public class MainController {

  @GetMapping("/callapi")
  public List<Message> index(String message, String userId) {
    List<Message> messages = messageController(message, userId);
    if (messages == null) {
      messages = Collections.singletonList(new TextMessage("村が作成されていません"));
    }
    return messages;
  }

  private List<Message> messageController(String message, String userId) {
    boolean isNumber = false;
    int number = 0;
    try {
      number = Integer.parseInt(message);
      isNumber = true;

      if (isNumber) {
        if (number > 9999) {
          return getMessageSpecialVillage(userId, number);
        } else if (number > 999) {
          return getMessageVillage(userId, number);
        }
      }
    } catch (NumberFormatException e) {
      return null;
    }

    return null;
  }

  private List<Message> getMessageVillage(String userId, int number) {
    List<Message> messages = null;

    Village village = VillageList.getVillage(number);

    if (village == null) {
      return null;
    }

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
    return messages;
  }

  private List<Message> getMessageSpecialVillage(String userId, int number) {

    List<Message> messages = null;

    SpecialVillage village = SpecialVillageList.getVillage(number);

    if (village == null) {
      return null;
    }

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

    return messages;

  }

}
