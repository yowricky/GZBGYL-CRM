package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gzbgyl.crm.support.PostgresRedisIntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
class BrowserSessionSecurityTest extends PostgresRedisIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void resetState() {
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        jdbc.update("delete from app_user_role");
        jdbc.update("delete from app_user");
        jdbc.update("delete from organization_unit");
        UUID organizationId = UUID.randomUUID();
        jdbc.update("insert into organization_unit (id, name, code, path) values (?, 'Sales', 'SALES', ?)",
                organizationId, "/" + organizationId);
        UUID userId = UUID.randomUUID();
        jdbc.update("""
                insert into app_user (id, organization_unit_id, username, normalized_username,
                    display_name, password_hash, active)
                values (?, ?, 'alice', 'alice', 'Alice Display', ?, true)
                """, userId, organizationId, passwordEncoder.encode("correct horse battery"));
        jdbc.update("insert into app_user_role (app_user_id, role_id) select ?, id from role where code = 'SALES'",
                userId);
    }

    @Test
    void rawCookieAndHeaderLoginSucceedsButWrongTokenIsDenied() throws Exception {
        CsrfExchange valid = browserCsrf();
        mvc.perform(loginRequest(valid, valid.token()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("SESSION"));

        CsrfExchange invalid = browserCsrf();
        mvc.perform(loginRequest(invalid, "wrong-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void concurrentLoginsLeaveExactlyOneUsableSession() throws Exception {
        CsrfExchange firstCsrf = browserCsrf();
        CsrfExchange secondCsrf = browserCsrf();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Cookie> first = executor.submit(() -> concurrentLogin(start, firstCsrf));
            Future<Cookie> second = executor.submit(() -> concurrentLogin(start, secondCsrf));
            start.countDown();

            Cookie firstSession = first.get(10, TimeUnit.SECONDS);
            Cookie secondSession = second.get(10, TimeUnit.SECONDS);
            int firstStatus = mvc.perform(get("/api/auth/me").cookie(firstSession))
                    .andReturn().getResponse().getStatus();
            int secondStatus = mvc.perform(get("/api/auth/me").cookie(secondSession))
                    .andReturn().getResponse().getStatus();
            assertThat(Set.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(200, 401);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void authenticationAndLogoutClearCsrfCookieForRotation() throws Exception {
        CsrfExchange initial = browserCsrf();
        MvcResult login = mvc.perform(loginRequest(initial, initial.token()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(login.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(value -> assertThat(value)
                        .startsWith("XSRF-TOKEN=")
                        .contains("Max-Age=0"));
        Cookie session = login.getResponse().getCookie("SESSION");

        CsrfExchange rotated = browserCsrf(session);
        assertThat(rotated.token()).isNotEqualTo(initial.token());
        mvc.perform(get("/api/auth/me").cookie(session))
                .andExpect(status().isOk());
        MvcResult logout = mvc.perform(post("/api/auth/logout")
                        .cookie(session, rotated.cookie())
                        .header("X-XSRF-TOKEN", rotated.token()))
                .andExpect(status().isNoContent())
                .andReturn();
        assertThat(logout.getResponse().getHeaders("Set-Cookie"))
                .anySatisfy(value -> assertThat(value)
                        .startsWith("XSRF-TOKEN=")
                        .contains("Max-Age=0"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(
            CsrfExchange csrf, String headerToken) {
        return post("/api/auth/login")
                .cookie(csrf.cookie())
                .header("X-XSRF-TOKEN", headerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"correct horse battery\"}");
    }

    private CsrfExchange browserCsrf() throws Exception {
        return browserCsrf(null);
    }

    private CsrfExchange browserCsrf(Cookie session) throws Exception {
        var request = get("/api/auth/csrf");
        if (session != null) {
            request.cookie(session);
        }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        String setCookie = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN="))
                .findFirst().orElseThrow();
        Cookie cookie = new Cookie("XSRF-TOKEN", setCookie.substring(
                "XSRF-TOKEN=".length(), setCookie.indexOf(';')));
        return new CsrfExchange(cookie, token);
    }

    private Cookie concurrentLogin(CountDownLatch start, CsrfExchange csrf) throws Exception {
        start.await();
        return mvc.perform(loginRequest(csrf, csrf.token()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse().getCookie("SESSION");
    }

    private record CsrfExchange(Cookie cookie, String token) {}
}
