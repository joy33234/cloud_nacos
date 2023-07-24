package com.seektop.fund.controller.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlWaingForProcessedResult implements Serializable {
    /**
     * 申请提款条数
     */
    private Integer withdraw;
    /**
     * 风控审核条数
     */
    private Integer commission;
}
