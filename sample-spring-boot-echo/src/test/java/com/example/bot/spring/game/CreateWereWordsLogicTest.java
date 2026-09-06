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

package com.example.bot.spring.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.example.bot.testing.GameFixture;

/**
 * Werewordsの配布メッセージを固定する.
 *
 * <p>配役はshuffleされるため個々の割り当ては検証しない。
 * 配布数と「欠け」の扱いという、人数に対する不変条件だけを固定する。
 */
public class CreateWereWordsLogicTest {

  private GameFixture fixture;
  private CreateWereWordsLogic logic;

  @Before
  public void setUp() {
    fixture = new GameFixture();
    logic = fixture.createWereWords;
  }

  @Test
  public void godModeDistributesOneMessagePerParticipant() {
    List<String> messages = logic.getMessages(true, 5, "すいか");

    assertEquals(5, messages.size());
    assertTrue("先頭はGM向けメッセージ", messages.get(0).startsWith("あなたの役職はGMです。"));
    assertTrue("GMには欠けた役職が伝わる", messages.get(0).contains("が欠けています。"));
  }

  @Test
  public void withoutGodModeAnExtraVillagerMessageIsAdded() {
    // GMが役掛けで入室するため、参加人数より1件多く配る
    List<String> messages = logic.getMessages(false, 5, "すいか");

    assertEquals(6, messages.size());
    assertEquals("あなたの役職は村人です", messages.get(0));
  }

  @Test
  public void everyMessageIsAssigned() {
    for (String message : logic.getMessages(true, 4, "すいか")) {
      assertNotNull(message);
      assertTrue("空のメッセージは配らない", message.length() > 0);
    }
  }

  /**
   * 欠けた役職が村人のとき、GM向け文言は役職名で「村人」と伝える.
   *
   * <p>役職テーブルの添字3に文章がそのまま入っていたため、GM向けメッセージが
   * 『役職は「あなたの役職は村人です」が欠けています。』になっていた。
   */
  @Test
  public void theMissingRoleIsNamedRatherThanDescribed() {
    assertEquals("村人", CommonSubLogic.getWereRole(3));

    // 欠けの役職はシャッフルで決まるため、村人が先頭に来るまで繰り返す
    boolean sawAMissingVillager = false;
    for (int attempt = 0; attempt < 100; attempt++) {
      String gmMessage = logic.getMessages(true, 5, "すいか").get(0);

      assertEquals("『あなたの役職は』が二重に現れる: " + gmMessage,
          1, countOccurrences(gmMessage, "あなたの役職は"));
      if (gmMessage.contains("役職は「村人」が欠けています。")) {
        sawAMissingVillager = true;
      }
    }
    assertTrue("村人が欠けるケースを引けなかった", sawAMissingVillager);
  }

  @Test
  public void createWereWordsRegistersASpecialVillage() {
    int villageNum = logic.createWereWords(true, 4, "すいか");

    SpecialVillage village = fixture.specialVillages.getVillage(villageNum);
    assertNotNull(village);
    assertEquals(4, village.getMessageList().size());
    assertTrue(villageNum >= 10000 && villageNum <= 99998);
  }

  private static int countOccurrences(String text, String part) {
    int count = 0;
    for (int from = text.indexOf(part); from >= 0; from = text.indexOf(part, from + 1)) {
      count++;
    }
    return count;
  }
}
