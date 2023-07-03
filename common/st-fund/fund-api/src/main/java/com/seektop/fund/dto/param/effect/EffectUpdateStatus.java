package com.seektop.fund.dto.param.effect;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@ToString
@Data
public class EffectUpdateStatus implements Serializable {
    private String orderId;
    private Integer status;
    private String remark;
}
