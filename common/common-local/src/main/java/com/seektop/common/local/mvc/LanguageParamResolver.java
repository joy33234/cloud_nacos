package com.seektop.common.local.mvc;


import com.alibaba.fastjson.JSONObject;
import com.seektop.common.local.base.annotation.LanguageBackendParam;
import com.seektop.common.local.base.dto.LanguageDTO;
import com.seektop.enumerate.Language;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;


@Component
public class LanguageParamResolver implements HandlerMethodArgumentResolver {


    @Override
    public boolean supportsParameter(MethodParameter parameter) {

        return parameter.getParameterAnnotation(LanguageBackendParam.class) != null;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {
        final LanguageBackendParam parameterAnnotation = parameter.getParameterAnnotation(LanguageBackendParam.class);
        final LanguageDTO jsonObject = new LanguageDTO();
        String paramName = parameterAnnotation.name();
        for (Language language : Language.getEnabledLanguage()) {
            final String code = language.getCode();
            if(language.equals(Language.ZH_CN)){
                final String value = webRequest.getParameter(paramName);
                jsonObject.put(code, value);
                if(!StringUtils.isEmpty(value)){
                    continue;
                }
            }
            final String languageValue = webRequest.getParameter(paramName + upperFirst(code));
            jsonObject.put(code, languageValue);
            if(StringUtils.isEmpty(languageValue)){
                jsonObject.put(code, webRequest.getParameter(paramName + "."+code));
            }
        }
        return jsonObject;
    }

    public static String upperFirst(String name) {
        char[] cs = name.toCharArray();
        cs[0] -= 32;
        return String.valueOf(cs);
    }

}
