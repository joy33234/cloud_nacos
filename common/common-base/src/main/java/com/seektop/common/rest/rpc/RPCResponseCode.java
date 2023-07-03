package com.seektop.common.rest.rpc;

public enum RPCResponseCode {

    SUCCESS(1, "SUCCESS"),
    FAIL_DEFAULT(-1, "未知错误"),
    DATA_NOSEARCH(-2, "未查询到相应条件的数据"),
    SERVER_ERROR(500, "服务器错误"),
    PARAM_ERROR(1001, "参数错误"),
    THIRD_PROVIDER_ERROR(2001, ""),

    SMS_FREQUENCY_VALIDATE_FAIL(3000, "发送短信验证码频率偏高，请稍后再试"),
    SMS_API_NOCONFIG(3001, "短信发送API未配置"),
    EMAIL_TEMPLATE_NOCONFIG(3002, "邮件验证码模版未配置"),
    SMS_CODE_EXPIRED(3003, "短信验证码已过期，请重新获取"),
    ;

    private int code;
    private String message;

    private RPCResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}