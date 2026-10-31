package io.github.md5sha256.realty.command.util;

import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.Party;
import io.github.md5sha256.realty.localisation.MessageContainer;
import net.kyori.adventure.text.Component;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The breakdown shown before a bid, offer or buy, rendered from the shipped messages.yml. */
class FeeQuoteFormatterTest {

    private static final MessageContainer MESSAGES = new MessageContainer();

    @BeforeAll
    static void loadShippedMessages() throws IOException {
        try (InputStream in = FeeQuoteFormatterTest.class.getResourceAsStream("/messages.yml")) {
            assertNotNull(in, "messages.yml is missing from the plugin resources");
            MESSAGES.load(YamlConfigurationLoader.builder()
                    .source(() -> new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
                    .build()
                    .load());
        }
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void specExample_rendersPriceLevyTotalAndSellerLine() {
        Party government = Party.personal(UUID.randomUUID());
        Party agent = Party.personal(UUID.randomUUID());
        FeeQuote quote = new FeeQuote(1000, List.of(
                new FeeLine("townville-levy", government, FeeSide.BUYER, 5, 50),
                new FeeLine("agent fee", agent, FeeSide.SELLER, 3, 30)),
                1050, 970);

        String text = plain(FeeQuoteFormatter.breakdown(quote, 4, MESSAGES,
                party -> party.equals(agent) ? "Servalot" : "Townville"));

        assertTrue(text.contains("1,000.00"), text);
        assertTrue(text.contains("50.00  (5%, you hold 4 plots)"), text);
        assertTrue(text.contains("1,050.00"), text);
        assertTrue(text.contains("agent fee 30.00 (3%, to "), text);
        assertTrue(text.contains("Servalot"), text);
    }

    @Test
    void playerFeePaidByBuyer_hasNoPlotCount() {
        Party agent = Party.personal(UUID.randomUUID());
        FeeQuote quote = new FeeQuote(200, List.of(
                new FeeLine("agent fee", agent, FeeSide.BUYER, 2.5, 5)), 205, 200);

        String text = plain(FeeQuoteFormatter.breakdown(quote, 1, MESSAGES, party -> "Servalot"));

        assertTrue(text.contains("5.00  (2.5%)"), text);
        assertTrue(!text.contains("you hold"), text);
        assertTrue(!text.contains("The seller also pays"), text);
    }

    @Test
    void oneLevyPlot_isSingular() {
        FeeQuote quote = new FeeQuote(100, List.of(
                new FeeLine("national", Party.personal(UUID.randomUUID()), FeeSide.BUYER, 5, 5)), 105, 100);

        String text = plain(FeeQuoteFormatter.breakdown(quote, 1, MESSAGES, party -> "x"));

        assertTrue(text.contains("you hold 1 plot)"), text);
    }

    @Test
    void noLines_isEmpty() {
        assertEquals(Component.empty(),
                FeeQuoteFormatter.breakdown(FeeQuote.none(1000), 4, MESSAGES, party -> "x"));
    }

    @Test
    void playerFeeSources_areTheRoleLabels_everyOtherSourceIsALevy() {
        Party anyone = Party.personal(UUID.randomUUID());
        for (String role : FeeLine.PLAYER_FEE_ROLES) {
            assertTrue(!new FeeLine(FeeLine.playerFeeSource(role), anyone, FeeSide.SELLER, 1, 1).isLevy(), role);
        }
        assertEquals("agent fee", FeeLine.playerFeeSource("agent"));
        assertTrue(new FeeLine("townville-levy", anyone, FeeSide.BUYER, 1, 1).isLevy());
    }

    @Test
    void offerPreview_listsEachApprovedAgentFee_asApplyingIfThatAgentAccepts() {
        Party agent = Party.personal(UUID.randomUUID());
        List<io.github.md5sha256.realty.api.RealtyBackend.FeeListing> fees = List.of(
                new io.github.md5sha256.realty.api.RealtyBackend.FeeListing("townville-levy", null, FeeSide.BUYER,
                        "5%", true, null),
                new io.github.md5sha256.realty.api.RealtyBackend.FeeListing("agent fee", agent, FeeSide.SELLER,
                        "3%", true, "agent"),
                new io.github.md5sha256.realty.api.RealtyBackend.FeeListing("agent fee",
                        Party.personal(UUID.randomUUID()), FeeSide.SELLER, "4%", false, "agent"),
                new io.github.md5sha256.realty.api.RealtyBackend.FeeListing("titleholder fee", agent, FeeSide.BUYER,
                        "1%", true, "titleholder"));

        var agentFees = FeePreview.approvedAgentFees(fees);

        assertEquals(1, agentFees.size(), agentFees.toString());
        assertEquals(agent, agentFees.getFirst().recipient());
        String line = plain(MESSAGES.messageFor(io.github.md5sha256.realty.localisation.MessageKeys.FEES_BREAKDOWN_AGENT_IF_ACCEPTS,
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("agent", "Servalot"),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("side", "seller"),
                net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.unparsed("percent", "3%")));
        assertEquals("  If Servalot accepts, their seller-paid fee of 3% applies.", line);
    }
}
