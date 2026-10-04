package com.telusko.apigateway;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiGatewayConfigContractTest {

    @Test
    void applicationYml_shouldContainConfiguredRoutesAndCors() throws IOException {
        String yml = readClasspathResource("application.yml");

        assertTrue(yml.contains("port: 8060"));
        assertTrue(yml.contains("Path=/tasks/**"));
        assertTrue(yml.contains("Path=/api/candidates/**"));
        assertTrue(yml.contains("Path=/api/v1/**"));
        assertTrue(yml.contains("Path=/api/notifications/**"));
        assertTrue(yml.contains("allowedOrigins: \"http://localhost:4200\""));
    }

    /**
     * The event service exposes four resources. Without a route for each of them
     * the browser has to reach port 8089 directly, which only works on the
     * developer machine, because nothing behind nginx or an Ingress listens
     * there.
     *
     * A missing route fails silently: the frontend builds, the image is valid,
     * and the event pages simply return 404.
     */
    @Test
    void applicationYml_shouldRouteEveryEventServiceResource() throws IOException {
        String yml = readClasspathResource("application.yml");

        assertTrue(yml.contains("Path=/api/events/**"),
                "missing route for /api/events/**");
        assertTrue(yml.contains("Path=/api/venues/**"),
                "missing route for /api/venues/**");
        assertTrue(yml.contains("Path=/api/event-types/**"),
                "missing route for /api/event-types/**");
        assertTrue(yml.contains("Path=/api/contacts/**"),
                "missing route for /api/contacts/**");

        // Each of the four must resolve through Eureka to the event service,
        // not to a hardcoded host.
        long eventRoutes = yml.lines()
                .filter(line -> line.contains("uri: lb://event-service"))
                .count();
        assertTrue(eventRoutes >= 4,
                "expected at least 4 routes to lb://event-service, found " + eventRoutes);
    }

    /**
     * Every route must resolve through Eureka. A literal host such as
     * http://localhost:8089 would work on a developer machine and fail
     * everywhere else, and it fails silently at startup because the gateway
     * accepts the configuration.
     */
    @Test
    void applicationYml_shouldNotRouteToAHardcodedHost() throws IOException {
        String yml = readClasspathResource("application.yml");

        yml.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("uri:"))
                .forEach(line -> assertTrue(line.startsWith("uri: lb://"),
                        "route must resolve through Eureka but found: " + line));
    }

    private String readClasspathResource(String resourcePath) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}