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

package com.example.bot.spring.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.model.message.Message;

public class VillageTest {

  @Test
  public void longInsiderMessageIncludesStatusWithoutMutatingSingletonList() {
    Village village = villageOf(2, insiderAt(1));
    village.setVillageNum(1234);
    village.applyOdai("長いお題長いお題長いお題長いお題長いお題長いお題長いお題"
        + "長いお題長いお題長いお題長いお題長いお題長いお題長いお題");
    assertNotNull(village.join("user"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("user"));

    List<Message> messages = village.getRoleMessage("user");

    assertEquals(2, messages.size());
  }

  @Test
  public void joinIsCapacityBoundAndIdempotent() {
    Village village = villageOf(1, insiderAt(1));

    assertNotNull(village.join("first"));
    assertNotNull(village.join("first"));
    assertNull(village.join("second"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("first"));
  }

  @Test
  public void normalVillageAssignsInsiderByJoinOrder() {
    Village village = villageOf(3, insiderAt(2));

    joinAll(village, "first", "second", "third");

    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("first"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("second"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("third"));
  }

  @Test
  public void godModeAssignsGameMasterAtItsOwnPosition() {
    Village village = new Village();
    village.setGmNum(MessageConst.DEFAULT_GMNUM);
    // インサイダーは1番目、GMは3番目
    village.configure(3, new FixedRandom(0, 2));

    joinAll(village, "first", "second", "third");

    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("first"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("second"));
    assertEquals(MessageConst.GAMEMASTER_ROLE, village.getMemberRole("third"));
  }

  @Test
  public void godModeNeverPutsTheGameMasterOnTheInsiderPosition() {
    Village village = new Village();
    village.setGmNum(MessageConst.DEFAULT_GMNUM);
    // GM位置がインサイダーと衝突したら引き直す
    village.configure(3, new FixedRandom(1, 1, 1, 0));

    assertEquals(2, village.getInsiderNum());
    assertEquals(1, village.getGmNum());
  }

  @Test
  public void reverseVillageInvertsInsiderAndVillagers() {
    Village village = villageOf(3, insiderAt(2));
    // 逆村: お題を知らない村人が1人だけになる
    assertTrue(village.applyReverseVillage());

    joinAll(village, "first", "second", "third");

    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("first"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("second"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("third"));
  }

  @Test
  public void reverseVillageIsRejectedOnceSomeoneJoined() {
    Village village = villageOf(3, insiderAt(2));
    assertNotNull(village.join("first"));

    // 参加後に切り替えると、既に配った配役と混在する
    assertFalse("参加者がいる村は逆村化しない", village.applyReverseVillage());
    assertFalse(village.isReverseVillage());
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("first"));
  }

  @Test
  public void concurrentJoinAndReverseSwitchNeverMixesRoles() throws Exception {
    // 逆村化と参加が競合しても、村全体はどちらか一方の配役に収まること。
    // 通常村ならインサイダーが1人、逆村なら村人が1人になる。
    for (int attempt = 0; attempt < 200; attempt++) {
      final Village village = villageOf(4, insiderAt(2));
      final CountDownLatch start = new CountDownLatch(1);
      ExecutorService pool = Executors.newFixedThreadPool(5);

      pool.submit(new Callable<Boolean>() {
        @Override
        public Boolean call() throws Exception {
          start.await();
          return village.applyReverseVillage();
        }
      });
      for (final String userId : new String[] { "u1", "u2", "u3", "u4" }) {
        pool.submit(new Callable<Object>() {
          @Override
          public Object call() throws Exception {
            start.await();
            return village.join(userId);
          }
        });
      }

      start.countDown();
      pool.shutdown();
      assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

      int insiders = 0;
      int villagers = 0;
      for (String userId : new String[] { "u1", "u2", "u3", "u4" }) {
        if (MessageConst.INSIDER_ROLE.equals(village.getMemberRole(userId))) {
          insiders++;
        } else if (MessageConst.VILLAGE_ROLE.equals(village.getMemberRole(userId))) {
          villagers++;
        }
      }

      int expectedMinority = village.isReverseVillage() ? villagers : insiders;
      int expectedMajority = village.isReverseVillage() ? insiders : villagers;
      assertEquals("attempt=" + attempt + " 逆村=" + village.isReverseVillage(),
          1, expectedMinority);
      assertEquals("attempt=" + attempt + " 逆村=" + village.isReverseVillage(),
          3, expectedMajority);
    }
  }

  @Test
  public void configureIsOneShotSoConcurrentSetupCannotReshuffleRoles() {
    Village village = villageOf(3, insiderAt(2));

    assertFalse("2度目の人数設定は拒否する", village.configure(5, insiderAt(1)));
    assertEquals(3, village.getVillageSize());
    assertEquals(2, village.getInsiderNum());
  }

  @Test
  public void applyOdaiIsOneShot() {
    Village village = new Village();

    assertTrue(village.applyOdai("すいか"));
    assertFalse(village.applyOdai("めろん"));
    assertEquals("すいか", village.getOdai());
  }

  @Test
  public void exactlyOneInsiderIsAssignedWhateverTheDraw() {
    for (int seed = 0; seed < 50; seed++) {
      Village village = new Village();
      village.configure(6, new Random(seed));

      joinAll(village, "u1", "u2", "u3", "u4", "u5", "u6");

      int insiders = 0;
      for (String userId : new String[] { "u1", "u2", "u3", "u4", "u5", "u6" }) {
        if (MessageConst.INSIDER_ROLE.equals(village.getMemberRole(userId))) {
          insiders++;
        }
      }
      assertEquals("seed=" + seed, 1, insiders);
    }
  }

  private Village villageOf(int size, Random random) {
    Village village = new Village();
    village.configure(size, random);
    return village;
  }

  /** インサイダーを{@code position}番目に固定する乱数. */
  private Random insiderAt(int position) {
    return new FixedRandom(position - 1);
  }

  private void joinAll(Village village, String... userIds) {
    List<String> joined = new ArrayList<String>();
    for (String userId : userIds) {
      assertNotNull("参加できなかった: " + userId, village.join(userId));
      joined.add(userId);
    }
    assertEquals(joined.size(), village.getMemberCount());
  }

  /** 決められた順に値を返すRandom. */
  private static final class FixedRandom extends Random {
    private static final long serialVersionUID = 1L;

    private final int[] values;
    private int index;

    FixedRandom(int... values) {
      this.values = values.clone();
    }

    @Override
    public int nextInt(int bound) {
      return values[index++];
    }
  }
}
