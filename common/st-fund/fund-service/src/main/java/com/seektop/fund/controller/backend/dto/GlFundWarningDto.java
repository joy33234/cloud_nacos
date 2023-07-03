package com.seektop.fund.controller.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlFundWarningDto implements Serializable {

    /**
     * 是否有低预警
     */
    private Boolean hasLow;

    /**
     * 是否有高预警
     */
    private Boolean hasHigh;

}
