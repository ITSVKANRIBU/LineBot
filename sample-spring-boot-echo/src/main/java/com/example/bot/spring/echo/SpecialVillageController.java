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

import java.io.BufferedReader;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bot.spring.game.CreateVillage;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** HTTP adapter for the special-village creation form. */
@RestController
public class SpecialVillageController {

  /**
   * 1つの村へ配れるメッセージ数の上限.
   *
   * <p>通常村は101以上の入力を村番号として扱うため、参加人数の上限が100人。
   * 特殊村も同じ上限に揃える。
   */
  static final int MAX_MESSAGES = 100;

  /** メッセージ長の上限。LINEのテキストメッセージが5000文字まで. */
  static final int MAX_MESSAGE_LENGTH = 5000;

  private final ObjectMapper objectMapper = new ObjectMapper()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final CreateVillage createVillage;

  public SpecialVillageController(CreateVillage createVillage) {
    this.createVillage = createVillage;
  }

  /**
   * リクエスト本文の{@code message}配列から特殊村を作成する.
   *
   * @param request 生のリクエスト。本文は{@code {"message":[...]}}形式
   * @param response nosniffヘッダ付与のためのレスポンス
   * @return 採番された村番号を持つ{@code {"data":"<番号>"}}。不正な本文はHTTP 400
   */
  @RequestMapping("/specialvillage")
  @CrossOrigin
  public ResponseEntity<Map<String, String>> create(
      HttpServletRequest request, HttpServletResponse response) {
    response.addHeader("X-Content-Type-Options", "nosniff");

    try {
      StringBuilder body = new StringBuilder();
      try (BufferedReader reader = request.getReader()) {
        String line;
        while ((line = reader.readLine()) != null) {
          body.append(line);
        }
      }

      // 型付きDTOで読むことで、要素が文字列であることをJacksonに担保させる
      SpecialVillageRequest payload =
          objectMapper.readValue(body.toString(), SpecialVillageRequest.class);
      List<String> messages = payload.getMessage();
      if (!isDeliverable(messages)) {
        return ResponseEntity.badRequest().build();
      }

      int villageNumber = createVillage.createNewVillage(messages);
      return ResponseEntity.ok(Collections.singletonMap(
          "data", String.valueOf(villageNumber)));
    } catch (Exception e) {
      // 不正なJSON・型不一致はすべてクライアント起因として400へ丸める
      return ResponseEntity.badRequest().build();
    }
  }

  /**
   * 登録前に、村が使用可能かつ配信可能かを判定する.
   *
   * <p>空の配列は誰も参加できない村になり、長すぎるメッセージはLINEが送信を拒否する。
   * どちらも登録後には気付けないため、採番する前に弾く。
   */
  private boolean isDeliverable(List<String> messages) {
    if (messages == null || messages.isEmpty() || messages.size() > MAX_MESSAGES) {
      return false;
    }
    for (String message : messages) {
      // nullと空文字はSpecialVillage側で「メッセージは特にありません。」に置き換わる
      if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
        return false;
      }
    }
    return true;
  }

  /** {@code POST /specialvillage}の本文. */
  public static final class SpecialVillageRequest {
    private List<String> message;

    public List<String> getMessage() {
      return message;
    }

    public void setMessage(List<String> message) {
      this.message = message;
    }
  }
}
