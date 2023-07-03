package com.seektop.common.http.component;

import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.http.RedirectInterceptor;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class OKHttpTools {

    public static OkHttpUtil okHttpUtil;
    public static OkHttpClient okHttpClient;

    public static OkHttpClient redirectOkHttpClient;

    public static RedirectInterceptor redirectInterceptor;

    @Autowired
    public void setOkHttpUtil(OkHttpUtil okHttpUtil){
        OKHttpTools.okHttpUtil = okHttpUtil;
    }

    @Autowired
    public  void setOkHttpClient(OkHttpClient okHttpClient) {
        OKHttpTools.okHttpClient = okHttpClient;
    }

    @Resource(name = "redirectOkHttpClient")
    public  void setRedirectOkHttpClient(OkHttpClient redirectOkHttpClient) {
        OKHttpTools.redirectOkHttpClient = redirectOkHttpClient;
    }

    @Resource(name = "redirectInterceptor")
    public void setRedirectInterceptor(RedirectInterceptor redirectInterceptor) {
        this.redirectInterceptor = redirectInterceptor;
    }
}
