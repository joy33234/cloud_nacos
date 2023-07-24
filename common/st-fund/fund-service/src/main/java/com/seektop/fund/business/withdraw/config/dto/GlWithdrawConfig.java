package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawConfig implements Serializable {

	private static final long serialVersionUID = -3654827046573303731L;
	private int multiple; // 提现流水倍数
	private int freeTimes; // 每日可免费提现次数
	private String feeType; // 手续费类型：fix固定金额，percent百分比
	private BigDecimal fee; // 手续费
	private int feeLimit; // 最高手续费
	private String minLimit; // 提现最低限额
	private String maxLimit; // 提现最高限额
	private List<Integer> keyAmount; // 快捷金额

}