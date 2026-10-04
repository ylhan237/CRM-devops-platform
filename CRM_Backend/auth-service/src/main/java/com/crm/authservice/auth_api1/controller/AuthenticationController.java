package com.crm.authservice.auth_api1.controller;


import com.crm.authservice.auth_api1.Repository.ProfilePhotoRepository;
import com.crm.authservice.auth_api1.Repository.UserRepository;
import com.crm.authservice.auth_api1.Request.AuthenticationRequest;
import com.crm.authservice.auth_api1.Request.RegistrationRequest;
import com.crm.authservice.auth_api1.Response.AuthenticationResponse;
import com.crm.authservice.auth_api1.Service.AuthenticationService;
import com.crm.authservice.auth_api1.Service.ProfilePhotoService;
import com.crm.authservice.auth_api1.Service.RoleService;
import com.crm.authservice.auth_api1.filters.JwtService;
import com.crm.authservice.auth_api1.models.ProfilePhoto;
import com.crm.authservice.auth_api1.models.Role;
import com.crm.authservice.auth_api1.models.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
// @CrossOrigin(origins = "http://localhost:4200")
public class AuthenticationController {

    /**
     * The other endpoints in this file declare a local logger in every method.
     * A constant is added rather than eight more local declarations, and it is
     * named LOGGER so it cannot be confused with, or silently shadowed by, those
     * locals.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationController.class);

    private final AuthenticationService service;


    private final JwtService jwtService;

    
    @Autowired
    private RoleService roleService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfilePhotoService profilePhotoService;




    // Upload user profile image


    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<?> register(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                      @RequestBody @Valid RegistrationRequest request) {
        Logger logger = LoggerFactory.getLogger(this.getClass());

        // Check if the Authorization header is present and valid
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("error", "Unauthorized");
            responseBody.put("message", "Authorization token is required.");

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(responseBody);
        }

        String jwt = authHeader.substring(7); // Extract JWT token
        String userRole = jwtService.extractUserRole(jwt); // You should have a method to extract user role

        // Check if the user has admin role
        if (userRole == null || !userRole.equals("ADMIN")) {
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("error", "Forbidden");
            responseBody.put("message", "You do not have permission to register users.");

            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(responseBody);
        }

        try {
            service.register(request);
            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("status", "success");
            successResponse.put("message", "Registration successful. A confirmation email has been sent.");

            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(successResponse);
        } catch (MessagingException e) {
            logger.error("Failed to send registration email for request: {}", request, e);

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Registration failed. Unable to send confirmation email.");

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(errorResponse);
        } catch (Exception e) {
            logger.error("Unexpected error occurred during registration for request: {}", request, e);

            Map<String, Object> badRequestResponse = new HashMap<>();
            badRequestResponse.put("status", "error");
            badRequestResponse.put("message", "Registration failed due to invalid input.");

            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .body(badRequestResponse);
        }

    }
//    @PostMapping("/register")
//    @ResponseStatus(HttpStatus.ACCEPTED)
//    public ResponseEntity<?> register(
//            @RequestBody @Valid RegistrationRequest request
//    ) throws MessagingException {
//        service.register(request);
//        return ResponseEntity.accepted().build();
//    }
    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request
    ) {
        return ResponseEntity.ok(service.authenticate(request));
    }
    //    @GetMapping("/activate-account")
//    public void confirm(
//            @RequestParam String token
//    ) throws MessagingException {
//        service.activateAccount(token);
//    }
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(
            @RequestParam String email,
            @RequestParam String newPassword
    ) throws MessagingException {
        service.changePassword(email, newPassword);
        return ResponseEntity.ok("Password changed successfully");
    }
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestParam String email) {
        try {
            service.sendPasswordResetToken(email);
            return ResponseEntity.ok("Password reset email sent successfully.");
        } catch (MessagingException e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to send password reset email.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        } catch (UsernameNotFoundException e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "User with the given email not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(response);
        }
    }
    @PostMapping("/verify-token")
    public ResponseEntity<?> verifyToken(@RequestParam String token) {
        boolean isTokenValid = service.isTokenValid(token);
        if (isTokenValid) {
            return ResponseEntity.ok("Token is valid. Please proceed to set your new password.");
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid or expired token.");
        }
    }


    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestParam String token,
            @RequestParam String newPassword
    ) {
        service.resetPasswordWithToken(token, newPassword);
        return ResponseEntity.ok("Password reset successfully.");
    }

    // ==============================================
    // NEWLY ADDED USER MANAGEMENT FUNCTIONALITY
    // ==============================================

    // Update User by ID
    @PutMapping("/update-user/{id}")
    public ResponseEntity<?> updateUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Integer id,
            @RequestBody @Valid User updatedUserDetails
    ) {
        Logger logger = LoggerFactory.getLogger(this.getClass());

        // Check if the Authorization header is present and valid
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization token is required.");
        }

        String jwt = authHeader.substring(7); // Extract JWT token
        String userRole = jwtService.extractUserRole(jwt); // You should have a method to extract user role

        // Check if the user has admin role
        if (userRole == null || !userRole.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You do not have permission to update users.");
        }

        try {
            User updatedUser = service.updateUser(id, updatedUserDetails);
            logger.info("User updated successfully: {}", updatedUser);
            return ResponseEntity.ok(updatedUser);
        } catch (UsernameNotFoundException e) {
            logger.error("User not found with ID: {}", id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User not found with ID: " + id);
        } catch (Exception e) {
            logger.error("Failed to update user with ID: {} due to {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to update user");
    }
    }

    @DeleteMapping("/delete-user/{id}")
    public ResponseEntity<Object> deleteUser(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                             @PathVariable Integer id) {
        Logger logger = LoggerFactory.getLogger(this.getClass());

        // Check for Authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Authorization token is missing or invalid for delete user request.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authorization token is required.");
        }

        String jwt = authHeader.substring(7);
        String userRole;
        try {
            userRole = jwtService.extractUserRole(jwt);
        } catch (Exception e) {
            logger.error("Failed to extract user role from JWT: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid authorization token.");
        }

        // Check if user has ADMIN role
        if (userRole == null || !userRole.equals("ADMIN")) {
            logger.warn("User with role {} attempted to delete user with ID {} without permission.", userRole, id);
            Map<String, String> response = new HashMap<>();
            response.put("error", "You do not have permission to delete users.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Attempt to delete user
        try {
            service.deleteUser(id);
            logger.info("User with ID {} deleted successfully.", id);
            return ResponseEntity.noContent().build();
        } catch (UsernameNotFoundException e) {
            logger.warn("User not found with ID: {}", id);
            Map<String, String> response = new HashMap<>();
            response.put("error", "User not found with ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Failed to delete user with ID: {}", id, e);
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to delete user.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }


    // Get All Users
    @GetMapping("/all-users")
    public ResponseEntity<List<User>> getAllUsers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Logger logger = LoggerFactory.getLogger(this.getClass());

        // Check for Authorization header
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            logger.warn("Authorization token is missing or invalid for get all users request.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String jwt = authHeader.substring(7);
        String userRole;
        try {
            userRole = jwtService.extractUserRole(jwt);
        } catch (Exception e) {
            logger.error("Failed to extract user role from JWT: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Check if user has ADMIN role
        if (userRole == null || !userRole.equals("ADMIN")) {
            logger.warn("User with role '{}' attempted to access user list without permission.", userRole);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
        }

        // Retrieve all users
        List<User> users;
        try {
            users = service.getAllUsers(); // Assuming service.getAllUsers() returns List<User>
            logger.info("Successfully retrieved list of users.");
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            logger.error("Failed to retrieve users: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // Get Specific User by ID
    @GetMapping("/user/{id}")
    public ResponseEntity<?> getUserById(@RequestHeader(value = "Authorization", required = false) String authHeader,
                                         @PathVariable Integer id) {
        Logger logger = LoggerFactory.getLogger(this.getClass());

        // Check if the Authorization header is present and valid
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String jwt = authHeader.substring(7); // Extract JWT token
        String userRole = jwtService.extractUserRole(jwt); // You should have a method to extract user role

        // Check if the user has admin role
        if (userRole == null || !userRole.equals("ADMIN")) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "You do not have permission to view user details.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        // Validate ID
        if (id <= 0) {
            logger.warn("Invalid user ID: {}", id);
            Map<String, String> response = new HashMap<>();
            response.put("error", "User ID must be a positive integer.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            User user = service.getUserById(id);
            logger.info("User retrieved successfully: {}", user);
            return ResponseEntity.ok(user);
        } catch (UsernameNotFoundException e) {
            logger.error("User not found with ID: {}", id);
            Map<String, String> response = new HashMap<>();
            response.put("error", "User not found with ID: " + id);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve user with ID: {} due to {}", id, e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to retrieve user");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/user-info")
    public ResponseEntity<?> getUserInfoFromToken(@RequestHeader(value = "Authorization", required = false) String authHeader) {

        Logger logger = LoggerFactory.getLogger(this.getClass());
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        String jwt = authHeader.substring(7); // Extract the JWT token

        try {
            User user = service.getUserInfoFromToken(jwt);
            logger.info("User information retrieved successfully for token.");
            return ResponseEntity.ok(user);
        } catch (UsernameNotFoundException e) {
            logger.error("User not found for provided token.");
            Map<String, String> response = new HashMap<>();
            response.put("error", "User not found.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            logger.error("Failed to retrieve user information due to an error: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "Failed to retrieve user information.");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @PostMapping("/logout")
public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
    Logger logger = LoggerFactory.getLogger(this.getClass());
    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
        Map<String, String> response = new HashMap<>();
        response.put("error", "Authorization token is required.");
        return ResponseEntity.status(401).body(response);
    }

    String jwt = authHeader.substring(7); // Extract the JWT token

    try {
        service.logout(jwt);
        logger.info("User successfully logged out. Token invalidated.");
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logout successful.");
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        logger.error("Logout failed: {}", e.getMessage());
        Map<String, String> response = new HashMap<>();
        response.put("error", "Logout failed.");
        return ResponseEntity.status(500).body(response);
    }
}

    @GetMapping("/roles/{name}")
    public ResponseEntity<?> getRoleByName(
        @PathVariable String name,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Logger logger = LoggerFactory.getLogger(this.getClass());
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(401).body(response);
        }
    
        try {
            Role role = roleService.getRoleByName(name);
            return ResponseEntity.ok(role);
        } catch (RuntimeException e) {
            logger.error("Role not found: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "No such role "+name);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<?> getUserByEmail(
        @PathVariable String email,
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Logger logger = LoggerFactory.getLogger(this.getClass());
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(401).body(response);
        }
        try {
            User user = service.getUserByEmail(email);
            return ResponseEntity.ok(user);
        } catch (RuntimeException e) {
            logger.error("User not found: {}", e.getMessage());
            Map<String, String> response = new HashMap<>();
            response.put("error", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
    @GetMapping("/{candidateId}/profile-photo")
    public ResponseEntity<?> getProfilePhoto(@PathVariable Long candidateId) {
        Optional<ProfilePhoto> profilePhotoOpt = profilePhotoService.getProfilePhotoByCandidateId(candidateId);

        if (profilePhotoOpt.isPresent()) {
            ProfilePhoto profilePhoto = profilePhotoOpt.get();
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG) // Adjust content type if necessary (image/png, etc.)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"profile_photo.jpg\"")
                    .body(profilePhoto.getPhotoData());  // Assuming the photo is stored as a byte array
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Profile photo not found");  // Return 404 with a message
        }
    }


    private static final String BEARER = "Bearer ";

    /**
     * The authenticated caller, or null when the request carries no usable token.
     *
     * <p>This controller authorizes by hand rather than through Spring Security
     * method security, so every protected endpoint has to repeat the same
     * preamble. It is centralised here instead of being copied a fourth time.
     *
     * <p>The subject of the token is the email address, not the numeric
     * identifier: {@code User.getUsername()} returns the email and that is what
     * {@code JwtService} writes into the token. The identifier in the URL is an
     * {@code Integer} in the entity and a {@code Long} in the path, so the two
     * are compared as longs.
     */
    private User authenticatedCaller(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER)) {
            LOGGER.warn("Authorization token is missing or invalid.");
            return null;
        }

        String jwt = authHeader.substring(BEARER.length());
        try {
            String email = jwtService.extractUsername(jwt);
            return userRepository.findByEmail(email).orElse(null);
        } catch (Exception e) {
            // An expired, tampered or otherwise unreadable token lands here.
            LOGGER.error("Failed to identify the caller from the JWT: {}", e.getMessage());
            return null;
        }
    }

    private boolean hasRole(String authHeader, String role) {
        try {
            return role.equals(jwtService.extractUserRole(authHeader.substring(BEARER.length())));
        } catch (Exception e) {
            LOGGER.error("Failed to read the role from the JWT: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Whether the caller is allowed to act on the resource owned by {@code ownerId}.
     *
     * <p>An administrator may act on anyone's, because that is what the role is
     * for. A regular user may only act on their own.
     *
     * <p>This is the check the three profile photo endpoints were missing. The
     * identifier comes from the URL and nothing tied it to the caller, so any
     * request could name any user: uploading, replacing or deleting someone
     * else's photo needed no credential at all.
     */
    private boolean mayActOn(User caller, String authHeader, Long ownerId) {
        if (hasRole(authHeader, "ADMIN")) {
            return true;
        }
        return caller.getId() != null && caller.getId().longValue() == ownerId.longValue();
    }

    @PostMapping("/{userId}/upload")
    public ResponseEntity<?> uploadProfilePhoto(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file) {

        User caller = authenticatedCaller(authHeader);
        if (caller == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!mayActOn(caller, authHeader, userId)) {
            LOGGER.warn("User {} attempted to upload a profile photo for user {}.", caller.getId(), userId);
            Map<String, String> response = new HashMap<>();
            response.put("error", "You may only change your own profile photo.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            // Pass the file and candidateId to the service layer for processing
            profilePhotoService.saveProfilePhoto(file, userId);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Profile photo uploaded successfully.");

            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to upload profile photo: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }


    // Update profile photo (Update operation)
    @PutMapping("/{userId}/update")
    public ResponseEntity<?> updateProfilePhoto(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long userId,
            @RequestParam("file") MultipartFile file) {

        User caller = authenticatedCaller(authHeader);
        if (caller == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!mayActOn(caller, authHeader, userId)) {
            LOGGER.warn("User {} attempted to update the profile photo of user {}.", caller.getId(), userId);
            Map<String, String> response = new HashMap<>();
            response.put("error", "You may only change your own profile photo.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            profilePhotoService.updateProfilePhoto(file, userId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Profile photo updated successfully.");
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to update profile photo: " + e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Candidate not found: " + e.getMessage());
        }
    }

    // Delete profile photo by candidate ID (Delete operation)
    @DeleteMapping("/{userId}/delete")
    public ResponseEntity<?> deleteProfilePhoto(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long userId) {

        User caller = authenticatedCaller(authHeader);
        if (caller == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!mayActOn(caller, authHeader, userId)) {
            LOGGER.warn("User {} attempted to delete the profile photo of user {}.", caller.getId(), userId);
            Map<String, String> response = new HashMap<>();
            response.put("error", "You may only change your own profile photo.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        try {
            profilePhotoService.deleteProfilePhoto(userId);
            Map<String, String> response = new HashMap<>();
            response.put("message", "Profile photo deleted successfully.");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete profile photo: " + e.getMessage());
        }
    }

    /**
     * Administrator only.
     *
     * <p>The pair of endpoints below answered, to anyone who asked, whether an
     * account exists for a given name and what its email address is. That is an
     * enumeration oracle: it turns a public form into a way of listing the user
     * base, one full name at a time, and the email address is the identifier
     * every other part of the system accepts as a login.
     *
     * <p>Neither is called by the frontend, so closing them changes nothing for
     * the application.
     */
    @GetMapping("/{userFullName}/exists")
    public ResponseEntity<?> doesUserExist(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable String userFullName) {

        if (authenticatedCaller(authHeader) == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!hasRole(authHeader, "ADMIN")) {
            LOGGER.warn("A non administrator asked whether an account exists.");
            Map<String, String> response = new HashMap<>();
            response.put("error", "Administrator role required.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        return ResponseEntity.ok(service.doesUserExist(userFullName));
    }

    @GetMapping("/{userfullname}/email")
    public ResponseEntity<?> getEmailByFullName(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable("userfullname") String userFullName) {

        if (authenticatedCaller(authHeader) == null) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Authorization token is required.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
        if (!hasRole(authHeader, "ADMIN")) {
            LOGGER.warn("A non administrator asked for the email address of an account.");
            Map<String, String> response = new HashMap<>();
            response.put("error", "Administrator role required.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
        }

        return ResponseEntity.ok(service.getEmailByFullName(userFullName));
    }

}


