package com.seektop.common.mybatis.mapper;

import com.seektop.common.mybatis.provider.STDuplicateUpdateProvider;
import org.apache.ibatis.annotations.InsertProvider;
import tk.mybatis.mapper.annotation.RegisterMapper;

import java.util.Collection;

@RegisterMapper
public interface STDuplicateSelectiveMapper<T> {

    @InsertProvider(
            type = STDuplicateUpdateProvider.class,
            method = "dynamicSQL"
    )
    int duplicateSelective(Collection<? extends T> var1);

}