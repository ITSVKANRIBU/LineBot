package com.example.bot.staticdata;

public interface Message {

	String DEFAILT_MESSAGE = "お題を配りたい方は「お題」を、お題及び役職を確認したい場合は村番号（数字4桁）を入力してください。";

	String OWNER_ODAIMESSAGE = "お題を入力してください。";
	String OWNER_NUMSETMESSAGE = "お題を配りたい人数を入力してください。"
			+ "\n　例：「5」の場合は、「インサイダー１人、村4人」です。";
	String OWNER_CONFMESSAGE = "配布状況を確認したい場合は村番号を入力してください。";
	String INSIDER_ROLE ="インサイダー";
	String VILLAGE_ROLE = "村人";

}
