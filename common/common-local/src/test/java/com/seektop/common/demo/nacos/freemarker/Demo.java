package com.seektop.common.demo.nacos.freemarker;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.BaseTest;

import com.seektop.common.demo.nacos.freemarker.enums.DemoConfigDicEnums;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.enumerate.Language;
import lombok.Data;

public class Demo extends BaseTest {

    public void test(){
        /**
         * 传递对象，或者map，JSONObject
         */
        User user = new User();
        user.setName("张三");

        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_TITILE)
                        .parse(Language.EN,user)
        );

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("balance",1000);
        jsonObject.put("amount",1000);
        jsonObject.put("fee",80);
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_BODY)
                        .parse(Language.EN,jsonObject)
        );
    }

    @Data
    public class User{
        String name;
    }
}
