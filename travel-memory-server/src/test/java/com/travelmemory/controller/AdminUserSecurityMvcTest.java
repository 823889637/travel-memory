package com.travelmemory.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.travelmemory.config.SecurityConfig;
import com.travelmemory.entity.AppUser;
import com.travelmemory.mapper.AppUserMapper;
import com.travelmemory.mapper.MemoryPhotoMapper;
import com.travelmemory.mapper.TravelMemoryMapper;
import com.travelmemory.mapper.TravelTripMapper;
import com.travelmemory.security.CurrentUser;
import com.travelmemory.security.MustChangePasswordFilter;
import com.travelmemory.security.UserAccountStateFilter;
import com.travelmemory.security.UserPrincipal;
import com.travelmemory.service.AppUserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminUserController.class)
@Import({SecurityConfig.class, UserAccountStateFilter.class, MustChangePasswordFilter.class})
class AdminUserSecurityMvcTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private AppUserService users;

    @MockBean
    private CurrentUser currentUser;

    @MockBean
    private AppUserMapper appUserMapper;

    @MockBean
    private TravelTripMapper travelTripMapper;

    @MockBean
    private TravelMemoryMapper travelMemoryMapper;

    @MockBean
    private MemoryPhotoMapper memoryPhotoMapper;

    @Test
    void ordinaryUserCannotAccessAccountManagement() throws Exception {
        UserPrincipal user = principal(2L, "USER");
        when(users.currentPrincipal(2L)).thenReturn(user);

        mvc.perform(get("/api/admin/users").session(sessionFor(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanReadUsersButWritesStillRequireCsrf() throws Exception {
        UserPrincipal admin = principal(1L, "ADMIN");
        when(users.currentPrincipal(1L)).thenReturn(admin);
        when(users.listUsers()).thenReturn(List.of());

        mvc.perform(get("/api/admin/users").session(sessionFor(admin)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/users").session(sessionFor(admin))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession sessionFor(UserPrincipal principal) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }

    private UserPrincipal principal(Long id, String role) {
        AppUser user = new AppUser();
        user.setId(id); user.setUsername("user" + id); user.setDisplayName("User"); user.setPasswordHash("hash");
        user.setRole(role); user.setEnabled(true); user.setMustChangePassword(false);
        return new UserPrincipal(user);
    }
}
