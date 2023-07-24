package com.seektop.fund.controller.forehead.param.recharge;

import com.seektop.enumerate.Language;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.DecimalMin;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AgencyRechargePaymentInfoDO implements Serializable {

    private static final long serialVersionUID = -8287681143207544776L;

    /**
     * 充值金额
     */
    @DecimalMin(value = "100", message = "单笔最小充值金额为100.0元")
    private BigDecimal amount;

    /**
     * 充值渠道(0-普通充值、1-大额充值)
     */
    private Integer limitType = 0;

    /**
     * 是否按照金额过滤(T-过滤、F-不过滤)
     */
    private Boolean amountFilter = true;

    /**
     * 是否过滤极速支付渠道(T-过滤、F-不过滤)
     */
    private Boolean quickFilter = true;

    /**
     * 待客充值使用
     */
    private String userName;

    private Integer osType;

    private String headerToken;

    private Integer headerUid;

    private Integer headerAppType;

    private String headerDeviceId;

    private Integer headerOsType;

    private String headerUserAgent;

    private String headerVersion;

    private String requestIp;

    private String requestUrl;

    private String headerHost;

    private String headerLanguage;

    /**
     * 当前选择的币种
     */
    private String headerCoinCode;

    public Language getLanguage() {
        if (StringUtils.isEmpty(this.headerLanguage)) {
            return Language.ZH_CN;
        }
        Language language = Language.getLanguage(this.headerLanguage);
        return ObjectUtils.isEmpty(language) ? Language.ZH_CN : language;
    }

}
