package com.crm.authservice.auth_api1.models;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards what the User entity is allowed to expose over HTTP.
 *
 * The entity is returned directly by /all-users, /user/{id}, /user-info and
 * /email/{email} instead of a dedicated response type, so whatever Jackson
 * serializes becomes part of the API contract. A field left unguarded here is a
 * field handed to every caller who reaches those endpoints.
 */
class UserSerializationTest {

    private ObjectMapper mapper;
    private User user;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        user = User.builder()
                .id(1)
                .firstname("Ada")
                .lastname("Lovelace")
                .email("ada@example.com")
                .password("$2a$10$abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMN")
                .passwordResetToken("reset-token-value-that-must-never-leak")
                .enabled(true)
                .roles(List.of(Role.builder().id(1).name("ADMIN").build()))
                .build();
        user.setTokenExpirationTime(java.time.LocalDateTime.now().plusMinutes(15));
    }

    @Test
    @DisplayName("the BCrypt hash is never serialized")
    void password_isNotWrittenToJson() throws Exception {
        String json = mapper.writeValueAsString(user);

        assertThat(json)
                .doesNotContain("password")
                .doesNotContain("$2a$10$");
        assertThat(json).doesNotContain("abcdefghijklmnopqrstuvwxyz");
    }

    @Test
    @DisplayName("the password reset token is never serialized")
    void passwordResetToken_isNotWrittenToJson() throws Exception {
        String json = mapper.writeValueAsString(user);

        assertThat(json)
                .doesNotContain("passwordResetToken")
                .doesNotContain("reset-token-value-that-must-never-leak");
        assertThat(json).doesNotContain("tokenExpirationTime");
    }

    @Test
    @DisplayName("the self reference does not recurse")
    void selfReference_doesNotSerializeNestedUsers() throws Exception {
        user.setUser(User.builder().id(2).email("parent@example.com").build());

        String json = mapper.writeValueAsString(user);

        assertThat(json).doesNotContain("parent@example.com");
    }

    @Test
    @DisplayName("the fields the API does need are still serialized")
    void usefulFields_areStillSerialized() throws Exception {
        String json = mapper.writeValueAsString(user);

        assertThat(json)
                .contains("\"id\":1")
                .contains("ada@example.com")
                .contains("Lovelace")
                .contains("ADMIN");
    }

    @Test
    @DisplayName("a password sent by the client is still accepted")
    void password_isStillDeserialized() throws Exception {
        // PUT /auth/update-user/{id} takes the entity as its request body, so
        // silencing the setter would break every password change silently. This
        // is why the field is WRITE_ONLY rather than ignored.
        String requestBody = """
                {
                  "id": 1,
                  "email": "ada@example.com",
                  "firstname": "Ada",
                  "lastname": "Lovelace",
                  "password": "new-secret"
                }
                """;

        User parsed = mapper.readValue(requestBody, User.class);

        assertThat(parsed.getPassword()).isEqualTo("new-secret");
    }
}