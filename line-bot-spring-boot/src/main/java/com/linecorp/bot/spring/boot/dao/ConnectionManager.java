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
import java.sql.DriverManager;
import java.sql.SQLException;

import com.linecorp.bot.spring.boot.common.Properties;

/**
 * コネクションマネージャー.
 */
public class ConnectionManager {

  /**
   * データベースの接続を取得する.
   * @return データベースの接続
   * @throws SQLException DB接続に失敗した場合
   */
  public static synchronized Connection getConnection() throws SQLException {

    Connection con = null;
    con = DriverManager.getConnection(Properties.DB_URL, Properties.DB_USER, Properties.DB_PASS);

    return con;
  }

}
