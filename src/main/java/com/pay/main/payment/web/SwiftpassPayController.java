package com.pay.main.payment.web;

import com.pay.main.payment.core.PayCore;
import com.pay.main.payment.core.SwiftpassPayCore;
import com.pay.main.payment.service.ISwitchMerchantService;
import com.pay.main.payment.util.ReturnUtil;
import com.pay.main.payment.util.XmlUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * 威富通支付
 *
 * @author Guo
 */
@Controller
@RequestMapping("pay")
public class SwiftpassPayController {
	Logger logger = LoggerFactory.getLogger(SwiftpassPayController.class);

	@Autowired
    PayCore payCore;
	@Autowired
	SwiftpassPayCore spPayCore;

	@RequestMapping(value = "gateway", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> gateway(HttpServletRequest request, HttpServletResponse resp) {
		logger.info("支付下单-威富通!");
		String keys = "service,mch_id,out_trade_no,body,attach,total_fee,notify_url,callback_url,sign";
		Map<String, Object> params = payCore.getRequestParameter(request, keys);

		if ("1000".equals(params.get("rtnCode"))) {
			@SuppressWarnings("unchecked")
			Map<String, String> dataMap = (Map<String, String>) params.get("rtnMsg");
			return spPayCore.placeOrder(dataMap);
		}
		return params;
	}

	/**
	 * 回调信息接口(接受上游返回回调信息)
	 *
	 * @param request
	 * @return
	 * @throws IOException
	 */
	@RequestMapping(value = "/callback", method = RequestMethod.POST)
	public @ResponseBody String notifyUrl(HttpServletRequest request) {
		logger.info("接受回调信息-威富通!");
		try {
			request.setCharacterEncoding("utf-8");
			String resString = XmlUtils.parseRequst(request);
			System.err.println(resString);
			boolean autograph = spPayCore.getAutograph(resString);
			if (autograph) {
				return "success";
			}else {
				return "fail";
			}
		} catch (Exception ex) {
			logger.error("回调信息错误-威富通：", ex);
		}
		return "fail";
	}
}