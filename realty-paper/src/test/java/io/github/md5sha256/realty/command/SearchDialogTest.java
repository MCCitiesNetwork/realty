package io.github.md5sha256.realty.command;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

class SearchDialogTest {

    @Nested
    @DisplayName("buildResultsCommand")
    class BuildResultsCommand {

        @Test
        @DisplayName("includes exclude-rented flag when requested")
        void includesExcludeRentedFlag() {
            String command = SearchDialog.buildResultsCommand(
                    true, true, List.of("farm", "port"), List.of("vip"),
                    true, 25.0, 250.0, 3);

            Assertions.assertTrue(command.contains(" --exclude-rented"));
            Assertions.assertTrue(command.contains(" --freehold"));
            Assertions.assertTrue(command.contains(" --leasehold"));
            Assertions.assertTrue(command.contains(" --tags farm,port"));
            Assertions.assertTrue(command.contains(" --exclude-tags vip"));
            Assertions.assertTrue(command.endsWith(" --page 3"));
        }

        @Test
        @DisplayName("omits exclude-rented flag by default")
        void omitsExcludeRentedFlag() {
            String command = SearchDialog.buildResultsCommand(
                    false, true, null, null,
                    false, 0.0, Double.MAX_VALUE, 1);

            Assertions.assertFalse(command.contains(" --exclude-rented"));
            Assertions.assertEquals("/realty search results --leasehold --page 1", command);
        }
    }
}
