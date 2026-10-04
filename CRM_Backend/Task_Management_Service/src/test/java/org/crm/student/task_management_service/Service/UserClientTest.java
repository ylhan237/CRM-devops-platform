package org.crm.student.task_management_service.Service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserClientTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final UserClient userClient = new UserClient(restTemplate);

    @Test
    void validateUserForwardsTheAuthenticatedCallersToken() {
        String url = "http://localhost:8060/api/v1/auth/Ada Lovelace/exists";
        ReflectionTestUtils.setField(userClient, "apiGatewayUrl", "http://localhost:8060");
        when(restTemplate.exchange(
                eq(url),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Boolean.class)))
                .thenReturn(ResponseEntity.ok(true));

        assertTrue(userClient.validateUser("Ada Lovelace", "Bearer admin-token"));

        verify(restTemplate).exchange(
                eq(url),
                eq(HttpMethod.GET),
                org.mockito.ArgumentMatchers.argThat(request ->
                        "Bearer admin-token".equals(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))),
                eq(Boolean.class));
    }
}
