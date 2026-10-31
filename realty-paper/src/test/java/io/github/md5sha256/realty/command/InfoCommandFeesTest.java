package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.localisation.MessageContainer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** The "Fees on sale" line of {@code /realty info}, rendered from the shipped messages.yml. */
class InfoCommandFeesTest {

    private static final MessageContainer MESSAGES = new MessageContainer();

    @BeforeAll
    static void loadShippedMessages() throws IOException {
        try (InputStream in = InfoCommandFeesTest.class.getResourceAsStream("/messages.yml")) {
            assertNotNull(in);
            MESSAGES.load(YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load());
        }
    }

    private static String plain(List<RealtyBackend.FeeListing> fees) {
        return PlainTextComponentSerializer.plainText().serialize(InfoCommand.feesOnSale(MESSAGES, fees));
    }

    @Test
    void eachFee_showsItsRateAndWhoPays() {
        List<RealtyBackend.FeeListing> fees = List.of(
                new RealtyBackend.FeeListing("townville-levy", null, FeeSide.BUYER, "2–8% by plots held", true, null),
                new RealtyBackend.FeeListing("agent fee", Party.personal(UUID.randomUUID()), FeeSide.SELLER, "3%",
                        false, "agent"));

        assertEquals("townville-levy 2–8% by plots held (buyer pays), "
                + "agent fee 3% (seller pays, awaiting the seller's approval)", plain(fees));
    }

    @Test
    void noFees_saysNone() {
        assertEquals("None", plain(List.of()));
    }
}
