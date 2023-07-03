package com.seektop.fund.handler.validation;

import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.system.dto.MobileValidateDto;
import com.seektop.system.service.GlSystemApiService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@AllArgsConstructor
public class ValidationCode implements DataValidation {

    private GlUserDO user;
    private String code;
    private String type;
    private GlSystemApiService service;

    @Override
    public void valid() throws GlobalException {
        if(StringUtils.isBlank(code)){
            throw new GlobalException("请输入验证码");
        }
        MobileValidateDto validateDto = new MobileValidateDto();
        validateDto.setTelArea(user.getTelArea());
        validateDto.setMobile(user.getTelephone());
        validateDto.setType(type);
        validateDto.setCode(code);
        boolean validate = RPCResponseUtils.getData(service.mobileValidate(validateDto));
        if (!validate) {
            throw new GlobalException(ResultCode.MOBILE_VALIDATE_ERROR);
        }
        service.clearCode(user.getTelArea(), user.getTelephone(), type);
    }
}
