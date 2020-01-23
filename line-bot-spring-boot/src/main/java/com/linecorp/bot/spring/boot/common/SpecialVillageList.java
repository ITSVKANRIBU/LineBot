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

import java.util.ArrayList;

import com.linecorp.bot.spring.boot.entity.SpecialVillage;

public class SpecialVillageList {

  static final int MAX_VILLAGE_NUM = 30;
  private static ArrayList<SpecialVillage> villageList = new ArrayList<SpecialVillage>();

  public static ArrayList<SpecialVillage> getVillageList() {
    return villageList;
  }

  /**
   * 特殊村用.
   * @param userId ユーザーID
   * @return
   */
  public static ArrayList<SpecialVillage> getVillageList(String userId) {
    ArrayList<SpecialVillage> rtnList = new ArrayList<SpecialVillage>();

    for (SpecialVillage village : villageList) {
      if (userId.equals(village.getOwnerId())) {
        rtnList.add(village);
      }
    }

    return rtnList;
  }

  /**
   * 特殊村用.
   * @param village 村.
   * @return
   */
  public static void addVillage(SpecialVillage village) {
    villageList.add(village);

    if (villageList.size() > MAX_VILLAGE_NUM) {
      villageList.remove(0);
    }
  }

  /**
   * 特殊村用.
   * @param villageNum 村番号.
   * @return
   */
  public static SpecialVillage getVillage(int villageNum) {
    return villageList.stream()
        .filter(dao -> villageNum == dao.getVillageNum()).findFirst().orElse(null);
  }

  public static SpecialVillage get(int i) {
    return villageList.get(i);
  }

}
