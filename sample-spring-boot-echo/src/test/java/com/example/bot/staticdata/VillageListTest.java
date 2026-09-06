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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.example.bot.spring.entity.Village;

/** 通常村レジストリの採番・FIFO eviction・検索を固定する. */
public class VillageListTest {

  @Before
  public void resetRegistry() {
    VillageList.clear();
  }

  @Test
  public void assignsUniqueFourDigitNumbers() {
    Set<Integer> numbers = new HashSet<Integer>();

    for (int i = 0; i < VillageList.MAX_VILLAGE_COUNT; i++) {
      int villageNum = VillageList.addVillage(newVillage("owner"), new Random());
      assertTrue("村番号が4桁ではない: " + villageNum, villageNum >= 1000 && villageNum <= 9999);
      numbers.add(villageNum);
    }

    assertEquals(VillageList.MAX_VILLAGE_COUNT, numbers.size());
  }

  @Test
  public void evictsTheOldestVillageBeyondTheLimit() {
    int oldest = VillageList.addVillage(newVillage("owner"), new Random());
    int second = VillageList.addVillage(newVillage("owner"), new Random());

    for (int i = 0; i < VillageList.MAX_VILLAGE_COUNT - 1; i++) {
      VillageList.addVillage(newVillage("owner"), new Random());
    }

    assertNull("上限超過で最古の村がFIFOで削除される", VillageList.getVillage(oldest));
    assertNotNull(VillageList.getVillage(second));
  }

  @Test
  public void findLatestOwnedReturnsTheNewestMatchOfThatOwner() {
    VillageList.addVillage(newVillage("other"), new Random());
    int older = VillageList.addVillage(newVillage("owner"), new Random());
    int newer = VillageList.addVillage(newVillage("owner"), new Random());

    assertEquals(newer, VillageList.findLatestOwned("owner", village -> true).getVillageNum());

    // 新しい方を条件から外すと、次に新しい自分の村が返る
    assertEquals(older,
        VillageList.findLatestOwned("owner", village -> village.getVillageNum() != newer)
            .getVillageNum());
  }

  @Test
  public void findLatestOwnedIgnoresOtherOwners() {
    VillageList.addVillage(newVillage("other"), new Random());

    assertNull(VillageList.findLatestOwned("owner", village -> true));
  }

  private Village newVillage(String ownerId) {
    Village village = new Village();
    village.setOwnerId(ownerId);
    return village;
  }
}
