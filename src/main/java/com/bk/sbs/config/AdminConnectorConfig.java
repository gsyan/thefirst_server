//--------------------------------------------------------------------------------------------------
package com.bk.sbs.config;

import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

// 어드민 전용 커넥터 — 게임 API(8080)와 물리적으로 다른 포트로 분리해 /admin/**을 외부 도메인/리버스 프록시에서 격리
@Configuration
public class AdminConnectorConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Value("${admin.server.port:8081}")
    private int adminServerPort;

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        Connector adminConnector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
        adminConnector.setPort(adminServerPort);
        factory.addAdditionalTomcatConnectors(adminConnector);
    }
}
