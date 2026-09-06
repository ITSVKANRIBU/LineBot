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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;


/** 通常村レジストリの採番・FIFO eviction・検索を固定する. */
public class VillageRegistryTest {

  private VillageRegistry registry;

  @Before
  public void setUp() {
    registry = new VillageRegistry();
  }

  @Test
  public void assignsUniqueFourDigitNumbers() {
    Set<Integer> numbers = new HashSet<Integer>();

    for (int i = 0; i < VillageRegistry.MAX_VILLAGE_COUNT; i++) {
      int villageNum = registry.addVillage(newVillage("owner"), new Random());
      assertTrue("村番号が4桁ではない: " + villageNum, villageNum >= 1000 && villageNum <= 9999);
      numbers.add(villageNum);
    }

    assertEquals(VillageRegistry.MAX_VILLAGE_COUNT, numbers.size());
  }

  @Test
  public void evictsTheOldestVillageBeyondTheLimit() {
    int oldest = registry.addVillage(newVillage("owner"), new Random());
    int second = registry.addVillage(newVillage("owner"), new Random());

    for (int i = 0; i < VillageRegistry.MAX_VILLAGE_COUNT - 1; i++) {
      registry.addVillage(newVillage("owner"), new Random());
    }

    assertNull("上限超過で最古の村がFIFOで削除される", registry.getVillage(oldest));
    assertNotNull(registry.getVillage(second));
  }

  @Test
  public void findLatestOwnedReturnsTheNewestMatchOfThatOwner() {
    registry.addVillage(newVillage("other"), new Random());
    int older = registry.addVillage(newVillage("owner"), new Random());
    int newer = registry.addVillage(newVillage("owner"), new Random());

    assertEquals(newer, registry.findLatestOwned("owner", village -> true).getVillageNum());

    // 新しい方を条件から外すと、次に新しい自分の村が返る
    assertEquals(older,
        registry.findLatestOwned("owner", village -> village.getVillageNum() != newer)
            .getVillageNum());
  }

  @Test
  public void findLatestOwnedIgnoresOtherOwners() {
    registry.addVillage(newVillage("other"), new Random());

    assertNull(registry.findLatestOwned("owner", village -> true));
  }

  private Village newVillage(String ownerId) {
    Village village = new Village();
    village.setOwnerId(ownerId);
    return village;
  }
}
