package com.travelmemory.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.entity.AppUser;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.service.AppUserService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UserAccountStateFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void disabledUserCannotContinueUsingAnExistingSession() throws Exception {
        AppUserService users = mock(AppUserService.class);
        when(users.currentPrincipal(2L)).thenThrow(new BusinessException(401, "Authentication is required"));
        UserAccountStateFilter filter = new UserAccountStateFilter(users, new ObjectMapper());
        UserPrincipal principal = principal(2L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/trips"), response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passwordChangeInvalidatesEverySessionUsingTheOldPasswordHash() throws Exception {
        AppUserService users = mock(AppUserService.class);
        UserPrincipal original = principal(2L);
        UserPrincipal refreshed = principal(2L);
        AppUser changed = new AppUser();
        changed.setId(2L); changed.setUsername("user2"); changed.setDisplayName("User"); changed.setPasswordHash("new-hash");
        changed.setRole("USER"); changed.setEnabled(true); changed.setMustChangePassword(false);
        refreshed = new UserPrincipal(changed);
        when(users.currentPrincipal(2L)).thenReturn(refreshed);
        UserAccountStateFilter filter = new UserAccountStateFilter(users, new ObjectMapper());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(original, null, original.getAuthorities()));
        FilterChain chain = mock(FilterChain.class);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/trips"), response, chain);

        assertEquals(401, response.getStatus());
        verify(chain, never()).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private UserPrincipal principal(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setDisplayName("User");
        user.setPasswordHash("hash");
        user.setRole("USER");
        user.setEnabled(true);
        user.setMustChangePassword(false);
        return new UserPrincipal(user);
    }
}
