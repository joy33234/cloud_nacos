package com.seektop.fund.dto.param.effect;

import com.seektop.dto.GlUserDO;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@ToString
public class EffectApproveDto implements Serializable {
    private BigDecimal revAmount;
    private String  remark;
    private String operator;
    private String orderNo;
    private GlUserDO userDO;
    private String coinCode;
}
