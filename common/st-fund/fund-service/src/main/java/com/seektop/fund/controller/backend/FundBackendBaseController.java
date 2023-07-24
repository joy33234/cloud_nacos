package com.seektop.fund.controller.backend;

import com.seektop.common.mvc.BaseController;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlAdminDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;

@Slf4j
public class FundBackendBaseController extends BaseController {

    @Autowired
    private RedisService redisService;

    @ModelAttribute("adminInfo")
    public GlAdminDO before(HttpServletRequest request) {
        String token = request.getHeader("token");
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        String redisKey = KeyConstant.TOKEN.ADMIN_TOKEN + token;
        Integer uid = redisService.get(redisKey, Integer.class);
        if (ObjectUtils.isEmpty(uid)) {
            return null;
        }
        GlAdminDO adminDO = redisService.get(KeyConstant.TOKEN.ADMIN_USER + uid, GlAdminDO.class);
        adminDO.setToken(token);
        return adminDO;
    }

    public GlAdminDO getAdminByToken(String token) {
        if (StringUtils.isEmpty(token)) {
            return null;
        }
        String redisKey = KeyConstant.TOKEN.ADMIN_TOKEN + token;
        Integer uid = redisService.get(redisKey, Integer.class);
        if (ObjectUtils.isEmpty(uid)) {
            return null;
        }
        GlAdminDO adminDO = redisService.get(KeyConstant.TOKEN.ADMIN_USER + uid, GlAdminDO.class);
        adminDO.setToken(token);
        return adminDO;
    }

}