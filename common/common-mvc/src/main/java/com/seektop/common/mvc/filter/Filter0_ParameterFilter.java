package com.seektop.common.mvc.filter;

import com.seektop.common.http.RequestUtil;
import com.seektop.common.mvc.request.ParameterRequestWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * 参数处理过滤器
 *
 * 这里负责将所有公共的Header中的参数放到Request对象中
 * 在Controller的方法中可以直接注入Header中的参数
 */
@Slf4j
@Order(-1)
@Component
@WebFilter(urlPatterns = "/*", filterName = "parameterFilter")
public class Filter0_ParameterFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        ParameterRequestWrapper requestWrapper = new ParameterRequestWrapper(request);
        // 应用类型
        String appType = request.getHeader("appType");
        if (StringUtils.isEmpty(appType)) {
            appType = request.getHeader("app-type");
            if (StringUtils.isEmpty(appType)) {
                appType = request.getHeader("app_type");
                if (StringUtils.isEmpty(appType)) {
                    appType = request.getParameter("appType");
                    if (StringUtils.isEmpty(appType)) {
                        appType = request.getParameter("app-type");
                        if (StringUtils.isEmpty(appType)) {
                            appType = request.getParameter("app_type");
                        }
                    }
                }
            }
        }
        requestWrapper.addParameter("headerAppType", appType);
        // 设备号
        String deviceId = request.getHeader("deviceId");
        if (StringUtils.isEmpty(deviceId)) {
            deviceId = request.getHeader("device-id");
            if (StringUtils.isEmpty(deviceId)) {
                deviceId = request.getHeader("device_id");
                if (StringUtils.isEmpty(deviceId)) {
                    deviceId = request.getParameter("deviceId");
                    if (StringUtils.isEmpty(deviceId)) {
                        deviceId = request.getParameter("device-id");
                        if (StringUtils.isEmpty(deviceId)) {
                            deviceId = request.getParameter("device_id");
                        }
                    }
                }
            }
        }
        requestWrapper.addParameter("headerDeviceId", deviceId);
        // Host
        requestWrapper.addParameter("headerHost", request.getHeader("x-forwarded-host"));
        // 系统类型
        String osType = request.getHeader("osType");
        if (StringUtils.isEmpty(osType)) {
            osType = request.getHeader("os-type");
            if (StringUtils.isEmpty(osType)) {
                osType = request.getHeader("os_type");
                if (StringUtils.isEmpty(osType)) {
                    osType = request.getParameter("osType");
                    if (StringUtils.isEmpty(osType)) {
                        osType = request.getParameter("os-type");
                        if (StringUtils.isEmpty(osType)) {
                            osType = request.getParameter("os_type");
                        }
                    }
                }
            }
        }
        requestWrapper.addParameter("headerOsType", osType);
        // 浏览器类型
        String userAgent = request.getHeader("User-Agent");
        requestWrapper.addParameter("headerUserAgent", userAgent);
        // 请求的域名和IP
        String url = RequestUtil.getInstance().getUrl(request);
        String ip = RequestUtil.getInstance().getIp(request);
        requestWrapper.addParameter("requestIp", ip);
        requestWrapper.addParameter("requestUrl", url);
        // Token
        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            token = request.getParameter("token");
        }
        requestWrapper.addParameter("headerToken", token);
        // 用户ID
        String uid = request.getHeader("uid");
        if (StringUtils.isEmpty(uid)) {
            uid = request.getParameter("uid");
        }
        //用户id不合法置空
        if(StringUtils.isEmpty(uid)||!Pattern.matches("^[1-9]\\d*$",uid)){
            uid = "";
        }
        requestWrapper.addParameter("headerUid", uid);
        // 三方接入用户ID
        String userId = request.getHeader("userId");
        requestWrapper.addParameter("headerUserId", userId);
        // 三方接入应用ID
        String appId = request.getHeader("appId");
        if (StringUtils.isEmpty(appId)) {
            appId = request.getHeader("app_id");
            if (StringUtils.isEmpty(appId)) {
                appId = request.getHeader("app-id");
            }
        }
        requestWrapper.addParameter("headerAppId", appId);
        //版本号
        String version = request.getHeader("version");
        requestWrapper.addParameter("headerVersion", version);
        // 语言
        String headerLanguage = request.getHeader("language");
        requestWrapper.addParameter("headerLanguage", headerLanguage);
        // 币种
        String headerCoin = request.getHeader("coinCode");
        requestWrapper.addParameter("headerCoinCode", headerCoin);

        filterChain.doFilter(requestWrapper, servletResponse);
    }

    @Override
    public void destroy() {

    }

}