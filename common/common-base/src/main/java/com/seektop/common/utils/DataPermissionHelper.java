package com.seektop.common.utils;


import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;


public class DataPermissionHelper {


    private static Supplier<String> newSystemGet;

    public static void setNewSystemGet(Supplier<String> newSystemGet) {
        DataPermissionHelper.newSystemGet = newSystemGet;
    }

    public static String getDataMenuList(Supplier<String> old){
        return PermissionUtils.checkPermission(
                // 新系统返回所有的权限
                Optional.ofNullable(newSystemGet).orElseThrow(()->new IllegalArgumentException("未配置查询数据权限的方法，请检查配置")),
                //老系统返回该岗位具有的权限
                old
        );
    }

    public static String getNewDataMenuList(Supplier<String> old, String data){
        return PermissionUtils.checkPermission(
                ()->{
                    if(StringUtils.isEmpty(data)){
                        return old.get();
                    }
                    List<Long> list = JSONArray.parseArray(old.get(), Long.class);
                    List<Long> dataList = JSONArray.parseArray(data, Long.class);
                    if(!ObjectUtils.isEmpty(list) && list.contains(2010000L)){
                        if(ObjectUtils.isEmpty(dataList) || !dataList.contains(1L)){
                            list.remove(2010000L);
                        }
                    }else {
                        list = Lists.newArrayList();
                        if(!ObjectUtils.isEmpty(dataList) && dataList.contains(1L)){
                            list.add(2010000L);
                        }
                    }
                    return JSONObject.toJSONString(list);
                },
                //老系统返回该岗位具有的权限
                ()->old.get()
        );
    }

}
