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
import java.util.Random;

public class CreatVillage {

  /**
   * メッセージをランダムに並び替えて特殊村を作成する.
   *
   * @param messageList 参加者へ配るメッセージ。呼び出し元のリストは変更しない
   * @return 採番された村番号
   */
  public int createNewVillage(List<String> messageList) {
    // 呼び出し元のリストを壊さないよう、複製してから並び替える
    List<String> shuffled = new ArrayList<String>(messageList);
    Collections.shuffle(shuffled);

    return SpecialVillageList.addVillage(new SpecialVillage(shuffled), new Random());
  }
}
