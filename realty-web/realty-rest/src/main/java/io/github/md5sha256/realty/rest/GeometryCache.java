package io.github.md5sha256.realty.rest;

import io.github.md5sha256.realty.rest.json.RegionResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Region footprints, remembered so that drawing a map costs the game server one reading of
 * WorldGuard rather than one per page per visitor.
 *
 * <p>A footprint can only be read on the server's main thread, because WorldGuard's regions are
 * not safe to touch from anywhere else. Each region is cheap to measure, but the readings add
 * up: uncached, one visitor opening a map of a built-up world costs the main thread a reading
 * per page of it, and a hundred visitors cost a hundred times that -- for answers that are, in
 * almost every case, identical to the one before. Regions are redrawn far more rarely than maps
 * are looked at.</p>
 *
 * <p>So a world is read whole, in the background, and kept for {@code ttl}. Requests are never
 * made to wait for that reading:</p>
 *
 * <ul>
 *   <li>What has been read is answered from memory, costing the game server nothing.</li>
 *   <li>Ids the last whole reading did not cover -- everything, for the first seconds after a
 *       restart, and a plot registered since, after that -- are asked about on their own. That
 *       is one small reading rather than a page's worth, and it stops as soon as the whole
 *       reading lands.</li>
 *   <li>A reading that has aged out is replaced by a new one taken in the background while the
 *       old one is still being served, so nobody waits at the moment it expires.</li>
 * </ul>
 *
 * <p>Only footprints are kept. Which regions are registered is read from the database on every
 * request, as it always was, so this can never hide a region -- at worst a very new one is
 * drawn without its shape until the reading that covers it lands.</p>
 */
public final class GeometryCache {

    private static final Logger LOGGER = Logger.getLogger(GeometryCache.class.getName());

    /**
     * How many ids one call to the module carries.
     *
     * <p>Under the module's own batch limit, which it truncates to rather than refusing -- a
     * truncated batch would leave the regions past the cut looking as though WorldGuard had
     * never heard of them. The module spreads a batch of this size across ticks itself.</p>
     */
    private static final int BATCH = 200;

    /** Rows read from the database at a time when listing a world's registered regions. */
    static final int ID_PAGE = 1000;

    /**
     * How long a reading that found nothing is kept before another is taken.
     *
     * <p>Much shorter than the configured term, because the ordinary reason to find nothing is a
     * game server that is not up yet. Keeping that answer for the full term would leave every
     * map blank for the term after a restart, which is the one moment an operator is most
     * likely to be looking at one.</p>
     */
    private static final Duration NOTHING_FOUND_TTL = Duration.ofSeconds(15);

    private final Duration ttl;
    private final LongSupplier nanoTime;
    private final Function<UUID, List<String>> registeredRegionIds;
    private final BiFunction<UUID, Collection<String>, Map<String, RegionResponse.Dimensions>> readDimensions;
    private final Consumer<Runnable> inBackground;
    private final ConcurrentMap<UUID, Reading> readings = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Boolean> beingRead = new ConcurrentHashMap<>();

    /**
     * @param ttl                 how long a whole reading is kept; {@link Duration#ZERO} disables
     *                            the cache and reads the game server on every request
     * @param registeredRegionIds every region id the database holds for a world
     * @param readDimensions      the module call, which yields no entry for an id it cannot place
     */
    public GeometryCache(@NotNull Duration ttl,
                         @NotNull Function<UUID, List<String>> registeredRegionIds,
                         @NotNull BiFunction<UUID, Collection<String>, Map<String, RegionResponse.Dimensions>> readDimensions) {
        this(ttl, registeredRegionIds, readDimensions, System::nanoTime,
                // A virtual thread rather than a pool: a reading is one blocking errand per world
                // per term, and giving it a carrier of its own keeps it away from whatever else
                // the service is doing.
                task -> Thread.ofVirtual().name("realty-geometry-reading").start(task));
    }

    /**
     * @param nanoTime     a clock, injected so a test need not wait out a term
     * @param inBackground runs a whole reading; a test passes {@link Runnable#run} to make the
     *                     reading happen where it can be observed
     */
    GeometryCache(@NotNull Duration ttl,
                  @NotNull Function<UUID, List<String>> registeredRegionIds,
                  @NotNull BiFunction<UUID, Collection<String>, Map<String, RegionResponse.Dimensions>> readDimensions,
                  @NotNull LongSupplier nanoTime,
                  @NotNull Consumer<Runnable> inBackground) {
        this.ttl = ttl;
        this.registeredRegionIds = registeredRegionIds;
        this.readDimensions = readDimensions;
        this.nanoTime = nanoTime;
        this.inBackground = inBackground;
    }

