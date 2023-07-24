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
public class ParamBaseDO implements Serializable {

    private String headerToken;

    private Integer headerUid;

    @NotNull(message = "应用类型不能为空")
    private Integer headerAppType;

    @NotBlank(message = "设备号不能为空")
    private String headerDeviceId;

    @NotNull(message = "设备类型不能为空")
    private Integer headerOsType;

    private String headerUserAgent;

    private String headerVersion;

    private String requestIp;

    private String requestUrl;

    private String headerHost;

    private String headerLanguage;

    public String getHeaderLanguage(){
        if(StringUtils.isEmpty(headerLanguage)){
            return Language.ZH_CN.getCode();
        }else {
            final Language language = Language.getLanguage(headerLanguage);
            if(null == language){
              return Language.ZH_CN.getCode();
            }
            return headerLanguage;
        }
    }

    /**
     * 当前选择的币种
     */
    private String headerCoinCode;

    public Language getLanguage() {
        if (StringUtils.isEmpty(this.headerLanguage)) {
            return Language.ZH_CN;
        }
        Language language = Language.getLanguage(this.headerLanguage);
        return ObjectUtils.isEmpty(language) ? Language.ZH_CN : language;
    }

}