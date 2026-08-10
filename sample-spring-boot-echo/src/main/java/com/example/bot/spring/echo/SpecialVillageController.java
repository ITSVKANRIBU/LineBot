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

import com.example.bot.spring.game.CreatVillage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/** HTTP adapter for the special-village creation form. */
@RestController
public class SpecialVillageController {

  private final ObjectMapper objectMapper = new ObjectMapper();

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

      Map<String, Object> payload = objectMapper.readValue(
          body.toString(), new TypeReference<Map<String, Object>>() { });
      @SuppressWarnings("unchecked")
      List<String> messages = (List<String>) payload.get("message");
      if (messages == null) {
        return ResponseEntity.badRequest().build();
      }

      int villageNumber = new CreatVillage().createNewVillage(messages);
      return ResponseEntity.ok(Collections.singletonMap(
          "data", String.valueOf(villageNumber)));
    } catch (Exception e) {
      // 不正なJSON・型不一致はすべてクライアント起因として400へ丸める
      return ResponseEntity.badRequest().build();
    }
  }
}
