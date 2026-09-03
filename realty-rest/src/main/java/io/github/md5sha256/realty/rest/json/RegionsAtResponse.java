package io.github.md5sha256.realty.rest.json;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Which registered regions cover a block.
 *
 * <p>{@code test} names the question that was answered: {@code point} when the caller sent a
 * {@code y}, {@code column} when it did not. The two are different queries -- a column test
 * matches every region over the footprint at any height, which is what a 2-D map click means --
 * so a consumer is told which it got rather than having to remember what it sent.</p>
 *
 * <p>The list is regions Realty has registered. WorldGuard knows others; they are not this
 * API's to report.</p>
 */
public record RegionsAtResponse(@NotNull String test, @NotNull List<Entry> regions) {

    public record Entry(@NotNull String worldGuardRegionId, @NotNull WorldRef world) {
    }
}
