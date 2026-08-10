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

import java.util.Collections;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bot.spring.game.VillageService;

import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;

@RestController
public class MainController {

  /**
   * 村への参加・作成・設定をLINEメッセージ形式のJSONで返す.
   *
   * @param message 村番号、または「お題」「題」「神」「人数」「お題文字列」
   * @param userId 呼び出し元が指定する参加者識別子
   * @return LINE Message APIのJSON配列。必須parameter不足はHTTP 400
   */
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
    int number;
    try {
      number = Integer.parseInt(message);
    } catch (NumberFormatException e) {
      return nonNumberMessage(message, userId);
    }

    if (number > 9999) {
      return VillageService.joinSpecialVillage(userId, number);
    } else if (number > 999) {
      return VillageService.joinVillage(userId, number);
    } else {
      return VillageService.setVillageSize(userId, number);
    }
  }

  private List<Message> nonNumberMessage(String message, String userId) {
    String command = message.trim();

    if ("お題".equals(command) || "題".equals(command) || "神".equals(command)) {
      return VillageService.createVillage(userId, "神".equals(command));
    }

    // お題設定の場合
    return VillageService.setOdai(userId, message);
  }
}
