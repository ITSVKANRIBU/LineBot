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

package com.example.bot.testing;

import com.example.bot.spring.game.CreateVillage;
import com.example.bot.spring.game.CreateWereWordsLogic;
import com.example.bot.spring.game.SpecialVillageRegistry;
import com.example.bot.spring.game.TextCommandHandler;
import com.example.bot.spring.game.VillageRegistry;
import com.example.bot.spring.game.VillageService;

/**
 * 本番と同じ依存関係でゲーム層を組み立てる、テストごとに独立した一式.
 *
 * <p>レジストリはインスタンスなので、{@code new GameFixture()}するだけでテストが隔離される。
 * 以前はstaticなレジストリを{@code clear()}して共有していたため、テストの逐次実行が前提だった。
 */
public final class GameFixture {

  public final VillageRegistry villages = new VillageRegistry();
  public final SpecialVillageRegistry specialVillages = new SpecialVillageRegistry();
  public final CreateVillage createVillage = new CreateVillage(specialVillages);
  public final CreateWereWordsLogic createWereWords = new CreateWereWordsLogic(createVillage);
  public final VillageService villageService =
      new VillageService(villages, specialVillages, createWereWords);
  public final TextCommandHandler textCommandHandler = new TextCommandHandler(villageService);
}
