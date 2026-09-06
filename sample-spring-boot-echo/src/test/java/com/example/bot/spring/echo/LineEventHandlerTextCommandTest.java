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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.example.bot.spring.game.SpecialVillageList;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;

import com.linecorp.bot.client.LineMessagingClient;
import com.linecorp.bot.model.ReplyMessage;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.event.MessageEvent;
import com.linecorp.bot.model.event.PostbackEvent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.postback.PostbackContent;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;
import com.linecorp.bot.model.message.template.ConfirmTemplate;
import com.linecorp.bot.model.response.BotApiResponse;

/**
 * LINEのテキストコマンド経路の応答を固定する.
 *
 * <p>これまでゲーム規則は{@code /callapi}経由でしか覆われておらず、
 * LINE側の分岐とメッセージ組み立ては固定されていなかった。
 * 応答文・ボタンの構成・返信の通数は外部から観測できる契約のため、
 * 責務分離や経路の共通化で変わっていないことをここで検知する。
 */
public class LineEventHandlerTextCommandTest {

  private static final String OWNER = "owner-user";
  private static final String MEMBER = "member-user";

  private LineEventHandler handler;
  private LineMessagingClient lineMessagingClient;

  @Before
  public void setUp() {
    VillageList.clear();
    SpecialVillageList.clear();

    // BotApiResponseはfinal（Lombok @Value）のためmockではなく実インスタンスを使う
    lineMessagingClient = mock(LineMessagingClient.class);
    when(lineMessagingClient.replyMessage(any(ReplyMessage.class)))
        .thenReturn(CompletableFuture.completedFuture(new BotApiResponse("ok", null)));

    handler = new LineEventHandler(lineMessagingClient);
  }

  // ---------- 村の作成 ----------

  @Test
  public void villageCreationRepliesWithTheVillageNumberAndAnAutoOdaiButton() {
    List<Message> messages = send(OWNER, "お題");

    assertEquals(1, messages.size());
    int villageNum = villageNumberOf(OWNER);
    assertTrue("村番号は4桁で採番される: " + villageNum, villageNum >= 1000 && villageNum <= 9999);
    assertEquals(villageNum + "村 を新しく作成しました。" + MessageConst.OWNER_ODAIMESSAGE,
        altTextOf(messages));

    ButtonsTemplateNonURL buttons = buttonsOf(messages);
    assertEquals(1, buttons.getActions().size());
    assertEquals(new PostbackAction("お題の自動取得", "0"), buttons.getActions().get(0));
  }

  @Test
  public void theShortAndGodVillageCommandsCreateAVillageToo() {
    assertTrue(altTextOf(send(OWNER, "題")).contains("村 を新しく作成しました。"));
    assertTrue(altTextOf(send("god-user", "神")).contains("村 を新しく作成しました。"));
  }

  @Test
  public void randomVillageCreationRepliesWithPlainText() {
    List<Message> messages = send(OWNER, "ランダム");

    assertEquals(Collections.singletonList(new TextMessage(villageNumberOf(OWNER)
        + "村 を新しく作成しました。" + MessageConst.RANDOM_NUMSETMESSAGE)), messages);
  }

  // ---------- お題の設定 ----------

  @Test
  public void odaiForANormalVillageAsksForTheParticipantCount() {
    send(OWNER, "お題");

    assertEquals(new TextMessage(villageNumberOf(OWNER) + "村 のお題を『すいか』に設定しました。\n"
        + MessageConst.OWNER_NUMSETMESSAGE), send(OWNER, "すいか").get(0));
  }

  @Test
  public void odaiForAGodVillageAsksForTheParticipantCountIncludingTheGameMaster() {
    send(OWNER, "神");

    assertEquals(new TextMessage(villageNumberOf(OWNER) + "村 のお題を『すいか』に設定しました。\n"
        + MessageConst.GOD_NUMSETMESSAGE), send(OWNER, "すいか").get(0));
  }

  /**
   * 人数を先に設定した神モードの村では、お題設定の応答が人数入力を促す文になる.
   *
   * <p>神モードの目印である{@code gmNum}が人数確定で席番号へ上書きされるため、
   * お題設定の時点では神モードと判定されない。オーナーが許容している挙動として固定する。
   */
  @Test
  public void odaiSetAfterTheParticipantCountStillAsksForTheParticipantCount() {
    send(OWNER, "神");
    send(OWNER, "5");

    assertEquals(new TextMessage(villageNumberOf(OWNER) + "村 のお題を『すいか』に設定しました。\n"
        + MessageConst.OWNER_NUMSETMESSAGE), send(OWNER, "すいか").get(0));
  }

  @Test
  public void odaiWithoutAVillageFallsBackToTheDefaultReply() {
    assertDefaultReply(send(OWNER, "すいか"));
  }

