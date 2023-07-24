package com.seektop.fund.business;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.mapper.BindCardRecordMapper;
import com.seektop.fund.model.BindCardRecord;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.vo.BindCardRecordForm;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Component
public class BindCardRecordBusiness extends AbstractBusiness<BindCardRecord> {

    @Resource
    private BindCardRecordMapper bindCardRecordMapper;

    public void insert(GlWithdrawUserBankCard bankCard, GlUserDO user, GlAdminDO admin){
        BindCardRecord record = new BindCardRecord();
        BeanUtils.copyProperties(bankCard, record);
        record.setUsername(user.getUsername());
        record.setCreateTime(new Date());
        record.setCreateUserId(admin.getUserId());
        record.setCreateUsername(admin.getUsername());
        save(record);
    }

    /**
     * 分页查询
     * @param form
     * @return
     */
    public PageInfo<BindCardRecord> findPage(BindCardRecordForm form){
        PageHelper.startPage(form.getPage(), form.getSize());
        List<BindCardRecord> records = bindCardRecordMapper.queryAll(form);
        return new PageInfo<>(records);
    }
}
