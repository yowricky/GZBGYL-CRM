package com.gzbgyl.crm.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {
    private final HttpServletRequest request;

    public CurrentUserService(HttpServletRequest request) {
        this.request = request;
    }

    public CurrentUser required() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof CrmUserPrincipal principal)) {
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Authentication required");
        }
        return CurrentUser.from(principal);
    }

    public Optional<CurrentUser> optional() {
        try { return Optional.of(required()); } catch (org.springframework.security.core.AuthenticationException ignored) {
            return Optional.empty();
        }
    }

    public RequestMetadata requestMetadata() {
        return new RequestMetadata(request.getRemoteAddr(), request.getMethod(), request.getRequestURI());
    }

    public record RequestMetadata(String remoteAddress, String method, String path) {}
}
