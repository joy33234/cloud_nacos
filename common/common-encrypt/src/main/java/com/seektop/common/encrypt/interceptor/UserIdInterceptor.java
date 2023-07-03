package com.seektop.common.encrypt.interceptor;

import com.seektop.common.utils.JobEncryptPermissionUtils;
import com.seektop.common.utils.UserIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Slf4j
public class UserIdInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String uid = request.getHeader("uid");
        if(!StringUtils.isEmpty(uid)) {
            try {
                Integer userId = Integer.valueOf(uid);

                log.info("设置thread local- userId {}", userId);
                UserIdUtils.setUserId(userId);
            }catch (Exception e){
                // todo 拿不到网关的url排除配置
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        log.info("释放 UserIdUtils JobEncryptPermissionUtils");
        UserIdUtils.release();
        JobEncryptPermissionUtils.release();
    }
}
