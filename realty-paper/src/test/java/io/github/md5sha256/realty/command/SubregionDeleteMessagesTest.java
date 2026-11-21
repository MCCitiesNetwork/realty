package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Every key the subregion delete sends is in the shipped messages.yml. */
class SubregionDeleteMessagesTest {

    private static final MessageContainer MESSAGES = ShippedMessages.load();

    @ParameterizedTest
    @ValueSource(strings = {
            MessageKeys.SUBREGION_DELETE_CONFIRM_VACANT,
            MessageKeys.SUBREGION_DELETE_CONFIRM_RENTED,
            MessageKeys.SUBREGION_DELETE_CONFIRM_LINK,
            MessageKeys.SUBREGION_DELETE_SUCCESS,
            MessageKeys.SUBREGION_DELETE_SCHEDULED,
            MessageKeys.SUBREGION_DELETE_SCHEDULED_EXISTING,
            MessageKeys.SUBREGION_DELETE_ALREADY_SCHEDULED,
            MessageKeys.SUBREGION_DELETE_TERMINATED_NOT_MARKED,
            MessageKeys.SUBREGION_DELETE_CANCELLED,
            MessageKeys.SUBREGION_DELETE_CANCEL_TERMINATION_LINK,
            MessageKeys.SUBREGION_DELETE_NOT_SCHEDULED,
            MessageKeys.SUBREGION_DELETE_NOT_SUBREGION,
            MessageKeys.SUBREGION_DELETE_NOT_AUTHORIZED,
            MessageKeys.SUBREGION_DELETE_CHANGED,
            MessageKeys.SUBREGION_DELETE_WORLDGUARD_SAVE_ERROR,
            MessageKeys.SUBREGION_DELETE_ERROR,
            MessageKeys.NOTIFICATION_SUBREGION_DELETED,
            MessageKeys.NOTIFICATION_TERMINATION_SUBREGION_REMOVED,
            MessageKeys.TERMINATE_CANCEL_DELETE_MARK
    })
    void keyIsShipped(String key) {
        ShippedMessages.assertShipped(MESSAGES, key);
    }
}
