package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.json.RegionResponse;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

class GeometryCacheTest {

    private static final UUID WORLD = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Duration TERM = Duration.ofMinutes(5);

    private static RegionResponse.Dimensions shapeOf(String regionId) {
        return new RegionResponse.Dimensions("CUBOID", 60, 70, 0,
                List.of(new RegionResponse.Point(regionId.length(), 0)));
    }

    /** Stands in for the game server: answers about the regions it holds, and counts the asking. */
    private static final class FakeModule {

        private final List<String> holds = new ArrayList<>();
        private final List<List<String>> asked = new ArrayList<>();
        private boolean reachable = true;

        Map<String, RegionResponse.Dimensions> dimensionsOf(UUID worldId, Collection<String> ids) {
            this.asked.add(List.copyOf(ids));
            if (!this.reachable) {
                // What an unreachable module looks like from here: not an error, just nothing.
                return Map.of();
            }
            Map<String, RegionResponse.Dimensions> found = new LinkedHashMap<>();
            for (String id : ids) {
                if (this.holds.contains(id)) {
                    found.put(id, shapeOf(id));
                }
            }
            return found;
        }

        int timesAsked() {
            return this.asked.size();
        }

        List<String> lastAsked() {
            return this.asked.get(this.asked.size() - 1);
        }
    }

    /** Runs a reading where the test can see it, or holds it back so a test can watch it queue. */
    private static final class Background {

        private final Deque<Runnable> pending = new ArrayDeque<>();
        private boolean deferred;
        private int started;

        void accept(Runnable task) {
            this.started++;
            if (this.deferred) {
                this.pending.add(task);
            } else {
                task.run();
            }
        }

        void runQueued() {
            while (!this.pending.isEmpty()) {
                this.pending.poll().run();
            }
        }
    }

    private record Fixture(GeometryCache cache, FakeModule module, Background background,
                           AtomicLong clock, List<String> registered) {
    }

    private static Fixture fixture(Duration ttl, String... regionIds) {
        FakeModule module = new FakeModule();
        module.holds.addAll(List.of(regionIds));
        Background background = new Background();
        AtomicLong clock = new AtomicLong(1_000_000_000L);
        List<String> registered = new ArrayList<>(List.of(regionIds));
        GeometryCache cache = new GeometryCache(ttl, world -> List.copyOf(registered),
                module::dimensionsOf, clock::get, background::accept);
        return new Fixture(cache, module, background, clock, registered);
    }

    @Test
    void theSecondVisitorCostsTheGameServerNothing() {
        // The whole point: a hundred people looking at a map is one reading of WorldGuard, not a
        // hundred, because the regions have not moved between the first look and the last.
        Fixture fixture = fixture(TERM, "plot_a", "plot_b", "plot_c");

        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        int afterFirst = fixture.module().timesAsked();
        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        fixture.cache().forRegions(WORLD, List.of("plot_b"));

        Assertions.assertEquals(afterFirst, fixture.module().timesAsked());
    }

    @Test
    void oneReadingCoversThePagesNobodyHasOpenedYet() {
        // A world is read whole rather than a page at a time, so paging through a map costs the
        // game server the first page and nothing after it.
        Fixture fixture = fixture(TERM, "plot_a", "plot_b", "plot_c");

        fixture.cache().forRegions(WORLD, List.of("plot_a"));

        Assertions.assertEquals(List.of("plot_a", "plot_b", "plot_c"), fixture.module().lastAsked());
        Map<String, RegionResponse.Dimensions> later =
                fixture.cache().forRegions(WORLD, List.of("plot_c"));
        Assertions.assertEquals(shapeOf("plot_c"), later.get("plot_c"));
    }

    @Test
    void aCrowdArrivingTogetherIsOneReading() {
        Fixture fixture = fixture(TERM, "plot_a", "plot_b");
        fixture.background().deferred = true;

        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        fixture.cache().forRegions(WORLD, List.of("plot_b"));

        Assertions.assertEquals(1, fixture.background().started);
    }

    @Test
    void nobodyWaitsForTheReading() {
        // The reading runs behind the request. Until it lands the page is answered by asking
        // about its own ids, which is one small reading rather than the world.
        Fixture fixture = fixture(TERM, "plot_a", "plot_b", "plot_c");
        fixture.background().deferred = true;

        Map<String, RegionResponse.Dimensions> answer =
                fixture.cache().forRegions(WORLD, List.of("plot_a"));

        Assertions.assertEquals(shapeOf("plot_a"), answer.get("plot_a"));
        Assertions.assertEquals(List.of("plot_a"), fixture.module().lastAsked());
    }

