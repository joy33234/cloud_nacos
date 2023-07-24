package com.seektop.fund.controller.forehead;

import com.seektop.common.mvc.BaseController;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlUserDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;

@Slf4j
public class FundForeheadBaseController extends BaseController {

    @Autowired
    private RedisService redisService;

    @ModelAttribute("userInfo")
    public GlUserDO before(HttpServletRequest request) {
        String userId = request.getParameter("headerUid");
        if (StringUtils.isEmpty(userId)) {
            return null;
        }
        return redisService.get(KeyConstant.USER.DETAIL_CACHE + userId, GlUserDO.class);
    }

}
