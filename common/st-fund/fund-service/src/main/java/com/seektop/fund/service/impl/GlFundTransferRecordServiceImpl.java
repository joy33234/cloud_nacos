package com.seektop.fund.service.impl;

import com.seektop.fund.business.GlFundTransferRecordBusiness;
import com.seektop.fund.service.GlFundTransferRecordService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Service;

import javax.annotation.Resource;
import java.util.Date;

@Slf4j
@Service(timeout = 5000, interfaceClass = GlFundTransferRecordService.class)
public class GlFundTransferRecordServiceImpl implements GlFundTransferRecordService {
  @Resource
  private GlFundTransferRecordBusiness glFundTransferRecordBusiness;

  @Override
  public void deleteLogs(Date date) {
    glFundTransferRecordBusiness.deleteLogs(date);
  }
}
