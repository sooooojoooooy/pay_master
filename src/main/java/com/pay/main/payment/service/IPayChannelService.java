package com.pay.main.payment.service;

import com.pay.main.payment.entity.PayChannel;

/**
 * 渠道订单接口
 */
public interface IPayChannelService {
	public int insertSelective(PayChannel record);

	// 根据平台唯一订单号查询订单信息
	PayChannel getByPrimaryKey(String tradeNo);
	
	// 根据商户ID和商户订单号退款
	PayChannel getByPrimaryKeyMer(String merId,String merNo);

	int updateByPrimaryKeySelective(PayChannel record);

	int updateByPrimaryKey(PayChannel record);
}