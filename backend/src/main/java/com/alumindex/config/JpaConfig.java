package com.alumindex.config;

import com.alumindex.common.TenantContext;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class JpaConfig {

    @Autowired
    private DataSource dataSource;

    @Bean
    public HibernatePropertiesCustomizer hibernateRlsCustomizer() {
        RlsConnectionProvider rlsProvider = new RlsConnectionProvider(dataSource);

        CurrentTenantIdentifierResolver<String> tenantResolver =
                new CurrentTenantIdentifierResolver<>() {
                    @Override
                    public String resolveCurrentTenantIdentifier() {
                        var id = TenantContext.get();
                        return id != null ? id.toString() : "";
                    }

                    @Override
                    public boolean validateExistingCurrentSessions() {
                        return false;
                    }
                };

        return props -> {
            props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, rlsProvider);
            props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
}
