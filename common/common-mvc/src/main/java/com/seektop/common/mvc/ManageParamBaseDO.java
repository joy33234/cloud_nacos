package com.seektop.common.mvc;

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
public class ManageParamBaseDO implements Serializable {

	private String headerToken;

    private Integer headerUid;

    private String headerHost;

    @NotBlank(message = "设备号不能为空")
    private String headerDeviceId;

    @NotNull(message = "设备类型不能为空")
    private Integer headerOsType;

    private String headerLanguage;

    public Language getLanguage() {
        if (StringUtils.isEmpty(this.headerLanguage)) {
            return Language.ZH_CN;
        }
        Language language = Language.getLanguage(this.headerLanguage);
        return ObjectUtils.isEmpty(language) ? Language.ZH_CN : language;
    }


}