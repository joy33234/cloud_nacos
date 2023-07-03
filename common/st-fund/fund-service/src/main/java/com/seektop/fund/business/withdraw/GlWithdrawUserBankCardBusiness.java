package com.seektop.fund.business.withdraw;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.dto.BankCardOperatingDto;
import com.seektop.fund.controller.backend.result.UserBankCardListResult;
import com.seektop.fund.dto.param.bankCard.BankCardApplyDto;
import com.seektop.fund.dto.param.bankCard.DeleteBankCardDto;
import com.seektop.fund.dto.param.bankCard.ResetBankCardDto;
import com.seektop.fund.handler.validation.Validator;
import com.seektop.fund.mapper.GlWithdrawUserBankCardMapper;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.model.WithdrawUserBankCardApply;
import com.seektop.fund.vo.UserBindBankDO;
import com.seektop.fund.vo.UserBindQueryDO;
import com.seektop.report.user.UserBankCardReport;
import com.seektop.report.user.UserOperationLogReport;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.dto.UserReallyNameDto;
import com.seektop.user.service.GlUserService;
import com.seektop.user.service.UserManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
public class GlWithdrawUserBankCardBusiness extends AbstractBusiness<GlWithdrawUserBankCard> {

    @Resource
    private RedisService redisService;
    @Resource
    private GlWithdrawUserBankCardMapper glWithdrawUserbankcardMapper;
    @Reference(retries = 2, timeout = 3000)
    private GlUserService userService;
    @Reference(retries = 2, timeout = 3000)
    private UserManageService userManageService;
    @Autowired
    private ReportService reportService;
    @Resource
    private DynamicKey dynamicKey;
    @Resource
    private UserVipUtils userVipUtils;

    @Autowired
    private WithdrawUserBankCardApplyBusiness bankCardApplyBusiness;

    /**
     * 查询银行卡信息
     *
     * @param cardNo
     * @return
     */
    public GlWithdrawUserBankCard findByCardNo(String cardNo) {
        Condition con = new Condition(GlWithdrawUserBankCard.class);
        con.createCriteria().andEqualTo("cardNo", cardNo).andEqualTo("status", 0);
        List<GlWithdrawUserBankCard> cardList = findByCondition(con);
        if (null != cardList && !cardList.isEmpty()) {
            return cardList.get(0);
        }
        return null;
    }

    /**
     * 查询指定银行卡号
     *
     * @param cardNo
     * @return
     */
    public List<GlWithdrawUserBankCard> findWithdrawBankCardByCardNo(String cardNo) {
        Condition con = new Condition(GlWithdrawUserBankCard.class);
        con.createCriteria()
                .andEqualTo("cardNo", cardNo)
                .andEqualTo("status", 0);
        return findByCondition(con);
    }

    /**
     * 查询用户有效的银行卡信息
     *
     * @param userId
     * @return
     */
    public List<GlWithdrawUserBankCard> findUserActiveCardList(Integer userId) {
        return glWithdrawUserbankcardMapper.findUserActiveCardList(userId);
    }

    /**
     * @param userId
     * @return
     */
    public List<GlWithdrawUserBankCard> findUserCards(Integer userId) {
        if (ObjectUtils.isEmpty(userId)) {
            return new ArrayList<>();
        }

        List<GlWithdrawUserBankCard> glWithdrawUserBankCards = null;

        RedisResult<GlWithdrawUserBankCard> userBankCardRedisResult = redisService.getListResult(RedisKeyHelper.USER_BANK_CARD_INFO + userId, GlWithdrawUserBankCard.class);

        if (ObjectUtils.isEmpty(userBankCardRedisResult.getListResult())) {
            glWithdrawUserBankCards = glWithdrawUserbankcardMapper.findUserCards(userId, 0);
            redisService.set(RedisKeyHelper.USER_BANK_CARD_INFO + userId, glWithdrawUserBankCards, 5 * 60);
        } else {
            glWithdrawUserBankCards = userBankCardRedisResult.getListResult();
        }
        return glWithdrawUserBankCards;
    }

