package com.seektop.common.demo.mysql.nomal;

import com.seektop.common.BaseTest;
import com.seektop.common.demo.mysql.mapper.DicModel;
import com.seektop.common.demo.mysql.nomal.enums.DemoConfigNormalEnums;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.local.tools.LanguageLocalSource;
import com.seektop.enumerate.Language;
import org.junit.Test;

public class Demo extends BaseTest {

    @Test
    public void testInsert() {
        LanguageLocalSource.builder()
                .add(new DicModel()
                        .buildKey(DemoConfigNormalEnums.NACOS_NORMAL, Language.EN)
                        .build("normal text config"))
                .add(new DicModel()
                        .buildKey(DemoConfigNormalEnums.NACOS_NORMAL, Language.ZH_CN)
                        .build("普通文字设置"))
                .add(new DicModel()
                        .buildKey(DemoConfigNormalEnums.NACOS_NORMAL_DEMO_PARAM, Language.EN,"1")
                        .build("with param you can set json or string with acticity 1"))
                .add(new DicModel()
                        .buildKey(DemoConfigNormalEnums.NACOS_NORMAL_DEMO_PARAM, Language.ZH_CN,"1")
                        .build("设置参数，比如活动1"))
                .add(new DicModel()
                        .buildKey(DemoConfigNormalEnums.NACOS_NORMAL_DEMO_PARAM, Language.EN,"2")
                        .build("with param you can set json or string with acticity 2"))
                .add(new DicModel()
                        .buildKey(DemoConfigNormalEnums.NACOS_NORMAL_DEMO_PARAM, Language.ZH_CN,"2")
                        .build("设置参数，比如活动2"))
        .submit();
        ;
    }

    @Test
    public void test() {
        /**
         * 普通文字读取-en
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigNormalEnums.NACOS_NORMAL)
                        .parse(Language.EN)
        );

        /**
         * 普通文字读取-zh_CN
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigNormalEnums.NACOS_NORMAL)
                        .parse(Language.ZH_CN)
        );

        /**
         * 带参数读取-en
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigNormalEnums.NACOS_NORMAL_DEMO_PARAM)
                        .withParam("1")
                        .parse(Language.EN)
        );

        /**
         * 带参数读取-en
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigNormalEnums.NACOS_NORMAL_DEMO_PARAM)
                        .withParam("2")
                        .parse(Language.ZH_CN)
        );

    }
}
