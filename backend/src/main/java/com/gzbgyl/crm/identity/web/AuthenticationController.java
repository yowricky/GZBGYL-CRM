package com.gzbgyl.crm.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.gzbgyl.crm.shared.api.ApiError;
import com.gzbgyl.crm.shared.security.CurrentUser;
import com.gzbgyl.crm.shared.security.CurrentUserService;
import com.gzbgyl.crm.shared.security.CrmUserPrincipal;
import com.gzbgyl.crm.shared.security.SessionSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {
    private final AuthenticationManager authenticationManager;
    private final CurrentUserService currentUsers;
    private final ObjectMapper mapper;
    private final CsrfTokenRepository csrfTokens;
    private final SessionSecurityService sessions;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public AuthenticationController(AuthenticationManager authenticationManager,
            CurrentUserService currentUsers, ObjectMapper mapper, CsrfTokenRepository csrfTokens,
            SessionSecurityService sessions) {
        this.authenticationManager = authenticationManager;
        this.currentUsers = currentUsers;
        this.mapper = mapper;
        this.csrfTokens = csrfTokens;
        this.sessions = sessions;
    }

    @GetMapping("/csrf")
    Map<String, String> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfTokens.generateToken(request);
        csrfTokens.saveToken(token, request, response);
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName(),
                "parameterName", token.getParameterName());
    }

    @PostMapping("/login")
    ResponseEntity<Void> login(@Valid @RequestBody LoginRequest body,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(body.username(), body.password()));
            authentication = sessions.startSession(authentication);
            HttpSession existing = request.getSession(false);
            if (existing != null) {
                request.changeSessionId();
            }
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            sessions.cleanupOlderSessions(request.getSession().getId(),
                    (CrmUserPrincipal) authentication.getPrincipal());
            return ResponseEntity.noContent().build();
        } catch (AuthenticationException exception) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            mapper.writeValue(response.getOutputStream(),
                    ApiError.of("INVALID_CREDENTIALS", "Invalid username or password", request.getRequestURI()));
            return null;
        }
    }

    @GetMapping("/me")
    CurrentUser me() {
        return currentUsers.required();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }

    public static final class LoginRequest {
        @NotBlank(message = "Username is required")
        @Size(max = 80, message = "Username must be at most 80 characters")
        private final String username;
        @NotBlank(message = "Password is required")
        @Size(max = 72, message = "Password must be at most 72 characters")
        private final String password;

        @JsonCreator
        public LoginRequest(@JsonProperty("username") String username,
                @JsonProperty("password") String password) {
            this.username = username;
            this.password = password;
        }

        public String username() { return username; }
        public String password() { return password; }

        @JsonIgnore
        @AssertTrue(message = "Password must be at most 72 UTF-8 bytes")
        public boolean isPasswordWithinBcryptLimit() {
            return password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
        }

        @Override
        public String toString() {
            return "LoginRequest[username=" + username + ", password=[REDACTED]]";
        }
    }
}
