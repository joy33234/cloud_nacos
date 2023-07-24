package com.seektop.fund.service;

import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.dto.param.account.TransferDO;
import com.seektop.fund.dto.param.account.TransferRecoverDO;
import com.seektop.fund.dto.param.account.UserAccountChangeDO;
import com.seektop.fund.dto.result.account.FundUserAccountDO;
import com.seektop.report.fund.HandlerResponse;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;

public interface FundUserAccountService {

    /**
     * 获取绑定银行卡的用户信息
     *
     * @param cardNo
     * @return
     */
    RPCResponse<Set<Integer>> getBankcardUser(String cardNo);

    /**
     * 查询用户财务信息
     *
     * @param userId
     * @return
     */
    RPCResponse<FundUserAccountDO> getFundUserAccount(Integer userId);

    /**
     * 获取用户绑定的银行卡卡号
     *
     * @param userId
     * @return
     */
    RPCResponse<List<String>> getUserBankcard(Integer userId);

    /**
     * 获取用户最近是用的银行卡卡号
     *
     * @param userId
     * @return
     */
    RPCResponse<String> getUserLastBankcard(Integer userId);
    /**
     * 获取用户最近是用的银行卡卡号
     *
     * @param userId
     * @return
     */
    RPCResponse<String> getUserLastUsdtAddress(Integer userId);

    /**
     * 游戏转账
     * @param userId
     * @param orderId
     * @param amount  订单金额，必须大于0
     * @param changeType  0加币  1减币
     * @param remark
     * @return
     */
    @Deprecated
    /**
     * @See transfer(TransferDO transferDO)
     */
    HandlerResponse transfer(Integer userId, String orderId, BigDecimal amount, Integer changeType, String remark);

    /**
     * 游戏转账恢复
     * @param orderId
     * @return
     */
    @Deprecated
    /**
     * @See transferRecover(TransferRecoverDO transferRecoverDO)
     */
    HandlerResponse transferRecover(String orderId);

    void doCommissionApprove(Integer userId, BigDecimal amount,DigitalCoinEnum coinEnum, Date now) throws GlobalException;

    /**
     * 创建 fundAccount
     * @param user
     * @param creator
     */
    RPCResponse<Void> createFundAccount(GlUserDO user, DigitalCoinEnum coin, String creator);

    /**
     * 用户账变通用接口
     * @return
     */
    HandlerResponse userAccountChange(UserAccountChangeDO userAccountChangeDO);

    RPCResponse<BigDecimal> getUserAccountBalance(Integer userId);

    RPCResponse<BigDecimal> getUserAccountBalance(Integer userId, DigitalCoinEnum coinEnum);


    HandlerResponse transfer(TransferDO transferDO);

    HandlerResponse transferRecover(TransferRecoverDO transferRecoverDO);
    /**
     * 用户账户信息同步
     * @return
     */
    void syncUser(Integer userId);
}