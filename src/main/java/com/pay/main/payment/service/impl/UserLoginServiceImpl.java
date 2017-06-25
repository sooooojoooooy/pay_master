package com.pay.main.payment.service.impl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pay.main.payment.dao.UserLoginMapper;
import com.pay.main.payment.entity.UserLogin;
import com.pay.main.payment.service.IUserLoginService;

@Service("userLoginService")
public class UserLoginServiceImpl implements IUserLoginService {
	private static Log logger = LogFactory.getLog(UserLoginServiceImpl.class);

	@Autowired
	UserLoginMapper userLoginMapper;

	@Override
	public UserLogin getByPrimaryKey(String ulMerId) {
		UserLogin selectByPrimaryKey = null;
		try {
			selectByPrimaryKey = userLoginMapper.selectByPrimaryKey(ulMerId);
		} catch (Exception ex) {
			logger.error("查询用户状态失败：", ex);
		}
		return selectByPrimaryKey;
	}
}