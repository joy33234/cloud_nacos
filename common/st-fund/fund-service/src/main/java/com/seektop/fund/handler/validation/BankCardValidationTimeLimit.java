package com.seektop.fund.handler.validation;

import com.seektop.common.redis.RedisService;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;

@Slf4j
@AllArgsConstructor
public class BankCardValidationTimeLimit implements DataValidation {

    private String name;
    private String cardNo;
    private Integer userId;
    private RedisService redisService;

    @Override
    public void valid() throws GlobalException {
        // 同一姓名+卡号校验，只能调用一次
        String key = String.format(RedisKeyHelper.BANK_API_CALL_COUNT, name, cardNo);
        Integer count = redisService.get(key, Integer.class);
        if (!ObjectUtils.isEmpty(count) && count >= 3) {
            log.error("同一姓名+卡号({}_{})校验，只能调用3次，已调用次数：{}", StringEncryptor.encryptBankCard(cardNo), name, count);
            throw new GlobalException(ResultCode.UNSUPPORTED_BANK_ERROR);
        }
        // 如果当前用户今天错误验证次数大于十次则不予以请求
        count = redisService.get(RedisKeyHelper.BANK_CARD_VALIDATE_ERROR_COUNT + userId, Integer.class);
        if (!ObjectUtils.isEmpty(count) && count >= 10) {
            log.error("当前用户今天错误验证次数大于十次则不予以请求，用户(userId:{})已调用次数：{}", userId, count);
            throw new GlobalException(ResultCode.CARD_VALIDATEEXCEED_ERROR);
        }
    }
}
