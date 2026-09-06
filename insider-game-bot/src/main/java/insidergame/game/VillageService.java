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

package insidergame.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.stereotype.Service;

import insidergame.common.CommonModule;
import insidergame.common.WordGetter;
import insidergame.message.MessageConst;

import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;

/**
 * LINE webhookと{@code /callapi}で共通のゲーム操作.
 *
 * <p>対象の村が見つからない場合はいずれのメソッドも{@code null}を返す。
 * 呼び出し側は{@code null}を「村が作成されていません」相当の応答へ変換する。
 */
@Service
public class VillageService {

  /** Werewordsへ変換できる最小人数. 占師・インサイダー・村人で3人必要. */
  private static final int MIN_WEREWORDS_VILLAGE_SIZE = 3;

  private final VillageRegistry villages;
  private final SpecialVillageRegistry specialVillages;
  private final CreateWereWordsLogic createWereWords;

  /**
   * @param villages 通常村のレジストリ
   * @param specialVillages 特殊村のレジストリ
   * @param createWereWords Werewords村の作成
   */
  public VillageService(VillageRegistry villages, SpecialVillageRegistry specialVillages,
      CreateWereWordsLogic createWereWords) {
    this.villages = villages;
    this.specialVillages = specialVillages;
    this.createWereWords = createWereWords;
  }

  /**
   * 通常村を作成する.
   *
   * @param userId オーナーのユーザーID
   * @param godMode GM（神）モードで作成する場合true
   * @return 作成完了メッセージ
   */
  public List<Message> createVillage(String userId, boolean godMode) {
    Village village = new Village();
    village.setOwnerId(userId);

    if (godMode) {
      village.markGodMode();
    }

    int villageNum = villages.addVillage(village, new Random());

    String message = villageNum + "村 を新しく作成しました。" + MessageConst.OWNER_ODAIMESSAGE;

    ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(
        message + "\nお題の自動取得もできます。", Collections.singletonList(
            new PostbackAction("お題の自動取得", String.valueOf(0))));

    return Collections.singletonList(new TemplateMessage(message, buttons));
  }

  /**
   * ランダム村を作成する.
   *
   * <p>お題は「初心者」から自動で引く。役職はオーナーを含めて抽選するため、
   * オーナーへ聞くのは人数だけになる。
   *
   * @param userId オーナーのユーザーID
   * @return 作成完了メッセージ
   */
  public List<Message> createRandomVillage(String userId) {
    Village village = new Village();
    village.setOwnerId(userId);
    // GMも抽選対象にする
    village.markGodMode();
    village.setRandomMode(true);
    village.applyOdai(WordGetter.getWord(WordGetter.BEGINNER_RANK));

    int villageNum = villages.addVillage(village, new Random());

    return Collections.singletonList(new TextMessage(
        villageNum + "村 を新しく作成しました。" + MessageConst.RANDOM_NUMSETMESSAGE));
  }

  /**
   * 参加人数を設定し、インサイダーとGMの位置を抽選する.
   *
   * @param userId オーナーのユーザーID
   * @param number 参加人数
   * @return 設定完了メッセージ。人数未設定の自分の村がない場合はnull
   */
  public List<Message> setVillageSize(String userId, int number) {
    return setVillageSize(userId, number, new Random());
  }

  /** 乱数を差し替えられる{@link #setVillageSize(String, int)}。テスト用のseam. */
  List<Message> setVillageSize(String userId, int number, Random random) {
    Village village = villages.findLatestOwned(userId, target -> 0 == target.getVillageSize());

    if (village == null) {
      return null;
    }

    if (number <= 1) {
      return Collections.singletonList(new TextMessage(MessageConst.ERR_NUMSETMESSAGE));
    }

    // 神モードかどうかは人数確定で上書きされるため、先に控える
    boolean godMode = village.isGodModeAwaitingSize();
    boolean randomMode = village.isRandomMode();

    // 人数確定と配役抽選は村側で原子的に行う
    if (!village.configure(number, random)) {
      // 同時操作で既に確定済み。既定応答へ落とす
      return null;
    }

    if (randomMode) {
      // オーナーも参加者なので、村番号の案内と一緒に自分の役職を返す
      List<Message> messages = new ArrayList<Message>();
      messages.add(new TextMessage(village.getVillageNum() + "村：人数を『" + number
          + "人』に設定しました。\n皆さんに村番号を伝えてください。"));
      messages.addAll(village.getRoleMessage(userId));
      return messages;
    }

    String roleUrl = CommonModule.getIllustUrl(godMode ? "GOD" : "GM");
    String villageNumStr = String.valueOf(village.getVillageNum());
    String message = "人数を『" + number
        + "人』に設定しました。"
        + "\n皆さんに村番号を伝えてください。";

    ButtonsTemplate buttons = new ButtonsTemplate(
        roleUrl,
        villageNumStr + "村", message, Collections.singletonList(
            new MessageAction("確認", villageNumStr)));

    return Collections.singletonList(
        new TemplateMessage(message + "配布状況の確認は村番号を入力してください。", buttons));
  }

