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
package com.linecorp.bot.spring.boot.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.linecorp.bot.spring.boot.entity.Problem;

public class ProblemDao {
  private Connection con;

  public ProblemDao(Connection con) {
    this.con = con;
  }

  /**
   * 問題登録.
   */
  public int insertProblem(Problem problem) throws SQLException {

    // 戻り値 自動採番されたメンバーコード
    int id = 0;

    // nextvalmc はメンバーコードの自動採番を行う関数。role を引数に取る。

    // 同時に nextvalmc が実行された場合、後から実行された nextvalmc は
    // トランザクションがcommitされるまで待機するので、
    // トランザクションが異なる場合は、同じ値がinsertされることはない。

    String sql = "INSERT INTO PROBLEM(THEME,NAME,DIFFICULTY,OTHER) VALUES (?,?,?,?)";

    try (PreparedStatement stmt = con.prepareStatement(sql)) {
      stmt.setString(1, problem.getTheme());
      stmt.setString(2, problem.getName());
      stmt.setInt(3, problem.getDifficulty());
      stmt.setString(4, problem.getOther());
      stmt.executeUpdate();
    }

    String sql2 = "SELECT lastval()";

    try (PreparedStatement stmt = con.prepareStatement(sql2)) {

      try (ResultSet res = stmt.executeQuery();) {

        res.next();
        id = res.getInt(1);
      }
    }

    return id;

  }

}
