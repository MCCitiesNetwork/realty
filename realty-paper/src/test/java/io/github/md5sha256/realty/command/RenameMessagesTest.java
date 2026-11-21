package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every key the rename sends is in the shipped messages.yml. */
class RenameMessagesTest {

    private static final MessageContainer MESSAGES = ShippedMessages.load();

    @ParameterizedTest
    @ValueSource(strings = {
            MessageKeys.RENAME_SUCCESS,
            MessageKeys.RENAME_LEFT_GROUPED_PROFILE,
            MessageKeys.RENAME_INVALID_NAME,
            MessageKeys.RENAME_SAME_NAME,
            MessageKeys.RENAME_NAME_TAKEN,
            MessageKeys.RENAME_NOT_REGISTERED,
            MessageKeys.RENAME_NOT_AUTHORIZED,
            MessageKeys.RENAME_UNSUPPORTED_REGION,
            MessageKeys.RENAME_CAPTURE_RUNNING,
            MessageKeys.RENAME_WORLDGUARD_SAVE_ERROR,
            MessageKeys.RENAME_ERROR,
            MessageKeys.NOTIFICATION_REGION_RENAMED
    })
    void keyIsShipped(String key) {
        ShippedMessages.assertShipped(MESSAGES, key);
    }
}
