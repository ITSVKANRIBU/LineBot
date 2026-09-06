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
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

/**
 * 通常村のプロセス内レジストリ.
 *
 * <p>状態はプロセスメモリだけで保持し、{@link #MAX_VILLAGE_COUNT}件を超えると
 * 古い村からFIFOで削除する。再起動で失われる。
 * 参照・更新はすべてこのインスタンスのモニタ上で行うため、
 * 外部へ可変コレクションを公開しない。
 *
 * <p>本番ではsingletonのBeanが1つだけ存在する。テストは{@code new}で隔離する。
 */
@Component
public final class VillageRegistry {

  /** レジストリが保持する村の上限件数. 村番号の範囲とは別物. */
  static final int MAX_VILLAGE_COUNT = 50;

  /** 通常村の番号の最小値. */
  public static final int MIN_VILLAGE_NUMBER = 1000;
  /** 通常村の番号の最大値. */
  public static final int MAX_VILLAGE_NUMBER = 9999;

  private final ArrayList<Village> villageList = new ArrayList<Village>();

  /**
   * 空き番号を採番して村を登録する.
   *
   * <p>採番と登録を同一ロック内で行うため、同時実行でも番号が重複しない。
   *
   * @param village 登録する村
   * @param random 番号抽選に使う乱数
   * @return 採番された村番号
   */
  public synchronized int addVillage(Village village, Random random) {
    village.setVillageNum(nextVillageNumber(random));
    villageList.add(village);

    if (villageList.size() > MAX_VILLAGE_COUNT) {
      // FIFO eviction is intentional runtime behavior.
      villageList.remove(0);
    }

    return village.getVillageNum();
  }

  public synchronized Village getVillage(int villageNum) {
    return villageList.stream()
        .filter(dao -> villageNum == dao.getVillageNum()).findFirst().orElse(null);
  }

  /**
   * 指定ユーザーが所有し、条件を満たす最新の村を返す.
   *
   * @param userId オーナーのユーザーID
   * @param predicate 村の追加条件
   * @return 該当する最新の村。なければnull
   */
  public synchronized Village findLatestOwned(String userId, Predicate<Village> predicate) {
    for (int i = villageList.size() - 1; i >= 0; i--) {
      Village village = villageList.get(i);
      if (userId.equals(village.getOwnerId()) && predicate.test(village)) {
        return village;
      }
    }
    return null;
  }

  /** 呼び出し元がこのインスタンスのモニタを保持している前提で、未使用の4桁村番号を返す. */
  private int nextVillageNumber(Random random) {
    for (int attempt = 0; attempt < 100; attempt++) {
      // 抽選の範囲は最大値の1つ手前まで。最大値は総当たりでのみ採番される
      int candidate =
          random.nextInt(MAX_VILLAGE_NUMBER - MIN_VILLAGE_NUMBER) + MIN_VILLAGE_NUMBER;
      if (getVillage(candidate) == null) {
        return candidate;
      }
    }
    for (int candidate = MIN_VILLAGE_NUMBER; candidate <= MAX_VILLAGE_NUMBER; candidate++) {
      if (getVillage(candidate) == null) {
        return candidate;
      }
    }
    throw new IllegalStateException("No village number is available");
  }
}
