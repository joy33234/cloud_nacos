package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawUserCheckConfig implements Serializable {

	private static final long serialVersionUID = -3654827046573303731L;
	private int id;
	private long minTime; // 提现时间
	private long maxTime; // 提现时间
	private double min; // 金额区间
	private double max; // 金额区间
	private int num; // 会员数
	private Boolean upDate; // 是否修改或新增

	public boolean equals(GlWithdrawUserCheckConfig config) {
		return config!=null &&
				id == config.id &&
				minTime == config.minTime &&
				maxTime == config.maxTime &&
				Double.compare(config.min, min) == 0 &&
				Double.compare(config.max, max) == 0 &&
				num == config.num;
	}
}