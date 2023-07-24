package com.seektop.common.encrypt.enums;

import com.seektop.common.utils.RegexValidator;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.ProjectConstant;

import java.util.Arrays;
import java.util.function.Function;


public enum EncryptTypeEnum {
    //-- 单字段单类型脱敏--------//
    MOBILE("手机号字段脱敏", StringEncryptor::encryptMobile, ProjectConstant.EncryptPermission.MOBILE),
    NAME("姓名字段脱敏", StringEncryptor::encryptUsername, ProjectConstant.EncryptPermission.NAME),
    EMAIL("邮箱字段脱敏脱敏",StringEncryptor::encryptEmail, ProjectConstant.EncryptPermission.EMAIL),
    BANKCARD("银行卡号脱敏",StringEncryptor::encryptBankCard, ProjectConstant.EncryptPermission.BANKCARD),
    ADDRESS("家庭地址脱敏", StringEncryptor::encryptAddress, ProjectConstant.EncryptPermission.ADDRESS),

    //-- 正则匹配脱敏--一个字段可以指定多个------//
    // 姓名:张三,李四,xxx
    REGEX_OPT_NAME("操作审核姓名字段正则脱敏",
            item->StringEncryptor.encryptOptFunction(item, RegexValidator.REGEX_OPERATION_NAME,
                    str->StringEncryptor.commaSplitEncrypt(str,StringEncryptor::encryptUsername)),
            ProjectConstant.EncryptPermission.NAME),
    // "name":"陈奕迅"
    REGEX_JSON_NAME("正则匹配name的json脱敏",
            item->StringEncryptor.encryptOptFunction2(item, RegexValidator.REGEX_JSON_NAME,
                    StringEncryptor::encryptUsername),
            ProjectConstant.EncryptPermission.NAME),
    REGEX_MOBILE("手机号字段则正则脱敏",
            item->StringEncryptor.encryptOpt(
                    item, Arrays.asList(
                            source->StringEncryptor.encryptOptFunction(source, RegexValidator.REGEX_EXTRACT_MOBILE,StringEncryptor::encryptMobile),
                            source->StringEncryptor.encryptOptFunction(source, RegexValidator.REGEX_TAI_MOBILE,StringEncryptor::encryptMobile),
                            source->StringEncryptor.encryptOptFunction(source, RegexValidator.REGEX_KONG_MOBILE,StringEncryptor::encryptMobile)
                    )
            ),
            ProjectConstant.EncryptPermission.MOBILE),
    REGEX_EMAIL("邮箱字段脱敏正则脱敏",
            item->StringEncryptor.encryptOptFunction(item, RegexValidator.REGEX_EXTRACT_EMAIL,StringEncryptor::encryptEmail),
            ProjectConstant.EncryptPermission.EMAIL),
    REGEX_BANKCARD("银行卡号正则脱敏",
            item->StringEncryptor.encryptOptFunction(item, RegexValidator.REGEX_EXTRACT_BANKCARD,StringEncryptor::encryptBankCard),
            ProjectConstant.EncryptPermission.BANKCARD),

    // 张三,李四,王五
    COMMA_NAME("操作审核姓名字段正则脱敏",
            item->StringEncryptor.commaSplitEncrypt(item, StringEncryptor::encryptUsername),
            ProjectConstant.EncryptPermission.NAME),
    ;
    private String name;
    private Function<String,String> parse;
    private Integer jobEncryptId;

    EncryptTypeEnum(String name, Function<String, String> parse, Integer jobEncryptId) {
        this.name = name;
        this.parse = parse;
        this.jobEncryptId = jobEncryptId;
    }

    public String getName() {
        return name;
    }

    public Function<String, String> getParse() {
        return parse;
    }

    public Integer getJobEncryptId() {
        return jobEncryptId;
    }

}
