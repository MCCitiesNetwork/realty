package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.adapter.query.json.ResourcePackAttribution;
import io.github.md5sha256.realty.adapter.query.json.ResourcePackEntry;
import io.github.md5sha256.realty.adapter.query.json.ResourcePackResponse;
import io.github.md5sha256.realty.api.PlayerNameService;
import org.jetbrains.annotations.NotNull;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TestServers {

    static final String SECRET = "hunter2";

    /** The common case: server.properties leaves resource-pack empty. */
    static @NotNull ResourcePackSource noPack() {
        return () -> new ResourcePackResponse(List.of(), null, false);
    }

    static @NotNull QueryServiceServer withResourcePack(@NotNull String url,
                                                        @NotNull String hash,
                                                        boolean required) {
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), twoRegions(), twoPlayers(),
                () -> new ResourcePackResponse(List.of(new ResourcePackEntry(url, List.of())), hash, required));
    }

    static @NotNull QueryServiceServer withResourcePackAttribution(
            @NotNull String url, @NotNull List<ResourcePackAttribution> attribution) {
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), twoRegions(), twoPlayers(),
                () -> new ResourcePackResponse(List.of(new ResourcePackEntry(url, attribution)), null, false));
    }

    static @NotNull QueryServiceServer withResourcePacks(@NotNull List<ResourcePackEntry> packs) {
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), twoRegions(), twoPlayers(),
                () -> new ResourcePackResponse(packs, null, false));
    }

    static @NotNull QueryServiceServer withoutResourcePack() {
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), twoRegions(), twoPlayers(),
                noPack());
    }
    static final UUID WORLD = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    private TestServers() {
    }

    static @NotNull RegionDimensions plot14() {
        return new RegionDimensions("POLYGONAL", 62, 140, 0, List.of(
                new RegionDimensions.Point(104, -88), new RegionDimensions.Point(131, -88),
                new RegionDimensions.Point(131, -61), new RegionDimensions.Point(104, -61)));
    }

    static @NotNull RegionDimensions annex() {
        return new RegionDimensions("CUBOID", 0, 255, 0, List.of(
                new RegionDimensions.Point(200, 200), new RegionDimensions.Point(210, 200),
                new RegionDimensions.Point(210, 210), new RegionDimensions.Point(200, 210)));
    }

    /**
     * Knows two regions in {@link #WORLD}: {@code downtown_plot_14}, which covers the block
     * (110, 70, -70), and {@code annex}, which shares its footprint at every height but is only
     * entered at y = 300 -- so the column and point tests give different answers there.
     */
    static @NotNull RegionSource twoRegions() {
        return new StubRegionSource(false);
    }

    /** A source whose main thread never ticks: no future ever completes. */
    static @NotNull RegionSource stalledMainThread() {
        return new StubRegionSource(true);
    }

    private record StubRegionSource(boolean stalled) implements RegionSource {

        private static final Map<String, RegionDimensions> REGIONS =
                Map.of("downtown_plot_14", plot14(), "annex", annex());

        private <T> @NotNull CompletableFuture<T> answer(@NotNull T value) {
            return this.stalled ? new CompletableFuture<>() : CompletableFuture.completedFuture(value);
        }

        @Override
        public @NotNull CompletableFuture<Optional<RegionDimensions>> dimensions(@NotNull UUID worldId,
                                                                                 @NotNull String regionId) {
            return answer(worldId.equals(WORLD)
                    ? Optional.ofNullable(REGIONS.get(regionId)) : Optional.empty());
        }

        @Override
        public @NotNull CompletableFuture<Map<String, RegionDimensions>> dimensionsOf(
                @NotNull UUID worldId, @NotNull Collection<String> regionIds) {
            Map<String, RegionDimensions> found = new LinkedHashMap<>();
            if (worldId.equals(WORLD)) {
                for (String regionId : regionIds) {
                    RegionDimensions dims = REGIONS.get(regionId);
                    if (dims != null) {
                        found.put(regionId, dims);
                    }
                }
            }
            return answer(found);
        }

        @Override
        public @NotNull CompletableFuture<Optional<List<String>>> regionsAt(@NotNull UUID worldId,
                                                                            int x,
                                                                            @Nullable Integer y,
                                                                            int z) {
            if (!worldId.equals(WORLD)) {
                return answer(Optional.empty());
            }
            if (x != 110 || z != -70) {
                return answer(Optional.of(List.of()));
            }
            if (y == null) {
                return answer(Optional.of(List.of("downtown_plot_14", "annex")));
            }
            return answer(Optional.of(y == 300 ? List.of("annex") : List.of("downtown_plot_14")));
        }

        @Override
        public @NotNull CompletableFuture<Optional<RegionMembers>> members(@NotNull UUID worldId,
                                                                           @NotNull String regionId) {
            if (!worldId.equals(WORLD) || !REGIONS.containsKey(regionId)) {
                return answer(Optional.empty());
            }
            return answer(Optional.of(new RegionMembers(
                    new RegionMembers.Party(List.of(NOTCH.toString()), List.of(), List.of("staff")),
                    new RegionMembers.Party(List.of(BEDROCK.toString()), List.of("legacyname"), List.of()))));
        }
    }

    static @NotNull PlayerNameService twoPlayers() {
        Map<UUID, String> names = Map.of(NOTCH, "Notch", BEDROCK, ".Cool Guy 123");
        return new PlayerNameService() {
            @Override
            public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
                return CompletableFuture.completedFuture(Optional.ofNullable(names.get(id)));
            }

            @Override
            public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
                return CompletableFuture.completedFuture(names.entrySet().stream()
                        .filter(e -> e.getValue().equals(name))
                        .map(Map.Entry::getKey)
                        .findFirst());
            }
        };
    }

    static @NotNull QueryServiceServer standard() {
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), twoRegions(), twoPlayers(), noPack());
    }

    static @NotNull QueryServiceServer withStalledMainThread(@NotNull Duration timeout) {
        return new QueryServiceServer(SECRET, timeout, stalledMainThread(), twoPlayers(), noPack());
    }

    /** A name service that never answers, standing in for a wedged resolver. */
    static @NotNull PlayerNameService stalledNames() {
        return new PlayerNameService() {
            @Override
            public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
                return new CompletableFuture<>();
            }

            @Override
            public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
                return new CompletableFuture<>();
            }
        };
    }

    static @NotNull QueryServiceServer withStalledNames(@NotNull Duration timeout) {
        return new QueryServiceServer(SECRET, timeout, twoRegions(), stalledNames(), noPack());
    }

    /** Answers every id and name as unknown; enough to exercise batch-size limits. */
    static @NotNull PlayerNameService noPlayers() {
        return new PlayerNameService() {
            @Override
            public @NotNull CompletableFuture<Optional<String>> nameOf(@NotNull UUID id) {
                return CompletableFuture.completedFuture(Optional.empty());
            }

            @Override
            public @NotNull CompletableFuture<Optional<UUID>> uuidOf(@NotNull String name) {
                return CompletableFuture.completedFuture(Optional.empty());
            }
        };
    }

    static @NotNull QueryServiceServer withNoPlayers() {
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), twoRegions(), noPlayers(), noPack());
    }
}
