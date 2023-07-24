package com.seektop.fund.controller.backend.param.recharge.account;

import lombok.*;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class MerchantAccountAddDO implements Serializable {

    @NotNull(message = "参数异常:channelId Not Null")
    private Integer channelId ;

    //商户号
    @NotNull(message = "参数异常:merchantCode Not Null")
    private String merchantCode;

    @NotNull(message = "参数异常:publicKey Not Null")
    private String publicKey ;

    @NotNull(message = "参数异常:privateKey Not Null")
    private String privateKey ;

    @NotNull(message = "参数异常:payUrl Not Null")
    private String payUrl ;

    @NotNull(message = "参数异常:notifyUrl Not Null")
    private String notifyUrl ;


    @NotNull(message = "参数异常:dailyLimit Not Null")
    private Integer dailyLimit;

    @NotNull(message = "参数异常:remark Not Null")
    private String remark ;

    @NotNull(message = "参数异常:limitType Not Null")
    private Integer limitType ;

    @NotNull(message = "参数异常:resultUrl Not Null")
    private String resultUrl ;

    @NotNull(message = "参数异常:enableScript Not Null")
    private Integer enableScript;

    @NotNull(message = "参数异常:limitAmount Not Null")
    private BigDecimal limitAmount;

    @NotNull(message = "参数异常:coin Not Null")
    private String coin;

}
