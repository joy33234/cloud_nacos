package com.seektop.common.http.component;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seektop.common.annotation.*;
import com.seektop.common.function.OKHttpRequestPostHandler;
import com.seektop.common.function.OKHttpRequestPreHandler;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.http.enums.STHttpContentType;
import com.seektop.common.http.enums.STHttpMethod;
import com.seektop.common.http.enums.STRResultParser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RequestMethodInvocationHandler implements MethodInterceptor {


    private OkHttpUtil okHttpUtil;

    public RequestMethodInvocationHandler(OkHttpUtil okHttpUtil) {
        this.okHttpUtil = okHttpUtil;
    }

    public static ObjectMapper objectMapper = new ObjectMapper();

    static {
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        objectMapper.configOverride(Map.class).setInclude(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
    }

    @Override
    public Object intercept(Object o, Method method, Object[] objects, MethodProxy methodProxy) throws Throwable {
        final STRequest request = method.getAnnotation(STRequest.class);
        okHttpUtil = OKHttpTools.okHttpUtil;
        final STHttpMethod requestMethod = request.method();
        final int timeout = request.timeout();
        final STHttpContentType stHttpContentType = request.ContentType();
        final boolean redirect = request.redirect();
        String requestUrl = request.url();

        JSONObject param = new JSONObject();
        JSONObject header = new JSONObject();
        header.put("Content-Type", stHttpContentType.getContentType());

        final Parameter[] parameters = method.getParameters();
        OKHttpRequestPreHandler httpRequestPreHandler = null;
        OKHttpRequestPostHandler httpRequestPostHandler = null;
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object arg = objects[i];
            if (arg instanceof GlRequestHeader) {
                final Map<String, String> headerMapByDto = okHttpUtil.getHeaderMapByDto((GlRequestHeader) arg);
                header.putAll(headerMapByDto);
                continue;
            }
            if (arg instanceof OKHttpRequestPreHandler) {
                httpRequestPreHandler = (OKHttpRequestPreHandler) arg;
                continue;
            }
            if (arg instanceof OKHttpRequestPostHandler) {
                httpRequestPostHandler = (OKHttpRequestPostHandler) arg;
                continue;
            }
            if (null != parameter.getAnnotation(STRequestHeader.class)) {
                final JSONObject parse = (JSONObject) JSON.toJSON(arg);
                header.putAll(parse);
            }
            if (null != parameter.getAnnotation(STRequestParam.class)) {
                final JSONObject apply = request.jsonType().getParser().apply(arg);
                param.putAll(apply);
            }
            if (null != parameter.getAnnotation(STRequestPathParam.class)) {
                final STRequestPathParam annotation = parameter.getAnnotation(STRequestPathParam.class);
                final String name = annotation.name();
                requestUrl = requestUrl.replace("${" + name + "}", arg.toString());

            }
            if (null != parameter.getAnnotation(STRequestPath.class)) {
                requestUrl = arg.toString() + requestUrl;
            }
        }
        if(null != httpRequestPreHandler){
            httpRequestPreHandler.pre(param,header,requestUrl);
        }
        log.info("当前的request参数:{}", param);
        String result = "";
        Response response = null;
        switch (requestMethod) {
            case GET:
                response = get(requestUrl, param, header,timeout,redirect);
                break;
            case POST: {
                switch (stHttpContentType) {
                    case FORM:
                        response = post(requestUrl, param, header,timeout,redirect);
                        break;
                    case APPLICATION_JSON:
                        response = postJSON(requestUrl, param, header,timeout,redirect);
                        break;
                }
            }
            break;
        }

        if(null != httpRequestPostHandler){
           return httpRequestPostHandler.post(response);
        }
        result = dealResponse(response);
        if (null == result) {
            return null;
        }
        final Type returnType = method.getGenericReturnType();
        if (String.class == returnType) {
            return result;
        }
        if(request.showResponse()){
            log.info("当前的请求结果：{}",result);
        }
        final STRResultParser.ResultParser<String, Type, Object> parser = request.resultParser().getParser();
        if(null != parser){
            return parser.parse(result,returnType);
        }
        final Object obj = this.objectMapper.readValue(result, this.objectMapper.constructType(returnType));
        return obj;
    }

    /**
     *
     * @param connectTimeOutSeconds
     * @param redirect
     * @return
     */
    private OkHttpClient getClient(Integer connectTimeOutSeconds,boolean redirect){
        // 超时是否有效
        boolean withTimeout = null != connectTimeOutSeconds && -1 != connectTimeOutSeconds;

        // 未配置超时和重定向
        if(!withTimeout && !redirect){
            return OKHttpTools.okHttpClient;
        }
        final OkHttpClient.Builder builder = OKHttpTools.okHttpClient.newBuilder();
        if(withTimeout){
            builder.connectTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
                    .writeTimeout(connectTimeOutSeconds, TimeUnit.SECONDS)
                    .readTimeout(connectTimeOutSeconds, TimeUnit.SECONDS);
            if(redirect){
                builder.addInterceptor(OKHttpTools.redirectInterceptor);
            }
            return builder.build();
        }
        if(redirect){
            return OKHttpTools.redirectOkHttpClient;
        }
        return OKHttpTools.okHttpClient;
    }


    @NotNull
    private Headers.Builder getHeaderBuilder(JSONObject headers) {
        Headers.Builder headerBuilder = new Headers.Builder();
        if (CollectionUtils.isEmpty(headers) == false) {
            headers.forEach((k, v) -> headerBuilder.add(k, String.valueOf(v)));
        }
        return headerBuilder;
    }

    @NotNull
    private FormBody.Builder getBodyBuilder(JSONObject params) {
        FormBody.Builder bodyBuilder = new FormBody.Builder();
        if (!CollectionUtils.isEmpty(params)) {
            params.forEach((k, v) -> {
                if(null != v) {
                    bodyBuilder.add(k, String.valueOf(v));
                }
            });

        }
        return bodyBuilder;
    }

    public static StringBuffer getQueryString(String url, JSONObject queries) {
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

    public Response postJSON(String url, JSONObject params, JSONObject headers, Integer timeOut, Boolean redirect) {
        RequestBody requestBody = RequestBody.create(params.toJSONString(), MediaType.parse("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(getHeaderBuilder(headers).build())
                .build();
        final OkHttpClient client = getClient(timeOut, redirect);
        return executeCallResponse(request,client);
    }

    /**
     * 普通的http请求
     *
     * @param url
     * @param params
     * @param headers
     */
    public Response post(String url, JSONObject params, JSONObject headers, Integer timeOut, Boolean redirect) {
        FormBody.Builder bodyBuilder = getBodyBuilder(params);
        Headers.Builder headerBuilder = getHeaderBuilder(headers);
        final FormBody build = bodyBuilder.build();
        Request request = new Request.Builder().url(url).post(build).headers(headerBuilder.build()).build();
        final OkHttpClient client = getClient(timeOut, redirect);
        return executeCallResponse(request,client);
    }

    /**
     * 普通的http请求
     *
     * @param url
     * @param params
     * @param headers
     */
    public Response get(String url, JSONObject params, JSONObject headers, Integer timeOut, boolean redirect) {
        url = getQueryString(url, params).toString();
        Headers.Builder headerBuilder = getHeaderBuilder(headers);
        Request request = new Request.Builder().url(url).get().headers(headerBuilder.build()).build();
        final OkHttpClient client = getClient(timeOut, redirect);
        return executeCallResponse(request,client);
    }

    public String dealResponse(Response response){
        try {
            if(null == response){
                return "";
            }
            return response.body().string();
        } catch (IOException e) {
            log.error("OKHttpUtil.executeCall()", e);
            return "";
        } finally {
            response.close();
        }
    }

    public Response executeCallResponse(Request request,OkHttpClient client) {
        Response response = null;
        try {
            response = client.newCall(request).execute();
            if (response != null && response.isSuccessful()) {
                return response;
            }
        } catch (Exception ex) {
            log.error("OKHttpUtil.executeCall()", ex);
        }
        return null;
    }
}
