package com.seektop.common.demo.mvc.model;

import com.seektop.common.local.base.LanguageParse;
import com.seektop.common.local.base.dto.LanguageDTO;
import lombok.Data;

@Data
public class ActivityParam {

    private String name;
    /**
     * 表示支持多语言传参
     * nameLocal{
     *     "en":"321313",
     *     "zh_CN":"丢雷"
     * }
     */
    private LanguageDTO nameLocal;
    /**
     * 表示支持多语言传参
     * desc{
     *     "en":"321313",
     *     "zh_CN":"丢雷".
     * }
     */
    private LanguageDTO desc;
    private Integer id;
}