    /**
     * Footprints for the given regions, keyed by region id.
     *
     * <p>An id the module cannot place is absent from the result, exactly as the module's own
     * answer leaves it absent -- the caller renders that as a null shape either way.</p>
     */
    public @NotNull Map<String, RegionResponse.Dimensions> forRegions(@NotNull UUID worldId,
                                                                      @NotNull List<String> regionIds) {
        if (regionIds.isEmpty()) {
            return Map.of();
        }
        if (this.ttl.isZero()) {
            return readInBatches(worldId, regionIds);
        }

        Reading reading = this.readings.computeIfAbsent(worldId, id -> Reading.unread());
        takeNewReadingIfDue(worldId, reading);
        // A reading taken just now has replaced the one found above, and answering from the
        // older one would ask the game server again for what it has this moment reported.
        return answerFrom(worldId, this.readings.getOrDefault(worldId, reading), regionIds);
    }

    /**
     * The answer for one page, asking the game server only about ids no reading has covered.
     *
     * <p>Those ids are recorded as covered only when the module actually answered. An empty
     * answer means the module could not be reached at least as often as it means the regions
     * are not there, and remembering an outage as fact would blank the map until the term ran
     * out.</p>
     */
    private @NotNull Map<String, RegionResponse.Dimensions> answerFrom(@NotNull UUID worldId,
                                                                       @NotNull Reading reading,
                                                                       @NotNull List<String> regionIds) {
        List<String> uncovered = new ArrayList<>();
        for (String regionId : regionIds) {
            if (!reading.covers.contains(regionId)) {
                uncovered.add(regionId);
            }
        }
        if (!uncovered.isEmpty()) {
            Map<String, RegionResponse.Dimensions> late = readInBatches(worldId, uncovered);
            if (!late.isEmpty()) {
                reading.found.putAll(late);
                reading.covers.addAll(uncovered);
            }
        }

        Map<String, RegionResponse.Dimensions> answer = new LinkedHashMap<>();
        for (String regionId : regionIds) {
            RegionResponse.Dimensions dimensions = reading.found.get(regionId);
            if (dimensions != null) {
                answer.put(regionId, dimensions);
            }
        }
        return answer;
    }

    /**
     * Starts a whole reading of the world if the one in hand has aged out, or if there has never
     * been one. At most one reading of a world runs at a time, so a crowd arriving together is
     * one reading rather than one each.
     */
    private void takeNewReadingIfDue(@NotNull UUID worldId, @NotNull Reading reading) {
        if (!isDue(reading)) {
            return;
        }
        if (this.beingRead.putIfAbsent(worldId, Boolean.TRUE) != null) {
            return;
        }
        this.inBackground.accept(() -> {
            try {
                takeReading(worldId);
            } catch (RuntimeException ex) {
                // Nothing is waiting on this, so a failure that went unlogged would be a map
                // that quietly stopped refreshing.
                LOGGER.warning("Could not read region footprints for world " + worldId + ": " + ex);
            } finally {
                this.beingRead.remove(worldId);
            }
        });
    }

    private boolean isDue(@NotNull Reading reading) {
        Long readAt = reading.readAtNanos;
        if (readAt == null) {
            return true;
        }
        Duration term = reading.found.isEmpty() ? NOTHING_FOUND_TTL : this.ttl;
        return this.nanoTime.getAsLong() - readAt >= term.toNanos();
    }

    /**
     * Reads a world in full and puts the result in place of what was there.
     *
     * <p>A reading that found nothing for a world that has regions is dropped rather than
     * installed: the game server being unreachable is not news about where the regions are, and
     * the reading already in place is still the best answer available.</p>
     */
    private void takeReading(@NotNull UUID worldId) {
        List<String> ids = this.registeredRegionIds.apply(worldId);
        Map<String, RegionResponse.Dimensions> found = readInBatches(worldId, ids);
        if (found.isEmpty() && !ids.isEmpty()) {
            return;
        }
        this.readings.put(worldId, Reading.of(found, ids, this.nanoTime.getAsLong()));
    }

    private @NotNull Map<String, RegionResponse.Dimensions> readInBatches(@NotNull UUID worldId,
                                                                          @NotNull List<String> ids) {
        Map<String, RegionResponse.Dimensions> found = new LinkedHashMap<>();
        for (int from = 0; from < ids.size(); from += BATCH) {
            List<String> batch = ids.subList(from, Math.min(from + BATCH, ids.size()));
            found.putAll(this.readDimensions.apply(worldId, batch));
        }
        return found;
    }

    /** One world's footprints as of one moment, and which ids that moment covered. */
    private static final class Reading {

        private final Map<String, RegionResponse.Dimensions> found;
        private final Set<String> covers;
        /** When the whole reading landed, or null for a world nothing has been read of yet. */
        private final @Nullable Long readAtNanos;

        private Reading(@Nullable Long readAtNanos) {
            this.found = new ConcurrentHashMap<>();
            this.covers = ConcurrentHashMap.newKeySet();
            this.readAtNanos = readAtNanos;
        }

        static @NotNull Reading unread() {
            return new Reading(null);
        }

        static @NotNull Reading of(@NotNull Map<String, RegionResponse.Dimensions> found,
                                   @NotNull List<String> covered,
                                   long readAtNanos) {
            Reading reading = new Reading(readAtNanos);
            reading.found.putAll(found);
            reading.covers.addAll(covered);
            return reading;
        }
    }
}
