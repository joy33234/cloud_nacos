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
public class GlChannelBankDto implements Serializable {

    private Integer bankId;

    private String bankName;

    private String bankCode;

}
