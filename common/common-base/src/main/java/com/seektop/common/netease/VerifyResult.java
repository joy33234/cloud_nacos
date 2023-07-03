package com.seektop.common.netease;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class VerifyResult implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 486373488950543482L;

	/**
	 * 异常代号
	 */
	private int error;

	/**
	 * 错误描述信息
	 */
	private String msg;

	/**
	 * 二次校验结果
	 *
	 * true:校验通过 false:校验失败
	 */
	private boolean result;

	/**
	 * 短信上行发送的手机号码 仅限于短信上行的验证码类型
	 */
	private String phone;

	/**
	 * 额外字段
	 */
	private String extraData;

	public static VerifyResult fakeNormalResult(String resp) {
		VerifyResult result = new VerifyResult();
		result.setResult(false);
		result.setError(0);
		result.setMsg(resp);
		result.setPhone("");
		return result;
	}

}