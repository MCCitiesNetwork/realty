package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.rest.json.WorldRef;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves worlds by name or UUID against the {@code RealtyWorld} table.
 *
 * <p>Reads the database on every call. Worlds are a handful of rows and the query
 * is a primary-key or indexed lookup; caching is not worth the staleness until
 * something measures a need.</p>
 */
public final class WorldLookup {

    private final Database database;

    public WorldLookup(@NotNull Database database) {
        this.database = database;
    }

    public @NotNull List<WorldRef> all() {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            List<WorldRef> refs = new ArrayList<>();
            for (RealtyWorldEntity entity : session.realtyWorldMapper().selectAll()) {
                refs.add(new WorldRef(entity.worldId().toString(), entity.worldName()));
            }
            return refs;
        }
    }

    /**
     * Accepts a world UUID or a world name. A UUID is returned as-is without a
     * database round trip, so a lookup by UUID works even for a world the table
     * has never seen.
     */
    public @NotNull UUID resolve(@NotNull String worldNameOrUuid) {
        try {
            return UUID.fromString(worldNameOrUuid);
        } catch (IllegalArgumentException ignored) {
            // Not a UUID, so treat it as a name.
        }
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            RealtyWorldEntity entity = session.realtyWorldMapper().selectByName(worldNameOrUuid);
            if (entity == null) {
                throw ApiException.notFound("WORLD_NOT_FOUND",
                        "No world named '" + worldNameOrUuid + "'");
            }
            return entity.worldId();
        }
    }

    public @NotNull WorldRef refFor(@NotNull UUID worldId) {
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            RealtyWorldEntity entity = session.realtyWorldMapper().selectById(worldId);
            return new WorldRef(worldId.toString(), entity == null ? null : entity.worldName());
        }
    }

    /**
     * Resolves several worlds in a single session/query, for a handler building a
     * list of regions -- one {@link #refFor} call per region would open one session
     * per region. Worlds are a handful of rows, so a single {@code selectAll()}
     * filtered in memory is simpler than a parameterised IN-list query and just as
     * cheap. A {@code worldId} absent from the table still yields an entry with a
     * null name, exactly as {@link #refFor} does for a single lookup.
     */
    public @NotNull Map<UUID, WorldRef> refsFor(@NotNull Collection<UUID> worldIds) {
        Map<UUID, WorldRef> result = new HashMap<>();
        if (worldIds.isEmpty()) {
            return result;
        }
        try (SqlSessionWrapper session = this.database.openSession(true)) {
            Map<UUID, String> names = new HashMap<>();
            for (RealtyWorldEntity entity : session.realtyWorldMapper().selectAll()) {
                names.put(entity.worldId(), entity.worldName());
            }
            for (UUID worldId : worldIds) {
                result.put(worldId, new WorldRef(worldId.toString(), names.get(worldId)));
            }
        }
        return result;
    }

}
