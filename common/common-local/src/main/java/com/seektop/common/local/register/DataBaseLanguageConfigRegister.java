package com.seektop.common.local.register;




import com.seektop.common.local.base.DataSourceEntity;

import java.util.List;
import java.util.Set;

public abstract class DataBaseLanguageConfigRegister {

    public abstract void insert(List<? extends DataSourceEntity> dataSource);

    public abstract String get(String module,String key,String language);

}
