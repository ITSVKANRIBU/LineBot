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

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;

import insidergame.adapter.IllustrationCatalogJob;

/**
 * デプロイのヘルスチェックが依存する {@code /actuator/health} の契約を固定する.
 *
 * <p>VM 上の更新スクリプトは「HTTP 200 かつ本文が {@code {"status":"UP"}}」で起動完了を
 * 判定する。詳細 (diskSpace 等) が本文に混ざると文字列一致が壊れるため、本文まで固定する。
 *
 * <p>{@link IllustrationCatalogJob} は mock に差し替える。実際に取得すると staticなカタログが
 * 埋まり、既定画像を期待する他のテストが同じ JVM 上で壊れるため。
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "line.bot.channel-token=dummy-channel-token",
    "line.bot.channel-secret=dummy-channel-secret",
})
public class HealthEndpointTest {

  @MockBean
  private IllustrationCatalogJob illustrationCatalogJob;

  @Autowired
  private TestRestTemplate rest;

  @Test
  public void healthReportsUpWithoutDetails() {
    ResponseEntity<String> response = rest.getForEntity("/actuator/health", String.class);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("{\"status\":\"UP\"}", response.getBody());
  }

  /** 公開するのは health だけ。env や beans が HTTP に出ていないことを固定する. */
  @Test
  public void otherActuatorEndpointsAreNotExposed() {
    assertEquals(HttpStatus.NOT_FOUND,
        rest.getForEntity("/actuator/env", String.class).getStatusCode());
    assertEquals(HttpStatus.NOT_FOUND,
        rest.getForEntity("/actuator/beans", String.class).getStatusCode());
  }
}
