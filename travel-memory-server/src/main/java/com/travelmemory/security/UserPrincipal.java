package com.travelmemory.security;

import com.travelmemory.entity.AppUser;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final String avatarUrl;
    private final String role;
    private final boolean enabled;
    private final boolean mustChangePassword;

    public UserPrincipal(AppUser user) {
        this.id = user.getId(); this.username = user.getUsername(); this.passwordHash = user.getPasswordHash();
        this.displayName = user.getDisplayName(); this.avatarUrl = user.getAvatarUrl(); this.role = user.getRole();
        this.enabled = Boolean.TRUE.equals(user.getEnabled()); this.mustChangePassword = Boolean.TRUE.equals(user.getMustChangePassword());
    }
    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getRole() { return role; }
    public boolean mustChangePassword() { return mustChangePassword; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return List.of(new SimpleGrantedAuthority("ROLE_" + role)); }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return username; }
    @Override public boolean isEnabled() { return enabled; }
}
