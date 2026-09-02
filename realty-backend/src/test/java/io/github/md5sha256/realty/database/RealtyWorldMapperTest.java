package io.github.md5sha256.realty.database;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class RealtyWorldMapperTest extends AbstractDatabaseTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000001");

    @Test
    void upsertThenSelectByIdReturnsTheWorld() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyWorldMapper().upsert(WORLD_ID, "world_nether");
            RealtyWorldEntity found = session.realtyWorldMapper().selectById(WORLD_ID);
            Assertions.assertNotNull(found);
            Assertions.assertEquals(WORLD_ID, found.worldId());
            Assertions.assertEquals("world_nether", found.worldName());
        }
    }

    @Test
    void upsertTwiceUpdatesTheNameRatherThanInserting() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyWorldMapper().upsert(WORLD_ID, "old_name");
            session.realtyWorldMapper().upsert(WORLD_ID, "new_name");
            List<RealtyWorldEntity> all = session.realtyWorldMapper().selectAll();
            Assertions.assertEquals(1, all.size());
            Assertions.assertEquals("new_name", all.getFirst().worldName());
        }
    }

    @Test
    void selectByNameFindsAWorldWithSpacesInItsName() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            session.realtyWorldMapper().upsert(WORLD_ID, "My World");
            RealtyWorldEntity found = session.realtyWorldMapper().selectByName("My World");
            Assertions.assertNotNull(found);
            Assertions.assertEquals(WORLD_ID, found.worldId());
        }
    }

    @Test
    void selectByNameReturnsNullForAnUnknownName() {
        try (SqlSessionWrapper session = database.openSession(true)) {
            Assertions.assertNull(session.realtyWorldMapper().selectByName("nope"));
        }
    }
}
