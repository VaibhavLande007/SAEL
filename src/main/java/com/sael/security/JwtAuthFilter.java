package com.sael.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component @RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    @Override
    protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
        String header=req.getHeader("Authorization");
        String token=(StringUtils.hasText(header)&&header.startsWith("Bearer "))?header.substring(7):null;
        if(token!=null&&jwtService.isValid(token)){
            try{
                var userId=jwtService.extractUserId(token);
                var tenantId=jwtService.extractTenantId(token);
                var roles=jwtService.extractRoles(token);
                TenantContext.set(tenantId,userId);
                var authorities=roles.stream().map(r->new SimpleGrantedAuthority("ROLE_"+r)).toList();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(userId,null,authorities));
            }catch(Exception e){SecurityContextHolder.clearContext();}
        }
        try{chain.doFilter(req,res);}finally{TenantContext.clear();}
    }
}
