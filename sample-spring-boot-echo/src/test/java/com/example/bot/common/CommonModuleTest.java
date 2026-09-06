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
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Test;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.example.bot.common.CommonModule.CatalogFile;
import com.example.bot.common.CommonModule.WeightedUrl;
import com.example.bot.staticdata.MessageConst;

/**
 * イラストカタログの取得先、抽選の分布、取得できない場合の挙動を固定する.
 *
 * <p>ネットワークへは出ない。実際の取得は{@code @Scheduled}から行われる。
 * 解析と抽選はstaticなカタログから切り離してあるため、他のテストへ影響しない。
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

/**
   * カタログ取得にタイムアウトが設定されている.
   *
   * <p>Apps Scriptが無応答のとき、タイムアウトがないと定期取得のスレッドが
   * 永久に固まり、以降カタログが更新されなくなる。
   */
  @Test
  public void theCatalogFetchIsBoundedByTimeouts() {
    assertEquals(15000, CommonModule.CONNECT_TIMEOUT_MILLIS);
    assertEquals(30000, CommonModule.READ_TIMEOUT_MILLIS);

    RestTemplate restTemplate =
        (RestTemplate) ReflectionTestUtils.getField(CommonModule.class, "restTemplate");
    ClientHttpRequestFactory factory = restTemplate.getRequestFactory();

    assertEquals(CommonModule.CONNECT_TIMEOUT_MILLIS,
        ReflectionTestUtils.getField(factory, "connectTimeout"));
    assertEquals(CommonModule.READ_TIMEOUT_MILLIS,
        ReflectionTestUtils.getField(factory, "readTimeout"));
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
    Map<String, List<WeightedUrl>> parsed = CommonModule.parseCatalog(Arrays.asList(
        catalogEntry("INSIDER_2_a.png", "https://example.com/insider.png"),
        catalogEntry("VILLAGERS.png", "https://example.com/no-weight.png"),
        catalogEntry("GM_1_b_c.png", "https://example.com/too-many-parts.png")));

    assertEquals(Collections.singleton("INSIDER"), parsed.keySet());
    assertEquals("https://example.com/insider.png",
        CommonModule.getIllustUrl("INSIDER", parsed, new FixedRandom(0, 1)));
    // 取り込まれなかった役職は既定画像へ落ちる
    assertEquals(MessageConst.VILLAGERS_URL,
        CommonModule.getIllustUrl("VILLAGERS", parsed, new FixedRandom(0)));
  }

  /**
   * 重みが整数でない要素、URLのない要素は、その要素だけ読み飛ばして残りを取り込む.
   *
   * <p>1件の書き間違いで全役職のイラストが既定画像へ戻ると、原因が分からないまま
   * 見た目だけが変わる。カタログはBotのリリースと無関係に差し替えられるため、
   * 書き間違いは起こりうるものとして扱う。
   */
  @Test
  public void anUnusableEntryIsSkippedWithoutDroppingTheRestOfTheCatalog() {
    Map<String, List<WeightedUrl>> parsed = CommonModule.parseCatalog(Arrays.asList(
        catalogEntry("INSIDER_おおい_broken.png", "https://example.com/broken.png"),
        catalogEntry("GM_1_no-url.png", null),
        catalogEntry(null, "https://example.com/no-name.png"),
        catalogEntry("VILLAGERS_1_ok.png", "https://example.com/villagers.png")));

    assertEquals(Collections.singleton("VILLAGERS"), parsed.keySet());
    assertEquals("https://example.com/villagers.png",
        CommonModule.getIllustUrl("VILLAGERS", parsed, new FixedRandom(0)));
    // 読み飛ばされた役職は既定画像へ落ちる
    assertEquals(MessageConst.INSIDER_URL,
        CommonModule.getIllustUrl("INSIDER", parsed, new FixedRandom(0)));
    assertEquals(MessageConst.GM_URL,
        CommonModule.getIllustUrl("GM", parsed, new FixedRandom(0)));
  }

  /** 重み1と3のとき、抽選枠は1:3に分かれる. */
  @Test
  public void weightsDecideHowManyDrawSlotsAFileGets() {
    Map<String, List<WeightedUrl>> parsed = CommonModule.parseCatalog(Arrays.asList(
        catalogEntry("INSIDER_1_rare.png", "https://example.com/rare.png"),
        catalogEntry("INSIDER_3_common.png", "https://example.com/common.png")));

    // 総重み4のうち、0番目の枠だけがrare、1〜3番目がcommon
    assertEquals("https://example.com/rare.png", drawn(parsed, 0));
    assertEquals("https://example.com/common.png", drawn(parsed, 1));
    assertEquals("https://example.com/common.png", drawn(parsed, 3));

    assertEquals("抽選の範囲は総重み", 4, boundPassedToRandom(parsed));
  }

  /** 重みが0や負数のファイルは抽選枠を持たないため選ばれない. */
  @Test
  public void filesWithoutAPositiveWeightAreNeverDrawn() {
    Map<String, List<WeightedUrl>> parsed = CommonModule.parseCatalog(Arrays.asList(
        catalogEntry("INSIDER_0_zero.png", "https://example.com/zero.png"),
        catalogEntry("INSIDER_-2_negative.png", "https://example.com/negative.png"),
        catalogEntry("INSIDER_1_only.png", "https://example.com/only.png")));

    assertEquals(1, boundPassedToRandom(parsed));
    assertEquals("https://example.com/only.png", drawn(parsed, 0));
  }

  /** 総重みが0の役職と、空のカタログは既定画像へ落とす. */
  @Test
  public void aRoleWithoutAnyPositiveWeightFallsBackToTheFixedIllustration() {
    Map<String, List<WeightedUrl>> allZero = CommonModule.parseCatalog(Arrays.asList(
        catalogEntry("INSIDER_0_a.png", "https://example.com/a.png"),
        catalogEntry("INSIDER_0_b.png", "https://example.com/b.png")));

    assertEquals(MessageConst.INSIDER_URL,
        CommonModule.getIllustUrl("INSIDER", allZero, new FixedRandom(0)));
    assertEquals(MessageConst.INSIDER_URL, CommonModule.getIllustUrl(
        "INSIDER", CommonModule.parseCatalog(new ArrayList<CatalogFile>()), new FixedRandom(0)));
  }

  private String drawn(Map<String, List<WeightedUrl>> parsed, int value) {
    return CommonModule.getIllustUrl("INSIDER", parsed, new FixedRandom(value));
  }

  /** 抽選に渡された上限（＝その役職の総重み）を覗く. */
  private int boundPassedToRandom(Map<String, List<WeightedUrl>> parsed) {
    RecordingRandom random = new RecordingRandom();
    CommonModule.getIllustUrl("INSIDER", parsed, random);
    return random.bound;
  }

  private static CatalogFile catalogEntry(String name, String url) {
    CatalogFile file = new CatalogFile();
    file.setName(name);
    file.setUrl(url);
    return file;
  }

  /** 指定した値を順に返す{@link Random}. */
  private static final class FixedRandom extends Random {
    private static final long serialVersionUID = 1L;

    private final int[] values;
    private int index;

    FixedRandom(int... values) {
      this.values = values.clone();
    }

    @Override
    public int nextInt(int bound) {
      return values[index++];
    }
  }

  /** 渡された上限を記録する{@link Random}. */
  private static final class RecordingRandom extends Random {
    private static final long serialVersionUID = 1L;

    private int bound;

    @Override
    public int nextInt(int bound) {
      this.bound = bound;
      return 0;
    }
  }
}
