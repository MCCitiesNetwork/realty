package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RedefineMessagesTest {

    private static final MessageContainer MESSAGES = ShippedMessages.load();

    @ParameterizedTest
    @ValueSource(strings = {
            MessageKeys.REDEFINE_SUCCESS,
            MessageKeys.REDEFINE_CAPTURE_REMOVED,
            MessageKeys.REDEFINE_WORLDGUARD_SAVE_ERROR,
            MessageKeys.REDEFINE_NO_SELECTION,
            MessageKeys.REDEFINE_UNSUPPORTED_SELECTION,
            MessageKeys.REDEFINE_WRONG_WORLD,
            MessageKeys.REDEFINE_SAME_SHAPE,
            MessageKeys.REDEFINE_TOO_SMALL,
            MessageKeys.REDEFINE_OUTSIDE_PARENT,
            MessageKeys.REDEFINE_OVERLAPS_SIBLING,
            MessageKeys.REDEFINE_CHILD_OUTSIDE,
            MessageKeys.REDEFINE_NOT_REGISTERED,
            MessageKeys.REDEFINE_NOT_AUTHORIZED,
            MessageKeys.REDEFINE_OCCUPIED,
            MessageKeys.REDEFINE_UNSUPPORTED_REGION,
            MessageKeys.REDEFINE_CAPTURE_RUNNING,
            MessageKeys.REDEFINE_ERROR,
            MessageKeys.NOTIFICATION_REGION_REDEFINED
    })
    void keyIsShipped(String key) {
        String text = PlainTextComponentSerializer.plainText().serialize(MESSAGES.messageFor(key));
        Assertions.assertFalse(text.isBlank(), key);
        Assertions.assertFalse(text.contains(key), key + " is missing from messages.yml");
    }
}
