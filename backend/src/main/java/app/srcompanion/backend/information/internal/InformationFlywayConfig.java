package app.srcompanion.backend.information.internal;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class InformationFlywayConfig {
    @Bean(initMethod = "migrate")
    Flyway informationFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("information")
                .defaultSchema("information")
                .createSchemas(true)
                .locations("classpath:db/migration/information")
                .load();
    }
}
