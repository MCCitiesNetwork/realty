package io.github.md5sha256.realty.fees;

import io.github.md5sha256.realty.api.AccountKind;
import io.github.md5sha256.realty.api.FeeLine;
import io.github.md5sha256.realty.api.FeeQuote;
import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import io.github.md5sha256.realty.api.Party;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeeCalculatorTest {

    private static final UUID BUYER = UUID.randomUUID();
    private static final Party STATE = Party.account(1, AccountKind.GOVERNMENT);

    private static FeeCalculator.LevyInput levy(double percent) {
        LevyRule rule = new LevyRule(Set.of(), Set.of(), List.of(new LevyTier(0, percent)));
        return new FeeCalculator.LevyInput(
                new LevyDefinition("national-levy", true, true, FeeSide.BUYER, null, null, List.of(rule)), STATE);
    }

    @Test
    void specExample() {
        Party agent = Party.personal(UUID.randomUUID());
        FeeQuote quote = FeeCalculator.quote(1000, BUYER, levy(5), Set.of(), 0,
                List.of(new FeeCalculator.PlayerFeeInput(agent, FeeSide.SELLER, 3)));
        assertEquals(2, quote.lines().size());
        FeeLine levyLine = quote.lines().get(0);
        assertEquals(50.00, levyLine.amount());
        assertEquals(FeeSide.BUYER, levyLine.side());
        assertEquals(STATE, levyLine.recipient());
        FeeLine agentLine = quote.lines().get(1);
        assertEquals(30.00, agentLine.amount());
        assertEquals(FeeSide.SELLER, agentLine.side());
        assertEquals(1050, quote.buyerTotal());
        assertEquals(970, quote.sellerReceives());
    }

    @Test
    void roundsEachFeeDownToTheCent() {
        Party agent = Party.personal(UUID.randomUUID());
        FeeQuote quote = FeeCalculator.quote(333.33, BUYER, null, Set.of(), 0,
                List.of(new FeeCalculator.PlayerFeeInput(agent, FeeSide.SELLER, 3)));
        assertEquals(9.99, quote.lines().get(0).amount());
        assertEquals(323.34, quote.sellerReceives());
    }

    @Test
    void feeToTheBuyerIsSkipped() {
        FeeQuote quote = FeeCalculator.quote(1000, BUYER, null, Set.of(), 0,
                List.of(new FeeCalculator.PlayerFeeInput(Party.personal(BUYER), FeeSide.SELLER, 3)));
        assertTrue(quote.lines().isEmpty());
    }

    @Test
    void sourcesAreLevyNameAndRecipientString() {
        Party agent = Party.personal(UUID.randomUUID());
        FeeQuote quote = FeeCalculator.quote(1000, BUYER, levy(5), Set.of(), 0,
                List.of(new FeeCalculator.PlayerFeeInput(agent, FeeSide.BUYER, 1)));
        assertEquals("national-levy", quote.lines().get(0).source());
        assertEquals(agent.toString(), quote.lines().get(1).source());
    }

    @Test
    void sellerLinesAreCutToThePrice() {
        Party a = Party.personal(UUID.randomUUID());
        Party b = Party.personal(UUID.randomUUID());
        FeeQuote quote = FeeCalculator.quote(100, BUYER, null, Set.of(), 0, List.of(
                new FeeCalculator.PlayerFeeInput(a, FeeSide.SELLER, 70),
                new FeeCalculator.PlayerFeeInput(b, FeeSide.SELLER, 50)));
        assertEquals(70.00, quote.lines().get(0).amount());
        assertEquals(30.00, quote.lines().get(1).amount());
        assertEquals(0, quote.sellerReceives());
    }

    @Test
    void zeroPercentTierGivesNoLine() {
        FeeQuote quote = FeeCalculator.quote(1000, BUYER, levy(0), Set.of(), 0, List.of());
        assertTrue(quote.lines().isEmpty());
        assertEquals(1000, quote.buyerTotal());
    }

    @Test
    void levyPaidToTheBuyerIsSkipped() {
        FeeCalculator.LevyInput toBuyer = new FeeCalculator.LevyInput(levy(5).levy(), Party.personal(BUYER));
        FeeQuote quote = FeeCalculator.quote(1000, BUYER, toBuyer, Set.of(), 0, List.of());
        assertTrue(quote.lines().isEmpty());
        assertEquals(1000, quote.buyerTotal());
        assertEquals(1000, quote.sellerReceives());
    }

    @Test
    void sellerLevyAppliedBeforePlayerFeesWhenCapped() {
        LevyRule rule = new LevyRule(Set.of(), Set.of(), List.of(new LevyTier(0, 80)));
        FeeCalculator.LevyInput sellerLevy = new FeeCalculator.LevyInput(
                new LevyDefinition("seller-levy", true, true, FeeSide.SELLER, null, null, List.of(rule)), STATE);
        Party agent = Party.personal(UUID.randomUUID());
        FeeQuote quote = FeeCalculator.quote(100, BUYER, sellerLevy, Set.of(), 0,
                List.of(new FeeCalculator.PlayerFeeInput(agent, FeeSide.SELLER, 50)));
        assertEquals(80.00, quote.lines().get(0).amount());
        assertEquals(20.00, quote.lines().get(1).amount());
        assertEquals(0, quote.sellerReceives());
    }

    @Test
    void noLevyAndNoFeesIsNone() {
        assertEquals(FeeQuote.none(500), FeeCalculator.quote(500, BUYER, null, Set.of(), 0, List.of()));
    }
}
