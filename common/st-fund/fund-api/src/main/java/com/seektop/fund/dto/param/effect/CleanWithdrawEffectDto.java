package com.seektop.fund.dto.param.effect;

import com.seektop.dto.GlUserDO;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
public class CleanWithdrawEffectDto implements Serializable {
    private GlUserDO userDO;
    private String operator;
    private String remark;
    private String coin;
}
