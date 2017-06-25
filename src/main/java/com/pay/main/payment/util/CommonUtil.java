package com.pay.main.payment.util;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommonUtil {
	private static Logger logger = LoggerFactory.getLogger(CommonUtil.class);



	/**
	 * 获取用户IP（Linux系统有问题）
	 */
	public static String getIp(HttpServletRequest request) {
		String ip = null;
		try {
			ip = request.getHeader("X-Forwarded-For");
			if (null != ip && !"".equals(ip) && !"unKnown".equalsIgnoreCase(ip)) {
				// 多次反向代理后会有多个ip值，第一个ip才是真实ip
				int index = ip.indexOf(",");
				if (index != -1) {
					return ip.substring(0, index);
				} else {
					return ip;
				}
			}
			ip = request.getHeader("X-Real-IP");
			if (null != ip && !"".equals(ip) && !"unKnown".equalsIgnoreCase(ip)) {
				return ip;
			}
			return request.getRemoteAddr();
		} catch (Exception ex) {
			logger.error("获取用户IP：", ex);
		}
		return null;
	}

	/**
	 * 根据下单规则传值，返回订单号
	 */
	public static String getOrderNo(String no) {
		String name = null;
		if (no.length() == 2) {
			String temp = null;
			char f = no.charAt(0);
			char t = no.charAt(1);
			if ('A' == f) { // 支付宝
				temp = "AL";
			} else if ('W' == f) { // 微信
				temp = "WX";
			} else if ('Y' == f) { // 易联
				temp = "YL";
			} else if ('Q' == f) { // QQ
				temp = "QQ";
			}
			name = payTypeName(temp, t);
		}
		return name;
	}

	/**
	 * 根据下单规则，拼接订单号
	 */
	private static String payTypeName(String temp, char t) {
		String typeName = null;
		if ('A' == t) { // APP
			typeName = DataFormatUtil.getONLYID32(temp + "APP");
		} else if ('S' == t) { // SCAN 扫码
			typeName = DataFormatUtil.getONLYID32(temp + "SCA");
		} else if ('P' == t) { // PUB 公众号
			typeName = DataFormatUtil.getONLYID32(temp + "PUB");
		} else if ('W' == t) { // PUB 公众号
			typeName = DataFormatUtil.getONLYID32(temp + "WAP");
		}
		return typeName;
	}
}