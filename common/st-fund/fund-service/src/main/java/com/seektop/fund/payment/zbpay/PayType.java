package com.seektop.fund.payment.zbpay;

import java.util.HashMap;
import java.util.Map;

public enum PayType {
	
	WEIXIN_SCAN("1000","微信扫码"),
	WEIXIN_H5("1002","微信H5"),
	ZHIFUBAO_SCAN("1003","支付宝扫码"),
	ZHIFUBAO_H5("1004","支付宝H5"),
	QQ_SCAN("1005","QQ钱包扫码"),
	QQ_H5("1006","QQ钱包H5"),
	JD_SCAN("1007","京东钱包扫码"),
	JD_H5("1008","京东钱包H5"),
	UNION_SCAN("1009","银联扫码"),
	
	UNPAID("0","未支付"),
	SUCCESS("1","支付成功"),
	FAIL("2","失败");
	
	private String code;
	private String message;
	
	private static Map<String, PayType> maps = new HashMap<>();
	static {
		for (PayType item : PayType.values()) {
			maps.put(item.getCode(), item);
		}
	}
	
	private PayType (String code,String message) {
		this.code = code;
		this.message = message;
	}
	
	public String getCode() {
		return code;
	}
	
	public String getMessage() {
		return message;
	}
	
	public static String getMessage(String code) {
		PayType item = maps.get(code);
		if (item == null) {
			return null;
		}
		return item.getMessage();
	}
}
