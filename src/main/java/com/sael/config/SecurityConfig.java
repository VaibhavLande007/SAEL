package com.sael.config;
import com.sael.security.JwtAuthFilter;
import com.sael.security.AdminSecretAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
// Suppress Spring Boot's auto-configured InMemoryUserDetailsManager — we use JWT, not form login
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;
    private final AdminSecretAuthFilter adminSecretAuthFilter;

    /**
     * No-op UserDetailsService — prevents Spring Boot from creating an
     * InMemoryUserDetailsManager that would print "Using generated security password"
     * and intercept authentication. All auth is handled by JwtAuthFilter.
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // JWT-only auth — no UserDetails lookup needed
        return username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
                "UserDetailsService not used — JWT auth only");
        };
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(c -> c.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Disable Spring's default form-login and http-basic — JWT only
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(a -> a
                .requestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/actuator/health",
                    "/api/v1/admin/tenants/bootstrap",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/swagger-ui.html"
                ).permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(adminSecretAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        var c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
        c.setAllowedHeaders(List.of("*"));
        c.setAllowCredentials(true);
        var s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/api/**", c);
        return s;
    }
}
