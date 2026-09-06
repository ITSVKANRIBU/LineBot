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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.example.bot.staticdata.MessageConst;

/**
 * イラストカタログの取得先と、取得できない場合の挙動を固定する.
 *
 * <p>ネットワークへは出ない。実際の取得は{@code @Scheduled}から行われる。
 */
public class CommonModuleTest {

  /**
   * 取得先はデプロイURLでなければならない.
   *
   * <p>{@code /exec}をブラウザで開くと{@code script.googleusercontent.com/macros/echo}へ
   * 転送される。その転送先URLを貼ると、{@code user_content_key}の失効後に400を返し続け、
   * イラストが固定画像へ落ちたまま戻らなくなる。過去に実際に発生している。
   */
  @Test
  public void catalogUrlIsADeploymentUrlNotItsRedirectTarget() {
    assertTrue("デプロイURLを指定すること: " + CommonModule.URL,
        CommonModule.URL.startsWith("https://script.google.com/macros/s/"));
    assertTrue("デプロイURLは/execで終わる: " + CommonModule.URL,
        CommonModule.URL.endsWith("/exec"));
    assertFalse("転送先URLはuser_content_keyが失効するため使えない: " + CommonModule.URL,
        CommonModule.URL.contains("googleusercontent.com"));
  }

  /** カタログを取得できていない間は、MessageConstの固定画像へ落とす. */
  @Test
  public void fallsBackToTheFixedIllustrationWhileTheCatalogIsUnavailable() {
    assertEquals(MessageConst.INSIDER_URL, CommonModule.getIllustUrl("INSIDER"));
    assertEquals(MessageConst.VILLAGERS_URL, CommonModule.getIllustUrl("VILLAGERS"));
    assertEquals(MessageConst.GM_URL, CommonModule.getIllustUrl("GM"));
    assertEquals(MessageConst.GOD_URL, CommonModule.getIllustUrl("GOD"));
  }

  /**
   * ファイル名が{@code <役職名>_<重み>_<任意>}の3部構成でない要素は取り込まない.
   *
   * <p>カタログはBotのリリースと無関係に差し替えられるため、規約外の名前が混ざり得る。
   */
  @Test
  public void catalogEntriesWithoutAThreePartNameAreIgnored() {
    Map<String, ArrayList<Integer>> ratioMap = new HashMap<String, ArrayList<Integer>>();
    Map<String, ArrayList<String>> urlMap = new HashMap<String, ArrayList<String>>();

    CommonModule.parseCatalog(Arrays.asList(
        catalogEntry("INSIDER_2_a.png", "https://example.com/insider.png"),
        catalogEntry("VILLAGERS.png", "https://example.com/no-weight.png"),
        catalogEntry("GM_1_b_c.png", "https://example.com/too-many-parts.png")),
        ratioMap, urlMap);

    assertEquals(Collections.singleton("INSIDER"), ratioMap.keySet());
    // 重み2は抽選枠を2つ持つ
    assertEquals(Arrays.asList(0, 0), ratioMap.get("INSIDER"));
    assertEquals(Collections.singletonList("https://example.com/insider.png"),
        urlMap.get("INSIDER"));
  }

  private static Map<String, String> catalogEntry(String name, String url) {
    Map<String, String> element = new HashMap<String, String>();
    element.put("name", name);
    element.put("url", url);
    return element;
  }
}