    public void doUserCardSelect(GlWithdrawUserBankCard userCard) {
        List<GlWithdrawUserBankCard> cardList = findUserCards(userCard.getUserId());
        if (cardList == null || cardList.isEmpty()) {
            return;
        }
        Date now = new Date();
        boolean found = false;
        List<GlWithdrawUserBankCard> userBankCardList = new ArrayList<>(cardList.size());
        for (GlWithdrawUserBankCard dbCard : cardList) {
            GlWithdrawUserBankCard card = new GlWithdrawUserBankCard();
            card.setCardId(dbCard.getCardId());
            card.setSelected(0);
            card.setLastUpdate(now);
            if (dbCard.getCardId().equals(userCard.getCardId())) {
                card.setSelected(1);
                found = true;
            }
            userBankCardList.add(card);
        }
        if (found == true) {
            for (GlWithdrawUserBankCard card : userBankCardList) {
                glWithdrawUserbankcardMapper.updateByPrimaryKeySelective(card);
            }
        }
    }

    public List<GlWithdrawUserBankCard> findUserCardList(Integer userId) {
        return glWithdrawUserbankcardMapper.findUserCardList(userId);
    }

  /*  public List<GlWithdrawUserBankCard> findByData(String username, String reallyName, String cardNo, Integer userType, Integer dateType, Date fromDate, Date endDate, Integer bankId, Integer status, Integer page, Integer size)throws GlobalException {
        //TODO:need userdata service
        List<UserBankCard> list = glUserDataService.findUserBankCard(username, reallyName, cardNo, userType, dateType, fromDate, endDate, bankId, status, page, size);
        List<GlWithdrawUserBankCard> result = new ArrayList<>();
        if (list != null && !list.isEmpty()) {
            for (UserBankCard card : list) {
                GlWithdrawUserBankCard bankCard = new GlWithdrawUserBankCard();
                BeanUtils.copyProperties(card, bankCard);
                result.add(bankCard);
            }
        }
        return result;
    }

    public long countByData(String username, String reallyName, String cardNo, Integer userType, Integer dateType, Date fromDate, Date endDate, Integer bankId, Integer status) {
        //TODO:need userdata service
        return glUserDataService.countUserBankCard(username, reallyName, cardNo, userType, dateType, fromDate, endDate, bankId, status);
    }*/

    public UserBankCardListResult getUserBankCardInfo(GlUserDO user) {
        UserBankCardListResult result = new UserBankCardListResult();
        result.setName(StringEncryptor.encryptUsername(user.getReallyName()));
        // 检查用户是否有删除银行卡的权限
        Boolean canDelete = false;
        if (user.getUserType() == UserTypeEnum.PROXY.code()) {
            canDelete = true;
        } else {
            UserVIPCache vipCache = userVipUtils.getUserVIPCacheFromDB(user.getId());
            JSONArray vipLevelConfigArray = dynamicKey.getDynamicValue(DynamicKey.Key.USER_BANKCARD_DELETE_VIP_LEVEL, JSONArray.class);
            canDelete = vipLevelConfigArray.contains(vipCache.getVipLevel());
        }
        // 用户已经绑定的银行卡
        List<GlWithdrawUserBankCard> cardList = findUserActiveCardList(user.getId());
        if (CollectionUtils.isEmpty(cardList) == false) {
            // 只有最后一张卡时不允许删除
            if (cardList.size() <= 1 && user.getUserType() == UserTypeEnum.MEMBER.code()) {
                canDelete = false;
            }
            for (GlWithdrawUserBankCard bankCard : cardList) {
                bankCard.setCanDelete(canDelete);
                bankCard.setName(StringEncryptor.encryptUsername(bankCard.getName()));
                bankCard.setCardNo(StringEncryptor.encryptBankCard(bankCard.getCardNo()));
            }
        }
        // 用户申请人工绑卡的银行卡
        List<GlWithdrawUserBankCard> applyCards = bankCardApplyBusiness.findCardByUserId(user.getId());
        if (CollectionUtils.isEmpty(applyCards) == false) {
            for (GlWithdrawUserBankCard bankCard : applyCards) {
                bankCard.setCanDelete(false);
                bankCard.setName(StringEncryptor.encryptUsername(bankCard.getName()));
                bankCard.setCardNo(StringEncryptor.encryptBankCard(bankCard.getCardNo()));
            }
        }

        if (CollectionUtils.isEmpty(cardList) == false) {
            cardList.addAll(applyCards);
        } else {
            cardList = applyCards;
        }

        result.setCardList(cardList);
        return result;
    }

