package io.github.md5sha256.realty.adapter.playernotifs;

import java.util.List;
import java.util.Map;

/**
 * Builders for the category sets the tests exercise, so each test states only the part it cares
 * about rather than repeating a full five-category declaration.
 */
final class TestCategories {

    private TestCategories() {
    }

    /** A category with a label and title matching its key and no priority. */
    static CategoryDefinition category(String key, String label, String... keys) {
        return new CategoryDefinition(key, label, "", "", 0, List.of(keys));
    }

    /** A representative one-key-per-category set mirroring the shipped defaults. */
    static NotificationCategoryMapper defaults() {
        return new NotificationCategoryMapper(
                List.of(new CategoryDefinition("realty.auction", "Realty auctions",
                                "Bids and outcomes", "Realty — Auction", 1,
                                List.of("notification.outbid")),
                        category("realty.offer", "Realty offers", "notification.offer-placed"),
                        category("realty.lease", "Realty leases", "notification.leasehold-expired"),
                        category("realty.agent", "Realty agents", "notification.agent-invited"),
                        new CategoryDefinition("realty.general", "Realty", "Everything else",
                                "Realty", 0, List.of("notification.region-bought"))),
                Map.of(),
                "realty.general");
    }
}
