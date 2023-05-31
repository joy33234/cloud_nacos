package com.ruoyi.pandora.config;

import org.apache.coyote.http11.Http11NioProtocol;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.ConfigurableWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.stereotype.Component;

/**
 * @Version 1.0
 * @describetion 当Spring容器内没有TomcatEmbeddedServletContainerFactory這個bean时加载
 */
@Component
public class WebServerConfiguration implements WebServerFactoryCustomizer<ConfigurableWebServerFactory> {
    @Override
    public void customize(ConfigurableWebServerFactory configurableWebServerFactory) {
        //使用对应工厂类提供给我们的接口定制化我们的tomcat connector
        ((TomcatServletWebServerFactory) configurableWebServerFactory).addConnectorCustomizers(
                (connector) -> {
                    Http11NioProtocol protocol = (Http11NioProtocol) connector.getProtocolHandler();
                    //定制化keepalivetimeout,设置61秒内没有请求则服务端自动断开keepalive链接
                    protocol.setKeepAliveTimeout(61000);
                    //当客户端发送超过10000个请求则自动断开keepalive链接
                    protocol.setMaxKeepAliveRequests(20000);
                }
        );
    }
}