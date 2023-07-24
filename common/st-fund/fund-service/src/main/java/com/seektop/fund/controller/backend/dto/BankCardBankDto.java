package com.seektop.fund.controller.backend.dto;

import com.seektop.fund.model.GlWithdrawUserBankCard;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString(callSuper = true)
public class BankCardBankDto extends GlWithdrawUserBankCard {

    private static final long serialVersionUID = 2448377281455061866L;
    /**
     * 调用接口状态：0 接口调用失败，1 接口正常校验，2 接口返回系统级别异常（余额不足，第三方维护等）
     */
    private Integer callState;
    /**
     * 响应报文
     */
    private String returnMessage;
    /**
     * 商户channel
     */
    //private BusinessChannelEnum channel;
}