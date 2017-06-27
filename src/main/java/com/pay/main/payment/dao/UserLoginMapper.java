package com.pay.main.payment.dao;

import com.pay.main.payment.entity.UserLogin;
import org.apache.ibatis.annotations.Param;

public interface UserLoginMapper {
	
	UserLogin selectByUserPwd(@Param("username") String username, @Param("password") String password);

	UserLogin selectByPrimaryKey(@Param("merId") String ulMerId);
	
}