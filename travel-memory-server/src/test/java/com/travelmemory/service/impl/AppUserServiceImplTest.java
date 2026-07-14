package com.travelmemory.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.travelmemory.dto.RegisterRequest;
import com.travelmemory.entity.AppUser;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.mapper.AppUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AppUserServiceImplTest {

    @Test
    void firstRegistrationActivatesReservedAdministrator() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AppUser reserved = reservedAdministrator();
        when(mapper.selectByIdForUpdate(1L)).thenReturn(reserved);
        when(mapper.selectOne(any())).thenReturn(null);
        when(encoder.encode("password-at-least-12")).thenReturn("bcrypt-hash");
        AppUserServiceImpl service = service(mapper, encoder, true, "invite-code");

        var principal = service.register(request("first.admin", "First Admin", "invite-code"));

        assertEquals("ADMIN", principal.getRole());
        assertEquals("first.admin", reserved.getUsername());
        assertFalse(principal.mustChangePassword());
        verify(mapper).updateById(reserved);
    }

    @Test
    void laterRegistrationCreatesRegularUser() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        AppUser activeAdministrator = reservedAdministrator();
        activeAdministrator.setUsername("admin");
        activeAdministrator.setEnabled(true);
        activeAdministrator.setPasswordHash("bcrypt-hash");
        when(mapper.selectByIdForUpdate(1L)).thenReturn(activeAdministrator);
        when(mapper.selectOne(any())).thenReturn(null);
        when(encoder.encode("password-at-least-12")).thenReturn("new-hash");
        AppUserServiceImpl service = service(mapper, encoder, true, "invite-code");

        var principal = service.register(request("travel.user", "Travel User", "invite-code"));

        assertEquals("USER", principal.getRole());
        assertFalse(principal.mustChangePassword());
        verify(mapper).insert(any(AppUser.class));
    }

    @Test
    void rejectsRegistrationWhenDisabledOrInviteCodeIsWrong() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        BusinessException closed = assertThrows(BusinessException.class,
                () -> service(mapper, encoder, false, "invite-code").register(request("user.one", "User", "invite-code")));
        assertEquals(403, closed.getCode());
        assertThrows(BusinessException.class,
                () -> service(mapper, encoder, true, "invite-code").register(request("user.one", "User", "wrong-code")));
    }

    @Test
    void registrationStatusFailsClosedWithoutAConfiguredInviteCode() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        assertFalse(service(mapper, encoder, false, "invite-code").registrationStatus().enabled());
        assertFalse(service(mapper, encoder, true, "").registrationStatus().enabled());

        BusinessException missingInvite = assertThrows(BusinessException.class,
                () -> service(mapper, encoder, true, "").register(request("user.one", "User", "invite-code")));
        assertEquals(403, missingInvite.getCode());
    }

    @Test
    void rejectsAnExistingSessionForADisabledUser() {
        AppUserMapper mapper = mock(AppUserMapper.class);
        AppUser disabled = reservedAdministrator();
        disabled.setId(8L);
        disabled.setUsername("disabled.user");
        when(mapper.selectById(8L)).thenReturn(disabled);

        assertThrows(BusinessException.class, () -> service(mapper, mock(PasswordEncoder.class), false, "").currentPrincipal(8L));
    }

    private AppUserServiceImpl service(AppUserMapper mapper, PasswordEncoder encoder, boolean enabled, String inviteCode) {
        AppUserServiceImpl service = new AppUserServiceImpl(mapper, encoder);
        ReflectionTestUtils.setField(service, "registrationEnabled", enabled);
        ReflectionTestUtils.setField(service, "registrationInviteCode", inviteCode);
        return service;
    }

    private RegisterRequest request(String username, String displayName, String invitationCode) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setDisplayName(displayName);
        request.setPassword("password-at-least-12");
        request.setInvitationCode(invitationCode);
        return request;
    }

    private AppUser reservedAdministrator() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setDisplayName("Initial administrator");
        user.setPasswordHash("!");
        user.setRole("ADMIN");
        user.setEnabled(false);
        user.setMustChangePassword(true);
        return user;
    }
}
