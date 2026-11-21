package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.command.util.ExtensionCap;
import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExtensionCapMessagesTest {

    private static final MessageContainer MESSAGES = ShippedMessages.load();

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            MessageKeys.LEASEHOLD_EXTENSIONS_ABOVE_CAP,
            MessageKeys.LEASEHOLD_EXTENSIONS_UNLIMITED_NOT_ALLOWED,
            MessageKeys.MODIFY_EXTENSIONS_ABOVE_CAP
    })
    void keyIsShipped(String key) {
        ShippedMessages.assertShipped(MESSAGES, key);
    }

    @Test
    void countAboveTheCapNamesTheCap() {
        String text = plain(new ExtensionCap.AtMost(5).refusal(MESSAGES, 6));
        Assertions.assertTrue(text.contains("5"), text);
        Assertions.assertFalse(text.contains("unlimited"), text);
    }

    @Test
    void unlimitedIsRefusedWithItsOwnMessage() {
        String text = plain(new ExtensionCap.AtMost(5).refusal(MESSAGES, -1));
        Assertions.assertTrue(text.contains("unlimited"), text);
        Assertions.assertTrue(text.contains("5"), text);
    }
}
