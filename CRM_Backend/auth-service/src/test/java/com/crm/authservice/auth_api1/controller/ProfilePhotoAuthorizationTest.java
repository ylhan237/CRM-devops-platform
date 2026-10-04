package com.crm.authservice.auth_api1.controller;

import com.crm.authservice.auth_api1.Service.AuthenticationService;
import com.crm.authservice.auth_api1.Service.ProfilePhotoService;
import com.crm.authservice.auth_api1.Service.RoleService;
import com.crm.authservice.auth_api1.Repository.UserRepository;
import com.crm.authservice.auth_api1.filters.JwtService;
import com.crm.authservice.auth_api1.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The profile photo endpoints took the owner identifier from the URL and
 * checked nothing else, so any request could name any user:
 * POST /auth/{userId}/upload, PUT /auth/{userId}/update and
 * DELETE /auth/{userId}/delete wrote to someone else's row without a credential.
 *
 * The two lookup endpoints below answered the same questions to anyone:
 * whether an account exists for a name, and which email it belongs to. That is
 * an enumeration oracle over the user base, and the email is the identifier the
 * rest of the system logs in with.
 *
 * Each case asserts on both halves: the status, and that the service was never
 * reached. A test that only checked the status would still pass if the
 * controller wrote the photo and then returned 403, which is the failure mode
 * that actually matters here.
 */
class ProfilePhotoAuthorizationTest {

    private static final int CALLER_ID = 7;
    private static final int SOMEBODY_ELSE_ID = 42;

    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ProfilePhotoService profilePhotoService = mock(ProfilePhotoService.class);
    private final RoleService roleService = mock(RoleService.class);

    private AuthenticationController controller;

    private static User user(int id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        return user;
    }

    @BeforeEach
    void setUp() throws Exception {
        controller = new AuthenticationController(authenticationService, jwtService);
        // The controller declares these three as @Autowired fields rather than
        // constructor parameters, so they have to be injected the same way.
        ReflectionTestUtils.setField(controller, "roleService", roleService);
        ReflectionTestUtils.setField(controller, "userRepository", userRepository);
        ReflectionTestUtils.setField(controller, "profilePhotoService", profilePhotoService);
    }

    /** A token that resolves to a regular, non administrator user. */
    private String tokenOf(int id) {
        given(jwtService.extractUsername("token")).willReturn("user" + id + "@example.com");
        given(jwtService.extractUserRole("token")).willReturn("USER");
        given(userRepository.findByEmail("user" + id + "@example.com"))
                .willReturn(Optional.of(user(id, "user" + id + "@example.com")));
        return "Bearer token";
    }

