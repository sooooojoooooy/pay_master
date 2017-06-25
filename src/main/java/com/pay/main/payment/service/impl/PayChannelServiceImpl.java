package com.pay.main.payment.service.impl;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pay.main.payment.dao.PayChannelMapper;
import com.pay.main.payment.entity.PayChannel;
import com.pay.main.payment.service.IPayChannelService;
import com.pay.main.payment.util.DataProcessUtil;

@Service("payChannelService")
public class PayChannelServiceImpl implements IPayChannelService {
	Logger logger = LoggerFactory.getLogger(PayChannelServiceImpl.class);

	@Autowired
	PayChannelMapper payChannelMapper;

	@Override
	public int insertSelective(PayChannel record) {
		int index = 0;
		try {
			Map<String, Object> param = DataProcessUtil.convertBeanToMap(record);
			index = payChannelMapper.insertSelective(param);
		} catch (Exception ex) {
			logger.error("PayChannelServiceImpl-insertSelective:", ex);
		}
		return index;
	}

	@Override
	public int updateByPrimaryKeySelective(PayChannel record) {
		int index = 0;
		try {
			Map<String, Object> param = DataProcessUtil.convertBeanToMap(record);
			index = payChannelMapper.updateByPrimaryKeySelective(param);
		} catch (Exception ex) {
			logger.error("PayChannelServiceImpl-updateByPrimaryKeySelective:", ex);
		}
		return index;
	}

	@Override
	public PayChannel getByPrimaryKey(String tradeNo) {
		PayChannel payChannel = null;
		try {
			payChannel = payChannelMapper.selectByPrimaryKey(tradeNo);
		} catch (Exception ex) {
			logger.error("PayChannelServiceImpl-selectByPrimaryKey is error:", ex);
		}
		return payChannel;
	}
	
	@Override
	public PayChannel getByPrimaryKeyMer(String merId, String merNo) {
		PayChannel payChannel = null;
		try {
			payChannel = payChannelMapper.selectByPrimaryKeyMer(merId, merNo);
		} catch (Exception ex) {
			logger.error("PayChannelServiceImpl-getByPrimaryKeyMer is error:", ex);
		}
		return payChannel;
	}

	@Override
	public int updateByPrimaryKey(PayChannel record) {
		// TODO Auto-generated method stub
		return 0;
	}
}