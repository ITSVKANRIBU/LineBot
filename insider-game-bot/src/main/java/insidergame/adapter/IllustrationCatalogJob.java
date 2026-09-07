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

package insidergame.adapter;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import insidergame.common.CommonModule;

import lombok.extern.slf4j.Slf4j;

/**
 * 役職イラストのカタログを定期的に取り直す.
 *
 * <p>画像はBotのリリースと無関係に差し替えられるため、外部から定期取得する。
 * 取得に失敗しても前回のカタログで配布を続けるので、この失敗は可用性に響かない。
 */
@Slf4j
@Component
public class IllustrationCatalogJob {

  /** 取り直しの間隔（ミリ秒）。前回の完了からこの時間だけ空けて起動する. */
  private static final long REFRESH_INTERVAL_MILLIS = 300000;

  @Scheduled(fixedDelay = REFRESH_INTERVAL_MILLIS)
  public void refresh() {
    log.info("Refreshing illustration catalog");
    CommonModule.createMap();
  }
}
