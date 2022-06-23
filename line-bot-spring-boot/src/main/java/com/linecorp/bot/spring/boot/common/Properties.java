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

  //jdbc:postgresql://localhost:5432/postgres
  public static final String DB_URL = "jdbc:postgresql:"
      + "//ec2-34-235-92-52.compute-1.amazonaws.com:5432/d9tmatn9aj1v6j";

  //postgres
  public static final String DB_USER = "flxxcrisejfjsu";

  //root
  public static final String DB_PASS = "f608fb100882f007ffe980fed"
      + "a6f1dbf6221a073742f277497a75a3ecfae2afc";

  public static final String ERR_NOINPUT = "入力内容が不正です。";

}
