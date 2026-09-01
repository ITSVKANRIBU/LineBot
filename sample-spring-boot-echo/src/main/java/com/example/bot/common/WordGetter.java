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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import lombok.extern.slf4j.Slf4j;

/**
 * お題CSVから難易度別にお題を1つ引く.
 *
 * <p>CSVはclasspath上の{@value #RESOURCE_NAME}を起動時に一度だけ読み込む。
 * 実行ディレクトリからの相対パスで読むと、jar実行時やテスト実行時に
 * お題を1つも引けなくなるため、リソースとして解決する。
 */
@Slf4j
public class WordGetter {

  /** 難易度の境界。値はCSVの行番号（1始まり）で、その難易度の最終行を指す. */
  public static final int SECOND_LINE = 954;
  public static final int THIRD_LINE = 5084;
  public static final int FOURTH_LINE = 7646;
  public static final int FIFTH_LINE = 8436;

  /** 「初心者」の難易度. */
  public static final int BEGINNER_RANK = 2;

  static final String RESOURCE_NAME = "word.csv";

  private static final List<String> WORDS = loadWords();

  /**
   * 指定した難易度のお題を1つ返す.
   *
   * @param rank 難易度。1、{@link #BEGINNER_RANK}（初心者）、3（上級者）、4（変態）
   * @return お題。お題CSVを読み込めなかった場合はnull
   */
  public static String getWord(int rank) {
    if (WORDS.isEmpty()) {
      return null;
    }

    Random rand = new Random();
    int line;
    if (1 == rank) {
      line = rand.nextInt(SECOND_LINE) + 1;
    } else if (BEGINNER_RANK == rank) {
      line = rand.nextInt(THIRD_LINE) + 1;
    } else if (3 == rank) {
      line = rand.nextInt(FOURTH_LINE - SECOND_LINE) + SECOND_LINE + 1;
    } else if (4 == rank) {
      line = rand.nextInt(FIFTH_LINE - FOURTH_LINE) + FOURTH_LINE + 1;
    } else {
      line = rand.nextInt(FOURTH_LINE) + 1;
    }

    return WORDS.get(Math.min(line, WORDS.size()) - 1);
  }

  /** CSVの各行の1列目を行順に読み込む。難易度の境界を行番号で表すため、行を間引かない. */
  private static List<String> loadWords() {
    InputStream input = WordGetter.class.getClassLoader().getResourceAsStream(RESOURCE_NAME);
    if (input == null) {
      log.error("Word CSV {} is not on the classpath", RESOURCE_NAME);
      return Collections.emptyList();
    }

    List<String> words = new ArrayList<String>();
    try (BufferedReader buffer = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = buffer.readLine()) != null) {
        words.add(line.split(",")[0]);
      }
    } catch (IOException e) {
      log.error("Failed to read the word CSV", e);
    }

    return Collections.unmodifiableList(words);
  }
}
