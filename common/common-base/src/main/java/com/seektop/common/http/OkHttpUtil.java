package com.seektop.common.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class OkHttpUtil {

    @Resource
    private OkHttpClient okHttpClient;

    @Resource(name = "redirectOkHttpClient")
    private OkHttpClient redirectOkHttpClient;

    @Resource(name = "redirectInterceptor")
    private RedirectInterceptor redirectInterceptor;

    public String post(final String url, final Map<String, String> params, final Map<String, String> headers) {
        FormBody.Builder bodyBuilder = new FormBody.Builder();
        if (CollectionUtils.isEmpty(params) == false) {
            params.forEach((k,v) -> bodyBuilder.add(k,v));
        }
        Headers.Builder headerBuilder = new Headers.Builder();
        if (CollectionUtils.isEmpty(headers) == false) {
            headers.forEach((k,v) -> headerBuilder.add(k,v));
        }
        return post(url, bodyBuilder.build(), headerBuilder.build());
    }

    public String post(final String url, final Map<String, String> params) {
        FormBody.Builder bodyBuilder = new FormBody.Builder();
        if (CollectionUtils.isEmpty(params) == false) {
            params.forEach((k,v) -> {
                bodyBuilder.add(k,v);
            });
        }
        Headers.Builder headerBuilder = new Headers.Builder();
        return post(url, bodyBuilder.build(), headerBuilder.build());
    }

    /**
     * post (自定义请求头)
     *
     * @param url
     * @param params
     * @param requestHeader
     * @return
     */
    public String post(String url, Map<String, String> params, GlRequestHeader requestHeader) {
        return this.post(url, params, null, requestHeader);
    }

    /**
     * post （自定义请求头+超时时间）
     *
     * @param url
     * @param paramsMap
     * @param timeOutSeconds
     * @param requestHeader
     * @return
     */
    public String post(String url, Map<String, String> paramsMap, Long timeOutSeconds, GlRequestHeader requestHeader) {
        Request request = null;
        FormBody.Builder builder = new FormBody.Builder();
        //添加参数
        if (paramsMap != null && paramsMap.keySet().size() > 0) {
            for (String key : paramsMap.keySet()) {
                builder.add(key, paramsMap.get(key));
            }
        }
        //判断是否需要请求头
        if (requestHeader == null) {
            request = new Request.Builder().url(url).post(builder.build()).build();
        } else {
            request = new Request.Builder().url(url).headers(getHeadersByDto(requestHeader)).post(builder.build()).build();
        }
        //判断是否自定义超时时间
        if (timeOutSeconds == null) {
            return execNewCall(request);
        }
        return execNewCall(request, timeOutSeconds);
    }

    /**
     * post （自定义请求头）
     *
     * @param url
     * @param paramsMap
     * @param requestHeader
     * @return
     */
    public String post(String url, Map<String, Object> paramsMap, GlRequestHeader requestHeader, Map<String, String> headParams) {
        Request request = null;
        FormBody.Builder builder = new FormBody.Builder();
        //添加参数
        if (paramsMap != null && paramsMap.keySet().size() > 0) {
            for (String key : paramsMap.keySet()) {
                builder.add(key, paramsMap.get(key).toString());
            }
        }
        //判断是否需要请求头
        if (requestHeader == null) {
            request = new Request.Builder().url(url).post(builder.build()).build();
        } else {
            request = new Request.Builder().url(url).headers(getHeaders(requestHeader,headParams)).post(builder.build()).build();
        }
        //判断是否自定义超时时间
        return execNewCall(request);
    }

    /**
     * 调用okhttp的newCall方法
     *
     * @param request
     * @return
     */
    public String execNewCall(Request request) {
        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
            int status = response.code();
            if (response.isSuccessful()) {
                return response.body().string();
            }
        } catch (Exception e) {
            log.error("okhttp3 put error >> ex = {}", ExceptionUtils.getStackTrace(e));
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return "";
    }


    /**
     * 调用okhttp的newCall方法
     *
     * @param request
     * @return
     */
    public String execNewCall(Request request, long timeOutSeconds) {
        return execNewCall(request,timeOutSeconds,timeOutSeconds,timeOutSeconds);
    }

    /**
     * 设置连接超时和读取超时
     * @param request
     * @param connectTimeOutSeconds
     * @param readTimeOutSeconds
     * @return
     */
    public String execNewCall(Request request, long connectTimeOutSeconds,long readTimeOutSeconds) {
        return execNewCall(request,connectTimeOutSeconds,readTimeOutSeconds,readTimeOutSeconds);
    }
    /**
     * 自定义超时时间
     *
     * @param request
     * @return
     */
    public String execNewCall(Request request, long connectTimeOutSeconds,long readTimeOutSeconds,long writeTimeOutSeconds) {
        Response response = null;
        try {
            OkHttpClient client = okHttpClient.newBuilder()
                    .connectTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(writeTimeOutSeconds, TimeUnit.SECONDS)
                    .readTimeout(readTimeOutSeconds, TimeUnit.SECONDS).build();

            response = client.newCall(request).execute();
            int status = response.code();
            if (response.isSuccessful()) {
                return response.body().string();
            }
        } catch (Exception e) {
            log.error("okhttp3 put error >> ex = {}", ExceptionUtils.getStackTrace(e));
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return "";
    }
    public String execNewCallWithRedirect(Request request, long connectTimeOutSeconds,long readTimeOutSeconds,long writeTimeOutSeconds) {
        Response response = null;
        try {
            OkHttpClient client = okHttpClient.newBuilder()
                    .connectTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(writeTimeOutSeconds, TimeUnit.SECONDS)
                    .addInterceptor(redirectInterceptor)
                    .readTimeout(readTimeOutSeconds, TimeUnit.SECONDS).build();

            response = client.newCall(request).execute();
            int status = response.code();
            if (response.isSuccessful()) {
                return response.body().string();
            }
        } catch (Exception e) {
            log.error("okhttp3 put error >> ex = {}", ExceptionUtils.getStackTrace(e));
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return "";
    }

    /**
     * postJSON （自定义请求头）
     *
     * @param url
     * @param jsonParams
     * @param requestHeader
     * @return
     */
    public String postJSON(String url, String jsonParams, GlRequestHeader requestHeader) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonParams);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(getHeadersByDto(requestHeader))
                .build();
        return execNewCall(request);
    }

    /**
     * 封装业务请求头
     *
     * @param requestHeader
     * @return
     */
    public Headers getHeadersByDto(GlRequestHeader requestHeader) {

        return Headers.of(getHeaderMapByDto(requestHeader));
    }


    /**
     * 将dto转换为map
     *
     * @param requestHeader
     * @return
     */
    public Map<String, String> getHeaderMapByDto(GlRequestHeader requestHeader) {
        if (requestHeader == null) {
            return null;
        }
        //key增加gl前缀，避免与原header中的key重复
        Map<String, String> map = new HashMap<>();
        map.put("glAction", StringUtils.isBlank(requestHeader.getAction())?"":requestHeader.getAction());
        map.put("glChannelId", StringUtils.isBlank(requestHeader.getChannelId())?"":requestHeader.getChannelId());
        String channelName = StringUtils.isBlank(requestHeader.getChannelName())?"":requestHeader.getChannelName();
        try {
            channelName = URLEncoder.encode(channelName, "UTF-8");  // 请求头不支持中文
        }
        catch (UnsupportedEncodingException e) {
            log.error("URLEncoder.encode error", e);
        }
        map.put("glChannelName", channelName);
        map.put("glUserId", StringUtils.isBlank(requestHeader.getUserId())?"":requestHeader.getUserId());
        map.put("glUserName", StringUtils.isBlank(requestHeader.getUserName())?"":requestHeader.getUserName());
        map.put("glTradeId", StringUtils.isBlank(requestHeader.getTradeId())?"":requestHeader.getTradeId());
        map.put("glTerminal", StringUtils.isBlank( requestHeader.getTerminal())?"": requestHeader.getTerminal());

        return map;
    }

    public String postJSON(final String url, final String jsonParams) {
        RequestBody body = RequestBody.create(jsonParams, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        return executeCall(request);
    }

    public String postJSON(final String url, final String jsonParams,Boolean redirect) {
        RequestBody body = RequestBody.create(jsonParams, MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(url).post(body).build();
        return execCallRedirect(request);
    }

    public String get(final String url) {
        Request request = new Request.Builder().url(url).build();
        return executeCall(request);
    }

    private String post(final String url, FormBody params, Headers headers) {
        Request request = new Request.Builder().url(url).post(params).headers(headers).build();
        return executeCall(request);
    }

    public String executeCall(Request request) {
        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
            if (response != null && response.isSuccessful()) {
                return response.body().string();
            }
        } catch (Exception ex) {
            log.error("OKHttpUtil.executeCall()", ex);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return null;
    }

    public Response executeCallResponse(Request request) {
        Response response = null;
        try {
            response = okHttpClient.newCall(request).execute();
            if (response != null && response.isSuccessful()) {
                return response;
            }
        } catch (Exception ex) {
            log.error("OKHttpUtil.executeCall()", ex);
        }
        return null;
    }

    public String execCall(Request request, int timeout){
        try (Response response = okHttpClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()) {
            return response.body().string();
        }
        catch (Exception e) {
            log.error("okhttp3 put error >> ex = {}", ExceptionUtils.getStackTrace(e));
        }
        return "";
    }
    public String execCallRedirect(Request request){
        Response response = null;
        try {
            response = redirectOkHttpClient.newCall(request).execute();
            if (response != null && response.isSuccessful()) {
                return response.body().string();
            }
        } catch (Exception ex) {
            log.error("OKHttpUtil.executeCall()", ex);
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return null;
    }
    /**
     * get(自定义请求头+额外参数)
     *
     * @param url
     * @param queries
     * @param requestHeader
     * @return
     */
    public String get(String url, Map<String, String> queries, GlRequestHeader requestHeader) {
        return this.get(url, queries, null, requestHeader);
    }

    /**
     * get
     * @param url
     * @param timeOutSeconds
     * @return
     */
    public String get(String url, Long timeOutSeconds) {
        return this.get(url, null, timeOutSeconds, null);
    }

    /**
     * get  (供其他方法重载)
     *
     * @param url
     * @param paramsMap
     * @param timeOutSeconds
     * @param requestHeader
     * @return
     */
    public String get(String url, Map<String, String> paramsMap, Long timeOutSeconds, GlRequestHeader requestHeader) {
        Request request = null;
        //判断参数是否存在
        if (paramsMap != null && !paramsMap.isEmpty()) {
            url = getQueryString(url, paramsMap).toString();
        }
        //判断是否需要请求头
        if (requestHeader == null) {
            request = new Request.Builder().url(url).build();
        } else {
            request = new Request.Builder().url(url).headers(getHeadersByDto(requestHeader)).build();
        }
        //判断是否自定义超时时间
        if (timeOutSeconds == null) {
            return execNewCall(request);
        }
        return execNewCall(request, timeOutSeconds);
    }

    /**
     * get  (供其他方法重载)
     *
     * @param url
     * @param paramsMap
     * @param requestHeader
     * @return
     */
    public String get(String url, Map<String, String> paramsMap, GlRequestHeader requestHeader, Map<String, String> headParams) {
        Request request = null;
        //判断参数是否存在
        if (paramsMap != null && !paramsMap.isEmpty()) {
            url = getQueryString(url, paramsMap).toString();
        }
        //判断是否需要请求头
        if (requestHeader == null) {
            request = new Request.Builder().url(url).build();
        } else {
            request = new Request.Builder().url(url).headers(getHeaders(requestHeader,headParams)).build();
        }

        return execNewCall(request);
    }


    public String get(String url, Map<String, String> paramsMap, Map<String, String> headParams) {
        Request request = null;
        //判断参数是否存在
        if (paramsMap != null && !paramsMap.isEmpty()) {
            url = getQueryString(url, paramsMap).toString();
        }
        Map<String, String> map = new HashMap<>();
        if(headParams != null){
            for (Map.Entry<String,String> entry: headParams.entrySet()) {
                map.put(entry.getKey(),entry.getValue());
            }
        }
        request = new Request.Builder().url(url).headers(Headers.of(map)).build();
        return execNewCall(request);
    }

    /**
     * 根据map获取get请求参数
     *
     * @param queries
     * @return
     */
    public static StringBuffer getQueryString(String url, Map<String, String> queries) {
        StringBuffer sb = new StringBuffer(url);
        if (queries != null && queries.keySet().size() > 0) {
            boolean firstFlag = true;
            Iterator iterator = queries.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry entry = (Map.Entry<String, String>) iterator.next();
                if (firstFlag) {
                    sb.append("?" + entry.getKey() + "=" + entry.getValue());
                    firstFlag = false;
                } else {
                    sb.append("&" + entry.getKey() + "=" + entry.getValue());
                }
            }
        }
        return sb;
    }


    /**
     * for 小金体育注单拉取
     * @param url
     * @param text
     * @param headers
     * @param removeHeaderKey
     * @param requestHeader
     * @param connectTimeout 连接超时（秒）
     * @param readTimeout    读取超时（秒）
     * @return
     * @throws Exception
     */
    public String postText(String url, String text, Map<String, String> headers, String removeHeaderKey, GlRequestHeader requestHeader, long connectTimeout, long readTimeout) throws Exception {
        Map<String, String> map = this.getHeaderMapByDto(requestHeader);
        //将map合并到headers中
        headers.putAll(map);

        Headers okHeaders = Headers.of(headers);
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/plain"), text);
        Request request = new Request.Builder()
                .headers(okHeaders)
                .url(url)
                .post(requestBody)
                .removeHeader(removeHeaderKey)
                .build();
        return execNewCall(request,connectTimeout,readTimeout);
    }
    /**
     * for 小金游戏（添加自定义请求头）
     * @param url
     * @param text
     * @param headers
     * @param removeHeaderKey
     * @param requestHeader
     * @return
     * @throws Exception
     */
    public String postText(String url, String text, Map<String, String> headers, String removeHeaderKey, GlRequestHeader requestHeader) throws Exception {
        Map<String, String> map = this.getHeaderMapByDto(requestHeader);
        //将map合并到headers中
        headers.putAll(map);

        Headers okHeaders = Headers.of(headers);
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/plain"), text);
        Request request = new Request.Builder()
                .headers(okHeaders)
                .url(url)
                .post(requestBody)
                .removeHeader(removeHeaderKey)
                .build();
        return execNewCall(request);
    }

    public String postText(String url, String text, Map<String, String> headers, GlRequestHeader requestHeader) {
        Map<String, String> map = this.getHeaderMapByDto(requestHeader);
        //将map合并到headers中
        headers.putAll(map);

        Headers okHeaders = Headers.of(headers);
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/plain"), text);
        Request request = new Request.Builder()
                .headers(okHeaders)
                .url(url)
                .post(requestBody)
                .build();
        return execNewCall(request);
    }

    public String postText(String url, String text, GlRequestHeader requestHeader) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("text/plain"), text);
        Request request = new Request.Builder()
                .headers(this.getHeadersByDto(requestHeader))
                .url(url)
                .post(requestBody)
                .build();
        return execNewCall(request);
    }

    /**
     *  postJSON（额外请求头+监控请求头）
     * @param url
     * @param jsonParams
     * @param header
     * @param requestHeader
     * @return
     */
    public String postJSON(String url, String jsonParams, Map<String, String> header, GlRequestHeader requestHeader) {

        //将map合并到headers中
        Map<String, String> headers = new HashMap<>();
        headers.putAll(header);

        Map<String, String> map = this.getHeaderMapByDto(requestHeader);
        if(map != null){
            headers.putAll(map);
        }

        Headers okHeaders = Headers.of(headers);
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonParams);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(okHeaders)
                .build();
        return execNewCall(request);
    }
    /**
     * postJSON （自定义请求头+超时时间）
     * @param url
     * @param jsonParams
     * @param requestHeader
     * @param timeOutSeconds 连接超时时间（秒）
     * @return
     */
    public String postJSONLimitTime(String url, String jsonParams, GlRequestHeader requestHeader, long timeOutSeconds) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonParams);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(getHeadersByDto(requestHeader))
                .build();
        return execNewCall(request,timeOutSeconds);
    }
    public String postJSONLimitTimeWithRedirect(String url, String jsonParams, GlRequestHeader requestHeader, long timeOutSeconds) {
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonParams);
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(getHeadersByDto(requestHeader))
                .build();
        return  execNewCallWithRedirect(request,timeOutSeconds,timeOutSeconds,timeOutSeconds);
    }

    /**
     * putJSON （自定义请求头）
     *
     * @param url
     * @param jsonParams
     * @param type
     * @param requestHeader
     * @return
     */
    public String putJSON(String url, String jsonParams, String type, GlRequestHeader requestHeader) {

        Map<String, String> headers = new HashMap<>();
        if (type != null) {
            headers.put("User-Agent", type);
        }
        headers.put("Content-Type", "application/json");

        Map<String, String> map = this.getHeaderMapByDto(requestHeader);
        //将map合并到headers中
        headers.putAll(map);

        Headers okHeaders = Headers.of(headers);
        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonParams);
        Request request = new Request.Builder()
                .headers(okHeaders)
                .url(url)
                .put(requestBody)
                .build();
        return execNewCall(request);
    }


    /**
     * Post请求发送JSON数据....{"name":"zhangsan","pwd":"123456"}
     * 参数一：请求Url
     * 参数二：请求的JSON
     * 参数三：请求回调
     */
    public String putJSON(String url, String jsonParams) {

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonParams);
        Request request = new Request.Builder()
                .url(url)
                .put(requestBody)
                .build();
        return execNewCall(request);
    }

    /**
     * putJSON （自定义请求头）
     *
     * @param url
     * @param jsonParams
     * @param requestHeader
     * @return
     */
    public String putJSON(String url, String jsonParams, GlRequestHeader requestHeader) {

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), jsonParams);
        Request request = new Request.Builder()
                .url(url)
                .put(requestBody)
                .headers(getHeadersByDto(requestHeader))
                .build();
        return execNewCall(request);
    }

    /**
     * 封装请求头
     *
     * @param requestHeader
     * @return
     */
    private Headers getHeaders(GlRequestHeader requestHeader, Map<String, String> headParams) {
        Map<String, String> map = getHeaderMapByDto(requestHeader);
        if(map != null && headParams != null){
            for (Map.Entry<String,String> entry: headParams.entrySet()) {
                map.put(entry.getKey(),entry.getValue());
            }
        }
        return Headers.of(map);
    }

    /**
     * post （自定义请求头）
     *
     * @param url
     * @param paramsMap
     * @param requestHeader
     * @param charset
     * @return
     */
    public String post(String url, Map<String, Object> paramsMap, GlRequestHeader requestHeader, Map<String, String> headParams, Charset charset) {
        Request request = null;
        FormBody.Builder builder = new FormBody.Builder();
        if(charset != null){
            builder = new FormBody.Builder(charset);
        }
        //添加参数
        if (paramsMap != null && paramsMap.keySet().size() > 0) {
            for (String key : paramsMap.keySet()) {
                builder.add(key, paramsMap.get(key).toString());
            }
        }
        //判断是否需要请求头
        if (requestHeader == null) {
            request = new Request.Builder().url(url).post(builder.build()).build();
        } else {
            request = new Request.Builder().url(url).headers(getHeaders(requestHeader,headParams)).post(builder.build()).build();
        }
        //判断是否自定义超时时间
        return execNewCall(request);
    }

    /**
     * post
     * @param url
     * @param params
     * @param headers
     * @param timeout
     * @return
     */
    public String post(String url, Map<String, String> params, Map<String, String> headers, int timeout) {
        FormBody.Builder builder = new FormBody.Builder();
        //添加参数
        if (!CollectionUtils.isEmpty(params)) {
            for (String key : params.keySet()) {
                builder.add(key, params.get(key));
            }
        }
        //判断是否需要请求头
        Request request;
        if (CollectionUtils.isEmpty(headers)) {
            request = new Request.Builder().url(url).post(builder.build()).build();
        }
        else {
            request = new Request.Builder().url(url).headers(Headers.of(headers)).post(builder.build()).build();
        }
        return execCall(request, timeout);
    }

    public String post(String url, Map<String, String> params, Map<String, String> headers, GlRequestHeader header, int timeout) {
        Map<String, String> headersMap = Optional.ofNullable(headers).orElse(new HashMap<>());
        Optional.ofNullable(getHeaderMapByDto(header)).ifPresent(headersMap::putAll);
        return post(url, params, headersMap, timeout);
    }

    /**
     * get
     * @param url
     * @param headers
     * @param timeout
     * @return
     */
    public String get(String url, Map<String, String> headers, int timeout) {
        Request request;
        if (CollectionUtils.isEmpty(headers)) {
            request = new Request.Builder().url(url).build();
        }
        else {
            request = new Request.Builder().url(url).headers(Headers.of(headers)).build();
        }
        return execCall(request, timeout);
    }

    public String get(String url, Map<String, String> headers, GlRequestHeader header, int timeout) {
        Map<String, String> headersMap = Optional.ofNullable(headers).orElse(new HashMap<>());
        Optional.ofNullable(getHeaderMapByDto(header)).ifPresent(headersMap::putAll);
        return get(url, headersMap, timeout);
    }

    /**
     * DELETE方法
     * @param url
     * @param headers
     * @return
     */
    public String delete(String url, Map<String, String> headers) {
        Request request = new Request.Builder()
                .url(url)
                .delete()
                .headers(Headers.of(headers))
                .build();
        return execNewCall(request);
    }
}
