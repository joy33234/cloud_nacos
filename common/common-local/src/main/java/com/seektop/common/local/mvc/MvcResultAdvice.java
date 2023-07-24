package com.seektop.common.local.mvc;

import com.seektop.common.local.base.LocalKeyConfig;
import com.seektop.common.local.constant.enums.ResultCodeEnums;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.rest.Result;
import com.seektop.enumerate.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@Slf4j
@ControllerAdvice
public class MvcResultAdvice implements ResponseBodyAdvice {

    @Override
    public boolean supports(MethodParameter methodParameter, Class clas) {
        return methodParameter.getParameterType() == Result.class;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType, Class selectedConverterType, ServerHttpRequest request, ServerHttpResponse response) {
        Result result = (Result) body;
        if (ObjectUtils.isEmpty(result)) {
            return body;
        }
        String deviceLanguage = request.getHeaders().getFirst("language");
        Language language = Language.getLanguage(deviceLanguage);
        if (ObjectUtils.isEmpty(language)) {
            language = Language.ZH_CN;
        }
        if (null != result.getKeyConfig()) {
            String message =
                    LanguageLocalParser.key((LocalKeyConfig) result.getKeyConfig())
                            .withDefaultValue(result.getMessage())
                            .parse(language,result.getLanguagePassVal());
            result.setMessage(message);
        }
        if (null == result.getKeyConfig()) {
            final Integer code = result.getCode();
            String resultMessage = result.getMessage();
            if(null == resultMessage){
                resultMessage = "";
            }
            String message =
                    LanguageLocalParser.key(ResultCodeEnums.MVC_RESULT_CODE)
                            .withParam(code+"")
                            .withDefaultValue(resultMessage)
                            .parse(language);
            result.setMessage(message);
        }
        return result;
    }
}

