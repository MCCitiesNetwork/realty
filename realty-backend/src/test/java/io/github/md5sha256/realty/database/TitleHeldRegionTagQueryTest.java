package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.TitleHeldRegionTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@DisplayName("FreeholdContractMapper.selectTitleHeldRegionTags")
class TitleHeldRegionTagQueryTest extends AbstractDatabaseTest {

    private static String uniqueRegion(String suffix) {
        return "thrt_" + UUID.randomUUID().toString().substring(0, 8) + "_" + suffix;
    }

    @Test
    @DisplayName("returns each title-held region with its tag set; untagged regions yield a null-tag row")
    void enumeratesRegionsAndTags() {
        UUID world = UUID.randomUUID();
        UUID authority = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        String regionA = uniqueRegion("a");
        String regionB = uniqueRegion("b");

        Assertions.assertTrue(logic.createFreehold(regionA, world, 100.0, authority, owner));
        Assertions.assertTrue(logic.createFreehold(regionB, world, 100.0, authority, owner));

        try (SqlSessionWrapper session = database.openSession(true)) {
            session.regionTagMapper().insert("commercial", regionA);
            session.regionTagMapper().insert("industrial", regionA);
            // regionB intentionally left untagged
        }

        Map<String, Set<String>> tagsByRegion = new HashMap<>();
        try (SqlSessionWrapper session = database.openSession(true)) {
            for (TitleHeldRegionTag row : session.freeholdContractMapper().selectTitleHeldRegionTags()) {
                Assertions.assertEquals(owner, row.titleHolderId());
                Set<String> tags = tagsByRegion.computeIfAbsent(row.worldGuardRegionId(), k -> new HashSet<>());
                if (row.tagId() != null) {
                    tags.add(row.tagId());
                }
            }
        }

        Assertions.assertEquals(Set.of(regionA, regionB), tagsByRegion.keySet());
        Assertions.assertEquals(Set.of("commercial", "industrial"), tagsByRegion.get(regionA));
        Assertions.assertTrue(tagsByRegion.get(regionB).isEmpty(),
                "untagged region should appear with no tags (single null-tag row)");
    }

    @Test
    @DisplayName("excludes freeholds with no title holder")
    void excludesUnownedFreeholds() {
        UUID world = UUID.randomUUID();
        UUID authority = UUID.randomUUID();
        String region = uniqueRegion("unowned");

        // Freehold with a null title holder (for sale, not owned) must not be taxed.
        Assertions.assertTrue(logic.createFreehold(region, world, 100.0, authority, null));

        try (SqlSessionWrapper session = database.openSession(true)) {
            boolean present = session.freeholdContractMapper().selectTitleHeldRegionTags().stream()
                    .anyMatch(r -> r.worldGuardRegionId().equals(region));
            Assertions.assertFalse(present, "untitled freehold should be excluded");
        }
    }
}
