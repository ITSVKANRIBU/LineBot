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
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

/** 特殊村の作成が、呼び出し元から渡されたメッセージ列をどう扱うかを固定する. */
public class CreateVillageTest {

  private final CreateVillage creatVillage = new CreateVillage();

  @Before
  public void resetRegistry() {
    SpecialVillageList.clear();
  }

  /**
   * 呼び出し元のリストを並び替えない.
   *
   * <p>配布順は村の状態であって、渡した側の持ち物ではない。呼び出し元が
   * 同じリストを検証や再利用に使っても、村の採番によって順序が変わらないようにする。
   */
  @Test
  public void theCallersListIsLeftUntouched() {
    List<String> original = Arrays.asList("1人目", "2人目", "3人目", "4人目", "5人目");
    List<String> messages = new ArrayList<String>(original);

    creatVillage.createNewVillage(messages);

    assertEquals(original, messages);
  }

  @Test
  public void everyMessageIsRegisteredForDistribution() {
    List<String> messages = Arrays.asList("1人目", "2人目", "3人目");

    int villageNum = creatVillage.createNewVillage(messages);

    SpecialVillage village = SpecialVillageList.getVillage(villageNum);
    assertTrue(village.getMessageList().containsAll(messages));
    assertEquals(messages.size(), village.getMessageList().size());
    assertTrue(villageNum >= 10000 && villageNum <= 99998);
  }
}
