package com.seektop.fund.controller.forehead.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class C2CEggResult implements Serializable {

    /**
     * 是否开启彩蛋活动
     */
    private Boolean isOpen = false;

    /**
     * 记录ID
     */
    private Integer recordId;

    /**
     * 彩蛋类型
     *
     * @see com.seektop.enumerate.fund.C2CEggTypeEnum
     */
    private Short type;

    /**
     * 剩余时间
     */
    private Long ttl;

    /**
     * 奖励比例
     */
    private BigDecimal awardRate;

    /**
     * 彩蛋图标路径
     */
    private String eggIconPath;

}