  /**
   * お題を設定する.
   *
   * @param userId オーナーのユーザーID
   * @param odai お題
   * @return 設定完了メッセージ。お題未設定の自分の村がない場合はnull
   */
  public List<Message> setOdai(String userId, String odai) {
    Village village = villages.findLatestOwned(userId, target -> null == target.getOdai());

    if (village == null) {
      return null;
    }

    if (!village.applyOdai(odai)) {
      // 同時操作で既に設定済み。既定応答へ落とす
      return null;
    }

    String message = village.getVillageNum() + "村 のお題を『" + odai + "』に設定しました。\n";
    if (village.isGodModeAwaitingSize()) {
      message = message + MessageConst.GOD_NUMSETMESSAGE;
    } else {
      message = message + MessageConst.OWNER_NUMSETMESSAGE;
    }

    return Collections.singletonList(new TextMessage(message));
  }

  /**
   * 自分の村を逆村へ切り替える.
   *
   * @param userId オーナーのユーザーID
   * @return 設定完了メッセージ。参加者のいない自分の村がない場合はnull
   */
  public List<Message> setReverseVillage(String userId) {
    // ランダム村はGMとインサイダーを1人ずつ配るため、逆村へは切り替えない
    Village village = villages.findLatestOwned(
        userId, target -> !target.hasMembers() && !target.isRandomMode());

    if (village == null) {
      return null;
    }

    // 参加者の有無の確認と切り替えは村側で原子的に行う
    if (!village.applyReverseVillage()) {
      // 同時操作で参加者が入室済み。既定応答へ落とす
      return null;
    }

    String message = village.getVillageNum() + "村 を『逆村』に設定しました。\n"
        + "お題を知らない村人が1人となります。";

    return Collections.singletonList(new TextMessage(message));
  }

  /**
   * 自分の村をWerewords村へ変換する.
   *
   * <p>元の通常村は残る。変換で作るのは配布メッセージだけを持つ新しい特殊村で、
   * 配布順は登録時にシャッフルされる。
   *
   * @param userId オーナーのユーザーID
   * @return 案内メッセージ。変換できる自分の村がない場合はnull
   */
  public List<Message> convertToWerewords(String userId) {
    Village village = villages.findLatestOwned(userId, target -> !target.hasMembers());

    // 人数とお題が揃っていない村は変換できない。より古い村へは遡らない
    if (village == null
        || village.getVillageSize() < MIN_WEREWORDS_VILLAGE_SIZE
        || village.getOdai() == null) {
      return null;
    }

    String odai = village.getOdai();
    // GMがいる村ではGMが役掛けで入室しないため、参加人数ぶんだけ配る
    boolean godMode = village.hasGameMaster();

    int villageNum =
        createWereWords.createWereWords(godMode, village.getVillageSize(), odai);

    String message = "お題を『" + odai + "』として新たにワーワーズの『" + villageNum + "』村を作成しました。";
    if (godMode) {
      message = message + "参加者へ『" + villageNum + "』を伝えてください。";
    } else {
      message = message + "参加者へ『" + villageNum + "』を伝え、あなたも入室してください。\n"
          + "\n■注意\n"
          + "あなたはGMです。入室時に表示された役職が欠けた役職となります。";
    }

    return Collections.singletonList(new TextMessage(message));
  }

  /**
   * 通常村へ参加する。オーナーの場合は配布状況を返す.
   *
   * <p>ただしランダム村のオーナーは参加者でもあるため、配布状況ではなく役職を返す。
   *
   * @param userId ユーザーID
   * @param villageNum 村番号
   * @return 役職メッセージまたは配布状況。村がない場合はnull
   */
  public List<Message> joinVillage(String userId, int villageNum) {
    Village village = villages.getVillage(villageNum);

    if (village == null) {
      return null;
    }

    if (userId.equals(village.getOwnerId())) {
      // オーナーの場合。ただしランダム村のオーナーは参加者なので、
      // お題を含む配布状況ではなく自分の役職を返す
      if (!village.isRandomMode()) {
        return village.getMessageOwner();
      }
      if (village.getMemberRole(userId) == null) {
        // 人数未設定のため、まだ配役されていない
        return Collections.singletonList(
            new TextMessage(MessageConst.RANDOM_NUMSETMESSAGE));
      }
    } else if (village.getMemberRole(userId) == null && village.join(userId) == null) {
      // 参加者の場合。既に参加済みならjoinは呼ばずに役職を再表示する
      return Collections.singletonList(new TextMessage("村がいっぱいです。"));
    }

    return village.getRoleMessage(userId);
  }

  /**
   * 通常村の入室状況を返す.
   *
   * @param userId ユーザーID
   * @param villageNum 村番号
   * @return 入室状況。村がない場合はnull
   */
  public List<Message> villageStatus(String userId, int villageNum) {
    Village village = villages.getVillage(villageNum);
    return village == null ? null : village.getStatusMessage(userId);
  }

  /**
   * 特殊村の入室状況を返す.
   *
   * @param userId ユーザーID
   * @param villageNum 村番号
   * @return 入室状況。村がない場合はnull
   */
  public List<Message> specialVillageStatus(String userId, int villageNum) {
    SpecialVillage village = specialVillages.getVillage(villageNum);
    return village == null ? null : village.getStatusMessage(userId);
  }

  /**
   * 特殊村へ参加する.
   *
   * @param userId ユーザーID
   * @param villageNum 村番号
   * @return 役職メッセージ。村がない場合はnull
   */
  public List<Message> joinSpecialVillage(String userId, int villageNum) {
    SpecialVillage village = specialVillages.getVillage(villageNum);

    if (village == null) {
      return null;
    }

    // joinは参加済みユーザーに対して冪等
    if (!village.join(userId)) {
      return Collections.singletonList(new TextMessage("村がいっぱいです。"));
    }

    return village.getRoleMessage(userId);
  }
}
