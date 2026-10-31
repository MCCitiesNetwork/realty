package io.github.md5sha256.realty.command;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevyCommandGroupTest {

    private static LevyDefinition levy(List<LevyRule> rules) {
        return new LevyDefinition("townville-levy", true, false, FeeSide.BUYER, null, null, rules);
    }

    @Test
    void tiersAcrossRules_showTheWholeRange() {
        LevyDefinition levy = levy(List.of(
                new LevyRule(Set.of(), Set.of("residential"), List.of(new LevyTier(0, 2), new LevyTier(5, 8))),
                new LevyRule(Set.of(), Set.of(), List.of(new LevyTier(0, 2.5)))));

        assertEquals("2–8% by plots held", LevyCommandGroup.rateRange(levy));
    }

    @Test
    void oneRate_isShownAlone() {
        LevyDefinition levy = levy(List.of(new LevyRule(Set.of(), Set.of(), List.of(new LevyTier(0, 5)))));

        assertEquals("5%", LevyCommandGroup.rateRange(levy));
    }

    @Test
    void noTiers_saysSo() {
        assertEquals("no rate", LevyCommandGroup.rateRange(levy(List.of())));
    }

    @Test
    void change_withoutAuthority_isRefused_evenForAdmins() {
        org.junit.jupiter.api.Assertions.assertFalse(LevyCommandGroup.mayChange(false),
                "one plot or all, with or without the bypass, a levy change takes --authority");
        org.junit.jupiter.api.Assertions.assertTrue(LevyCommandGroup.mayChange(true));
    }
}
