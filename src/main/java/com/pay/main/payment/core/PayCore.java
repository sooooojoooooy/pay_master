package com.pay.main.payment.core;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.pay.main.payment.entity.PayChannel;
import com.pay.main.payment.entity.SwitchMerchant;
import com.pay.main.payment.entity.UserLogin;
import com.pay.main.payment.service.IPayChannelService;
import com.pay.main.payment.service.ISwitchMerchantService;
import com.pay.main.payment.service.IUserLoginService;
import com.pay.main.payment.util.CommonUtil;
import com.pay.main.payment.util.DataFormatUtil;
import com.pay.main.payment.util.DataProcessUtil;
import com.pay.main.payment.util.EncryptionUtil;
import com.pay.main.payment.util.FileIOUtils;
import com.pay.main.payment.util.HttpUtil;
import com.pay.main.payment.util.ReturnUtil;
import com.pay.main.payment.vo.RMSendNotifyVO;

/**
 * 支付通用方法
 *
 * @author Guo
 */
@Service("payCore")
public class PayCore {
	private static Logger logger = LoggerFactory.getLogger(PayCore.class);

	@Autowired
	IPayChannelService payChannelService;
	@Autowired
	IUserLoginService userLoginService;
	@Autowired
	ISwitchMerchantService switchMerchantService;

	/**
	 * 获取客户端下单参数（必传merId,商户sign）
	 *
	 * @param keyStr
	 *            获取参数名，逗号分割
	 * @return
	 */
	public Map<String, Object> getRequestParameter(HttpServletRequest request, String keyStr) {
		Map<String, Object> params = new HashMap<String, Object>();

		String[] key = null != keyStr ? keyStr.split(",") : null;

		// 遍历保存传参数据
		for (int i = 0; i < key.length; i++) {
			String onKey = key[i];
			params.put(onKey, request.getParameter(onKey));
		}

		// 打印传值
		logger.info("客户端下单传值:" + params.toString());

		// 判断用户是否开启支付
		Integer ulPaystate = 0;
		UserLogin userInfo = userLoginService.getByPrimaryKey(params.get("merId") + "");
		ulPaystate = null != userInfo ? userInfo.getUlPaystate() : 0;
		if (ulPaystate != 1) {
			return ReturnUtil.returnFail("该商户号不可用");
		}

		// 判断签名正确性
		Map<String, Object> map = DataProcessUtil.removeNullMap(params);
		map.remove("sign");
		String textLink = DataProcessUtil.textLink(map, true); // 拼接字符串
		String md5s = EncryptionUtil.md5s(textLink + "&&&" + userInfo.getUlSign());
		String sign = params.get("sign") + "";
		if (!md5s.equals(sign)) {
			String signStr = textLink + "&&&" + userInfo.getUlSign();
			logger.info("生成签名字符串: " + signStr + " ==> " + md5s + " : " + sign);
			return ReturnUtil.returnFail("签名错误！");
		}

		// 生成订单号
		String orderNo = CommonUtil.getOrderNo(params.get("payType") + "");
		params.put("orderNo", orderNo);
		if (null == orderNo) {
			return ReturnUtil.returnFail("支付类型错误！");
		}

		return ReturnUtil.returnInfo(params);
	}

	/**
	 * 动态获取下单详情
	 *
	 * @param code
	 * @return
	 */
	public Map<String, String> getAutoSM(String code) {
		Map<String, String> map = new HashMap<String, String>();

		// 处理自动下单和指定下单
		String appid = null;
		String mchid = null;
		boolean boole = false;
		String[] split = code.split("_");
		if (split.length == 3) {
			mchid = split[2];
			appid = split[0] + "_" + split[1];
		} else {
			appid = split[0];
			boole = true;
		}

		String INFO_MCHID = null;// 商户号
		String SIGN_KEY = null;// 签名KEY
		// 数据库中查询appid对应的签名
		SwitchMerchant sm = null;
		if (!boole) {
			sm = switchMerchantService.getAutoInfo(appid, mchid);
		} else {
			sm = switchMerchantService.getByPrimaryKey(appid);
		}
		if (null != sm) {
			INFO_MCHID = null != sm.getMchid() ? sm.getMchid() : INFO_MCHID;
			SIGN_KEY = null != sm.getSign() ? sm.getSign() : SIGN_KEY;
		} else {
			logger.info("回调信息数据校验CODE未查询到（" + code + "），无法使用，请修改CODE：");
			logger.error("回调信息数据校验CODE未查询到（" + code + "），无法使用，请修改CODE：", sm.toString());
		}

		map.put("INFO_MCHID", INFO_MCHID);
		map.put("SIGN_KEY", SIGN_KEY);
		return map;
	}

