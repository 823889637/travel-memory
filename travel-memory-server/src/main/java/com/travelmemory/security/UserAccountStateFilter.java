package com.travelmemory.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.common.Result;
import com.travelmemory.exception.BusinessException;
import com.travelmemory.service.AppUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class UserAccountStateFilter extends OncePerRequestFilter {
    private final AppUserService users;
    private final ObjectMapper mapper;

    public UserAccountStateFilter(AppUserService users, ObjectMapper mapper) {
        this.users = users;
        this.mapper = mapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            try {
                UserPrincipal refreshed = users.currentPrincipal(principal.getId());
                if (!java.util.Objects.equals(principal.getPassword(), refreshed.getPassword())) {
                    throw new BusinessException(401, "Authentication is required");
                }
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(refreshed, null, refreshed.getAuthorities()));
            } catch (BusinessException exception) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json;charset=UTF-8");
                mapper.writeValue(response.getOutputStream(), Result.fail(401, "Authentication is required"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
