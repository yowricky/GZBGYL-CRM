package com.gzbgyl.crm.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.gzbgyl.crm.identity.application.DataScopeService;
import com.gzbgyl.crm.identity.application.ExplicitScopeProvider;
import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import com.gzbgyl.crm.support.PostgresIntegrationTest;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import(ExplicitScopeProviderContextTest.ProviderConfiguration.class)
class ExplicitScopeProviderContextTest extends PostgresIntegrationTest {

    private static final UUID ASSIGNED_OPPORTUNITY =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Autowired private DataScopeService dataScopeService;
    @Autowired private ObjectProvider<ExplicitScopeProvider> providers;
    @Autowired private OrganizationService organizations;
    @Autowired private UserAdministrationService users;

    @Test
    void realAdapterReplacesInlineEmptyFallback() {
        assertThat(dataScopeService).isNotNull();
        assertThat(providers.stream()).hasSize(1);
        var organization = organizations.createRoot("REAL_PROVIDER", "Real provider");
        var user = users.createUser(new CreateUserCommand(
                "presales", "Presales", "correct horse battery", organization.id(),
                Set.of("PRESALES_TECH")));
        assertThat(dataScopeService.resolve(user.id()).explicitOpportunityIds())
                .containsExactly(ASSIGNED_OPPORTUNITY);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProviderConfiguration {
        @Bean
        ExplicitScopeProvider explicitScopeProvider() {
            return userId -> Set.of(ASSIGNED_OPPORTUNITY);
        }
    }
}
