package io.github.md5sha256.realty.api;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegionNameTest {

    @ParameterizedTest
    @ValueSource(strings = {"plot", "Plot-12", "a", "0-0"})
    void lettersDigitsAndHyphensAreValid(String name) {
        Assertions.assertTrue(WorldGuardRegion.isValidName(name), name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "my plot", "plot_a", "plot!", "plot.a", "pl\u00f6t"})
    void anythingElseIsNot(String name) {
        Assertions.assertFalse(WorldGuardRegion.isValidName(name), name);
    }
}