  // ---------- 人数の設定 ----------

  @Test
  public void theParticipantCountRepliesWithAConfirmationButton() {
    send(OWNER, "お題");
    send(OWNER, "すいか");

    List<Message> messages = send(OWNER, "2");

    String villageNum = String.valueOf(villageNumberOf(OWNER));
    String message = "人数を『2人』に設定しました。\n皆さんに村番号を伝えてください。";
    assertEquals(message + "配布状況の確認は村番号を入力してください。", altTextOf(messages));

    ButtonsTemplate buttons =
        (ButtonsTemplate) ((TemplateMessage) messages.get(0)).getTemplate();
    assertEquals(MessageConst.GM_URL, buttons.getThumbnailImageUrl());
    assertEquals(villageNum + "村", buttons.getTitle());
    assertEquals(message, buttons.getText());
    assertEquals(Collections.singletonList(new MessageAction("確認", villageNum)),
        buttons.getActions());
  }

  @Test
  public void aParticipantCountBelowTwoIsRejectedWithGuidance() {
    send(OWNER, "お題");

    assertEquals(new TextMessage(MessageConst.ERR_NUMSETMESSAGE), send(OWNER, "1").get(0));
  }

  @Test
  public void oneHundredIsStillTreatedAsAParticipantCount() {
    send(OWNER, "お題");

    assertTrue(altTextOf(send(OWNER, "100")).contains("人数を『100人』に設定しました。"));
  }

  /** 101以上は村番号として扱われ、その番号の村は採番されないため既定応答になる. */
  @Test
  public void oneHundredAndOneIsTreatedAsAVillageNumberAndFallsBackToTheDefaultReply() {
    send(OWNER, "お題");

    assertDefaultReply(send(OWNER, "101"));
  }

  // ---------- 参加と配布状況 ----------

  @Test
  public void participantsJoinUntilTheVillageIsFull() {
    String villageNum = normalVillage("すいか", 2);

    assertTrue(altTextOf(send(MEMBER, villageNum)).startsWith("あなたの役職は"));
    assertTrue(altTextOf(send("second", villageNum)).startsWith("あなたの役職は"));
    assertEquals(new TextMessage("村がいっぱいです。"), send("third", villageNum).get(0));
  }

  @Test
  public void aRepeatedJoinReturnsTheSameRole() {
    String villageNum = normalVillage("すいか", 3);

    assertEquals(send(MEMBER, villageNum), send(MEMBER, villageNum));
  }

  @Test
  public void theOwnerSeesTheDistributionStatus() {
    String villageNum = normalVillage("すいか", 2);
    send(MEMBER, villageNum);

    List<Message> messages = send(OWNER, villageNum);

    assertEquals(villageNum + "村：1/2人にお題を配りました。お題は『すいか』です。",
        altTextOf(messages));
    assertEquals(Collections.singletonList(new MessageAction("再確認", villageNum)),
        buttonsOf(messages).getActions());
  }

  /** ランダム村のオーナーは参加者でもあるため、配布状況ではなく自分の役職が返る. */
  @Test
  public void theRandomVillageOwnerSeesTheirOwnRoleAgain() {
    send(OWNER, "ランダム");
    List<Message> sized = send(OWNER, "2");
    String villageNum = String.valueOf(villageNumberOf(OWNER));

    assertEquals(new TextMessage(villageNum + "村：人数を『2人』に設定しました。\n"
        + "皆さんに村番号を伝えてください。"), sized.get(0));
    assertEquals(sized.subList(1, sized.size()), send(OWNER, villageNum));
  }

  @Test
  public void anUnknownVillageNumberFallsBackToTheDefaultReply() {
    assertDefaultReply(send(MEMBER, "1234"));
    assertDefaultReply(send(MEMBER, "99999"));
  }

  // ---------- 逆村 ----------

  @Test
  public void theReverseVillageIsAppliedWhileNobodyHasJoined() {
    String villageNum = normalVillage("すいか", 3);

    assertEquals(new TextMessage(villageNum + "村 を『逆村』に設定しました。\n"
        + "お題を知らない村人が1人となります。"), send(OWNER, "@逆村").get(0));
  }

  @Test
  public void theReverseVillageIsRejectedOnceSomebodyHasJoined() {
    String villageNum = normalVillage("すいか", 3);
    send(MEMBER, villageNum);

    assertDefaultReply(send(OWNER, "@逆村"));
  }

  @Test
  public void theReverseVillageIsRejectedForARandomVillage() {
    send(OWNER, "ランダム");

    assertDefaultReply(send(OWNER, "@逆村"));
  }

  @Test
  public void theReverseVillageWithoutAVillageFallsBackToTheDefaultReply() {
    assertDefaultReply(send(OWNER, "@逆村"));
  }

