package com.seektop.fund.payment.zbpay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Submit {

	public static String buildRequest(Map<String, String> param, String actionUrl, String methodType) {

		List<String> keys = new ArrayList<String>(param.keySet());

		StringBuffer sbHtml = new StringBuffer();

		sbHtml.append("<form id=\"frm1\" name=\"frm1\" action=\"" + actionUrl + "\" method=\"" + methodType + "\">");

		for (int i = 0; i < keys.size(); i++) {
			String name = (String) keys.get(i);
			String value = (String) param.get(name);

			sbHtml.append("<input type=\"hidden\" name=\"" + name + "\" value=\"" + value + "\"/>");
		}

		// submit按钮控件请不要含有name属性
		sbHtml.append("<input type=\"submit\" value=\"确认付款\" style=\"display:none;\"></form>");
		sbHtml.append("<script>setTimeout(\"document.getElementById('frm1').submit();\",100);</script>");

		return sbHtml.toString();
	}
}
