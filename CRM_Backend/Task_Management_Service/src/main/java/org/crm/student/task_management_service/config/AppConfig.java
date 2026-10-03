package org.crm.student.task_management_service.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * The client used to reach the other services.
     *
     * A bare new RestTemplate() has no timeouts, so a service that stops
     * answering blocks a Tomcat worker thread until the connection times out at
     * the OS level, which can be minutes. Under load that exhausts the request
     * pool and the service stops serving requests it could have answered.
     *
     * The timeouts are configurable so a slow dependency can be tolerated within
     * a known bound rather than indefinitely.
     */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${clients.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${clients.read-timeout-ms:10000}") int readTimeoutMs) {

        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