    @Test
    void aRegionRegisteredSinceTheReadingIsAskedAboutOnItsOwn() {
        // A plot registered a minute ago should be drawn now, not when the term runs out -- and
        // asking about it should not mean reading the world again.
        Fixture fixture = fixture(TERM, "plot_a");
        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        int afterReading = fixture.module().timesAsked();
        fixture.module().holds.add("plot_new");
        fixture.registered().add("plot_new");

        Map<String, RegionResponse.Dimensions> answer =
                fixture.cache().forRegions(WORLD, List.of("plot_a", "plot_new"));

        Assertions.assertEquals(shapeOf("plot_new"), answer.get("plot_new"));
        Assertions.assertEquals(afterReading + 1, fixture.module().timesAsked());
        Assertions.assertEquals(List.of("plot_new"), fixture.module().lastAsked());
        // And having been asked about once, it is not asked about again.
        fixture.cache().forRegions(WORLD, List.of("plot_a", "plot_new"));
        Assertions.assertEquals(afterReading + 1, fixture.module().timesAsked());
    }

    @Test
    void aRegionWorldGuardCannotPlaceIsReportedAbsentRatherThanAskedAboutForever() {
        // The register holds regions WorldGuard no longer does. They have no shape to find, so
        // asking again on every request would be a reading that can only ever come back empty.
        Fixture fixture = fixture(TERM, "plot_a");
        fixture.registered().add("plot_gone");

        Map<String, RegionResponse.Dimensions> answer =
                fixture.cache().forRegions(WORLD, List.of("plot_a", "plot_gone"));
        int afterReading = fixture.module().timesAsked();
        fixture.cache().forRegions(WORLD, List.of("plot_a", "plot_gone"));

        Assertions.assertNull(answer.get("plot_gone"));
        Assertions.assertEquals(afterReading, fixture.module().timesAsked());
    }

    @Test
    void anUnreachableGameServerIsNotRememberedAsAnAnswer() {
        // Finding nothing because the server is down is not news about where the regions are.
        // Keeping it would leave the map blank for the whole term after a restart.
        Fixture fixture = fixture(TERM, "plot_a");
        fixture.module().reachable = false;

        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        fixture.module().reachable = true;
        Map<String, RegionResponse.Dimensions> answer =
                fixture.cache().forRegions(WORLD, List.of("plot_a"));

        Assertions.assertEquals(shapeOf("plot_a"), answer.get("plot_a"));
    }

    @Test
    void aReadingIsTakenAgainOnceItHasAgedOut() {
        Fixture fixture = fixture(TERM, "plot_a");
        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        int afterReading = fixture.module().timesAsked();

        fixture.clock().addAndGet(TERM.toNanos() - 1);
        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        Assertions.assertEquals(afterReading, fixture.module().timesAsked());

        fixture.clock().addAndGet(2);
        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        Assertions.assertTrue(fixture.module().timesAsked() > afterReading);
    }

    @Test
    void anAgedReadingIsStillServedWhileItsReplacementIsBeingTaken() {
        // Nobody should be made to wait at the moment a term happens to run out.
        Fixture fixture = fixture(TERM, "plot_a");
        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        fixture.background().deferred = true;
        fixture.clock().addAndGet(TERM.toNanos() + 1);

        Map<String, RegionResponse.Dimensions> answer =
                fixture.cache().forRegions(WORLD, List.of("plot_a"));

        Assertions.assertEquals(shapeOf("plot_a"), answer.get("plot_a"));
        fixture.background().runQueued();
        Assertions.assertEquals(shapeOf("plot_a"),
                fixture.cache().forRegions(WORLD, List.of("plot_a")).get("plot_a"));
    }

    @Test
    void aTermOfZeroReadsTheGameServerEveryTime() {
        // The way an operator turns this off, and what the endpoint's own tests run with.
        Fixture fixture = fixture(Duration.ZERO, "plot_a");

        fixture.cache().forRegions(WORLD, List.of("plot_a"));
        fixture.cache().forRegions(WORLD, List.of("plot_a"));

        Assertions.assertEquals(2, fixture.module().timesAsked());
        Assertions.assertEquals(0, fixture.background().started);
    }

    @Test
    void askingAboutNothingAsksTheGameServerNothing() {
        Fixture fixture = fixture(TERM, "plot_a");

        Assertions.assertEquals(Map.of(), fixture.cache().forRegions(WORLD, List.of()));
        Assertions.assertEquals(0, fixture.module().timesAsked());
    }
}
