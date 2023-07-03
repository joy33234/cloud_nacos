package com.seektop.fund.common;

import com.seektop.activity.dto.param.user.GlUserVipDo;
import com.seektop.activity.service.GlUserVipService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.DateConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.UserVIPCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class UserVipUtils {

    @Resource
    private RedisService redisService;

    @DubboReference(retries = 2, timeout = 3000)
    private GlUserVipService glUserVipService;

    public UserVIPCache getUserVIPCacheFromDB(Integer userId) {
        RPCResponse<GlUserVipDo> rpcResponse = glUserVipService.findById(userId);
        if (RPCResponseUtils.isFail(rpcResponse)) {
            return getUserVIPCache(userId);
        }
        GlUserVipDo userVipDo = rpcResponse.getData();
        UserVIPCache vipCache = new UserVIPCache();
        vipCache.setLockStatus(userVipDo.getLockStatus());
        vipCache.setVipLevel(userVipDo.getVipLevel());
        redisService.set(KeyConstant.USER.USER_VIP_CACHE + userId, vipCache, DateConstant.SECOND.DAY);
        return vipCache;
    }

    public UserVIPCache getUserVIPCache(Integer userId) {
        UserVIPCache vipCache = redisService.get(KeyConstant.USER.USER_VIP_CACHE + userId, UserVIPCache.class);
        if (vipCache != null) {
            return vipCache;
        }
        RPCResponse<GlUserVipDo> vipDoRPCResponse = glUserVipService.findById(userId);
        if (RPCResponseUtils.isSuccess(vipDoRPCResponse)) {
            GlUserVipDo userVipDo = vipDoRPCResponse.getData();
            vipCache = new UserVIPCache();
            vipCache.setLockStatus(userVipDo.getLockStatus());
            vipCache.setVipLevel(userVipDo.getVipLevel());
            redisService.set(KeyConstant.USER.USER_VIP_CACHE + userId, vipCache, DateConstant.SECOND.DAY);
            return vipCache;
        }
        return null;
    }

}