package com.crm.authservice.auth_api1.controller;

import com.crm.authservice.auth_api1.Service.AuthenticationService;
import com.crm.authservice.auth_api1.filters.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserNotFoundHandlingTest {

    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final AuthenticationController controller =
            new AuthenticationController(authenticationService, jwtService);

    @Test
    void missingUserReturnsNotFoundInsteadOfInternalServerError() {
        when(jwtService.extractUserRole("admin-token")).thenReturn("ADMIN");
        when(authenticationService.getUserById(99))
                .thenThrow(new UsernameNotFoundException("User not found with id: 99"));

        ResponseEntity<?> response = controller.getUserById("Bearer admin-token", 99);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
