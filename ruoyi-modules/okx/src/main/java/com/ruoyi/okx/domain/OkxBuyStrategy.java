package com.ruoyi.okx.domain;

import com.ruoyi.common.core.web.domain.CommonEntity;
import io.swagger.models.auth.In;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Data
public class OkxBuyStrategy extends CommonEntity {

    private Integer id;

    private String name;

    private Integer fallDays;

    private BigDecimal fallPercent;

    private Integer times;

    private Integer level;

    private Integer holdMaxTimes;

    private Integer accountId;



//
//    public Integer getId() {
//        return id;
//    }
//
//    public void setId(Integer id) {
//        this.id = id;
//    }
//
//    @NotBlank(message = "参数名称不能为空")
//    @Size(min = 0,  message = "参数名称不能超过100个字符")
//    public String getName() {
//        return name;
//    }
//
//    public void setName(String name) {
//        this.name = name;
//    }
//
//    public Integer getFallDays() {
//        return fallDays;
//    }
//
//    public void setFallDays(Integer fallDays) {
//        this.fallDays = fallDays;
//    }
//
//    public BigDecimal getFallPercent() {
//        return fallPercent;
//    }
//
//    public void setFallPercent(BigDecimal fallPercent) {
//        this.fallPercent = fallPercent;
//    }
//
//    public BigDecimal getTimes() {
//        return times;
//    }
//
//    public void setTimes(BigDecimal times) {
//        this.times = times;
//    }
//
//    public Integer getLevel() {
//        return level;
//    }
//
//    public void setLevel(Integer level) {
//        this.level = level;
//    }
//
//    public Integer getHoldMaxTimes() {
//        return holdMaxTimes;
//    }
//
//    public void setHoldMaxTimes(Integer holdMaxTimes) {
//        this.holdMaxTimes = holdMaxTimes;
//    }
//
//    @NotBlank(message = "参数帐号ID不能为空")
//    public Integer getAccountId() {
//        return accountId;
//    }
//
//    public void setAccountId(Integer accountId) {
//        this.accountId = accountId;
//    }
//
//    @NotBlank(message = "参数订单类型不能为空")
//    public Integer getOrdType() {
//        return ordType;
//    }
//
//    public void setOrdType(Integer ordType) {
//        this.ordType = ordType;
//    }
}