  // ---------- Werewords への変換 ----------

  @Test
  public void werewordsFromAGodVillageDistributesOneMessagePerParticipant() {
    send(OWNER, "神");
    send(OWNER, "すいか");
    send(OWNER, "3");

    List<Message> messages = send(OWNER, "@わーわーず");

    assertEquals(1, messages.size());
    String text = ((TextMessage) messages.get(0)).getText();
    int specialNum = specialVillageNumberIn(text);
    assertTrue("特殊村は5桁で採番される: " + specialNum,
        specialNum >= 10000 && specialNum <= 99998);
    assertEquals("お題を『すいか』として新たにワーワーズの『" + specialNum + "』村を作成しました。"
        + "参加者へ『" + specialNum + "』を伝えてください。", text);

    // 配布順はシャッフル済みのため、通数とGM向けの有無だけを固定する
    List<String> distributed = SpecialVillageList.getVillage(specialNum).getMessageList();
    assertEquals(3, distributed.size());
    assertEquals(1, countStartingWith(distributed, "あなたの役職はGMです。"));
  }

  @Test
  public void werewordsFromANormalVillageAddsOneExtraMessageForTheOwner() {
    send(OWNER, "お題");
    send(OWNER, "すいか");
    send(OWNER, "3");

    String text = ((TextMessage) send(OWNER, "@わーわーず").get(0)).getText();

    int specialNum = specialVillageNumberIn(text);
    assertEquals("お題を『すいか』として新たにワーワーズの『" + specialNum + "』村を作成しました。"
        + "参加者へ『" + specialNum + "』を伝え、あなたも入室してください。\n"
        + "\n■注意\n"
        + "あなたはGMです。入室時に表示された役職が欠けた役職となります。", text);

    List<String> distributed = SpecialVillageList.getVillage(specialNum).getMessageList();
    assertEquals(4, distributed.size());
    assertEquals(0, countStartingWith(distributed, "あなたの役職はGMです。"));
  }

  @Test
  public void werewordsKeepsTheOriginalVillage() {
    normalVillage("すいか", 3);
    int villageNum = villageNumberOf(OWNER);

    send(OWNER, "@わーわーず");

    assertNotNull(VillageList.getVillage(villageNum));
  }

  @Test
  public void werewordsNeedsMoreThanTwoParticipants() {
    normalVillage("すいか", 2);

    assertDefaultReply(send(OWNER, "@わーわーず"));
  }

  @Test
  public void werewordsNeedsATopic() {
    send(OWNER, "お題");
    send(OWNER, "3");

    assertDefaultReply(send(OWNER, "@わーわーず"));
  }

  @Test
  public void werewordsWithoutAVillageFallsBackToTheDefaultReply() {
    assertDefaultReply(send(OWNER, "@わーわーず"));
  }

  // ---------- お題の自動取得と案内コマンド ----------

  @Test
  public void theOdaiLookupOffersTheCandidateAndThreeDifficulties() {
    List<Message> messages = send(OWNER, "@取得");

    assertEquals(1, messages.size());
    ButtonsTemplateNonURL buttons = buttonsOf(messages);
    assertEquals(4, buttons.getActions().size());

    MessageAction confirm = (MessageAction) buttons.getActions().get(0);
    assertEquals("確定", confirm.getLabel());
    assertEquals("お題は「" + confirm.getText() + "」です。確定しますか？", altTextOf(messages));
    assertEquals(new PostbackAction("初心者", "2"), buttons.getActions().get(1));
    assertEquals(new PostbackAction("上級者", "3"), buttons.getActions().get(2));
    assertEquals(new PostbackAction("変態", "4"), buttons.getActions().get(3));
  }

  @Test
  public void theDistributionCommandRepliesWithTheOfficialAccountInvitation() {
    List<Message> messages = send(OWNER, "@配布");

    assertEquals(3, messages.size());
    String imageUrl = MessageConst.ILLUSTRATION_URL_PREFIX + "966mpnqz.png";
    assertEquals(new ImageMessage(imageUrl, imageUrl), messages.get(0));
    assertEquals(new TextMessage("https://line.me/R/ti/p/%40966mpnqz"), messages.get(1));
    assertEquals(new TextMessage("お友達ID\n@966mpnqz"), messages.get(2));
  }

  @Test
  public void theSpecialVillageCommandRepliesWithTheFormUrl() {
    assertEquals(Collections.singletonList(
        new TextMessage("https://insidergametool.netlify.app/form.html")),
        send(OWNER, "@特殊"));
  }

