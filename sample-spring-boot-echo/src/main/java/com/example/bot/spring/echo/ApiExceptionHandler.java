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

package com.example.bot.spring.echo;

import java.util.Collections;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 公開APIの内部エラーを、詳細を含まない一般的な応答へ丸める.
 *
 * <p>Spring既定のエラー応答は例外メッセージとpathを含むため、
 * お題やユーザーIDが外部へ漏れ得る。対象は公開APIの2 controllerに限定し、
 * {@code /callback}の署名検証結果には影響させない。
 */
@RestControllerAdvice(assignableTypes = { MainController.class, SpecialVillageController.class })
public class ApiExceptionHandler {

  /**
   * 想定外の例外をHTTP 500の定型メッセージへ変換する.
   *
   * @param e 発生した例外
   * @return 個人情報を含まないエラー応答
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, String>> handleUnexpected(Exception e) {
    e.printStackTrace();
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Collections.singletonMap("error", "内部エラーが発生しました。"));
  }
}
