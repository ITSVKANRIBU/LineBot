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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import insidergame.message.MessageConst;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.extern.slf4j.Slf4j;

/**
 * 役職に添えるイラストのカタログを外部から取得し、重み付きで抽選する.
 *
 * <p>画像はBotのリリースと無関係に差し替えられるため、一覧を外部化している。
 * カタログは取得のたびに不変mapへ差し替える。取得に失敗した場合は前回の
 * カタログをそのまま使い、対象の役職が無ければ{@link MessageConst}の既定画像へ落とす。
 */
@Slf4j
public class CommonModule {

  /**
   * 接続タイムアウト（ミリ秒）.
   *
   * <p>Apps Scriptが無応答のとき、定期取得のスレッドが永久に固まるのを防ぐ。
   */
  static final int CONNECT_TIMEOUT_MILLIS = 15000;

  /**
   * 読み取りタイムアウト（ミリ秒）.
   *
   * <p>1回の読み取り待ちに対する上限で、通信全体の上限ではない。
   */
  static final int READ_TIMEOUT_MILLIS = 30000;

  private static final RestTemplate restTemplate = timeoutBoundRestTemplate();

  /**
   * イラスト一覧を返すGoogle Apps ScriptのウェブアプリURL.
   *
   * <p>必ずデプロイURL（{@code /macros/s/<デプロイID>/exec}）を指定する。
   * ブラウザでこのURLを開くと{@code script.googleusercontent.com/macros/echo}へ
   * 302で転送されるが、転送先の{@code user_content_key}は一時的な発行物で
   * いずれ失効し、以降は400を返し続ける。転送先URLを貼ってはならない。
   * GETのリダイレクト追跡は{@link RestTemplate}の既定動作で行われる。
   */
  static final String URL =
      "https://script.google.com/macros/s/"
      + "AKfycbyy5RZiz_11ylnVV4NB4kToZv6Qv9ecXkxRgnWo9yvE_AxNLvM/exec";

  /** ファイル名の構成。{@code <役職名>_<重み>_<任意の文字列>}の3部. */
  private static final int FILE_NAME_PART_COUNT = 3;

  /**
   * 役職名 → 重み付きURL.
   *
   * <p>読み手はスケジューラと複数のwebhookスレッド。更新は常に丸ごとの差し替えで、
   * 参照は不変mapのため、参照の可視性だけをvolatileで保証すればよい。
   */
  private static volatile Map<String, List<WeightedUrl>> catalog = Collections.emptyMap();

  /**
   * 役職に添えるイラストのURLを1つ返す.
   *
   * @param roleName {@code INSIDER} / {@code VILLAGERS} / {@code GM} / {@code GOD}
   * @return イラストのURL。カタログに候補がなければ既定画像
   */
  public static String getIllustUrl(String roleName) {
    return getIllustUrl(roleName, catalog, new Random());
  }

  /** 乱数とカタログを差し替えられる{@link #getIllustUrl(String)}。テスト用のseam. */
  static String getIllustUrl(String roleName,
      Map<String, List<WeightedUrl>> from, Random random) {
    List<WeightedUrl> candidates = from.get(roleName);
    if (candidates == null || candidates.isEmpty()) {
      return defaultIllustUrl(roleName);
    }

    int totalWeight = candidates.get(candidates.size() - 1).cumulativeWeight;
    if (totalWeight <= 0) {
      // 重みが正のファイルが1つもない役職。抽選できないので既定画像へ落とす
      return defaultIllustUrl(roleName);
    }

    int draw = random.nextInt(totalWeight);
    for (WeightedUrl candidate : candidates) {
      if (draw < candidate.cumulativeWeight) {
        return candidate.url;
      }
    }
    return defaultIllustUrl(roleName);
  }

  private static String defaultIllustUrl(String roleName) {
    switch (roleName) {
    case "INSIDER":
      return MessageConst.INSIDER_URL;

    case "GM":
      return MessageConst.GM_URL;

    case "VILLAGERS":
      return MessageConst.VILLAGERS_URL;

    case "GOD":
      return MessageConst.GOD_URL;

    }
    return null;
  }

