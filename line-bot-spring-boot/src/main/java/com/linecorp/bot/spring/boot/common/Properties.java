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
package com.linecorp.bot.spring.boot.common;

public class Properties {

  //REMOVED_DB_DRIVER//localhost:5432/postgres
  public static final String DB_URL = "REMOVED_DB_DRIVER"
      + "//REMOVED_DB_HOST:5432/REMOVED_DB_NAME";

  //postgres
  public static final String DB_USER = "REMOVED_DB_USER";

  //root
  public static final String DB_PASS = "REMOVED_DB_PASSWORD"
      + "REMOVED_DB_PASSWORD";

  public static final String ERR_NOINPUT = "入力内容が不正です。";

}
