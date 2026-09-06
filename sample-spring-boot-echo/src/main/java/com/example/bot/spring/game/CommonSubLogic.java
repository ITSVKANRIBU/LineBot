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

import java.text.MessageFormat;

public final class CommonSubLogic {

  private static final String[] WEREWORDS_MESSAGE_MAP = {
      "",
      "あなたの役職は占師です。お題は「{0}」です。",
      "あなたの役職はインサイダーです。お題は「{0}」です。",
      "あなたの役職は村人です",
      "あなたの役職はGMです。お題は「{0}」です。\n役職は「{1}」が欠けています。"
  };

  /** 「欠け」として名指しする役職名。添字は{@link #WEREWORDS_MESSAGE_MAP}と共通. */
  private static final String[] WEREWORDS_ROLE_MAP = {
      "", "占師", "インサイダー", "村人"
  };

  private CommonSubLogic() {
  }

  /**
   * 役職メッセージを組み立てる.
   *
   * @param roleNum 役職番号
   * @param placeholders 埋め字
   * @return 役職メッセージ
   */
  public static String getWereMessage(int roleNum, String[] placeholders) {
    return new MessageFormat(WEREWORDS_MESSAGE_MAP[roleNum]).format(placeholders);
  }

  public static String getWereRole(int roleNum) {
    return WEREWORDS_ROLE_MAP[roleNum];
  }
}
