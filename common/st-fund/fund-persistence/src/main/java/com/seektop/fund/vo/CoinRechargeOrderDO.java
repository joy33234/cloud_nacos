package com.seektop.fund.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CoinRechargeOrderDO implements Serializable {

    private String orderId;

    private Integer userId;

    private String coin;

    private Integer receiveWalletId;

}