package com.seektop.fund.payment.xinhuipay.common.util;

import lombok.extern.log4j.Log4j;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

@Log4j
public class XinhuiHttpUtils {

    public static String doPost(String url, String param, String charset) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        try {
            if (param!=null) {
                httpPost.setEntity(new StringEntity(param, charset));
            }

            HttpResponse response = httpClient.execute(httpPost);

            log.info(response.toString());
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity, charset);
        } catch (Exception e) {
            log.error(e.getMessage());
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
            }
        }
        return null;
    }
}
