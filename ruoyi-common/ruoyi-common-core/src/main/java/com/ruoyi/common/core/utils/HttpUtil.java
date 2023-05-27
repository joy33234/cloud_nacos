package com.ruoyi.common.core.utils;


import com.alibaba.fastjson.JSON;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

public class HttpUtil {
    private static final int OK = 200;

    private static CloseableHttpClient httpClient;

    private static RequestConfig requestConfig = RequestConfig.custom().setSocketTimeout(30000).setConnectTimeout(60000).build();

    static {
        SSLConnectionSocketFactory sslsf = createSSLConnSocketFactory();
        Registry<ConnectionSocketFactory> socketRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory())
                .register("https", sslsf)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(socketRegistry);
        cm.setMaxTotal(400);
        cm.setDefaultMaxPerRoute(50);
        cm.closeIdleConnections(60L, TimeUnit.SECONDS);
        if (sslsf != null) {
            httpClient = HttpClients.custom().setRetryHandler((HttpRequestRetryHandler)new MyHttpRequestRetryHandler()).setSSLSocketFactory((LayeredConnectionSocketFactory)sslsf).setConnectionManager((HttpClientConnectionManager)cm).disableCookieManagement().setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.101 Safari/537.36").setDefaultRequestConfig(requestConfig).build();
        } else {
            httpClient = HttpClients.custom().setRetryHandler((HttpRequestRetryHandler)new MyHttpRequestRetryHandler()).setConnectionManager((HttpClientConnectionManager)cm).disableCookieManagement().setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.101 Safari/537.36").setDefaultRequestConfig(requestConfig).build();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                HttpUtil.shutdown();
            }
        }));
    }

    private static SSLConnectionSocketFactory createSSLConnSocketFactory() {
        SSLConnectionSocketFactory sslsf = null;
        try {
            SSLContext sslContext = (new SSLContextBuilder()).loadTrustMaterial(null, new TrustStrategy() {
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            }).build();
            sslsf = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sslsf;
    }

    public static CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    protected static class MyHttpRequestRetryHandler extends DefaultHttpRequestRetryHandler {
        public MyHttpRequestRetryHandler() {
            super(10, false, Arrays.asList(new Class[] { InterruptedIOException.class, SocketTimeoutException.class, SSLException.class }));
        }
    }

    protected static class BlankResponseException extends RuntimeException {}

    protected static class FailResponseException extends RuntimeException {}

    public static void shutdown() {
        if (null != httpClient) {
            try {
                httpClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            httpClient = null;
        }
    }

    public static String post(String url, Map<String, String> params) {
        return post(url, params, false);
    }

    public static String post(String url, Map<String, String> params, boolean erreturn) {
        String result = post(url, params, new DefaultStringHandler(erreturn));
        return (result == null) ? "" : result;
    }

    public static <T> T post(String url, Map<String, String> params, HandlerFunction<T> handlerFunction) {
        List<NameValuePair> args = new ArrayList<>();
        if (null != params && params.size() > 0)
            for (Map.Entry<String, String> entry : params.entrySet())
                args.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        return postWithBody(url, (HttpEntity)new UrlEncodedFormEntity(args, Consts.UTF_8), handlerFunction);
    }

    public static String postWithBody(String url, String body, String contentType) {
        return postWithBody(url, body, contentType, false);
    }

    public static String postWithBody(String url, Map<String, String> headers, String body, String contentType) {
        return postWithBody(url, headers, body, contentType, false);
    }

    public static String postWithBody(String url, String body, String contentType, boolean erreturn) {
        String result = postWithBody(url, (Map<String, String>)null, body, contentType, new DefaultStringHandler(erreturn));
        return (result == null) ? "" : result;
    }

    public static String postWithBody(String url, Map<String, String> headers, String body, String contentType, boolean erreturn) {
        String result = postWithBody(url, headers, body, contentType, new DefaultStringHandler(erreturn));
        return (result == null) ? "" : result;
    }

    public static <T> T postWithBody(String url, Map<String, String> headers, String body, String contentType, HandlerFunction<T> handlerFunction) {
        HttpPost httpPost = new HttpPost(url);
        if (StringUtils.isNotBlank(body)) {
            StringEntity se = new StringEntity(body, Consts.UTF_8);
            se.setContentType(contentType);
            httpPost.setEntity((HttpEntity)se);
        }
        if (headers != null)
            for (Map.Entry<String, String> header : headers.entrySet())
                httpPost.setHeader(header.getKey(), header.getValue());
        try (CloseableHttpResponse response = getHttpClient().execute((HttpUriRequest)httpPost)) {
            return handlerFunction.handleResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return handlerFunction.handleException(e);
        }
    }

    public static <T> T postWithBody(String url, HttpEntity body, HandlerFunction<T> handlerFunction) {
        HttpPost httpPost = new HttpPost(url);
        httpPost.setEntity(body);
        try (CloseableHttpResponse response = getHttpClient().execute((HttpUriRequest)httpPost)) {
            return handlerFunction.handleResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return handlerFunction.handleException(e);
        }
    }

    public static <T> T postWithBody(String url, Map<String, String> headers, HttpEntity body, HandlerFunction<T> handlerFunction) {
        HttpPost httpPost = new HttpPost(url);
        if (headers != null)
            for (Map.Entry<String, String> header : headers.entrySet())
                httpPost.setHeader(header.getKey(), header.getValue());
        httpPost.setEntity(body);
        try (CloseableHttpResponse response = getHttpClient().execute((HttpUriRequest)httpPost)) {
            return handlerFunction.handleResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return handlerFunction.handleException(e);
        }
    }

    public static String get(String url, Map<String, String> params) {
        return get(url, params, false);
    }

    public static String getOkx(String url, Map<String, String> params, Map<String, String> account) {
        Map<String, String> head = getOkxHead("GET", url, "", account);
        url = "https://www.okx.com" + url;
        return get(url, head, params, new DefaultStringHandler(true));
    }

    public static String postOkx(String url, Map<String, String> params, Map<String, String> account) {
        Map<String, String> head = getOkxHead("POST", url, JSON.toJSONString(params), account);
        url = "https://www.okx.com" + url;
        List<NameValuePair> args = new ArrayList<>();
        if (null != params && params.size() > 0)
            for (Map.Entry<String, String> entry : params.entrySet())
                args.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        return postWithBody(url, head, JSON.toJSONString(params), "application/json");
    }

    public static Map<String, String> getOkxHead(String method, String path, String body, Map<String, String> account) {
        Map<String, String> head = new HashMap<>();
        head.put("OK-ACCESS-KEY", account.get("apikey"));
        head.put("OK-ACCESS-TIMESTAMP", DateUtil.formatBTITime(new Date()));
        head.put("OK-ACCESS-PASSPHRASE", account.get("password"));
        head.put("OK-ACCESS-SIGN", SignUtils.getSign((String)head.get("OK-ACCESS-TIMESTAMP") + method + path + body, account.get("secretkey")));
        return head;
    }



    public static String getOpenAi(String url, Map<String, String> params, String key) {
        Map<String, String> head = getOpenAiHead("GET", url, "", key);
        url = "https://api.openai.com" + url;
        return get(url, head, params, new DefaultStringHandler(true));
    }

    public static String postOpenAi(String url, Map<String, String> params, String key) {
        Map<String, String> head = getOpenAiHead("POST", url, JSON.toJSONString(params), key);
        url = "https://api.openai.com" + url;
        List<NameValuePair> args = new ArrayList<>();
        if (null != params && params.size() > 0)
            for (Map.Entry<String, String> entry : params.entrySet())
                args.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
        return postWithBody(url, head, JSON.toJSONString(params), "application/json");
    }

    public static Map<String, String> getOpenAiHead(String method, String path, String body, String key) {
        Map<String, String> head = new HashMap<>();
        head.put("Content-Type", "application/json");
        head.put("Authorization", "Bearer " + key);
        return head;
    }

    public static String get(String url, Map<String, String> params, boolean erreturn) {
        String result = get(url, params, new DefaultStringHandler(erreturn));
        return (result == null) ? "" : result;
    }

    public static String get(String url, Map<String, String> headers, Map<String, String> params) {
        String result = get(url, headers, params, new DefaultStringHandler(true));
        return (result == null) ? "" : result;
    }

    public static <T> T get(String url, Map<String, String> params, HandlerFunction<T> handlerFunction) {
        return get(url, null, params, handlerFunction);
    }

    public static <T> T get(String url, Map<String, String> headers, Map<String, String> params, HandlerFunction<T> handlerFunction) {
        StringBuilder getUrl = new StringBuilder(url);
        int i = 1, size = (params == null) ? 0 : params.size();
        if (size > 0) {
            getUrl.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String[] multiValues = ((String)entry.getValue()).split("@");
                if (multiValues.length > 1) {
                    for (int j = 0; j < multiValues.length; j++) {
                        getUrl.append(entry.getKey()).append("=").append(multiValues[j]);
                        if (j + 1 < multiValues.length)
                            getUrl.append("&");
                    }
                } else {
                    getUrl.append(entry.getKey()).append("=").append(entry.getValue());
                }
                if (i != size)
                    getUrl.append("&");
                i++;
            }
        }
        HttpGet httpGet = new HttpGet(getUrl.toString());
        if (headers != null)
            for (String key : headers.keySet())
                httpGet.addHeader(key, headers.get(key));
        try (CloseableHttpResponse response = getHttpClient().execute((HttpUriRequest)httpGet)) {
            return handlerFunction.handleResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return handlerFunction.handleException(e);
        }
    }

    public static String delete(String url, Map<String, String> headers, Map<String, String> params) {
        StringBuilder getUrl = new StringBuilder(url);
        DefaultStringHandler handlerFunction = new DefaultStringHandler();
        int i = 1, size = (params == null) ? 0 : params.size();
        if (size > 0) {
            getUrl.append("?");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                getUrl.append(entry.getKey()).append("=").append(entry.getValue());
                if (i != size)
                    getUrl.append("&");
                i++;
            }
        }
        HttpDelete httpGet = new HttpDelete(getUrl.toString());
        if (headers != null)
            for (String key : headers.keySet())
                httpGet.addHeader(key, headers.get(key));
        try (CloseableHttpResponse response = getHttpClient().execute((HttpUriRequest)httpGet)) {
            return handlerFunction.handleResponse(response);
        } catch (Exception e) {
            e.printStackTrace();
            return handlerFunction.handleException(e);
        }
    }

    public static class DefaultStringHandler implements HandlerFunction<String> {
        private boolean erreturn = false;

        public DefaultStringHandler(boolean erreturn) {
            this.erreturn = erreturn;
        }

        public String handleResponse(CloseableHttpResponse response) {
            if (response == null)
                throw new HttpUtil.BlankResponseException();
            if (response.getStatusLine().getStatusCode() == 200 || this.erreturn)
                try {
                    return EntityUtils.toString(response.getEntity(), "UTF-8");
                } catch (ParseException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        if (response != null)
                            response.close();
                    } catch (IOException iOException) {}
                }
            throw new HttpUtil.FailResponseException();
        }

        public DefaultStringHandler() {}
    }

    public static interface HandlerFunction<T> {
        T handleResponse(CloseableHttpResponse param1CloseableHttpResponse);

        default T handleException(Exception e) {
            return null;
        }
    }
}