	/**
	 * 数据库入库1.0（汇元支付使用）
	 *
	 * @return
	 */
	public boolean payOrder(String merId, String merDescribe, String tradeNo, String merchantNo, String pChannel, String prince, String pTitle, String pAttach, String pType, String code, String notifyUrl) {
		try {
			// 数据入库
			PayChannel payEntity = new PayChannel(merId, merDescribe, tradeNo, merchantNo, pChannel, prince, pTitle, pAttach, pType, null, 0, new Date(), code, notifyUrl);

			payChannelService.insertSelective(payEntity);
		} catch (Exception ex) {
			logger.error("支付数据入库失败【PayRongMaiController】:", ex);
			return false;
		}
		return true;
	}

	/**
	 * 数据库入库2.0
	 *
	 * @return
	 */
	public boolean savePayOrder(Map<String, String> params, boolean bool) {
		String merId = params.get("merId"); // 商户号
		String merDescribe = params.get("describe"); // 商户描述
		String tradeNo = params.get("orderNo"); // 订单号
		String merchantNo = params.get("merNo"); // 商户订单号
		String pChannel = params.get("channel"); // 支付渠道
		String price = params.get("price"); // 支付金额
		String pTitle = params.get("title"); // 标题名称
		String pAttach = params.get("attach"); // 自定义参数
		String pType = params.get("payType"); // 支付类型
		Date pTime = null; // 支付成功时间null
		int pState = 0; // 支付状态默认0
		Date createTime = new Date(); // 创建时间
		String code = params.get("code"); // 使用商户标志
		String notifyUrl = params.get("notifyUrl"); // 商户通知地址

		// 分转元
		if (bool) {
			Float yuan = Float.parseFloat(price) / 100;
			price = yuan + "";
		}

		PayChannel payEntity = null;
		try {
			// 数据入库
			payEntity = new PayChannel(merId, merDescribe, tradeNo, merchantNo, pChannel, price, pTitle, pAttach, pType, pTime, pState, createTime, code, notifyUrl);

			payChannelService.insertSelective(payEntity);
		} catch (Exception ex) {
			logger.error("支付数据入库错误-数据：" + payEntity.toString() + "-日志:", ex);
			return false;
		}
		return true;
	}

	/**
	 * 发送异步数据给商户
	 *
	 * @param payChannel
	 * @return
	 */
	public boolean sendNotify(PayChannel payChannel, String state) {
		boolean bool = false;
		if (null == payChannel)
			return bool;
		try {
			String merNo = payChannel.getMerchantNo();
			String title = payChannel.getpTitle();
			String attach = payChannel.getpAttach();
			String prince = payChannel.getpFee() + "";
			String type = payChannel.getpType() + "";
			String appId = payChannel.getAppId();
			String notifyUrl = payChannel.getNotifyUrl();

			String merId = payChannel.getMerId();
			UserLogin userInfo = userLoginService.getByPrimaryKey(merId);

			RMSendNotifyVO vo = new RMSendNotifyVO(merNo, title, attach, prince, state, type, appId, null);
			Map<String, Object> params = DataProcessUtil.convertBeanNotNullToMap(vo);
			params.remove("sign"); // 删除sign
			logger.info("回调信息需要加密数据：" + params);
			String textLink = DataProcessUtil.textLink(params, true); // 拼接字符串
			String sign = EncryptionUtil.md5s(textLink + "&&&" + userInfo.getUlSign());
			params.put("sign", sign); // 添加生成的sign

			String doPost = HttpUtil.doPost(notifyUrl, params, "UTF-8");
			logger.info("（" + merNo + "）通知商户返回信息：" + doPost);
			String temp = (doPost + "").toUpperCase().trim();
			if ("SUCCESS".equals(temp)) {
				bool = true;
			} else {
				// Thread.sleep(5000);
			}
		} catch (Exception ex) {
			logger.error("通知商户发送异步数据-sendNotify：", ex);
		}
		return bool;
	}

	public static <V> boolean writeNotifyInfo(String channel, String ip, Map<String, V> params) {
		return writeInfo("notify", channel, "callback", ip, params);
	}

	public static <V> boolean writeRefundInfo(String channel, String ip, Map<String, V> params) {
		return writeInfo("refund", channel, "refund", ip, params);
	}

	/**
	 * 回调信息写入文档
	 */
	private static <V> boolean writeInfo(String mark, String channel, String fileName, String ip, Map<String, V> params) {
		try {
			Date date = new Date();
			String time = DataFormatUtil.currentDateFormat(2, date);
			String dName = DataFormatUtil.currentDateFormat(0, date);
			String body = ip + "(" + time + ") -> " + params;
			File file = new File("/app/logs/pay-main/" + mark + "/" + channel);
			if (!file.isDirectory()) {
				file.mkdirs();
			}
			FileIOUtils.wirteTxtFile(body, "/app/logs/pay-main/" + mark + "/" + channel + "/" + fileName + "." + dName + ".txt");
			return true;
		} catch (Exception ex) {
			logger.error("写入回调信息失败！-" + mark, ex);
			return false;
		}
	}
}