    private static MultipartFile photo() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});
    }

    @Nested
    class Upload {

        @Test
        @DisplayName("without a token the request is refused and nothing is written")
        void withoutAToken() throws Exception {
            ResponseEntity<?> response = controller.uploadProfilePhoto(null, (long) CALLER_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(profilePhotoService, never()).saveProfilePhoto(any(), anyLong());
        }

        @Test
        @DisplayName("a token that does not resolve to a user is refused")
        void anUnresolvableToken() throws Exception {
            given(jwtService.extractUsername("expired")).willThrow(new IllegalArgumentException("expired"));

            ResponseEntity<?> response =
                    controller.uploadProfilePhoto("Bearer expired", (long) CALLER_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(profilePhotoService, never()).saveProfilePhoto(any(), anyLong());
        }

        @Test
        @DisplayName("naming somebody else's id is forbidden and nothing is written")
        void namingSomebodyElse() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response =
                    controller.uploadProfilePhoto(header, (long) SOMEBODY_ELSE_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(profilePhotoService, never()).saveProfilePhoto(any(), anyLong());
        }

        @Test
        @DisplayName("uploading one's own photo is allowed")
        void ownPhoto() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response = controller.uploadProfilePhoto(header, (long) CALLER_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(profilePhotoService).saveProfilePhoto(any(), anyLong());
        }

        @Test
        @DisplayName("an administrator may upload for anybody")
        void anAdministratorMayActForAnybody() throws Exception {
            given(jwtService.extractUsername("token")).willReturn("root@example.com");
            given(jwtService.extractUserRole("token")).willReturn("ADMIN");
            given(userRepository.findByEmail("root@example.com"))
                    .willReturn(Optional.of(user(CALLER_ID, "root@example.com")));

            ResponseEntity<?> response =
                    controller.uploadProfilePhoto("Bearer token", (long) SOMEBODY_ELSE_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(profilePhotoService).saveProfilePhoto(any(), anyLong());
        }
    }

    @Nested
    class Update {

        @Test
        @DisplayName("naming somebody else's id is forbidden and nothing is written")
        void namingSomebodyElse() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response =
                    controller.updateProfilePhoto(header, (long) SOMEBODY_ELSE_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(profilePhotoService, never()).updateProfilePhoto(any(), anyLong());
        }

        @Test
        @DisplayName("updating one's own photo is allowed")
        void ownPhoto() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response = controller.updateProfilePhoto(header, (long) CALLER_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(profilePhotoService).updateProfilePhoto(any(), anyLong());
        }

        @Test
        @DisplayName("without a token the request is refused")
        void withoutAToken() throws Exception {
            ResponseEntity<?> response = controller.updateProfilePhoto(null, (long) CALLER_ID, photo());

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(profilePhotoService, never()).updateProfilePhoto(any(), anyLong());
        }
    }

    @Nested
    class Delete {

        @Test
        @DisplayName("naming somebody else's id is forbidden and nothing is deleted")
        void namingSomebodyElse() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response = controller.deleteProfilePhoto(header, (long) SOMEBODY_ELSE_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(profilePhotoService, never()).deleteProfilePhoto(anyLong());
        }

        @Test
        @DisplayName("deleting one's own photo is allowed")
        void ownPhoto() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response = controller.deleteProfilePhoto(header, (long) CALLER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(profilePhotoService).deleteProfilePhoto((long) CALLER_ID);
        }

        @Test
        @DisplayName("without a token the request is refused")
        void withoutAToken() throws Exception {
            ResponseEntity<?> response = controller.deleteProfilePhoto(null, (long) CALLER_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(profilePhotoService, never()).deleteProfilePhoto(anyLong());
        }
    }

    @Nested
    class Enumeration {

        @Test
        @DisplayName("the email lookup refuses an anonymous caller and answers nothing")
        void emailIsNotReadableAnonymously() throws Exception {
            ResponseEntity<?> response = controller.getEmailByFullName(null, "Ada Lovelace");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(authenticationService, never()).getEmailByFullName(any());
        }

        @Test
        @DisplayName("the email lookup refuses a non administrator and answers nothing")
        void emailIsNotReadableByARegularUser() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response = controller.getEmailByFullName(header, "Ada Lovelace");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(authenticationService, never()).getEmailByFullName(any());
        }

        @Test
        @DisplayName("the email lookup answers an administrator")
        void emailIsReadableByAnAdministrator() throws Exception {
            given(jwtService.extractUsername("token")).willReturn("root@example.com");
            given(jwtService.extractUserRole("token")).willReturn("ADMIN");
            given(userRepository.findByEmail("root@example.com"))
                    .willReturn(Optional.of(user(CALLER_ID, "root@example.com")));
            given(authenticationService.getEmailByFullName("Ada Lovelace"))
                    .willReturn("ada@example.com");

            ResponseEntity<?> response =
                    controller.getEmailByFullName("Bearer token", "Ada Lovelace");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo("ada@example.com");
        }

        @Test
        @DisplayName("the existence check refuses an anonymous caller and answers nothing")
        void existenceIsNotReadableAnonymously() throws Exception {
            ResponseEntity<?> response = controller.doesUserExist(null, "Ada Lovelace");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(authenticationService, never()).doesUserExist(any());
        }

        @Test
        @DisplayName("the existence check refuses a non administrator and answers nothing")
        void existenceIsNotReadableByARegularUser() throws Exception {
            String header = tokenOf(CALLER_ID);

            ResponseEntity<?> response = controller.doesUserExist(header, "Ada Lovelace");

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            verify(authenticationService, never()).doesUserExist(any());
        }
    }
}
