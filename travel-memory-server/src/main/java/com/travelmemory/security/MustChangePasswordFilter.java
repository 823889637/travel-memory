package com.travelmemory.security;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travelmemory.common.Result;
import jakarta.servlet.FilterChain;import jakarta.servlet.ServletException;import jakarta.servlet.http.HttpServletRequest;import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;import org.springframework.security.core.context.SecurityContextHolder;import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
@Component public class MustChangePasswordFilter extends OncePerRequestFilter {
 private final ObjectMapper mapper; public MustChangePasswordFilter(ObjectMapper mapper){this.mapper=mapper;}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws ServletException,IOException{
  Authentication auth=SecurityContextHolder.getContext().getAuthentication();String path=req.getRequestURI();
  boolean allowed=path.equals("/api/auth/csrf")||path.equals("/api/auth/me")||path.equals("/api/auth/password")||path.equals("/api/auth/logout");
  if(auth!=null&&auth.getPrincipal() instanceof UserPrincipal user&&user.mustChangePassword()&&!allowed){res.setStatus(403);res.setContentType("application/json;charset=UTF-8");mapper.writeValue(res.getOutputStream(),Result.fail(403,"请先修改初始密码"));return;}
  chain.doFilter(req,res);
 }
}
