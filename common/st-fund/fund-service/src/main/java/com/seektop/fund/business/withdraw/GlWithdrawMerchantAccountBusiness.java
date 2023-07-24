package com.seektop.fund.business.withdraw;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.redis.RedisService;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.NameTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundBusiness;
import com.seektop.fund.business.GlPaymentChannelBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.withdraw.ValidMerchantAccountForm;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawMerchantAccountDO;
import com.seektop.fund.controller.backend.dto.withdraw.merchant.*;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.mapper.GlWithdrawMerchantAccountMapper;
import com.seektop.fund.model.GlPaymentChannel;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.model.WithdrawAutoConditionMerchantAccount;
import com.seektop.fund.payment.groovy.GroovyScriptUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.seektop.constant.fund.Constants.FUND_COMMON_ON;

@Component
@Slf4j
public class GlWithdrawMerchantAccountBusiness extends AbstractBusiness<GlWithdrawMerchantAccount> {

    @Resource
    private GlWithdrawMerchantAccountMapper glWithdrawMerchantAccountMapper;

    @Resource
    private GlWithdrawMapper glWithdrawMapper;

    @Resource
    private GlPaymentChannelBusiness paymentChannelBusiness;

    @Resource
    private RedisService redisService;

    @Resource
    private GlWithdrawMerchantAccountModifyLogBusiness modifyLogBusiness;
    @Autowired
    private WithdrawAutoConditionMerchantAccountBusiness conditionMerchantAccountBusiness;
    @Resource
    private GlFundBusiness glFundBusiness;


