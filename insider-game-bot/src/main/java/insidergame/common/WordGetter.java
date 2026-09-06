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

package insidergame.common;

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
 *
 * <p>1列目がお題、2列目がCSV難易度（1〜5）。難易度の区間はCSVの2列目から導出するため、
 * 行を増減しても境界を手で直す必要はない。ただし<b>CSVは難易度の昇順である必要がある</b>。
 */
@Slf4j
public class WordGetter {

  /** 「初心者」の難易度. 公開rankであり、CSV難易度とは番号体系が異なる. */
  public static final int BEGINNER_RANK = 2;

  static final String RESOURCE_NAME = "word.csv";

  /** CSV難易度の最大値. */
  private static final int MAX_CSV_DIFFICULTY = 5;

  private static final Dictionary DICTIONARY = load();

  /**
   * 指定した難易度のお題を1つ返す.
   *
   * <p>公開rankとCSV難易度は番号体系が異なる。
   * 初心者はCSV難易度1〜3、上級者は3〜4、変態は5、指定なしは1〜4から引く。
   *
   * @param rank 難易度。{@link #BEGINNER_RANK}（初心者）、3（上級者）、4（変態）
   * @return お題。お題CSVを読み込めなかった場合はnull
   */
  public static String getWord(int rank) {
    List<String> words = DICTIONARY.words;
    if (words.isEmpty()) {
      return null;
    }

    Random rand = new Random();
    int line;
    if (BEGINNER_RANK == rank) {
      line = rand.nextInt(lastLineOf(3)) + 1;
    } else if (3 == rank) {
      line = rand.nextInt(lastLineOf(4) - lastLineOf(2)) + lastLineOf(2) + 1;
    } else if (4 == rank) {
      line = rand.nextInt(lastLineOf(5) - lastLineOf(4)) + lastLineOf(4) + 1;
    } else {
      line = rand.nextInt(lastLineOf(4)) + 1;
    }

    return words.get(Math.min(line, words.size()) - 1);
  }

  /**
   * 指定したCSV難易度の最終行番号を返す.
   *
   * @param difficulty CSV難易度（1〜{@value #MAX_CSV_DIFFICULTY}）
   * @return 最終行番号（1始まり）。読み込めていない場合は0
   */
  static int lastLineOf(int difficulty) {
    return DICTIONARY.lastLines[difficulty];
  }

  /** 読み込めたお題の数. */
  static int wordCount() {
    return DICTIONARY.words.size();
  }

  /**
   * CSVの各行の1列目を行順に読み込み、CSV難易度ごとの最終行番号を記録する.
   *
   * <p>難易度が読めない行、昇順を破る行、区間が揃わない辞書は補完せずに捨てる。
   * 中途半端な辞書から引くと、難易度の違うお題を黙って配ることになるため。
   * その場合{@link #getWord(int)}はnullを返す。
   */
  private static Dictionary load() {
    InputStream input = WordGetter.class.getClassLoader().getResourceAsStream(RESOURCE_NAME);
    if (input == null) {
      log.error("Word CSV {} is not on the classpath", RESOURCE_NAME);
      return Dictionary.empty();
    }

    List<String> words = new ArrayList<String>();
    int[] lastLines = new int[MAX_CSV_DIFFICULTY + 1];
    int previousDifficulty = 0;

    try (BufferedReader buffer = new BufferedReader(
        new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while ((line = buffer.readLine()) != null) {
        String[] columns = line.split(",");
        int difficulty = difficultyOf(columns);

        if (difficulty < previousDifficulty || difficulty > MAX_CSV_DIFFICULTY) {
          log.error("Word CSV {} has an unusable difficulty at line {}",
              RESOURCE_NAME, words.size() + 1);
          return Dictionary.empty();
        }

        words.add(columns[0]);
        lastLines[difficulty] = words.size();
        previousDifficulty = difficulty;
      }
    } catch (IOException e) {
      log.error("Failed to read the word CSV", e);
    }

    for (int difficulty = 1; difficulty <= MAX_CSV_DIFFICULTY; difficulty++) {
      if (lastLines[difficulty] <= lastLines[difficulty - 1]) {
        log.error("Word CSV {} has no words for difficulty {}", RESOURCE_NAME, difficulty);
        return Dictionary.empty();
      }
    }

    return new Dictionary(Collections.unmodifiableList(words), lastLines);
  }

  /** 2列目の難易度。整数として読めない場合は-1. */
  private static int difficultyOf(String[] columns) {
    if (columns.length < 2) {
      return -1;
    }
    try {
      return Integer.parseInt(columns[1].trim());
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  /** お題の一覧と、CSV難易度ごとの区間. */
  private static final class Dictionary {
    private final List<String> words;
    /** CSV難易度 → その難易度の最終行番号（1始まり）。添字0は常に0. */
    private final int[] lastLines;

    Dictionary(List<String> words, int[] lastLines) {
      this.words = words;
      this.lastLines = lastLines;
    }

    static Dictionary empty() {
      return new Dictionary(Collections.<String>emptyList(),
          new int[MAX_CSV_DIFFICULTY + 1]);
    }
  }
}
