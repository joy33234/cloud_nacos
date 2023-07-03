package com.seektop.fund.business;

import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.account.UserCoinAccountChangeDO;
import com.seektop.fund.mapper.GlFundUserCoinAccountChangeRecordMapper;
import com.seektop.fund.mapper.GlFundUserCoinAccountMapper;
import com.seektop.fund.model.GlFundUserCoinAccount;
import com.seektop.fund.model.GlFundUserCoinAccountChangeRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

/**
 * @Auther: walter
 * @Date: 8/2/20 15:45
 * @Description:
 */
@Component
@Slf4j
public class GlFundUserCoinBalanceBusiness extends AbstractBusiness<GlFundUserCoinAccount> {

    @Autowired
    private GlFundUserCoinAccountMapper glFundUserCoinAccountMapper;
    @Autowired
    private GlFundUserCoinAccountChangeRecordMapper glFundUserCoinAccountChangeRecordMapper;

    public GlFundUserCoinAccount getFundUserCoinBalance(GlUserDO glUserDO) throws GlobalException {
        Integer userId = glUserDO.getId();
        String userName = glUserDO.getUsername();
        GlFundUserCoinAccount glFundUserCoinAccount = glFundUserCoinAccountMapper.selectByPrimaryKey(userId);
        if (Objects.isNull(glFundUserCoinAccount)) {
            Date now = new Date();
            String remark = "初始化用户金币账户";
            glFundUserCoinAccount = new GlFundUserCoinAccount();
            glFundUserCoinAccount.setUserId(userId);
            glFundUserCoinAccount.setUserName(userName);
            glFundUserCoinAccount.setCoinBalance(0);
            glFundUserCoinAccount.setLastUpdate(now);
            glFundUserCoinAccount.setLastUpdateRemark(remark);
            glFundUserCoinAccountMapper.insert(glFundUserCoinAccount);
        }
        return glFundUserCoinAccount;
    }

    @Transactional(rollbackFor = GlobalException.class)
    public void doCoinBalanceChange(UserCoinAccountChangeDO userCoinAccountChangeDO, GlUserDO glUserDO, GlFundUserCoinAccount glFundUserCoinAccount) throws GlobalException {
        String tradeId = userCoinAccountChangeDO.getTradeId();
        Integer userId = userCoinAccountChangeDO.getUserId();
        int recordCount = glFundUserCoinAccountChangeRecordMapper.countByTradeId(userId, tradeId);
        if (recordCount >= 1) {
            log.error("发现重复tradeId,拒绝执行加减币操作 {}", tradeId);
            throw new GlobalException("tradeId已存在，该笔金币改动操作已处理:" + tradeId);
        }
        String userName = glUserDO.getUsername();
        // 查询数据库中已有金额，乐观锁开始
//        Integer beforeBalance = glFundUserCoinAccount.getCoinBalance();
        Integer amountChange = userCoinAccountChangeDO.getAmount();
//        Integer afterBalance = beforeBalance + amountChange;
//        if (amountChange < 0 && afterBalance < 0) {
//            throw new GlobalException("用户金币余额" + beforeBalance + "，扣减" + Math.abs(amountChange) + " 超额");
//        }
        String remark = userCoinAccountChangeDO.getRemark();
        String operator = userCoinAccountChangeDO.getOperator();
        Date changeDate = new Date();
        glFundUserCoinAccount.setLastUpdateRemark(remark);
        glFundUserCoinAccount.setLastUpdate(changeDate);
        GlFundUserCoinAccountChangeRecord glFundUserCoinAccountChangeRecord = new GlFundUserCoinAccountChangeRecord();
        glFundUserCoinAccountChangeRecord.setUserId(userId);
        glFundUserCoinAccountChangeRecord.setUserName(userName);
        glFundUserCoinAccountChangeRecord.setCreateDate(changeDate);
        glFundUserCoinAccountChangeRecord.setCreator(operator);
        glFundUserCoinAccountChangeRecord.setAmount(amountChange);
        glFundUserCoinAccountChangeRecord.setTradeId(tradeId);
//        int updated = glFundUserCoinAccountMapper.updateStatusByTradeId(userId, beforeBalance, afterBalance, changeDate, remark);
        int updated = glFundUserCoinAccountMapper.updateBalance(userId,amountChange,changeDate,remark);
        if (updated <= 0) {
            throw new GlobalException("更新金币余额异常，请重试");
        }
        GlFundUserCoinAccount coinAccount = glFundUserCoinAccountMapper.selectByPrimaryKey(userId);
        glFundUserCoinAccount.setCoinBalance(coinAccount.getCoinBalance());
        glFundUserCoinAccountChangeRecord.setBeforeBalance(coinAccount.getCoinBalance()-amountChange);
        glFundUserCoinAccountChangeRecord.setAfterBalance(coinAccount.getCoinBalance());
        glFundUserCoinAccountChangeRecordMapper.insert(glFundUserCoinAccountChangeRecord);
    }

}
