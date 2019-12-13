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
import java.util.ArrayList;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.ui.Model;

import com.linecorp.bot.spring.boot.entity.Problem;
import com.linecorp.bot.spring.boot.logic.InsertLogic;

public class FrontServlet {
  /**
   * 修正箇所.
   */
  public String getnextPage(HttpServletRequest request, HttpServletResponse response, Model model) {
    try {
      model.addAttribute("buttonName", "登録");
      model.addAttribute("action", "entry");
      String button = request.getParameter("button");
      System.out.println("BUTTON=" + button);
      String subject = request.getParameter("subject");
      String id = request.getParameter("id");
      String name = request.getParameter("name");
      String difficulty = request.getParameter("difficulty");
      String comment = request.getParameter("comment");

      int difInt = 0;
      try {
        difInt = Integer.parseInt(difficulty);
      } catch (NumberFormatException e) {
        //処理なし
      }

      if ("list".equals(button)) {
        InsertLogic logic = new InsertLogic();
        ArrayList<Problem> problemList = null;
        if (!"".equals(name)) {
          problemList = logic.getProblemList(name);
        }
        model.addAttribute("problemList", problemList);
        return "List";
      }

      if ("delete".equals(button)) {
        //処理なし
      }

      if ("update".equals(button)) {
        // 同名登録
        int updateId = Integer.parseInt(id);
        Problem updateProblem = new Problem();
        updateProblem.setId(updateId);
        updateProblem.setTheme(subject.trim());
        updateProblem.setName(name);
        updateProblem.setDifficulty(difInt);
        updateProblem.setOther(comment);
        InsertLogic logic = new InsertLogic();
        logic.updateProblem(updateProblem);
        model.addAttribute("message", "更新完了しました。続けて登録できます。");
        // 初期遷移と同じ動作
        button = null;
      }

      if ("view".equals(button)) {
        InsertLogic logic = new InsertLogic();
        int viewId = Integer.parseInt(id);
        Problem result = logic.getProblem(viewId, null);
        model.addAttribute("subject", result.getTheme());
        model.addAttribute("name", result.getName());
        model.addAttribute("difficulty", result.getName());
        model.addAttribute("comment", result.getOther());
        model.addAttribute("id", result.getId());
        model.addAttribute("action", "update");
        model.addAttribute("buttonName", "更新");

        return "Entry";
      }
      //初期遷移 または お題がない場合
      if (button == null || subject == null || "".equals(subject)) {
        String userName = "";
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
          for (Cookie cook : cookies) {
            if (cook.getName().equals("userName")) {
              userName = cook.getValue();
            }
          }
        }
        model.addAttribute("name", userName);
      } else if ("entry".equals(button)) {
        System.out.println("subject=" + subject);
        System.out.println("name=" + name);
        System.out.println("difficulty=" + difficulty);
        System.out.println("comment=" + comment);
        System.out.println("id=" + id);

        // クッキーへ名前を保存
        String nameStr = name.trim().replaceAll("[\\r\\n]", "");
        Cookie cookName = new Cookie("userName", nameStr);
        cookName.setMaxAge(3600000);
        response.addCookie(cookName);

        InsertLogic logic = new InsertLogic();

        Problem result = logic.addProblem(subject.trim(), name.trim(), difInt, comment);

        if (result == null) {
          // 次の遷移先
          model.addAttribute("name", name);
          model.addAttribute("message", "登録完了しました。続けて登録できます。");
        } else {
          model.addAttribute("errMessage", "既に同じものが登録されています。");
          model.addAttribute("subject", result.getTheme());
          model.addAttribute("name", result.getName());
          model.addAttribute("difficulty", result.getName());
          model.addAttribute("comment", result.getOther());
          model.addAttribute("id", result.getId());
        }

      }

    } catch (SQLException | NumberFormatException | NullPointerException e) {
      e.printStackTrace();
      model.addAttribute("errMessage", "予期せぬ例外が発生しました。");
    }
    return "Entry";
  }

}
