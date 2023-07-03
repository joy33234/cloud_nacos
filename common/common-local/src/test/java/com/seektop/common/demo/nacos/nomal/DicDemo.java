package com.seektop.common.demo.nacos.nomal;

import com.seektop.common.BaseTest;
import com.seektop.common.demo.nacos.nomal.enums.DemoConfigDicEnums;
import com.seektop.common.demo.nacos.nomal.enums.DemoConfigNormalEnums;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.enumerate.Language;

public class DicDemo extends BaseTest {

    public void test(){
        /**
         * 普通文字读取-en
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL)
                .parse(Language.EN)
        );

        /**
         * 普通文字读取-zh_CN
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL)
                        .parse(Language.ZH_CN)
        );

        /**
         * 带参数读取-en
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_DEMO_PARAM)
                        .withParam("1")
                        .parse(Language.EN)
        );

        /**
         * 带参数读取-zh_CN
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_DEMO_PARAM)
                        .withParam("1")
                        .parse(Language.ZH_CN)
        );

        /**
         * 带多个参数读取-en
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_MULTIPART_PARAM)
                        .withParam("1","job")
                        .parse(Language.EN)
        );

        /**
         * 带多个参数读取-zh_CN
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_MULTIPART_PARAM)
                        .withParam("1","name")
                        .parse(Language.ZH_CN)
        );

        /**
         * 结果中需要参数-en
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_RESULT)
                        .parse(Language.EN,"tom","usa")
        );

        /**
         * 带多个参数读取-zh_CN
         */
        System.out.println(
                LanguageLocalParser.key(DemoConfigDicEnums.NACOS_NORMAL_RESULT)
                        .parse(Language.ZH_CN,"熊猫","雅安")
        );
    }
}
