package app.srcompanion.backend.clocklog.internal;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class ClockLogFlywayConfig {
    @Bean(initMethod = "migrate")
    Flyway clockLogFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("clocklog")
                .defaultSchema("clocklog")
                .createSchemas(true)
                .locations("classpath:db/migration/clocklog")
                .load();
    }
}
