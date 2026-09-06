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

package insidergame.testing;

import java.util.Random;

/**
 * {@code nextInt}が指定した値を順に返す{@link Random}.
 *
 * <p>配役の抽選もイラストの抽選も、乱数の呼び出し回数と順序そのものが契約になっている。
 * 実装が余分に引いたり順番を入れ替えたりすると配役が変わるため、値を固定して検知する。
 */
public final class FixedRandom extends Random {

  private static final long serialVersionUID = 1L;

  private final int[] values;
  private int index;

  public FixedRandom(int... values) {
    this.values = values.clone();
  }

  @Override
  public int nextInt(int bound) {
    return values[index++];
  }
}
