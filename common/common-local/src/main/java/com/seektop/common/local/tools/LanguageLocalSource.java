package com.seektop.common.local.tools;


import com.seektop.common.local.base.DataSourceEntity;
import com.seektop.common.local.base.LocalKeyConfig;
import com.seektop.common.local.context.LanguageDataSourceContext;
import com.seektop.enumerate.Language;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LanguageLocalSource {

    List<DataSourceEntity> dataSourceList = new ArrayList<>();

    public static LanguageLocalSource builder(){
        return new LanguageLocalSource();
    }

    public  LanguageLocalSource add(DataSourceEntity dataSource){
        dataSourceList.add(dataSource);
        return this;
    }

    public  LanguageLocalSource add(LanguageLocalSource languageLocalSource){
        dataSourceList.addAll(languageLocalSource.dataSourceList);
        return this;
    }

    public void submit(){
        LanguageDataSourceContext.getDataBaseLanguageConfigRegister().insert(dataSourceList);
    }

}