    public List<GlWithdrawUserBankCard> getUserCardInfo(Integer userId) throws GlobalException {
        GlUserDO user = RPCResponseUtils.getData(userService.findById(userId));
        Validator.build().add(null == user, "会员不存在").valid();

        List<GlWithdrawUserBankCard> cardList = findUserActiveCardList(user.getId());
        List<GlWithdrawUserBankCard> applyCards = bankCardApplyBusiness.findCardByUserId(user.getId());
        cardList.addAll(applyCards);
        return cardList;
    }

    /**
     * 绑定或删除银行操作
     *
     * @param dto
     * @return
     */
    @Transactional(rollbackFor = {Exception.class})
    public GlWithdrawUserBankCard operatingBankCard(BankCardOperatingDto dto) {
        GlWithdrawUserBankCard bankCard = dto.getBankCard();
        Integer operationType = dto.getOperationType();
        GlUserDO user = dto.getUser();

        // 银行卡处理
        if (UserConstant.UserOperateType.BIND_BANK_CARD_OPTTYPE == operationType) {
            //绑定银行卡
            glWithdrawUserbankcardMapper.insertSelective(bankCard);
            GlWithdrawUserBankCard dbCard = glWithdrawUserbankcardMapper.findUserCard(user.getId(), bankCard.getCardNo());
            saveCardToCache(user, dbCard);
        } else if (UserConstant.UserOperateType.UNTIED_BANK_CARD_OPTTYPE == operationType) {
            //解绑银行卡
            glWithdrawUserbankcardMapper.updateByPrimaryKeySelective(bankCard);
            GlWithdrawUserBankCard dbCard = findById(bankCard.getCardId());
            if (!ObjectUtils.isEmpty(dbCard)) {
                // 同步ES数据
                dbCard.setStatus(1);
                saveCardToCache(user, dbCard);
            }
        }
        // 删除银行卡缓存
        redisService.delete(RedisKeyHelper.USER_BANK_CARD_INFO + user.getId());

        // 更新用户reallyName
        updateUserReallyName(dto);
        // 保存用户操作记录
        saveUserOperationLog(dto);

        return bankCard;
    }

    /**
     * 二审通过，绑定银行卡
     *
     * @param applyDto
     */
    @Transactional(rollbackFor = Exception.class)
    public void secondApprove(final BankCardApplyDto applyDto) throws GlobalException {
        String optData = applyDto.getOptData();
        WithdrawUserBankCardApply apply = JSON.parseObject(applyDto.getOptData(), WithdrawUserBankCardApply.class);
        if (null == apply)
            return;
        apply = bankCardApplyBusiness.findById(apply.getCardId());
        if (null == apply)
            return;
        optData = bankCardApplyBusiness.updateStatus(optData, applyDto.getStatus(), null);

        Date now = new Date();
        if (ProjectConstant.UserManageStatus.SECOND_SUCCESS == applyDto.getStatus()) {
            GlWithdrawUserBankCard bankCard = new GlWithdrawUserBankCard();
            bankCard.setUserId(apply.getUserId());
            bankCard.setCardNo(apply.getCardNo());
            bankCard.setName(apply.getName());
            bankCard.setBankId(apply.getBankId());
            bankCard.setBankName(apply.getBankName());
            bankCard.setAddress(apply.getAddress());
            bankCard.setCreateDate(now);
            bankCard.setLastUpdate(now);
            bankCard.setStatus(0);
            bankCard.setSelected(0);

            List<GlWithdrawUserBankCard> cardList = findUserActiveCardList(apply.getUserId());
            GlUserDO user = RPCResponseUtils.getData(userService.findById(apply.getUserId()));
            BankCardOperatingDto dto = new BankCardOperatingDto();
            dto.setOperationType(UserConstant.UserOperateType.BIND_BANK_CARD_OPTTYPE);
            dto.setUser(user);
            dto.setBankCard(bankCard);
            dto.setCardList(cardList);
            dto.setUserOperating(false);
            operatingBankCard(dto);
        }

        GlUserManageDO manage = new GlUserManageDO();
        manage.setManageId(applyDto.getManageId());
        manage.setStatus(applyDto.getStatus());
        manage.setSecondApprover(applyDto.getApprover());
        manage.setSecondRemark(applyDto.getRemark());
        manage.setSecondTime(now);
        manage.setOptData(optData);
        userManageService.updateBankCardApply(manage);
    }

