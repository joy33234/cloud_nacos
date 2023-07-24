package com.seektop.fund.vo;

import com.seektop.common.annotation.SeektopExport;
import lombok.Data;

import java.math.BigDecimal;

@Data
public  class GlWithdrawExcelDto {

    @SeektopExport(name = "提现时间")
    private String createDateStr;

    @SeektopExport(name = "提现单号")
    private String orderId;

    @SeektopExport(name = "账户类型")
    private String userType;//账户类型：0玩家，1代理

    @SeektopExport(name = "用户层级")
    private String userLevelName;

    @SeektopExport(name = "账户名")
    private String userName;

    @SeektopExport(name = "收款人姓名")
    private String name;

    @SeektopExport(name = "提现金额")
    private BigDecimal amount;

    @SeektopExport(name = "手续费")
    private BigDecimal fee;

    @SeektopExport(name = "出款金额")
    private BigDecimal confirmAmount;

    @SeektopExport(name = "出款时间")
    private String lastUpdateStr;

    @SeektopExport(name = "出款类型")
    private String withdrawTypeName;

    @SeektopExport(name = "出款状态")
    private String withdrawStatus;

    @SeektopExport(name = "出款商户")
    private String merchant;

    @SeektopExport(name = "商户号")
    private String merchantCode;

    @SeektopExport(name = "出款银行")
    private String transferBankName;

    @SeektopExport(name = "出款卡号")
    private String transferBankCardNo;

    @SeektopExport(name = "出款卡姓名")
    private String transferName;

    @SeektopExport(name = "备注")
    private String remark;
}