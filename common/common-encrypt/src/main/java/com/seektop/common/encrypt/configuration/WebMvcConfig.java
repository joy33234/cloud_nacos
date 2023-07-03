package com.seektop.common.encrypt.configuration;

import com.seektop.common.encrypt.interceptor.UserIdInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;


//@Import(UserIdInterceptor.class)
//@Order
//@Configuration

/**
 * 如果没有设置 userId隐式传参，需要启用该配置
 * @see com.seektop.common.utils.UserIdUtils
 */
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private UserIdInterceptor userIdInterceptor;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userIdInterceptor).addPathPatterns("/manage/**");
    }
}
