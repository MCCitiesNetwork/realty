package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.entity.RegionStateRow;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.RegionMembers;
import io.github.md5sha256.realty.rest.module.RegionsAt;
import io.javalin.testtools.JavalinTest;
import io.javalin.testtools.Response;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The three routes that cross both halves of the seam: the database says which regions Realty
 * manages, the query-service module says where they are and who is on them.
 */
class RegionQueryRoutesTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    private static final UUID ALICE = UUID.fromString("aaaa0000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("bbbb0000-0000-0000-0000-0000000000b2");

    private static final List<RealtyWorldEntity> WORLDS =
            List.of(new RealtyWorldEntity(WORLD_ID, "world"));

    /** Realty manages plot_a and plot_b. It does not manage "spawn", which WorldGuard does. */
    private static final List<RegionStateRow> REGISTERED = List.of(
            new RegionStateRow(1, "plot_a", WORLD_ID, "FOR_SALE"),
            new RegionStateRow(2, "plot_b", WORLD_ID, null));

    private static final RegionResponse.Dimensions PLOT_A = new RegionResponse.Dimensions(
            "CUBOID", 0, 63, 0,
            List.of(new RegionResponse.Point(0, 0), new RegionResponse.Point(15, 15)));

    private static final RegionMembers MEMBERS = new RegionMembers(
            new RegionMembers.Party(List.of(ALICE), List.of(), List.of("staff")),
            new RegionMembers.Party(List.of(BOB), List.of("legacyname"), List.of()));

    private static ModuleClient module(RegionsAt column, RegionsAt point, RegionMembers members) {
        return TestServers.regionQueryModule(Map.of(ALICE, "Alice", BOB, "Bob"),
                Map.of("plot_a", PLOT_A), column, point, members);
    }

    private static ModuleClient standardModule() {
        return module(new RegionsAt("column", List.of("plot_a", "spawn", "plot_b")),
                new RegionsAt("point", List.of("plot_a")),
                MEMBERS);
    }

    private static RealtyRestServer server(ModuleClient module) {
        return TestServers.withAllRegionsAndModule(REGISTERED, WORLDS, module);
    }

    /** Counts the readings of WorldGuard a request costs, which is what the cache exists to cut. */
    private static final class CountingModule implements ModuleClient {

        private final ModuleClient delegate;
        private final AtomicInteger readings = new AtomicInteger();

        private CountingModule(ModuleClient delegate) {
            this.delegate = delegate;
        }

        @Override
        public @org.jetbrains.annotations.NotNull Map<String, RegionResponse.Dimensions> dimensionsOf(
                @org.jetbrains.annotations.NotNull UUID worldId,
                @org.jetbrains.annotations.NotNull Collection<String> regionIds) {
            this.readings.incrementAndGet();
            return this.delegate.dimensionsOf(worldId, regionIds);
        }

        @Override
        public @org.jetbrains.annotations.NotNull Optional<RegionResponse.Dimensions> dimensions(
                @org.jetbrains.annotations.NotNull UUID worldId,
                @org.jetbrains.annotations.NotNull String regionId) {
            return this.delegate.dimensions(worldId, regionId);
        }

        @Override
        public @org.jetbrains.annotations.NotNull Map<UUID, String> names(
                @org.jetbrains.annotations.NotNull Collection<UUID> ids) {
            return this.delegate.names(ids);
        }

        @Override
        public @org.jetbrains.annotations.NotNull io.github.md5sha256.realty.rest.module.NameLookup uuidOf(
                @org.jetbrains.annotations.NotNull String name) {
            return this.delegate.uuidOf(name);
        }

        @Override
        public @org.jetbrains.annotations.NotNull io.github.md5sha256.realty.rest.module.ModuleResult<RegionsAt> regionsAt(
                @org.jetbrains.annotations.NotNull UUID worldId, int x,
                @org.jetbrains.annotations.Nullable Integer y, int z) {
            return this.delegate.regionsAt(worldId, x, y, z);
        }

        @Override
        public @org.jetbrains.annotations.NotNull io.github.md5sha256.realty.rest.module.ModuleResult<RegionMembers> members(
                @org.jetbrains.annotations.NotNull UUID worldId,
                @org.jetbrains.annotations.NotNull String regionId) {
            return this.delegate.members(worldId, regionId);
        }

        @Override
        public @org.jetbrains.annotations.NotNull io.github.md5sha256.realty.rest.module.ModuleResult<io.github.md5sha256.realty.rest.module.ResourcePack> resourcePack() {
            return this.delegate.resourcePack();
        }

        @Override
        public @org.jetbrains.annotations.NotNull Status status() {
            return this.delegate.status();
        }
    }

    /** Waits for the reading taken behind the request, which runs on a thread of its own. */
    private static void settle(CountingModule module) throws InterruptedException {
        int seen = -1;
        for (int attempt = 0; attempt < 100 && seen != module.readings.get(); attempt++) {
            seen = module.readings.get();
            Thread.sleep(20);
        }
    }

    // ---- /v1/regions/at -------------------------------------------------------------------

    @Test
    void omittingYRunsAColumnTestAndSaysWhichTestRan() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/regions/at?world=world&x=8&z=8");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"test\":\"column\""), body);
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_a\""), body);
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_b\""), body);
        });
    }

    @Test
    void givingYRunsAPointTest() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/regions/at?world=world&x=8&y=30&z=8");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"test\":\"point\""), body);
            Assertions.assertFalse(body.contains("plot_b"),
                    "plot_b covers the column but not the block: " + body);
        });
    }

    @Test
    void dropsAWorldGuardRegionRealtyDoesNotManage() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            String body = client.get("/v1/regions/at?world=world&x=8&z=8").body().string();
            Assertions.assertFalse(body.contains("spawn"),
                    "an unregistered WorldGuard region must not reach the response: " + body);
        });
    }

    @Test
    void reportsRegionsInWorldGuardsOwnOrder() {
        // The database filter is free to reorder; the response must not, because the order is
        // the order the server itself applies the regions in.
        ModuleClient module = module(new RegionsAt("column", List.of("plot_b", "plot_a")),
                null, MEMBERS);
        JavalinTest.test(server(module).javalin(), (s, client) -> {
            String body = client.get("/v1/regions/at?world=world&x=8&z=8").body().string();
            Assertions.assertTrue(body.indexOf("plot_b") < body.indexOf("plot_a"), body);
        });
    }

    @Test
    void aBlockInNoRegisteredRegionIsAnEmptyListNotA404() {
        ModuleClient module = module(new RegionsAt("column", List.of("spawn")), null, MEMBERS);
        JavalinTest.test(server(module).javalin(), (s, client) -> {
            Response response = client.get("/v1/regions/at?world=world&x=8&z=8");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals("{\"test\":\"column\",\"regions\":[]}", response.body().string());
        });
    }

    @Test
    void aMissingOrNonIntegerCoordinateIs400() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            for (String query : new String[]{"world=world&x=8", "world=world&z=8",
                    "world=world&x=1.5&z=8", "world=world&x=8&z=8&y=sixty"}) {
                Response response = client.get("/v1/regions/at?" + query);
                Assertions.assertEquals(400, response.code(), query);
                Assertions.assertTrue(response.body().string().contains("INVALID_COORDINATE"), query);
            }
        });
    }

    @Test
    void anEmptyYAsksTheColumnQuestionRatherThanFailing() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/regions/at?world=world&x=8&z=8&y=");
            Assertions.assertEquals(200, response.code());
            Assertions.assertTrue(response.body().string().contains("\"test\":\"column\""));
        });
    }

    @Test
    void anUnknownWorldIs404BeforeTheModuleIsAsked() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/regions/at?world=nether&x=8&z=8");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("WORLD_NOT_FOUND"));
        });
    }

    @Test
    void anUnreachableModuleIs502RatherThanAnEmptyAnswer() {
        JavalinTest.test(server(TestServers.unreachableModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/regions/at?world=world&x=8&z=8");
            Assertions.assertEquals(502, response.code());
            Assertions.assertTrue(response.body().string().contains("GEOMETRY_UNAVAILABLE"),
                    "an empty list would claim the block is in no region, which is a different fact");
        });
    }

    // ---- /v1/region/members ---------------------------------------------------------------

    @Test
    void membersKeepsWorldGuardsThreeKindsOfEntryApart() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/region/members?world=world&region=plot_a");
            Assertions.assertEquals(200, response.code());
            Assertions.assertEquals(
                    "{\"owners\":{\"players\":[{\"id\":\"" + ALICE + "\",\"name\":\"Alice\"}],"
                            + "\"playerNames\":[],\"groups\":[\"staff\"]},"
                            + "\"members\":{\"players\":[{\"id\":\"" + BOB + "\",\"name\":\"Bob\"}],"
                            + "\"playerNames\":[\"legacyname\"],\"groups\":[]}}",
                    response.body().string());
        });
    }

    @Test
    void membersOfARegionRealtyDoesNotManageIs404() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/region/members?world=world&region=spawn");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("REGION_NOT_FOUND"));
        });
    }

    @Test
    void membersIs404WhenWorldGuardNoLongerHoldsARegisteredRegion() {
        JavalinTest.test(server(module(null, null, null)).javalin(), (s, client) -> {
            Response response = client.get("/v1/region/members?world=world&region=plot_a");
            Assertions.assertEquals(404, response.code());
            Assertions.assertTrue(response.body().string().contains("REGION_NOT_FOUND"));
        });
    }

    @Test
    void membersOnAnUnreachableModuleIs502() {
        JavalinTest.test(server(TestServers.unreachableModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/region/members?world=world&region=plot_a");
            Assertions.assertEquals(502, response.code());
            Assertions.assertTrue(response.body().string().contains("MEMBERS_UNAVAILABLE"));
        });
    }

    @Test
    void membersRequiresBothParameters() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Assertions.assertEquals(400, client.get("/v1/region/members?world=world").code());
            Assertions.assertEquals(400, client.get("/v1/region/members?region=plot_a").code());
        });
    }

    // ---- /v1/worlds/geometry --------------------------------------------------------------

    @Test
    void geometryReportsThePageWithItsFootprints() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/worlds/geometry?world=world");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"totalCount\":2"), body);
            Assertions.assertTrue(body.contains("\"shape\":\"CUBOID\""), body);
        });
    }

    @Test
    void geometryIsReadFromTheGameServerOncePerWorldRatherThanOncePerPage() throws Exception {
        // A footprint can only be measured on the server's main thread. Paging a map of a
        // built-up world once cost that thread a reading per page, and every visitor paid it
        // again; the regions had not moved between any two of those readings.
        CountingModule counting = new CountingModule(standardModule());
        JavalinTest.test(TestServers.withCachedGeometry(REGISTERED, WORLDS, counting, 300).javalin(),
                (s, client) -> {
                    client.get("/v1/worlds/geometry?world=world&pageSize=1&page=1");
                    settle(counting);
                    int afterFirstVisitor = counting.readings.get();

                    client.get("/v1/worlds/geometry?world=world&pageSize=1&page=2");
                    client.get("/v1/worlds/geometry?world=world&pageSize=1&page=1");
                    client.get("/v1/worlds/geometry?world=world");

                    Assertions.assertEquals(afterFirstVisitor, counting.readings.get());
                });
    }

    @Test
    void geometryTellsABrowserItMayKeepThePage() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Assertions.assertEquals(List.of("public, max-age=60"),
                    client.get("/v1/worlds/geometry?world=world").headers().get("Cache-Control"));
        });
    }

    @Test
    void geometryStillReportsARegionWorldGuardNoLongerHolds() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            String body = client.get("/v1/worlds/geometry?world=world").body().string();
            Assertions.assertTrue(body.contains("\"worldGuardRegionId\":\"plot_b\""), body);
            Assertions.assertTrue(body.contains("\"dimensions\":null"),
                    "plot_b is registered but has no WorldGuard geometry: " + body);
        });
    }

    @Test
    void geometryDegradesToNullRatherThan502OnAnUnreachableModule() {
        JavalinTest.test(server(TestServers.unreachableModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/worlds/geometry?world=world");
            Assertions.assertEquals(200, response.code(),
                    "the region list comes from the database, so there is something to degrade to");
            Assertions.assertFalse(response.body().string().contains("\"shape\""));
        });
    }

    @Test
    void geometryPagesLikeEveryOtherListing() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Response response = client.get("/v1/worlds/geometry?world=world&page=2&pageSize=1");
            Assertions.assertEquals(200, response.code());
            String body = response.body().string();
            Assertions.assertTrue(body.contains("\"totalPages\":2"), body);
            Assertions.assertTrue(body.contains("plot_b"), body);
            Assertions.assertFalse(body.contains("plot_a"), body);
        });
    }

    @Test
    void geometryRequiresAWorld() {
        JavalinTest.test(server(standardModule()).javalin(), (s, client) -> {
            Assertions.assertEquals(400, client.get("/v1/worlds/geometry").code());
            Assertions.assertEquals(404, client.get("/v1/worlds/geometry?world=nether").code());
        });
    }
}
