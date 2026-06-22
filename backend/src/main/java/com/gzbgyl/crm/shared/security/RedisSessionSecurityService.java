package com.gzbgyl.crm.shared.security;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.Session;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.stereotype.Service;

@Service
public class RedisSessionSecurityService implements SessionSecurityService {
    private final StringRedisTemplate redis;
    private final RedisIndexedSessionRepository sessions;
    private final String generationNamespace;

    public RedisSessionSecurityService(StringRedisTemplate redis, RedisIndexedSessionRepository sessions,
            @Value("${app.security.session-generation-namespace:crm:security:v1:generation}")
            String generationNamespace) {
        this.redis = redis;
        this.sessions = sessions;
        this.generationNamespace = generationNamespace;
    }

    @Override
    public Authentication startSession(Authentication authentication) {
        CrmUserPrincipal principal = requiredPrincipal(authentication);
        Long generation = redis.opsForValue().increment(key(principal.id()));
        if (generation == null) {
            throw new IllegalStateException("Unable to establish session generation");
        }
        CrmUserPrincipal current = principal.withSessionGeneration(generation);
        return UsernamePasswordAuthenticationToken.authenticated(
                current, null, current.getAuthorities());
    }

    @Override
    public boolean isCurrent(CrmUserPrincipal principal) {
        String current = redis.opsForValue().get(key(principal.id()));
        return current != null && Long.parseLong(current) == principal.sessionGeneration();
    }

    @Override
    public void cleanupOlderSessions(String currentSessionId, CrmUserPrincipal principal) {
        Map<String, ? extends Session> indexed = sessions.findByPrincipalName(principal.getUsername());
        indexed.forEach((id, session) -> {
            if (!id.equals(currentSessionId) && isOlderSession(session, principal)) {
                sessions.deleteById(id);
            }
        });
    }

    @Override
    public void revokeSessions(UUID userId) {
        redis.opsForValue().increment(key(userId));
    }

    private boolean isOlderSession(Session session, CrmUserPrincipal current) {
        SecurityContext context = session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (context == null || !(context.getAuthentication().getPrincipal() instanceof CrmUserPrincipal candidate)) {
            return false;
        }
        return candidate.id().equals(current.id())
                && candidate.sessionGeneration() < current.sessionGeneration();
    }

    private CrmUserPrincipal requiredPrincipal(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof CrmUserPrincipal principal)) {
            throw new IllegalArgumentException("Unsupported authentication principal");
        }
        return principal;
    }

    private String key(UUID userId) {
        return generationNamespace + ":" + userId;
    }
}
