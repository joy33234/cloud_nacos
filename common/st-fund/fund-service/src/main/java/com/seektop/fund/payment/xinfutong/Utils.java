package com.seektop.fund.payment.xinfutong;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Demo利用的工具
 */
public final class Utils {
    /**
     *  日期转换
     */
    public static String createDate(){
        Date date =  new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String day = sdf.format(date);
        return day;
    }

    /**
     * 生成支付商户订单
     */
    public static String createPayNo(){
        long data = System.currentTimeMillis();
        String orderNo = "RM" + data;
        return orderNo;
    }
    /**
     * 生成代付商户订单
     */
    public static String createAgentPayNo(){
        long data = System.currentTimeMillis();
        String agentPayNo = "RX" + data;
        return agentPayNo;
    }
    /**
     * 遍历map
     */
    public static void iteraor(Map<String,String> map){
        for(Map.Entry<String,String> entry:map.entrySet()) {
            Log.Write(entry.getKey() + "=" + entry.getValue());
        }
    }
    /**
     * 拼接Map
     */
    public static String createLinkString(Map<String, String> params, boolean encode) {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);//不按首字母排序, 需要按首字母排序请打开
        StringBuilder prestrSB = new StringBuilder();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);
            if (encode) {
                try {
                    value = URLEncoder.encode(value, "GBK");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            if (i == keys.size() - 1) {// 拼接时，不包括最后一个&字符
                prestrSB.append(key).append("=").append(value);
            } else {
                prestrSB.append(key).append("=").append(value).append("&");
            }
        }
        return prestrSB.toString();
    }
}