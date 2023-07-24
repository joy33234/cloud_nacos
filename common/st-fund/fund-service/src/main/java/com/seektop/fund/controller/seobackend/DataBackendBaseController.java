package com.seektop.fund.controller.seobackend;

import com.seektop.common.mvc.BaseController;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.data.service.UserService;
import com.seektop.dto.proxy.ProxyAdminDO;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ModelAttribute;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class DataBackendBaseController extends BaseController {

    @Resource
    RedisService redisService;
    @DubboReference
    UserService userService;

    @ModelAttribute("proxyAdmin")
    public ProxyAdminDO before(HttpServletRequest request) {
        String uid = request.getParameter("headerUid");
        if (StringUtils.isBlank(uid)) {
            return null;
        }
        Integer userId = Integer.valueOf(uid);
        ProxyAdminDO userDO = RedisTools.valueOperations().get(KeyConstant.PROXY_BACKEND.PROXY_BACKEND_ADMIN_CACHE + userId, ProxyAdminDO.class);
        if (userDO!=null&& ObjectUtils.isEmpty(userDO.getProxyIds()))userDO.setProxyIds(getAgentIds());
        return userDO;
    }

    public Set<Integer> getAgentIds(){
        Set<String> smembers = Optional.ofNullable(redisService.smembers(RedisKeyHelper.SEO_TEAM_AGENT_ID)).orElse(Collections.emptySet());
        Set<Integer> collect = smembers.stream().map(Integer::parseInt).collect(Collectors.toSet());
        return collect;
    }
    public boolean userCheck(ProxyAdminDO dto, String userName){
        Integer parentId = userService.getPrentId(userName).getData();
        if (dto!=null&&parentId!=null&&dto.getProxyIds().contains(parentId)) return true;
        return false;
    }

    public boolean userCheck(ProxyAdminDO dto, Integer uid){
        Integer parentId = userService.getPrentId(uid).getData();
        if (dto!=null&&parentId!=null&&dto.getProxyIds().contains(parentId)) return true;
        return false;
    }
}