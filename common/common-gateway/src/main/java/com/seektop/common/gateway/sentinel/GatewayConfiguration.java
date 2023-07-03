package com.seektop.common.gateway.sentinel;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayParamFlowItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.SentinelGatewayFilter;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.exception.SentinelGatewayBlockExceptionHandler;
import com.alibaba.csp.sentinel.datasource.ReadableDataSource;
import com.alibaba.csp.sentinel.datasource.nacos.NacosDataSource;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.result.view.ViewResolver;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * 限流配置动态加载
 */
@Slf4j
@Configuration
public class GatewayConfiguration {

    /**
     * API定义配置DataID
     */
    @Value("${sentinel.api.dataId}")
    private String apiDefinitionDataId;

    /**
     * 限流规则定义配置DataId
     */
    @Value("${sentinel.flow.rule.dataId}")
    private String flowRuleDataId;

    @Autowired
    private NacosConfigProperties properties;

    private final List<ViewResolver> viewResolvers;
    private final ServerCodecConfigurer serverCodecConfigurer;

    public GatewayConfiguration(ObjectProvider<List<ViewResolver>> viewResolversProvider, ServerCodecConfigurer serverCodecConfigurer) {
        this.viewResolvers = viewResolversProvider.getIfAvailable(Collections::emptyList);
        this.serverCodecConfigurer = serverCodecConfigurer;
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SentinelGatewayBlockExceptionHandler sentinelGatewayBlockExceptionHandler() {
        return new SentinelGatewayBlockExceptionHandler(viewResolvers, serverCodecConfigurer);
    }

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public GlobalFilter sentinelGatewayFilter() {
        return new SentinelGatewayFilter();
    }

    @PostConstruct
    public void init() {
        try {
            // API分组定义
            ReadableDataSource<String, Set<ApiDefinition>> apiDefinitionDataSource = new NacosDataSource<>(properties.getServerAddr(), properties.getGroup(), apiDefinitionDataId,  this::converterApiDefinition);
            GatewayApiDefinitionManager.register2Property(apiDefinitionDataSource.getProperty());
            // 限流规则配置加载
            ReadableDataSource<String, Set<GatewayFlowRule>> flowRuleDataSource = new NacosDataSource<>(properties.getServerAddr(), properties.getGroup(), flowRuleDataId,  this::converterGatewayFlowRule);
            GatewayRuleManager.register2Property(flowRuleDataSource.getProperty());
        } catch (Exception e) {
            log.error("GatewayConfiguration init() error", e);
        }
        // 设置限流自定义错误消息Handler
        GatewayCallbackManager.setBlockHandler(new CustomerBlockRequestHandler());
    }

    private Set<ApiDefinition> converterApiDefinition(final String source) {
        Set<ApiDefinition> apiDefinitionSet = Sets.newHashSet();
        if (StringUtils.isEmpty(source)) {
            return apiDefinitionSet;
        }
        JSONObject obj = JSON.parseObject(source);
        if (obj.containsKey("configs") == false) {
            return apiDefinitionSet;
        }
        JSONArray apiDefinitionArray = obj.getJSONArray("configs");
        for (int i = 0, len = apiDefinitionArray.size(); i < len; i++) {
            JSONObject apiObj = apiDefinitionArray.getJSONObject(i);
            JSONArray urlArray = apiObj.getJSONArray("url");
            ApiDefinition apiDefinition = new ApiDefinition(apiObj.getString("id"));
            Set<ApiPredicateItem> predicateItems = Sets.newHashSet();
            for (int j = 0, jlen = urlArray.size(); j < jlen; j++) {
                JSONObject urlObj = urlArray.getJSONObject(j);
                String urlPath = urlObj.getString("path");
                Integer urlMatchStrategy = urlObj.getIntValue("matchStrategy");
                predicateItems.add(new ApiPathPredicateItem().setPattern(urlPath).setMatchStrategy(urlMatchStrategy));
            }
            apiDefinition.setPredicateItems(predicateItems);
            apiDefinitionSet.add(apiDefinition);
        }
        return apiDefinitionSet;
    }

    private Set<GatewayFlowRule> converterGatewayFlowRule(final String source) {
        Set<GatewayFlowRule> flowRuleSet = Sets.newHashSet();
        JSONObject flowRuleObj = JSONObject.parseObject(source);
        if (flowRuleObj.containsKey("rules") == false) {
            return flowRuleSet;
        }
        JSONArray flowArray = flowRuleObj.getJSONArray("rules");
        for (int i = 0, len = flowArray.size(); i < len; i++) {
            JSONObject flowObj = flowArray.getJSONObject(i);
            GatewayFlowRule flowRule = new GatewayFlowRule(flowObj.getString("resource_id"));
            flowRule.setBurst(flowObj.getIntValue("burst"));
            flowRule.setCount(flowObj.getIntValue("count"));
            flowRule.setIntervalSec(flowObj.getLongValue("interval-second"));
            String resourceMode = flowObj.getString("resource_mode");
            // 指定资源模式
            if ("route".equals(resourceMode)) {
                flowRule.setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_ROUTE_ID);
            } else if ("api".equals(resourceMode)) {
                flowRule.setResourceMode(SentinelGatewayConstants.RESOURCE_MODE_CUSTOM_API_NAME);
            }
            // 如果有配置限流详细参数,处理详细参数内容
            if (flowObj.containsKey("paramItem")) {
                setGatewayParamFlowItem(flowRule, flowObj.getJSONObject("paramItem"));
            }
            flowRuleSet.add(flowRule);
        }
        return flowRuleSet;
    }

    private void setGatewayParamFlowItem(GatewayFlowRule flowRule, JSONObject paramItemObj) {
        GatewayParamFlowItem gatewayParamFlowItem = new GatewayParamFlowItem();
        // 设置限流参数的策略
        if (paramItemObj.containsKey("parseStrategy")) {
            String parseStrategy = paramItemObj.getString("parseStrategy");
            switch (parseStrategy) {
                case "IP":
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_CLIENT_IP);
                break;
                case "Host":
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HOST);
                break;
                case "Header":
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_HEADER);
                break;
                case "URL":
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_PARSE_STRATEGY_URL_PARAM);
                break;
            }
        }
        // 若提取策略选择 Header 模式或 URL 参数模式，则需要指定对应的 header 名称或 URL 参数名称
        if (paramItemObj.containsKey("fieldName")) {
            String fieldName = paramItemObj.getString("fieldName");
            gatewayParamFlowItem.setFieldName(fieldName);
        }
        // 参数值的匹配模式，只有匹配该模式的请求属性值会纳入统计和流控；若为空则统计该请求属性的所有值。
        if (paramItemObj.containsKey("pattern")) {
            String pattern = paramItemObj.getString("pattern");
            gatewayParamFlowItem.setPattern(pattern);
        }
        // 参数值的匹配策略
        if (paramItemObj.containsKey("matchStrategy")) {
            int matchStrategy = paramItemObj.getIntValue("matchStrategy");
            switch (matchStrategy) {
                case 0:
                    // 精确匹配
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_EXACT);
                break;
                case 1:
                    // 前缀匹配
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_PREFIX);
                break;
                case 2:
                    // 正则匹配
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_REGEX);
                break;
                case 3:
                    // 子串匹配
                    gatewayParamFlowItem.setParseStrategy(SentinelGatewayConstants.PARAM_MATCH_STRATEGY_CONTAINS);
                break;
            }
        }
        flowRule.setParamItem(gatewayParamFlowItem);
    }

}