    public List<GlWithdrawMerchantAccount> findMerchantList(List<String> ids, Integer status, BigDecimal amount, Integer bankId, Integer nameType) {
        Condition condition = new Condition(GlWithdrawMerchantAccount.class);
        Condition.Criteria criteria = condition.createCriteria();
        if (ids != null && !ids.isEmpty()) {
            criteria.andIn("merchantId", ids);
        }
        if (status != null) {
            criteria.andEqualTo("status", status);
        }
        if (bankId != null) {
            criteria.andCondition("(find_in_set('" + bankId + "',bank_id))");
        }
        if (nameType != null && nameType != NameTypeEnum.ALL.getType()) {
            criteria.andCondition("(find_in_set('" + nameType + "',name_type))");
        }
        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            criteria.andLessThanOrEqualTo("minAmount", amount);
            criteria.andGreaterThanOrEqualTo("maxAmount", amount);
        }
        return findByCondition(condition);
    }


    public long getMerchantSuccess(Integer merchantId) {
        Date now = new Date();
        String day = DateUtils.format(now, DateUtils.YYYYMMDD);
        Long success = redisService.get(RedisKeyHelper.WITHDRAW_SUCCESS_AMOUNT + day + "_" + merchantId, Long.class);
        return success == null ? 0 : success;
    }

    public PageInfo<GlWithdrawMerchantAccount> findMerchantAccountList(WithdrawMerchantAccountQueryDO dto) {
        PageHelper.startPage(dto.getPage(), dto.getSize());
        Condition con = new Condition(GlWithdrawMerchantAccount.class);
        con.excludeProperties("script");
        Example.Criteria criteria = con.createCriteria();
        if (-1 != dto.getChannelId()) {
            criteria.andEqualTo("channelId", dto.getChannelId());
        }
        if (!ObjectUtils.isEmpty(dto.getStatus()) && -1 != dto.getStatus()) {
            criteria.andEqualTo("status", dto.getStatus());
        } else {
            criteria.andNotEqualTo("status", 2);
        }
        if (!ObjectUtils.isEmpty(dto.getOpenStatus()) && -1 != dto.getOpenStatus()) {
            criteria.andEqualTo("openStatus", dto.getOpenStatus());
        }
        if (!ObjectUtils.isEmpty(dto.getEnableScript()) && -1 != dto.getEnableScript()) {
            criteria.andEqualTo("enableScript", dto.getEnableScript());
        }
        if (!ObjectUtils.isEmpty(dto.getCoin()) && "-1" != dto.getCoin()) {
            criteria.andEqualTo("coin", dto.getCoin());
        }
        con.setOrderByClause("open_status asc,status asc,last_update desc");
        List<GlWithdrawMerchantAccount> list = findByCondition(con);
        for (GlWithdrawMerchantAccount account : list) {
            account.setPrivateKey(StringEncryptor.encryptKey(account.getPrivateKey()));
            account.setPublicKey(StringEncryptor.encryptKey(account.getPublicKey()));
            account.setSuccessAmount(BigDecimal.valueOf(this.getMerchantSuccess(account.getMerchantId())));
        }
        return new PageInfo(list);
    }

    public List<GlWithdrawMerchantAccount> findValidMerchantAccount() {
        WithdrawMerchantAccountQueryDO queryDO = new WithdrawMerchantAccountQueryDO();
        queryDO.setPage(1);
        queryDO.setSize(100);
        queryDO.setStatus(0);
        queryDO.setOpenStatus(0);
        queryDO.setOrder("create_date desc");
        return findMerchantAccount(queryDO);
    }

    public List<WithdrawMerchantAccountDO> findValidMerchantAccount(ValidMerchantAccountForm form){
        List<GlWithdrawMerchantAccount> list = findValidMerchantAccount();
        List<WithdrawAutoConditionMerchantAccount> merchantAccounts = ObjectUtils.isEmpty(form.getConditionId()) ?
                Lists.newArrayList() : conditionMerchantAccountBusiness.findByConditionId(form.getConditionId());
        return list.stream().map(a -> {
            WithdrawMerchantAccountDO accountDO = new WithdrawMerchantAccountDO();
            BeanUtils.copyProperties(a, accountDO);
            merchantAccounts.stream()
                    .filter(mr -> mr.getMerchantId().equals(a.getMerchantId()))
                    .findFirst().ifPresent(accountDO::setMerchantAccount);
            return accountDO;
        }).collect(Collectors.toList());
    }

    public List<GlWithdrawMerchantAccount> findMerchantAccount(WithdrawMerchantAccountQueryDO queryDO) {
        PageHelper.startPage(queryDO.getPage(), queryDO.getSize());
        Condition con = new Condition(GlWithdrawMerchantAccount.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andEqualTo("status", queryDO.getStatus());
        criteria.andEqualTo("openStatus", queryDO.getOpenStatus());
        con.setOrderByClause(queryDO.getOrder());
        List<GlWithdrawMerchantAccount> list = findByCondition(con);
        for (GlWithdrawMerchantAccount account : list) {
            account.setPrivateKey(StringEncryptor.encryptKey(account.getPrivateKey()));
            account.setPublicKey(StringEncryptor.encryptKey(account.getPublicKey()));
        }
        return list;
    }

    /**
     * 查询已开启已上架所有商户
     *
     * @return
     */
    public List<GlWithdrawMerchantAccount> findSeperatordMerchantAccount(ParamBaseDO paramBaseDO) {

        WithdrawMerchantAccountQueryDO queryDO = new WithdrawMerchantAccountQueryDO();
        queryDO.setPage(1);
        queryDO.setSize(1000);
        queryDO.setStatus(0);
        queryDO.setOpenStatus(0);
        queryDO.setOrder("last_update desc");

        List<GlWithdrawMerchantAccount> list = findMerchantAccount(queryDO);
        for (GlWithdrawMerchantAccount glWithdrawMerchantAccount : list) {
            glWithdrawMerchantAccount.setDisplayName(glWithdrawMerchantAccount.getChannelName() + "-" + glWithdrawMerchantAccount.getMerchantCode());
        }
        return list;
    }

    /**
     * 根据提现订单查询商户
     *
     * @return
     */
    public List<GlWithdrawMerchantAccount> findOrderMerchantAccount(WithdrawMerchantSearchDO searchDO) throws GlobalException {

        GlWithdraw dbWithdraw = glWithdrawMapper.selectByPrimaryKey(searchDO.getOrderId());
        if (null == dbWithdraw ||
                (FundConstant.WithdrawStatus.PENDING != dbWithdraw.getStatus()
                        && FundConstant.WithdrawStatus.AUTO_FAILED != dbWithdraw.getStatus()
                        && FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT != dbWithdraw.getStatus())) {
            throw new GlobalException(ResultCode.DATA_ERROR, "提现记录不存在或不可操作");
        }
        NameTypeEnum nameTypeEnum =  glFundBusiness.getNameType(dbWithdraw.getName());

        List<GlWithdrawMerchantAccount> list = this.findMerchantList(Collections.emptyList(), 0, dbWithdraw.getAmount(), dbWithdraw.getBankId(), nameTypeEnum.getType());
        //极速提现
        if (dbWithdraw.getAisleType() != 4) {
            list = list.stream().filter(obj -> !obj.getChannelId().equals(FundConstant.PaymentChannel.C2CPay)).collect(Collectors.toList());
        } else {
            list = list.stream().filter(obj -> obj.getChannelId().equals(FundConstant.PaymentChannel.C2CPay)).collect(Collectors.toList());
        }
        //数字币提现
        if (dbWithdraw.getCoin().equals(DigitalCoinEnum.CNY.getCode())) {
            list = list.stream().filter(obj -> !obj.getChannelId().equals(FundConstant.PaymentChannel.DigitalPay)).collect(Collectors.toList());
        } else {
            list = list.stream().filter(obj -> obj.getChannelId().equals(FundConstant.PaymentChannel.DigitalPay)).collect(Collectors.toList());
        }

        //过滤超过商户日限额
        List<GlWithdrawMerchantAccount> avalibleList = list.stream().filter(account -> (BigDecimal.valueOf(this.getMerchantSuccess(account.getMerchantId()))
                .add(dbWithdraw.getAmount()).compareTo(BigDecimal.valueOf(account.getDailyLimit())) < 1)).collect(Collectors.toList());

        avalibleList.stream().map(accout -> {
            accout.setPrivateKey(StringEncryptor.encryptKey(accout.getPrivateKey()));
            accout.setPublicKey(StringEncryptor.encryptKey(accout.getPublicKey()));
            accout.setDisplayName(accout.getChannelName() + "-" + accout.getMerchantCode());
            return accout;
        }).collect(Collectors.toList());
        return avalibleList;
    }


    public List<GlWithdrawMerchantAccount> findAllMerchantaccount() {
        Condition con = new Condition(GlWithdrawMerchantAccount.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.orNotEqualTo("status", 2);
        con.setOrderByClause("create_date desc");
        List<GlWithdrawMerchantAccount> list = findByCondition(con);
        for (GlWithdrawMerchantAccount account : list) {
            account.setPrivateKey(StringEncryptor.encryptKey(account.getPrivateKey()));
            account.setPublicKey(StringEncryptor.encryptKey(account.getPublicKey()));
        }
        return list;
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void save(WithdrawMerchantAccountAddDO addDO, GlAdminDO adminDO) throws GlobalException {
        GlPaymentChannel paymentChannel = paymentChannelBusiness.findById(addDO.getChannelId());
        if (null == paymentChannel) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        if (addDO.getMaxAmount().compareTo(addDO.getMinAmount()) < 1) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        Date now = new Date();

        GlWithdrawMerchantAccount merchantAccount = new GlWithdrawMerchantAccount();
        merchantAccount.setChannelId(paymentChannel.getChannelId());
        merchantAccount.setChannelName(paymentChannel.getChannelName());
        merchantAccount.setMerchantCode(addDO.getMerchantCode());
        merchantAccount.setPayUrl(addDO.getPayUrl());
        merchantAccount.setNotifyUrl(addDO.getNotifyUrl());
        merchantAccount.setPublicKey(addDO.getPublicKey());
        merchantAccount.setPrivateKey(addDO.getPrivateKey());
        merchantAccount.setMerchantFeeType(addDO.getMerchantFeeType());
        merchantAccount.setMerchantFee(addDO.getMerchantFee());
        merchantAccount.setDailyLimit(addDO.getDailyLimit());
        merchantAccount.setRemark(addDO.getRemark());
        merchantAccount.setMinAmount(addDO.getMinAmount());
        merchantAccount.setMaxAmount(addDO.getMaxAmount());
        merchantAccount.setStatus(1);
        merchantAccount.setOpenStatus(0);
        merchantAccount.setCreateDate(now);
        merchantAccount.setCreator(adminDO.getUsername());
        merchantAccount.setLastUpdate(now);
        merchantAccount.setLastOperator(adminDO.getUsername());
        merchantAccount.setEnableScript(addDO.getEnableScript());
        merchantAccount.setBankId(addDO.getBankId());
        merchantAccount.setNameType(StringUtils.join(addDO.getNameType(),","));
        glWithdrawMerchantAccountMapper.insert(merchantAccount);

        modifyLogBusiness.saveModifyLog(merchantAccount, adminDO, FundConstant.AccountModifyType.ADD);
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void edit(WithdrawMerchantAccountEditDO editDO, GlAdminDO adminDO) throws GlobalException {

        GlWithdrawMerchantAccount withdrawMerchantAccount = glWithdrawMerchantAccountMapper.selectByPrimaryKey(editDO.getMerchantId());
        if (withdrawMerchantAccount == null || withdrawMerchantAccount.getStatus() == 2) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }

        GlPaymentChannel paymentChannel = paymentChannelBusiness.findById(editDO.getChannelId());
        if (null == paymentChannel) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        withdrawMerchantAccount.setChannelId(paymentChannel.getChannelId());
        withdrawMerchantAccount.setChannelName(paymentChannel.getChannelName());
        //商户号不支持修改
        withdrawMerchantAccount.setPayUrl(editDO.getPayUrl());
        withdrawMerchantAccount.setNotifyUrl(editDO.getNotifyUrl());
        //密钥信息不支持修改
        withdrawMerchantAccount.setMerchantFeeType(editDO.getMerchantFeeType());
        withdrawMerchantAccount.setMerchantFee(editDO.getMerchantFee());
        withdrawMerchantAccount.setDailyLimit(editDO.getDailyLimit());
        withdrawMerchantAccount.setRemark(editDO.getRemark());

        withdrawMerchantAccount.setLastUpdate(new Date());
        withdrawMerchantAccount.setLastOperator(adminDO.getUsername());
        withdrawMerchantAccount.setMinAmount(editDO.getMinAmount());
        withdrawMerchantAccount.setMaxAmount(editDO.getMaxAmount());
        withdrawMerchantAccount.setBankId(editDO.getBankId());
        withdrawMerchantAccount.setEnableScript(editDO.getEnableScript());
        withdrawMerchantAccount.setNameType(StringUtils.join(editDO.getNameType(),","));
        glWithdrawMerchantAccountMapper.updateByPrimaryKeySelective(withdrawMerchantAccount);

        modifyLogBusiness.saveModifyLog(withdrawMerchantAccount, adminDO, FundConstant.AccountModifyType.UPDATE);
    }


    /**
     * 获取商户脚本
     *
     * @param merchantId
     * @return
     * @throws GlobalException
     */
    public String getScript(Integer merchantId) throws GlobalException {
        GlWithdrawMerchantAccount merchantaccount = glWithdrawMerchantAccountMapper.selectByPrimaryKey(merchantId);
        if (merchantaccount == null || !Objects.equals(FUND_COMMON_ON, merchantaccount.getEnableScript())) {
            throw new GlobalException("商户不存在或未启用脚本");
        }
        return merchantaccount.getScript();
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void editAccountScript(WithdrawMerchantAccountEditScriptDO editScriptDO, GlAdminDO adminDO) throws GlobalException {

        GlPaymentChannel paymentChannel = paymentChannelBusiness.findById(editScriptDO.getChannelId());
        if (null == paymentChannel) {
            throw new GlobalException("提现渠道不存在");
        }

        GlWithdrawMerchantAccount withdrawMerchantAccount = glWithdrawMerchantAccountMapper.selectByPrimaryKey(editScriptDO.getMerchantId());
        if (withdrawMerchantAccount == null || withdrawMerchantAccount.getStatus() == 2) {
            throw new GlobalException("出款商户不存在或不可操作");
        }

        if (!Objects.equals(FUND_COMMON_ON, withdrawMerchantAccount.getEnableScript())
                || StringUtils.isBlank(editScriptDO.getScript())) {
            throw new GlobalException("脚本未启用或为空");
        }
        String md5Digest = MD5.md5(editScriptDO.getScript());
        // 尝试加载脚本，脚本加载失败时不允许保存
        if (StringUtils.isNotBlank(editScriptDO.getScript())) {
            if (!GroovyScriptUtil.validateScript(editScriptDO.getMerchantId(), editScriptDO.getScript(), md5Digest)) {
                log.error("加载动态脚本异常 {} {}", editScriptDO.getScript());
                throw new GlobalException("加载动态脚本异常");
            }
        }

        GlWithdrawMerchantAccount update = new GlWithdrawMerchantAccount();
        update.setMerchantId(editScriptDO.getMerchantId());
        update.setLastUpdate(new Date());
        update.setLastOperator(adminDO.getUsername());
        update.setScript(editScriptDO.getScript());
        update.setScriptSign(MD5.md5(editScriptDO.getScript()));
        glWithdrawMerchantAccountMapper.updateByPrimaryKeySelective(update);

        withdrawMerchantAccount.setScript(editScriptDO.getScript());
        modifyLogBusiness.saveModifyLog(withdrawMerchantAccount, adminDO , FundConstant.AccountModifyType.UPDATE_SCRIPT);
    }

    public void updateStatus(WithdrawMerchantUpdateSatusDO updateSatusDO, GlAdminDO adminDO) throws GlobalException {
        GlWithdrawMerchantAccount withdrawMerchantAccount = glWithdrawMerchantAccountMapper.selectByPrimaryKey(updateSatusDO.getMerchantId());
        if (withdrawMerchantAccount == null || withdrawMerchantAccount.getStatus() == 2) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        withdrawMerchantAccount.setStatus(updateSatusDO.getStatus());
        withdrawMerchantAccount.setLastOperator(adminDO.getUsername());
        withdrawMerchantAccount.setLastUpdate(new Date());

        glWithdrawMerchantAccountMapper.updateByPrimaryKey(withdrawMerchantAccount);
    }

    public void updateOpenStatus(WithdrawMerchantUpdateSatusDO updateSatusDO, GlAdminDO adminDO) throws GlobalException {
        GlWithdrawMerchantAccount withdrawMerchantAccount = glWithdrawMerchantAccountMapper.selectByPrimaryKey(updateSatusDO.getMerchantId());
        if (withdrawMerchantAccount == null || withdrawMerchantAccount.getStatus() == 2) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        withdrawMerchantAccount.setOpenStatus(updateSatusDO.getStatus());
        withdrawMerchantAccount.setLastOperator(adminDO.getUsername());
        withdrawMerchantAccount.setLastUpdate(new Date());

        glWithdrawMerchantAccountMapper.updateByPrimaryKey(withdrawMerchantAccount);
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void delete(WithdrawMerchantAccountDeleteDO deleteDO, GlAdminDO adminDO) throws GlobalException {
        GlWithdrawMerchantAccount withdrawMerchantAccount = glWithdrawMerchantAccountMapper.selectByPrimaryKey(deleteDO.getMerchantId());
        if (withdrawMerchantAccount == null || withdrawMerchantAccount.getStatus() == 2) {
            throw new GlobalException(ResultCode.PARAM_ERROR);
        }
        withdrawMerchantAccount.setStatus(2);
        withdrawMerchantAccount.setLastOperator(adminDO.getUsername());
        withdrawMerchantAccount.setLastUpdate(new Date());

        glWithdrawMerchantAccountMapper.updateByPrimaryKey(withdrawMerchantAccount);

        modifyLogBusiness.saveModifyLog(withdrawMerchantAccount, adminDO, FundConstant.AccountModifyType.DELETE);
    }

    public GlWithdrawMerchantAccount getWithdrawMerchant(Integer merchantId) {
        Condition con = new Condition(GlWithdrawMerchantAccount.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andEqualTo("status", 0);
        criteria.andEqualTo("openStatus", 0);
        criteria.andEqualTo("merchantId", merchantId);
        return glWithdrawMerchantAccountMapper.selectOneByExample(con);
    }

}
