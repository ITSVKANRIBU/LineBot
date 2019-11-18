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

package com.example.bot.staticdata;

public class MessageConst {

  public static final String DEFAILT_MESSAGE = "お題を配りたい方は「お題」または「神」を、\n"
      + "お題及び役職を確認したい場合は村番号（数字4桁）を入力してください。";

  public static final String OWNER_ODAIMESSAGE = "お題を入力してください。";
  public static final String OWNER_NUMSETMESSAGE = "お題を配りたい人数を入力してください。"
      + "\n　例：「5」の場合は、「インサイダー１人、村4人」です。";
  public static final String GOD_NUMSETMESSAGE = "お題を配りたい人数を入力してください。"
      + "\n　例：「6」の場合は、「GM１人、インサイダー１人、村4人」です。";
  public static final String ERR_NUMSETMESSAGE = "村の人数は2人以上に設定してください。\n"
      + "もう一度村の人数を設定してください。";
  public static final String OWNER_CONFMESSAGE = "配布状況を確認したい場合は"
      + "村番号を入力してください。";
  public static final String INSIDER_ROLE = "インサイダー";
  public static final String VILLAGE_ROLE = "村人";
  public static final String GAMEMASTER_ROLE = "ＧＭ";
  public static final int DEFAULT_GMNUM = 999;

  //URL
  public static final String GOD_URL = "https://raw.githubusercontent.com/"
      + "ITSVKANRIBU/LineBot/3.0/Image/GOD.png";
  public static final String GM_URL = "https://raw.githubusercontent.com/"
      + "ITSVKANRIBU/LineBot/3.0/Image/GM.png";
  public static final String INSIDER_URL = "https://raw.githubusercontent.com/"
      + "ITSVKANRIBU/LineBot/3.0/Image/INSIDER.png";
  public static final String VILLAGERS_URL = "https://raw.githubusercontent.com/"
      + "ITSVKANRIBU/LineBot/3.0/Image/VILLAGERS.png";

}
