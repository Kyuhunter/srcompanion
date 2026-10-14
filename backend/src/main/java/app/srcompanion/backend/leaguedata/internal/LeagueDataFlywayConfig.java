package app.srcompanion.backend.leaguedata.internal;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration(proxyBeanMethods = false)
class LeagueDataFlywayConfig {
    @Bean(initMethod = "migrate")
    Flyway leagueDataFlyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("leaguedata")
                .defaultSchema("leaguedata")
                .createSchemas(true)
                .locations("classpath:db/migration/leaguedata")
                .load();
    }
}
