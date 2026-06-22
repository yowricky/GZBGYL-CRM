package com.gzbgyl.crm.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            JsonAuthenticationEntryPoint entryPoint, JsonAccessDeniedHandler deniedHandler,
            CsrfTokenRepository csrf) throws Exception {
        return http
                .csrf(configurer -> configurer.csrfTokenRepository(csrf))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .anyRequest().authenticated())
                .httpBasic(configurer -> configurer.disable())
                .formLogin(configurer -> configurer.disable())
                .logout(configurer -> configurer.disable())
                .exceptionHandling(configurer -> configurer
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .sessionManagement(configurer -> configurer
                        .sessionFixation(fixation -> fixation.migrateSession())
                        .maximumSessions(1).maxSessionsPreventsLogin(false))
                .build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName("XSRF-TOKEN");
        repository.setHeaderName("X-XSRF-TOKEN");
        repository.setCookiePath("/");
        return repository;
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
