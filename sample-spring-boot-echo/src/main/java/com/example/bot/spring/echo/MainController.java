package com.example.bot.spring.echo;

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bot.spring.entity.Village;
import com.example.bot.staticdata.VillageList;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.spring.game.SpecialVillage;

@RestController
public class MainController {

  @GetMapping("/callapi")
  @CrossOrigin
  public ResponseEntity<List<Message>> index(
      @RequestParam(value = "message", required = false) String message,
      @RequestParam(value = "userId", required = false) String userId) {
    if (message == null || message.trim().isEmpty()
        || userId == null || userId.trim().isEmpty()) {
      return ResponseEntity.badRequest().build();
    }

    List<Message> messages = messageController(message, userId);
    if (messages == null) {
      messages = Collections.singletonList(new TextMessage("村が作成されていません"));
    }
    return ResponseEntity.ok(messages);
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
        if (village.join(userId) == null) {
          messages = Collections.singletonList(new TextMessage("村がいっぱいです。"));
        } else {
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
        if (!village.join(userId)) {
          messages = Collections.singletonList(new TextMessage("村がいっぱいです。"));
        } else {
          messages = village.getRoleMessage(userId);
        }
      }
    }

    return messages;

  }

}
