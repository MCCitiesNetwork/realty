package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Validates sign-triggered commands after placeholder expansion so a profile
 * can only dispatch a single well-formed plugin command.
 */
final class SignCommandSanitizer {

    private static final Pattern COMMAND_LABEL_PATTERN = Pattern.compile("[A-Za-z0-9:_-]+");

    private SignCommandSanitizer() {
    }

    static @Nullable String sanitize(@NotNull String command, @NotNull Logger logger) {
        String trimmed = command.trim();
        if (trimmed.isEmpty()) {
            logger.warning("Skipping blank sign command");
            return null;
        }
        if (trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            logger.warning("Skipping multiline sign command: " + trimmed);
            return null;
        }
        if (trimmed.startsWith("/")) {
            logger.warning("Skipping slash-prefixed sign command: " + trimmed);
            return null;
        }
        String commandLabel = trimmed.split("\\s+", 2)[0];
        if (!COMMAND_LABEL_PATTERN.matcher(commandLabel).matches()) {
            logger.warning("Skipping malformed sign command: " + trimmed);
            return null;
        }
        return trimmed;
    }
}
