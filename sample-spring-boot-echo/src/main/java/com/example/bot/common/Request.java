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
package com.example.bot.common;

import java.net.URI;

import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import com.example.bot.spring.entity.EntryOdai;
import com.example.bot.staticdata.MessageConst;

@EnableAsync
public class Request {

  private static RestTemplate restTemplate = new RestTemplate();

  @Async
  public static void run(String odai, String user) {

    // 履歴取得フラグがある場合のみ
    if (MessageConst.RIREKI_FLG) {
      EntryOdai entryOdai = new EntryOdai(odai, user);
      try {
        RequestEntity<EntryOdai> requestEntity = RequestEntity
            .post(new URI(MessageConst.URL_RIREKI))
            .contentType(MediaType.APPLICATION_JSON)
            .body(entryOdai);
        restTemplate.exchange(requestEntity, String.class);
        System.out.println(requestEntity.toString());
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
}
