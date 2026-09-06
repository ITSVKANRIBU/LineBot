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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;

import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

/**
 * {@code GET /callapi}の契約テスト.
 *
 * <p>村番号での参加だけでなく、村作成・お題設定・人数設定も引き続き提供することを固定する。
 */
public class MainControllerTest {

  private static final String OWNER = "owner-user";
  private static final String MEMBER = "member-user";

  private final MainController controller = new MainController();

  @Before
  public void resetRegistries() {
    VillageList.clear();
    SpecialVillageList.clear();
  }

  @Test
  public void missingApiParametersAreRejected() {
    assertEquals(HttpStatus.BAD_REQUEST, controller.index(null, OWNER).getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, controller.index("1234", null).getStatusCode());
  }

  @Test
  public void blankApiParametersAreRejected() {
    assertEquals(HttpStatus.BAD_REQUEST, controller.index("   ", OWNER).getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, controller.index("1234", "  ").getStatusCode());
  }

  @Test
  public void unknownVillageNumberFallsBackToNotCreatedMessage() {
    ResponseEntity<List<Message>> response = controller.index("1234", MEMBER);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(new TextMessage("村が作成されていません"), response.getBody().get(0));
  }

  @Test
  public void villageCanBeCreatedThroughTheApi() {
    ResponseEntity<List<Message>> response = controller.index("お題", OWNER);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(response.getBody().get(0) instanceof TemplateMessage);
    assertTrue(((TemplateMessage) response.getBody().get(0)).getAltText()
        .contains("村 を新しく作成しました。"));
  }

  @Test
  public void randomVillageCanBeCreatedThroughTheApi() {
    ResponseEntity<List<Message>> created = controller.index("ランダム", OWNER);

    assertEquals(HttpStatus.OK, created.getStatusCode());
    assertEquals(new TextMessage(villageNumberOf(OWNER) + "村 を新しく作成しました。"
        + MessageConst.RANDOM_NUMSETMESSAGE), created.getBody().get(0));

    // 人数設定だけで配布が終わり、オーナーも参加者として数えられる
    ResponseEntity<List<Message>> sized = controller.index("2", OWNER);
    assertEquals(HttpStatus.OK, sized.getStatusCode());
    assertTrue(((TextMessage) sized.getBody().get(0)).getText()
        .contains("人数を『2人』に設定しました。"));

    String villageNum = String.valueOf(villageNumberOf(OWNER));
    assertTrue(isRoleMessage(controller.index(villageNum, MEMBER).getBody()));

    ResponseEntity<List<Message>> full = controller.index(villageNum, "third");
    assertEquals(new TextMessage("村がいっぱいです。"), full.getBody().get(0));
  }

  @Test
  public void odaiAndSizeCanBeSetThroughTheApi() {
    controller.index("神", OWNER);

    ResponseEntity<List<Message>> odaiResponse = controller.index("すいか", OWNER);
    assertEquals(new TextMessage("" + villageNumberOf(OWNER) + "村 のお題を『すいか』に設定しました。\n"
        + MessageConst.GOD_NUMSETMESSAGE), odaiResponse.getBody().get(0));

    ResponseEntity<List<Message>> sizeResponse = controller.index("5", OWNER);
    assertEquals(HttpStatus.OK, sizeResponse.getStatusCode());
    assertTrue(((TemplateMessage) sizeResponse.getBody().get(0)).getAltText()
        .contains("人数を『5人』に設定しました。"));
  }

  /** 数値の前後の空白は、LINE経由と同じくtrimしてから判定する. */
  @Test
  public void aNumberWithSurroundingSpacesIsStillAParticipantCount() {
    controller.index("お題", OWNER);

    ResponseEntity<List<Message>> response = controller.index(" 5", OWNER);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertTrue(((TemplateMessage) response.getBody().get(0)).getAltText()
        .contains("人数を『5人』に設定しました。"));
  }

  @Test
  public void villageSizeBelowTwoIsRejectedWithGuidance() {
    controller.index("お題", OWNER);

    ResponseEntity<List<Message>> response = controller.index("1", OWNER);

    assertEquals(new TextMessage(MessageConst.ERR_NUMSETMESSAGE), response.getBody().get(0));
  }

