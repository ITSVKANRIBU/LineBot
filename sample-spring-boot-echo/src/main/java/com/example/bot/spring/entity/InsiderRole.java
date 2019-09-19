package com.example.bot.spring.entity;

public class InsiderRole {

  private String role;
  private String userId;
  private boolean checkFlg;

  public InsiderRole() {
  }

  public InsiderRole(String role, String userId) {
    this.role = role;
    this.userId = userId;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public boolean isCheckFlg() {
    return checkFlg;
  }

  public void setCheckFlg(boolean checkFlg) {
    this.checkFlg = checkFlg;
  }

}