  private static RestTemplate timeoutBoundRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
    factory.setReadTimeout(READ_TIMEOUT_MILLIS);
    return new RestTemplate(factory);
  }

  /**
   * カタログを取り直して差し替える。失敗した場合は前回のカタログを使い続ける.
   *
   * <p>タイムアウトした場合もWARNを残して前回のカタログを維持し、次回の
   * 再取得に進む。{@code @Scheduled(fixedDelay)}は前回の完了から数えるため、
   * 固まった取得が次の取得と重なることはない。
   */
  public static void createMap() {
    try {
      CatalogResponse body = restTemplate.getForEntity(URL, CatalogResponse.class).getBody();

      if (body == null || body.getFiles() == null) {
        log.warn("The illustration catalog response has no files");
        return;
      }

      catalog = parseCatalog(body.getFiles());

    } catch (Exception e) {
      // 取得できなければ前回のカタログ、無ければMessageConstの標準画像へfallbackする
      log.warn("Failed to refresh the illustration catalog", e);
    }
  }

  /**
   * カタログ応答の{@code files}を、役職ごとの重み付きURL一覧へ展開する.
   *
   * <p>HTTPから切り離してあるため、解析だけを単体テストできる。
   * 重みは抽選枠の数を表し、その役職の総重みに対する比率で選ばれる。
   * 重みが0以下のファイルは枠を持たないため選ばれない。
   *
   * <p>規約外の要素は<b>その要素だけ</b>読み飛ばし、残りは取り込む。カタログは
   * Botのリリースと無関係に差し替えられるため、1件の書き間違いで全役職の
   * イラストが既定画像へ戻ってしまうのは割に合わない。
   *
   * @param files カタログの要素
   * @return 役職名 → 重み付きURLの不変map
   */
  static Map<String, List<WeightedUrl>> parseCatalog(List<CatalogFile> files) {
    Map<String, List<WeightedUrl>> parsed = new HashMap<String, List<WeightedUrl>>();
    List<String> ignored = new ArrayList<String>();

    for (CatalogFile file : files) {
      String[] fileNameArray = nameParts(file);
      if (fileNameArray == null || file.getUrl() == null) {
        ignored.add(String.valueOf(file.getName()));
        continue;
      }

      String roleName = fileNameArray[0];
      int weight = Integer.parseInt(fileNameArray[1]);

      List<WeightedUrl> candidates = parsed.get(roleName);
      if (candidates == null) {
        candidates = new ArrayList<WeightedUrl>();
        parsed.put(roleName, candidates);
      }
      // 重みが0以下のファイルは枠を持たない。累積は直前の要素から引き継ぐ
      candidates.add(new WeightedUrl(
          file.getUrl(), totalWeightOf(candidates) + Math.max(weight, 0)));
    }

    if (!ignored.isEmpty()) {
      // ファイル名は画像の名前で、userIdやお題は含まれないためログに出せる
      log.warn("Ignored {} illustration catalog entries: {}", ignored.size(), ignored);
    }

    for (Map.Entry<String, List<WeightedUrl>> entry : parsed.entrySet()) {
      entry.setValue(Collections.unmodifiableList(entry.getValue()));
    }
    return Collections.unmodifiableMap(parsed);
  }

  /**
   * {@code <役職名>_<重み>_<任意の文字列>}に分解する.
   *
   * @return 3つの要素。名前がないか、3部構成でないか、重みが整数でない場合はnull
   */
  private static String[] nameParts(CatalogFile file) {
    if (file.getName() == null) {
      return null;
    }

    String[] parts = file.getName().split("_");
    if (parts.length != FILE_NAME_PART_COUNT) {
      return null;
    }

    try {
      Integer.parseInt(parts[1]);
    } catch (NumberFormatException e) {
      return null;
    }
    return parts;
  }

  private static int totalWeightOf(List<WeightedUrl> candidates) {
    return candidates.isEmpty() ? 0 : candidates.get(candidates.size() - 1).cumulativeWeight;
  }

  /** 抽選枠を累積で持つイラストURL. */
  static final class WeightedUrl {
    private final String url;
    /** この要素までの重みの合計。抽選値がこの値未満ならこのURLが選ばれる. */
    private final int cumulativeWeight;

    WeightedUrl(String url, int cumulativeWeight) {
      this.url = url;
      this.cumulativeWeight = cumulativeWeight;
    }
  }

  /** カタログ応答の本体. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  static final class CatalogResponse {
    private List<CatalogFile> files;

    public List<CatalogFile> getFiles() {
      return files;
    }

    public void setFiles(List<CatalogFile> files) {
      this.files = files;
    }
  }

  /** カタログの1要素. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  static final class CatalogFile {
    private String name;
    private String url;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }
  }
}