  @Test
  public void participantJoinsAndCapacityIsEnforced() {
    controller.index("お題", OWNER);
    controller.index("すいか", OWNER);
    controller.index("2", OWNER);
    int villageNum = villageNumberOf(OWNER);

    // 定員2、オーナーは参加者に含まれないため2人まで入室できる
    assertTrue(isRoleMessage(controller.index(String.valueOf(villageNum), MEMBER).getBody()));
    assertTrue(isRoleMessage(controller.index(String.valueOf(villageNum), "second").getBody()));

    ResponseEntity<List<Message>> full = controller.index(String.valueOf(villageNum), "third");
    assertEquals(new TextMessage("村がいっぱいです。"), full.getBody().get(0));
  }

  @Test
  public void repeatedJoinReturnsTheSameRole() {
    controller.index("お題", OWNER);
    controller.index("すいか", OWNER);
    controller.index("3", OWNER);
    String villageNum = String.valueOf(villageNumberOf(OWNER));

    List<Message> first = controller.index(villageNum, MEMBER).getBody();
    List<Message> second = controller.index(villageNum, MEMBER).getBody();

    assertEquals(first, second);
  }

  /** 101〜999はLINEと同じく村番号として扱う。その範囲の村は採番されない. */
  @Test
  public void aNumberAboveOneHundredIsTreatedAsAVillageNumber() {
    controller.index("お題", OWNER);

    ResponseEntity<List<Message>> response = controller.index("101", OWNER);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(new TextMessage("村が作成されていません"), response.getBody().get(0));
  }

  @Test
  public void oneHundredIsStillAParticipantCount() {
    controller.index("お題", OWNER);

    ResponseEntity<List<Message>> response = controller.index("100", OWNER);

    assertTrue(((TemplateMessage) response.getBody().get(0)).getAltText()
        .contains("人数を『100人』に設定しました。"));
  }

  /** @逆村はLINEと同じくコマンドとして解釈される. お題の文字列にはならない. */
  @Test
  public void theReverseVillageCommandIsInterpretedThroughTheApi() {
    controller.index("お題", OWNER);
    controller.index("すいか", OWNER);
    controller.index("3", OWNER);
    int villageNum = villageNumberOf(OWNER);

    ResponseEntity<List<Message>> response = controller.index("@逆村", OWNER);

    assertEquals(new TextMessage(villageNum + "村 を『逆村』に設定しました。\n"
        + "お題を知らない村人が1人となります。"), response.getBody().get(0));
  }

  @Test
  public void theWerewordsCommandIsInterpretedThroughTheApi() {
    controller.index("お題", OWNER);
    controller.index("すいか", OWNER);
    controller.index("3", OWNER);

    ResponseEntity<List<Message>> response = controller.index("＠わーわーず", OWNER);

    assertTrue(((TextMessage) response.getBody().get(0)).getText()
        .startsWith("お題を『すいか』として新たにワーワーズの"));
  }

  /**
   * @取得はポストバックボタン付きのテンプレートを返す.
   *
   * <p>このAPIにポストバックの入口はないが、「確定」ボタン相当の候補文字列は
   * JSONから読み取れる。
   */
  @Test
  public void theOdaiLookupCommandReturnsATemplateThroughTheApi() {
    ResponseEntity<List<Message>> response = controller.index("@取得", OWNER);

    TemplateMessage template = (TemplateMessage) response.getBody().get(0);
    ButtonsTemplateNonURL buttons = (ButtonsTemplateNonURL) template.getTemplate();

    assertEquals(4, buttons.getActions().size());
    assertEquals(new PostbackAction("初心者", "2"), buttons.getActions().get(1));
  }

  @Test
  public void theDistributionCommandIsInterpretedThroughTheApi() {
    ResponseEntity<List<Message>> response = controller.index("@配布", OWNER);

    assertEquals(3, response.getBody().size());
  }

  private int villageNumberOf(String ownerId) {
    return VillageList.findLatestOwned(ownerId, village -> true).getVillageNum();
  }

  private boolean isRoleMessage(List<Message> messages) {
    return !new TextMessage("村がいっぱいです。").equals(messages.get(0))
        && !new TextMessage("村が作成されていません").equals(messages.get(0));
  }
}
