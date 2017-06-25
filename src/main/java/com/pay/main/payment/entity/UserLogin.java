package com.pay.main.payment.entity;

/**
 * 用户信息查询（获取用户签名）
 * 
 * @author Guo
 */
public class UserLogin {
	private Integer id;
	private String ulUsername;
	private String ulPassword;
	private String ulMerId;
	private String ulSign;
	private Integer ulGroup;
	private Integer ulPaystate;
	private String ulNotify;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public String getUlUsername() {
		return ulUsername;
	}

	public void setUlUsername(String ulUsername) {
		this.ulUsername = ulUsername == null ? null : ulUsername.trim();
	}

	public String getUlPassword() {
		return ulPassword;
	}

	public void setUlPassword(String ulPassword) {
		this.ulPassword = ulPassword == null ? null : ulPassword.trim();
	}

	public String getUlMerId() {
		return ulMerId;
	}

	public void setUlMerId(String ulMerId) {
		this.ulMerId = ulMerId;
	}

	public Integer getUlGroup() {
		return ulGroup;
	}

	public void setUlGroup(Integer ulGroup) {
		this.ulGroup = ulGroup;
	}

	public Integer getUlPaystate() {
		return ulPaystate;
	}

	public void setUlPaystate(Integer ulPaystate) {
		this.ulPaystate = ulPaystate;
	}

	public String getUlSign() {
		return ulSign;
	}

	public void setUlSign(String ulSign) {
		this.ulSign = ulSign;
	}

	public String getUlNotify() {
		return ulNotify;
	}

	public void setUlNotify(String ulNotify) {
		this.ulNotify = ulNotify;
	}

}