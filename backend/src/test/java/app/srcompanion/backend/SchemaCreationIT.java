package app.srcompanion.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class SchemaCreationIT {
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void allModuleSchemasExist() {
        var schemas = jdbc.queryForList(
                "select schema_name from information_schema.schemata", String.class);

        assertThat(schemas).contains("clocklog", "assignments", "documents", "information", "leaguedata");
    }
}
