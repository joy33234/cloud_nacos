package com.seektop.fund.dto.param.account;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
public class TransferRecoverDO implements Serializable {
  private String orderId;
  /**
   * true 账变可为负数， 其他不可为负数
   */
  private Boolean negative = false;
}
