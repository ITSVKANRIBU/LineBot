package com.example.bot.spring.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CreatWereWordsLogic {
  public int createWereWords(boolean godFlg, int num, String theme) {
    return new CreatVillage().createNewVillage(getMessages(godFlg, num, theme));
  }

  public List<String> getMessages(boolean godFlg, int num, String theme) {
    List<String> messages = new ArrayList<String>();
    List<Integer> roleList = new ArrayList<Integer>();
    roleList.add(1);
    roleList.add(2);
    for (int i = 2; i < num; i++) {
      roleList.add(3);
    }
    Collections.shuffle(roleList);
    String[] placeholders = { theme, CommonSubLogic.getWereRole(roleList.get(0)) };
    if (godFlg) {
      messages.add(CommonSubLogic.getWereMesse(4, placeholders));
      for (int i = 1; i < roleList.size(); i++) {
        messages.add(CommonSubLogic.getWereMesse(roleList.get(i), placeholders));
      }
    } else {
      messages.add(CommonSubLogic.getWereMesse(3, placeholders));
      for (Integer role : roleList) {
        messages.add(CommonSubLogic.getWereMesse(role, placeholders));
      }
    }
    return messages;
  }
}
