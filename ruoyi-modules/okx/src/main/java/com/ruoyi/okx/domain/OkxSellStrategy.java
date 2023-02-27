//package com.ruoyi.okx.domain;
//
//import com.ruoyi.common.core.web.domain.CommonEntity;
//import lombok.Data;
//
//import javax.validation.constraints.NotBlank;
//import javax.validation.constraints.Size;
//import java.math.BigDecimal;
//
//@Data
//public class OkxSellStrategy extends CommonEntity {
//
//    private Integer id;
//
//    private String name;
//
//    private Integer riseDays;
//
//    private BigDecimal risePercent;
//
//    private BigDecimal times;
//
//    private Integer level;
//
//    private Integer accountId;
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
//    @NotBlank(message = "参数上涨天数不能为空")
//    @Size(min = 0,  message = "参数上涨天数最低值为0")
//    public Integer getRiseDays() {
//        return riseDays;
//    }
//
//    public void setRiseDays(Integer riseDays) {
//        this.riseDays = riseDays;
//    }
//
//    @NotBlank(message = "参数上涨百分比不能为空")
//    public BigDecimal getRisePercent() {
//        return risePercent;
//    }
//
//    public void setRisePercent(BigDecimal risePercent) {
//        this.risePercent = risePercent;
//    }
//
//    @NotBlank(message = "参数上涨百分比不能为空")
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
//    @NotBlank(message = "参数帐号ID不能为空")
//    public Integer getAccountId() {
//        return accountId;
//    }
//
//    public void setAccountId(Integer accountId) {
//        this.accountId = accountId;
//    }
//
//    @Override
//    public String toString() {
//        return "OkxSellStrategy{" +
//                "id=" + id +
//                ", riseDays=" + riseDays +
//                ", risePercent=" + risePercent +
//                ", times=" + times +
//                ", level=" + level +
//                ", accountId=" + accountId +
//                '}';
//    }
//}
