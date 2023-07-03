package com.seektop.common.demo.mysql.freemarker;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.BaseTest;
import com.seektop.common.demo.mysql.freemarker.enums.DemoConfigDicEnums;
import com.seektop.common.demo.mysql.mapper.DicModel;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.local.tools.LanguageLocalSource;
import com.seektop.enumerate.Language;
import lombok.Data;
import org.junit.Test;

public class Demo extends BaseTest {


    @Test
    public void insert() {
        LanguageLocalSource.builder()
                .add(new DicModel()
                        .buildKey(DemoConfigDicEnums.NACOS_NORMAL_TITILE,Language.ZH_CN)
                        .build("尊敬的客户${name} 您有一条新消息"))
                .add(new DicModel()
                        .buildKey(DemoConfigDicEnums.NACOS_NORMAL_TITILE,Language.EN)
                        .build("Dear ${name}"))

                .add(new DicModel()
                        .buildKey(DemoConfigDicEnums.NACOS_NORMAL_BODY,Language.ZH_CN)
                        .build("之前的余额：${balanceBefore}，现在的余额:${balanceBefore+prize}，冻结：${freeze},可用：${balanceBefore+prize-freeze}获得奖金:${prize}"))
                .add(new DicModel()
                        .buildKey(DemoConfigDicEnums.NACOS_NORMAL_BODY,Language.EN)
                        .build("before balance：${balanceBefore}，balance:${balanceBefore+prize}，freeze：${freeze},available：${balanceBefore+prize-freeze} prize:${prize}"))
        .submit();
        ;
    }

    public void test() {
        /**
         * 传递对象，或者map，JSONObject
         */
        User user = new User();
        user.setName("张三");

        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_TITILE)
                        .parse(Language.EN, user)
        );

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("balanceBefore", 880);
        jsonObject.put("freeze", 1000);
        jsonObject.put("prize", 80);
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_BODY)
                        .parse(Language.EN, jsonObject)
        );

        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_BODY)
                        .parse(Language.ZH_CN, jsonObject)
        );
    }

    @Data
    public class User {
        String name;
    }
}
