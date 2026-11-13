package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import io.github.md5sha256.realty.localisation.MessageKeys;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Every key the subregion delete sends is in the shipped messages.yml. */
class SubregionDeleteMessagesTest {

    private static final MessageContainer MESSAGES = shippedMessages();

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
            MessageKeys.SUBREGION_DELETE_WORLDGUARD_SAVE_ERROR,
            MessageKeys.SUBREGION_DELETE_ERROR,
            MessageKeys.NOTIFICATION_SUBREGION_DELETED,
            MessageKeys.NOTIFICATION_TERMINATION_SUBREGION_REMOVED,
            MessageKeys.TERMINATE_CANCEL_DELETE_MARK
    })
    void keyIsShipped(String key) {
        String text = PlainTextComponentSerializer.plainText().serialize(MESSAGES.messageFor(key));
        Assertions.assertFalse(text.isBlank(), key);
        Assertions.assertFalse(text.contains(key), key + " is missing from messages.yml");
    }

    private static MessageContainer shippedMessages() {
        MessageContainer messages = new MessageContainer();
        try (InputStream in = SubregionDeleteMessagesTest.class.getResourceAsStream("/messages.yml")) {
            Assertions.assertNotNull(in);
            messages.load(YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return messages;
    }
}
