package com.pay.main.payment.core;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.pay.main.payment.config.RMConfig;
import com.pay.main.payment.entity.PayChannel;
import com.pay.main.payment.entity.SwitchMerchant;
import com.pay.main.payment.service.IPayChannelService;
import com.pay.main.payment.service.ISwitchMerchantService;
import com.pay.main.payment.thread.RunThread;
import com.pay.main.payment.util.DataProcessUtil;
import com.pay.main.payment.util.EncryptionUtil;
import com.pay.main.payment.util.HttpUtil;
import com.pay.main.payment.util.KafkaUtil;
import com.pay.main.payment.util.ReturnUtil;
import com.pay.main.payment.vo.RMNotifyVO;
import com.pay.main.payment.vo.RMRefundVO;

/**
 * 融脉支付
 * 
 * @author Guo
 */
@Service("rmPayCore")
public class RMPayCore {
	Logger logger = LoggerFactory.getLogger(RMPayCore.class);

	@Autowired
	PayCore payCore;
	@Autowired
	IPayChannelService payChannelService;
	@Autowired
	private ISwitchMerchantService switchMerchantService;

	/**
	 * 1.1融脉下单
	 * 
	 * @param request
	 * @return
	 */
	public Map<String, Object> placeOrder(Map<String, Object> map) {
		logger.info("支付下单--选用融脉订单接口！");
		if (ReturnUtil.RETURN_SUCCESS.equals(map.get("state"))) {
			@SuppressWarnings("unchecked")
			Map<String, String> dataMap = (Map<String, String>) map.get("data");
			dataMap.put("channel", "RMPAY"); // 支付渠道入库用

			try {
				String INFO_MCHID = null; // 商户号
				String SIGN_KEY = null; // 签名KEY
				SwitchMerchant selectSM = null;
				String code = dataMap.get("code");
				// 1.1 数据库中查询appid对应的签名
				if (null == code || "".equals(code)) {
					List<SwitchMerchant> smList = switchMerchantService.getAutoList("AUTO_RM");
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
					selectSM = switchMerchantService.getByPrimaryKey(code);
				}
				// 1.2 判断数据是否异常
				if (null != selectSM) {
					INFO_MCHID = null != selectSM.getMchid() ? selectSM.getMchid() : INFO_MCHID;
					SIGN_KEY = null != selectSM.getSign() ? selectSM.getSign() : SIGN_KEY;
				} else {
					logger.error("此APPID未配置信息（" + null != code ? code : "AUTO_RM" + "），无法使用，请修改APPID：");
					return null;
				}

				// 1.3 设置入库选择code
				dataMap.put("code", selectSM.getAppid() + "_" + selectSM.getMchid());
				dataMap.put("mchid", INFO_MCHID);
				dataMap.put("sign", SIGN_KEY);

				// 2 数据入库
				boolean bool = payCore.savePayOrder(dataMap, false);
				if (!bool) {
					return ReturnUtil.returnFail("数据错误");
				}

				// 开启线程-发送支付成功消息到kafka
				RunThread.sendMessage(KafkaUtil.PAY_NOTICE_PLACE, dataMap.toString());

				// 3 拼接下单数据
				String params = getParams(dataMap);
				byte[] buf = HttpUtil.httpPost(RMConfig.PRODUCE_URL, params);
				if (buf == null || buf.length == 0) {
					return ReturnUtil.returnFail();
				}
				String content = new String(buf);
				Map<String, String> param = null;
				param = JSON.parseObject(content, Map.class);
				return ReturnUtil.returnInfo(param);
			} catch (Exception ex) {
				logger.error("下单接口错误-融脉：", ex);
			}
			return map;
		} else {
			logger.info("下单信息有误");
			return ReturnUtil.returnFail();
		}
	}

