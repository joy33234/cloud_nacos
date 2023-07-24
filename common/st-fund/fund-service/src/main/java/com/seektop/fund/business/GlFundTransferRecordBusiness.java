package com.seektop.fund.business;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.controller.backend.dto.GlFundTransferRecordQueryDto;
import com.seektop.fund.mapper.GlFundTransferRecordMapper;
import com.seektop.fund.model.GlFundTransferRecord;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class GlFundTransferRecordBusiness extends AbstractBusiness<GlFundTransferRecord> {
  @Resource
  private GlFundTransferRecordMapper glFundTransferRecordMapper;
  @Reference(retries = 2, timeout = 3000)
  private GlUserService glUserService;

  public void deleteLogs(Date date){
    Condition condition = new Condition(GlFundTransferRecord.class);
    Example.Criteria criteria = condition.createCriteria();
    criteria.andEqualTo("status", 1);
    criteria.andLessThan("lastupdate", date);
    int count = glFundTransferRecordMapper.deleteByCondition(condition);
    log.info("delete success date = {}, count = {}", date, count);
  }


  public PageInfo<GlFundTransferRecord> searchLogs(GlFundTransferRecordQueryDto dto){
    PageHelper.startPage(dto.getPage(), dto.getSize());
    Condition condition = new Condition(GlFundTransferRecord.class);
    Example.Criteria criteria = condition.createCriteria();
    if(dto.getStatus() != null && dto.getStatus() < 2){
      criteria.andEqualTo("status", dto.getStatus());
    }
    if(dto.getStatus() != null && dto.getStatus() >= 2){
      criteria.andGreaterThan("status", dto.getStatus());
    }
    if(dto.getEndTime() != null){
      criteria.andLessThan("lastupdate", dto.getEndTime());
    }
    if(dto.getStartTime() != null){
      criteria.andGreaterThan("lastupdate", dto.getStartTime());
    }
    if(dto.getOrderId() != null){
      criteria.andEqualTo("orderId", dto.getOrderId());
    }
    if(dto.getUserId() != null){
      criteria.andEqualTo("userId", dto.getUserId());
    }

    List<GlFundTransferRecord> records = glFundTransferRecordMapper.selectByExample(condition);
    if(records != null && records.size() > 0){
      records.stream().forEach(r -> {
        RPCResponse<GlUserDO> dbUser = glUserService.findById(r.getUserId());
        if(dbUser.getData() != null){
          r.setUserName(dbUser.getData().getUsername());
        }
      });
    }
    return new PageInfo<>(records);
  }

  public boolean deleteById(String id){
    Condition condition = new Condition(GlFundTransferRecord.class);
    Example.Criteria criteria = condition.createCriteria();
    criteria.andEqualTo("id", id);
    criteria.andGreaterThan("status", 1);
    int count = glFundTransferRecordMapper.deleteByCondition(condition);
    log.info("delete success id = {}, count = {}", id, count);
    if(count > 0){
      return true;
    }
    return false;
  }
}
