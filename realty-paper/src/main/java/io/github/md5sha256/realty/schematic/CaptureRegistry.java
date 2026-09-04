package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks captures currently running, so one region cannot be captured twice at once.
 *
 * <p>The cooldown usually prevents overlap, but {@code --force} bypasses the cooldown,
 * so overlap has to be refused separately rather than assumed away.</p>
 */
public final class CaptureRegistry {

    private final Map<Key, TickSlicedCopy> inFlight = new ConcurrentHashMap<>();

    /**
     * Claims the region for {@code copy}.
     *
     * @return {@code false} if a capture of this region is already running, in which
     *         case the caller must cancel the copy it just started
     */
    public boolean begin(@NotNull String worldGuardRegionId,
                         @NotNull UUID worldId,
                         @NotNull TickSlicedCopy copy) {
        return this.inFlight.putIfAbsent(new Key(worldGuardRegionId, worldId), copy) == null;
    }

    public void finish(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        this.inFlight.remove(new Key(worldGuardRegionId, worldId));
    }

    /**
     * Cancels every running capture. Called from {@code onDisable}: a copy still
     * running at shutdown would hold a task against a disabled plugin and deliver a
     * partial clipboard into a closing database.
     */
    public void cancelAll() {
        this.inFlight.values().forEach(TickSlicedCopy::cancel);
        this.inFlight.clear();
    }

    private record Key(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
    }
}
