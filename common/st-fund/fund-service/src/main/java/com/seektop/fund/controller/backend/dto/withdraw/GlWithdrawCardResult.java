package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawCardResult implements Serializable {

    private static final long serialVersionUID = -5616782689560067403L;
    private int cardId;
    private int bankId;
    private String bankName;
    private String cardNo;
    private String name;
    private int selected;

}
