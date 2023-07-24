package com.seektop.fund.vo;

import com.seektop.enumerate.Language;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ManageParamBase implements Serializable {

    private String headerLanguage;

    public Language getLanguage() {
        if (StringUtils.isEmpty(this.headerLanguage)) {
            return Language.ZH_CN;
        }
        Language language = Language.getLanguage(this.headerLanguage);
        return ObjectUtils.isEmpty(language) ? Language.ZH_CN : language;
    }


}