package com.seektop.common.local.configuration;


import com.seektop.common.local.base.DataSourceEntity;
import com.seektop.common.local.base.annotation.LanguageDataSourceConfiguration;
import com.seektop.common.local.mapper.DemoDicMapper;
import com.seektop.common.local.mapper.DicModel;
import com.seektop.common.local.register.DataBaseLanguageConfigRegister;
import tk.mybatis.spring.annotation.MapperScan;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.List;

@LanguageDataSourceConfiguration
@MapperScan("com.seektop.common.local.mapper")
public class MysqlDatasourceConfig extends DataBaseLanguageConfigRegister {

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