	/**
	 * 1.2支付下单-组装参数
	 * 
	 * @return
	 * @see [类、类#方法、类#成员]
	 */
	public String getParams(Map<String, String> dataMap) {
		String orderJson = null;
		String payType = dataMap.get("payType") + "";
		try {
			// 构造数据
			Map<String, Object> param = new HashMap<String, Object>();
			param.put("tradeType", RMConfig.INFO_TRADETYPE); // 交易类型
			param.put("version", RMConfig.INFO_VERSION); // 版本
			if ("WS".equals(payType)) {
				param.put("channel", RMConfig.INFO_CHANNEL_SCAN); // 支付渠道-扫码
			} else if ("WA".equals(payType)) {
				param.put("channel", RMConfig.INFO_CHANNEL_APP); // 支付渠道-APP
			} else if ("WP".equals(payType)) {
				param.put("channel", RMConfig.INFO_CHANNEL_PUB); // 支付渠道-公众号
			} else if ("WW".equals(payType)) {
				param.put("channel", RMConfig.INFO_CHANNEL_H5); // 支付渠道
			} else if ("AS".equals(payType)) {
				param.put("channel", RMConfig.INFO_CHANNEL_ALSCAN); //
			} else if ("AA".equals(payType)) {
				param.put("channel", RMConfig.INFO_CHANNEL_ALAPP); //
			} else if ("AW".equals(payType)) {
				param.put("channel", RMConfig.INFO_CHANNEL_ALWAP); //
			}
			param.put("mchNo", dataMap.get("mchid")); // 由融脉分配的商户号
			param.put("body", dataMap.get("title"));// 商品或支付单简要描述
			param.put("mchOrderNo", dataMap.get("orderNo")); // 商户系统内部的支付订单号,32个字符内、可包含字母,数字，请确保在商户系统唯一
			param.put("amount", dataMap.get("price"));
			param.put("description", dataMap.get("describe"));

			// 批量订单
			if ("WP".equals(payType)) {
				param.put("openId", dataMap.get("openId")); // 为用户在商户appid下的唯一标识
			}
			param.put("callbackUrl", dataMap.get("synUrl")); // 交易完成后跳转的 URL
			param.put("notifyUrl", RMConfig.NOTIFY_URL);// 支付结果通知地址
			param.put("outMerchantNo", dataMap.get("orderNo"));
			param.put("virAccNo", RMConfig.INFO_VIRACCNO);
			param.put("rmt", "订单充值");

			// 生成签名
			String sign = createSign(dataMap.get("sign"), JSON.toJSONString(param));
			param.put("sign", sign); // sign签名
			orderJson = JSON.toJSONString(param);
		} catch (Exception ex) {
			logger.error("退款签名错误:", ex);
		}
		return orderJson;
	}

	/**
	 * 退款接口
	 * 
	 * @param request
	 * @return
	 */
	public Map<String, Object> getRefund(String merId, String merNo) {
		String content = null;
		try {
			// 回调信息写入文档
			Map<String, String> param = new HashMap<String, String>();
			param.put("merId", merId);
			param.put("merNo", merNo);
			PayCore.writeRefundInfo("zypay", "127.0.0.1", param);

			if (null == merNo) {
				return ReturnUtil.returnFail("无该订单号:" + merNo);
			}

			// 查询订单号，发送通知下游支付状态
			PayChannel payChannel = payChannelService.getByPrimaryKeyMer(merId, merNo);
			String params = getRefundJson(payChannel);
			logger.info("退款订单信息-融脉：" + params);
			byte[] buf = HttpUtil.httpPost(RMConfig.PRODUCE_URL, params);
			if (buf == null || buf.length == 0) {
				return ReturnUtil.returnFail();
			}
			content = new String(buf);
			logger.info("申请成功-融脉：" + content);
			RMRefundVO vo = JSON.parseObject(content, RMRefundVO.class);
			// 修改数据订单状态值
			boolean bool = setRefundPayInfo(vo);
			if (bool) {
				// 查询订单号，发送通知下游支付状态
				int index = 0;
				while (index < 5) {
					payChannel.setpState(3);
					boolean sendBool = payCore.sendNotify(payChannel, "3");
					index++;
					index = sendBool ? 99 : index;
				}
			}
		} catch (Exception ex) {
			logger.error("退款接口错误-融脉：", ex);
		}
		return ReturnUtil.returnInfo(content);
	}

	/**
	 * 拼接 退款接口数据
	 * 
	 * @param channel
	 * @return
	 */
	public String getRefundJson(PayChannel channel) {
		// 查询订单号，发送通知下游支付状态
		String appId = channel.getAppId();
		Float prince = channel.getpFee();
		String tradeNo = channel.getChannelNo();

		Map<String, String> autoSM = payCore.getAutoSM(appId);
		// 设置默认值
		String INFO_MCHID = autoSM.get("INFO_MCHID");// 商户号
		String SIGN_KEY = autoSM.get("SIGN_KEY");// 签名KEY

		Map<String, Object> param = new HashMap<String, Object>();
		try {
			param.put("tradeType", "cs.refund.submit");
			param.put("channel", "refund");
			param.put("version", "2.0");// 版本号，默认2.0
			param.put("mchNo", INFO_MCHID);// 由融脉分配的商户号
			param.put("cpOrderNo", tradeNo);
			param.put("mchRefundNo", tradeNo); // 如果首次退款失败，再次重试退款，退款单号不变
			param.put("refundAmt", prince); // 退款金额
			param.put("remark", "退款申请"); // 否
			// 生成签名
			String sign = createSign(SIGN_KEY, JSON.toJSONString(param));
			param.put("sign", sign); // sign签名
		} catch (Exception ex) {
			logger.error("退款签名错误:", ex);
		}
		return JSON.toJSONString(param);
	}

