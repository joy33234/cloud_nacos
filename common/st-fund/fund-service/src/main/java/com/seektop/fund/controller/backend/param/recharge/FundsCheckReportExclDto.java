package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.common.annotation.SeektopExport;
import com.xuxueli.poi.excel.annotation.ExcelSheet;
import lombok.Data;
import org.apache.poi.hssf.util.HSSFColor;

import java.math.BigDecimal;


@ExcelSheet(name = "资金调整明细导出", headColor = HSSFColor.HSSFColorPredefined.LIGHT_GREEN)
@Data
public class FundsCheckReportExclDto {

    @SeektopExport(name = "订单号")
    private String orderId;

    @SeektopExport(name = "关联订单号")
    private String relationRechargeOrderId;

    @SeektopExport(name = "账户类型")
    private String userTypeName;

    @SeektopExport(name = "账户名称")
    private String userName;

    @SeektopExport(name = "调整类型")
    private String changeTypeName;

    @SeektopExport(name = "调整子类型")
    private String subTypeName;

    @SeektopExport(name = "调整金额")
    private BigDecimal amount;


    @SeektopExport(name = "需求流水金额")
    private BigDecimal freezeAmount;


    @SeektopExport(name = "申请人")
    private String creator;
}
