package com.seektop.fund.dto.param.account;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@ToString
public class TransferDO implements Serializable {
  private Integer userId;
  private String orderId;
  private BigDecimal amount;
  private Integer changeType;
  private String remark;
  /**
   * true 账变可为负数， 其他不可为负数
   */
  private Boolean negative = false;
}
