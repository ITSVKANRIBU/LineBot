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
import java.util.Random;

import org.springframework.stereotype.Component;

/**
 * 特殊村のプロセス内レジストリ.
 *
 * <p>状態はプロセスメモリだけで保持し、{@link #MAX_VILLAGE_COUNT}件を超えると
 * 古い村からFIFOで削除する。再起動で失われる。
 *
 * <p>本番ではsingletonのBeanが1つだけ存在する。テストは{@code new}で隔離する。
 */
@Component
public final class SpecialVillageRegistry {

  /** レジストリが保持する村の上限件数. 村番号の範囲とは別物. */
  static final int MAX_VILLAGE_COUNT = 30;

  /** 特殊村の番号の最小値. */
  public static final int MIN_VILLAGE_NUMBER = 10000;
  /** 特殊村の番号の最大値. */
  public static final int MAX_VILLAGE_NUMBER = 99998;

  private final ArrayList<SpecialVillage> villageList = new ArrayList<SpecialVillage>();

  /**
   * 空き番号を採番して特殊村を登録する.
   *
   * <p>採番と登録を同一ロック内で行うため、同時実行でも番号が重複しない。
   *
   * @param village 登録する特殊村
   * @param random 番号抽選に使う乱数
   * @return 採番された村番号
   */
  public synchronized int addVillage(SpecialVillage village, Random random) {
    village.setVillageNum(nextVillageNumber(random));
    villageList.add(village);

    if (villageList.size() > MAX_VILLAGE_COUNT) {
      // FIFO eviction is intentional runtime behavior.
      villageList.remove(0);
    }

    return village.getVillageNum();
  }

  public synchronized SpecialVillage getVillage(int villageNum) {
    return villageList.stream()
        .filter(village -> villageNum == village.getVillageNum()).findFirst().orElse(null);
  }

  /** 呼び出し元がこのインスタンスのモニタを保持している前提で、未使用の5桁村番号を返す. */
  private int nextVillageNumber(Random random) {
    for (int attempt = 0; attempt < 100; attempt++) {
      int candidate =
          random.nextInt(MAX_VILLAGE_NUMBER - MIN_VILLAGE_NUMBER + 1) + MIN_VILLAGE_NUMBER;
      if (getVillage(candidate) == null) {
        return candidate;
      }
    }
    for (int candidate = MIN_VILLAGE_NUMBER; candidate <= MAX_VILLAGE_NUMBER; candidate++) {
      if (getVillage(candidate) == null) {
        return candidate;
      }
    }
    throw new IllegalStateException("No special village number is available");
  }
}
