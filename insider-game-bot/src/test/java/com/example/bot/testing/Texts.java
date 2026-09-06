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

package com.example.bot.testing;

/** テスト用の文字列を組み立てる. */
public final class Texts {

  private Texts() {
  }

  /**
   * 同じ文字列を繰り返した文字列を返す.
   *
   * <p>メッセージ長の閾値（ボタンテンプレートの160文字、LINEのテキストの5000文字）を
   * またぐ入力を作るために使う。{@code String.repeat}はJava 11以降のため使えない。
   *
   * @param unit 繰り返す文字列
   * @param times 繰り返し回数
   */
  public static String repeat(String unit, int times) {
    StringBuilder builder = new StringBuilder(unit.length() * times);
    for (int i = 0; i < times; i++) {
      builder.append(unit);
    }
    return builder.toString();
  }
}
