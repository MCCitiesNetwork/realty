package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.entity.RentedRegionView;
import io.github.md5sha256.realty.database.mapper.LeaseholdContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import org.apache.ibatis.session.ExecutorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Builds a {@link RealtyRestServer} over stub {@link RealtyBackend} and
 * {@link Database} implementations, for tests that only need the HTTP layer.
 *
 * <p>{@link RealtyBackend} and {@link SqlSessionWrapper} are large interfaces with
 * many accessors that no endpoint under test calls, so both are backed by a
 * {@link Proxy} that throws {@link UnsupportedOperationException} from every method
 * except the handful a test actually exercises.</p>
 */
final class TestServers {

    private TestServers() {
    }

    static @NotNull RealtyRestServer withHealthyDatabase() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false), defaultSettings());
    }

    static @NotNull RealtyRestServer withFailingDatabase() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(true), defaultSettings());
    }

    static @NotNull RealtyRestServer withWorlds() {
        List<RealtyWorldEntity> worlds = List.of(
                new RealtyWorldEntity(UUID.randomUUID(), "world"),
                new RealtyWorldEntity(UUID.randomUUID(), "My World"));
        return new RealtyRestServer(stubBackend(), new StubDatabase(false, worlds), defaultSettings());
    }

    static @NotNull RealtyRestServer withNoWorlds() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false, List.of()), defaultSettings());
    }

    /**
     * A world lookup that throws a {@link RuntimeException} carrying the given
     * message when its table is queried -- for pinning that the server's catch-all
     * 500 handler never echoes an exception's message back to the client.
     */
    static @NotNull RealtyRestServer withWorldLookupThatThrows(@NotNull String secretMessage) {
        return new RealtyRestServer(stubBackend(), new ThrowingDatabase(secretMessage), defaultSettings());
    }

    /**
     * A single freehold region, {@code downtown_plot_14} in world {@code world},
     * for sale (no title holder, a price) and untagged.
     */
    static @NotNull RealtyRestServer withForSaleRegion() {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(UUID.randomUUID(), "world"));
        FreeholdContractEntity freehold = new FreeholdContractEntity(
                1, UUID.randomUUID(), null, 25000.0, true);
        RealtyBackend.RegionInfo info = new RealtyBackend.RegionInfo(freehold, null, null, null, null);
        return new RealtyRestServer(regionBackend(info, RegionState.FOR_SALE),
                new StubDatabase(false, worlds, false, List.of()), defaultSettings());
    }

    /**
     * A single freehold region, {@code plot_1}, in a world named {@code My World} --
     * for the world-name-with-spaces resolution tests.
     */
    static @NotNull RealtyRestServer withRegionInWorldNamedMyWorld() {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(UUID.randomUUID(), "My World"));
        FreeholdContractEntity freehold = new FreeholdContractEntity(
                1, UUID.randomUUID(), null, 1000.0, true);
        RealtyBackend.RegionInfo info = new RealtyBackend.RegionInfo(freehold, null, null, null, null);
        return new RealtyRestServer(regionBackend(info, RegionState.FOR_SALE),
                new StubDatabase(false, worlds, false, List.of()), defaultSettings());
    }

    /**
     * A single freehold region {@code plot_1} in a world with the given literal
     * name -- for tests proving a name containing {@code +} or {@code %} survives
     * the query-decoding path unmangled when requested percent-encoded.
     */
    static @NotNull RealtyRestServer withRegionInWorldNamed(@NotNull String worldName) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(UUID.randomUUID(), worldName));
        FreeholdContractEntity freehold = new FreeholdContractEntity(
                1, UUID.randomUUID(), null, 1000.0, true);
        RealtyBackend.RegionInfo info = new RealtyBackend.RegionInfo(freehold, null, null, null, null);
        return new RealtyRestServer(regionBackend(info, RegionState.FOR_SALE),
                new StubDatabase(false, worlds, false, List.of()), defaultSettings());
    }

    /**
     * A known world with no region matching any lookup -- every {@code RealtyBackend}
     * region query comes back empty/null.
     */
    static @NotNull RealtyRestServer withWorldsButNoRegions() {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(UUID.randomUUID(), "world"));
        RealtyBackend.RegionInfo info = new RealtyBackend.RegionInfo(null, null, null, null, null);
        return new RealtyRestServer(regionBackend(info, null),
                new StubDatabase(false, worlds, false, List.of()), defaultSettings());
    }

    /**
     * A player who owns one region, is landlord of another, and rents a third
     * (with an end date, so {@code secondsRemaining} is exercised) -- all three in
     * a single world.
     */
    static @NotNull RealtyRestServer withPlayerHoldings() {
        return withPlayerHoldings(100);
    }

    /**
     * As {@link #withPlayerHoldings()}, but with {@code maxPageSize} set to the
     * given value -- for the page-size clamping test.
     */
    static @NotNull RealtyRestServer withPlayerHoldingsAndMaxPageSize(int maxPageSize) {
        return withPlayerHoldings(maxPageSize);
    }

    private static @NotNull RealtyRestServer withPlayerHoldings(int maxPageSize) {
        UUID worldId = UUID.randomUUID();
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(worldId, "world"));

        RealtyRegionEntity owned = new RealtyRegionEntity(1, "owned_plot", worldId);
        RealtyRegionEntity landlord = new RealtyRegionEntity(2, "landlord_plot", worldId);
        RentedRegionView rented = new RentedRegionView("rented_plot", worldId, LocalDateTime.now().plusDays(1));

        RealtyBackend.ListResult listResult =
                new RealtyBackend.ListResult(1, 1, 1, List.of(owned), List.of(landlord), List.of());
        RealtyBackend.SingleCategoryResult ownedResult =
                new RealtyBackend.SingleCategoryResult(1, List.of(owned));
        RealtyBackend.SingleCategoryResult rentedResult =
                new RealtyBackend.SingleCategoryResult(1, List.of());

        RestSettings settings = new RestSettings("localhost", 0, maxPageSize, null, null, 1500);
        return new RealtyRestServer(
                playerBackend(listResult, ownedResult, rentedResult),
                new StubDatabase(false, worlds, false, List.of(), List.of(rented)),
                settings);
    }

    /**
     * A player who owns a single region whose {@code worldId} is absent from the
     * {@code RealtyWorld} table -- {@link WorldLookup#refsFor} must still yield a
     * {@link io.github.md5sha256.realty.rest.json.WorldRef} for it (with a null
     * name) rather than a missing entry, since {@code RegionRef.world} is
     * {@code @NotNull}.
     */
    static @NotNull RealtyRestServer withPlayerOwningRegionInMissingWorld() {
        UUID missingWorldId = UUID.randomUUID();
        RealtyRegionEntity owned = new RealtyRegionEntity(1, "orphaned_plot", missingWorldId);

        RealtyBackend.ListResult listResult =
                new RealtyBackend.ListResult(1, 1, 0, List.of(owned), List.of(), List.of());
        RealtyBackend.SingleCategoryResult ownedResult =
                new RealtyBackend.SingleCategoryResult(1, List.of(owned));
        RealtyBackend.SingleCategoryResult empty = new RealtyBackend.SingleCategoryResult(0, List.of());

        return new RealtyRestServer(
                playerBackend(listResult, ownedResult, empty),
                new StubDatabase(false, List.of(), false, List.of()),
                defaultSettings());
    }

    /**
     * A player who owns, is landlord of, and rents nothing -- the zero-total path.
     */
    static @NotNull RealtyRestServer withEmptyPlayerHoldings() {
        List<RealtyWorldEntity> worlds = List.of();
        RealtyBackend.ListResult listResult =
                new RealtyBackend.ListResult(0, 0, 0, List.of(), List.of(), List.of());
        RealtyBackend.SingleCategoryResult empty = new RealtyBackend.SingleCategoryResult(0, List.of());
        return new RealtyRestServer(
                playerBackend(listResult, empty, empty),
                new StubDatabase(false, worlds, false, List.of(), List.of()),
                defaultSettings());
    }

    private static @NotNull RealtyBackend playerBackend(@NotNull RealtyBackend.ListResult listResult,
                                                          @NotNull RealtyBackend.SingleCategoryResult ownedResult,
                                                          @NotNull RealtyBackend.SingleCategoryResult rentedResult) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("listRegions".equals(method.getName())) {
                return listResult;
            }
            if ("listOwnedRegions".equals(method.getName())) {
                return ownedResult;
            }
            if ("listRentedRegions".equals(method.getName())) {
                return rentedResult;
            }
            throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        return (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
    }

    /**
     * A {@link Database} backed by the given worlds, for tests that exercise
     * {@link WorldLookup} directly rather than through a {@link RealtyRestServer}.
     */
    static @NotNull Database databaseWithWorlds(@NotNull List<RealtyWorldEntity> worlds) {
        return new StubDatabase(false, worlds, false);
    }

    /**
     * A {@link Database} whose {@code selectByName} fails the test if invoked, for
     * proving a caller short-circuits before ever reaching a name lookup.
     */
    static @NotNull Database databaseThatFailsIfSelectByNameCalled(@NotNull List<RealtyWorldEntity> worlds) {
        return new StubDatabase(false, worlds, true);
    }

    private static @NotNull RestSettings defaultSettings() {
        return new RestSettings("localhost", 0, 100, null, null, 1500);
    }

    private static @NotNull RealtyBackend stubBackend() {
        InvocationHandler handler = (proxy, method, args) -> {
            throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        return (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
    }

    private static @NotNull SqlSessionWrapper stubSession(@NotNull List<RealtyWorldEntity> worlds,
                                                            boolean failOnSelectByName) {
        return stubSession(worlds, failOnSelectByName, List.of(), List.of());
    }

    private static @NotNull SqlSessionWrapper stubSession(@NotNull List<RealtyWorldEntity> worlds,
                                                            boolean failOnSelectByName,
                                                            @NotNull List<String> regionTags) {
        return stubSession(worlds, failOnSelectByName, regionTags, List.of());
    }

    private static @NotNull SqlSessionWrapper stubSession(@NotNull List<RealtyWorldEntity> worlds,
                                                            boolean failOnSelectByName,
                                                            @NotNull List<String> regionTags,
                                                            @NotNull List<RentedRegionView> rentedViews) {
        RealtyWorldMapper realtyWorldMapper = worldMapperHandler(worlds, failOnSelectByName);
        RegionTagMapper regionTagMapper = regionTagMapperHandler(regionTags);
        LeaseholdContractMapper leaseholdContractMapper = leaseholdContractMapperHandler(rentedViews);
        InvocationHandler handler = (proxy, method, args) -> {
            if ("realtyWorldMapper".equals(method.getName())) {
                return realtyWorldMapper;
            }
            if ("regionTagMapper".equals(method.getName())) {
                return regionTagMapper;
            }
            if ("leaseholdContractMapper".equals(method.getName())) {
                return leaseholdContractMapper;
            }
            if ("close".equals(method.getName())) {
                return null;
            }
            throw new UnsupportedOperationException(
                    "SqlSessionWrapper#" + method.getName() + " is not stubbed for this test");
        };
        return (SqlSessionWrapper) Proxy.newProxyInstance(
                SqlSessionWrapper.class.getClassLoader(),
                new Class<?>[]{SqlSessionWrapper.class},
                handler);
    }

    private static @NotNull LeaseholdContractMapper leaseholdContractMapperHandler(
            @NotNull List<RentedRegionView> rentedViews) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("selectRentedRegionsWithEndDate".equals(method.getName())) {
                return rentedViews;
            }
            throw new UnsupportedOperationException(
                    "LeaseholdContractMapper#" + method.getName() + " is not stubbed for this test");
        };
        return (LeaseholdContractMapper) Proxy.newProxyInstance(
                LeaseholdContractMapper.class.getClassLoader(),
                new Class<?>[]{LeaseholdContractMapper.class},
                handler);
    }

    private static @NotNull RegionTagMapper regionTagMapperHandler(@NotNull List<String> regionTags) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("selectTagIdsByRegionId".equals(method.getName())) {
                return regionTags;
            }
            throw new UnsupportedOperationException(
                    "RegionTagMapper#" + method.getName() + " is not stubbed for this test");
        };
        return (RegionTagMapper) Proxy.newProxyInstance(
                RegionTagMapper.class.getClassLoader(),
                new Class<?>[]{RegionTagMapper.class},
                handler);
    }

    /**
     * A {@link RealtyBackend} stubbing only {@code getRegionInfo} and
     * {@code getRegionState} with fixed values, regardless of which region or world
     * is asked for -- these tests only ever ask about the one region they set up.
     */
    private static @NotNull RealtyBackend regionBackend(@NotNull RealtyBackend.RegionInfo info,
                                                          @Nullable RegionState state) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("getRegionInfo".equals(method.getName())) {
                return info;
            }
            if ("getRegionState".equals(method.getName())) {
                return state;
            }
            throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        return (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
    }

    private static @NotNull RealtyWorldMapper worldMapperHandler(@NotNull List<RealtyWorldEntity> worlds,
                                                                   boolean failOnSelectByName) {
        InvocationHandler handler = (proxy, method, args) -> {
            if ("selectAll".equals(method.getName())) {
                return worlds;
            }
            if ("selectByName".equals(method.getName())) {
                if (failOnSelectByName) {
                    throw new AssertionError(
                            "RealtyWorldMapper#selectByName must not be called on this path");
                }
                String name = (String) args[0];
                return worlds.stream()
                        .filter(world -> world.worldName().equals(name))
                        .findFirst()
                        .orElse(null);
            }
            if ("selectById".equals(method.getName())) {
                UUID id = (UUID) args[0];
                return worlds.stream()
                        .filter(world -> world.worldId().equals(id))
                        .findFirst()
                        .orElse(null);
            }
            throw new UnsupportedOperationException(
                    "RealtyWorldMapper#" + method.getName() + " is not stubbed for this test");
        };
        return (RealtyWorldMapper) Proxy.newProxyInstance(
                RealtyWorldMapper.class.getClassLoader(),
                new Class<?>[]{RealtyWorldMapper.class},
                handler);
    }

    /**
     * A {@link Database} whose {@code openSession} methods either return a working
     * stub session or throw, depending on {@code failing}. Every other method throws:
     * this server must never migrate or write, so nothing else should ever be called.
     */
    private static final class StubDatabase implements Database {

        private final boolean failing;
        private final List<RealtyWorldEntity> worlds;
        private final boolean failOnSelectByName;
        private final List<String> regionTags;
        private final List<RentedRegionView> rentedViews;

        private StubDatabase(boolean failing) {
            this(failing, List.of());
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds) {
            this(failing, worlds, false);
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName) {
            this(failing, worlds, failOnSelectByName, List.of());
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName, @NotNull List<String> regionTags) {
            this(failing, worlds, failOnSelectByName, regionTags, List.of());
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName, @NotNull List<String> regionTags,
                              @NotNull List<RentedRegionView> rentedViews) {
            this.failing = failing;
            this.worlds = worlds;
            this.failOnSelectByName = failOnSelectByName;
            this.regionTags = regionTags;
            this.rentedViews = rentedViews;
        }

        @Override
        public @NotNull SqlSessionWrapper openSession() {
            return openSession(true);
        }

        @Override
        public @NotNull SqlSessionWrapper openSession(boolean autoCommit) {
            if (this.failing) {
                throw new RuntimeException("stub database is unreachable");
            }
            return stubSession(this.worlds, this.failOnSelectByName, this.regionTags, this.rentedViews);
        }

        @Override
        public @NotNull SqlSessionWrapper openSession(@NotNull ExecutorType executorType, boolean autoCommit) {
            return openSession(autoCommit);
        }

        @Override
        public void initializeSchema(@NotNull Path schemaFilesDirectory) {
            throw new UnsupportedOperationException("realty-rest must never migrate the schema");
        }

        @Override
        public void close() {
            // no-op
        }

    }

    /**
     * A {@link Database} whose every {@code openSession} call throws a
     * {@link RuntimeException} carrying the given message -- used only to drive an
     * endpoint into the server's catch-all 500 handler, to pin that the response
     * never echoes the exception's message back to the client.
     */
    private static final class ThrowingDatabase implements Database {

        private final String message;

        private ThrowingDatabase(@NotNull String message) {
            this.message = message;
        }

        @Override
        public @NotNull SqlSessionWrapper openSession() {
            throw new RuntimeException(this.message);
        }

        @Override
        public @NotNull SqlSessionWrapper openSession(boolean autoCommit) {
            throw new RuntimeException(this.message);
        }

        @Override
        public @NotNull SqlSessionWrapper openSession(@NotNull ExecutorType executorType, boolean autoCommit) {
            throw new RuntimeException(this.message);
        }

        @Override
        public void initializeSchema(@NotNull Path schemaFilesDirectory) {
            throw new UnsupportedOperationException("realty-rest must never migrate the schema");
        }

        @Override
        public void close() {
            // no-op
        }

    }

}
