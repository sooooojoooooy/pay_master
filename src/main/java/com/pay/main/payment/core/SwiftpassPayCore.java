package com.pay.main.payment.core;

import com.pay.main.payment.config.Constant;
import com.pay.main.payment.config.SwiftpassConfig;
import com.pay.main.payment.entity.PayChannel;
import com.pay.main.payment.entity.SwitchMerchant;
import com.pay.main.payment.entity.TransChannel;
import com.pay.main.payment.service.ISwitchMerchantService;
import com.pay.main.payment.service.ITransChannelService;
import com.pay.main.payment.thread.RunThread;
import com.pay.main.payment.util.*;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;

import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 威富通
 * 
 * @author Guo
 */
@Service("spPayCore")
public class SwiftpassPayCore {
	Logger logger = LoggerFactory.getLogger(SwiftpassPayCore.class);

	@Autowired
    PayCore payCore;
	@Autowired
	ITransChannelService transChannelService;
	@Autowired
    ISwitchMerchantService switchMerchantService;

	/**
	 * 1.1 威富通下单
	 * 
	 * @return
	 * @throws IOException
	 */
	public Map<String, Object> placeOrder(Map<String, String> dataMap) {
		logger.info("支付下单--选用威富通订单接口！");
		dataMap.put("ip", null != dataMap.get("ip") ? dataMap.get("ip") : "0.0.0.0");
		dataMap.put("channel", "SWIFTPASS"); // 支付渠道入库用

		String INFO_MCHID = null; // 商户号
		String SIGN_KEY = null; // 签名KEY
		SwitchMerchant selectSM = null;
		String code = dataMap.get("code");
		// 1.1 数据库中查询Appid对应的签名
		if (null == code || "".equals(code)) {
			List<SwitchMerchant> smList = switchMerchantService.getAutoList(dataMap.get("mch_id"));
			if (null != smList && smList.size() > 0) {
				Float temp = smList.get(0).getTotal();
				for (SwitchMerchant info : smList) {
					if (temp >= info.getTotal()) {
						selectSM = info;
						temp = info.getTotal();
					}
				}
			}
		} else {
			return ReturnUtil.returnFail("无支付通道！",1002);
		}
		// 1.2 判断数据是否异常
		if (null != selectSM) {
			INFO_MCHID = null != selectSM.getMchid() ? selectSM.getMchid() : null;
			SIGN_KEY = null != selectSM.getSign() ? selectSM.getSign() : null;
		} else {
			logger.error("此APPID未配置信息（" + null != code ? code : "AUTO_RM" + "），无法使用，请修改APPID：");
			return ReturnUtil.returnFail("支付通道异常！",1003);
		}

		// 1.3 设置入库选择code
		dataMap.put("code", selectSM.getAppid() + "_" + selectSM.getMchid());

		// 2.1 数据入库
		boolean bool = payCore.savePayOrder(dataMap, false);
		if (!bool) {
			return ReturnUtil.returnFail("下单参数有误！",1005);
		}

		// 创建下单信息
		SortedMap<String, String> orderMap = new TreeMap<String, String>();
		orderMap.put("service", dataMap.get("service"));
		orderMap.put("version", "2.0");
		orderMap.put("total_fee", dataMap.get("total_fee"));
		orderMap.put("charset", "UTF-8");
		orderMap.put("sign_type", "MD5");
		orderMap.put("mch_id", INFO_MCHID);
		orderMap.put("out_trade_no", dataMap.get("orderNo"));
		orderMap.put("body", dataMap.get("body"));
		orderMap.put("attach", dataMap.get("attach"));
		orderMap.put("total_fee", dataMap.get("total_fee"));
		orderMap.put("mch_create_ip", dataMap.get("ip"));
		orderMap.put("notify_url", SwiftpassConfig.notify_url);
		orderMap.put("nonce_str", String.valueOf(new Date().getTime()));
		
		// 条件添加
//		if (dataMap.get("payType").equals("WP")) { // 微信公众号支付
//			orderMap.put("callback_url", dataMap.get("synUrl"));
//			orderMap.put("sub_openid", dataMap.get("openId"));
//		} else if(dataMap.get("payType").equals("WW")){
//			orderMap.put("device_info", "iOS_WAP");
//			orderMap.put("mch_app_name", "AppStore");
//			orderMap.put("mch_app_id", "http://www.baidu.com");
//		}
		
		// 生成下单签名
		Map<String, String> params = SwiftpassPayCore.paraFilter(orderMap);
		StringBuilder buf = new StringBuilder();
		SwiftpassPayCore.buildPayParams(buf, params, false);
		String preStr = buf.toString();
		String onSign = MD5.sign(preStr, "&key=" + SIGN_KEY, "utf-8");
		orderMap.put("sign", onSign);

		try {
			String xml = XmlUtils.parseXML(orderMap);
			logger.error("威富通下单报文:", xml);
			
			// 下单请求参数
			byte[] httpPost2 = HttpUtil.httpPost(SwiftpassConfig.req_url, xml);
			Map<String, String> map = XmlUtils.toMap(httpPost2, "UTF-8");
			logger.error("威富通下单返回信息", map.toString());
			// 拼接返回信息
			Map<String, Object> resultMap = new HashMap<String, Object>();
			resultMap.put("token_id", map.get("token_id"));
			resultMap.put("code_url", map.get("code_url"));
			resultMap.put("code_img_url", map.get("code_img_url"));
			resultMap.put("message", map.get("message"));
			resultMap.put("err_msg", map.get("err_msg"));
			resultMap.put("pay_info", map.get("pay_info"));
			return ReturnUtil.returnInfo(DataProcessUtil.removeNullMap(resultMap));
		} catch (Exception ex) {
			logger.error("威富通下单错误：", ex);
			return ReturnUtil.returnFail("下单参数有误！",1005);
		}
	}

