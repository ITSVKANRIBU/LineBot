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
package com.linecorp.bot.spring.boot.servlet;

import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.ui.Model;

import com.linecorp.bot.spring.boot.dao.ConnectionManager;

public class FrontServlet {
  /**
   * 修正箇所.
   */
  public String getnextPage(HttpServletRequest request, HttpServletResponse response, Model model) {
    String subject = request.getParameter("subject");
    String name = request.getParameter("name");
    String difficulty = request.getParameter("difficulty");
    String comment = request.getParameter("comment");

    System.out.println(subject + name + difficulty + comment);
    model.addAttribute("subject", subject);
    model.addAttribute("name", name);
    model.addAttribute("difficulty", difficulty);
    model.addAttribute("comment", comment);

    //    List<Problem> problemList = repository.findAll();
    //    for (Problem problem : problemList) {
    //      System.out.println(problem.getTheme());
    //    }

    try {
      ConnectionManager.getConnection();
    } catch (SQLException e) {
      e.printStackTrace();
    }

    return "01_Login.html";
  }

}
