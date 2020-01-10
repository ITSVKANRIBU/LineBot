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

package com.example.bot.common;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Random;

public class WordGetter {

  public static final int FIRST_LINE = 61;
  public static final int SECOND_LINE = 954;
  public static final int THIRD_LINE = 5084;
  public static final int FOURTH_LINE = 7646;
  public static final int FIFTH_LINE = 8436;

  public static String getWord(int rank) {

    int line = 0;
    Random rand = new Random();
    if (1 == rank) {
      line = rand.nextInt(SECOND_LINE) + 1;
    } else if (2 == rank) {
      line = rand.nextInt(THIRD_LINE) + 1;
    } else if (3 == rank) {
      line = rand.nextInt(FOURTH_LINE - SECOND_LINE) + SECOND_LINE + 1;
    } else if (4 == rank) {
      line = rand.nextInt(FIFTH_LINE - FOURTH_LINE) + FOURTH_LINE + 1;
    } else {
      line = rand.nextInt(FOURTH_LINE) + 1;
    }

    String path = new File(".").getAbsoluteFile().getParent();
    File file = new File(path + "/sample-spring-boot-echo/src/main/resources/word.csv");
    String str = null;
    try {
      //文字コードUTF-8を指定してファイルを読み込む
      FileInputStream input = new FileInputStream(file);
      InputStreamReader stream = new InputStreamReader(input, "UTF-8");
      try (BufferedReader buffer = new BufferedReader(stream);) {

        //ファイルの最終行まで読み込む
        int i = 1;
        while ((str = buffer.readLine()) != null) {
          i++;
          if (i >= line) {
            break;
          }
        }
        str = buffer.readLine();

      } catch (Exception e) {
        throw e;
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return str;
  }
}
