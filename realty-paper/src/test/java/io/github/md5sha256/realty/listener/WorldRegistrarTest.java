package io.github.md5sha256.realty.listener;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class WorldRegistrarTest {

    private static final class RecordingMapper implements RealtyWorldMapper {

        private final Map<UUID, String> rows = new LinkedHashMap<>();

        @Override
        public void upsert(UUID worldId, String worldName) {
            this.rows.put(worldId, worldName);
        }

        @Override
        public List<RealtyWorldEntity> selectAll() {
            List<RealtyWorldEntity> all = new ArrayList<>();
            this.rows.forEach((id, name) -> all.add(new RealtyWorldEntity(id, name)));
            return all;
        }

        @Override
        public RealtyWorldEntity selectById(UUID worldId) {
            String name = this.rows.get(worldId);
            return name == null ? null : new RealtyWorldEntity(worldId, name);
        }

        @Override
        public RealtyWorldEntity selectByName(String worldName) {
            return null;
        }
    }

    @Test
    void syncAllUpsertsEveryWorld() {
        RecordingMapper mapper = new RecordingMapper();
        UUID overworld = UUID.randomUUID();
        UUID nether = UUID.randomUUID();

        WorldRegistrar.syncAll(mapper, Map.of(overworld, "world", nether, "world_nether"));

        Assertions.assertEquals(2, mapper.selectAll().size());
        Assertions.assertEquals("world", mapper.selectById(overworld).worldName());
        Assertions.assertEquals("world_nether", mapper.selectById(nether).worldName());
    }

    @Test
    void syncAllOverwritesARenamedWorld() {
        RecordingMapper mapper = new RecordingMapper();
        UUID overworld = UUID.randomUUID();

        WorldRegistrar.syncAll(mapper, Map.of(overworld, "old_name"));
        WorldRegistrar.syncAll(mapper, Map.of(overworld, "new_name"));

        Assertions.assertEquals(1, mapper.selectAll().size());
        Assertions.assertEquals("new_name", mapper.selectById(overworld).worldName());
    }

    @Test
    void syncAllAcceptsAWorldNameContainingSpaces() {
        RecordingMapper mapper = new RecordingMapper();
        UUID id = UUID.randomUUID();

        WorldRegistrar.syncAll(mapper, Map.of(id, "My World"));

        Assertions.assertEquals("My World", mapper.selectById(id).worldName());
    }
}
