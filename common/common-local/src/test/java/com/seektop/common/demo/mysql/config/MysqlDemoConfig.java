package com.seektop.common.demo.mysql.config;

import com.google.common.collect.Sets;
import com.seektop.common.demo.mysql.mapper.DemoDicMapper;
import com.seektop.common.demo.mysql.mapper.DicModel;
import com.seektop.common.demo.mysql.nomal.enums.DemoConfigDicEnums;
import com.seektop.common.demo.mysql.nomal.enums.DemoConfigNormalEnums;
import com.seektop.common.local.base.DataSourceEntity;
import com.seektop.common.local.base.annotation.LanguageDataSourceConfiguration;
import com.seektop.common.local.config.NacosLanguageLocalRegister;
import com.seektop.common.local.constant.enums.RegisterTypeEnums;
import com.seektop.common.local.register.DataBaseLanguageConfigRegister;
import com.seektop.common.local.register.NacosLanguageConfigRegister;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@LanguageDataSourceConfiguration
public class MysqlDemoConfig extends DataBaseLanguageConfigRegister {

    @Resource
    private DemoDicMapper dicMapper;

    @Override
    public void insert(List<? extends DataSourceEntity> dataSource) {

        /**
         * 重复的就更新
         */
        dicMapper.duplicateSelective((Collection<? extends DicModel>) dataSource);
    }

    @Override
    public String get(String module, String key, String language) {
        DicModel value = new DicModel();
        value.setConfigKey(key);
        value.setModule(module);
        value.setLanguage(language);
        final List<DicModel> select = dicMapper.select(value);
        return select.get(0).getConfigValue();
    }
}
