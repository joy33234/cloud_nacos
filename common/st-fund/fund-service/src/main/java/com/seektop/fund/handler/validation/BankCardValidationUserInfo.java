package com.seektop.fund.handler.validation;

import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@AllArgsConstructor
public class BankCardValidationUserInfo implements DataValidation {

    private GlUserDO user;
    private String name;

    @Override
    public void valid() throws GlobalException {
        if (UserConstant.Type.PROXY == user.getUserType()) {
            if (StringUtils.isEmpty(user.getTelephone())) {
                throw new GlobalException("请先绑定手机号");
            }
        }
        else{
            if (StringUtils.isNotBlank(name) && StringUtils.isNotBlank(user.getReallyName()) && !name.equals(user.getReallyName())) {
                log.error("用户真实姓名和持卡姓名不匹配，user.reallyName:{},name:{}", user.getReallyName(), name);
                throw new GlobalException(ResultCode.NAME_VALIDATE_ERROR);
            }
        }
    }
}
