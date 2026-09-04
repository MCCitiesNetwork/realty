package io.github.md5sha256.realty.schematic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Per-region capture rate limiting, held in memory only: it is a courtesy against
 * repeated expensive captures, not an audited limit, so it resets on restart rather
 * than costing a table and a write per attempt.
 *
 * <p>The clock is injected so the cooldown can be tested without sleeping.</p>
 */
public final class CaptureCooldown {

    private final Map<Key, Instant> lastCapture = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    public CaptureCooldown(@NotNull Supplier<Instant> clock) {
        this.clock = clock;
    }

    /**
     * How much longer the region must wait, or {@code null} if it may be captured now.
     *
     * @param cooldown the configured cooldown; zero or negative disables the check,
     *                 which is how an operator turns the cooldown off
     */
    public @Nullable Duration remaining(@NotNull String worldGuardRegionId,
                                        @NotNull UUID worldId,
                                        @NotNull Duration cooldown) {
        if (cooldown.isZero() || cooldown.isNegative()) {
            return null;
        }
        Instant last = this.lastCapture.get(new Key(worldGuardRegionId, worldId));
        if (last == null) {
            return null;
        }
        Duration elapsed = Duration.between(last, this.clock.get());
        if (elapsed.compareTo(cooldown) >= 0) {
            return null;
        }
        return cooldown.minus(elapsed);
    }

    public void record(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
        this.lastCapture.put(new Key(worldGuardRegionId, worldId), this.clock.get());
    }

    private record Key(@NotNull String worldGuardRegionId, @NotNull UUID worldId) {
    }
}
