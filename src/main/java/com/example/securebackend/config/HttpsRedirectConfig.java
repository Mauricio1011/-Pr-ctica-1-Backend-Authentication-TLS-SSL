package com.example.securebackend.config;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.http-redirect.enabled", havingValue = "true", matchIfMissing = true)
public class HttpsRedirectConfig {

    private static final String HTTP_PROTOCOL = "org.apache.coyote.http11.Http11NioProtocol";

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpToHttpsRedirectCustomizer(
            @Value("${app.http-redirect.http-port:8080}") int httpPort,
            @Value("${server.port:8443}") int httpsPort) {
        return factory -> {
            factory.addContextCustomizers(context -> {
                SecurityConstraint constraint = new SecurityConstraint();
                constraint.setUserConstraint("CONFIDENTIAL");
                SecurityCollection collection = new SecurityCollection();
                collection.addPattern("/*");
                constraint.addCollection(collection);
                context.addConstraint(constraint);
            });
            factory.addAdditionalTomcatConnectors(createHttpConnector(httpPort, httpsPort));
        };
    }

    private Connector createHttpConnector(int httpPort, int httpsPort) {
        Connector connector = new Connector(HTTP_PROTOCOL);
        connector.setScheme("http");
        connector.setSecure(false);
        connector.setPort(httpPort);
        connector.setRedirectPort(httpsPort);
        return connector;
    }
}
