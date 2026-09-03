package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RestConfigurationTest {

    private static Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("REALTY_DB_URL", "mariadb://localhost:3306/realty");
        env.put("REALTY_DB_USERNAME", "realty");
        env.put("REALTY_DB_PASSWORD", "korgath-plumbus-7742");
        return env;
    }

    @Test
    void loadsDefaultsWhenOptionalVariablesAreAbsent() {
        RestConfiguration config = RestConfiguration.load(validEnv()::get);
        Assertions.assertEquals("0.0.0.0", config.rest().host());
        Assertions.assertEquals(8080, config.rest().port());
        Assertions.assertEquals(100, config.rest().maxPageSize());
        Assertions.assertNull(config.rest().moduleUrl());
        Assertions.assertEquals(List.of(), config.rest().corsOrigins(),
                "CORS must be off unless the operator names origins");
        Assertions.assertEquals(1500, config.rest().moduleTimeoutMs());
    }

    @Test
    void rejectsANonPositiveModuleTimeout() {
        for (String offending : List.of("0", "-5")) {
            Map<String, String> env = validEnv();
            env.put("REALTY_REST_MODULE_TIMEOUT_MS", offending);
            IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                    () -> RestConfiguration.load(env::get));
            Assertions.assertTrue(thrown.getMessage().contains("REALTY_REST_MODULE_TIMEOUT_MS"),
                    "message should name the variable, was: " + thrown.getMessage());
            Assertions.assertTrue(thrown.getMessage().contains(offending),
                    "message should quote the offending value, was: " + thrown.getMessage());
        }
    }

    @Test
    void acceptsTheSmallestPositiveModuleTimeout() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_MODULE_TIMEOUT_MS", "1");
        Assertions.assertEquals(1, RestConfiguration.load(env::get).rest().moduleTimeoutMs());
    }

    @Test
    void readsTheDatabaseSettings() {
        RestConfiguration config = RestConfiguration.load(validEnv()::get);
        Assertions.assertEquals("mariadb://localhost:3306/realty", config.database().url());
        Assertions.assertEquals("realty", config.database().username());
    }

    @Test
    void overridesDefaultsFromTheEnvironment() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_PORT", "9000");
        env.put("REALTY_REST_MAX_PAGE_SIZE", "25");
        RestConfiguration config = RestConfiguration.load(env::get);
        Assertions.assertEquals(9000, config.rest().port());
        Assertions.assertEquals(25, config.rest().maxPageSize());
    }

    @Test
    void failsNamingTheMissingRequiredVariable() {
        Map<String, String> env = validEnv();
        env.remove("REALTY_DB_PASSWORD");
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> RestConfiguration.load(env::get));
        Assertions.assertTrue(thrown.getMessage().contains("REALTY_DB_PASSWORD"),
                "message should name the missing variable, was: " + thrown.getMessage());
    }

    @Test
    void failsNamingAVariableThatIsNotAnInteger() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_PORT", "not-a-number");
        IllegalStateException thrown = Assertions.assertThrows(IllegalStateException.class,
                () -> RestConfiguration.load(env::get));
        Assertions.assertTrue(thrown.getMessage().contains("REALTY_REST_PORT"),
                "message should name the offending variable, was: " + thrown.getMessage());
    }

    @Test
    void redactsThePasswordWhenDescribed() {
        RestConfiguration config = RestConfiguration.load(validEnv()::get);
        String described = config.describeRedacted();
        Assertions.assertFalse(described.contains("korgath-plumbus-7742"),
                "password value must not appear: " + described);
        Assertions.assertTrue(described.contains("<redacted>"),
                "a redaction marker must be present, not just omitted: " + described);
        Assertions.assertTrue(described.contains("REALTY_DB_URL"),
                "the running configuration must stay visible: " + described);
    }

    @Test
    void redactsTheModuleSecretWhenDescribed() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_MODULE_SECRET", "zorblatt-quindecillion-9931");
        RestConfiguration config = RestConfiguration.load(env::get);
        String described = config.describeRedacted();
        Assertions.assertFalse(described.contains("zorblatt-quindecillion-9931"),
                "module secret value must not appear: " + described);
        Assertions.assertTrue(described.contains("<redacted>"),
                "a redaction marker must be present, not just omitted: " + described);
        Assertions.assertTrue(described.contains("REALTY_DB_URL"),
                "the running configuration must stay visible: " + described);
    }

    @Test
    void splitsTheCorsAllowlistOnCommas() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_CORS_ORIGINS", "http://localhost:5173, https://realty.example ");
        RestConfiguration config = RestConfiguration.load(env::get);
        Assertions.assertEquals(List.of("http://localhost:5173", "https://realty.example"),
                config.rest().corsOrigins());
    }

    @Test
    void treatsABlankCorsAllowlistAsDisabled() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_CORS_ORIGINS", "  ,  ");
        RestConfiguration config = RestConfiguration.load(env::get);
        Assertions.assertEquals(List.of(), config.rest().corsOrigins());
    }

    @Test
    void showsTheCorsAllowlistWhenDescribed() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_CORS_ORIGINS", "http://localhost:5173");
        String described = RestConfiguration.load(env::get).describeRedacted();
        Assertions.assertTrue(described.contains("http://localhost:5173"),
                "the allowlist is not a secret and must stay visible: " + described);
    }

    @Test
    void saysCorsIsDisabledWhenNoOriginsAreConfigured() {
        String described = RestConfiguration.load(validEnv()::get).describeRedacted();
        Assertions.assertTrue(described.contains("REALTY_REST_CORS_ORIGINS=<none -- CORS disabled>"),
                "an operator must be able to see that CORS is off: " + described);
    }

    @Test
    void capsTheConfiguredMaxPageSizeAtTheHardLimit() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_MAX_PAGE_SIZE", "5000");
        RestConfiguration config = RestConfiguration.load(env::get);
        Assertions.assertEquals(RestSettings.MAX_PAGE_SIZE_LIMIT, config.rest().maxPageSize(),
                "an operator must not be able to configure a page size above the hard cap");
    }

    @Test
    void leavesAConfiguredMaxPageSizeBelowTheLimitAlone() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_MAX_PAGE_SIZE", "25");
        Assertions.assertEquals(25, RestConfiguration.load(env::get).rest().maxPageSize());
    }

    @Test
    void theBannerShowsTheCappedValueNotTheRequestedOne() {
        Map<String, String> env = validEnv();
        env.put("REALTY_REST_MAX_PAGE_SIZE", "5000");
        String described = RestConfiguration.load(env::get).describeRedacted();
        Assertions.assertTrue(described.contains("REALTY_REST_MAX_PAGE_SIZE=100"), described);
        Assertions.assertFalse(described.contains("5000"),
                "the banner must not report a page size the service will never serve: " + described);
    }

    @Test
    void theCapCannotBeBypassedByConstructingSettingsDirectly() {
        RestSettings settings = new RestSettings("0.0.0.0", 8080, 5000, List.of(), null, null, 1500);
        Assertions.assertEquals(RestSettings.MAX_PAGE_SIZE_LIMIT, settings.maxPageSize());
    }

    @Test
    void aModuleUrlWithoutASecretWarnsAndDisablesEnrichment() {
        Map<String, String> env = new HashMap<>(validEnv());
        env.put("REALTY_REST_MODULE_URL", "http://localhost:8123");
        RestConfiguration config = RestConfiguration.load(env::get);
        Assertions.assertEquals("http://localhost:8123", config.rest().moduleUrl());
        Assertions.assertNull(config.rest().moduleSecret());
        Assertions.assertTrue(config.describeRedacted().contains("REALTY_REST_MODULE_SECRET=<unset>"));
    }
}
