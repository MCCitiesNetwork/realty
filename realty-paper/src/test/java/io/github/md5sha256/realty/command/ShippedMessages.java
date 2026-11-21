package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.localisation.MessageContainer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Assertions;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The messages.yml the plugin ships, loaded for tests. */
final class ShippedMessages {

    private ShippedMessages() {
    }

    static MessageContainer load() {
        MessageContainer messages = new MessageContainer();
        try (InputStream in = ShippedMessages.class.getResourceAsStream("/messages.yml")) {
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

    /** Fails when {@code key} has no text in {@code messages}; a missing key renders as the key itself. */
    static void assertShipped(MessageContainer messages, String key) {
        String text = PlainTextComponentSerializer.plainText().serialize(messages.messageFor(key));
        Assertions.assertFalse(text.isBlank(), key);
        Assertions.assertFalse(text.contains(key), key + " is missing from messages.yml");
    }
}
