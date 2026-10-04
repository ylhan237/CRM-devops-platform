package com.crm.authservice.auth_api1.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static jakarta.persistence.FetchType.EAGER;


@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "_user")
@EntityListeners(AuditingEntityListener.class)
public class User implements UserDetails, Principal {

    @Id
    @GeneratedValue
    private Integer id;
    private String firstname;
    private String lastname;
    private LocalDate dateOfBirth;
    @Column(unique = true)
    private String email;

    /**
     * BCrypt hash of the password.
     *
     * WRITE_ONLY, not IGNORE. The entity is used as a request body by
     * PUT /auth/update-user/{id}, so the value has to stay readable from JSON,
     * but it must never be written back: this entity is returned as-is by
     * /all-users, /user/{id}, /user-info and /email/{email}, and the endpoints
     * do not build a dedicated response type.
     *
     * @JsonIgnore would also silence the setter, which would silently break
     * every password change.
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @OneToOne(mappedBy = "user")
    private ProfilePhoto profilePhoto;

    private boolean isTemporaryPassword;
    private boolean accountLocked;
    private boolean enabled;
    @ManyToMany(fetch = EAGER)
    private List<Role> roles;

    /**
     * Self reference. Serializing it recurses, since each nested User exposes
     * another one.
     */
    @ManyToOne(cascade = CascadeType.REMOVE)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private User user;



    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @LastModifiedDate
    @Column(insertable = false)
    private LocalDateTime lastModifiedDate;

    /**
     * Single-use token emailed to reset a forgotten password. Exposing it
     * would hand an attacker a working credential reset for any account.
     */
    @JsonIgnore
    @Column(name = "password_reset_token")
    private String passwordResetToken;

    /** Server-side bookkeeping, never part of the API contract. */
    @JsonIgnore
    @Column(name = "token_expiration_time")
    private LocalDateTime tokenExpirationTime;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.roles
                .stream()
                .map(r -> new SimpleGrantedAuthority(r.getName()))
                .collect(Collectors.toList());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public String fullName() {
        return getFirstname() + " " + getLastname();
    }

    @Override
    public String getName() {
        return email;
    }

    public String getFullName() {
        return firstname + " " + lastname;
    }
}
