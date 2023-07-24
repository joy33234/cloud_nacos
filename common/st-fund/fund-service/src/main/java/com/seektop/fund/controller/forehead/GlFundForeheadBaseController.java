package com.seektop.fund.controller.forehead;

import com.seektop.common.mvc.BaseController;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.dto.GlUserDO;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.servlet.http.HttpServletRequest;

@Slf4j
public class GlFundForeheadBaseController extends BaseController {

//    @Autowired
//    private RedisService redisService;

    @DubboReference(retries = 3, timeout = 3000)
    private GlUserService glUserService;


    @ModelAttribute("userInfo")
    public GlUserDO before(HttpServletRequest request) {
        String paramUserId = request.getParameter("headerUid");
        if (StringUtils.isBlank(paramUserId)) {
            return null;
        }
        Integer uid = Integer.valueOf(paramUserId);
//        GlUserDO userDO = redisService.get(KeyConstant.USER.DETAIL_CACHE + uid, GlUserDO.class);
//        return userDO;
        RPCResponse<GlUserDO> rpcResponse = glUserService.findById(uid);
        if (RPCResponseUtils.isFail(rpcResponse)) {
            return null;
        }
        return rpcResponse.getData();
    }
}
