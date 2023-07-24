package com.seektop.fund.common;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.enumerate.Language;
import com.seektop.fund.enums.FundLanguageDicEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FundLanguageUtils {

   public static String getPaymentName(Integer paymentId,String defaultName, Language language){
      if (ObjectUtils.isEmpty(paymentId) || ObjectUtils.isEmpty(language) || StringUtils.isEmpty(defaultName)) {
         return "";
      }
      return LanguageLocalParser.key(FundLanguageDicEnum.RECHARGE_METHOD).withParam(paymentId.toString()).withDefaultValue(defaultName).parse(language);
   }

}