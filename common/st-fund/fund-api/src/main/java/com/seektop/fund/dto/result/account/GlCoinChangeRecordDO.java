package com.seektop.fund.dto.result.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GlCoinChangeRecordDO implements Serializable {
  private Integer recordId;

  /**
   * 交易id
   */
  private String tradeId;

  private Integer userId;

  private String userName;

  /**
   * 余额
   */
  private Integer amount;

  /**
   * 变动前余额
   */
  private Integer beforeBalance;

  /**
   * 变动后余额
   */
  private Integer afterBalance;

  /**
   * 更新时间
   */
  private Date createDate;

  /**
   * 更新人
   */
  private String creator;
}
