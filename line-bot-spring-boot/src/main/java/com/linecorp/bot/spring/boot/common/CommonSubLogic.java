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
package com.linecorp.bot.spring.boot.common;

import java.text.MessageFormat;

public class CommonSubLogic {

  private static String[] WEREWORDS_MESSAGE_MAP = {
      "",
      "あなたの役職は占師です。お題は「{0}」です。",
      "あなたの役職はインサイダーです。お題は「{0}」です。",
      "あなたの役職は村人です",
      "あなたの役職はGMです。お題は「{0}」です。\n"
          + "役職は「{1}」が欠けています。"
  };
  private static String[] WEREWORDS_ROLE_MAP = {
      "",
      "占師",
      "インサイダー",
      "あなたの役職は村人です",
      "村人",
      "GM"
  };

  /**
   * メッセ―ジ種痘.
   * @param roleNum 役職番号
   * @param umeji 埋め字
   * @return
   */
  public static String getWereMesse(int roleNum, String[] umeji) {

    MessageFormat mf = new MessageFormat(WEREWORDS_MESSAGE_MAP[roleNum]);

    return mf.format(umeji);
  }

  public static String getWereRole(int rolenum) {
    return WEREWORDS_ROLE_MAP[rolenum];
  }

}
