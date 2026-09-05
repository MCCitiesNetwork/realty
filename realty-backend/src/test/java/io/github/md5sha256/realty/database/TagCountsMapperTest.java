package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.TagCountEntity;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

/**
 * One statement for every tag's count, in place of a query per tag.
 */
class TagCountsMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000005");
    private static final UUID AUTHORITY = UUID.fromString("3a1c88f0-0000-0000-0000-000000000030");

    private static List<TagCountEntity> tagCounts() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            return session.regionTagMapper().selectTagCounts();
        }
    }

    @Test
    void reportsEveryTagInUseWithItsCount() {
        Assertions.assertTrue(logic.createFreehold("tags_a", WORLD_ID, 100.0, AUTHORITY, null));
        Assertions.assertTrue(logic.createFreehold("tags_b", WORLD_ID, 100.0, AUTHORITY, null));
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.regionTagMapper().insert("commercial", "tags_a");
            wrapper.regionTagMapper().insert("commercial", "tags_b");
            wrapper.regionTagMapper().insert("waterfront", "tags_b");
            session.commit();
        }

        List<TagCountEntity> counts = tagCounts();

        Assertions.assertEquals(List.of(
                new TagCountEntity("commercial", 2),
                new TagCountEntity("waterfront", 1)), counts);
    }

    @Test
    void agreesWithCountingEachTagSeparately() {
        Assertions.assertTrue(logic.createFreehold("tags_c", WORLD_ID, 100.0, AUTHORITY, null));
        try (SqlSessionWrapper wrapper = database.openSession();
             SqlSession session = wrapper.session()) {
            wrapper.regionTagMapper().insert("island", "tags_c");
            session.commit();
        }

        for (TagCountEntity count : tagCounts()) {
            Assertions.assertEquals(logic.countRegionsByTag(count.tagId()), count.regionCount(), count.tagId());
        }
        Assertions.assertEquals(logic.getAllTagIds().size(), tagCounts().size());
    }

    @Test
    void nothingTaggedIsAnEmptyList() {
        Assertions.assertEquals(List.of(), tagCounts());
    }
}
