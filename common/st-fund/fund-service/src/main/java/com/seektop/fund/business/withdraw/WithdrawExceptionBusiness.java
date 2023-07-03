package com.seektop.fund.business.withdraw;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.seektop.activity.dto.param.user.GlUserVipDo;
import com.seektop.activity.service.GlUserVipService;
import com.seektop.agent.dto.ValidWithdrawalDto;
import com.seektop.common.encrypt.EncryptHelper;
import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.ConvertNameUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.RegexValidator;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.user.UserConstant;
import com.seektop.digital.mapper.DigitalUserAccountMapper;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.Language;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.WithdrawStatusEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.dto.ExportFileDto;
import com.seektop.fund.controller.backend.dto.NoticeFailDto;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawDO;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawListResult;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawLevelConfigListDto;
import com.seektop.fund.controller.backend.param.withdraw.WithdrawExceptionAmountDo;
import com.seektop.fund.controller.backend.param.withdraw.WithdrawExceptionDo;
import com.seektop.fund.controller.backend.result.FundUserLevelResult;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.handler.ExportFileHandler;
import com.seektop.fund.handler.NoticeHandler;
import com.seektop.fund.handler.UserManageHandler;
import com.seektop.fund.handler.validation.UserLockStatus;
import com.seektop.fund.handler.validation.Validator;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.mapper.GlWithdrawSplitMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.vo.WithdrawExceptionQueryDto;
import com.seektop.fund.vo.WithdrawVO;
import com.seektop.report.fund.WithdrawParentOrderReport;
import com.seektop.report.fund.WithdrawReport;
import com.seektop.report.fund.WithdrawReturnReport;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 风控管理-异常提现
 */
@Component
@Slf4j
public class WithdrawExceptionBusiness extends AbstractBusiness<GlWithdraw> {

    @Resource
    private GlWithdrawMapper glWithdrawMapper;
    @Resource
    private RedisService redisService;
    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;
    @Resource
    private GlWithdrawApproveBusiness glWithdrawApproveBusiness;
    @Resource
    private GlWithdrawReturnRequestBusiness withdrawReturnRequestBusiness;
    @Resource
    private GlFundUserlevelBusiness fundUserlevelBusiness;
    @Resource
    private GlWithdrawLevelConfigBusiness glWithdrawLevelConfigBusiness;
    @DubboReference(retries = 2, timeout = 3000)
    private GlUserService glUserService;

    @Autowired
    private ExportFileHandler exportFileHandler;
    @Resource(name = "withdrawNoticeHandler")
    private NoticeHandler noticeHandler;
    @Autowired
    private WithdrawRiskApproveBusiness withdrawRiskApproveBusiness;
    @Resource
    private FundProxyAccountBusiness fundProxyAccountBusiness;
    @Autowired
    private ReportService reportService;
    @Resource
    private GlWithdrawSplitMapper glWithdrawSplitMapper;
    @DubboReference(retries = 1, timeout = 3000)
    private GlUserVipService userVipService;
    @Resource
    private GlWithdrawSplitBusiness glWithdrawSplitBusiness;
    @Autowired
    private UserManageHandler userManageHandler;
    @Resource
    private UserVipUtils userVipUtils;
    @Resource
    private UserFundUtils userFundUtils;
    @Resource
    private DigitalUserAccountMapper digitalUserAccountMapper;
    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    public PageInfo<GlWithdrawListResult> findWithdrawExceptionList(WithdrawExceptionQueryDto queryDto) throws GlobalException {
        Page<?> page = PageHelper.startPage(queryDto.getPage(), queryDto.getSize());
        List<WithdrawVO> pageList = glWithdrawMapper.findWithdrawExceptionList(queryDto);
        PageInfo<GlWithdrawListResult> resultPageInfo = (PageInfo<GlWithdrawListResult>) new PageInfo<>(page);
        if (CollectionUtils.isEmpty(pageList)) {
            resultPageInfo.setList(Lists.newArrayList());
            return resultPageInfo;
        }
        resultPageInfo.setList(getResultList(pageList,queryDto.getQueryTime(), queryDto.getLanguage()));
        return resultPageInfo;
    }

    /**
     * 导出数据
     *
     * @param queryDto
     * @param admin
     * @throws GlobalException
     */
    @Async
    public void download(WithdrawExceptionQueryDto queryDto, GlAdminDO admin) throws GlobalException {
        ExportFileDto exportFileDto = new ExportFileDto();
        exportFileDto.setUserId(admin.getUserId());
        exportFileDto.setFileName("异常取款审核记录");
        String headers = "创建时间,提现单号,账户名,币种,提现金额,账户类型,用户层级,操作端,风控审核时间,审核人,风险类型,审核状态,提现银行,出款时间";
        exportFileDto.setHeaders(headers);
        exportFileDto.setSupplier(() -> getExportData(queryDto));
        exportFileHandler.exportFile(exportFileDto);

    }

