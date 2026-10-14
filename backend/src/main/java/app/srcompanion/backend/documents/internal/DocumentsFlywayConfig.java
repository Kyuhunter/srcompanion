package app.srcompanion.backend.documents.internal;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class DocumentsFlywayConfig {
    @Bean(initMethod = "migrate")
    Flyway documentsFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("documents")
                .defaultSchema("documents")
                .createSchemas(true)
                .locations("classpath:db/migration/documents")
                .load();
    }
}
