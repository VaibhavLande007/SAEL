package com.sael.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class AdminSecretAuthFilter extends OncePerRequestFilter {

    @Value("${admin.secret}")
    private String expectedSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String secret = req.getHeader("x-admin-secret");
        if (secret == null) {
            secret = req.getParameter("adminSecret");
        }

        if (secret != null) {
            if (expectedSecret != null && !expectedSecret.isEmpty() && secret.equals(expectedSecret)) {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"));
                var auth = new UsernamePasswordAuthenticationToken("SYSTEM_ADMIN", null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"Invalid admin secret\"},\"message\":\"Invalid admin secret\",\"statusCode\":401}");
                return;
            }
        }

        chain.doFilter(req, res);
    }
}
