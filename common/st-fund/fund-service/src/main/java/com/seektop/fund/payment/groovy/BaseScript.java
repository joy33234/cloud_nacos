package com.seektop.fund.payment.groovy;

import java.util.Map;

public class BaseScript {
    @SuppressWarnings("unchecked")
    public static Object getResource(Object resourceMap, ResourceEnum resourceEnum) {
        return ((Map<ResourceEnum, Object>)resourceMap).get(resourceEnum);
    }
}