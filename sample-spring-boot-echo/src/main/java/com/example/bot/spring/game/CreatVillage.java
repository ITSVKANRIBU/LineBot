package com.example.bot.spring.game;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class CreatVillage {
  public int createNewVillage(List<String> messageList) {
    Random random = new Random();
    int villageNum = SpecialVillageList.nextVillageNumber(random);

    SpecialVillage village = new SpecialVillage();
    village.setOwnerId("DEFOLT");
    village.setVillageNum(villageNum);
    Collections.shuffle(messageList);
    village.setMessageList(messageList);
    village.setUserList(new CopyOnWriteArrayList<String>());
    SpecialVillageList.addVillage(village);
    return villageNum;
  }
}
