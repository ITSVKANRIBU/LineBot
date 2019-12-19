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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import com.example.bot.staticdata.MessageConst;

public class CommonModule {

  private static Map<String, ArrayList<String>> illustrationMap;

  //ファイル名は　【役職】_【確率（整数）】_【任意】　とする。
  // 確率が高いほど当選確率よい

  static {

    illustrationMap = new HashMap<String, ArrayList<String>>();
    System.out.println("読み込み");

    String path = new File(".").getAbsoluteFile().getParent();
    System.out.println(path);

    File dirdefo = new File(path);
    File[] fileListdefo = dirdefo.listFiles();
    if (fileListdefo != null) {
      for (File file : fileListdefo) {
        System.out.println(file.getAbsolutePath());
        System.out.println(file.getName());
      }
    }

    File dir = new File(path + MessageConst.ILLUSTRATION_PATH);

    File[] fileList = dir.listFiles();

    if (fileList != null) {
      for (File file : fileList) {
        String[] fileName = file.getName().split("_");

        if (fileName.length != 3) {
          continue;
        }

        if (illustrationMap.containsKey(fileName[0])) {
          for (int i = 0; i < Integer.parseInt(fileName[1]); i++) {
            illustrationMap.get(fileName[0]).add(file.getName());
          }
        } else {
          ArrayList<String> newList = new ArrayList<String>();
          for (int i = 0; i < Integer.parseInt(fileName[1]); i++) {
            newList.add(file.getName());
          }
          System.out.println(file.getName());
          illustrationMap.put(fileName[0], newList);
        }

      }
    }
  }

  public static String getIllustUrl(String roleName) {

    String returnPath = null;
    if (illustrationMap.containsKey(roleName)) {
      ArrayList<String> nameList = illustrationMap.get(roleName);
      Random random = new Random();
      int num = random.nextInt(nameList.size());
      returnPath = MessageConst.ILLUSTRATION_URL_PREFIX + nameList.get(num);
    } else {
      returnPath = null;
    }
    return returnPath;
  }
}
