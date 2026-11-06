package io.github.md5sha256.realty.fees;

import io.github.md5sha256.realty.api.FeeSide;
import io.github.md5sha256.realty.api.LevyDefinition;
import io.github.md5sha256.realty.api.LevyRule;
import io.github.md5sha256.realty.api.LevyTier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevyDefinitionTest {

    private static LevyDefinition levy(boolean enabled) {
        LevyRule rule = new LevyRule(Set.of(), Set.of("Residential"),
                List.of(new LevyTier(0, 0), new LevyTier(3, 5), new LevyTier(10, 15)));
        return new LevyDefinition("stamp", enabled, false, FeeSide.BUYER, null, null, List.of(rule));
    }

    @Test
    void percentFor_firstMatchingRuleAndHighestTier() {
        LevyDefinition levy = levy(true);
        assertEquals(5.0, levy.percentFor(Set.of("residential"), 4).getAsDouble());
        assertEquals(15.0, levy.percentFor(Set.of("residential"), 10).getAsDouble());
        assertTrue(levy.percentFor(Set.of("commercial"), 4).isEmpty());
    }

    @Test
    void percentFor_disabled_isEmpty() {
        assertTrue(levy(false).percentFor(Set.of("residential"), 4).isEmpty());
    }

    @Test
    void levyRule_matchesLikeTagMatch() {
        LevyRule all = new LevyRule(Set.of("A", "b"), Set.of(), List.of());
        assertTrue(all.matches(Set.of("a", "b", "c")));
        assertFalse(all.matches(Set.of("a")));
        LevyRule any = new LevyRule(Set.of(), Set.of("a", "b"), List.of());
        assertTrue(any.matches(Set.of("b")));
        assertFalse(any.matches(Set.of("c")));
        LevyRule none = new LevyRule(Set.of(), Set.of(), List.of());
        assertTrue(none.matches(Set.of()));
        assertTrue(none.matches(Set.of("x")));
    }
}
