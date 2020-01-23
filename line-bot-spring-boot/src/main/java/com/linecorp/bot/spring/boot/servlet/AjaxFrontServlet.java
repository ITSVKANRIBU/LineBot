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
package com.linecorp.bot.spring.boot.servlet;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.ui.Model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.linecorp.bot.spring.boot.logic.CreatVillage;

public class AjaxFrontServlet {
  /**
   * 修正箇所.
   */
  public void doServlet(HttpServletRequest request, HttpServletResponse response, Model model) {

    try {

      // レスポンス設定
      response.setContentType("application/json; charset=utf-8");
      response.addHeader("X-Content-Type-Options", "nosniff");

      String reqJson = null;

      // 送信されたJSONの取得
      try (BufferedReader buffer = new BufferedReader(request.getReader())) {
        reqJson = buffer.readLine();
      }

      // JSONをオブジェクトに変更
      ObjectMapper mapper = new ObjectMapper();
      Map<String, Object> reqMap = mapper.readValue(reqJson, new TypeReference<Map<String, Object>>() {
      });

      @SuppressWarnings("unchecked")
      List<String> messageList = (ArrayList<String>) reqMap.get("message");

      CreatVillage logic = new CreatVillage();
      int createVillageNum = logic.createNewVillage(messageList);

      PrintWriter out = response.getWriter();
      out.println("{  \"data\" : \"" + createVillageNum + "\" }");
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
      try {
        response.sendError(HttpServletResponse.SC_BAD_REQUEST);
      } catch (IOException e1) {
        e1.printStackTrace();
      }

    }
  }

}
