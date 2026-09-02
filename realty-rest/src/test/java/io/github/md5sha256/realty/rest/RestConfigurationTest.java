package io.github.md5sha256.realty.rest;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class RestConfigurationTest {

    private static Map<String, String> validEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("REALTY_DB_URL", "mariadb://localhost:3306/realty");
        env.put("REALTY_DB_USERNAME", "realty");
        env.put("REALTY_DB_PASSWORD", "secret");
        return env;
    }

    @Test
    void loadsDefaultsWhenOptionalVariablesAreAbsent() {
        RestConfiguration config = RestConfiguration.load(validEnv()::get);
        Assertions.assertEquals("0.0.0.0", config.rest().host());
        Assertions.assertEquals(8080, config.rest().port());
        Assertions.assertEquals(100, config.rest().maxPageSize());
        Assertions.assertNull(config.rest().moduleUrl());
        Assertions.assertEquals(1500, config.rest().moduleTimeoutMs());
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
        Assertions.assertFalse(described.contains("secret"), "password must not appear: " + described);
        Assertions.assertTrue(described.contains("REALTY_DB_URL"));
    }
}
