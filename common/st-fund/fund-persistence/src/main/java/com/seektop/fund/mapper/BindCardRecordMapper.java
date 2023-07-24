package com.seektop.fund.mapper;

import com.seektop.common.mybatis.mapper.Mapper;
import com.seektop.fund.model.BindCardRecord;
import com.seektop.fund.vo.BindCardRecordForm;

import java.util.List;

public interface BindCardRecordMapper extends Mapper<BindCardRecord> {

    List<BindCardRecord> queryAll(BindCardRecordForm form);
}
