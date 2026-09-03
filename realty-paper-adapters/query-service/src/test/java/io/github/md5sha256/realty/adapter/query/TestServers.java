package io.github.md5sha256.realty.adapter.query;

import io.github.md5sha256.realty.api.PlayerNameService;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class TestServers {

    static final String SECRET = "hunter2";
    static final UUID WORLD = UUID.fromString("8f4d0000-0000-0000-0000-000000000001");
    static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");
    static final UUID BEDROCK = UUID.fromString("00000000-0000-0000-0009-01f64f65c7e1");

    private TestServers() {
    }

    static @NotNull RegionDimensions plot14() {
        return new RegionDimensions("POLYGONAL", 62, 140, List.of(
                new RegionDimensions.Point(104, -88), new RegionDimensions.Point(131, -88),
                new RegionDimensions.Point(131, -61), new RegionDimensions.Point(104, -61)));
    }

    /** Knows exactly one region, {@code downtown_plot_14} in {@link #WORLD}. */
    static @NotNull RegionDimensionsSource oneRegion() {
        return (worldId, regionId) -> CompletableFuture.completedFuture(
                worldId.equals(WORLD) && regionId.equals("downtown_plot_14")
                        ? Optional.of(plot14()) : Optional.empty());
    }

    /** A source whose main thread never ticks: the future never completes. */
    static @NotNull RegionDimensionsSource stalledMainThread() {
        return (worldId, regionId) -> new CompletableFuture<>();
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
        return new QueryServiceServer(SECRET, Duration.ofSeconds(5), oneRegion(), twoPlayers());
    }

    static @NotNull QueryServiceServer withStalledMainThread(@NotNull Duration timeout) {
        return new QueryServiceServer(SECRET, timeout, stalledMainThread(), twoPlayers());
    }
}
