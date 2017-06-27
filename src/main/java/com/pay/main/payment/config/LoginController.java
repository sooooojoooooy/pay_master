package com.pay.main.payment.config;

import com.pay.main.payment.service.IUserLoginService;
import com.pay.main.payment.util.CaptchaUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

@Controller
@RequestMapping("login")
public class LoginController {
	private static Log logger = LogFactory.getLog(LoginController.class);
	@Autowired
	IUserLoginService userLoginService;
	@RequestMapping(value = "onLogin", method = RequestMethod.POST, produces = "application/json; charset=utf-8")
	public @ResponseBody
	Map<String, Object> onLogin(@RequestParam String username, String password, String captcha, HttpServletRequest request) {
		return userLoginService.getLoginAccount(request, username, password, captcha);
	}
	/**
	 * 获取验证码
	 * 
	 * @param request
	 * @param response
	 */
	@RequestMapping(value = "onCaptcha", method = RequestMethod.GET)
	public void onCaptcha(HttpServletRequest request, HttpServletResponse response) {
		try {
			BufferedImage image = CaptchaUtils.getCaptcha(request, response);
			ImageIO.write(image, "JPEG", response.getOutputStream());
		} catch (ServletException | IOException ex) {
			logger.error("获取验证码：", ex);
		}
	}
}