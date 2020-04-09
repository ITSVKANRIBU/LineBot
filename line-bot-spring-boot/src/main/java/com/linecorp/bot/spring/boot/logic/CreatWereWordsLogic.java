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

package com.linecorp.bot.spring.boot.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.linecorp.bot.spring.boot.common.CommonSubLogic;

public class CreatWereWordsLogic {

  /**
   * ワーワーズ.
   */
  public int createWereWords(boolean godFlg, int num, String theme) {
    int villageNum = 0;

    CreatVillage create = new CreatVillage();

    villageNum = create.createNewVillage(getMessages(godFlg, villageNum, theme));

    return villageNum;
  }

  /**
   * メッセージ取得.
   * @param godFlg 神フラグ
   * @param num 人数
   * @param theme お題
   * @return
   */
  public List<String> getMessages(boolean godFlg, int num, String theme) {
    List<String> messages = new ArrayList<String>();

    // 役職判定リスト生成
    List<Integer> yakuList = new ArrayList<Integer>();
    yakuList.add(1);//占師
    yakuList.add(2);//インサイダー

    // 村人追加
    for (int i = 2; i < num; i++) {
      yakuList.add(3);//村人
    }

    // ランダムに並び替える
    Collections.shuffle(yakuList);

    // 先頭の役職を欠けとしてメッセージ設定
    String[] umeji = { theme, CommonSubLogic.getWereRole(yakuList.get(0)) };
    if (godFlg) {
      //GM追加
      messages.add(CommonSubLogic.getWereMesse(4, umeji));

      for (int i = 1; i < yakuList.size(); i++) {
        messages.add(CommonSubLogic.getWereMesse(yakuList.get(i), umeji));
      }

    } else {
      // 村人追加（GMの役掛け用）
      messages.add(CommonSubLogic.getWereMesse(3, umeji));
      for (int i = 0; i < yakuList.size(); i++) {
        messages.add(CommonSubLogic.getWereMesse(yakuList.get(i), umeji));
      }

    }

    return messages;

  }

}
