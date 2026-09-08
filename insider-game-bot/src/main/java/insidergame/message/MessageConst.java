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

package insidergame.message;

public class MessageConst {

  public static final String DEFAULT_MESSAGE = "お題を配りたい方は「お題」または「神」を、\n"
      + "お題及び役職を確認したい場合は村番号（数字4桁）を入力してください。";

  public static final String OWNER_ODAIMESSAGE = "お題を入力してください。";
  public static final String OWNER_NUMSETMESSAGE = "お題を配りたい人数を入力してください。"
      + "\n　例：「5」の場合は、「インサイダー１人、村4人」です。";
  public static final String GOD_NUMSETMESSAGE = "お題を配りたい人数を入力してください。"
      + "\n　例：「6」の場合は、「GM１人、インサイダー１人、村4人」です。";
  public static final String RANDOM_NUMSETMESSAGE = "人数を設定してください。"
      + "役職もお題もランダムに配ります。";
  public static final String ERR_NUMSETMESSAGE = "村の人数は2人以上に設定してください。\n"
      + "もう一度村の人数を設定してください。";
  public static final String ERR_UNIDENTIFIED_USER = "ユーザーを識別できないため操作できません。\n"
      + "botとの1対1のトークから操作してください。";
  public static final String INSIDER_ROLE = "インサイダー";
  public static final String VILLAGE_ROLE = "村人";
  public static final String GAMEMASTER_ROLE = "ＧＭ";
  public static final int DEFAULT_GMNUM = 999;
  // 既定画像は raw.githubusercontent が配信するので、ブランチ名がそのまま公開URLに入る。
  // 参照先のブランチを消すと利用者に画像が出なくなるため、ブランチ名はここ1箇所だけに書き、
  // 個別のURLはこの接頭辞から組み立てる (4本だけ直して1本取り残す事故を構造で防ぐ)。
  public static final String ILLUSTRATION_URL_PREFIX = "https://raw.githubusercontent.com/"
      + "ITSVKANRIBU/LineBot/master/Image/";

  //URL
  public static final String GOD_URL = ILLUSTRATION_URL_PREFIX + "GOD.png";
  public static final String GM_URL = ILLUSTRATION_URL_PREFIX + "GM.png";
  public static final String INSIDER_URL = ILLUSTRATION_URL_PREFIX + "INSIDER.png";
  public static final String VILLAGERS_URL = ILLUSTRATION_URL_PREFIX + "VILLAGERS.png";

}
