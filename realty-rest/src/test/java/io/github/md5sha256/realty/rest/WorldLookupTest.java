package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.rest.json.WorldRef;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class WorldLookupTest {

    private static final RealtyWorldEntity WORLD = new RealtyWorldEntity(UUID.randomUUID(), "world");
    private static final RealtyWorldEntity MY_WORLD = new RealtyWorldEntity(UUID.randomUUID(), "My World");

    @Test
    void resolveReturnsAUuidStringDirectlyWithoutQueryingTheDatabase() {
        WorldLookup lookup = new WorldLookup(
                TestServers.databaseThatFailsIfSelectByNameCalled(List.of(WORLD, MY_WORLD)));
        UUID resolved = lookup.resolve(WORLD.worldId().toString());
        Assertions.assertEquals(WORLD.worldId(), resolved);
    }

    @Test
    void resolveReturnsAUuidEvenForAWorldAbsentFromTheTable() {
        WorldLookup lookup = new WorldLookup(
                TestServers.databaseThatFailsIfSelectByNameCalled(List.of()));
        UUID unknown = UUID.randomUUID();
        UUID resolved = lookup.resolve(unknown.toString());
        Assertions.assertEquals(unknown, resolved);
    }

    @Test
    void resolveFindsAKnownWorldByName() {
        WorldLookup lookup = new WorldLookup(TestServers.databaseWithWorlds(List.of(WORLD, MY_WORLD)));
        UUID resolved = lookup.resolve("world");
        Assertions.assertEquals(WORLD.worldId(), resolved);
    }

    @Test
    void resolveFindsAKnownWorldByNameContainingASpace() {
        WorldLookup lookup = new WorldLookup(TestServers.databaseWithWorlds(List.of(WORLD, MY_WORLD)));
        UUID resolved = lookup.resolve("My World");
        Assertions.assertEquals(MY_WORLD.worldId(), resolved);
    }

    @Test
    void resolveThrowsNotFoundForAnUnknownName() {
        WorldLookup lookup = new WorldLookup(TestServers.databaseWithWorlds(List.of(WORLD, MY_WORLD)));
        ApiException exception = Assertions.assertThrows(ApiException.class,
                () -> lookup.resolve("nonexistent"));
        Assertions.assertEquals(404, exception.status());
        Assertions.assertEquals("WORLD_NOT_FOUND", exception.code());
    }

    @Test
    void refForReturnsAPopulatedRefForAKnownWorld() {
        WorldLookup lookup = new WorldLookup(TestServers.databaseWithWorlds(List.of(WORLD, MY_WORLD)));
        WorldRef ref = lookup.refFor(WORLD.worldId());
        Assertions.assertEquals(WORLD.worldId().toString(), ref.id());
        Assertions.assertEquals("world", ref.name());
    }

    @Test
    void refForReturnsANullNameForAnUnknownWorldWithoutThrowing() {
        WorldLookup lookup = new WorldLookup(TestServers.databaseWithWorlds(List.of()));
        UUID unknown = UUID.randomUUID();
        WorldRef ref = lookup.refFor(unknown);
        Assertions.assertEquals(unknown.toString(), ref.id());
        Assertions.assertNull(ref.name());
    }

}
