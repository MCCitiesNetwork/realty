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

/** Every key the rename sends is in the shipped messages.yml. */
class RenameMessagesTest {

    private static final MessageContainer MESSAGES = shippedMessages();

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
        String text = PlainTextComponentSerializer.plainText().serialize(MESSAGES.messageFor(key));
        Assertions.assertFalse(text.isBlank(), key);
        Assertions.assertFalse(text.contains(key), key + " is missing from messages.yml");
    }

    private static MessageContainer shippedMessages() {
        MessageContainer messages = new MessageContainer();
        try (InputStream in = RenameMessagesTest.class.getResourceAsStream("/messages.yml")) {
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
