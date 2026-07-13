package com.travelmemory.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.travelmemory.dto.LoginRequest;
import com.travelmemory.dto.PasswordChangeRequest;
import com.travelmemory.dto.RegisterRequest;
import com.travelmemory.entity.AppUser;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.security.UserPrincipal;
import com.travelmemory.service.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

class AuthControllerSessionTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginAndRegistrationRotateTheSessionId() {
        AppUserService users = mock(AppUserService.class);
        CurrentUser current = mock(CurrentUser.class);
        UserPrincipal user = principal(1L, "USER");
        when(users.authenticate("travel.user", "password-at-least-12")).thenReturn(user);
        when(users.register(org.mockito.ArgumentMatchers.any(RegisterRequest.class))).thenReturn(user);
        AuthController controller = new AuthController(users, current);

        MockHttpServletRequest loginRequest = requestWithSession();
        String loginSessionId = loginRequest.getSession().getId();
        LoginRequest login = new LoginRequest(); login.setUsername("travel.user"); login.setPassword("password-at-least-12");
        controller.login(login, loginRequest);
        assertNotEquals(loginSessionId, loginRequest.getSession().getId());

        MockHttpServletRequest registerRequest = requestWithSession();
        String registerSessionId = registerRequest.getSession().getId();
        RegisterRequest register = new RegisterRequest(); register.setUsername("new.user"); register.setDisplayName("New User");
        register.setPassword("password-at-least-12"); register.setInvitationCode("invite");
        controller.register(register, registerRequest);
        assertNotEquals(registerSessionId, registerRequest.getSession().getId());
    }

    @Test
    void passwordChangeRotatesCurrentSessionAndLogoutInvalidatesIt() {
        AppUserService users = mock(AppUserService.class);
        CurrentUser current = mock(CurrentUser.class);
        UserPrincipal user = principal(1L, "USER");
        when(current.requireId()).thenReturn(1L);
        when(users.currentPrincipal(1L)).thenReturn(user);
        AuthController controller = new AuthController(users, current);
        MockHttpServletRequest request = requestWithSession();
        MockHttpSession session = (MockHttpSession) request.getSession();
        String oldId = session.getId();
        PasswordChangeRequest password = new PasswordChangeRequest();
        password.setCurrentPassword("password-at-least-12"); password.setNewPassword("changed-password-12");

        controller.changePassword(password, request);
        assertNotEquals(oldId, request.getSession().getId());
        controller.logout(request);
        assertTrue(session.isInvalid());
    }

    private MockHttpServletRequest requestWithSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(new MockHttpSession());
        return request;
    }

    private UserPrincipal principal(Long id, String role) {
        AppUser user = new AppUser();
        user.setId(id); user.setUsername("user" + id); user.setDisplayName("User"); user.setPasswordHash("hash");
        user.setRole(role); user.setEnabled(true); user.setMustChangePassword(false);
        return new UserPrincipal(user);
    }
}
