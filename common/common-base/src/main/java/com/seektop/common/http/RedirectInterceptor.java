package com.seektop.common.http;

import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

@Slf4j
public class RedirectInterceptor implements Interceptor {

    private OkHttpClient redirectOkHttpClient;

    public RedirectInterceptor(OkHttpClient.Builder builder){
        this.redirectOkHttpClient  = builder.addInterceptor(this).build();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Response response = chain.proceed(request);
        int code = response.code();
        if (code == 307) {

            String location = response.headers().get("Location");
            log.info("原地址：{} 重定向地址：{}"  ,request.url(),location);
            Request newRequest = request.newBuilder()
                    .headers(request.headers())
                    .post(request.body())
                    .url(location)
                    .build();
            response = redirectOkHttpClient.newCall(newRequest).execute();
        }
        return response;
    }
}