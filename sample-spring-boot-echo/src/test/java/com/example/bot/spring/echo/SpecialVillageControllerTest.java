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
}
