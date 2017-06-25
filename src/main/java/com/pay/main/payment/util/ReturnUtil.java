package com.pay.main.payment.util;

import java.util.HashMap;
import java.util.Map;

public class ReturnUtil {
	public static final String RETURN_SUCCESS = "200";
	public static final String RETURN_FAIL = "400";

	public static Map<String, Object> returnInfo(Object obj) {
		Map<String, Object> map = new HashMap<String, Object>();
		if (null != obj) {
			map.put("state", RETURN_SUCCESS);
			map.put("data", obj);
		} else {
			map.put("state", RETURN_FAIL);
		}
		return map;
	}

	public static Map<String, Object> returnFail() {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("state", RETURN_FAIL);
		return map;
	}

	public static Map<String, Object> returnFail(Object obj) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put("state", RETURN_FAIL);
		map.put("error", obj);
		return map;
	}
}
