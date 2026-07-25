package com.example.bot.spring.game;

import java.util.ArrayList;
import java.util.Random;

public final class SpecialVillageList {
  static final int MAX_VILLAGE_NUM = 30;
  private static final ArrayList<SpecialVillage> villageList = new ArrayList<SpecialVillage>();

  private SpecialVillageList() { }

  public static ArrayList<SpecialVillage> getVillageList() { return villageList; }

  public static ArrayList<SpecialVillage> getVillageList(String userId) {
    ArrayList<SpecialVillage> result = new ArrayList<SpecialVillage>();
    for (SpecialVillage village : villageList) {
      if (userId.equals(village.getOwnerId())) {
        result.add(village);
      }
    }
    return result;
  }

  public static synchronized void addVillage(SpecialVillage village) {
    villageList.add(village);
    if (villageList.size() > MAX_VILLAGE_NUM) {
      // FIFO eviction is intentional runtime behavior.
      villageList.remove(0);
    }
  }

  public static synchronized SpecialVillage getVillage(int villageNum) {
    return villageList.stream()
        .filter(village -> villageNum == village.getVillageNum()).findFirst().orElse(null);
  }

  public static SpecialVillage get(int i) { return villageList.get(i); }

  /** Returns a free five-digit village number, with a deterministic fallback. */
  public static synchronized int nextVillageNumber(Random random) {
    for (int attempt = 0; attempt < 100; attempt++) {
      int candidate = random.nextInt(89999) + 10000;
      if (getVillage(candidate) == null) {
        return candidate;
      }
    }
    for (int candidate = 10000; candidate <= 99998; candidate++) {
      if (getVillage(candidate) == null) {
        return candidate;
      }
    }
    throw new IllegalStateException("No special village number is available");
  }
}
