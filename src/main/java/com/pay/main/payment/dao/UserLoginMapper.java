package com.pay.main.payment.dao;

import com.pay.main.payment.entity.UserLogin;

public interface UserLoginMapper {
	
	UserLogin selectByPrimaryKey(String ulMerId);
	
}