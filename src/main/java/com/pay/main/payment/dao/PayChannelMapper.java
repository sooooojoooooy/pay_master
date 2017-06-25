package com.pay.main.payment.dao;

import java.util.Map;

import com.pay.main.payment.entity.PayChannel;

public interface PayChannelMapper {
	public int insertSelective(Map<String, Object> param);
	
	public int updateByPrimaryKeySelective(Map<String, Object> param);

	// 根据平台唯一订单号查询订单信息
	public PayChannel selectByPrimaryKey(String tradeNo);
	
	// 根据商户ID和商户订单号退款
	public PayChannel selectByPrimaryKeyMer(String tradeNo, String merNo);

	public int updateByPrimaryKey(PayChannel record);
}