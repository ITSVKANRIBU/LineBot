package com.example.bot.spring.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;

import com.example.bot.staticdata.MessageConst;
import com.linecorp.bot.model.message.Message;

public class VillageTest {
  @Test
  public void longInsiderMessageIncludesStatusWithoutMutatingSingletonList() {
    Village village = new Village();
    village.setVillageNum(1234);
    village.setVillageSize(2);
    village.setInsiderNum(1);
    village.setOdai("長いお題長いお題長いお題長いお題長いお題長いお題長いお題");
    village.addRoleList(null, "user");
    assertNotNull(village.setInsiderRole("user"));

    List<Message> messages = village.getRoleMessage("user");

    assertEquals(2, messages.size());
  }

  @Test
  public void joinIsCapacityBoundAndIdempotent() {
    Village village = new Village();
    village.setVillageSize(1);
    village.setInsiderNum(1);

    assertNotNull(village.join("first"));
    assertNotNull(village.join("first"));
    assertNull(village.join("second"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("first"));
  }
}
