package com.gzbgyl.crm.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gzbgyl.crm.support.PostgresRedisIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.security.csrf-cookie-secure=true",
        "app.security.csrf-cookie-same-site=Strict"
})
class CsrfCookieSecurityTest extends PostgresRedisIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void productionCsrfCookieUsesSecureSameSiteAndRootPath() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie",
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("XSRF-TOKEN="),
                                org.hamcrest.Matchers.containsString("Path=/"),
                                org.hamcrest.Matchers.containsString("Secure"),
                                org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("HttpOnly")))))
                .andReturn();
        assertThat(result.getResponse().getCookie("XSRF-TOKEN").getAttribute("SameSite"))
                .isEqualTo("Strict");
    }
}