    @Transactional(rollbackFor = Exception.class)
    public void resetBankCard(ResetBankCardDto resetBankCardDto) throws GlobalException {
        Integer status = resetBankCardDto.getStatus();
        Integer userId = resetBankCardDto.getUserId();
        GlUserDO user = RPCResponseUtils.getData(userService.findById(userId));
        Date now = new Date();
        if (ProjectConstant.UserManageStatus.SECOND_SUCCESS == status) {
            List<GlWithdrawUserBankCard> glWithdrawUserBankCards = glWithdrawUserbankcardMapper.findUserCardList(userId);
            for (GlWithdrawUserBankCard userBankCard : glWithdrawUserBankCards) {
                if (userBankCard.getStatus() == 0) {
                    userBankCard.setStatus(1);
                    userBankCard.setLastUpdate(now);
                    glWithdrawUserbankcardMapper.updateByPrimaryKeySelective(userBankCard);
                    //Update ES Cache
                    saveCardToCache(user, userBankCard);
                }
            }
        }

        //更新用户信息真实姓名并上报变更
        UserReallyNameDto reallyNameDto = new UserReallyNameDto();
        reallyNameDto.setUserId(user.getId());
        reallyNameDto.setReallyName("");
        reallyNameDto.setLastUpdate(now);

        // 更新审核状态
        GlUserManageDO manage = new GlUserManageDO();
        manage.setManageId(resetBankCardDto.getManageId());
        manage.setStatus(resetBankCardDto.getStatus());
        manage.setSecondApprover(resetBankCardDto.getApprover());
        manage.setSecondRemark(resetBankCardDto.getRemark());
        manage.setSecondTime(now);

        userService.updateReallyName(reallyNameDto, manage);
    }

    /**
     * 删除银行卡
     *
     * @param dbcDto
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteBankCard(DeleteBankCardDto dbcDto) throws GlobalException {
        Integer status = dbcDto.getStatus();
        if (ProjectConstant.UserManageStatus.SECOND_SUCCESS != status) {
            return;
        }
        int cardId = dbcDto.getCardId();
        GlWithdrawUserBankCard card = findById(cardId);
        if (card != null) {
            Date now = new Date();
            card.setStatus(1);
            card.setLastUpdate(now);
            Integer userId = dbcDto.getUserId();
            List<GlWithdrawUserBankCard> cardList = findUserActiveCardList(userId);
            GlUserDO user = RPCResponseUtils.getData(userService.findById(userId));
            BankCardOperatingDto dto = new BankCardOperatingDto();
            dto.setOperationType(UserConstant.UserOperateType.UNTIED_BANK_CARD_OPTTYPE);
            dto.setUser(user);
            dto.setBankCard(card);
            dto.setCardList(cardList);
            dto.setUserOperating(false);
            operatingBankCard(dto);
        }
    }

    /**
     * 上报es
     *
     * @param user
     * @param bankCard
     */
    private void saveCardToCache(GlUserDO user, GlWithdrawUserBankCard bankCard) {
        UserBankCardReport report = new UserBankCardReport();
        report.setAddress(bankCard.getAddress());
        report.setBankId(bankCard.getBankId());
        report.setBankName(bankCard.getBankName());
        report.setCardId(bankCard.getCardId());
        report.setCardNo(bankCard.getCardNo());
        report.setCreateDate(bankCard.getCreateDate().getTime());
        report.setLastUpdate(bankCard.getLastUpdate().getTime());
        report.setName(bankCard.getName());
        report.setSelected(bankCard.getSelected());
        report.setStatus(bankCard.getStatus());
        report.setSyncDate(bankCard.getLastUpdate().getTime());
        report.setUserId(bankCard.getUserId());
        report.setUserType(user.getUserType());
        report.setUsername(user.getUsername());
        reportService.userBankCardReport(report);
    }

