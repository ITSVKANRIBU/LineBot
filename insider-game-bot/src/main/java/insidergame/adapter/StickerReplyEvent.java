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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import insidergame.common.CommonModule;

import com.linecorp.bot.model.action.Action;
import com.linecorp.bot.model.action.URIActionNonAltUri;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonTitle;

/**
 * スタンプを受け取ったときの応答.
 *
 * <p>ゲームの操作ではないため、製作者への連絡先とホームページを案内するだけ。
 */
public class StickerReplyEvent {

  private static final String HOMEPAGE_URL = "https://insidergametool.netlify.app";

  private static final String FEEDBACK_FORM_URL = "https://docs.google.com/forms/d/e/"
      + "1FAIpQLSf5pH-nC86Lb9L18dx9fBJv1ZUu-qdftS_PBkBRA5imjjFVgA/viewform";

  /**
   * 製作者への連絡先とホームページを案内するメッセージを組み立てる.
   *
   * @return 「ご意見」「ホームぺージ」のボタンを持つテンプレート1通
   */
  public List<Message> messages() {
    String message = "ご利用ありがとうございます。" + "要望・報告は以下にご連絡ください。";

    List<Action> actionList = new ArrayList<Action>();
    actionList.add(new URIActionNonAltUri("ご意見", FEEDBACK_FORM_URL));
    actionList.add(new URIActionNonAltUri("ホームぺージ", HOMEPAGE_URL));

    ButtonsTemplateNonTitle buttons = new ButtonsTemplateNonTitle(
        CommonModule.getIllustUrl("INSIDER"), message, actionList);

    String titleMessage = "製作者の「白いフランです。」\n" + message + "\n Hp:  " + HOMEPAGE_URL;

    return Collections.<Message>singletonList(new TemplateMessage(titleMessage, buttons));
  }
}
