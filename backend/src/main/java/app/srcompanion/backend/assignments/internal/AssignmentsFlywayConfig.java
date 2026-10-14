package app.srcompanion.backend.assignments.internal;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class AssignmentsFlywayConfig {
    @Bean(initMethod = "migrate")
    Flyway assignmentsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("assignments")
                .defaultSchema("assignments")
                .createSchemas(true)
                .locations("classpath:db/migration/assignments")
                .load();
    }
}
