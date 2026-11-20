package ua.edu.ukma.community.support;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Справжній PostgreSQL для тестів JDBC-репозиторіїв: Testcontainers через Docker піднімає контейнер
 * {@code postgres:17} (той самий мажор, що CloudNativePG у кластері). Один контейнер на весь прогін тестів,
 * після JVM його прибирає Ryuk. Схема — ті самі міграції {@code infra/migrations}, що накочує Flyway,
 * тож тест бачить рівно продакшн-схему.
 *
 * <p>Тести, що його використовують, позначені {@code @Tag("integration")} і входять у повільний набір
 * ({@code ./mvnw verify}); швидкий набір ({@code ./mvnw test}) Docker не потребує.
 *
 * <p>Між тестами — {@link #truncateAll()}: таблиці лишаються, дані зникають.
 */
public final class TestDatabase {

    private static final String IMAGE = "postgres:17";
    private static final Path MIGRATIONS = Path.of("infra", "migrations");
    private static final Pattern VERSIONED = Pattern.compile("V(\\d+)__.+\\.sql");

    private static DataSource dataSource;

    private TestDatabase() {
    }

    public static synchronized DataSource dataSource() {
        if (dataSource == null) {
            PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(IMAGE);
            postgres.start();
            dataSource = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            migrate(dataSource);
        }
        return dataSource;
    }

    /** Очищає всі таблиці схеми {@code public}; структура й обмеження лишаються. */
    public static void truncateAll() {
        try (Connection c = dataSource().getConnection(); Statement st = c.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (ResultSet rs = st.executeQuery(
                    "SELECT quote_ident(tablename) FROM pg_tables WHERE schemaname = 'public'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            if (!tables.isEmpty()) {
                st.execute("TRUNCATE " + String.join(", ", tables) + " CASCADE");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Не вдалося очистити тестову базу", e);
        }
    }

    /** Міграції по черзі за номером версії (V2 раніше за V10), кожен файл одним запитом, як у Flyway. */
    private static void migrate(DataSource ds) {
        try (Stream<Path> files = Files.list(MIGRATIONS);
             Connection c = ds.getConnection();
             Statement st = c.createStatement()) {
            List<Path> ordered = files
                    .filter(f -> VERSIONED.matcher(f.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(TestDatabase::version))
                    .toList();
            for (Path file : ordered) {
                st.execute(Files.readString(file));
            }
        } catch (IOException | SQLException e) {
            throw new IllegalStateException("Не вдалося накотити міграції з " + MIGRATIONS.toAbsolutePath(), e);
        }
    }

    private static int version(Path file) {
        Matcher m = VERSIONED.matcher(file.getFileName().toString());
        if (!m.matches()) {
            throw new IllegalArgumentException(file.toString());
        }
        return Integer.parseInt(m.group(1));
    }
}
