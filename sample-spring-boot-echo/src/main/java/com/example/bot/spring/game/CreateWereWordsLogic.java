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

public class CreateWereWordsLogic {

  /**
   * Werewords村を作成する.
   *
   * @param godMode GMを立てる場合true
   * @param num 参加人数
   * @param theme お題
   * @return 採番された村番号
   */
  public int createWereWords(boolean godMode, int num, String theme) {
    return new CreateVillage().createNewVillage(getMessages(godMode, num, theme));
  }

  /**
   * 配布順のメッセージを組み立てる。先頭の役職を「欠け」として扱う.
   *
   * @param godMode GMを立てる場合true
   * @param num 参加人数
   * @param theme お題
   * @return 参加順に配るメッセージ
   */
  public List<String> getMessages(boolean godMode, int num, String theme) {
    List<String> messages = new ArrayList<String>();

    // 役職判定リスト生成
    List<Integer> roleList = new ArrayList<Integer>();
    roleList.add(1);//占師
    roleList.add(2);//インサイダー

    // 村人追加
    for (int i = 2; i < num; i++) {
      roleList.add(3);//村人
    }

    // ランダムに並び替える
    Collections.shuffle(roleList);

    // 先頭の役職を欠けとしてメッセージ設定
    String[] placeholders = { theme, CommonSubLogic.getWereRole(roleList.get(0)) };
    if (godMode) {
      //GM追加
      messages.add(CommonSubLogic.getWereMessage(4, placeholders));
      for (int i = 1; i < roleList.size(); i++) {
        messages.add(CommonSubLogic.getWereMessage(roleList.get(i), placeholders));
      }
    } else {
      // 村人追加（GMの役掛け用）
      messages.add(CommonSubLogic.getWereMessage(3, placeholders));
      for (Integer role : roleList) {
        messages.add(CommonSubLogic.getWereMessage(role, placeholders));
      }
    }

    return messages;
  }
}
