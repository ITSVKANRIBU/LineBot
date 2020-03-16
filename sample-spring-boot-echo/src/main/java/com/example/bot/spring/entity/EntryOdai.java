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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class EntryOdai {

  private String odai;
  private String user;

  // 引数なしのコンストラクタを定義しないとデシリアライズ（JSON -> Javaオブジェクトへの変換）時にエラーが起きる
  public EntryOdai() {
  }

  public EntryOdai(String odai, String user) {
    this.odai = odai;
    this.user = user;
  }

  public String getOdai() {
    return odai;
  }

  public void setOdai(String odai) {
    this.odai = odai;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  @Override
  public String toString() {
    return "EntryOdai{"
        + "odai='" + odai + '\''
        + ", user=" + user
        + '}';
  }
}
