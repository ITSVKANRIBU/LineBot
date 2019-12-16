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

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ErrCheck {

  public static final List<String> ERR_WORDS = Collections
      .unmodifiableList(new LinkedList<String>() {
        {
          add("神");
          add("お題");
          add("お題取得");
          add("題");
          add("配布");
          add("ヘルプ");
        }
      });

  /**
   * エラーの時true.
   */
  public static boolean checkOdai(String odai) {
    boolean result = false;

    if (odai == null) {
      return true;
    }

    try {
      Integer.parseInt(odai);
      result = true;
    } catch (NumberFormatException e) {
      for (String tmp : ERR_WORDS) {
        if (tmp.equals(odai)) {
          result = true;
          break;
        }
      }
    }

    return result;
  }

}