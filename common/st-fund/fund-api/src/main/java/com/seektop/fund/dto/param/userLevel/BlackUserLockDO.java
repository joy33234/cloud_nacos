package com.seektop.fund.dto.param.userLevel;

import com.seektop.dto.GlUserDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BlackUserLockDO implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 8764336362990060991L;

	/**
     * 需要锁定的用户
     */
    private List<GlUserDO> users;

    /**
     * 需要锁定的财务层级
     */
    private Integer fundLevelId;

    /**
     * 操作人
     */
    private String operator;

}