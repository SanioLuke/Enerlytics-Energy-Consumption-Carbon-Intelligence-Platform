package com.enerlytics.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

/**
 * Authenticated user principal carrying tenant and authority information.
 *
 * <p>The authority list is scoped to the current request's organization.
 * Platform administrators carry all permissions for any organization.</p>
 */
public class UserPrincipal implements UserDetails {

    private final UUID userId;
    private final String email;
    private final boolean enabled;
    private final UUID currentOrganizationId;
    private final boolean platformAdmin;
    private final Collection<GrantedAuthority> authorities;

    public UserPrincipal(UUID userId, String email, boolean enabled,
                         UUID currentOrganizationId, boolean platformAdmin,
                         Collection<String> permissions) {
        this.userId = userId;
        this.email = email;
        this.enabled = enabled;
        this.currentOrganizationId = currentOrganizationId;
        this.platformAdmin = platformAdmin;
        List<GrantedAuthority> auths = new ArrayList<>();
        for (String permission : new HashSet<>(permissions)) {
            auths.add(new SimpleGrantedAuthority(permission));
        }
        this.authorities = Collections.unmodifiableList(auths);
    }

    public UUID getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public UUID getCurrentOrganizationId() {
        return currentOrganizationId;
    }

    public boolean isPlatformAdmin() {
        return platformAdmin;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return null;
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
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
