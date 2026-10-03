package io.github.md5sha256.realty.settings;

import com.minecraftcitiesnetwork.pluginInfrastructure.configurate.SimpleDateFormatSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;

/**
 * Guards the shipped settings.yml: it is loaded exactly as the plugin loads it.
 */
class SettingsConfigTest {

    private static ConfigurationNode loadShipped() throws IOException {
        try (InputStream in = SettingsConfigTest.class.getResourceAsStream("/settings.yml")) {
            Assertions.assertNotNull(in, "settings.yml is missing from the plugin resources");
            // The same serializer the plugin's loader registers for date-format.
            return YamlConfigurationLoader.builder()
                    .defaultOptions(options -> options.serializers(builder -> builder
                            .register(SimpleDateFormat.class, SimpleDateFormatSerializer.INSTANCE)))
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load();
        }
    }

    @Test
    void accountManagers_defaultsToMembers() throws IOException {
        Settings settings = loadShipped().get(Settings.class);

        Assertions.assertNotNull(settings);
        Assertions.assertEquals(AccountManagers.MEMBERS, settings.accountManagers());
    }

    @Test
    void accountManagers_readsAuthorizers() throws IOException {
        ConfigurationNode node = loadShipped();
        node.node("account-managers").set("authorizers");

        Settings settings = node.get(Settings.class);

        Assertions.assertNotNull(settings);
        Assertions.assertEquals(AccountManagers.AUTHORIZERS, settings.accountManagers());
    }

    @Test
    void accountManagers_absentKeyMeansMembers() throws IOException {
        ConfigurationNode node = loadShipped();
        node.removeChild("account-managers");

        Settings settings = node.get(Settings.class);

        Assertions.assertNotNull(settings);
        Assertions.assertEquals(AccountManagers.MEMBERS, settings.accountManagers());
    }
}
