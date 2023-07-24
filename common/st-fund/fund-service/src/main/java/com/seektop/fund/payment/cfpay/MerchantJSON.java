package com.seektop.fund.payment.cfpay;


import java.util.Map;

public class MerchantJSON {
	public static String encode(Map<String, Object> data) {
		StringBuilder json = new StringBuilder();

		json.append("{");
		for (Object key : data.keySet()) {
			json.append(getJSONValue((String) key) + ":");
			json.append(getJSONValue(data.get(key).toString()));
			json.append(",");
		}
		json.deleteCharAt(json.length() - 1);
		json.append("}");

		return json.toString();
	}

	private static String getJSONValue(String s) {
		return "\"" + StringExtension.utf8ToUnicode(addSlashes(s)) + "\"";
	}

	private static String addSlashes(String s) {
		s = s.replaceAll("\\\\", "\\\\\\\\");
		s = s.replaceAll("\\n", "\\\\n");
		s = s.replaceAll("\\r", "\\\\r");
		s = s.replaceAll("\\00", "\\\\0");
		s = s.replaceAll("'", "\\\\'");

		return s;
	}
}
