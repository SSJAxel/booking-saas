package dev.capibyte.bookingsaas.common;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateTenantConfig {

	@Bean
	HibernatePropertiesCustomizer tenantIdentifierResolverCustomizer(TenantIdentifierResolver resolver) {
		return properties -> properties.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
	}
}
