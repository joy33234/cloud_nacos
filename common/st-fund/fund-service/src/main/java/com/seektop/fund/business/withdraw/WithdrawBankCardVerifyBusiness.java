package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.fund.controller.backend.dto.BankCardBankDto;
import com.seektop.fund.mapper.WithdrawBankCardVerifyMapper;
import com.seektop.fund.model.WithdrawBankCardVerify;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class WithdrawBankCardVerifyBusiness extends AbstractBusiness<WithdrawBankCardVerify> {
    @Resource
    private WithdrawBankCardVerifyMapper withdrawBankCardVerifyMapper;

    /**
     * 查询校验记录
     * @param cardNo
     * @return
     */
    public BankCardBankDto findCardVerify(String cardNo, String name){
        Condition con = new Condition(WithdrawBankCardVerify.class);
        con.createCriteria().andEqualTo("cardNo", cardNo);
        List<WithdrawBankCardVerify> cardList = mapper.selectByCondition(con);
        WithdrawBankCardVerify card = null;
        if (!CollectionUtils.isEmpty(cardList)) {
            card = cardList.get(0);
        }
        BankCardBankDto cardDto = null;
        if (!ObjectUtils.isEmpty(card)) {
            if (card.getName().equals(name)) {
                cardDto = new BankCardBankDto();
                cardDto.setAddress(card.getAddress());
                cardDto.setBankId(card.getBankId());
                cardDto.setBankName(card.getBankName());
                cardDto.setCardNo(card.getCardNo());
                cardDto.setName(card.getName());
                return cardDto;
            }
        }
        return cardDto;
    }

    @Async
    public void saveCardVerify(BankCardBankDto info){
        WithdrawBankCardVerify verify = new WithdrawBankCardVerify();
        verify.setAddress(info.getAddress());
        verify.setBankId(info.getBankId());
        verify.setBankName(info.getBankName());
        verify.setCardNo(info.getCardNo());
        verify.setName(info.getName());
        verify.setCreateDate(new Date());
        try {
            mapper.insertSelective(verify);
        }
        catch (Exception e) {
            log.error("保存银行卡校验信息异常", e);
        }
    }
}
