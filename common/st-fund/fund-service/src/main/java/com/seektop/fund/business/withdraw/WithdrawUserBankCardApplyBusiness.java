package com.seektop.fund.business.withdraw;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.forehead.param.userCard.ApplyBindCardDto;
import com.seektop.fund.dto.param.bankCard.BankCardApplyDto;
import com.seektop.fund.mapper.WithdrawUserBankCardApplyMapper;
import com.seektop.fund.model.GlWithdrawBank;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.model.WithdrawUserBankCardApply;
import com.seektop.report.user.UserOperationLogReport;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.service.UserManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WithdrawUserBankCardApplyBusiness extends AbstractBusiness<WithdrawUserBankCardApply> {

    @Resource
    private WithdrawUserBankCardApplyMapper mapper;
    @Autowired
    private ReportService reportService;
    @Reference(retries = 1, timeout = 5000)
    private UserManageService userManageService;

    public List<WithdrawUserBankCardApply> findByUserId(Integer userId) {
        Map<String, Object> params = new HashMap<>();
        params.put("userId", userId);
        params.put("status", 2); //2审核中
        return mapper.findBy(params);
    }

    public List<GlWithdrawUserBankCard> findCardByUserId(Integer userId) {
        List<WithdrawUserBankCardApply> list = findByUserId(userId);
        return beanCopy(list);
    }

    public List<WithdrawUserBankCardApply> findByCardNo(String cardNo) {
        Map<String, Object> params = new HashMap<>();
        params.put("cardNo", cardNo);
        params.put("status", 2); //2审核中
        return mapper.findBy(params);
    }

    public List<GlWithdrawUserBankCard> findCardByCardNo(String cardNo) {
        List<WithdrawUserBankCardApply> list = findByCardNo(cardNo);
        return beanCopy(list);
    }

    @Transactional(rollbackFor = Exception.class)
    public void saveApplyBindCard(GlUserDO user, ApplyBindCardDto bindCardDto) {
        WithdrawUserBankCardApply apply = new WithdrawUserBankCardApply();
        BeanUtils.copyProperties(bindCardDto, apply);

        // 保存申请人工绑卡的银行卡信息
        apply.setUserId(user.getId());
        apply.setStatus(2); // 审核中
        Date now = new Date();
        apply.setCreateDate(now);
        apply.setLastUpdate(now);
        mapper.insertApply(apply);

        // 保存会员操作审核
        GlUserManageDO manage = new GlUserManageDO();
        manage.setCreateTime(now);
        manage.setCreator(user.getUsername());
        manage.setOptDesc(UserConstant.UserOperateType.BIND_BANK_CARD_APPLY.getDesc());
        manage.setOptType(UserConstant.UserOperateType.BIND_BANK_CARD_APPLY.getOptType());
        manage.setStatus(0);
        manage.setUserId(user.getId());
        manage.setUsername(user.getUsername());
        manage.setUserType(user.getUserType());
        manage.setRemark("");
        manage.setOptBeforeData("");
        String endCardNo = RegExUtils.replaceAll(apply.getCardNo(), ".*(.{4})$", "$1");
        manage.setOptAfterData(String.format("姓名：%s,卡号尾数：%s", apply.getName(), endCardNo));
        String json = JSON.toJSONString(apply);
        manage.setOptData(json);
        userManageService.saveManage(manage);

        // 上报前台用户操作记录
        UserOperationLogReport report = new UserOperationLogReport();
        report.setUrl(bindCardDto.getRequestUrl());
        report.setIp(bindCardDto.getRequestIp());
        report.setClientType(bindCardDto.getHeaderOsType());
        report.setDeviceId(bindCardDto.getHeaderDeviceId());
        report.setAddress("");
        report.setCreateTime(now);
        report.setUserId(user.getId());
        report.setUsername(user.getUsername());
        report.setUserType(user.getUserType());
        report.setOperationDesc(UserConstant.UserOperateType.BIND_BANK_CARD_APPLY.getDesc());
        report.setOperationType(UserConstant.UserOperateType.BIND_BANK_CARD_APPLY.getOptType());
        report.setOptBeforeData("");
        report.setOptAfterData(String.format("{\"姓名\":\"%s\",\"卡号尾数\":\"%s\"}", apply.getName(), endCardNo));
        try {
            reportService.userOperationLogReport(report);
        }
        catch (Exception e) {
            log.error("上报用户操作记录异常", e);
        }
    }

    /**
     * 更新申请的绑卡状态和更新操作审核状态
     * @param applyDto
     * @param bank
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public void firstApprove(BankCardApplyDto applyDto, GlWithdrawBank bank) throws GlobalException {
        Integer status = applyDto.getStatus();
        String optData = updateStatus(applyDto.getOptData(), status, bank);

        GlUserManageDO manage = new GlUserManageDO();
        manage.setManageId(applyDto.getManageId());
        manage.setStatus(status);
        manage.setFirstApprover(applyDto.getApprover());
        manage.setFirstRemark(applyDto.getRemark());
        manage.setFirstTime(new Date());
        manage.setOptData(optData);
        userManageService.updateBankCardApply(manage);
    }

    /**
     * 更新申请绑卡状态
     * @param optData
     * @param status
     * @param bank
     * @return
     */
    public String updateStatus(String optData, Integer status, GlWithdrawBank bank) {
        WithdrawUserBankCardApply apply = JSON.parseObject(optData, WithdrawUserBankCardApply.class);
        if (null == apply)
            return optData;
        apply = mapper.selectByPrimaryKey(apply.getCardId());
        if (null == apply)
            return optData;
        if (ProjectConstant.UserManageStatus.FIRST_FAIL == status || ProjectConstant.UserManageStatus.SECOND_FAIL == status) {
            apply.setStatus(3); // 3已拒绝
        }
        else if (ProjectConstant.UserManageStatus.FIRST_SUCCESS == status) {
            if (null != bank) {
                apply.setBankId(bank.getBankId());
                apply.setBankName(bank.getBankName());
            }
        }
        else if (ProjectConstant.UserManageStatus.SECOND_SUCCESS == status) {
            apply.setStatus(4); // 4已通过
        }
        apply.setLastUpdate(new Date());
        mapper.updateByPrimaryKeySelective(apply);
        return JSON.toJSONString(apply);
    }

    /**
     * Copy to GlWithdrawUserBankCard
     * @param list
     * @return
     */
    private List<GlWithdrawUserBankCard> beanCopy(List<WithdrawUserBankCardApply> list){
        List<GlWithdrawUserBankCard> cards = Lists.newArrayList();
        if(!CollectionUtils.isEmpty(list)){
            GlWithdrawUserBankCard card;
            for (WithdrawUserBankCardApply apply : list) {
                card = new GlWithdrawUserBankCard();
                BeanUtils.copyProperties(apply, card);
                cards.add(card);
            }
        }
        return cards;
    }
}
