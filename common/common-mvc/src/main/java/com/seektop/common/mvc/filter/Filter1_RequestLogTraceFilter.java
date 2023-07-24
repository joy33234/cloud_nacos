package com.seektop.common.mvc.filter;

import com.seektop.common.utils.JobEncryptPermissionUtils;
import com.seektop.common.utils.NumStringUtils;
import com.seektop.common.utils.PermissionUtils;
import com.seektop.common.utils.UserIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@Slf4j
@Order(0)
@Component
@WebFilter(urlPatterns = "/*", filterName = "requestLogTraceFilter")
public class Filter1_RequestLogTraceFilter implements Filter {

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String userId = request.getParameter("headerUid");
        String traceId = request.getHeader("traceId");
        if (StringUtils.isEmpty(traceId)) {
            traceId = NumStringUtils.getUUID();
        }
        MDC.put("userId", userId);
        MDC.put("traceId", traceId);
        UserIdUtils.release();
        if (StringUtils.hasText(userId)) {
           try {
               UserIdUtils.setUserId(Integer.parseInt(userId));
               // 每次先初始化需要情况job脱敏权限
               JobEncryptPermissionUtils.release();
           } catch (Exception e){
              log.error("前端传参userId错误：{} exception:{}", userId, e);
           }
        }
        PermissionUtils.release();
        String glVersion = request.getHeader("gl_Version");
        if(StringUtils.hasText(glVersion)){
            // 新旧后台，数据权限兼容
            PermissionUtils.setVersion(glVersion);
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }

}