  @Test
  public void aStickerRepliesWithTheAuthorInformation() {
    handler.handleStickerMessageEvent(new MessageEvent<StickerMessageContent>(
        "reply-token", new UserSource(OWNER),
        new StickerMessageContent("message-id", "1", "1"), Instant.now()));

    List<Message> messages = lastReply();
    assertEquals(1, messages.size());
    assertTrue(altTextOf(messages).contains("製作者の「白いフランです。」"));
  }

  /** 全角の＠でも同じコマンドとして解釈される. */
  @Test
  public void theFullWidthAtSignIsEquivalentToTheHalfWidthOne() {
    assertEquals(send(OWNER, "@特殊"), send(OWNER, "＠特殊"));
    assertEquals(send(OWNER, "@配布"), send(OWNER, "＠配布"));
    // お題は毎回引き直すため、ボタンの構成だけを突き合わせる
    assertEquals(4, buttonsOf(send(OWNER, "＠取得")).getActions().size());

    String villageNum = normalVillage("すいか", 3);
    assertEquals(new TextMessage(villageNum + "村 を『逆村』に設定しました。\n"
        + "お題を知らない村人が1人となります。"), send(OWNER, "＠逆村").get(0));
    assertTrue(((TextMessage) send(OWNER, "＠わーわーず").get(0)).getText()
        .startsWith("お題を『すいか』として新たにワーワーズの"));
  }

  // ---------- 入室状況のポストバック ----------

  @Test
  public void theStatusPostbackForANormalVillageShowsTheSeatAndOccupancy() {
    String villageNum = normalVillage("すいか", 3);
    send(MEMBER, villageNum);

    handler.handlePostbackEvent(postback(MEMBER, villageNum));

    assertEquals(new TextMessage("あなたは1番目の参加者です。\n　入室状況：1/3人"),
        lastReply().get(0));
  }

  @Test
  public void theStatusPostbackForASpecialVillageShowsTheSeatAndOccupancy() {
    normalVillage("すいか", 3);
    String specialNum = String.valueOf(specialVillageNumberIn(
        ((TextMessage) send(OWNER, "@わーわーず").get(0)).getText()));
    send(MEMBER, specialNum);

    handler.handlePostbackEvent(postback(MEMBER, specialNum));

    assertEquals(new TextMessage("あなたは1番目の参加者です。\n　入室状況：1/4人"),
        lastReply().get(0));
  }

  // ---------- helpers ----------

  /** お題と人数を設定済みの通常村を作り、その村番号を返す. */
  private String normalVillage(String odai, int size) {
    send(OWNER, "お題");
    send(OWNER, odai);
    send(OWNER, String.valueOf(size));
    return String.valueOf(villageNumberOf(OWNER));
  }

  private int villageNumberOf(String ownerId) {
    return VillageList.findLatestOwned(ownerId, village -> true).getVillageNum();
  }

  private List<Message> send(String userId, String text) {
    handler.handleTextMessageEvent(new MessageEvent<TextMessageContent>("reply-token",
        new UserSource(userId), new TextMessageContent("message-id", text), Instant.now()));
    return lastReply();
  }

  private PostbackEvent postback(String userId, String data) {
    return new PostbackEvent("reply-token", new UserSource(userId),
        new PostbackContent(data, null), Instant.now());
  }

  /** 直近の返信で送られたメッセージ列. */
  private List<Message> lastReply() {
    ArgumentCaptor<ReplyMessage> captor = ArgumentCaptor.forClass(ReplyMessage.class);
    verify(lineMessagingClient, atLeastOnce()).replyMessage(captor.capture());
    return captor.getValue().getMessages();
  }

  private String altTextOf(List<Message> messages) {
    return ((TemplateMessage) messages.get(0)).getAltText();
  }

  private ButtonsTemplateNonURL buttonsOf(List<Message> messages) {
    return (ButtonsTemplateNonURL) ((TemplateMessage) messages.get(0)).getTemplate();
  }

  private void assertDefaultReply(List<Message> messages) {
    assertEquals(1, messages.size());
    assertEquals(MessageConst.DEFAULT_MESSAGE, altTextOf(messages));

    ConfirmTemplate confirm = (ConfirmTemplate) ((TemplateMessage) messages.get(0)).getTemplate();
    assertEquals(2, confirm.getActions().size());
    assertEquals(new MessageAction("GM", "お題"), confirm.getActions().get(0));
    assertEquals(new MessageAction("神", "神"), confirm.getActions().get(1));
  }

  private int specialVillageNumberIn(String text) {
    Matcher matcher = Pattern.compile("『(\\d{5})』").matcher(text);
    assertTrue("特殊村番号が応答に含まれない: " + text, matcher.find());
    return Integer.parseInt(matcher.group(1));
  }

  private int countStartingWith(List<String> messages, String prefix) {
    int count = 0;
    for (String message : messages) {
      if (message.startsWith(prefix)) {
        count++;
      }
    }
    return count;
  }
}
