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

import java.util.Collections;
import java.util.List;
import java.util.Random;

import com.linecorp.bot.spring.boot.common.SpecialVillageList;
import com.linecorp.bot.spring.boot.entity.SpecialVillage;

public class CreatVillage {

  /**
   * 修正.
   * @param messageList List.
   * @return
   */
  public int createNewVillage(List<String> messageList) {
    int villageNum = 0;
    Random random = new Random();

    villageNum = random.nextInt(89999) + 10000;

    // 重複しない番号取得（防止のため、100回まで）
    for (int i = 0; i < 100; i++) {
      boolean breakFlg = true;
      for (SpecialVillage dao : SpecialVillageList.getVillageList()) {
        if (villageNum == dao.getVillageNum()) {
          villageNum = random.nextInt(89999) + 10000;
          breakFlg = false;
          break;
        }
      }
      if (breakFlg) {
        break;
      }
    }

    SpecialVillage newVillage = new SpecialVillage();
    newVillage.setOwnerId("DEFOLT");
    newVillage.setVillageNum(villageNum);

    // メッセージをランダムに並び替え
    Collections.shuffle(messageList);
    newVillage.setMessageList(messageList);

    // 追加
    SpecialVillageList.addVillage(newVillage);

    return villageNum;
  }
}
