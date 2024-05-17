package com.example.bot.spring.echo;

import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.bot.common.CommonModule;
import com.example.bot.spring.entity.Village;
import com.example.bot.staticdata.MessageConst;
import com.example.bot.staticdata.VillageList;
import com.linecorp.bot.model.action.MessageAction;
import com.linecorp.bot.model.action.PostbackAction;
import com.linecorp.bot.model.message.Message;
import com.linecorp.bot.model.message.TemplateMessage;
import com.linecorp.bot.model.message.TextMessage;
import com.linecorp.bot.model.message.template.ButtonsTemplate;
import com.linecorp.bot.model.message.template.ButtonsTemplateNonURL;
import com.linecorp.bot.spring.boot.common.SpecialVillageList;
import com.linecorp.bot.spring.boot.entity.SpecialVillage;

@RestController
public class MainController {

	@GetMapping("/callapi")
	@CrossOrigin
	public List<Message> index(String message, String userId) {
		List<Message> messages = messageController(message, userId);
		if (messages == null) {
			messages = Collections.singletonList(new TextMessage("村が作成されていません"));
		}
		return messages;
	}

	private List<Message> messageController(String message, String userId) {
		int number = 0;
		try {
			number = Integer.parseInt(message);

			if (number > 9999) {
				return getMessageSpecialVillage(userId, number);
			} else if (number > 999) {
				return getMessageVillage(userId, number);
			} else {
				// 人数が0のものを探す
				for (int i = VillageList.getVillageList().size() - 1; i >= 0; i--) {
					if (0 == VillageList.get(i).getVillageSize() && userId.equals(VillageList.get(i).getOwnerId())) {
						if (number <= 1) {
							return Collections.singletonList(new TextMessage(MessageConst.ERR_NUMSETMESSAGE));
						}

						// 村人数設定
						VillageList.get(i).setVillageSize(number);

						// インサイダー位置設定
						Random random = new Random();
						int insiderNum = random.nextInt(number) + 1;
						String roleUrl = CommonModule.getIllustUrl("GM");
						VillageList.get(i).setInsiderNum(insiderNum);
						if (VillageList.get(i).getGmNum() == MessageConst.DEFAULT_GMNUM) {
							roleUrl = CommonModule.getIllustUrl("GOD");
							int gmNum = random.nextInt(number) + 1;
							while (gmNum == insiderNum) {
								gmNum = random.nextInt(number) + 1;
							}
							VillageList.get(i).setGmNum(gmNum);
						}
						String villageNumStr = String.valueOf(VillageList.get(i).getVillageNum());
						String messageTmp = "人数を『" + number + "人』に設定しました。" + "\n皆さんに村番号を伝えてください。";

						ButtonsTemplate buttons = new ButtonsTemplate(roleUrl, villageNumStr + "村", messageTmp,
								Collections.singletonList(new MessageAction("確認", villageNumStr)));

						return Collections
								.singletonList(new TemplateMessage(message + "配布状況の確認は村番号を入力してください。", buttons));
					}
				}
			}

		} catch (NumberFormatException e) {
			// メッセージ
			return nonNumberMessage(message, userId);
		} catch (Throwable e) {
			return null;
		}

		return null;
	}

	private List<Message> getMessageVillage(String userId, int number) {
		List<Message> messages = null;

		Village village = VillageList.getVillage(number);

		if (village == null) {
			return null;
		}

		if (userId.equals(village.getOwnerId())) {
			// オーナーの場合
			messages = village.getMessageOwner();

		} else {

			// 参加者の場合
			String memberRole = village.getMemberRole(userId);
			if (memberRole == null) {

				if (village.getRoleList().size() >= village.getVillageSize()) {
					messages = Collections.singletonList(new TextMessage("村がいっぱいです。"));
				} else {
					// 配役の設定
					village.addRoleList(null, userId);
					village.setInsiderRole(userId);

					messages = village.getRoleMessage(userId);

				}
			} else {
				messages = village.getRoleMessage(userId);
			}

		}
		return messages;
	}

	private List<Message> getMessageSpecialVillage(String userId, int number) {

		List<Message> messages = null;

		SpecialVillage village = SpecialVillageList.getVillage(number);

		if (village == null) {
			return null;
		}

		if (village != null) {
			// 参加者フラグ
			boolean sankaFlg = village.hasMember(userId);

			// 参加している場合
			if (sankaFlg) {
				messages = village.getRoleMessage(userId);

			} else { //参加者の場合

				if (village.getUserList().size() >= village.getMessageList().size()) {
					messages = Collections.singletonList(new TextMessage("村がいっぱいです。"));
				} else {
					// 配役の設定
					village.getUserList().add(userId);
					messages = village.getRoleMessage(userId);
				}
			}
		}

		return messages;

	}

	private List<Message> nonNumberMessage(String message, String userId) {

		if ("お題".equals(message.trim()) || "題".equals(message.trim()) || "神".equals(message.trim())) {
			Random random = new Random();
			int villageNum = random.nextInt(8999) + 1000;

			// 重複しない番号取得（防止のため、100回まで）
			for (int i = 0; i < 100; i++) {
				boolean breakFlg = true;
				for (Village dao : VillageList.getVillageList()) {
					if (villageNum == dao.getVillageNum()) {
						villageNum = random.nextInt(8999) + 1000;
						breakFlg = false;
						break;
					}
				}
				if (breakFlg) {
					break;
				}
			}

			Village newVillage = new Village();
			newVillage.setOwnerId(userId);
			newVillage.setVillageNum(villageNum);

			if ("神".equals(message.trim())) {
				newVillage.setGmNum(MessageConst.DEFAULT_GMNUM);
			}

			VillageList.addVillage(newVillage);

			String messagetmp = villageNum + "村 を新しく作成しました。" + MessageConst.OWNER_ODAIMESSAGE;

			ButtonsTemplateNonURL buttons = new ButtonsTemplateNonURL(messagetmp + "\nお題の自動取得もできます。",
					Collections.singletonList(new PostbackAction("お題の自動取得", String.valueOf(0))));

			return Collections.singletonList(new TemplateMessage(messagetmp, buttons));

		}

		// お題設定の場合
		for (int i = VillageList.getVillageList().size() - 1; i >= 0; i--) {
			if (null == VillageList.get(i).getOdai() && userId.equals(VillageList.get(i).getOwnerId())) {
				VillageList.get(i).setOdai(message);
				String messageStr = VillageList.get(i).getVillageNum() + "村 のお題を『" + message + "』に設定しました。\n";
				if (VillageList.get(i).getGmNum() == MessageConst.DEFAULT_GMNUM) {
					messageStr += MessageConst.GOD_NUMSETMESSAGE;
				} else {
					messageStr += MessageConst.OWNER_NUMSETMESSAGE;
				}
				return Collections.singletonList(new TextMessage(messageStr));
			}
		}

		return null;
	}

}
