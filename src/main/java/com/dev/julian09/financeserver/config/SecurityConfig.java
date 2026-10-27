package com.dev.julian09.financeserver.config;

import com.dev.julian09.financeserver.shared.error.ApiException;
import com.dev.julian09.financeserver.shared.error.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Stateless API: public auth and ops routes, everything else needs a valid access token. */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http, AuthenticationEntryPoint problemEntryPoint) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/google", "/api/v1/auth/refresh",
                                "/api/v1/auth/password-reset", "/api/v1/auth/password-reset/confirm").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/v3/api-docs/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()).authenticationEntryPoint(problemEntryPoint))
                .exceptionHandling(e -> e.authenticationEntryPoint(problemEntryPoint))
                .build();
    }

    // Missing, expired or forged token → the same RFC 9457 401 the controllers produce
    @Bean
    AuthenticationEntryPoint problemEntryPoint(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return (request, response, ex) -> resolver.resolveException(request, response, null,
                new ApiException(ErrorCode.AUTH_INVALID, "Inicia sesión de nuevo."));
    }

    /** RNF-11: BCrypt with cost 12. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
