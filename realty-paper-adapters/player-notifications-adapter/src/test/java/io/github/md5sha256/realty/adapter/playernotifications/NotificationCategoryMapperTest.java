package io.github.md5sha256.realty.adapter.playernotifications;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

class NotificationCategoryMapperTest {

    private static NotificationCategoryMapper defaults() {
        return new NotificationCategoryMapper(
                Map.of("notification.outbid", "realty.auction",
                        "notification.offer-placed", "realty.offer",
                        "notification.leasehold-expired", "realty.lease",
                        "notification.agent-invited", "realty.agent",
                        "notification.region-bought", "realty.general"),
                Map.of("realty.auction", "Realty — Auction",
                        "realty.general", "Realty"),
                Map.of(),
                Map.of("realty.auction", 1));
    }

    @Test
    void eachCategoryResolvesFromARepresentativeKey() {
        NotificationCategoryMapper mapper = defaults();

        Assertions.assertEquals("realty.auction", mapper.dataTypeFor("notification.outbid"));
        Assertions.assertEquals("realty.offer", mapper.dataTypeFor("notification.offer-placed"));
        Assertions.assertEquals("realty.lease", mapper.dataTypeFor("notification.leasehold-expired"));
        Assertions.assertEquals("realty.agent", mapper.dataTypeFor("notification.agent-invited"));
        Assertions.assertEquals("realty.general", mapper.dataTypeFor("notification.region-bought"));
    }

    @Test
    void anUnmappedKeyFallsBackToGeneral() {
        NotificationCategoryMapper mapper = defaults();

        Assertions.assertEquals("realty.general", mapper.dataTypeFor("notification.some-future-key"));
        Assertions.assertFalse(mapper.isMapped("notification.some-future-key"));
        Assertions.assertTrue(mapper.isMapped("notification.region-bought"));
    }

    @Test
    void aConfigOverrideBeatsTheDefault() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                Map.of("notification.outbid", "realty.general"),
                Map.of("realty.auction", "Realty — Auction", "realty.general", "Realty"),
                Map.of(),
                Map.of());

        Assertions.assertEquals("realty.general", mapper.dataTypeFor("notification.outbid"));
    }

    @Test
    void aTitleOverrideBeatsTheDataTypeTitle() {
        NotificationCategoryMapper mapper = new NotificationCategoryMapper(
                Map.of("notification.outbid", "realty.auction",
                        "notification.auction-won", "realty.auction"),
                Map.of("realty.auction", "Realty — Auction"),
                Map.of("notification.auction-won", "Realty — Auction won"),
                Map.of());

        Assertions.assertEquals("Realty — Auction won", mapper.titleFor("notification.auction-won"));
        Assertions.assertEquals("Realty — Auction", mapper.titleFor("notification.outbid"));
    }

    @Test
    void anUnconfiguredTitleAndPriorityFallBack() {
        NotificationCategoryMapper mapper = defaults();

        Assertions.assertEquals("Realty", mapper.titleFor("notification.some-future-key"));
        Assertions.assertEquals(1, mapper.priorityFor("notification.outbid"));
        Assertions.assertEquals(0, mapper.priorityFor("notification.offer-placed"));
    }
}
