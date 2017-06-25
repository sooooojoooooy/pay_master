package com.pay.main.payment.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 融脉商户接口
 * 
 * @author Guo
 */
@Controller
@RequestMapping("memory")
public class MemoryController {
	Logger logger = LoggerFactory.getLogger(MemoryController.class);

	/**
	 * 商户获取下单信息接口
	 * 
	 * @param request
	 * @return
	 */
	@RequestMapping(value = "/getMemoryInfo", method = RequestMethod.GET)
	public @ResponseBody Map<String, String> placeOrder(HttpServletRequest request) {
		Map<String, String> rtnList = new HashMap<>();
		rtnList.put("max" ,Runtime.getRuntime().maxMemory()/1024/1024+"M");
		rtnList.put("total" , Runtime.getRuntime().totalMemory()/1024/1024+"M");
		rtnList.put("free" , Runtime.getRuntime().freeMemory()/1024/1024+"M");
		logger.error(rtnList.toString());
		System.err.println(rtnList.toString());
		return rtnList;
	}
}