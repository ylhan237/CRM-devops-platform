package org.crm.student.task_management_service.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class UserClient {

    private final RestTemplate restTemplate;

    /**
     * Base URL of the API gateway, which fronts the auth service. Defaults to
     * localhost for a local run; override it with the compose service name or
     * the Kubernetes Service name so the call leaves the container.
     */
    @Value("${clients.api-gateway.url:http://localhost:8060}")
    private String apiGatewayUrl;

    public UserClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean validateUser(String username, String authorization) {
        String url = apiGatewayUrl + "/api/v1/auth/" + username + "/exists";
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
            ResponseEntity<Boolean> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Boolean.class);
            return Boolean.TRUE.equals(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to validate username: " + username, e);
        }
    }
}
