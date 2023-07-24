package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.mapper.GlPaymentChannelBankMapper;
import com.seektop.fund.model.GlPaymentChannelBank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class GlPaymentChannelBankBusiness extends AbstractBusiness<GlPaymentChannelBank> {


    @Resource
    private GlPaymentChannelBankMapper glPaymentChannelBankMapper;


    public String getBankName(Integer bankId, Integer channelId) {
        if (bankId <= 0) {
            return "其他";
        }
        GlPaymentChannelBank merchantBank = findOne(bankId,channelId);

        return merchantBank == null ? "" : merchantBank.getBankName();
    }

    public String getBankCode(Integer bankId, Integer channelId) {
        if (bankId <= 0) {
            return "";
        }
        GlPaymentChannelBank merchantBank = findOne(bankId,channelId);

        return merchantBank == null ? "" : merchantBank.getBankCode();
    }

    public GlPaymentChannelBank findOne(Integer bankId, Integer channelId){
        GlPaymentChannelBank record = new GlPaymentChannelBank();
        record.setBankId(bankId);
        record.setChannelId(channelId);
        GlPaymentChannelBank merchantBank = glPaymentChannelBankMapper.selectOne(record);
        return merchantBank;
    }

    public GlPaymentChannelBank getChannelBank(Integer channelId, String bankCode) {
        GlPaymentChannelBank record = new GlPaymentChannelBank();
        record.setBankCode(bankCode);
        record.setChannelId(channelId);
        GlPaymentChannelBank merchantBank = glPaymentChannelBankMapper.selectOne(record);
        return merchantBank;
    }

    public GlPaymentChannelBank getBank(Integer channelId, String bankName) {
        GlPaymentChannelBank record = new GlPaymentChannelBank();
        record.setBankName(bankName);
        record.setChannelId(channelId);
        GlPaymentChannelBank merchantBank = glPaymentChannelBankMapper.selectOne(record);
        return merchantBank;
    }

    public List<GlPaymentChannelBank> getChannelBank(Integer channelId) {
        return glPaymentChannelBankMapper.getChannelBank(channelId);
    }

    public GlPaymentChannelBank getBankInfo(Integer channelId, Integer bankId, Integer status) {
        GlPaymentChannelBank record = new GlPaymentChannelBank();
        record.setBankId(bankId);
        record.setChannelId(channelId);
        record.setStatus(status);
        GlPaymentChannelBank merchantBank = glPaymentChannelBankMapper.selectOne(record);
        return merchantBank;
    }

    public List<GlPaymentChannelBank> findList(Integer channelId) {
        Condition con = new Condition(GlPaymentChannelBank.class);
        Condition.Criteria criteria = con.createCriteria();
        criteria.andEqualTo("channelId", channelId);
        con.setOrderByClause("sort asc");
        return glPaymentChannelBankMapper.selectByCondition(con);
    }

    public void batchUpdate(List<GlPaymentChannelBank> bankList) {
        bankList.forEach(item -> glPaymentChannelBankMapper.updateByPrimaryKeySelective(item));
    }

    public List<GlPaymentChannelBank> findByChannelIds(List<Integer> channelIds){
        return glPaymentChannelBankMapper.findByChannelIds(channelIds);
    }
}
