package com.seektop.fund.controller.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlFundTransferRecordQueryDto implements Serializable {
  private Date startTime;
  private Date endTime;
  private String orderId;
  private Integer userId;
  private Integer status;

  private int page = 1;

  private int size = 20;

}
