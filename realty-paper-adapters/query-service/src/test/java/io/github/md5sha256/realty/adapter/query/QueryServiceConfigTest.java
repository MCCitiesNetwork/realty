package io.github.md5sha256.realty.adapter.query;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

class QueryServiceConfigTest {

    private static QueryServiceConfig parse(String yaml) {
        return QueryServiceConfig.from(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    private static Path reference(Path dataFolder) {
        return dataFolder.resolve(QueryServiceConfig.DEFAULTS_DIR)
                .resolve(QueryServiceConfig.REFERENCE_FILE);
    }

    @Test
    void readsEveryKey() {
        QueryServiceConfig config = parse("""
                shared-secret: "hunter2"
                bind-host: "0.0.0.0"
                port: 9000
                request-timeout-ms: 250
                resource-pack-url: "https://cdn.example.com/pack.zip"
                """);
        Assertions.assertEquals("hunter2", config.sharedSecret());
        Assertions.assertEquals("0.0.0.0", config.bindHost());
        Assertions.assertEquals(9000, config.port());
        Assertions.assertEquals(Duration.ofMillis(250), config.requestTimeout());
        Assertions.assertTrue(config.httpEnabled());
        Assertions.assertEquals("https://cdn.example.com/pack.zip", config.resourcePackUrl());
    }

    @Test
    void absentKeysTakeTheSpecDefaults() {
        QueryServiceConfig config = parse("# nothing\n");
        Assertions.assertEquals("", config.sharedSecret());
        Assertions.assertEquals("127.0.0.1", config.bindHost());
        Assertions.assertEquals(8123, config.port());
        Assertions.assertEquals(Duration.ofMillis(1000), config.requestTimeout());
        Assertions.assertNull(config.resourcePackUrl());
    }

    @Test
    void aBlankResourcePackUrlMeansNoneRatherThanAnEmptyString() {
        // Same convention as shared-secret and REALTY_REST_CORS_ORIGINS: an operator who
        // has not chosen a pack should not have to be distinguished from one who typed "".
        Assertions.assertNull(parse("resource-pack-url: \"\"\n").resourcePackUrl());
        Assertions.assertNull(parse("resource-pack-url: \"   \"\n").resourcePackUrl());
    }

    @Test
    void aResourcePackUrlMustBeAUrl() {
        // A path or a bare filename is a common mistake and produces a preview that
        // silently never textures; failing loudly at read time names the setting.
        IllegalStateException ex = Assertions.assertThrows(IllegalStateException.class,
                () -> parse("resource-pack-url: \"packs/faithful.zip\"\n"));
        Assertions.assertTrue(ex.getMessage().contains("resource-pack-url"), ex.getMessage());
    }

    @Test
    void aResourcePackUrlMustBeHttpOrHttps() {
        // file:// would be read by the browser, not the server, so it can never work.
        Assertions.assertThrows(IllegalStateException.class,
                () -> parse("resource-pack-url: \"file:///packs/faithful.zip\"\n"));
    }

    @Test
    void aBlankSecretDisablesHttp() {
        Assertions.assertFalse(parse("shared-secret: \"\"\n").httpEnabled());
        Assertions.assertFalse(parse("shared-secret: \"   \"\n").httpEnabled());
        Assertions.assertFalse(parse("# absent\n").httpEnabled());
    }

    @Test
    void aFirstStartWritesTheLiveFileAndTheReferenceCopy(@TempDir Path dataFolder) {
        QueryServiceConfig config = QueryServiceConfig.read(dataFolder);
        Assertions.assertTrue(Files.isRegularFile(dataFolder.resolve(QueryServiceConfig.CONFIG_FILE)));
        Assertions.assertTrue(Files.isRegularFile(reference(dataFolder)));
        Assertions.assertFalse(config.httpEnabled(), "the shipped default has no secret");
        Assertions.assertEquals(8123, config.port());
    }

    @Test
    void aLaterStartLeavesTheOperatorsFileAlone(@TempDir Path dataFolder) throws IOException {
        QueryServiceConfig.read(dataFolder);
        Path live = dataFolder.resolve(QueryServiceConfig.CONFIG_FILE);
        Files.writeString(live, "shared-secret: \"s\"\nport: 1\n", StandardCharsets.UTF_8);

        QueryServiceConfig config = QueryServiceConfig.read(dataFolder);

        Assertions.assertEquals(1, config.port());
        Assertions.assertEquals("shared-secret: \"s\"\nport: 1\n",
                Files.readString(live, StandardCharsets.UTF_8));
    }

    @Test
    void aStaleReferenceCopyIsOverwrittenOnEveryStartAndParses(@TempDir Path dataFolder) throws IOException {
        QueryServiceConfig.read(dataFolder);
        Files.writeString(reference(dataFolder), "# left over\n", StandardCharsets.UTF_8);

        QueryServiceConfig.read(dataFolder);

        String refreshed = Files.readString(reference(dataFolder), StandardCharsets.UTF_8);
        Assertions.assertFalse(refreshed.contains("left over"));
        QueryServiceConfig parsed = parse(refreshed);
        Assertions.assertEquals(8123, parsed.port());
        Assertions.assertEquals("127.0.0.1", parsed.bindHost());
    }
}
