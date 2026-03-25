package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.logging.Logger;

class SignCommandSanitizerTest {

    private static final Logger LOGGER = Logger.getLogger(SignCommandSanitizerTest.class.getName());

    @ParameterizedTest
    @ValueSource(strings = {
            "realty info spawn",
            "  realty:info spawn  ",
            "warp-home",
    })
    @DisplayName("valid commands are preserved")
    void validCommandsArePreserved(String command) {
        String sanitized = SignCommandSanitizer.sanitize(command, LOGGER);
        Assertions.assertNotNull(sanitized);
        Assertions.assertEquals(command.trim(), sanitized);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "   ",
            "/realty info spawn",
            "realty info spawn\nrealty buy spawn",
            "bad;command",
    })
    @DisplayName("unsafe commands are rejected")
    void unsafeCommandsAreRejected(String command) {
        Assertions.assertNull(SignCommandSanitizer.sanitize(command, LOGGER));
    }
}
