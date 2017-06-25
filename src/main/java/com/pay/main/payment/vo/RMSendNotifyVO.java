package com.pay.main.payment.vo;

/**
 * 通知商户信息接口
 * 
 * @author Guo
 */
public class RMSendNotifyVO {
	private String merNo; // 商户订单号
	private String title; // 订单名
	private String attach; // 用户参数
	private String prince; // 订单金额
	private String state; // 支付状态
	private String type; // 支付类型
	private String appId; // 产品ID
	private String sign;

	public RMSendNotifyVO(String merNo, String title, String attach,
			String prince, String state, String type, String appId, String sign) {
		super();
		this.merNo = merNo;
		this.title = title;
		this.attach = attach;
		this.prince = prince;
		this.state = state;
		this.type = type;
		this.appId = appId;
		this.sign = sign;
	}

	public String getMerNo() {
		return merNo;
	}

	public void setMerNo(String merNo) {
		this.merNo = merNo;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAttach() {
		return attach;
	}

	public void setAttach(String attach) {
		this.attach = attach;
	}

	public String getPrince() {
		return prince;
	}

	public void setPrince(String prince) {
		this.prince = prince;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getAppId() {
		return appId;
	}

	public void setAppId(String appId) {
		this.appId = appId;
	}

	public String getSign() {
		return sign;
	}

	public void setSign(String sign) {
		this.sign = sign;
	}

	@Override
	public String toString() {
		return "RMSendNotifyVO [merNo=" + merNo + ", title=" + title
				+ ", attach=" + attach + ", prince=" + prince + ", state="
				+ state + ", type=" + type + ", appId=" + appId + ", sign="
				+ sign + "]";
	}
}