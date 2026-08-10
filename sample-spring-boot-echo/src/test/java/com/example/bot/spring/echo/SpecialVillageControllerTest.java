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

package com.example.bot.spring.echo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.bot.spring.game.SpecialVillage;
import com.example.bot.spring.game.SpecialVillageList;

/** {@code POST /specialvillage}の契約テスト. */
public class SpecialVillageControllerTest {

  private MockMvc mockMvc;

  @Before
  public void setUp() {
    SpecialVillageList.clear();
    mockMvc = MockMvcBuilders.standaloneSetup(new SpecialVillageController())
        .setControllerAdvice(new ApiExceptionHandler())
        .build();
  }

  @Test
  public void createsVillageAndReturnsItsNumber() throws Exception {
    MvcResult result = mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":[\"役職1\",\"役職2\"]}"))
        .andExpect(status().isOk())
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(jsonPath("$.data").exists())
        .andReturn();

    int villageNum = Integer.parseInt(
        result.getResponse().getContentAsString().replaceAll("\\D", ""));
    assertTrue(villageNum >= 10000 && villageNum <= 99998);

    SpecialVillage village = SpecialVillageList.getVillage(villageNum);
    assertNotNull(village);
    assertEquals(2, village.getMessageList().size());
  }

  @Test
  public void acceptsBodySpreadOverMultipleLines() throws Exception {
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\n  \"message\": [\n    \"役職1\",\n    \"役職2\"\n  ]\n}"))
        .andExpect(status().isOk());
  }

  @Test
  public void rejectsMalformedJson() throws Exception {
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void rejectsMissingMessageField() throws Exception {
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"other\":[\"役職1\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void rejectsMessageOfWrongType() throws Exception {
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":\"役職1\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void rejectsEmptyMessageArray() throws Exception {
    // 1件も配れない村は作っても参加できず、村番号だけを浪費する
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void rejectsMoreMessagesThanAVillageCanHold() throws Exception {
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content(messageArrayOf(SpecialVillageController.MAX_MESSAGES + 1, "役職")))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void acceptsTheLargestAllowedVillage() throws Exception {
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content(messageArrayOf(SpecialVillageController.MAX_MESSAGES, "役職")))
        .andExpect(status().isOk());
  }

  @Test
  public void rejectsMessageLongerThanLineCanDeliver() throws Exception {
    String tooLong = repeat("あ", SpecialVillageController.MAX_MESSAGE_LENGTH + 1);

    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":[\"" + tooLong + "\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void acceptsTheLongestDeliverableMessage() throws Exception {
    String longest = repeat("あ", SpecialVillageController.MAX_MESSAGE_LENGTH);

    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":[\"" + longest + "\"]}"))
        .andExpect(status().isOk());
  }

  @Test
  public void rejectsStructuredMessageElements() throws Exception {
    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":[{\"role\":\"役職1\"}]}"))
        .andExpect(status().isBadRequest());

    mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":[[\"役職1\"]]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void numericMessageElementsAreStoredAsDeliverableText() throws Exception {
    // 以前はunchecked castをすり抜けてIntegerが混入し、参加時にCCEになっていた
    MvcResult result = mockMvc.perform(post("/specialvillage")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"message\":[1,2]}"))
        .andExpect(status().isOk())
        .andReturn();

    SpecialVillage village = SpecialVillageList.getVillage(
        Integer.parseInt(result.getResponse().getContentAsString().replaceAll("\\D", "")));
    assertTrue(village.join("user"));
    assertNotNull(village.getRoleMessage("user"));
  }

  private String messageArrayOf(int count, String text) {
    StringBuilder body = new StringBuilder("{\"message\":[");
    for (int i = 0; i < count; i++) {
      body.append(i == 0 ? "" : ",").append('"').append(text).append(i).append('"');
    }
    return body.append("]}").toString();
  }

  private static String repeat(String unit, int times) {
    StringBuilder builder = new StringBuilder(unit.length() * times);
    for (int i = 0; i < times; i++) {
      builder.append(unit);
    }
    return builder.toString();
  }
}
