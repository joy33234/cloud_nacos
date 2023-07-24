package com.seektop.fund.handler.validation;

import com.seektop.exception.GlobalException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

@Slf4j
@AllArgsConstructor
public class BankCardValidationArgs implements DataValidation {

    private String name;
    private String cardNo;

    @Override
    public void valid() throws GlobalException {
        if(StringUtils.isBlank(name) || StringUtils.isBlank(cardNo)){
            throw new GlobalException("请输入姓名和卡号");
        }
        if(!Pattern.matches("^[\u4e00-\u9fa5\u4dae\uE863\u0020a-zA-Z·]{1,45}$", name)){
            log.error("姓名不符合规则，姓名要符合中文英文·空格，name:{}", name);
            throw new GlobalException("姓名不符合规则，姓名要符合中文英文·空格");
        }
    }
}
