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

package com.example.bot.staticdata;

import java.util.ArrayList;

import com.example.bot.spring.entity.Village;

public class VillageList {

  static final int MAX_VILLAGE_NUM = 50;
  private static ArrayList<Village> villageList = new ArrayList<Village>();

  public static ArrayList<Village> getVillageList() {
    return villageList;
  }

  public static ArrayList<Village> getVillageList(String userId) {
    ArrayList<Village> rtnList = new ArrayList<Village>();

    for (Village village : villageList) {
      if (userId.equals(village.getOwnerId())) {
        rtnList.add(village);
      }
    }

    return rtnList;
  }

  public static void addVillage(Village village) {
    villageList.add(village);

    if (villageList.size() > MAX_VILLAGE_NUM) {
      villageList.remove(0);
    }
  }

  public static Village getVillage(int villageNum) {
    return villageList.stream()
        .filter(dao -> villageNum == dao.getVillageNum()).findFirst().orElse(null);
  }

  public static Village get(int i) {
    return villageList.get(i);
  }

}
