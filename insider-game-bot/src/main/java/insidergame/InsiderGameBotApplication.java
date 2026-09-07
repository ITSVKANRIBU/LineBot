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

package insidergame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Botの起動クラス.
 *
 * <p>インサイダーゲームとWerewordsの役職・お題をLINEで配るBot。
 * LINEイベントの受け口は{@code adapter.LineEventHandler}、入力の解釈は
 * {@code game.TextCommandHandler}、イラストカタログの定期取得は
 * {@code adapter.IllustrationCatalogJob}が持つ。ここにあるのは起動の配線だけ。
 *
 * <p>ルートパッケージに置いてあるため、コンポーネントスキャンは
 * {@code insidergame}配下すべてに及ぶ。
 */
@SpringBootApplication
@EnableScheduling
public class InsiderGameBotApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsiderGameBotApplication.class, args);
  }
}
