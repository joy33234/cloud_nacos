package com.seektop.fund.controller.backend;

import com.github.pagehelper.PageInfo;
import com.seektop.common.rest.Result;
import com.seektop.fund.business.GlFundTransferRecordBusiness;
import com.seektop.fund.controller.backend.dto.GlFundTransferRecordQueryDto;
import com.seektop.fund.model.GlFundTransferRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage/fund/transrecord")
@Slf4j
public class GlFundTransferRecordController extends FundBackendBaseController{

  @Autowired
  private GlFundTransferRecordBusiness glFundTransferRecordBusiness;

  @RequestMapping("/search-logs")
  public Result searchLogs(GlFundTransferRecordQueryDto queryDto){
    PageInfo<GlFundTransferRecord> records = glFundTransferRecordBusiness.searchLogs(queryDto);
    return Result.genSuccessResult(records);
  }

  @RequestMapping("/delete-by-id")
  public Result deleteById(@RequestParam(value = "id") String id){
    boolean result = glFundTransferRecordBusiness.deleteById(id);
    return Result.genSuccessResult(result);
  }

}
