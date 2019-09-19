package com.example.bot.spring.entity;

import static com.example.bot.staticdata.Message.*;

import java.io.Serializable;
import java.util.ArrayList;

public class Village implements Serializable {

	private int villageNum;
	private String ownerId;
	private String odai;
	private ArrayList<InsiderRole> roleList;

	private int insiderNum;
	private int villageSize;

	public Village() {
		roleList = new ArrayList<InsiderRole>();
	}

	public int getVillageNum() {
		return villageNum;
	}

	public void setVillageNum(int villageNum) {
		this.villageNum = villageNum;
	}

	public String getOwnerId() {
		return ownerId;
	}

	public void setOwnerId(String ownerId) {
		this.ownerId = ownerId;
	}

	public String getOdai() {
		return odai;
	}

	public void setOdai(String odai) {
		this.odai = odai;
	}

	public ArrayList<InsiderRole> getRoleList() {
		return roleList;
	}

	public void setRoleList(ArrayList<InsiderRole> roleList) {
		this.roleList = roleList;
	}

	public void addRoleList(String role, String userId) {
		roleList.add(new InsiderRole(role, userId));
	}

	public boolean hasOwner(String userId) {
		return ownerId.equals(userId);
	}

	public int getInsiderNum() {
		return insiderNum;
	}

	public void setInsiderNum(int insiderNum) {
		this.insiderNum = insiderNum;
	}

	public int getVillageSize() {
		return villageSize;
	}

	public void setVillageSize(int villageSize) {
		this.villageSize = villageSize;
	}

	// 持ってなかったらnullを返却
	public String getMemberRole(String userId) {
		return 	roleList.stream().filter(dao -> userId.equals(dao.getUserId())).findFirst().orElse(new InsiderRole()).getRole();
	}

	public String getMessageOwner() {

		return villageNum + "村　："  +  roleList.size()  + "/" + villageSize + "人にお題を配りました。お題は『" + odai + "』です。";

	}

	public String getRoleMessage(String userId) {

		InsiderRole role= roleList.stream().filter(dao -> userId.equals(dao.getUserId())).findFirst().orElse(new InsiderRole());

		String message= DEFAILT_MESSAGE;

		if(INSIDER_ROLE.equals(role.getRole())) {
			message = "あなたの役職は" + INSIDER_ROLE + "です。お題は『" + odai + "』です。";
		}else if(VILLAGE_ROLE.equals(role.getRole())) {
			message = "あなたの役職は" + VILLAGE_ROLE + "です。";
		}

		return message;
	}

}
