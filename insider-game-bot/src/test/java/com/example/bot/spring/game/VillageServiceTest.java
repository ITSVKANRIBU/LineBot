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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.example.bot.staticdata.MessageConst;
import com.example.bot.testing.GameFixture;

import com.example.bot.testing.FixedRandom;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;

/**
 * 人数設定の配役抽選を、乱数を固定して検証する.
 *
 * <p>{@code setVillageSize}のseam経由で乱数を差し替える。production呼び出しは
 * 引数なしのオーバーロードのままで、挙動は変わらない。
 */
public class VillageServiceTest {

  private static final String OWNER = "owner-user";

  private GameFixture fixture;
  private VillageService service;

  @Before
  public void setUp() {
    fixture = new GameFixture();
    service = fixture.villageService;
  }

  @Test
  public void insiderPositionFollowsTheDraw() {
    service.createVillage(OWNER, false);

    // インサイダーを3番目に固定する
    service.setVillageSize(OWNER, 5, new FixedRandom(2));

    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);
    assertEquals(3, village.getInsiderNum());
    assertEquals(5, village.getVillageSize());

    joinAll(village, "u1", "u2", "u3", "u4", "u5");
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("u3"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("u1"));
  }

  @Test
  public void godModeDrawsAGameMasterDistinctFromTheInsider() {
    service.createVillage(OWNER, true);

    // インサイダーは2番目、GMは1回衝突してから4番目に決まる
    service.setVillageSize(OWNER, 5, new FixedRandom(1, 1, 3));

    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);
    assertEquals(2, village.getInsiderNum());
    assertEquals(4, village.getGmNum());

    joinAll(village, "u1", "u2", "u3", "u4", "u5");
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("u2"));
    assertEquals(MessageConst.GAMEMASTER_ROLE, village.getMemberRole("u4"));
  }

  @Test
  public void godModeUsesTheGodIllustrationAndNormalModeUsesGm() {
    service.createVillage(OWNER, true);
    assertEquals(MessageConst.GOD_URL,
        thumbnailOf(service.setVillageSize(OWNER, 3, new FixedRandom(0, 1))));

    service.createVillage("other-user", false);
    assertEquals(MessageConst.GM_URL,
        thumbnailOf(service.setVillageSize("other-user", 3, new FixedRandom(0))));
  }

  @Test
  public void secondSizeSettingIsRejectedInsteadOfReshufflingRoles() {
    service.createVillage(OWNER, false);
    assertNotNull(service.setVillageSize(OWNER, 5, new FixedRandom(2)));

    // 人数設定済みの村は findLatestOwned の条件から外れる
    assertNull(service.setVillageSize(OWNER, 9, new FixedRandom(0)));

    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);
    assertEquals(5, village.getVillageSize());
    assertEquals(3, village.getInsiderNum());
  }

  @Test
  public void randomVillageDealsRolesAndAnOdaiWithoutAskingTheOwner() {
    List<Message> created = service.createRandomVillage(OWNER);

    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);
    assertNotNull("お題が自動で決まっていない", village.getOdai());
    assertTrue(((TextMessage) created.get(0)).getText()
        .endsWith(MessageConst.RANDOM_NUMSETMESSAGE));

    // オーナーが1人目。インサイダーは3番目、GMは1番目
    service.setVillageSize(OWNER, 4, new FixedRandom(2, 0));

    assertEquals(4, village.getVillageSize());
    assertEquals(MessageConst.GAMEMASTER_ROLE, village.getMemberRole(OWNER));

    // 村を作成した人を含めて4人。GMとインサイダーは1人ずつ
    joinAll(village, "u2", "u3", "u4");
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("u2"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("u3"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("u4"));
  }

  @Test
  public void randomVillageOwnerIsToldTheirOwnRoleInsteadOfTheOdai() {
    service.createRandomVillage(OWNER);
    // オーナーは1人目。インサイダーは2番目、GMは3番目なのでオーナーは村人
    List<Message> sizeMessages = service.setVillageSize(OWNER, 3, new FixedRandom(1, 2));
    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);

    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole(OWNER));
    assertTrue(((TextMessage) sizeMessages.get(0)).getText()
        .startsWith(village.getVillageNum() + "村：人数を『3人』に設定しました。"));

    // 村人になったオーナーへは、お題を含む配布状況ではなく村人の役職だけを返す
    List<Message> ownerReply = service.joinVillage(OWNER, village.getVillageNum());
    assertEquals(village.getRoleMessage(OWNER), ownerReply);
    assertEquals("あなたの役職は" + MessageConst.VILLAGE_ROLE + "です。", textOf(ownerReply.get(0)));
  }

  @Test
  public void randomVillageOwnerIsAskedForTheSizeBeforeAnyRoleExists() {
    service.createRandomVillage(OWNER);
    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);

    List<Message> messages = service.joinVillage(OWNER, village.getVillageNum());

    assertEquals(new TextMessage(MessageConst.RANDOM_NUMSETMESSAGE), messages.get(0));
  }

  @Test
  public void randomVillageIsNotTurnedIntoAReverseVillage() {
    service.createRandomVillage(OWNER);

    // 逆村化するとインサイダーが1人ではなくなる
    assertNull(service.setReverseVillage(OWNER));
    assertFalse(fixture.villages.findLatestOwned(OWNER, target -> true).isReverseVillage());
  }

  @Test
  public void odaiIsSetOnlyOnce() {
    service.createVillage(OWNER, false);

    assertNotNull(service.setOdai(OWNER, "すいか"));
    // お題設定済みの村は対象から外れるため、2度目は既定応答へ落ちる
    assertNull(service.setOdai(OWNER, "めろん"));

    assertEquals("すいか", fixture.villages.findLatestOwned(OWNER, target -> true).getOdai());
  }

  @Test
  public void reverseVillageIsConfirmedToTheOwner() {
    service.createVillage(OWNER, false);
    service.setVillageSize(OWNER, 3, new FixedRandom(1));

    List<Message> messages = service.setReverseVillage(OWNER);

    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);
    assertNotNull(messages);
    assertTrue(village.isReverseVillage());
    assertTrue(((TextMessage) messages.get(0)).getText()
        .startsWith(village.getVillageNum() + "村 を『逆村』に設定しました。"));
  }

  @Test
  public void reverseVillageIsRejectedOnceParticipantsJoined() {
    service.createVillage(OWNER, false);
    service.setVillageSize(OWNER, 3, new FixedRandom(1));
    Village village = fixture.villages.findLatestOwned(OWNER, target -> true);
    joinAll(village, "u1");

    // 参加者がいる村は対象から外れるため、既定応答へ落ちる
    assertNull(service.setReverseVillage(OWNER));
    assertFalse(village.isReverseVillage());
    // インサイダーは2番目。逆村化されていればu1はインサイダーになっていた
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("u1"));
  }

  @Test
  public void reverseVillageWithoutAnOwnedVillageFallsBackToTheDefaultReply() {
    assertNull(service.setReverseVillage(OWNER));
  }

  private String textOf(Message message) {
    if (message instanceof TextMessage) {
      return ((TextMessage) message).getText();
    }
    return ((TemplateMessage) message).getAltText();
  }

  private String thumbnailOf(List<Message> messages) {
    TemplateMessage template = (TemplateMessage) messages.get(0);
    return ((ButtonsTemplate) template.getTemplate()).getThumbnailImageUrl();
  }

  private void joinAll(Village village, String... userIds) {
    for (String userId : userIds) {
      assertNotNull("参加できなかった: " + userId, village.join(userId));
    }
  }
}
