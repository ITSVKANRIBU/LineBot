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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.bot.common.WordGetter;
import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.model.action.Action;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.ImageMessage;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

/**
 * 経路を知らないテキストコマンドの入口.
 *
 * <p>LINE webhookと{@code /callapi}で違ってよいのは、入力の取り出し方と結果の返し方だけ。
 * 入力の解釈（数値の境界、コマンド表、trimの扱い）はここに1組だけ置く。
 * 経路ごとに解釈を書くと、片方だけを直したときに配布結果が分岐し、
 * 突き合わせる利用者がいないため誰も気付けない。
 *
 * <p>対象の村が見つからない場合は{@code null}を返す。呼び出し側が経路に応じた応答
 * （LINEは村の作成を促す確認テンプレート、APIは「村が作成されていません」）へ変換する。
 */
public final class TextCommandHandler {

  /** これを超える数値は特殊村の番号として扱う. */
  private static final int MAX_VILLAGE_NUMBER = 9999;

  /** 設定できる参加人数の上限。これを超える数値は通常村の番号として扱う. */
  private static final int MAX_SIZE_INPUT = 100;

  /** 「お題の自動取得」から引くときの難易度。難易度を指定しない区間になる. */
  private static final int UNSPECIFIED_RANK = 10;

  private TextCommandHandler() {
  }

  /**
   * 利用者のテキスト入力を解釈し、返すべきメッセージを組み立てる.
   *
   * @param userId 利用者の識別子
   * @param text 入力されたテキスト
   * @return 返すメッセージ。対象の村がない場合はnull
   */
  public static List<Message> handle(String userId, String text) {
    String command = text.trim();

    int number;
    try {
      number = Integer.parseInt(command);
    } catch (NumberFormatException e) {
      return nonNumberCommand(userId, text, command);
    }

    if (number > MAX_VILLAGE_NUMBER) {
      return VillageService.joinSpecialVillage(userId, number);
    }
    if (number > MAX_SIZE_INPUT) {
      return VillageService.joinVillage(userId, number);
    }
    return VillageService.setVillageSize(userId, number);
  }

  /**
   * お題候補と、引き直し用の難易度ボタンを組み立てる.
   *
   * <p>村を持っているかどうかに関わらず引けるため、利用者の識別を必要としない。
   *
   * @param rank 難易度
   * @return 候補1件と「確定」「初心者」「上級者」「変態」のボタンを持つテンプレート
   */
  public static List<Message> odaiCandidate(int rank) {
    String odai = WordGetter.getWord(rank);
    String message = "お題は「" + odai + "」です。確定しますか？";

    List<Action> actionList = new ArrayList<Action>();
    actionList.add(new MessageAction("確定", odai));
    actionList.add(new PostbackAction("初心者", String.valueOf(WordGetter.BEGINNER_RANK)));
    actionList.add(new PostbackAction("上級者", String.valueOf(3)));
    actionList.add(new PostbackAction("変態", String.valueOf(4)));

    return Collections.<Message>singletonList(
        new TemplateMessage(message, new ButtonsTemplateNonURL(message, actionList)));
  }

  private static List<Message> nonNumberCommand(String userId, String text, String command) {
    if ("お題".equals(command) || "題".equals(command) || "神".equals(command)) {
      return VillageService.createVillage(userId, "神".equals(command));
    }
    if ("ランダム".equals(command)) {
      return VillageService.createRandomVillage(userId);
    }
    if ("@配布".equals(command) || "＠配布".equals(command)) {
      return officialAccountInvitation();
    }
    if ("@特殊".equals(command) || "＠特殊".equals(command)) {
      return Collections.<Message>singletonList(
          new TextMessage("https://insidergametool.netlify.app/form.html"));
    }
    if ("@取得".equals(command) || "＠取得".equals(command)) {
      return odaiCandidate(UNSPECIFIED_RANK);
    }
    if ("@逆村".equals(command) || "＠逆村".equals(command)) {
      return VillageService.setReverseVillage(userId);
    }
    if ("@わーわーず".equals(command) || "＠わーわーず".equals(command)) {
      return VillageService.convertToWerewords(userId);
    }

    // 残りはお題。前後の空白は利用者が入力したお題の一部として保つ
    return VillageService.setOdai(userId, text);
  }

  /** 公式アカウントの友だち追加案内. */
  private static List<Message> officialAccountInvitation() {
    String imageUrl = MessageConst.ILLUSTRATION_URL_PREFIX + "966mpnqz.png";

    List<Message> messages = new ArrayList<Message>();
    messages.add(new ImageMessage(imageUrl, imageUrl));
    messages.add(new TextMessage("https://line.me/R/ti/p/%40966mpnqz"));
    messages.add(new TextMessage("お友達ID\n@966mpnqz"));
    return messages;
  }
}
