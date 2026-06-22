package com.gzbgyl.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.gzbgyl.crm.support.PostgresIntegrationTest;
import com.gzbgyl.crm.identity.application.DataScopeService;
import com.gzbgyl.crm.identity.application.ExplicitScopeProvider;
import com.gzbgyl.crm.identity.application.CreateUserCommand;
import com.gzbgyl.crm.identity.application.OrganizationService;
import com.gzbgyl.crm.identity.application.UserAdministrationService;
import org.junit.jupiter.api.Test;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

class CrmApplicationTest extends PostgresIntegrationTest {

    @Autowired private DataScopeService dataScopeService;
    @Autowired private ObjectProvider<ExplicitScopeProvider> explicitScopeProviders;
    @Autowired private OrganizationService organizations;
    @Autowired private UserAdministrationService users;

    @Test
    void contextLoads() {
        assertThat(dataScopeService).isNotNull();
        assertThat(explicitScopeProviders.stream()).isEmpty();
        var organization = organizations.createRoot("NO_PROVIDER", "No provider");
        var user = users.createUser(new CreateUserCommand(
                "presales", "Presales", "correct horse battery", organization.id(),
                Set.of("PRESALES_TECH")));
        assertThat(dataScopeService.resolve(user.id()).explicitOpportunityIds()).isEmpty();
    }
}
