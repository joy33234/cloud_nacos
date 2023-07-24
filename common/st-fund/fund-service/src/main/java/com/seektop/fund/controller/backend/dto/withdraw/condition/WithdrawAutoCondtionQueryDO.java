package com.seektop.fund.controller.backend.dto.withdraw.condition;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Setter
@Getter
public class WithdrawAutoCondtionQueryDO implements Serializable {

    //自动出款条件名称
    private String conditionName;

    //层级
    private List<Integer> userLevel;

    /**
     * 币种
     */
    private String coin;

}
