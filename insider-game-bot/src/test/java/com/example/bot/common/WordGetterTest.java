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

import static com.example.bot.common.WordGetter.BEGINNER_RANK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

/**
 * お題CSVの読み込みと難易度の範囲を固定する.
 *
 * <p>実行ディレクトリからの相対パスで読むと、テストやjar実行では
 * 1つもお題を引けない。ランダム村はお題を自動で決めるため、
 * 読み込めていないことに気付けないまま『null』を配ってしまう。
 *
 * <p>難易度の区間はCSVの2列目から導出する。行番号定数を使っていた頃と
 * 同じ区間になることを、境界値と抽選結果の両方で固定する。
 */
public class WordGetterTest {

  /** 行番号定数で表現していた頃の難易度境界. 導出結果がこれと一致すること. */
  private static final int SECOND_LINE = 954;
  private static final int THIRD_LINE = 5084;
  private static final int FOURTH_LINE = 7646;
  private static final int FIFTH_LINE = 8436;

  @Test
  public void theDifficultyBoundariesDerivedFromTheCsvMatchTheFormerConstants() {
    assertEquals(SECOND_LINE, WordGetter.lastLineOf(2));
    assertEquals(THIRD_LINE, WordGetter.lastLineOf(3));
    assertEquals(FOURTH_LINE, WordGetter.lastLineOf(4));
    assertEquals(FIFTH_LINE, WordGetter.lastLineOf(5));
    assertEquals(FIFTH_LINE, WordGetter.wordCount());
  }

  /** 導出は昇順を前提にしている。CSVがその前提を満たすことを固定する. */
  @Test
  public void theCsvIsSortedByDifficulty() throws IOException {
    int previous = 0;
    int lineNumber = 0;

    for (String[] columns : csvRows()) {
      lineNumber++;
      assertTrue("2列目がない: " + lineNumber, columns.length >= 2);

      int difficulty = Integer.parseInt(columns[1].trim());
      assertTrue("難易度が1〜5でない行: " + lineNumber, difficulty >= 1 && difficulty <= 5);
      assertTrue("難易度が昇順でない行: " + lineNumber, difficulty >= previous);
      previous = difficulty;
    }
  }

  @Test
  public void beginnerWordsAreDrawnFromTheBeginnerRangeOfTheCsv() throws IOException {
    Set<String> beginnerWords = csvWords(1, THIRD_LINE);

    for (int attempt = 0; attempt < 200; attempt++) {
      String word = WordGetter.getWord(BEGINNER_RANK);

      assertNotNull("お題CSVをclasspathから読み込めていない", word);
      assertTrue("初心者の範囲外のお題: " + word, beginnerWords.contains(word));
    }
  }

  @Test
  public void advancedWordsAreDrawnFromTheAdvancedRangeOfTheCsv() throws IOException {
    assertEveryDrawComesFrom(3, SECOND_LINE + 1, FOURTH_LINE);
  }

  @Test
  public void expertWordsAreDrawnFromTheExpertRangeOfTheCsv() throws IOException {
    assertEveryDrawComesFrom(4, FOURTH_LINE + 1, FIFTH_LINE);
  }

  /** 難易度を指定しないpostback（0や@取得の10）は、変態を除く全範囲から引く. */
  @Test
  public void unspecifiedDifficultyDrawsFromEverythingButTheExpertRange() throws IOException {
    assertEveryDrawComesFrom(0, 1, FOURTH_LINE);
    assertEveryDrawComesFrom(10, 1, FOURTH_LINE);
  }

  @Test
  public void everyDifficultyDrawsAWord() {
    // UIから送られる難易度は2（初心者）・3（上級者）・4（変態）
    for (int rank = BEGINNER_RANK; rank <= 4; rank++) {
      assertNotNull("難易度" + rank + "のお題を引けない", WordGetter.getWord(rank));
    }
  }

  private void assertEveryDrawComesFrom(int rank, int fromLine, int toLine) throws IOException {
    Set<String> allowed = csvWords(fromLine, toLine);

    for (int attempt = 0; attempt < 200; attempt++) {
      String word = WordGetter.getWord(rank);

      assertNotNull("お題CSVをclasspathから読み込めていない", word);
      assertTrue("難易度" + rank + "の範囲外のお題: " + word, allowed.contains(word));
    }
  }

  /** CSVの{@code fromLine}行目から{@code toLine}行目（いずれも1始まり・両端含む）の1列目. */
  private Set<String> csvWords(int fromLine, int toLine) throws IOException {
    Set<String> words = new HashSet<String>();
    int lineNumber = 0;

    for (String[] columns : csvRows()) {
      lineNumber++;
      if (lineNumber >= fromLine && lineNumber <= toLine) {
        words.add(columns[0]);
      }
    }
    return words;
  }

  private List<String[]> csvRows() throws IOException {
    InputStream input = WordGetter.class.getClassLoader()
        .getResourceAsStream(WordGetter.RESOURCE_NAME);
    assertNotNull("お題CSVがclasspathにない", input);

    List<String[]> rows = new ArrayList<String[]>();
    try (BufferedReader buffer = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = buffer.readLine()) != null) {
        rows.add(line.split(","));
      }
    }
    return rows;
  }
}
