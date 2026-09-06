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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

/**
 * お題CSVの読み込みと難易度の範囲を固定する.
 *
 * <p>実行ディレクトリからの相対パスで読むと、テストやjar実行では
 * 1つもお題を引けない。ランダム村はお題を自動で決めるため、
 * 読み込めていないことに気付けないまま『null』を配ってしまう。
 */
public class WordGetterTest {

  @Test
  public void beginnerWordsAreDrawnFromTheBeginnerRangeOfTheCsv() throws IOException {
    Set<String> beginnerWords = csvWords(WordGetter.THIRD_LINE);

    for (int attempt = 0; attempt < 200; attempt++) {
      String word = WordGetter.getWord(WordGetter.BEGINNER_RANK);

      assertNotNull("お題CSVをclasspathから読み込めていない", word);
      assertTrue("初心者の範囲外のお題: " + word, beginnerWords.contains(word));
    }
  }

  @Test
  public void everyDifficultyDrawsAWord() {
    // UIから送られる難易度は2（初心者）・3（上級者）・4（変態）
    for (int rank = BEGINNER_RANK; rank <= 4; rank++) {
      assertNotNull("難易度" + rank + "のお題を引けない", WordGetter.getWord(rank));
    }
  }

  /** CSVの先頭から{@code lines}行分の1列目を返す. */
  private Set<String> csvWords(int lines) throws IOException {
    InputStream input = WordGetter.class.getClassLoader()
        .getResourceAsStream(WordGetter.RESOURCE_NAME);
    assertNotNull("お題CSVがclasspathにない", input);

    Set<String> words = new HashSet<String>();
    try (BufferedReader buffer = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      // 重複するお題があるため、集合の要素数ではなく読んだ行数で打ち切る
      for (int read = 0; read < lines && (line = buffer.readLine()) != null; read++) {
        words.add(line.split(",")[0]);
      }
    }
    return words;
  }
}
