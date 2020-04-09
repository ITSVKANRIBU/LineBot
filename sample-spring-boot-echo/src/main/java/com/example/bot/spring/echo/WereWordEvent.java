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

import java.util.Collections;
import java.util.List;

import com.example.bot.spring.entity.Village;
import com.example.bot.staticdata.MessageConst;

import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.spring.boot.logic.CreatWereWordsLogic;

public class WereWordEvent {

  static final String DEFOLT_MESSAGE = "";

  public List<Message> branch(Village village) {

    String odai = village.getOdai();
    int villageSize = village.getVillageSize();
    boolean gotFlg = village.getGmNum() == 0 ? false : true;

    // お題と人数チェック
    if (villageSize <= 2
        || odai == null) {
      return Collections.singletonList(new TextMessage(MessageConst.WEREWORD_ERR));
    }
    CreatWereWordsLogic createLogic = new CreatWereWordsLogic();
    int num = createLogic.createWereWords(gotFlg, villageSize, odai);

    String message = "お題を『" + odai + "』として新たにワーワーズの『" + num + "』村を作成しました。";

    if (gotFlg) {
      message = message + "参加者へ『" + num + "』を伝えてください。";
    } else {
      message = message + "参加者へ『" + num + "』を伝え、あなたも入室してください。\n"
          + "\n■注意\n"
          + "あなたはGMです。入室時に表示された役職が欠けた役職となります。";
    }

    return Collections.singletonList(new TextMessage(message));

  }

}
