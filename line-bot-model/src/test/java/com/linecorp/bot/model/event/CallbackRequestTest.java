/*
 * Copyright 2018 LINE Corporation
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

package com.linecorp.bot.model.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.stream.Collectors;

import org.junit.Test;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.linecorp.bot.model.event.link.LinkContent;
import com.linecorp.bot.model.event.message.ContentProvider;
import com.linecorp.bot.model.event.message.FileMessageContent;
import com.linecorp.bot.model.event.message.ImageMessageContent;
import com.linecorp.bot.model.event.message.LocationMessageContent;
import com.linecorp.bot.model.event.message.MessageContent;
import com.linecorp.bot.model.event.message.StickerMessageContent;
import com.linecorp.bot.model.event.message.TextMessageContent;
import com.linecorp.bot.model.event.message.UnknownMessageContent;
import com.linecorp.bot.model.event.source.GroupSource;
import com.linecorp.bot.model.event.source.Source;
import com.linecorp.bot.model.event.source.UnknownSource;
import com.linecorp.bot.model.event.source.UserSource;
import com.linecorp.bot.model.event.things.ThingsContent;
import com.linecorp.bot.model.testutil.TestUtil;

public class CallbackRequestTest {
    interface RequestTester {
        void call(CallbackRequest request) throws IOException;
    }

    private void parse(String resourceName, RequestTester callback) throws IOException {
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            String json = StreamUtils.copyToString(resource, StandardCharsets.UTF_8);
            ObjectMapper objectMapper = TestUtil.objectMapperWithProductionConfiguration(false);
            CallbackRequest callbackRequest = objectMapper.readValue(json, CallbackRequest.class);

            callback.call(callbackRequest);
        }
    }

    private static void assertDestination(CallbackRequest request) {
        assertThat(request.getDestination()).isEqualTo("Uab012345678901234567890123456789");
    }

}