package com.seektop.fund.controller.backend.dto.withdraw.condition;

import com.seektop.fund.vo.ManageParamBase;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Setter
@Getter
public class WithdrawCondtionQueryDO extends ManageParamBase implements Serializable {

    /**
     * 条件名称
     */
    private String conditionName;

    /**
     * 用户层级ID
     */
    private List<Integer> userLevel;

    /**
     * 出款类型:-1全部、0-人工出款、2-三方手动出款
     */
    private Integer withdrawType = -1;

    private String coin;

}
