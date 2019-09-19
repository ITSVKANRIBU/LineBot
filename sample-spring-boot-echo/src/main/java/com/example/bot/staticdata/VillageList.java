package com.example.bot.staticdata;

import java.util.ArrayList;

import com.example.bot.spring.entity.Village;

public class VillageList {

  static final int MAX_VILLAGE_NUM = 50;
  static public ArrayList<Village> villageList = new ArrayList<Village>();

  public static void addVillage(Village village) {
    villageList.add(village);

    if (villageList.size() > MAX_VILLAGE_NUM) {
      villageList.remove(0);
    }
  }

  public static Village getVillage(int villageNum) {
    return villageList.stream().filter(dao -> villageNum == dao.getVillageNum()).findFirst().orElse(null);
  }

  public static Village get(int i) {
    return villageList.get(i);
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

}
