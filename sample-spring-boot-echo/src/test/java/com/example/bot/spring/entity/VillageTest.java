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
    village.setOdai("長いお題長いお題長いお題長いお題長いお題長いお題長いお題"
        + "長いお題長いお題長いお題長いお題長いお題長いお題長いお題");
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

  @Test
  public void normalVillageAssignsInsiderByJoinOrder() {
    Village village = new Village();
    village.setVillageSize(3);
    village.setInsiderNum(2);

    village.join("first");
    village.join("second");
    village.join("third");

    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("first"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("second"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("third"));
  }

  @Test
  public void godModeAssignsGameMasterAtItsOwnPosition() {
    Village village = new Village();
    village.setVillageSize(3);
    village.setInsiderNum(1);
    village.setGmNum(3);

    village.join("first");
    village.join("second");
    village.join("third");

    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("first"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("second"));
    assertEquals(MessageConst.GAMEMASTER_ROLE, village.getMemberRole("third"));
  }

  @Test
  public void reverseVillageInvertsInsiderAndVillagers() {
    Village village = new Village();
    village.setVillageSize(3);
    village.setInsiderNum(2);
    // 逆村: お題を知らない村人が1人だけになる
    village.setSpecialFlg(10);

    village.join("first");
    village.join("second");
    village.join("third");

    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("first"));
    assertEquals(MessageConst.VILLAGE_ROLE, village.getMemberRole("second"));
    assertEquals(MessageConst.INSIDER_ROLE, village.getMemberRole("third"));
  }
}
