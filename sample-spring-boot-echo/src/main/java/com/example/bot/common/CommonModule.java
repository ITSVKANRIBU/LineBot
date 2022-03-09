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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.example.bot.staticdata.MessageConst;

public class CommonModule {

  private static final RestTemplate restTemplate = new RestTemplate();
  private static final String URL = "https://script.googleusercontent.com/macros/echo?user_content_key=aKMvG1GRG6CYrznJw6x0cjbGjD3XCu6DkIARkCZYZB257AnrACX_SIEBnxtbI7z26GYt4zejEm13dha_xv4o3wgiVNcAjd6_m5_BxDlH2jW0nuo2oDemN9CCS2h10ox_1xSncGQajx_ryfhECjZEnCNpoqCEl5Y_84RG7F4nLvpUk-DyE4X5eE_1h0yajhEvYBr9Jf8qIeO625quMX_ShEW-dnclfdIe&lib=Mc3tlEOHbGs_amoROLTGc0nOYk-y_j7OD";
  private static Map<String, ArrayList<Integer>> illustrationRtioMap;
  private static Map<String, ArrayList<String>> illustrationUrlMap;

  public static String getIllustUrl(String roleName) {

    String returnPath = null;
    try {
      if (illustrationRtioMap.containsKey(roleName)) {
        ArrayList<Integer> nameList = illustrationRtioMap.get(roleName);
        Random random = new Random();
        int num = random.nextInt(nameList.size());
        returnPath = illustrationUrlMap.get(roleName).get(nameList.get(num));
      } else {
        return defoltIllustUrl(roleName);
      }
    } catch (Exception e) {
      return defoltIllustUrl(roleName);
    }
    return returnPath;
  }

  private static String defoltIllustUrl(String roleName) {
    switch (roleName) {
    case "INSIDER":
      return MessageConst.INSIDER_URL;

    case "GM":
      return MessageConst.GM_URL;

    case "VILLAGERS":
      return MessageConst.VILLAGERS_URL;

    case "GOD":
      return MessageConst.GOD_URL;

    }
    return null;
  }

  @SuppressWarnings("rawtypes")
  public static void createMap() {

    try {
      ResponseEntity<Map> responseEntity = restTemplate.getForEntity(URL, Map.class);

      Map res = responseEntity.getBody();

      if (res == null) {
        return;
      }

      List dataList = (List) res.get("files");
      Map<String, ArrayList<Integer>> tmpdataMap = new HashMap<String, ArrayList<Integer>>();
      Map<String, ArrayList<String>> tmpUrlMap = new HashMap<String, ArrayList<String>>();

      for (Object object : dataList) {
        @SuppressWarnings("unchecked")
        Map<String, String> map = (Map<String, String>) object;

        String name = map.get("name");
        String[] fileNameArray = name.split("_");

        // エラーチェック
        if (fileNameArray.length != 3) {
          continue;
        }

        // ある場合
        if (tmpdataMap.containsKey(fileNameArray[0])) {

          //urlListに追加
          int urlListindex = tmpUrlMap.get(fileNameArray[0]).size();
          tmpUrlMap.get(fileNameArray[0]).add(map.get("url"));

          for (int i = 0; i < Integer.parseInt(fileNameArray[1]); i++) {
            tmpdataMap.get(fileNameArray[0]).add(urlListindex);
          }
        } else {

          ArrayList<Integer> newIntList = new ArrayList<Integer>();

          for (int i = 0; i < Integer.parseInt(fileNameArray[1]); i++) {
            newIntList.add(0);
          }
          // 追加
          tmpdataMap.put(fileNameArray[0], newIntList);

          ArrayList<String> newStrList = new ArrayList<String>();
          newStrList.add(map.get("url"));
          tmpUrlMap.put(fileNameArray[0], newStrList);

        }
      }

      illustrationRtioMap = tmpdataMap;
      illustrationUrlMap = tmpUrlMap;

    } catch (Exception e) {
      e.printStackTrace();
    }

  }
}
