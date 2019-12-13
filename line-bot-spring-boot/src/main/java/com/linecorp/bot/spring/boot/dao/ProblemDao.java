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
import java.util.ArrayList;

import com.linecorp.bot.spring.boot.entity.Problem;

public class ProblemDao {
  private Connection con;

  public ProblemDao(Connection con) {
    this.con = con;
  }

  /**
   * 登録.
   */
  public int insertProblem(Problem problem) throws SQLException {

    // 戻り値 自動採番されたコード
    int id = 0;

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

      try (ResultSet res = stmt.executeQuery()) {

        res.next();
        id = res.getInt(1);
      }
    }

    return id;

  }

  /**
   * 更新.
   */
  public int updateProblem(Problem problem) throws SQLException {

    // 戻り値 自動採番されたコード
    int count = 0;

    String sql = "UPDATE PROBLEM SET THEME = ?, NAME = ?, DIFFICULTY = ? ,OTHER = ? WHERE ID = ?";

    try (PreparedStatement stmt = con.prepareStatement(sql)) {
      stmt.setString(1, problem.getTheme());
      stmt.setString(2, problem.getName());
      stmt.setInt(3, problem.getDifficulty());
      stmt.setString(4, problem.getOther());
      stmt.setInt(5, problem.getId());
      count = stmt.executeUpdate();
    }
    return count;

  }

  /**
   * ID検索 THEME検索 NAME検索.
   */
  public ArrayList<Problem> selectProblem(Problem problem) throws SQLException {

    String sql = "SELECT ID,THEME,NAME,DIFFICULTY,OTHER FROM PROBLEM WHERE"
        + "(CASE WHEN ? = 0 THEN TRUE ELSE ID=? END) AND"
        + "(CASE WHEN ? is null or ? = '' THEN TRUE ELSE THEME=? END) AND"
        + "(CASE WHEN ? is null or ? = '' THEN TRUE ELSE NAME=? END)";

    ArrayList<Problem> resultList = new ArrayList<Problem>();
    try (PreparedStatement stmt = con.prepareStatement(sql)) {
      stmt.setInt(1, problem.getId());
      stmt.setInt(2, problem.getId());
      stmt.setString(3, problem.getTheme());
      stmt.setString(4, problem.getTheme());
      stmt.setString(5, problem.getTheme());
      stmt.setString(6, problem.getName());
      stmt.setString(7, problem.getName());
      stmt.setString(8, problem.getName());
      try (ResultSet res = stmt.executeQuery()) {
        while (res.next()) {
          Problem result = new Problem();
          result.setId(res.getInt("ID"));
          result.setTheme(res.getString("THEME"));
          result.setName(res.getString("NAME"));
          result.setDifficulty(res.getInt("DIFFICULTY"));
          result.setOther(res.getString("OTHER"));
          resultList.add(result);
        }
      }
    }
    return resultList;
  }

}
