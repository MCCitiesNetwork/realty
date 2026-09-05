package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.api.RealtyBackend;
import io.github.md5sha256.realty.api.RegionState;
import io.github.md5sha256.realty.database.Database;
import io.github.md5sha256.realty.database.SqlSessionWrapper;
import io.github.md5sha256.realty.database.entity.ActiveAuctionRow;
import io.github.md5sha256.realty.database.entity.ActivityRow;
import io.github.md5sha256.realty.database.entity.AuctionSort;
import io.github.md5sha256.realty.database.entity.FreeholdContractEntity;
import io.github.md5sha256.realty.database.entity.HistoryEntry;
import io.github.md5sha256.realty.database.entity.RealtyRegionEntity;
import io.github.md5sha256.realty.database.entity.PlotOwnerCount;
import io.github.md5sha256.realty.database.entity.RealtyWorldEntity;
import io.github.md5sha256.realty.database.entity.TagCountEntity;
import io.github.md5sha256.realty.database.entity.StatisticsEntity;
import io.github.md5sha256.realty.database.entity.RegionStateRow;
import io.github.md5sha256.realty.database.entity.OccupancyFilter;
import io.github.md5sha256.realty.database.entity.RentedRegionView;
import io.github.md5sha256.realty.database.entity.SearchResultEntity;
import io.github.md5sha256.realty.database.entity.SearchSort;
import io.github.md5sha256.realty.database.mapper.ActivityMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractAuctionMapper;
import io.github.md5sha256.realty.database.mapper.FreeholdContractMapper;
import io.github.md5sha256.realty.database.mapper.LeaseholdContractMapper;
import io.github.md5sha256.realty.database.mapper.RealtyRegionMapper;
import io.github.md5sha256.realty.database.mapper.RealtyWorldMapper;
import io.github.md5sha256.realty.database.mapper.RegionTagMapper;
import io.github.md5sha256.realty.database.mapper.SearchMapper;
import io.github.md5sha256.realty.rest.json.RegionResponse;
import io.github.md5sha256.realty.rest.module.ModuleClient;
import io.github.md5sha256.realty.rest.module.ModuleResult;
import io.github.md5sha256.realty.rest.module.ResourcePack;
import io.github.md5sha256.realty.rest.module.RegionMembers;
import io.github.md5sha256.realty.rest.module.RegionsAt;
import io.github.md5sha256.realty.rest.module.NameLookup;
import io.javalin.http.staticfiles.Location;
import org.apache.ibatis.session.ExecutorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    /** A server that also serves a built front end from {@code directory} on disk. */
    static @NotNull RealtyRestServer withStaticSite(@NotNull Path directory) {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false), defaultSettings(),
                ModuleClient.disabled(),
                new StaticSite(directory.toAbsolutePath().toString(), Location.EXTERNAL));
    }

    /** A front end plus a config.json on disk, as the bundled build serves beside its jar. */
    static @NotNull RealtyRestServer withStaticSite(@NotNull Path directory, @NotNull Path configJson) {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false), defaultSettings(),
                ModuleClient.disabled(),
                new StaticSite(directory.toAbsolutePath().toString(), Location.EXTERNAL,
                        configJson.toAbsolutePath()));
    }

    static @NotNull RealtyRestServer withHealthyDatabase() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(false), defaultSettings());
    }

    static @NotNull RealtyRestServer withFailingDatabase() {
        return new RealtyRestServer(stubBackend(), new StubDatabase(true), defaultSettings());
    }

    /**
     * A server whose backend answers {@code getSchematic} with {@code schematic}
     * (null meaning "never captured"), in a world named "world".
     */
    static @NotNull RealtyRestServer withSchematic(byte @Nullable [] schematic) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(
                UUID.fromString("8f4d1c2e-0000-0000-0000-000000000099"), "world"));
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getSchematic" -> schematic;
            default -> throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        RealtyBackend backend = (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
        return new RealtyRestServer(backend, new StubDatabase(false, worlds), defaultSettings());
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
     * A server whose backend reports exactly {@code tagIds} in that order, and the
     * given per-tag region counts. A tag absent from {@code counts} counts zero, so
     * a test naming one tag need not populate the other.
     */
    static @NotNull RealtyRestServer withTags(@NotNull List<String> tagIds,
                                              @NotNull Map<String, Integer> counts) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "getAllTagIds" -> List.copyOf(tagIds);
            case "countRegionsByTag" -> counts.getOrDefault((String) args[0], 0);
            case "countRegionsPerTag" -> tagIds.stream()
                    .map(id -> new TagCountEntity(id, counts.getOrDefault(id, 0)))
                    .toList();
            default -> throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        RealtyBackend backend = (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
        return new RealtyRestServer(backend, new StubDatabase(false), defaultSettings());
    }

    /**
     * A server whose backend reports the given counter values. A counter absent from
     * {@code counters} answers the sample value named here, so a test asserting on one
     * figure need not restate the other nine.
     */
    static @NotNull RealtyRestServer withStats(@NotNull Map<String, Integer> counters) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "countAllRegions", "countAllFreeholdContracts", "countOccupiedFreeholdContracts",
                 "countAllLeaseholdContracts", "countOccupiedLeaseholdContracts",
                 "countActiveOffers", "countActiveAuctions" -> counters.getOrDefault(method.getName(), 1);
            case "averageFreeholdPrice" -> 18250.5d;
            case "averageLeaseholdPrice" -> 640.0d;
            case "averageLeaseholdDurationSeconds" -> 604800L;
            case "statistics" -> new StatisticsEntity(
                    counters.getOrDefault("countAllRegions", 1),
                    counters.getOrDefault("countAllFreeholdContracts", 1),
                    counters.getOrDefault("countOccupiedFreeholdContracts", 1),
                    18250.5d,
                    counters.getOrDefault("countAllLeaseholdContracts", 1),
                    counters.getOrDefault("countOccupiedLeaseholdContracts", 1),
                    640.0d,
                    604800L,
                    counters.getOrDefault("countActiveOffers", 1),
                    counters.getOrDefault("countActiveAuctions", 1));
            default -> throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        RealtyBackend backend = (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
        return new RealtyRestServer(backend, new StubDatabase(false), defaultSettings());
    }

    /**
     * A server whose backend reports a server on which nothing has been registered --
     * every counter and every average zero.
     */
    static @NotNull RealtyRestServer withEmptyStats() {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "countAllRegions", "countAllFreeholdContracts", "countOccupiedFreeholdContracts",
                 "countAllLeaseholdContracts", "countOccupiedLeaseholdContracts",
                 "countActiveOffers", "countActiveAuctions" -> 0;
            case "averageFreeholdPrice", "averageLeaseholdPrice" -> 0.0d;
            case "averageLeaseholdDurationSeconds" -> 0L;
            case "statistics" -> new StatisticsEntity(0, 0, 0, 0.0d, 0, 0, 0.0d, 0L, 0, 0);
            default -> throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        RealtyBackend backend = (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
        return new RealtyRestServer(backend, new StubDatabase(false), defaultSettings());
    }

    /**
     * A server whose backend answers the given holding counters, wired to a module
     * resolving {@code names}. A counter absent from {@code counters} answers zero.
     */
    static @NotNull RealtyRestServer withPlayerSummary(@NotNull Map<String, Integer> counters,
                                                       @NotNull Map<UUID, String> names) {
        return new RealtyRestServer(summaryBackend(counters), new StubDatabase(false),
                defaultSettings(), stubModule(names, Map.of(), Map.of()));
    }

    /**
     * A summary server whose module resolves exactly {@code name} to {@code id}, for
     * proving the route accepts a name where the other player routes do.
     */
    static @NotNull RealtyRestServer withPlayerSummaryByName(@NotNull String name, @NotNull UUID id) {
        return new RealtyRestServer(summaryBackend(Map.of()), new StubDatabase(false),
                defaultSettings(), stubModule(Map.of(id, name), Map.of(), Map.of(name, id)));
    }

    /**
     * A summary server wired to the given module, for proving which parameter depends
     * on it and which does not.
     */
    static @NotNull RealtyRestServer withPlayerSummaryAndModule(@NotNull ModuleClient module) {
        return new RealtyRestServer(summaryBackend(Map.of()), new StubDatabase(false),
                defaultSettings(), module);
    }

    private static @NotNull RealtyBackend summaryBackend(@NotNull Map<String, Integer> counters) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "countRegionsByTitleHolder", "countRegionsByLandlord",
                 "countOccupiedLeaseholdsByLandlord", "countRegionsByTenant",
                 "countRegionsByAuthority" -> counters.getOrDefault(method.getName(), 0);
            default -> throw new UnsupportedOperationException(
                    "RealtyBackend#" + method.getName() + " is not stubbed for this test");
        };
        return (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
    }

    /**
     * A server whose freehold mapper reports exactly {@code counts} as one page of the
     * owners leaderboard, out of {@code totalCount} distinct title holders, with the
     * module resolving {@code names}.
     */
    static @NotNull RealtyRestServer withOwnerCounts(@NotNull List<PlotOwnerCount> counts,
                                                     int totalCount,
                                                     @NotNull Map<UUID, String> names) {
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "selectPlotCountsByTitleHolderPaged" -> List.copyOf(counts);
            case "countDistinctTitleHolders" -> totalCount;
            default -> throw new UnsupportedOperationException(
                    "FreeholdContractMapper#" + method.getName() + " is not stubbed for this test");
        };
        FreeholdContractMapper mapper = (FreeholdContractMapper) Proxy.newProxyInstance(
                FreeholdContractMapper.class.getClassLoader(),
                new Class<?>[]{FreeholdContractMapper.class},
                handler);
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, List.of(), false, List.of(), List.of(), null, null, mapper),
                defaultSettings(), stubModule(names, Map.of(), Map.of()));
    }

    /**
     * Captures what a handler asked {@code searchHistory} for, so a test can assert on
     * the arguments as well as the response.
     */
    static final class HistoryStub {

        private final List<HistoryEntry> entries;
        private final int totalCount;

        String eventType;
        LocalDateTime since;
        UUID playerId;
        int limit;
        int offset;

        HistoryStub(@NotNull List<HistoryEntry> entries, int totalCount) {
            this.entries = entries;
            this.totalCount = totalCount;
        }
    }

    static @NotNull RealtyRestServer withHistory(@NotNull List<HistoryEntry> entries,
                                                 int totalCount,
                                                 @NotNull Map<UUID, String> names) {
        return withHistory(new HistoryStub(entries, totalCount), names);
    }

    static @NotNull RealtyRestServer withHistory(@NotNull HistoryStub stub,
                                                 @NotNull Map<UUID, String> names) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(WORLD_ID, "world"));
        InvocationHandler handler = (proxy, method, args) -> {
            if (!"searchHistory".equals(method.getName())) {
                throw new UnsupportedOperationException(
                        "RealtyBackend#" + method.getName() + " is not stubbed for this test");
            }
            stub.eventType = (String) args[2];
            stub.since = (LocalDateTime) args[3];
            stub.playerId = (UUID) args[4];
            stub.limit = (int) args[5];
            stub.offset = (int) args[6];
            return new RealtyBackend.HistoryResult(stub.entries, stub.totalCount);
        };
        RealtyBackend backend = (RealtyBackend) Proxy.newProxyInstance(
                RealtyBackend.class.getClassLoader(),
                new Class<?>[]{RealtyBackend.class},
                handler);
        return new RealtyRestServer(backend, new StubDatabase(false, worlds), defaultSettings(),
                stubModule(names, Map.of(), Map.of()));
    }

    /**
     * Captures what a handler asked the auction listing for.
     */
    static final class AuctionStub {

        private final List<ActiveAuctionRow> rows;
        private final int totalCount;

        UUID worldId;
        AuctionSort sort;
        int limit;
        int offset;

        AuctionStub(@NotNull List<ActiveAuctionRow> rows, int totalCount) {
            this.rows = rows;
            this.totalCount = totalCount;
        }
    }

    static @NotNull RealtyRestServer withAuctions(@NotNull List<ActiveAuctionRow> rows,
                                                  int totalCount,
                                                  @NotNull Map<UUID, String> names) {
        return withAuctions(new AuctionStub(rows, totalCount), names);
    }

    static @NotNull RealtyRestServer withAuctions(@NotNull AuctionStub stub,
                                                  @NotNull Map<UUID, String> names) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(WORLD_ID, "world"));
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "selectActivePage" -> {
                stub.worldId = (UUID) args[0];
                stub.sort = (AuctionSort) args[1];
                stub.limit = (int) args[2];
                stub.offset = (int) args[3];
                yield List.copyOf(stub.rows);
            }
            case "countActiveInWorld" -> stub.totalCount;
            default -> throw new UnsupportedOperationException(
                    "FreeholdContractAuctionMapper#" + method.getName() + " is not stubbed for this test");
        };
        FreeholdContractAuctionMapper mapper = (FreeholdContractAuctionMapper) Proxy.newProxyInstance(
                FreeholdContractAuctionMapper.class.getClassLoader(),
                new Class<?>[]{FreeholdContractAuctionMapper.class},
                handler);
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, worlds, false, List.of(), List.of(), null, null, null, mapper),
                defaultSettings(), stubModule(names, Map.of(), Map.of()));
    }

    /**
     * Captures what a handler asked the activity feed for.
     */
    static final class ActivityStub {

        private final List<ActivityRow> rows;
        private final int totalCount;

        List<String> eventTypes;
        UUID worldId;
        LocalDateTime since;
        int limit;
        int offset;

        ActivityStub(@NotNull List<ActivityRow> rows, int totalCount) {
            this.rows = rows;
            this.totalCount = totalCount;
        }
    }

    static @NotNull RealtyRestServer withActivity(@NotNull List<ActivityRow> rows,
                                                  int totalCount,
                                                  @NotNull Map<UUID, String> names) {
        return withActivity(new ActivityStub(rows, totalCount), names);
    }

    @SuppressWarnings("unchecked")
    static @NotNull RealtyRestServer withActivity(@NotNull ActivityStub stub,
                                                  @NotNull Map<UUID, String> names) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(WORLD_ID, "world"));
        InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
            case "selectPage" -> {
                stub.eventTypes = List.copyOf((Collection<String>) args[0]);
                stub.worldId = (UUID) args[1];
                stub.since = (LocalDateTime) args[2];
                stub.limit = (int) args[3];
                stub.offset = (int) args[4];
                yield List.copyOf(stub.rows);
            }
            case "countMatching" -> {
                stub.eventTypes = List.copyOf((Collection<String>) args[0]);
                yield stub.totalCount;
            }
            default -> throw new UnsupportedOperationException(
                    "ActivityMapper#" + method.getName() + " is not stubbed for this test");
        };
        ActivityMapper mapper = (ActivityMapper) Proxy.newProxyInstance(
                ActivityMapper.class.getClassLoader(),
                new Class<?>[]{ActivityMapper.class},
                handler);
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, worlds, false, List.of(), List.of(), null, null, null, null, mapper),
                defaultSettings(), stubModule(names, Map.of(), Map.of()));
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

    static final UUID WORLD_ID = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    static final UUID AUTHORITY = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    /**
     * A single freehold region, {@code downtown_plot_14} in world {@code WORLD_ID},
     * for sale, wired to the given {@link ModuleClient} -- for tests exercising
     * module-backed health and enrichment behaviour.
     */
    static @NotNull RealtyRestServer withModule(@NotNull ModuleClient module) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(WORLD_ID, "world"));
        FreeholdContractEntity freehold = new FreeholdContractEntity(1, AUTHORITY, null, 25000.0, true);
        RealtyBackend.RegionInfo info = new RealtyBackend.RegionInfo(freehold, null, null, null, null);
        return new RealtyRestServer(regionBackend(info, RegionState.FOR_SALE),
                new StubDatabase(false, worlds), defaultSettings(), module);
    }

    /**
     * A server serving exactly {@code info} for region {@code downtown_plot_14} in
     * world {@code WORLD_ID}, for tests asserting on one contract's serialised fields.
     */
    static @NotNull RealtyRestServer withRegionInfo(@NotNull RealtyBackend.RegionInfo info,
                                                    @Nullable RegionState state,
                                                    @NotNull ModuleClient module) {
        List<RealtyWorldEntity> worlds = List.of(new RealtyWorldEntity(WORLD_ID, "world"));
        return new RealtyRestServer(regionBackend(info, state),
                new StubDatabase(false, worlds), defaultSettings(), module);
    }

    static @NotNull ModuleClient stubModule(@NotNull Map<UUID, String> names,
                                            @NotNull Map<String, RegionResponse.Dimensions> dimensionsByRegionId,
                                            @NotNull Map<String, UUID> uuidsByName) {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                            @NotNull String regionId) {
                return Optional.ofNullable(dimensionsByRegionId.get(regionId));
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                Map<UUID, String> resolved = new LinkedHashMap<>();
                for (UUID id : ids) {
                    if (names.containsKey(id)) {
                        resolved.put(id, names.get(id));
                    }
                }
                return resolved;
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                UUID id = uuidsByName.get(name);
                return id == null ? new NameLookup.Unknown() : new NameLookup.Resolved(id, name);
            }

            @Override
            public @NotNull Map<String, RegionResponse.Dimensions> dimensionsOf(
                    @NotNull UUID worldId, @NotNull Collection<String> regionIds) {
                Map<String, RegionResponse.Dimensions> found = new LinkedHashMap<>();
                for (String regionId : regionIds) {
                    RegionResponse.Dimensions dims = dimensionsByRegionId.get(regionId);
                    if (dims != null) {
                        found.put(regionId, dims);
                    }
                }
                return found;
            }

            @Override
            public @NotNull ModuleResult<RegionsAt> regionsAt(@NotNull UUID worldId, int x,
                                                              @Nullable Integer y, int z) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<RegionMembers> members(@NotNull UUID worldId,
                                                                @NotNull String regionId) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<ResourcePack> resourcePack() {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull Status status() {
                return Status.OK;
            }
        };
    }

    /**
     * A module whose two enrichment calls each take {@code stallMillis}, for asserting
     * that a handler overlaps them rather than paying for both in series.
     */
    static @NotNull ModuleClient stallingModule(long stallMillis) {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                            @NotNull String regionId) {
                stall();
                return Optional.empty();
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                stall();
                return Map.of();
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                return new NameLookup.Unavailable();
            }

            @Override
            public @NotNull Map<String, RegionResponse.Dimensions> dimensionsOf(
                    @NotNull UUID worldId, @NotNull Collection<String> regionIds) {
                return Map.of();
            }

            @Override
            public @NotNull ModuleResult<RegionsAt> regionsAt(@NotNull UUID worldId, int x,
                                                              @Nullable Integer y, int z) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<RegionMembers> members(@NotNull UUID worldId,
                                                                @NotNull String regionId) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<ResourcePack> resourcePack() {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull Status status() {
                return Status.OK;
            }

            private void stall() {
                try {
                    Thread.sleep(stallMillis);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        };
    }

    static @NotNull ModuleClient unreachableModule() {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                            @NotNull String regionId) {
                return Optional.empty();
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                return Map.of();
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                return new NameLookup.Unavailable();
            }

            @Override
            public @NotNull Map<String, RegionResponse.Dimensions> dimensionsOf(
                    @NotNull UUID worldId, @NotNull Collection<String> regionIds) {
                return Map.of();
            }

            @Override
            public @NotNull ModuleResult<RegionsAt> regionsAt(@NotNull UUID worldId, int x,
                                                              @Nullable Integer y, int z) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<RegionMembers> members(@NotNull UUID worldId,
                                                                @NotNull String regionId) {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull ModuleResult<ResourcePack> resourcePack() {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull Status status() {
                return Status.UNREACHABLE;
            }
        };
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
    /**
     * The player id every {@code withPlayerHoldings...} backend is stubbed for --
     * the stub {@link RealtyBackend} ignores the requested id and returns the same
     * fixed holdings regardless, so any UUID works, but tests that resolve a name
     * through a {@link ModuleClient} need a fixed id to assert the response names.
     */
    static final UUID PLAYER_ID = UUID.fromString("3a1c88f0-0000-0000-0000-000000000099");

    static @NotNull RealtyRestServer withPlayerHoldings() {
        return withPlayerHoldings(100, ModuleClient.disabled());
    }

    /**
     * As {@link #withPlayerHoldings()}, but with {@code maxPageSize} set to the
     * given value -- for the page-size clamping test.
     */
    static @NotNull RealtyRestServer withPlayerHoldingsAndMaxPageSize(int maxPageSize) {
        return withPlayerHoldings(maxPageSize, ModuleClient.disabled());
    }

    /**
     * As {@link #withPlayerHoldings()}, but wired to the given {@link ModuleClient}
     * -- for tests exercising name resolution on {@code /v1/players/regions}.
     */
    static @NotNull RealtyRestServer withPlayerHoldingsAndModule(@NotNull ModuleClient module) {
        return withPlayerHoldings(100, module);
    }

    private static @NotNull RealtyRestServer withPlayerHoldings(int maxPageSize, @NotNull ModuleClient module) {
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

        RestSettings settings = new RestSettings("localhost", 0, maxPageSize, List.of(), null, null, 1500, 0, null);
        return new RealtyRestServer(
                playerBackend(listResult, ownedResult, rentedResult),
                new StubDatabase(false, worlds, false, List.of(), List.of(rented)),
                settings, module);
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

    /**
     * Records the arguments a request reached {@link SearchMapper} with, and
     * returns a fixed page of results.
     *
     * <p>The database is stubbed, so a test cannot prove filtering by observing
     * fewer rows -- the filtering itself is the mapper's SQL, and asserting on a
     * stub that pretends to filter would only test the stub. What the HTTP layer
     * owns is translating query parameters into the right mapper arguments, so
     * that is what these tests assert.</p>
     */
    static final class SearchStub {

        private final List<SearchResultEntity> results;
        private final int totalCount;

        boolean includeFreehold;
        boolean includeLeasehold;
        boolean includeUnpricedFreehold;
        UUID worldId;
        Collection<String> tagIds;
        Collection<String> excludedTagIds;
        boolean matchAllTags;
        double minPrice;
        double maxPrice;
        OccupancyFilter occupancy;
        SearchSort sort;
        int limit;
        int offset;

        SearchStub(@NotNull List<SearchResultEntity> results, int totalCount) {
            this.results = results;
            this.totalCount = totalCount;
        }

        static @NotNull SearchStub empty() {
            return new SearchStub(List.of(), 0);
        }
    }

    static @NotNull RealtyRestServer withSearch(@NotNull SearchStub stub,
                                                @NotNull List<RealtyWorldEntity> worlds) {
        return withSearch(stub, worlds, 100, List.of());
    }

    static @NotNull RealtyRestServer withSearch(@NotNull SearchStub stub,
                                                @NotNull List<RealtyWorldEntity> worlds,
                                                int maxPageSize,
                                                @NotNull List<String> corsOrigins) {
        RestSettings settings =
                new RestSettings("localhost", 0, maxPageSize, corsOrigins, null, null, 1500, 0, null);
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, worlds, false, List.of(), List.of(), stub),
                settings);
    }

    private static @NotNull SearchMapper searchMapperHandler(@NotNull SearchStub stub) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "searchCount" -> {
                    return stub.totalCount;
                }
                case "search" -> {
                    stub.includeFreehold = (boolean) args[0];
                    stub.includeLeasehold = (boolean) args[1];
                    stub.includeUnpricedFreehold = (boolean) args[2];
                    stub.worldId = (UUID) args[3];
                    stub.tagIds = asStrings(args[4]);
                    stub.excludedTagIds = asStrings(args[5]);
                    stub.matchAllTags = (boolean) args[6];
                    stub.minPrice = (double) args[7];
                    stub.maxPrice = (double) args[8];
                    stub.occupancy = (OccupancyFilter) args[9];
                    stub.sort = (SearchSort) args[10];
                    stub.limit = (int) args[11];
                    stub.offset = (int) args[12];
                    return stub.results;
                }
                default -> throw new UnsupportedOperationException(
                        "SearchMapper#" + method.getName() + " is not stubbed for this test");
            }
        };
        return (SearchMapper) Proxy.newProxyInstance(
                SearchMapper.class.getClassLoader(),
                new Class<?>[]{SearchMapper.class},
                handler);
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> asStrings(Object arg) {
        return (Collection<String>) arg;
    }

    /**
     * A server listing the given regions from {@code GET /v1/regions}. The stub
     * mapper slices its list exactly as the real query's {@code LIMIT}/{@code
     * OFFSET} would, so the handler's paging arithmetic is genuinely exercised;
     * the ordering itself belongs to the SQL and is verified against a real
     * database, not here.
     */
    static @NotNull RealtyRestServer withRegionList(@NotNull List<RealtyRegionEntity> regions,
                                                    @NotNull List<RealtyWorldEntity> worlds) {
        return withRegionList(regions, worlds, 100);
    }

    static @NotNull RealtyRestServer withRegionList(@NotNull List<RealtyRegionEntity> regions,
                                                    @NotNull List<RealtyWorldEntity> worlds,
                                                    int maxPageSize) {
        RestSettings settings =
                new RestSettings("localhost", 0, maxPageSize, List.of(), null, null, 1500, 0, null);
        List<RegionStateRow> rows = new ArrayList<>();
        for (RealtyRegionEntity region : regions) {
            rows.add(new RegionStateRow(region.realtyRegionId(),
                    region.worldGuardRegionId(), region.worldId(), null));
        }
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, worlds, false, List.of(), List.of(), null, rows),
                settings);
    }

    /**
     * A server whose region page reports exactly {@code rows}, each with the state
     * the mapper's projection would derive for it.
     */
    static @NotNull RealtyRestServer withRegionListStates(@NotNull List<RegionStateRow> rows,
                                                          @NotNull List<RealtyWorldEntity> worlds) {
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, worlds, false, List.of(), List.of(), null, rows),
                defaultSettings());
    }

    /**
     * A server over a fixed set of registered regions, wired to the given module -- the shape
     * every route in section E of the v1.x spec needs, since each one crosses both.
     */
    static @NotNull RealtyRestServer withAllRegionsAndModule(@NotNull List<RegionStateRow> rows,
                                                             @NotNull List<RealtyWorldEntity> worlds,
                                                             @NotNull ModuleClient module) {
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, worlds, false, List.of(), List.of(), null, rows),
                defaultSettings(), module);
    }

    /** The same, with region footprints kept for a term rather than read on every request. */
    static @NotNull RealtyRestServer withCachedGeometry(@NotNull List<RegionStateRow> rows,
                                                        @NotNull List<RealtyWorldEntity> worlds,
                                                        @NotNull ModuleClient module,
                                                        int geometryCacheSeconds) {
        RestSettings settings = new RestSettings("localhost", 0, 100, List.of(), null, null, 1500,
                geometryCacheSeconds, null);
        return new RealtyRestServer(stubBackend(),
                new StubDatabase(false, worlds, false, List.of(), List.of(), null, rows),
                settings, module);
    }

    /**
     * A module answering the three region-query routes from fixed data. {@code regionsAt} is
     * keyed by the y the caller sends, so a test can pin that the point and column forms are
     * genuinely different queries rather than one with a default.
     */
    static @NotNull ModuleClient regionQueryModule(@NotNull Map<UUID, String> names,
                                                   @NotNull Map<String, RegionResponse.Dimensions> geometry,
                                                   @Nullable RegionsAt column,
                                                   @Nullable RegionsAt point,
                                                   @Nullable RegionMembers members) {
        return new ModuleClient() {
            @Override
            public @NotNull Optional<RegionResponse.Dimensions> dimensions(@NotNull UUID worldId,
                                                                           @NotNull String regionId) {
                return Optional.ofNullable(geometry.get(regionId));
            }

            @Override
            public @NotNull Map<UUID, String> names(@NotNull Collection<UUID> ids) {
                Map<UUID, String> resolved = new LinkedHashMap<>();
                for (UUID id : ids) {
                    if (names.containsKey(id)) {
                        resolved.put(id, names.get(id));
                    }
                }
                return resolved;
            }

            @Override
            public @NotNull NameLookup uuidOf(@NotNull String name) {
                return new NameLookup.Unknown();
            }

            @Override
            public @NotNull Map<String, RegionResponse.Dimensions> dimensionsOf(
                    @NotNull UUID worldId, @NotNull Collection<String> regionIds) {
                Map<String, RegionResponse.Dimensions> found = new LinkedHashMap<>();
                for (String regionId : regionIds) {
                    RegionResponse.Dimensions dims = geometry.get(regionId);
                    if (dims != null) {
                        found.put(regionId, dims);
                    }
                }
                return found;
            }

            @Override
            public @NotNull ModuleResult<RegionsAt> regionsAt(@NotNull UUID worldId, int x,
                                                              @Nullable Integer y, int z) {
                RegionsAt answer = y == null ? column : point;
                return answer == null ? new ModuleResult.NotFound<>() : new ModuleResult.Found<>(answer);
            }

            @Override
            public @NotNull ModuleResult<RegionMembers> members(@NotNull UUID worldId,
                                                                @NotNull String regionId) {
                return members == null ? new ModuleResult.NotFound<>()
                        : new ModuleResult.Found<>(members);
            }

            @Override
            public @NotNull ModuleResult<ResourcePack> resourcePack() {
                return new ModuleResult.Unavailable<>();
            }

            @Override
            public @NotNull Status status() {
                return Status.OK;
            }
        };
    }

    private static @NotNull RealtyRegionMapper realtyRegionMapperHandler(
            @NotNull List<RegionStateRow> allRegions) {
        InvocationHandler handler = (proxy, method, args) -> {
            switch (method.getName()) {
                case "countAll" -> {
                    return allRegions.size();
                }
                case "selectPage" -> {
                    return asRegions(page(allRegions, (int) args[0], (int) args[1]));
                }
                case "selectPageWithState" -> {
                    return page(allRegions, (int) args[0], (int) args[1]);
                }
                case "countByWorld" -> {
                    return inWorld(allRegions, (UUID) args[0]).size();
                }
                case "selectPageByWorld" -> {
                    return asRegions(page(inWorld(allRegions, (UUID) args[0]),
                            (int) args[1], (int) args[2]));
                }
                case "selectPageWithStateByWorld" -> {
                    return page(inWorld(allRegions, (UUID) args[0]), (int) args[1], (int) args[2]);
                }
                case "selectRegisteredIds" -> {
                    @SuppressWarnings("unchecked")
                    List<String> candidates = (List<String>) args[1];
                    List<String> registered = new ArrayList<>();
                    for (RegionStateRow row : inWorld(allRegions, (UUID) args[0])) {
                        if (candidates.contains(row.worldGuardRegionId())
                                && !registered.contains(row.worldGuardRegionId())) {
                            registered.add(row.worldGuardRegionId());
                        }
                    }
                    return registered;
                }
                case "selectByWorldGuardRegion" -> {
                    for (RegionStateRow row : inWorld(allRegions, (UUID) args[1])) {
                        if (row.worldGuardRegionId().equals(args[0])) {
                            return new RealtyRegionEntity(row.realtyRegionId(),
                                    row.worldGuardRegionId(), row.worldId());
                        }
                    }
                    return null;
                }
                default -> throw new UnsupportedOperationException(
                        "RealtyRegionMapper#" + method.getName() + " is not stubbed for this test");
            }
        };
        return (RealtyRegionMapper) Proxy.newProxyInstance(
                RealtyRegionMapper.class.getClassLoader(),
                new Class<?>[]{RealtyRegionMapper.class},
                handler);
    }

    private static @NotNull List<RegionStateRow> inWorld(@NotNull List<RegionStateRow> rows,
                                                        @NotNull UUID worldId) {
        List<RegionStateRow> scoped = new ArrayList<>();
        for (RegionStateRow row : rows) {
            if (row.worldId().equals(worldId)) {
                scoped.add(row);
            }
        }
        return scoped;
    }

    private static @NotNull List<RegionStateRow> page(@NotNull List<RegionStateRow> rows,
                                                      int limit,
                                                      int offset) {
        int from = Math.min(offset, rows.size());
        int to = Math.min(from + limit, rows.size());
        return List.copyOf(rows.subList(from, to));
    }

    private static @NotNull List<RealtyRegionEntity> asRegions(@NotNull List<RegionStateRow> rows) {
        List<RealtyRegionEntity> regions = new ArrayList<>();
        for (RegionStateRow row : rows) {
            regions.add(new RealtyRegionEntity(
                    row.realtyRegionId(), row.worldGuardRegionId(), row.worldId()));
        }
        return regions;
    }

    private static @NotNull RestSettings defaultSettings() {
        return new RestSettings("localhost", 0, 100, List.of(), null, null, 1500, 0, null);
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
        return stubSession(worlds, failOnSelectByName, List.of(), List.of(), null, null, null, null, null);
    }

    private static @NotNull SqlSessionWrapper stubSession(@NotNull List<RealtyWorldEntity> worlds,
                                                            boolean failOnSelectByName,
                                                            @NotNull List<String> regionTags) {
        return stubSession(worlds, failOnSelectByName, regionTags, List.of(), null, null, null, null, null);
    }

    private static @NotNull SqlSessionWrapper stubSession(@NotNull List<RealtyWorldEntity> worlds,
                                                            boolean failOnSelectByName,
                                                            @NotNull List<String> regionTags,
                                                            @NotNull List<RentedRegionView> rentedViews,
                                                            @Nullable SearchStub searchStub,
                                                            @Nullable List<RegionStateRow> allRegions,
                                                            @Nullable FreeholdContractMapper freeholdContractMapper,
                                                            @Nullable FreeholdContractAuctionMapper auctionMapper,
                                                            @Nullable ActivityMapper activityMapper) {
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
            if ("realtyRegionMapper".equals(method.getName())) {
                if (allRegions == null) {
                    throw new UnsupportedOperationException(
                            "SqlSessionWrapper#realtyRegionMapper is not stubbed for this test");
                }
                return realtyRegionMapperHandler(allRegions);
            }
            if ("activityMapper".equals(method.getName())) {
                if (activityMapper == null) {
                    throw new UnsupportedOperationException(
                            "SqlSessionWrapper#activityMapper is not stubbed for this test");
                }
                return activityMapper;
            }
            if ("freeholdContractAuctionMapper".equals(method.getName())) {
                if (auctionMapper == null) {
                    throw new UnsupportedOperationException(
                            "SqlSessionWrapper#freeholdContractAuctionMapper is not stubbed for this test");
                }
                return auctionMapper;
            }
            if ("freeholdContractMapper".equals(method.getName())) {
                if (freeholdContractMapper == null) {
                    throw new UnsupportedOperationException(
                            "SqlSessionWrapper#freeholdContractMapper is not stubbed for this test");
                }
                return freeholdContractMapper;
            }
            if ("searchMapper".equals(method.getName())) {
                if (searchStub == null) {
                    throw new UnsupportedOperationException(
                            "SqlSessionWrapper#searchMapper is not stubbed for this test");
                }
                return searchMapperHandler(searchStub);
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
        private final SearchStub searchStub;
        private final List<RegionStateRow> allRegions;
        private final FreeholdContractMapper freeholdContractMapper;
        private final FreeholdContractAuctionMapper auctionMapper;
        private final ActivityMapper activityMapper;

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
            this(failing, worlds, failOnSelectByName, regionTags, rentedViews, null);
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName, @NotNull List<String> regionTags,
                              @NotNull List<RentedRegionView> rentedViews,
                              @Nullable SearchStub searchStub) {
            this(failing, worlds, failOnSelectByName, regionTags, rentedViews, searchStub, null);
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName, @NotNull List<String> regionTags,
                              @NotNull List<RentedRegionView> rentedViews,
                              @Nullable SearchStub searchStub,
                              @Nullable List<RegionStateRow> allRegions) {
            this(failing, worlds, failOnSelectByName, regionTags, rentedViews, searchStub,
                    allRegions, null);
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName, @NotNull List<String> regionTags,
                              @NotNull List<RentedRegionView> rentedViews,
                              @Nullable SearchStub searchStub,
                              @Nullable List<RegionStateRow> allRegions,
                              @Nullable FreeholdContractMapper freeholdContractMapper) {
            this(failing, worlds, failOnSelectByName, regionTags, rentedViews, searchStub,
                    allRegions, freeholdContractMapper, null);
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName, @NotNull List<String> regionTags,
                              @NotNull List<RentedRegionView> rentedViews,
                              @Nullable SearchStub searchStub,
                              @Nullable List<RegionStateRow> allRegions,
                              @Nullable FreeholdContractMapper freeholdContractMapper,
                              @Nullable FreeholdContractAuctionMapper auctionMapper) {
            this(failing, worlds, failOnSelectByName, regionTags, rentedViews, searchStub,
                    allRegions, freeholdContractMapper, auctionMapper, null);
        }

        private StubDatabase(boolean failing, @NotNull List<RealtyWorldEntity> worlds,
                              boolean failOnSelectByName, @NotNull List<String> regionTags,
                              @NotNull List<RentedRegionView> rentedViews,
                              @Nullable SearchStub searchStub,
                              @Nullable List<RegionStateRow> allRegions,
                              @Nullable FreeholdContractMapper freeholdContractMapper,
                              @Nullable FreeholdContractAuctionMapper auctionMapper,
                              @Nullable ActivityMapper activityMapper) {
            this.activityMapper = activityMapper;
            this.auctionMapper = auctionMapper;
            this.freeholdContractMapper = freeholdContractMapper;
            this.allRegions = allRegions;
            this.failing = failing;
            this.worlds = worlds;
            this.failOnSelectByName = failOnSelectByName;
            this.regionTags = regionTags;
            this.rentedViews = rentedViews;
            this.searchStub = searchStub;
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
            return stubSession(this.worlds, this.failOnSelectByName, this.regionTags,
                    this.rentedViews, this.searchStub, this.allRegions, this.freeholdContractMapper,
                    this.auctionMapper, this.activityMapper);
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
