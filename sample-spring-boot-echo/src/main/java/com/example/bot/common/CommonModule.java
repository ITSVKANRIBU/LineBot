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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CommonModule {

  private static final RestTemplate restTemplate = new RestTemplate();

  /**
   * イラスト一覧を返すGoogle Apps ScriptのウェブアプリURL.
   *
   * <p>必ずデプロイURL（{@code /macros/s/<デプロイID>/exec}）を指定する。
   * ブラウザでこのURLを開くと{@code script.googleusercontent.com/macros/echo}へ
   * 302で転送されるが、転送先の{@code user_content_key}は一時的な発行物で
   * いずれ失効し、以降は400を返し続ける。転送先URLを貼ってはならない。
   * GETのリダイレクト追跡は{@link RestTemplate}の既定動作で行われる。
   */
  static final String URL =
      "https://script.google.com/macros/s/"
      + "AKfycbyy5RZiz_11ylnVV4NB4kToZv6Qv9ecXkxRgnWo9yvE_AxNLvM/exec";
  /** 役職名 → URLリストの添字を重みぶん並べたリスト。カタログ取得のたびに差し替える. */
  private static Map<String, ArrayList<Integer>> illustrationRtioMap =
      new HashMap<String, ArrayList<Integer>>();
  /** 役職名 → URLリスト。{@link #illustrationRtioMap}と同時に差し替える. */
  private static Map<String, ArrayList<String>> illustrationUrlMap =
      new HashMap<String, ArrayList<String>>();

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
      // 総重みが0の役職ではnextInt(0)が例外になる。既定画像へ落とす
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

      parseCatalog(dataList, tmpdataMap, tmpUrlMap);

      illustrationRtioMap = tmpdataMap;
      illustrationUrlMap = tmpUrlMap;

    } catch (Exception e) {
      // 取得できなければMessageConstの標準画像へfallbackする
      log.warn("Failed to refresh the illustration catalog", e);
    }

  }

  /**
   * カタログ応答の{@code files}要素を、役職ごとの重みリストとURLリストへ展開する.
   *
   * <p>HTTPから切り離してあるため、解析だけを単体テストできる。
   * 呼び出し元が渡すmapへ書き込むだけで、staticなカタログには触れない。
   *
   * @param dataList {@code files}配列。各要素は{@code name}と{@code url}を持つmap
   * @param ratioMap 役職名 → URLリストの添字を重みぶん並べたリスト
   * @param urlMap 役職名 → URLリスト
   */
  @SuppressWarnings("rawtypes")
  static void parseCatalog(List dataList,
      Map<String, ArrayList<Integer>> ratioMap, Map<String, ArrayList<String>> urlMap) {
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
      if (ratioMap.containsKey(fileNameArray[0])) {

        //urlListに追加
        int urlListindex = urlMap.get(fileNameArray[0]).size();
        urlMap.get(fileNameArray[0]).add(map.get("url"));

        for (int i = 0; i < Integer.parseInt(fileNameArray[1]); i++) {
          ratioMap.get(fileNameArray[0]).add(urlListindex);
        }
      } else {

        ArrayList<Integer> newIntList = new ArrayList<Integer>();

        for (int i = 0; i < Integer.parseInt(fileNameArray[1]); i++) {
          newIntList.add(0);
        }
        // 追加
        ratioMap.put(fileNameArray[0], newIntList);

        ArrayList<String> newStrList = new ArrayList<String>();
        newStrList.add(map.get("url"));
        urlMap.put(fileNameArray[0], newStrList);

      }
    }
  }
}
