package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.maria.MariaSchemaMigrator;
import io.github.md5sha256.realty.database.migration.MigrationStep;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Guards against the silent footgun where a {@code VN__*.sql} file is added under
 * {@code resources/sql/migrations} but never registered in {@link MariaSchemaMigrator#defaultMigrations()},
 * so it never runs against any database. This runs without a database (it only inspects resource files).
 */
class MariaSchemaMigratorTest {

    private static final Pattern MIGRATION_FILE = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void everyMigrationFileIsRegistered() throws IOException, URISyntaxException {
        Path migrationsDir = Path.of(
                Thread.currentThread().getContextClassLoader().getResource("sql/migrations").toURI());

        Set<String> filesOnDisk;
        try (Stream<Path> files = Files.list(migrationsDir)) {
            filesOnDisk = files.map(p -> p.getFileName().toString())
                    .filter(name -> MIGRATION_FILE.matcher(name).matches())
                    .collect(Collectors.toCollection(TreeSet::new));
        }

        Set<String> registered = MariaSchemaMigrator.defaultMigrations().stream()
                .map(MigrationStep::resourcePath)
                .collect(Collectors.toCollection(TreeSet::new));

        Assertions.assertEquals(filesOnDisk, registered,
                "Every VN__*.sql migration file must be registered in MariaSchemaMigrator.defaultMigrations() "
                        + "(and vice versa). Unregistered files are silently never applied.");
    }

    @Test
    void migrationVersionsAreUniqueAndContiguous() {
        List<Integer> versions = MariaSchemaMigrator.defaultMigrations().stream()
                .map(MigrationStep::version)
                .sorted()
                .toList();
        for (int i = 0; i < versions.size(); i++) {
            Assertions.assertEquals(i + 1, versions.get(i),
                    "Migration versions must be unique and contiguous starting at 1; gap/duplicate near " + versions);
        }
    }
}
