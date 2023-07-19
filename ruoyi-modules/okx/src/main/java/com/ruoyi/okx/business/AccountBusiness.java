package com.ruoyi.okx.business;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.okx.domain.*;
import com.ruoyi.okx.mapper.OkxAccountMapper;
import com.ruoyi.okx.params.DO.OkxAccountDO;
import com.ruoyi.okx.params.dto.AccountProfitDto;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.DtoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountBusiness extends ServiceImpl<OkxAccountMapper, OkxAccount> {

    @Resource
    private OkxAccountMapper accountMapper;

    @Resource
    private SettingService settingService;

    @Resource
    private AccountBalanceBusiness balanceBusiness;


    public List<OkxAccount> list(OkxAccountDO account) {
        LambdaQueryWrapper<OkxAccount> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq((null != account.getApikey()), OkxAccount::getApikey, account.getApikey());
        wrapper.eq((null != account.getSecretkey()), OkxAccount::getSecretkey, account.getSecretkey());
        wrapper.eq((null != account.getPassword()), OkxAccount::getPassword, account.getPassword());
        wrapper.eq((null != account.getName()), OkxAccount::getName, account.getName());
        return this.list(wrapper);
    }

    public boolean save(OkxAccount account) {
        boolean result =  accountMapper.insert(account) > 0 ? true : false;
        if (result) {
            balanceBusiness.initBalance(account.getName());
        }
        return result;
    }


    public boolean update(OkxAccount account) {
        return accountMapper.updateById(account) > 0 ? true : false;
    }

    public boolean delete(OkxAccount account) {
        return accountMapper.deleteById(account) > 0 ? true : false;
    }

    public OkxAccount findOne(Integer accountId) {
        return accountMapper.selectById(accountId);
    }

    public OkxAccount findByName(String name) {
        LambdaQueryWrapper<OkxAccount> wrapper = new LambdaQueryWrapper();
        wrapper.eq((StringUtils.isNotEmpty(name)), OkxAccount::getName, name);
        return accountMapper.selectOne(wrapper);
    }


    public Map<String, String> getAccountMap(OkxAccount account) {
        Map<String, String> accountMap = new ConcurrentHashMap<>(8);
        accountMap.put("id", account.getId().toString());
        accountMap.put("accountName", account.getName());
        accountMap.put("apikey", account.getApikey());
        accountMap.put("password", account.getPassword());
        accountMap.put("secretkey", account.getSecretkey());
        return accountMap;
    }

    public Map<String, String> getAccountMap(String name) {
        OkxAccount account = findByName(name);
        return getAccountMap(account);
    }


    public String checkKeyUnique(OkxAccountDO accountDO)
    {
        OkxAccount account = this.getById(accountDO.getId());
        if (StringUtils.isNotNull(account) && account.getApikey().equals(accountDO.getApikey())){
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 查询参数配置信息
     *
     * @param accountId 参数配置ID
     * @return 参数配置信息
     */
    public List<OkxSetting> listByAccountId(Integer accountId) {
        String settingIds = this.getById(accountId).getSettingIds();
        return settingService.selectSettingByIds(DtoUtils.StringToLong(settingIds.split(",")));
    }

}
