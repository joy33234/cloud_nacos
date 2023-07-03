package com.seektop.fund.controller.backend.dto;

import com.seektop.common.annotation.SeektopExport;
import com.xuxueli.poi.excel.annotation.ExcelSheet;
import lombok.Getter;
import lombok.Setter;
import org.apache.poi.hssf.util.HSSFColor;

import java.math.BigDecimal;

@Getter
@Setter
@ExcelSheet(name = "充值明细", headColor = HSSFColor.HSSFColorPredefined.LIGHT_GREEN)
public class GlRechargeExcelDto {

        @SeektopExport(name = "充值时间")
        private String createDateStr;

        @SeektopExport(name = "充值单号")
        private String orderId;

        @SeektopExport(name = "第三方充值单号")
        private String thirdOrderId;

        @SeektopExport(name = "账户类型")
        private String userTypeName;

        @SeektopExport(name = "用户层级")
        private String userLevelName;

        @SeektopExport(name = "账户名")
        private String username;

        @SeektopExport(name = "会员姓名")
        private String reallyName;

        @SeektopExport(name = "存款金额")
        private BigDecimal amount;

        @SeektopExport(name = "手续费")
        private BigDecimal fee;

        @SeektopExport(name = "到账金额")
        private BigDecimal payAmount;

        @SeektopExport(name = "到账时间")
        private String payTimeStr;

        @SeektopExport(name = "充值类型")
        private String limitTypeName;

        @SeektopExport(name = "支付状态")
        private String statusName;

        @SeektopExport(name = "存款商户")
        private String merchantName;

        @SeektopExport(name = "商户号")
        private String merchantCode;

        @SeektopExport(name = "补单审核时间")
        private String sucApvTimeStr;

        @SeektopExport(name = "收款银行")
        private String bankName;

        @SeektopExport(name = "收款人姓名")
        private String cardUserName;

        @SeektopExport(name = "银行卡号")
        private String cardNo;

        @SeektopExport(name = "附言")
        private String keyword;

        @SeektopExport(name = "操作端")
        private String clientTypeName;

        @SeektopExport(name = "备注")
        private String newRemark;

        @SeektopExport(name = "关联单号")
        private String relationOrderId;

    }