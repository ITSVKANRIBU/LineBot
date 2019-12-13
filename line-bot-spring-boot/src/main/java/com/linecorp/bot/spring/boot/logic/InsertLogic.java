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
package com.linecorp.bot.spring.boot.logic;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

import com.linecorp.bot.spring.boot.dao.ConnectionManager;
import com.linecorp.bot.spring.boot.dao.ProblemDao;
import com.linecorp.bot.spring.boot.entity.Problem;

public class InsertLogic {

  /**
   * 修正箇所.
   */
  public Problem addProblem(String theme, String name, int dificulty, String other) throws SQLException {

    try (Connection con = ConnectionManager.getConnection()) {
      ProblemDao dao = new ProblemDao(con);

      // 重複チェック
      Problem checkProblem = new Problem();
      checkProblem.setTheme(theme);
      ArrayList<Problem> resultList = dao.selectProblem(checkProblem);

      // 重複物あり
      if (resultList.size() > 0) {
        return resultList.get(0);
      }
      Problem newProblem = new Problem();
      newProblem.setTheme(theme);
      newProblem.setName(name);
      newProblem.setDifficulty(dificulty);
      newProblem.setOther(other);
      System.out.println(dao.insertProblem(newProblem));

    }
    return null;

  }

  /**
   * 取得.
   */
  public ArrayList<Problem> getProblemList() throws SQLException {
    ArrayList<Problem> resultList = null;
    try (Connection con = ConnectionManager.getConnection()) {
      ProblemDao dao = new ProblemDao(con);
      resultList = dao.selectProblem(new Problem());
    }
    return resultList;
  }
}
