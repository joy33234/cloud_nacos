package com.seektop.common.utils;

import com.alibaba.fastjson.JSONObject;

import java.util.Map;

/**
 * Created by ken on 2018/5/14.
 */
public class HtmlTemplateUtils {
    public static String getPost(String payUrl, JSONObject paramJson) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("<html>");
            sb.append("<head>");
            sb.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
            sb.append("</head>");
            sb.append("<body>");
            sb.append("<form id='payform' name='payform' action='" + payUrl + "' method='post'>");
            for (String key : paramJson.keySet()) {
                sb.append("<input type='hidden' name='" + key + "' value='" + paramJson.get(key) + "'/>");
            }
            sb.append("</form>");
            sb.append("<script>document.getElementById('payform').submit();</script>");
            sb.append("</body>");
            sb.append("</html>");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            sb = null;
        }

    }

    public static String getPost(String payUrl, Map<String, String> paramJson) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("<html>");
            sb.append("<head>");
            sb.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
            sb.append("</head>");
            sb.append("<body>");
            sb.append("<form id='payform' name='payform' action='" + payUrl + "' method='post'>");
            for (String key : paramJson.keySet()) {
                sb.append("<input type='hidden' name='" + key + "' value='" + paramJson.get(key) + "'/>");
            }
            sb.append("</form>");
            sb.append("<script>document.getElementById('payform').submit();</script>");
            sb.append("</body>");
            sb.append("</html>");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            sb = null;
        }

    }



    public static String getPost1(String payUrl, Map<String, Object> paramJson) {
        StringBuilder sb = new StringBuilder();
        try {
            sb.append("<html>");
            sb.append("<head>");
            sb.append("<meta http-equiv='Content-Type' content='text/html; charset=UTF-8'>");
            sb.append("</head>");
            sb.append("<body>");
            sb.append("<form id='payform' name='payform' action='" + payUrl + "' method='post'>");
            for (String key : paramJson.keySet()) {
                sb.append("<input type='hidden' name='" + key + "' value='" + paramJson.get(key) + "'/>");
            }
            sb.append("</form>");
            sb.append("<script>document.getElementById('payform').submit();</script>");
            sb.append("</body>");
            sb.append("</html>");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            sb = null;
        }

    }
    public static String getQRCode(String qrCode) {
        StringBuilder sb = new StringBuilder();
        
        try {
            sb.append("<html>");
            sb.append("<head><meta http-equiv='Content-Type' content='text/html; charset=GBK'></head>");
            sb.append(
                    "<script type='text/javascript' src='https://cdn.staticfile.org/jquery/2.1.1/jquery.min.js'></script>");
            sb.append(
                    "<script type='text/javascript' src='https://cdn.staticfile.org/jquery.qrcode/1.0/jquery.qrcode.min.js'></script>");
            sb.append("<body style=\"margin: 0px; background: #0e0e0e;display: flex;align-items: center;\">");
            sb.append("<div id='qrcode' style=\"margin: auto;width: 385px;height: 385px;border:8px solid #fff;\"></div>");
            sb.append("</body>");
            sb.append("<script>");
            sb.append("$('#qrcode').qrcode({text:'").append(qrCode).append("',width:385,height:385});");
            sb.append("</script>");
            sb.append("</html>");
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            sb = null;
        }
    }
}
