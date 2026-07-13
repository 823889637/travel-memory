package com.travelmemory.config;
import com.travelmemory.security.MustChangePasswordFilter;
import com.travelmemory.security.UserAccountStateFilter;
import com.travelmemory.common.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain filterChain(HttpSecurity http, MustChangePasswordFilter passwordFilter,
        UserAccountStateFilter accountStateFilter, ObjectMapper mapper) throws Exception {
  CookieCsrfTokenRepository csrf=CookieCsrfTokenRepository.withHttpOnlyFalse();csrf.setCookiePath("/");
  http.csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
   .securityContext(c->c.requireExplicitSave(false))
   .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED).sessionFixation(f->f.changeSessionId()))
   .authorizeHttpRequests(a->a.requestMatchers("/api/auth/csrf","/api/auth/login","/api/auth/register","/api/auth/registration-status").permitAll().requestMatchers("/api/admin/**").hasRole("ADMIN").anyRequest().authenticated())
   .addFilterAfter(accountStateFilter, UsernamePasswordAuthenticationFilter.class)
   .addFilterAfter(passwordFilter, UserAccountStateFilter.class)
   .exceptionHandling(e->e.authenticationEntryPoint((req,res,x)->write(res,mapper,401,"Authentication is required")).accessDeniedHandler((req,res,x)->write(res,mapper,403,"Access is denied")))
   .formLogin(f->f.disable()).httpBasic(h->h.disable());
  return http.build();
 }
 private void write(HttpServletResponse response,ObjectMapper mapper,int status,String message)throws java.io.IOException{response.setStatus(status);response.setContentType("application/json;charset=UTF-8");mapper.writeValue(response.getOutputStream(),Result.fail(status,message));}
}
