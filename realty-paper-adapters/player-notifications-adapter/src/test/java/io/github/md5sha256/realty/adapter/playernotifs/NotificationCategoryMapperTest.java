package io.github.md5sha256.realty.adapter.playernotifs;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class NotificationCategoryMapperTest {

    @Test
    void eachCategoryResolvesFromARepresentativeKey() {
        NotificationCategoryMapper mapper = TestCategories.defaults();

        Assertions.assertEquals("realty.auction", mapper.dataTypeFor("notification.outbid"));
        Assertions.assertEquals("realty.offer", mapper.dataTypeFor("notification.offer-placed"));
        Assertions.assertEquals("realty.lease", mapper.dataTypeFor("notification.leasehold-expired"));
        Assertions.assertEquals("realty.agent", mapper.dataTypeFor("notification.agent-invited"));
        Assertions.assertEquals("realty.general", mapper.dataTypeFor("notification.region-bought"));
    }

    @Test
    void anUnmappedKeyFallsBackToTheFallbackCategory() {
        NotificationCategoryMapper mapper = TestCategories.defaults();

        Assertions.assertEquals("realty.general", mapper.dataTypeFor("notification.some-future-key"));
        Assertions.assertFalse(mapper.isMapped("notification.some-future-key"));
        Assertions.assertTrue(mapper.isMapped("notification.region-bought"));
    }

    @Test
    void aConfigOverrideBeatsTheDefault() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                List.of(TestCategories.category("realty.general", "Realty", "notification.outbid")),
                Map.of(),
                "realty.general");

        Assertions.assertEquals("realty.general", mapper.dataTypeFor("notification.outbid"));
    }

    @Test
    void aTitleOverrideBeatsTheCategoryTitle() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                List.of(new CategoryDefinition("realty.auction", "Realty auctions", "",
                        "Realty — Auction", 0,
                        List.of("notification.outbid", "notification.auction-won"))),
                Map.of("notification.auction-won", "Realty — Auction won"),
                "realty.auction");

        Assertions.assertEquals("Realty — Auction won", mapper.titleFor("notification.auction-won"));
        Assertions.assertEquals("Realty — Auction", mapper.titleFor("notification.outbid"));
    }

    @Test
    void anUnconfiguredTitleAndPriorityFallBack() {
        NotificationCategoryMapper mapper = TestCategories.defaults();

        Assertions.assertEquals("Realty", mapper.titleFor("notification.some-future-key"));
        Assertions.assertEquals(1, mapper.priorityFor("notification.outbid"));
        Assertions.assertEquals(0, mapper.priorityFor("notification.offer-placed"));
    }

    /**
     * A category that declares only a label still gets that label on the notification itself, rather
     * than a generic "Realty" that would tell the player nothing about where it came from.
     */
    @Test
    void aCategoryWithNoTitleRendersUnderItsLabel() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                List.of(TestCategories.category("realty.staff", "Staff alerts", "notification.custom")),
                Map.of(),
                "realty.staff");

        Assertions.assertEquals("Staff alerts", mapper.titleFor("notification.custom"));
    }

    /**
     * The whole point of the config change: an operator-declared category is a first-class data type,
     * so it appears in {@link NotificationCategoryMapper#dataTypes()} and therefore gets registered.
     */
    @Test
    void anOperatorDeclaredCategoryBecomesARegisteredDataType() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                List.of(TestCategories.category("realty.general", "Realty"),
                        new CategoryDefinition("realty.staff", "Staff alerts",
                                "Notifications only staff care about", "Staff", 5,
                                List.of("notification.outbid"))),
                Map.of(),
                "realty.general");

        Assertions.assertEquals(List.of("realty.general", "realty.staff"), mapper.dataTypes());
        Assertions.assertEquals("realty.staff", mapper.dataTypeFor("notification.outbid"));
        Assertions.assertEquals("Staff alerts", mapper.labelFor("realty.staff"));
        Assertions.assertEquals("Notifications only staff care about",
                mapper.descriptionFor("realty.staff"));
        Assertions.assertEquals(5, mapper.priorityFor("notification.outbid"));
    }

    @Test
    void declarationOrderIsPreserved() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                List.of(TestCategories.category("z.last", "Z"),
                        TestCategories.category("a.first", "A"),
                        TestCategories.category("realty.general", "Realty")),
                Map.of(),
                "realty.general");

        Assertions.assertEquals(List.of("z.last", "a.first", "realty.general"), mapper.dataTypes());
    }

    /**
     * An undeclared fallback would be registered nowhere, so every unmapped notification would be
     * enqueued under a data type with no serializer and no renderer, and lost silently.
     */
    @Test
    void anUndeclaredFallbackCategoryIsRejected() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new NotificationCategoryMapper(
                        List.of(TestCategories.category("realty.auction", "Realty auctions")),
                        Map.of(),
                        "realty.general"));

        Assertions.assertTrue(thrown.getMessage().contains("realty.general"), thrown.getMessage());
    }

    @Test
    void aMessageKeyClaimedByTwoCategoriesIsRejected() {
        IllegalArgumentException thrown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new NotificationCategoryMapper(
                        List.of(TestCategories.category("realty.general", "Realty", "notification.outbid"),
                                TestCategories.category("realty.auction", "Auctions", "notification.outbid")),
                        Map.of(),
                        "realty.general"));

        Assertions.assertTrue(thrown.getMessage().contains("notification.outbid"), thrown.getMessage());
    }

    @Test
    void anEmptyCategorySetIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new NotificationCategoryMapper(List.of(), Map.of(), "realty.general"));
    }

    /**
     * A category may exist purely so players can be given a switch for keys the operator has not
     * routed to it yet.
     */
    @Test
    void aCategoryThatClaimsNoKeysIsStillRegistered() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                List.of(TestCategories.category("realty.general", "Realty"),
                        TestCategories.category("realty.spare", "Spare")),
                Map.of(),
                "realty.general");

        Assertions.assertTrue(mapper.dataTypes().contains("realty.spare"));
    }
}
