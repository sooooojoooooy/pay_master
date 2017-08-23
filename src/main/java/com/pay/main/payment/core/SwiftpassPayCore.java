package com.pay.main.payment.core;

import com.alibaba.fastjson.JSONObject;
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
        dataMap.put("ip", null != dataMap.get("ip") ? dataMap.get("ip") : "127.0.0.1");
        dataMap.put("channel", "ZZ"); // 支付渠道入库用

        String INFO_MCHID = null; // 商户号
        String SIGN_KEY = null; // 签名KEY
        SwitchMerchant selectSM = null;
        // 2.1 数据入库
        boolean bool = payCore.savePayOrder(dataMap, true);
        if (!bool) {
            return ReturnUtil.returnFail("下单参数有误！", 1005);
        }
        // 创建下单信息
        SortedMap<String, Object> orderMap = new TreeMap<String, Object>();
        orderMap.put("amount", dataMap.get("total_fee"));
        orderMap.put("backurl", dataMap.get("callback_url"));
        orderMap.put("desc", "订单充值");
        orderMap.put("extra", dataMap.get("orderNo"));
        orderMap.put("merch", SwiftpassConfig.merId);
        orderMap.put("notifyurl", SwiftpassConfig.notify_url);
        orderMap.put("product", "订单充值");
        orderMap.put("type", "12");
//        orderMap.put("service", dataMap.get("service"));
//        orderMap.put("version", "2.0");
//        orderMap.put("total_fee", dataMap.get("total_fee"));
//        orderMap.put("charset", "UTF-8");
//        orderMap.put("sign_type", "MD5");
//        orderMap.put("mch_id", SwiftpassConfig.merId);
//        orderMap.put("out_trade_no", dataMap.get("orderNo"));
//        orderMap.put("body", "订单充值");
//        orderMap.put("attach", dataMap.get("attach"));
//        orderMap.put("total_fee", dataMap.get("total_fee"));
//        orderMap.put("mch_create_ip", dataMap.get("ip"));
//        orderMap.put("notify_url", SwiftpassConfig.notify_url);
//        orderMap.put("nonce_str", String.valueOf(new Date().getTime()));
//        orderMap.put("callback_url", dataMap.get("callback_url"));
//        orderMap.put("device_info", "iOS_WAP");
//        orderMap.put("mch_app_name", "AppStore");
//        orderMap.put("mch_app_id", "http://www.baidu.com");
        // 生成下单签名
        Map<String, String> params = SwiftpassPayCore.paraFilter(orderMap);
        StringBuilder buf = new StringBuilder();
        SwiftpassPayCore.buildPayParams(buf, params, false);
        String preStr = buf.toString();
        String onSign = MD5.sign(preStr, "&key=" + SwiftpassConfig.Key, "utf-8");
        orderMap.put("sign", onSign);

        try {
            // 下单请求参数
            String httpPost2 = HttpUtil.doPost(SwiftpassConfig.req_url, orderMap, "utf-8");
            Map map = null;
            if (httpPost2 != null) {
                map = JSONObject.parseObject(httpPost2, Map.class);
            } else {
                return ReturnUtil.returnFail("下单参数有误！", 1005);
            }
            logger.error("威富通下单返回信息", httpPost2);
            // 拼接返回信息
            Map<String, Object> resultMap = new HashMap<String, Object>();
            resultMap.put("pay_info", map.get("payurl"));
            resultMap.put("code_url", map.get("payurl"));
            return ReturnUtil.returnInfo(DataProcessUtil.removeNullMap(resultMap));
        } catch (Exception ex) {
            logger.error("威富通下单错误：", ex);
            return ReturnUtil.returnFail("下单参数有误！", 1005);
        }
    }

    public boolean getAutograph(Map<String, String> map) throws Exception {
        logger.info("回调信息-威富通：" + map);
        if (map != null) {
            if (map.containsKey("sign")) {
                boolean result = false;
                if (map.containsKey("sign")) {
                    String sign = map.get("sign");
                    map.remove("sign");
                    StringBuilder buf = new StringBuilder((map.size() + 1) * 10);
                    SwiftpassPayCore.buildPayParams(buf, map, false);
                    String preStr = buf.toString();
                    String signRecieve = MD5.sign(preStr, "&key=" + SwiftpassConfig.Key, "utf-8");
                    result = sign.equalsIgnoreCase(signRecieve);
                }
                if (!result) {
                    logger.info("SwiftpassPay验证签名不通过");
                } else {
                    return setSuccPayInfo(map);
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
    public boolean setSuccPayInfo(Map<String, String> vo) {
        Integer payType = 4;
        String orderNo = vo.get("extra");
        Float fee = Float.parseFloat(vo.get("amount")) / 100;
        // 修改订单信息
        Date date = new Date();
        PayChannel payChannel = new PayChannel();
        payChannel.setTradeNo(orderNo); // 订单号(自己 )
        payChannel.setChannelNo(vo.get("order")); //
        payChannel.setWechatNo(vo.get("order")); // 微信订单号
        payChannel.setpType(payType); // 支付方式
        payChannel.setpState(1); // 支付状态为成功
        payChannel.setpFee(fee); // 金额
        payChannel.setModifiedTime(date);
        Date pDate = new Date();
        payChannel.setpTime(pDate);
        // 修改数据库值
        boolean bool = false;
        try {
            bool = transChannelService.updateByPrimaryKeySelective(payChannel);
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
    public static Map<String, String> paraFilter(Map<String, Object> sArray) {
        Map<String, String> result = new HashMap<String, String>(sArray.size());
        if (sArray == null || sArray.size() <= 0) {
            return result;
        }
        for (String key : sArray.keySet()) {
            String value = (String) sArray.get(key);
            if (value == null || value.equals("") || key.equalsIgnoreCase("sign")) {
                continue;
            }
            result.put(key, value);
        }
        return result;
    }

    public static String payParamsToString(Map<String, String> payParams) {
        return payParamsToString(payParams, false);
    }

    public static String payParamsToString(Map<String, String> payParams, boolean encoding) {
        return payParamsToString(new StringBuilder(), payParams, encoding);
    }

    public static String payParamsToString(StringBuilder sb, Map<String, String> payParams, boolean encoding) {
        buildPayParams(sb, payParams, encoding);
        return sb.toString();
    }

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
            } else if ("pay.tenpay.native".endsWith(payType)) { // qq钱包扫码
                index = Constant.PAYTYPE_QS;
            } else if ("pay.tenpay.jspay".endsWith(payType)) { // qq钱包公众号
                index = Constant.PAYTYPE_QP;
            }
        }
        return index;
    }
}