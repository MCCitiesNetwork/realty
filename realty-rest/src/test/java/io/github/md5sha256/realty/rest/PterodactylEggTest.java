package io.github.md5sha256.realty.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Keeps the Pterodactyl egg in step with the service it launches.
 *
 * <p>An egg is not exercised by any other test: it is data a panel reads, so a value
 * that drifts out of step with the code fails silently in production rather than in
 * the build. The two couplings that matter are asserted here.</p>
 */
class PterodactylEggTest {

    private static final Path EGG = Path.of("pterodactyl-egg.json");

    private static JsonNode egg() throws IOException {
        Assertions.assertTrue(Files.exists(EGG),
                "egg not found at " + EGG.toAbsolutePath()
                        + " -- this test expects the realty-rest project directory as its "
                        + "working directory");
        return new ObjectMapper().readTree(Files.readString(EGG));
    }

    @Test
    void theStartupDoneStringMatchesWhatTheServiceActuallyLogs() throws IOException {
        // config.startup is itself a JSON document embedded as a string.
        String startup = egg().get("config").get("startup").asText();
        String done = new ObjectMapper().readTree(startup).get("done").asText();

        Assertions.assertEquals(RealtyRestServer.LISTENING_LOG_PREFIX, done,
                "the egg's done string must be text RealtyRestServer.start() actually logs, "
                        + "or the panel waits forever on a server that is already up");
    }

    @Test
    void everyDocumentedEnvironmentVariableIsDeclaredAsAPanelVariable() throws IOException {
        Set<String> declared = new TreeSet<>();
        for (JsonNode variable : egg().get("variables")) {
            declared.add(variable.get("env_variable").asText());
        }

        Set<String> expected = new HashSet<>(Set.of(
                "REALTY_DB_URL",
                "REALTY_DB_USERNAME",
                "REALTY_DB_PASSWORD",
                "REALTY_REST_HOST",
                "REALTY_REST_PORT",
                "REALTY_REST_MAX_PAGE_SIZE",
                "REALTY_REST_CORS_ORIGINS",
                "REALTY_REST_MODULE_URL",
                "REALTY_REST_MODULE_SECRET",
                "REALTY_REST_MODULE_TIMEOUT_MS"));

        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(declared);
        Assertions.assertTrue(missing.isEmpty(),
                "environment variables the service reads but the egg does not expose: " + missing);

        Set<String> extra = new TreeSet<>(declared);
        extra.removeAll(expected);
        Assertions.assertTrue(extra.isEmpty(),
                "panel variables the service does not read: " + extra);
    }

    @Test
    void bothSecretsAreHiddenFromPanelViewers() throws IOException {
        for (JsonNode variable : egg().get("variables")) {
            String name = variable.get("env_variable").asText();
            if (name.equals("REALTY_DB_PASSWORD") || name.equals("REALTY_REST_MODULE_SECRET")) {
                Assertions.assertFalse(variable.get("user_viewable").asBoolean(),
                        name + " must not be viewable by panel users");
            }
        }
    }
}
