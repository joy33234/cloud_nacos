package com.seektop.common.http;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.rest.Result;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.*;

@Slf4j
public class HttpUtils {

    /**
     *  默认五秒超时时间
     * @param url
     * @param params
     * @return
     * @throws Exception
     */
    public static String doJsonPost(String url, Object params) throws Exception {
        return doJsonPost(url, params, "UTF-8",5000);
    }

    /**
     *  默认UTF-8字符集
     * @param url
     * @param params
     * @param timeout
     * @return
     * @throws Exception
     */
    public static String doJsonPost(String url, Object params,int timeout) throws Exception {
        return doJsonPost(url, params, "UTF-8",timeout);
    }
    public static String doJsonPost(String url, Object params, String charset,int timeout) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build();//设置请求和传输超时时间
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(requestConfig);
        httpPost.addHeader("content-type", "application/json");
        String entry = JSON.toJSONString(params);
        httpPost.setEntity(new StringEntity(entry, charset));
        CloseableHttpResponse response = httpclient.execute(httpPost);
        String resStr = EntityUtils.toString(response.getEntity(), charset);
        return resStr;
    }

    public static String doJsonPost(String url, JSONObject json, String charset) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        httpPost.addHeader("content-type", "application/json");
        httpPost.setEntity(new StringEntity(json.toJSONString(), charset));
        CloseableHttpResponse response2 = httpclient.execute(httpPost);
        String resStr = EntityUtils.toString(response2.getEntity(), charset);
        return resStr;
    }

    public static String doJsonPost(String url, JSONObject json, Map<String, String> headers, String charset) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        if (headers != null && !headers.isEmpty()) {
            Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                httpPost.addHeader(entry.getKey(), entry.getValue());
            }
        }
        httpPost.addHeader("content-type", "application/json");
        httpPost.setEntity(new StringEntity(json.toJSONString(), charset));
        CloseableHttpResponse response2 = httpclient.execute(httpPost);
        String resStr = EntityUtils.toString(response2.getEntity(), charset);
        return resStr;
    }

    public static String doPost(String url, int timeout, Map<String, Object> params) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        RequestConfig config = getHttpConfig(timeout);
        HttpPost httpPost = new HttpPost(url);
        httpPost.setConfig(config);
        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        Iterator<Map.Entry<String, Object>> it = params.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            urlParameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue().toString()));
        }
        try {
            HttpEntity postParams = new UrlEncodedFormEntity(urlParameters);
            httpPost.setEntity(postParams);

            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();

            return response.toString();
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



    public static String doPost(String url, Map<String, String> params) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
        Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            urlParameters.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        try {
            HttpEntity postParams = new UrlEncodedFormEntity(urlParameters);
            httpPost.setEntity(postParams);

            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();

            return response.toString();
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

    public static String doPost(String url, Map<String, String> params, String charset) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        List<NameValuePair> nvps = new ArrayList<>();
        Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        }
        try {
            UrlEncodedFormEntity uefEntity = new UrlEncodedFormEntity(nvps, charset);
            httpPost.setEntity(uefEntity);

            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();

            return response.toString();
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


    public static String doPost(String url, Map<String, String> params, Map<String, String> headers, String charset) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        if (headers != null && !headers.isEmpty()) {
            Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                httpPost.addHeader(entry.getKey(), entry.getValue());
            }
        }
        List<NameValuePair> nvps = new ArrayList<>();
        if (params != null && !params.isEmpty()) {
            Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                nvps.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
        }
        try {
            UrlEncodedFormEntity uefEntity = null;
            if (charset != null) {
                uefEntity = new UrlEncodedFormEntity(nvps, charset);
            } else {
                uefEntity = new UrlEncodedFormEntity(nvps);
            }
            httpPost.setEntity(uefEntity);

            CloseableHttpResponse httpResponse = httpClient.execute(httpPost);

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();

            return response.toString();
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


    /**
     * temp method get request ip address 20131009
     *
     * @return
     */
    public static String getRequestIp(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");

        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            return ip.split(",")[0].trim();
        }
        return ip;
    }

    public static String getUrl(HttpServletRequest request) {
        String url = request.getHeader("Referer");
        if (null == url || "".equals(url)) {
            StringBuffer urlTmp = request.getRequestURL();
            url = urlTmp.delete(urlTmp.length() - request.getRequestURI().length(), urlTmp.length()).append("/").toString();
        }
        return url;
    }

    public static String getUrlV2(HttpServletRequest request) {
        String url = request.getHeader("Referer");
        // 移动端没有传Referer，用的Host，这里做兼容处理
        if (StringUtils.isEmpty(url)) {
            url = request.getHeader("Host");
        }
        // 如果任然为空，继续拿Origin
        if (StringUtils.isEmpty(url)) {
            url = request.getHeader("Origin");
        }
        // 如果任然为空，通过Request拿到请求的url
        if (StringUtils.isEmpty(url)) {
            StringBuffer urlTmp = request.getRequestURL();
            url = urlTmp.delete(urlTmp.length() - request.getRequestURI().length(), urlTmp.length()).append("/").toString();
        }
        // 没拿到URL，直接返回空字符串
        if (StringUtils.isEmpty(url)) {
            return "";
        }
        // 处理待协议的情况(不能有协议)
        if (url.indexOf("://") > -1) {
            try {
                URL uri = new URL(url);
                url = uri.getHost();
            } catch (Exception ex) {
                log.error("HttpUtils getUrl error", ex);
            }
        }
        return url.toLowerCase();
    }

    public static Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> keys = request.getHeaderNames();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            String value = request.getHeader(key);
            if (StringUtils.isNotEmpty(value)) {
                headers.put(key.trim(), value.trim());
            }
        }
        return headers;
    }

    public static Map<String, String> getParameters(HttpServletRequest request) {
        Map<String, String> parameters = new HashMap<>();
        Enumeration<String> keys = request.getParameterNames();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if ("password".equals(key.trim())) {
                continue;
            }
            String value = request.getParameter(key);
            if (StringUtils.isNotEmpty(value)) {
                parameters.put(key.trim(), value.trim());
            }
        }
        return parameters;
    }

    public static String doGet(String url, Map<String, String> params) {
        StringBuilder urlTmp = new StringBuilder(url);
        urlTmp.append("?");
        Iterator<Map.Entry<String, String>> it = params.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            try {
                urlTmp.append(entry.getKey()).append("=").append(java.net.URLEncoder.encode(String.valueOf(entry.getValue()), "utf-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return JSON.toJSONString(Result.newBuilder().fail().setMessage("URL构建异常").build());
            }
            urlTmp.append("&");
        }
        return doGet(urlTmp.toString());
    }

    public static String doGet(String url) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);

            BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.getEntity().getContent()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = reader.readLine()) != null) {
                response.append(inputLine);
            }
            reader.close();

            return response.toString();
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

    public static String doGet(String url, int timeout, String charset) {
        CloseableHttpClient httpClient = HttpClients.createDefault();
        HttpGet httpGet = new HttpGet(url);
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(timeout).setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout).build();
        httpGet.setConfig(requestConfig);
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpGet);
            return EntityUtils.toString(httpResponse.getEntity(), charset);
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

    public static String doTextPost(String url, String encodeJson, Map<String, String> headers, String charset) throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);
        if (headers != null && !headers.isEmpty()) {
            Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, String> entry = it.next();
                httpPost.addHeader(entry.getKey(), entry.getValue());
            }
        }
        httpPost.setEntity(new StringEntity(encodeJson, charset));
        CloseableHttpResponse response2 = httpclient.execute(httpPost);
        String resStr = EntityUtils.toString(response2.getEntity(), charset);
        return resStr;
    }

    public static String doPut(String urlPath, String data, String charSet, String type) {
        return doPut(urlPath, data, 2000000, charSet, type);
    }

    public static String doPut(String urlPath, String data,int timeout, String charSet, String type) {
        String result = null;
        URL url = null;
        HttpURLConnection httpurlconnection = null;
        try {
            url = new URL(urlPath);
            httpurlconnection = (HttpURLConnection) url.openConnection();
            httpurlconnection.setDoInput(true);
            httpurlconnection.setDoOutput(true);
            httpurlconnection.setConnectTimeout(timeout);// 设置连接主机超时（单位：毫秒）
            httpurlconnection.setReadTimeout(timeout);// 设置从主机读取数据超时（单位：毫秒）

//            if (header != null) {
//                for (int i = 0; i < header.length; i++) {
//                    String[] content = header[i].split(":");
//                    log.info("content[0]==="+content[0]);
//                    log.info("content[1]==="+content[1]);
//                    httpurlconnection.setRequestProperty(content[0], content[1]);
//                }
//            }
            if(type!=null){
                httpurlconnection.setRequestProperty("User-Agent", type);
            }
            httpurlconnection.setRequestMethod("PUT");
            httpurlconnection.setRequestProperty("Content-Type", "application/json");


            if (StringUtils.isNotBlank(data)) {
                httpurlconnection.getOutputStream().write(data.getBytes("UTF-8"));
            }
            httpurlconnection.getOutputStream().flush();
            httpurlconnection.getOutputStream().close();
            int code = httpurlconnection.getResponseCode();

            if (code == 200) {
                DataInputStream in = new DataInputStream(httpurlconnection.getInputStream());
                int len = in.available();
                byte[] by = new byte[len];
                in.readFully(by);
                if (StringUtils.isNotBlank(charSet)) {
                    result = new String(by, Charset.forName(charSet));
                } else {
                    result = new String(by);
                }
                in.close();
            } else {
                log.error("请求地址：" + urlPath + "返回状态异常，异常号为：" + code);
            }
        } catch (Exception e) {
            log.error("访问url地址：" + urlPath + "发生异常", e);
        } finally {
            url = null;
            if (httpurlconnection != null) {
                httpurlconnection.disconnect();
            }
        }
        return result;
    }

    private static RequestConfig getHttpConfig(int timeout){
        RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(timeout).setSocketTimeout(timeout).build();//设置请求和传输超时时间
        return requestConfig;
    }

    public static String post(String url, Map< String, String > paramsMap) {
        CloseableHttpClient client = HttpClients.createDefault();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000).setConnectionRequestTimeout(1000)
                .setSocketTimeout(5000).build();
        String responseText = "";
        CloseableHttpResponse response = null;
        try {
            HttpPost method = new HttpPost(url);
            method.setConfig(requestConfig);
            if (paramsMap != null) {
                List<NameValuePair> paramList = new ArrayList<NameValuePair >();
                for (Map.Entry < String, String > param: paramsMap.entrySet()) {
                    org.apache.http.NameValuePair pair = new BasicNameValuePair(param.getKey(),
                            param.getValue());
                    paramList.add(pair);
                }
                method.setEntity(new UrlEncodedFormEntity(paramList,
                        "utf-8"));
            }
            response = client.execute(method);
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                responseText = EntityUtils.toString(entity, "utf-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                response.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return responseText;
    }
}