    private StringBuffer getExportData(WithdrawExceptionQueryDto queryDto) throws GlobalException {
        int size = 2_000;
        Page<?> page = PageHelper.startPage(1, size);
        List<WithdrawVO> records = glWithdrawMapper.findWithdrawExceptionList(queryDto);
        int pages = Math.min(page.getPages(), 100);//限制上限20W;
        long total = Math.min(page.getTotal(), 100 * size);
        List<GlWithdrawListResult> csvList = new ArrayList<>((int) total);
        for (int i = 0; i < pages; i++) {
            if (i > 0) {
                PageHelper.startPage(i + 1, size, false);
                records = glWithdrawMapper.findWithdrawExceptionList(queryDto);
            }
            List<WithdrawVO> finalRecords = records;
            csvList.addAll(getResultList(finalRecords,queryDto.getQueryTime(), queryDto.getLanguage()));
        }

        List<GlWithdrawListResult> distinctIdList = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(csvList)) {
            distinctIdList = csvList.stream().collect(
                    Collectors.collectingAndThen(Collectors.toCollection(
                            () -> new TreeSet<>(Comparator.comparing(GlWithdrawListResult::getOrderId))), ArrayList::new));
            distinctIdList.sort(Comparator.comparing(GlWithdrawListResult::getCreateDate).reversed());
        }
        return getExportData(distinctIdList);
    }

    /**
     * 适配数据
     *
     * @param records
     * @return
     */
    public List<GlWithdrawListResult> getResultList(List<WithdrawVO> records, Date queryTime, Language language) throws GlobalException {
        List<GlWithdrawListResult> resultList = new ArrayList<>(records.size());
        List<Integer> levelIds = records.stream().map(a -> Integer.parseInt(a.getUserLevel())).distinct().collect(Collectors.toList());
        List<GlFundUserlevel> levelList = fundUserlevelBusiness.findByLevelIds(levelIds);

        List<Integer> userIds = records.stream().map(WithdrawVO::getUserId).distinct().collect(Collectors.toList());
        List<GlUserDO> users = RPCResponseUtils.getData(glUserService.findByIds(userIds));
        Map<Integer, GlUserDO> userMap = users.stream().filter(u -> UserConstant.Type.PLAYER == u.getUserType()).collect(Collectors.toMap(u -> u.getId(), u -> u));

        // 审核通过后的所有状态都是审核通过
        for (WithdrawVO withdraw : records) {
            GlWithdrawListResult result = new GlWithdrawListResult();
            BeanUtils.copyProperties(withdraw, result);
            result.setStatusName(ConvertNameUtils.convertCheckStatusName(result.getStatus()));

            //历史数据字符串转数组

            if (withdraw.getRiskType().contains("[")) {
                String riskTypeStr = withdraw.getRiskType().replace("[", "").replace("]", "");
                int[] riskInt = Arrays.stream(riskTypeStr.split(",")).mapToInt(s -> Integer.parseInt(s)).toArray();
                List<Integer> list = Arrays.stream(riskInt).boxed().collect(Collectors.toList());
                result.setRiskType(ConvertNameUtils.getAppendRiskName(list));
            } else if (!withdraw.getRiskType().contains("[") && withdraw.getRiskType().contains(",")) {
                int[] riskInt = Arrays.stream(withdraw.getRiskType().split(",")).mapToInt(s -> Integer.parseInt(s)).toArray();
                List<Integer> list = Arrays.stream(riskInt).boxed().collect(Collectors.toList());
                result.setRiskType(ConvertNameUtils.getAppendRiskName(list));
            } else {
                result.setRiskType(ConvertNameUtils.convertRiskTypeName(Integer.parseInt(withdraw.getRiskType())));
            }
            // noinspection deprecation
            result.setRiskType(StringEscapeUtils.escapeCsv(result.getRiskType()));
            //查询用户分层表转换用户层级
            if (StringUtils.isNotBlank(result.getUserLevel())) {
                Optional<GlFundUserlevel> f = levelList.stream()
                        .filter(l -> l.getLevelId().toString().equals(result.getUserLevel()))
                        .findFirst();
                if (f.isPresent()) {
                    result.setUserLevelName(f.get().getName());
                }
            }
            result.setName(Encryptor.builderName().doEncrypt(withdraw.getName()));
            result.setReallyName(Encryptor.builderName().doEncrypt(withdraw.getReallyName()));
            result.setTelephone(Encryptor.builderMobile().doEncrypt(withdraw.getTelephone()));
            result.setCardNo(Encryptor.builderBankCard().doEncrypt(withdraw.getCardNo()));

            if (withdraw.getStatus() == -3 || withdraw.getStatus() == -4) {
                List userList = com.google.common.collect.Lists.newArrayList();
                //查询当前操作异常取款审核的用户
                Map<String, Long> map = redisService.get(RedisKeyHelper.EXCEPTION_WITHDRAW_CACHE + result.getOrderId(), Map.class);
                Date now = new Date();
                if (null != map && map.size() > 0) {
                    Iterator<Map.Entry<String, Long>> entries = map.entrySet().iterator();
                    while (entries.hasNext()) {
                        Map.Entry<String, Long> entry = entries.next();
                        if (entry.getValue().longValue() >= now.getTime()) {
                            userList.add(entry.getKey());
                        }
                    }
                    result.setUserList(userList);
                }
            }
            //新会员:0 ：注册时间7天内的会员  老会员 2：注册时间超过7天的会员
            if (withdraw.getUserType().equals(UserConstant.Type.PLAYER) && userMap.get(withdraw.getUserId()) != null) {
                result.setUserType(DateUtils.diffDay(userMap.get(withdraw.getUserId()).getRegisterDate(),queryTime) > 7 ? 2 : 0);
            }
            resultList.add(result);
        }
        return resultList;
    }

    /**
     * 数据data
     *
     * @param data
     * @return
     */
    private StringBuffer getExportData(List<GlWithdrawListResult> data) {
        StringBuffer sb = new StringBuffer();
        for (GlWithdrawListResult item : data) {
            // 提现时间
            sb.append(DateUtils.format(item.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            sb.append(",");
            // 提现单号
            sb.append(item.getOrderId());
            sb.append(",");
            // 账户号
            sb.append(item.getUsername());
            sb.append(",");
            // 币种
            sb.append(item.getCoin()).append(",");
            // 提现金额
            sb.append(item.getAmount());
            sb.append(",");
            // 账户类型
            sb.append(item.getUserType() == 1 ? "代理" : "会员");
            sb.append(",");
            // 用户层级
            if (StringUtils.isNotBlank(item.getUserLevelName())) {
                sb.append(item.getUserLevelName());
            } else {
                sb.append("-");
            }
            sb.append(",");
            // 操作端
            sb.append(ConvertNameUtils.convertOsTypeName(item.getClientType()));
            sb.append(",");
            // 审核时间
            sb.append(DateUtils.format(item.getRiskApvTime(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            sb.append(",");
            // 审核人
            sb.append(StringUtils.isNotEmpty(item.getRiskApprover()) ? item.getRiskApprover() : "");
            sb.append(",");
            // 风险类型
            if (StringUtils.isNotBlank(item.getRiskType())) {
                sb.append(item.getRiskType());
            } else {
                sb.append("-");
            }
            sb.append(",");
            // 审核状态
            sb.append(ConvertNameUtils.convertCheckStatusName(item.getStatus()));
            sb.append(",");
            // 提现银行
            sb.append(StringUtils.isEmpty(item.getBankName()) ? "-" : item.getBankName());
            sb.append(",");
            // 出款时间
            sb.append(DateUtils.format(item.getLastUpdate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            sb.append("\r\n");
        }
        return sb;
    }

    public GlWithdraw doWithdrawRiskApprove(GlWithdraw withdraw, WithdrawExceptionApproveDto approveDto, GlAdminDO adminDO) {
        Date updateTime = approveDto.getUpdateTime();
        withdraw.setLastUpdate(updateTime);
        withdraw.setRiskApprover(adminDO.getUsername());
        withdraw.setRiskApvRemark(approveDto.getRemark());
        withdraw.setRejectReason(approveDto.getRejectReason());
        withdraw.setRiskApvTime(updateTime);
        if (approveDto.getStatus() == 1) {
            //风控通过-提现分单
            withdraw.setStatus(FundConstant.WithdrawStatus.PENDING);
            withdraw.setSeperateDate(updateTime);
            withdraw.setSeperateCreator("系统自动");
            withdraw.setWithdrawType(FundConstant.WithdrawType.All);//默认出款方式为全部
            if (withdraw.getBankId() != FundConstant.PaymentType.DIGITAL_PAY) {
                // 提现银行卡姓名(true纯中文、false含非中文字符)
                if (RegexValidator.isChinese(withdraw.getName())) {
                    String level = withdraw.getUserLevel();
                    withdraw = glWithdrawBusiness.setWithdrawManual(withdraw, level);
                } else {
                    withdraw.setSeperateReason("非纯中文姓名");
                }
            } else {
                withdraw = glWithdrawBusiness.setWithdrawManual(withdraw, withdraw.getUserLevel());
            }
        } else if (approveDto.getStatus() == 2) {
            withdraw.setStatus(FundConstant.WithdrawStatus.RISK_REJECT);
        } else if (approveDto.getStatus() == 4) {
            withdraw.setStatus(FundConstant.WithdrawStatus.REVIEW_HOLD);
        }
        return withdraw;
    }

    @Transactional(rollbackFor = Exception.class)
    public void doWithdrawRiskApprove(List<GlWithdraw> glWithdraws, WithdrawExceptionApproveDto approveDto, GlAdminDO admin) throws GlobalException {
        // 查询所需数据
        int size = glWithdraws.size();
        List<Integer> userIds = glWithdraws.stream().map(GlWithdraw::getUserId).distinct().collect(Collectors.toList());
        List<GlUserDO> users = RPCResponseUtils.getData(glUserService.findByIds(userIds));
        if (2 == approveDto.getStatus() && 1 == size && users.size() > 0) { // 单笔拒绝时，判断是否已锁定
            Validator.build().add(new UserLockStatus(approveDto.getOperation(), users.get(0))).valid();
        }

        List<String> orderIds = glWithdraws.stream().map(GlWithdraw::getOrderId).collect(Collectors.toList());
        List<GlWithdrawRiskApprove> approveList = withdrawRiskApproveBusiness.findByOrderIds(orderIds);
        List<Integer> vipUserIds = users.stream()
                .filter(u -> UserConstant.Type.PLAYER == u.getUserType())
                .map(GlUserDO::getId)
                .collect(Collectors.toList());
        List<GlUserVipDo> vips = RPCResponseUtils.getData(userVipService.findByUserIds(vipUserIds));
        FundUserLevelResult levelResult = fundUserlevelBusiness.findByUserIds(userIds);

        for (GlWithdraw dbWithdraw : glWithdraws) {
            glWithdrawMapper.updateByPrimaryKeySelective(dbWithdraw);

            if (!approveDto.isC2cToNormal()) {
                Optional<GlWithdrawRiskApprove> approveOptional = approveList.stream()
                        .filter(a -> a.getOrderId().equals(dbWithdraw.getOrderId())).findFirst();
                withdrawRiskApproveBusiness.save(dbWithdraw, approveOptional, approveDto, admin);
            }

            /**
             * 提现-风险审核拒绝(订单结束)
             */
            if (dbWithdraw.getStatus() == FundConstant.WithdrawStatus.RISK_REJECT ||
                    (approveDto.isC2cToNormal() && dbWithdraw.getStatus() == FundConstant.WithdrawStatus.FAILED)) {
                GlUserDO glUser = users.stream().filter(u -> u.getId().equals(dbWithdraw.getUserId())).findFirst().get();
                // 款项需要回归中心钱包
                DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(dbWithdraw.getUserId(), DigitalCoinEnum.getDigitalCoin(dbWithdraw.getCoin()));
                digitalUserAccountMapper.balanceTransferIn(dbWithdraw.getUserId() , dbWithdraw.getCoin(), dbWithdraw.getAmount(), dbWithdraw.getCreateDate());
                // 创建退回申请
                withdrawReturnRequestBusiness.save(dbWithdraw, approveDto, admin);
                /**
                 *代理可提现额度退回
                 */
                if (UserConstant.Type.PROXY == glUser.getUserType()) {
                    ValidWithdrawalDto validWithdrawalDto = new ValidWithdrawalDto();
                    validWithdrawalDto.setUserId(glUser.getId());
                    validWithdrawalDto.setAmount(dbWithdraw.getAmount());
                    fundProxyAccountBusiness.addValidWithdrawal(validWithdrawalDto);
                }

                // 上报提现订单状态
                updateWithdrawReport(approveDto, dbWithdraw, glUser, levelResult, vips);
                // 上报提现退回
                updateWithdrawReturnReport(approveDto, dbWithdraw, account, glUser);
                // 拆单父单状态上报
                updateParentOrderStatus(dbWithdraw);
                // 提现失败通知
                doWithdrawFailNotice(dbWithdraw, approveDto);
            }

            // 账号操作：申请锁定或间接锁定
            if (2 == approveDto.getStatus() && 1 == size && approveDto.getOperation() > 0) {
                userManageHandler.updateLockUp(dbWithdraw, approveDto, admin);
            }
        }
    }

    /**
     * 获取异常提现订单详情
     *
     * @param orderId
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    public GlWithdrawDO info(String orderId, GlAdminDO adminDO) throws GlobalException {
        GlWithdraw glWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
        if (null == glWithdraw) {
            throw new GlobalException("提现订单号异常");
        }
        GlWithdrawDO withdrawDO = new GlWithdrawDO();
        BeanUtils.copyProperties(glWithdraw, withdrawDO);

        //汇总提现总金额和总手续费
        List<GlWithdrawSplit> splitList = glWithdrawSplitBusiness.findAllSplitOrderByOrderId(glWithdraw.getOrderId());
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        if (null != splitList && !splitList.isEmpty()) {
            for (GlWithdrawSplit spilt : splitList) {
                GlWithdraw temp = glWithdrawMapper.selectByPrimaryKey(spilt.getOrderId());
                totalAmount = totalAmount.add(temp.getAmount());
                totalFee = totalFee.add(temp.getFee());
            }
        } else {
            totalAmount = glWithdraw.getAmount();
            totalFee = glWithdraw.getFee();
        }
        withdrawDO.setTotalAmount(totalAmount);
        withdrawDO.setTotalFee(totalFee);

        if (null != withdrawDO.getUserId()) {
            GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(withdrawDO.getUserId()));
            if (!ObjectUtils.isEmpty(userDO)) {
                withdrawDO.setRemark(userDO.getRemark());
                withdrawDO.setUserLockStatus(userDO.getStatus());
                withdrawDO.setRegisterDate(userDO.getRegisterDate());
            }
            if (StringUtils.isNotEmpty(withdrawDO.getName())) {
                withdrawDO.setName(EncryptHelper.doEncrypt(withdrawDO.getName(),
                        Encryptor.builder().encryptType(EncryptTypeEnum.NAME.getJobEncryptId())
                                .encrypt(EncryptTypeEnum.NAME.getParse())
                                .build()));
            }
            if (StringUtils.isNotEmpty(withdrawDO.getCardNo())) {
                withdrawDO.setCardNo(EncryptHelper.doEncrypt(withdrawDO.getCardNo(),
                        Encryptor.builder().encryptType(EncryptTypeEnum.BANKCARD.getJobEncryptId())
                                .encrypt(EncryptTypeEnum.BANKCARD.getParse())
                                .build()));
            }
        }

        //返回正在查看人
        if (withdrawDO.getStatus() == -3 || withdrawDO.getStatus() == -4) {
            List userList = com.google.common.collect.Lists.newArrayList();
            //查询当前操作异常取款审核的用户
            Map<String, Long> map = redisService.get(RedisKeyHelper.EXCEPTION_WITHDRAW_CACHE + orderId, Map.class);
            Date now = new Date();
            if (null != map && map.size() > 0) {
                Iterator<Map.Entry<String, Long>> entries = map.entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry<String, Long> entry = entries.next();
                    if (entry.getValue().longValue() >= now.getTime()) {
                        if (adminDO.getUsername().equals(entry.getKey())){
                            continue;
                        }
                        userList.add(entry.getKey());
                    }
                }
                withdrawDO.setUserList(userList);
            }
        }

        if (withdrawDO.getStatus() == -3 || withdrawDO.getStatus() == -4) {
            //设置用户查看及过期时间
            String key = RedisKeyHelper.EXCEPTION_WITHDRAW_CACHE + orderId;
            Map<String, Long> map = redisService.get(key, Map.class);
            try {
                if (ObjectUtils.isEmpty(map)) {
                    map = Maps.newHashMap();
                }
                Date expireTime = DateUtils.addMin(10, new Date());
                map.put(adminDO.getUsername(), expireTime.getTime());
                redisService.set(key, map, 600);
            } catch (Exception e) {
                log.error("addMin error:", e);
            }
        }
        return withdrawDO;
    }

    /**
     * 会员提现风控 新增
     */
    public void addLevelConfig(WithdrawExceptionDo withdrawExceptionDo, GlAdminDO adminDO) throws GlobalException {
        GlFundUserlevel level = fundUserlevelBusiness.findById(withdrawExceptionDo.getLevelId());
        if (level == null) {
            throw new GlobalException("指定的层级不存在");
        }

        List<GlWithdrawLevelConfig> dbList = glWithdrawLevelConfigBusiness.findByLevelId(withdrawExceptionDo.getLevelId());

        for (WithdrawExceptionAmountDo exceptionAmountDo:withdrawExceptionDo.getList()) {
            GlWithdrawLevelConfig config = glWithdrawLevelConfigBusiness.getWithdrawLevelConfig(withdrawExceptionDo.getLevelId(),exceptionAmountDo.getCoinCode());
            if (config != null) {
                throw new GlobalException("该层级" + exceptionAmountDo.getCoinCode() + "配置已存在");
            }

            GlWithdrawLevelConfig glWithdrawLevelConfig = DtoUtils.transformBean(withdrawExceptionDo, GlWithdrawLevelConfig.class);

            log.info("glWithdrawLevelConfig-1:{}", JSON.toJSONString(glWithdrawLevelConfig));
            BeanUtils.copyProperties(exceptionAmountDo , glWithdrawLevelConfig, "levelId,registerDays,sameDeviceCheck,sameIpCheck,timeCheck");
            glWithdrawLevelConfig.setCoin(exceptionAmountDo.getCoinCode());
            log.info("glWithdrawLevelConfig-2:{}", JSON.toJSONString(glWithdrawLevelConfig));

            Date now = new Date();
            glWithdrawLevelConfig.setCreateTime(now);
            glWithdrawLevelConfig.setCreator(adminDO.getUsername());
            glWithdrawLevelConfig.setStatus(0);
            glWithdrawLevelConfig.setId(null);
            glWithdrawLevelConfig.setLastOperator(adminDO.getUsername());
            glWithdrawLevelConfig.setLastUpdate(now);
            if (null == glWithdrawLevelConfig.getTimeCheck()) {
                glWithdrawLevelConfig.setTimeCheck(0);
            }
            List<GlWithdrawLevelConfig> tempList = dbList.stream()
                    .filter(item -> (item.getLevelId() == glWithdrawLevelConfig.getLevelId() && item.getCoin().equals(glWithdrawLevelConfig.getCoin())))
                    .collect(Collectors.toList());

            if (CollectionUtils.isEmpty(tempList)) {
                glWithdrawLevelConfigBusiness.save(glWithdrawLevelConfig);
            } else {
                glWithdrawLevelConfig.setId(tempList.get(0).getId());
                glWithdrawLevelConfigBusiness.updateByPrimaryKeySelective(glWithdrawLevelConfig);
            }
        }
    }

    /**
     * 会员提现风控 编辑
     */
    public void updateLevelConfig(WithdrawExceptionDo withdrawExceptionDo, GlAdminDO adminDO) throws GlobalException {

        for (WithdrawExceptionAmountDo exceptionAmountDo:withdrawExceptionDo.getList()) {
            GlWithdrawLevelConfig config = glWithdrawLevelConfigBusiness.getWithdrawLevelConfig(withdrawExceptionDo.getLevelId(),exceptionAmountDo.getCoinCode());
            if (config == null) {
                throw new GlobalException("配置不存在");
            }
            GlFundUserlevel level = fundUserlevelBusiness.findById(withdrawExceptionDo.getLevelId());
            if (level == null) {
                throw new GlobalException("指定的层级不存在");
            }
            GlWithdrawLevelConfig dbConfig = glWithdrawLevelConfigBusiness.getWithdrawLevelConfig(withdrawExceptionDo.getLevelId(),exceptionAmountDo.getCoinCode());
            if (dbConfig != null && !dbConfig.getId().equals(withdrawExceptionDo.getId())) {
                throw new GlobalException("指定层级的配置已存在");
            }
            GlWithdrawLevelConfig glWithdrawLevelConfig = DtoUtils.transformBean(withdrawExceptionDo, GlWithdrawLevelConfig.class);
            log.info("glWithdrawLevelConfig-1:{}", JSON.toJSONString(glWithdrawLevelConfig));
            BeanUtils.copyProperties(exceptionAmountDo , glWithdrawLevelConfig, "levelId,registerDays,sameDeviceCheck,sameIpCheck,timeCheck");
            log.info("glWithdrawLevelConfig-2:{}", JSON.toJSONString(glWithdrawLevelConfig));

            Date now = new Date();
            glWithdrawLevelConfig.setId(withdrawExceptionDo.getId());
            glWithdrawLevelConfig.setCreateTime(null);
            glWithdrawLevelConfig.setCreator(null);
            glWithdrawLevelConfig.setStatus(null);
            glWithdrawLevelConfig.setLastOperator(adminDO.getUsername());
            glWithdrawLevelConfig.setLastUpdate(now);
            glWithdrawLevelConfigBusiness.updateByPrimaryKeySelective(glWithdrawLevelConfig);
        }
    }

    /**
     * 会员提现风控 启用/禁用
     */
    public void levelConfigStatus(Integer id, Integer status, GlAdminDO adminDO) throws GlobalException {
        GlWithdrawLevelConfig config = glWithdrawLevelConfigBusiness.findById(id);
        if (config == null) {
            throw new GlobalException("配置不存在");
        }

        Date now = new Date();
        GlWithdrawLevelConfig glWithdrawLevelConfig = new GlWithdrawLevelConfig();
        glWithdrawLevelConfig.setId(id);
        glWithdrawLevelConfig.setStatus(status == 0 ? 0 : 1);
        glWithdrawLevelConfig.setLastOperator(adminDO.getUsername());
        glWithdrawLevelConfig.setLastUpdate(now);
        glWithdrawLevelConfigBusiness.updateByPrimaryKeySelective(glWithdrawLevelConfig);
    }

    /**
     * 会员提现风控 删除
     */
    public void deleteLevelConfig(Integer id, GlAdminDO adminDO) throws GlobalException {
        GlWithdrawLevelConfig config = glWithdrawLevelConfigBusiness.findById(id);
        if (config == null || config.getStatus() == 2) {
            throw new GlobalException("配置不存在或已删除");
        }

        Date now = new Date();
        GlWithdrawLevelConfig glWithdrawLevelConfig = new GlWithdrawLevelConfig();
        glWithdrawLevelConfig.setId(id);
        glWithdrawLevelConfig.setStatus(2);
        glWithdrawLevelConfig.setLastOperator(adminDO.getUsername());
        glWithdrawLevelConfig.setLastUpdate(now);
        glWithdrawLevelConfigBusiness.updateByPrimaryKeySelective(glWithdrawLevelConfig);
    }

    /**
     * 会员提现风控 列表
     */
    public PageInfo<GlWithdrawLevelConfig> listLevelConfig(WithdrawLevelConfigListDto dto) {
        PageHelper.startPage(dto.getPage(), dto.getSize());
        Condition con = new Condition(GlWithdrawLevelConfig.class);
        Example.Criteria criteria = con.createCriteria();
        criteria.andNotEqualTo("status", 2);
        criteria.andBetween("createTime", dto.getStartTime(), dto.getEndTime());
        if (-1 != dto.getLevelId()) {
            criteria.andEqualTo("levelId", dto.getLevelId());
        }
        if (null != dto.getStartTime()) {
            criteria.andGreaterThanOrEqualTo("createTime", dto.getStartTime());
        }
        if (null != dto.getEndTime()) {
            criteria.andLessThanOrEqualTo("createTime", dto.getEndTime());
        }
        if (null != dto.getCoinCode() && !dto.getCoinCode().equals("-1")) {
            criteria.andEqualTo("coin", dto.getCoinCode());
        }
        con.setOrderByClause("create_time desc");
        List<GlWithdrawLevelConfig> list = glWithdrawLevelConfigBusiness.findByCondition(con);
        PageInfo<GlWithdrawLevelConfig> pageInfo = new PageInfo(list);
        if (pageInfo.getList() != null && !pageInfo.getList().isEmpty()) {
            for (GlWithdrawLevelConfig config : pageInfo.getList()) {
                GlFundUserlevel level = fundUserlevelBusiness.findById(config.getLevelId());
                if (level != null) {
                    config.setLevelName(level.getName());
                }
            }
        }
        return pageInfo;
    }

    /**
     * 上报提交状态
     *
     * @param approveDto
     * @param withdraw
     * @param user
     * @param levelResult
     */
    private void updateWithdrawReport(WithdrawExceptionApproveDto approveDto, GlWithdraw withdraw,
                                      GlUserDO user, FundUserLevelResult levelResult, List<GlUserVipDo> vips) {
        //上报提现订单状态
        WithdrawReport report = new WithdrawReport();
        report.setUuid(withdraw.getOrderId());
        report.setStatus(WithdrawStatusEnum.WITHDRAWN_FAILED);
        report.setFinishTime(withdraw.getCreateDate());
        report.setTimestamp(withdraw.getCreateDate());
        report.setLastUpdate(withdraw.getLastUpdate());
        // 用户VIP等级
        UserVIPCache vipCache = userVipUtils.getUserVIPCache(user.getId());
        if (ObjectUtils.isEmpty(vipCache) == false) {
            report.setVipLevel(vipCache.getVipLevel());
            report.set("vipLevel", vipCache.getVipLevel());
        }
        // 用户层级
        FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(user.getId());
        if (ObjectUtils.isEmpty(userLevel) == false) {
            report.setUserLevel(userLevel.getLevelId());
            report.setUserLevelName(userLevel.getLevelName());
        }

        report.setRejectReason(approveDto.getRejectReason());
        Optional<GlFundUserlevel> filter = fundUserlevelBusiness.filter(levelResult, user.getId());
        if (filter.isPresent()) {
            GlFundUserlevel glFundUserlevel = filter.get();
            report.setUserLevel(glFundUserlevel.getLevelId());
            report.setUserLevelName(glFundUserlevel.getName());
        }

        if (user.getUserType() == UserConstant.Type.PROXY) {  //代理给默认 -1，便于es查询
            report.setVipLevel(-1);
        } else {
            if (!CollectionUtils.isEmpty(vips)) {
                Optional<GlUserVipDo> optional = vips.stream().filter(v -> v.getUserId().equals(user.getId())).findFirst();
                if (optional.isPresent()) {
                    report.setVipLevel(optional.get().getVipLevel());
                }
            }
        }
        reportService.withdrawReport(report);
    }

    /**
     * 上报提现退回
     *
     * @param approveDto
     * @param withdraw
     * @param user
     */
    private void updateWithdrawReturnReport(WithdrawExceptionApproveDto approveDto, GlWithdraw withdraw,
                                            DigitalUserAccount account, GlUserDO user) {
        //上报提现退回
        Date returnTime = new Date(approveDto.getUpdateTime().getTime() - 1000);
        String returnId = redisService.getTradeNo("TH");
        WithdrawReturnReport returnReport = new WithdrawReturnReport();
        returnReport.setAmount(withdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
        //添加账变前后金额
        returnReport.setBalanceAfter(account.getBalance().add(withdraw.getAmount()).multiply(BigDecimal.valueOf(100000000)).longValue());
        returnReport.setBalanceBefore(account.getBalance().multiply(BigDecimal.valueOf(100000000)).longValue());
        returnReport.setCreateTime(returnTime);
        returnReport.setTimestamp(returnTime);
        returnReport.setFinishTime(returnTime);
        returnReport.setParentName(user.getParentName());
        returnReport.setParentId(user.getParentId());
        returnReport.setUid(user.getId());
        returnReport.setUserName(user.getUsername());
        returnReport.setUserType(UserTypeEnum.valueOf(user.getUserType()));
        returnReport.setWithdrawId(withdraw.getOrderId());
        returnReport.setStatus(1);
        returnReport.setUuid(returnId);
        returnReport.setSubType("提现退回");
        returnReport.setIsFake(user.getIsFake());
        returnReport.setCoin(withdraw.getCoin());
        reportService.reportWithdrawReturn(returnReport);
    }

    /**
     * 拆单处理父单状态
     *
     * @param withdraw
     */
    private void updateParentOrderStatus(GlWithdraw withdraw) {
        if (ObjectUtils.isEmpty(withdraw) || ObjectUtils.isEmpty(withdraw.getSplitStatus())) {
            return;
        }
        if (withdraw.getSplitStatus() != 1) {
            return;
        }

        // 其他的中间条件不用上报父单最新状态
        if (6 == withdraw.getStatus() || 7 == withdraw.getStatus()
                || 10 == withdraw.getStatus() || -3 == withdraw.getStatus()) {
            return;
        }

        List<GlWithdrawSplit> glWithdrawSplits = glWithdrawSplitMapper.findAllSplitOrderByOrderId(withdraw.getOrderId());
        if (ObjectUtils.isEmpty(glWithdrawSplits)) {
            return;
        }

        String parentOrderId = "";
        List<String> orderIds = new ArrayList<>();
        for (GlWithdrawSplit glWithdrawSplit : glWithdrawSplits) {
            parentOrderId = ObjectUtils.isEmpty(parentOrderId) ? glWithdrawSplit.getParentId() : parentOrderId;
            orderIds.add(glWithdrawSplit.getOrderId());
        }

        List<GlWithdraw> subWithdraws = glWithdrawMapper.findWithdrawByOrderId(orderIds);
        if (ObjectUtils.isEmpty(subWithdraws)) {
            return;
        }

        StringBuffer subStatus = new StringBuffer();
        Date createDate = null;
        for (GlWithdraw subWithdraw : subWithdraws) {
            if (1 == subWithdraw.getStatus()
                    || 8 == subWithdraw.getStatus()) {
                subStatus.append(1);
            } else if (2 == subWithdraw.getStatus()
                    || -2 == subWithdraw.getStatus()
                    || 3 == subWithdraw.getStatus()
                    || 4 == subWithdraw.getStatus()
                    || 5 == subWithdraw.getStatus()
                    || 9 == subWithdraw.getStatus()) {
                subStatus.append(2);
            } else {
                subStatus.append(0);
            }

            createDate = subWithdraw.getCreateDate();
        }

        WithdrawParentOrderReport parentOrderReport = new WithdrawParentOrderReport();
        parentOrderReport.setUuid(parentOrderId);
        parentOrderReport.setSubOrderStatus(subStatus.toString());
        parentOrderReport.setTimestamp(createDate);
        WithdrawStatusEnum status = null;

        // 父单计算提现状态
        if (subStatus.toString().contains("0")) {
            status = WithdrawStatusEnum.valueOf(0);
        } else if (subStatus.toString().contains("1") && !subStatus.toString().contains("2")) {
            status = WithdrawStatusEnum.valueOf(1);
        } else if (!subStatus.toString().contains("1") && subStatus.toString().contains("2")) {
            status = WithdrawStatusEnum.valueOf(2);
        } else {
            status = WithdrawStatusEnum.valueOf(3);
        }

        parentOrderReport.setStatus(status);
        reportService.parentOrderReport(parentOrderReport);
    }

    /**
     * 提现失败通知
     *
     * @param withdraw
     * @param approveDto
     */
    private void doWithdrawFailNotice(GlWithdraw withdraw, WithdrawExceptionApproveDto approveDto) {
        NoticeFailDto noticeDto = new NoticeFailDto();
        noticeDto.setOrderId(withdraw.getOrderId());
        noticeDto.setUserId(withdraw.getUserId());
        noticeDto.setUserName(withdraw.getUsername());
        noticeDto.setAmount(withdraw.getAmount());
        noticeDto.setRejectReason(approveDto.getRejectReason());
        noticeDto.setCoin(DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()).getDesc());
        noticeHandler.doFailNotice(noticeDto);
    }
}
