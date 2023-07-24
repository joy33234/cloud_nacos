package com.seektop.common.utils;

import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.Option;

public class JsonPath {

  private static Configuration jsonConf = Configuration.defaultConfiguration().addOptions(Option.SUPPRESS_EXCEPTIONS);
  
  public static DocumentContext $$(String jsonContent) {
    return com.jayway.jsonpath.JsonPath.using(jsonConf).parse(jsonContent);
  }
  
}
