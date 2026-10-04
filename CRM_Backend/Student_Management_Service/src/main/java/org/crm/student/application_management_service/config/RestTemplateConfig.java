package org.crm.student.application_management_service.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    /**
     * The client used to reach the Notification service.
     *
     * A bare new RestTemplate() has no timeouts, so an unreachable Notification
     * service blocks the calling thread until the OS gives up, which can be
     * minutes. Timeouts keep the failure bounded and configurable.
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