	public boolean getAutograph(String resString) throws Exception {
		logger.info("回调信息-威富通：" + resString);
		if (resString != null && !"".equals(resString)) {
			Map<String, String> map = XmlUtils.toMap(resString.getBytes(), "utf-8");
			if (map.containsKey("sign")) {
				// 数据库中查询appid对应的签名
				String appid = map.get("mch_id");
				Map<String, Object> autoSM = payCore.getAutoSM(appid);
				// String INFO_MCHID = (String) autoSM.get("INFO_MCHID");// 商户号
				String SIGN_KEY = (String) autoSM.get("SIGN_KEY");// 签名KEY
				SwitchMerchant sm = (SwitchMerchant) autoSM.get("OBJECT_SM");
				boolean result = false;
				if (map.containsKey("sign")) {
					String sign = map.get("sign");
					map.remove("sign");
					StringBuilder buf = new StringBuilder((map.size() + 1) * 10);
					SwiftpassPayCore.buildPayParams(buf, map, false);
					String preStr = buf.toString();
					String signRecieve = MD5.sign(preStr, "&key=" + SIGN_KEY, "utf-8");
					result = sign.equalsIgnoreCase(signRecieve);
				}
				if (!result) {
					logger.info("SwiftpassPay验证签名不通过");
				} else {
					String status = map.get("status");
					if (status != null && "0".equals(status)) {
						String result_code = map.get("result_code");
						if (result_code != null && "0".equals(result_code)) {
							// 此处可以在添加相关处理业务，校验通知参数中的商户订单号out_trade_no和金额total_fee是否和商户业务系统的单号和金额是否一致，一致后方可更新数据库表中的记录。
							return setSuccPayInfo(map, sm);
						}
					}
				}
			}
		}
		return false;
	}

	/**
	 * 修改数据库中值(支付成功)
	 * 
	 * @param vo
	 * @return
	 */
	public boolean setSuccPayInfo(Map<String, String> vo, SwitchMerchant sm) {
		String paySign = vo.get("trade_type");
		Integer payType = getPayTypeMarkNum(paySign);
		String orderNo = vo.get("out_trade_no");
		Float fee = Float.parseFloat(vo.get("total_fee")) / 100;
		// 修改订单信息
		Date date = new Date();
		PayChannel payChannel = new PayChannel();
		payChannel.setTradeNo(orderNo); // 订单号(自己 )
		payChannel.setChannelNo(vo.get("transaction_id")); //
		payChannel.setWechatNo(vo.get("out_transaction_id")); // 微信订单号

		payChannel.setpType(payType); // 支付方式
		payChannel.setpState(1); // 支付状态为成功
		payChannel.setpFee(fee); // 金额
		payChannel.setModifiedTime(date);
		DateFormat fmt =new SimpleDateFormat("yyyyMMddHHmmss");
		Date pDate = new Date();
		try {
			pDate = fmt.parse(vo.get("time_end"));
		} catch (ParseException e) {
			e.printStackTrace();
		}
		payChannel.setpTime(pDate);
		// 修改数据库值
		boolean bool = false;
		try {
			bool = transChannelService.updateByPrimaryKeySelective(payChannel);
			
			// 修改下单金额，切换壳子用(sql累加)
			// sm.setTotal(vo.getTotalFee());
			sm.setTotal(1F);
			// 修改信息配置表支付时间
			sm.setTotal(fee);
			sm.setLastPayTime(date);
			if (fee > 1000) {
				sm.setLargePayTime(date);
			}
			switchMerchantService.setTotal(sm);
		} catch (Exception ex) {
			logger.error("更新支付订单状态错误：", ex);
		}

		// 通知商户支付状态
		if (bool) {
			PayChannel info = transChannelService.getByPrimaryKey(orderNo);
			// 查询订单号，发送通知下游支付状态
			return payCore.sendNotify(info, "1");
		} else {
			logger.error("数据状态更新错误！");
		}
		return false;
	}

