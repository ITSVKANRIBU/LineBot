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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.bot.common.CommonModule;

import com.linecorp.bot.model.action.Action;
import com.linecorp.bot.model.action.URIActionNonAltUri;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonTitle;

public class EchoImageEvent {

  public List<Message> echo() {

    String message = "ご利用ありがとうございます。"
        + "要望・報告は以下にご連絡ください。";

    List<Action> actionList = new ArrayList<Action>();
    actionList.add(new URIActionNonAltUri("ご意見",
        "https://docs.google.com/forms/d/e/1FAIpQLSf5pH-nC86Lb9L18dx9fBJv1ZUu-qdftS_PBkBRA5imjjFVgA/viewform"));

    String word = "インサイダーゲームツール";
    actionList.add(new URIActionNonAltUri("ググる",
        "https://www.google.com/search?q=" + word));

    ButtonsTemplateNonTitle buttons = new ButtonsTemplateNonTitle(
        CommonModule.getIllustUrl("INSIDER"),
        message, actionList);

    String titleMessage = "製作者の「白いフランです。」\n"
        + message + "\n Twitter:  https://twitter.com/2d7rqU5gFQ6VpGo";

    return Collections.singletonList(new TemplateMessage(titleMessage, buttons));

  }
}
