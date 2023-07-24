package com.seektop.fund.adapter.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
public class FailureChangeFundLevelConfigDO implements Serializable {

    /**
     * 目标财务层级ID
     */
    private Integer targetLevelId;

    /**
     * 新用户允许失败次数
     */
    private Long newUserAllowFailureTimes;

    /**
     * 旧用户允许失败次数
     */
    private Long oldUserAllowFailureTimes;

}