	/**
	 * 验证返回参数
	 * 
	 * @param params
	 * @param key
	 * @return
	 */
	public static boolean checkParam(Map<String, String> params, String key) {
		boolean result = false;
		if (params.containsKey("sign")) {
			String sign = params.get("sign");
			params.remove("sign");
			StringBuilder buf = new StringBuilder((params.size() + 1) * 10);
			SwiftpassPayCore.buildPayParams(buf, params, false);
			String preStr = buf.toString();
			String signRecieve = MD5.sign(preStr, "&key=" + key, "utf-8");
			result = sign.equalsIgnoreCase(signRecieve);
		}
		return result;
	}

	/**
	 * 过滤参数
	 * 
	 * @param sArray
	 * @return
	 */
	public static Map<String, String> paraFilter(Map<String, String> sArray) {
		Map<String, String> result = new HashMap<String, String>(sArray.size());
		if (sArray == null || sArray.size() <= 0) {
			return result;
		}
		for (String key : sArray.keySet()) {
			String value = sArray.get(key);
			if (value == null || value.equals("") || key.equalsIgnoreCase("sign")) {
				continue;
			}
			result.put(key, value);
		}
		return result;
	}

	/**
	 * 将map转成String
	 * 
	 * @param payParams
	 * @return
	 */
	public static String payParamsToString(Map<String, String> payParams) {
		return payParamsToString(payParams, false);
	}

	public static String payParamsToString(Map<String, String> payParams, boolean encoding) {
		return payParamsToString(new StringBuilder(), payParams, encoding);
	}

	/**
	 * @param payParams
	 * @return
	 */
	public static String payParamsToString(StringBuilder sb, Map<String, String> payParams, boolean encoding) {
		buildPayParams(sb, payParams, encoding);
		return sb.toString();
	}

	/**
	 * @param payParams
	 * @return
	 */
	public static void buildPayParams(StringBuilder sb, Map<String, String> payParams, boolean encoding) {
		List<String> keys = new ArrayList<String>(payParams.keySet());
		Collections.sort(keys);
		for (String key : keys) {
			sb.append(key).append("=");
			if (encoding) {
				sb.append(urlEncode(payParams.get(key)));
			} else {
				sb.append(payParams.get(key));
			}
			sb.append("&");
		}
		sb.setLength(sb.length() - 1);
	}

	public static String urlEncode(String str) {
		try {
			return URLEncoder.encode(str, "UTF-8");
		} catch (Throwable e) {
			return str;
		}
	}

	public static Element readerXml(String body, String encode) throws DocumentException {
		SAXReader reader = new SAXReader(false);
		InputSource source = new InputSource(new StringReader(body));
		source.setEncoding(encode);
		Document doc = reader.read(source);
		Element element = doc.getRootElement();
		return element;
	}

	/**
	 * 选择支付方式
	 * 
	 * @param payType
	 * @return
	 */
	public Map<String, String> getPayTypeMark(String payType) {
		Map<String, String> map = new HashMap<String, String>();
		String mark = null;
		String version = "2.0";
		if (payType.length() == 2) {
			// char f = payType.charAt(0); // 付款渠道
			char t = payType.charAt(1); // 付款类型
			if (t == 'A') { // 统一APP
				mark = "unified.trade.pay";
			} else if ("AS".equals(payType)) { // 支付宝扫码
				mark = "pay.alipay.native";
			} else if ("WS".equals(payType)) { // 微信扫码
				mark = "pay.weixin.native";
			} else if ("WP".equals(payType)) { // 微信公众号
				mark = "pay.weixin.jspay";
			} else if ("WW".equals(payType)) { // 微信WAP
				mark = "pay.weixin.wappay";
			} else if ("QS".equals(payType)) { // QQ钱包扫码
				mark = "pay.tenpay.native";
			} else if ("QP".equals(payType)) { // QQ钱包公众号
				mark = "pay.tenpay.jspay";
			}
			logger.info("威富通无该支付方式！");
		}
		map.put("mark", mark);
		map.put("version", version);
		return map;
	}

	/**
	 * 获取支付类型
	 * 
	 * @param payType
	 * @return
	 */
	public Integer getPayTypeMarkNum(String payType) {
		Integer index = 0;
		if (null != payType) {
			if ("unified.trade.pay".equals(payType)) { // 统一APP
				index = Constant.PAYTYPE_WA;
			} else if ("pay.weixin.native".equals(payType)) { // 微信扫码
				index = Constant.PAYTYPE_WS;
			} else if ("pay.weixin.jspay".equals(payType)) { // 微信公众号
				index = Constant.PAYTYPE_WP;
			} else if ("pay.weixin.wappay".equals(payType)) { // 微信WAP
				index = Constant.PAYTYPE_WW;
			} else if ("pay.alipay.native".equals(payType)) { // 支付宝扫码
				index = Constant.PAYTYPE_AS;
			}  else if ("pay.tenpay.native".endsWith(payType)) { // qq钱包扫码
				index = Constant.PAYTYPE_QS;
			} else if ("pay.tenpay.jspay".endsWith(payType)) { // qq钱包公众号
				index = Constant.PAYTYPE_QP;
			}
		}
		return index;
	}
}