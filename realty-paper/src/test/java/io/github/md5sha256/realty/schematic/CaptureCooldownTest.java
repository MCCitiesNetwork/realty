package io.github.md5sha256.realty.schematic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

class CaptureCooldownTest {

    private static final UUID WORLD_ID = UUID.fromString("8f4d1c2e-0000-0000-0000-0000000000ff");
    private static final Duration COOLDOWN = Duration.ofMinutes(10);

    @Test
    void aRegionNeverCapturedIsAllowedImmediately() {
        CaptureCooldown cooldown = new CaptureCooldown(Instant::now);
        Assertions.assertNull(cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }

    @Test
    void aRecentCaptureReportsTheTimeRemaining() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);
        now.set(Instant.parse("2026-09-04T12:04:00Z"));

        Assertions.assertEquals(Duration.ofMinutes(6), cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }

    @Test
    void theCooldownExpiresExactlyAtItsDuration() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);
        now.set(Instant.parse("2026-09-04T12:10:00Z"));

        Assertions.assertNull(cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }

    @Test
    void eachRegionCoolsDownIndependently() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertNotNull(cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
        Assertions.assertNull(cooldown.remaining("plot_b", WORLD_ID, COOLDOWN));
    }

    @Test
    void theSameRegionNameInAnotherWorldCoolsDownIndependently() {
        UUID otherWorld = UUID.fromString("8f4d1c2e-0000-0000-0000-000000000011");
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertNull(cooldown.remaining("plot_a", otherWorld, COOLDOWN));
    }

    @Test
    void aZeroCooldownAlwaysAllowsCapture() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertNull(cooldown.remaining("plot_a", WORLD_ID, Duration.ZERO));
    }

    @Test
    void recordingAgainRestartsTheCooldown() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-09-04T12:00:00Z"));
        CaptureCooldown cooldown = new CaptureCooldown(now::get);

        cooldown.record("plot_a", WORLD_ID);
        now.set(Instant.parse("2026-09-04T12:09:00Z"));
        cooldown.record("plot_a", WORLD_ID);

        Assertions.assertEquals(Duration.ofMinutes(10), cooldown.remaining("plot_a", WORLD_ID, COOLDOWN));
    }
}
