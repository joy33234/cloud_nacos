package com.seektop.common.dubbo.excepton;

import com.seektop.enumerate.ResultCode;

/**
 * 自定义Dubbo异常
 */
public class DubboException extends Exception {

    /**
	 * 
	 */
	private static final long serialVersionUID = -4397699105892546180L;
	
	private int code;
    private String extraMessage;

    public DubboException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public DubboException(Throwable cause) {
        super(cause);
    }

    public DubboException(String message, Throwable cause) {
        super(message, cause);
    }

    public DubboException(int code, String message, String extraMessage, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.extraMessage = extraMessage;
    }

    public DubboException(ResultCode resultCode, String extraMessage) {
        this(resultCode.getCode(), resultCode.getMessage(), extraMessage, null);
    }

    public DubboException(String extraMessage) {
        this(ResultCode.SERVER_ERROR, extraMessage);
    }

    public int getCode() {
        return code;
    }

    public String getExtraMessage() {
        return extraMessage;
    }

}