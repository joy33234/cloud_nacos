package com.seektop.fund.handler.validation;

import com.seektop.common.redis.RedisService;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class ValidationLimit implements DataValidation {
    private String limitKey;
    private RedisService redisService;

    @Override
    public void valid() throws GlobalException {
        if (redisService.incrBy(limitKey, 1) > 1) {
            redisService.setTTL(limitKey, 10);
            throw new GlobalException(ResultCode.TOOMANY_REQUEST, "您操作的太快了，请稍后再试");
        }
        else {
            redisService.setTTL(limitKey, 10);
        }
    }
}
