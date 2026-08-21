package io.github.md5sha256.realty.command.util;

import org.bukkit.block.Block;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

class SafeLocationFinderTest {

    @Test
    void predicateCanBeReplacedAfterConstruction() {
        Predicate<Block> alwaysUnsafe = block -> false;
        SafeLocationFinder finder = new SafeLocationFinder(alwaysUnsafe);

        Assertions.assertSame(alwaysUnsafe, finder.safetyPredicate());

        Predicate<Block> alwaysSafe = block -> true;
        finder.setSafetyPredicate(alwaysSafe);

        Assertions.assertSame(alwaysSafe, finder.safetyPredicate());
    }

    @Test
    void defaultPredicateIsUsedWhenNoneSupplied() {
        SafeLocationFinder finder = new SafeLocationFinder();

        Assertions.assertNotNull(finder.safetyPredicate());
    }
}
