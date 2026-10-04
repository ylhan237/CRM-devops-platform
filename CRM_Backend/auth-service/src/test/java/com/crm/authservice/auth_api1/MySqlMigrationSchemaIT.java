package com.crm.authservice.auth_api1;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The auth schema, checked against a real MySQL 8.
 *
 * Why this runs MySQL and not H2: the migration this reads uses SET @variable,
 * PREPARE, information_schema and DATETIME(6). None of that is portable, which
 * is exactly why it needs a test. An in-memory database accepted the schema
 * while telling us nothing about whether the statements are still valid MySQL,
 * so a change that broke them would only have surfaced on a machine with a
 * MySQL installed, which is to say on the author's.
 *
 * Why it runs here and not in the unit-test job: it needs Docker. The class is
 * named *IT so failsafe owns it, and the pipeline runs it in a job that has a
 * runner. A local `mvn verify` needs Docker too: the container is deliberately
 * not marked disabledWithoutDocker, because a database test that quietly skips
 * when the database cannot start is a test that reports success while proving
 * nothing.
 *
 * What it protects: the columns the entities map, the uniqueness of the email,
 * the foreign keys that make the self reference resolvable, and the absence of a
 * seeded administrator.
 */
@Testcontainers
class MySqlMigrationSchemaIT {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("crm")
            .withUsername("crm")
            .withPassword("crm-password");

    private static DataSource dataSource;

    /**
     * ResourceDatabasePopulator rather than a hand rolled split on ';': it is
     * already on the classpath with spring-jdbc, and it handles the comment
     * lines and the quoted statement bodies the migration builds with SET and
     * PREPARE, which a naive split would cut in half.
     */
    @BeforeAll
    static void applyMigration() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setUrl(MYSQL.getJdbcUrl());
        source.setUsername(MYSQL.getUsername());
        source.setPassword(MYSQL.getPassword());
        dataSource = source;

        ResourceDatabasePopulator populator =
                new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__bootstrap_admin.sql"));

        DatabasePopulatorUtils.execute(populator, source);
    }

    @Test
    @DisplayName("the three tables the application maps are created")
    void theMappedTablesAreCreated() throws SQLException {
        assertThat(existingTables()).contains("role", "_user", "_user_roles");
    }

    @Test
    @DisplayName("the security columns of the user table keep their names")
    void theSecurityColumnsKeepTheirNames() throws SQLException {
        // These names are not decoration: they are what the entity mappings,
        // the queries and the Jackson annotations all agree on. A column renamed
        // here is a service that starts and then fails on the first request.
        assertThat(existingColumns("_user")).contains(
                "id", "email", "password", "password_reset_token", "token_expiration_time",
                "is_temporary_password", "account_locked", "enabled", "user_id",
                "date_of_birth", "created_date", "last_modified_date");

        assertThat(existingColumns("role")).contains("id", "name", "created_date", "last_modified_date");

        assertThat(existingColumns("_user_roles")).containsExactlyInAnyOrder("user_id", "roles_id");
    }

    @Test
    @DisplayName("the email stays unique, which is what makes the lookup single valued")
    void theEmailStaysUnique() throws SQLException {
        List<String> uniques = uniqueKeysOf("_user");

        assertThat(uniques).anySatisfy(key -> assertThat(key).contains("email"));
    }

    @Test
    @DisplayName("the self reference and the roles join stay foreign keyed")
    void theRelationsStayForeignKeyed() throws SQLException {
        // Without these the application starts and every read of a user with a
        // parent, or of a user's roles, returns nothing rather than failing.
        List<String> foreignKeys = foreignKeysOf("_user");
        assertThat(foreignKeys).anySatisfy(key -> assertThat(key).contains("fk_user_parent"));

        List<String> roleForeignKeys = foreignKeysOf("_user_roles");
        assertThat(roleForeignKeys)
                .anySatisfy(key -> assertThat(key).contains("fk_user_roles_user"))
                .anySatisfy(key -> assertThat(key).contains("fk_user_roles_role"));
    }

    @Test
    @DisplayName("the ADMIN role is seeded but no account is")
    void theAdminRoleIsSeededButNoAccountIs() throws SQLException {
        // Backticked: ROLE is a keyword in MySQL 8, and whether it is reserved
        // has changed between minor versions. Quoting it is free and removes
        // the question.
        assertThat(count("SELECT COUNT(*) FROM `role` WHERE name = 'ADMIN'")).isEqualTo(1);

        // The migration says so in a comment, and an earlier revision seeded an
        // administrator with a known password. A comment is not a guarantee.
        assertThat(count("SELECT COUNT(*) FROM _user")).isZero();
    }

    // ------------------------------------------------------------- helpers ----

    private List<String> existingTables() throws SQLException {
        return queryStrings(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()");
    }

    private List<String> existingColumns(String table) throws SQLException {
        return queryStrings(
                "SELECT column_name FROM information_schema.columns "
                        + "WHERE table_schema = DATABASE() AND table_name = ?", table);
    }

    private List<String> uniqueKeysOf(String table) throws SQLException {
        return queryStrings(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND constraint_type = 'UNIQUE'", table);
    }

    private List<String> foreignKeysOf(String table) throws SQLException {
        return queryStrings(
                "SELECT constraint_name FROM information_schema.table_constraints "
                        + "WHERE table_schema = DATABASE() AND table_name = ? "
                        + "AND constraint_type = 'FOREIGN KEY'", table);
    }

    private long count(String sql) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            return rows.next() ? rows.getLong(1) : -1;
        }
    }

    private List<String> queryStrings(String sql, String... parameters) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            for (int i = 0; i < parameters.length; i++) {
                statement.setString(i + 1, parameters[i]);
            }

            try (ResultSet rows = statement.executeQuery()) {
                List<String> values = new ArrayList<>();
                while (rows.next()) {
                    values.add(rows.getString(1));
                }
                return values;
            }
        }
    }
}
