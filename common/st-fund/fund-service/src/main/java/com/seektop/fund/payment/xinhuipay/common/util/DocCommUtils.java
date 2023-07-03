package com.seektop.fund.payment.xinhuipay.common.util;

import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeMap;

/**
 * @Description <p>
 *              支付网关签名验签工具类
 *              </p>
 * @author ty
 * @since 2016年7月7日
 * @version 1.0.0
 * @ModifyBy
 *
 */
public class DocCommUtils {
	private static Logger logger = Logger.getLogger(DocCommUtils.class.getName());

	public static TreeMap<String, String> convertDocToSignMap(Document document) {
		logger.info("convert document to sign or check map!");

		TreeMap<String, String> signTreeMap = new TreeMap<String, String>();

		// 解析报文头
		logger.info("analysis message header data");
		signTreeMap.put("verNo", getTxnEnvHeadField(document, "verNo"));
		signTreeMap.put("sndChnlNo", getTxnEnvHeadField(document, "sndChnlNo"));
		signTreeMap.put("rcvChnlNo", getTxnEnvHeadField(document, "rcvChnlNo"));
		signTreeMap.put("txnNo", getTxnEnvHeadField(document, "txnNo"));
		signTreeMap.put("chnlDt", getTxnEnvHeadField(document, "chnlDt"));
		signTreeMap.put("chnlTm", getTxnEnvHeadField(document, "chnlTm"));
		signTreeMap.put("hostDt", getTxnEnvHeadField(document, "hostDt"));
		signTreeMap.put("hostTm", getTxnEnvHeadField(document, "hostTm"));
		signTreeMap.put("chnlSeq", getTxnEnvHeadField(document, "chnlSeq"));
		signTreeMap.put("hostSeq", getTxnEnvHeadField(document, "hostSeq"));
		signTreeMap.put("rspNo", getTxnEnvHeadField(document, "rspNo"));
		signTreeMap.put("rspMsg", getTxnEnvHeadField(document, "rspMsg"));

		// ....
		// 解析报文体
		logger.info("analysis message body data");
		Element bodyEle = (Element) document
				.selectSingleNode("//msg/main_data");
		if (bodyEle != null) {
			Iterator<Element> bodyIterator = bodyEle.elementIterator();
			if (bodyIterator != null) {
				while (bodyIterator.hasNext()) {
					Element bodys = bodyIterator.next();
					if (bodys.getName() != "List") {
						signTreeMap.put(bodys.getName(), bodys.getText());
					}
				}
			}
		}

		return signTreeMap;
	}

	/**
	 * <p>
	 * Description:请求报文的报文头指定字段的值
	 * </p>
	 * 
	 * @param document
	 * @param name
	 * @return
	 */
	public static String getTxnEnvHeadField(Document document,
                                            String name) {
		Element fieldNamElement = (Element) document.selectSingleNode("//msg/comm_head/" + name);
		// return fieldNamElement==null?null:fieldNamElement.getStringValue();
		if (fieldNamElement == null) {
			return null;
		}
		return fieldNamElement.getStringValue() == null ? null
				: fieldNamElement.getStringValue().trim();
	}

	public static String packageSignatureStr(TreeMap<String, String> signTreeMap) {
		String signStr = "";
		Set<String> keySet = signTreeMap.keySet();
		for (String key : keySet) {
			if (signTreeMap.get(key) != null
					&& !"".equals(signTreeMap.get(key))) {
				signStr = signStr + "&" + key + "=" + signTreeMap.get(key);
			}
		}

		if (signStr.startsWith("&")) {
			signStr = signStr.substring(1);
		}

		logger.info("signStr: " + signStr);
		return signStr;
	}
	
    /**
     * @Description <p> 设置标签内容
     * </p>
     */
    public static void setTextValue(Document resDoc, String pathStr, String valueStr) {
    	if (resDoc == null) {
    		logger.error("resDoc is null");
    		return;
		}
    	if (pathStr == null) {
    		logger.error("pathStr is null");
    		return;
		}
    	if (valueStr == null) {
    		logger.error("valueStr is null");
    		return;
		}
    	Element fieldNamElement = (Element) resDoc.selectSingleNode(pathStr);
    	if (fieldNamElement != null) {
			fieldNamElement.clearContent();
    		fieldNamElement.setText(valueStr);
		}else {
			int index = pathStr.lastIndexOf("/");
			if (index > 1) {
				String subPath = pathStr.substring(0, index);
				String eleName = pathStr.substring(index + 1);
				fieldNamElement = (Element) resDoc.selectSingleNode(subPath);
				if (fieldNamElement != null) {
					fieldNamElement = fieldNamElement.addElement(eleName);
					fieldNamElement.clearContent();
		    		fieldNamElement.setText(valueStr);
				}else {
					logger.error(subPath + " is not exist");
				}
			}else {
				logger.error(pathStr + " is not exist");
			}
		}
	}

}
