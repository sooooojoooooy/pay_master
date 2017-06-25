package com.pay.main.payment.service;

import com.pay.main.payment.entity.UserLogin;

public interface IUserLoginService {
	public UserLogin getByPrimaryKey(String ulMerId);
}