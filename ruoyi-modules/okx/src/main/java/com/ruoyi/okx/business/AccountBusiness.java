package com.ruoyi.okx.business;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.okx.domain.OkxAccount;
import com.ruoyi.okx.mapper.OkxAccountMapper;
import com.ruoyi.okx.params.DO.OkxAccountDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AccountBusiness extends ServiceImpl<OkxAccountMapper, OkxAccount> {
    private static final Logger log = LoggerFactory.getLogger(AccountBusiness.class);

    @Resource
    private OkxAccountMapper accountMapper;


    public List<OkxAccount> list(OkxAccountDO account) {
        LambdaQueryWrapper<OkxAccount> wrapper = new LambdaQueryWrapper();
//        wrapper.eq((null != buyRecordDO.getCoin()), OkxAccount::getCoin, buyRecordDO.getCoin());
//        wrapper.between((account.getCreateTime() != null), OkxAccount::getUpdateTime, account.getCreateTime(), account.getEndTime());
//        OkxAccount okxAccount = accountMapper.selectById(1);
//        log.info(JSON.toJSONString(okxAccount));
//        accountMapper.selectList(wrapper);
        return list();
    }

    public boolean save(OkxAccount account) {
        return accountMapper.insert(account) > 0 ? true : false;
    }

    public boolean update(OkxAccount account) {
        return accountMapper.updateById(account) > 0 ? true : false;
    }

    public boolean delete(OkxAccount account) {
        return accountMapper.deleteById(account) > 0 ? true : false;
    }


    public Map<String, String> getAccountMap(OkxAccount account) {
        Map<String, String> accountMap = new HashMap<>(4);
        accountMap.put("id", account.getId().toString());
        accountMap.put("apikey", account.getApikey());
        accountMap.put("password", account.getPassword());
        accountMap.put("secretkey", account.getSecretkey());
        return accountMap;
    }

    public Map<String, String> getAccountMap() {
        Map<String, String> accountMap = new HashMap<>(4);
        try {
            OkxAccount account = this.list().get(0);
            accountMap.put("id", account.getId().toString());
            accountMap.put("accountName", account.getName());
            accountMap.put("apikey", account.getApikey());
            accountMap.put("password", account.getPassword());
            accountMap.put("secretkey", account.getSecretkey());
        } catch (Exception e) {
            throw new ServiceException("查询帐户异常");
        }
        return accountMap;
    }



    public String checkKeyUnique(OkxAccountDO accountDO)
    {
        OkxAccount account = this.getById(accountDO.getId());
        if (StringUtils.isNotNull(account) && account.getApikey().equals(accountDO.getApikey()))
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }
}
