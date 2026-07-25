package com.example.bot.spring.game;

import java.text.MessageFormat;

public final class CommonSubLogic {
  private static final String[] WEREWORDS_MESSAGE_MAP = {
      "",
      "あなたの役職は占師です。お題は「{0}」です。",
      "あなたの役職はインサイダーです。お題は「{0}」です。",
      "あなたの役職は村人です",
      "あなたの役職はGMです。お題は「{0}」です。\n役職は「{1}」が欠けています。"
  };
  private static final String[] WEREWORDS_ROLE_MAP = { "", "占師", "インサイダー", "あなたの役職は村人です", "村人", "GM" };

  private CommonSubLogic() { }

  public static String getWereMesse(int roleNum, String[] umeji) {
    return new MessageFormat(WEREWORDS_MESSAGE_MAP[roleNum]).format(umeji);
  }

  public static String getWereRole(int roleNum) { return WEREWORDS_ROLE_MAP[roleNum]; }
}
