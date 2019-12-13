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

  //jdbc:postgresql://ec2-174-129-254-235.compute-1.amazonaws.com:5432/ddslj8lrpcv3ta
  //jdbc:postgresql://localhost:5432/postgres
  public static final String DB_URL = "jdbc:postgresql:"
      + "//ec2-174-129-254-235.compute-1.amazonaws.com:5432/ddslj8lrpcv3ta";

  //xteakkwpfsuepu
  //postgres
  public static final String DB_USER = "xteakkwpfsuepu";

  //9514102d518dc6dc4b4c251451e2caaa90b85dccec6f07d5ce034ee56394d047
  //root
  public static final String DB_PASS = "9514102d518dc6dc4b4c251451e2"
      + "caaa90b85dccec6f07d5ce034ee56394d047";

  public static final String ERR_NOINPUT = "入力内容が不正です。";

}
