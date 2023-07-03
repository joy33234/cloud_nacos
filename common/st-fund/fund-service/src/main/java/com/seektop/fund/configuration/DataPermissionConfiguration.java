package com.seektop.fund.configuration;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.utils.DataPermissionHelper;
import com.seektop.system.service.GlSystemDataMenuService;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataPermissionConfiguration {

    @Reference(timeout = 3000)
    public void setNewSystemDataMenu(GlSystemDataMenuService dataMenuService){
        // RPCResponse<List<Long>> allDataMenuIds = dataMenuService.getAllDataMenuIds();
        // List<Long> data = allDataMenuIds.getData();
        // String dataMenus = JSONObject.toJSONString(data);
        // ？ 是否需要只在项目启动的时候只初始化一次
        //DataPermissionHelper.setNewSystemGet(()->dataMenus);
        DataPermissionHelper.setNewSystemGet(()->{
            RPCResponse<List<Long>> allDataMenuIds = dataMenuService.getAllDataMenuIds();
            List<Long> data = allDataMenuIds.getData();
            String dataMenus = JSONObject.toJSONString(data);
            return dataMenus;
        });
    }
}