    /**
     * 更新用户reallyName
     *
     * @param dto
     */
    private void updateUserReallyName(BankCardOperatingDto dto) {
        GlWithdrawUserBankCard bankCard = dto.getBankCard();
        Integer operationType = dto.getOperationType();
        GlUserDO glUser = dto.getUser();
        List<GlWithdrawUserBankCard> cardList = dto.getCardList();
        String reallyName = null;
        if (UserConstant.UserOperateType.BIND_BANK_CARD_OPTTYPE == operationType) {
            if(StringUtils.isNotBlank(glUser.getReallyName())) {
                return;
            }
            reallyName = bankCard.getName();
        }
        else if (UserConstant.UserOperateType.UNTIED_BANK_CARD_OPTTYPE == operationType) {
            if (glUser.getUserType() == UserConstant.Type.PLAYER) { // 会员不更新reallyName
                return;
            }
            else if (StringUtils.equals(bankCard.getName(), glUser.getReallyName())) {
                Optional<GlWithdrawUserBankCard> first = cardList.stream()
                        .filter(c -> !c.getCardId().equals(bankCard.getCardId()))
                        .min(Comparator.comparing(GlWithdrawUserBankCard::getCreateDate));
                reallyName = first.isPresent() ? first.get().getName() : "";
            }
        }
        //更新用户信息真实姓名并上报变更
        UserReallyNameDto reallyNameDto = new UserReallyNameDto();
        reallyNameDto.setUserId(glUser.getId());
        reallyNameDto.setReallyName(reallyName);
        reallyNameDto.setLastUpdate(new Date());
        userService.updateReallyName(reallyNameDto);
    }

    /**
     * 保存用户操作记录
     *
     * @param dto
     */
    private void saveUserOperationLog(BankCardOperatingDto dto) {
        if (!dto.getUserOperating()) // 不是前台用户操作
            return;

        Integer operationType = dto.getOperationType();
        GlWithdrawUserBankCard bankCard = dto.getBankCard();
        GlUserDO glUser = dto.getUser();

        UserOperationLogReport report = new UserOperationLogReport();
        report.setLogId(redisService.generateIncr(KeyConstant.USER.USER_OPERATION_ID));
        report.setUrl(dto.getRequestUrl());
        report.setClientType(dto.getHeaderOsType());
        report.setDeviceId(dto.getHeaderDeviceId());
        report.setIp(dto.getRequestIp());

        report.setAddress("");
        report.setCreateTime(new Date());
        report.setUserId(glUser.getId());
        report.setUsername(glUser.getUsername());
        report.setUserType(glUser.getUserType());

        String cardNo = RegExUtils.replaceAll(bankCard.getCardNo(), ".*(.{4})$", "$1");
        String operationDes = String.format("{\"姓名\":\"%s\",\"卡号尾数\":\"%s\"}", bankCard.getName(), cardNo);
        if (UserConstant.UserOperateType.BIND_BANK_CARD_OPTTYPE == operationType) {
            report.setOptBeforeData("");
            report.setOptAfterData(operationDes);//操作后记录
            report.setOperationDesc(UserConstant.UserOperateType.BIND_BANK_CARD.getDesc());
            report.setOperationType(UserConstant.UserOperateType.BIND_BANK_CARD.getOptType());
        } else if (UserConstant.UserOperateType.UNTIED_BANK_CARD_OPTTYPE == operationType) {
            report.setOptBeforeData(operationDes);//操作后记录
            report.setOptAfterData("");
            report.setOperationDesc(UserConstant.UserOperateType.UNTIED_BANK_CARD.getDesc());
            report.setOperationType(UserConstant.UserOperateType.UNTIED_BANK_CARD.getOptType());
        }
        // 上报前台操作记录
        log.info("bindCard saveUserOperationLog report:{}", JSON.toJSONString(report));
        reportService.userOperationLogReport(report);
    }

    public PageInfo<UserBindBankDO> bankList(UserBindQueryDO queryDO) {
        return PageHelper
                .startPage(queryDO.getPage(),queryDO.getSize())
                .doSelectPageInfo(()->glWithdrawUserbankcardMapper.bankList(queryDO));
    }
}