	/**
	 * 创建签名（融脉交互）
	 * 
	 * @param signKey
	 * @param josnStr
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public String createSign(String signKey, String josnStr) throws Exception {
		JSONObject jsonObject = JSON.parseObject(josnStr);
		jsonObject.remove("sign"); // 去除sign，回调不加sign，下单无此对象
		Map<String, Object> extraMap = (Map<String, Object>) jsonObject.remove("extra");// 先把extra的挪到外面
		if (extraMap != null) {
			jsonObject.putAll(extraMap);
		}
		Map<String, Object> signParam = JSON.parseObject(jsonObject.toJSONString());// 复制出一个map用于签
		List<Map<String, Object>> detailParamList = new ArrayList<Map<String, Object>>();
		detailParamList = (List<Map<String, Object>>) signParam.get("details");
		String detailsText = "";
		if (null != detailParamList) {
			for (Map<String, Object> singleDetail : detailParamList) {
				detailsText += "{" + DataProcessUtil.textLink(singleDetail, false) + "}&";
			}
			signParam.put("details", "[" + detailsText.substring(0, detailsText.length() - 1) + "]");// 转成字符串后加入（//
																										// 字符串格式[{key1=11&key2=22},{key1=11&key2=22}]）
		}
		String orderText = DataProcessUtil.textLink(signParam, false) + "&key=" + signKey;
		String sign = "";
		// 获得签名验证结果
		try {
			sign = EncryptionUtil.md5s(orderText).toUpperCase();
		} catch (Exception e) {
			sign = EncryptionUtil.md5s(orderText).toUpperCase();
		}
		return sign;
	}

	/**
	 * 2.1 回调信息 处理
	 * 
	 * @param tradeNo
	 * @param str
	 * @return
	 * @throws Exception
	 */
	public boolean getAutograph(String wholeStr) throws Exception {
		RMNotifyVO vo = JSON.parseObject(wholeStr, RMNotifyVO.class);

		// 查询回调对应的下单信息
		PayChannel payChannel = payChannelService.getByPrimaryKey(vo.getMchOrderNo());
		String payAppid = payChannel.getAppId();

		// 数据库中查询appid对应的签名
		Map<String, String> autoSM = payCore.getAutoSM(payAppid);
		String INFO_MCHID = autoSM.get("INFO_MCHID");// 商户号
		String SIGN_KEY = autoSM.get("SIGN_KEY");// 签名KEY

		// 生成签名验证上游数据真伪性
		String sign = createSign(SIGN_KEY, wholeStr);
		if (sign.equals(vo.getSign())) {
			// 支付成功
			if ("0".equals(vo.getResultCode())) {
				// 修改数据订单状态值
				boolean setBool = setSuccPayInfo(vo);

				// 开启线程-发送支付成功消息到kafka
				Map<String, Object> param = new HashMap<String, Object>();
				param.put("code", payAppid);
				param.put("orderNo", payChannel.getTradeNo());
				RunThread.sendMessage(KafkaUtil.PAY_NOTICE_SUCCESS, param.toString());

				if (setBool) {
					// 查询订单号，发送通知下游支付状态
					int index = 0;
					while (index < 5) {
						boolean sendBool = payCore.sendNotify(payChannel, "1");
						index++;
						index = sendBool ? 99 : index;
					}
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * 2.2 修改数据库中值(支付成功)
	 * 
	 * @param vo
	 * @return
	 */
	public boolean setSuccPayInfo(RMNotifyVO vo) {
		int index = 0;
		try {
			// 修改订单信息
			Date date = new Date();
			PayChannel payChannel = new PayChannel();
			payChannel.setTradeNo(vo.getMchOrderNo()); // 商户订单号(自己 )
			payChannel.setChannelNo(vo.getCpOrderNo()); // 融脉订单号
			payChannel.setWechatNo(vo.getTransactionId()); // 微信订单号

			payChannel.setpState(1); // 支付状态为成功
			payChannel.setpFee(vo.getTotalFee()); // 金额
			payChannel.setpTime(date);
			payChannel.setModifiedTime(date);
			index = payChannelService.updateByPrimaryKeySelective(payChannel);

			// // 修改信息配置表支付时间
			// sm.setTotal(vo.getTotalFee());
			// sm.setLastPayTime(date);
			// if (vo.getTotalFee() > 1000) {
			// sm.setLargePayTime(date);
			// }
			// switchMerchantService.setTotal(sm);
		} catch (Exception ex) {
			logger.error("数据库入库失败-融脉 :", ex);
		}
		return index > 0;
	}

	/**
	 * 修改数据库中值(回调信息-退款)
	 * 
	 * @param vo
	 * @return
	 */
	public boolean setRefundPayInfo(RMRefundVO vo) {
		PayChannel payChannel = new PayChannel();
		payChannel.setTradeNo(vo.getMchOrderNo()); // 商户订单号(自己 )
		payChannel.setpState(3); // 支付状态为退款
		payChannel.setModifiedTime(new Date()); // 修改时间
		int index = payChannelService.updateByPrimaryKeySelective(payChannel);
		return index > 0;
	}
}