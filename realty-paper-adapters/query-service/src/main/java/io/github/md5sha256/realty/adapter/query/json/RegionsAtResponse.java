package io.github.md5sha256.realty.adapter.query.json;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Which regions cover a block, and which test answered the question.
 *
 * <p>A record rather than a map so the field order is fixed: the test name reads first, which is
 * what a consumer has to know before it can interpret the list.</p>
 *
 * @param test    {@code "point"} when a {@code y} was given, {@code "column"} when it was not
 * @param regions matching WorldGuard region ids, in the world's own region order
 */
public record RegionsAtResponse(@NotNull String test, @NotNull List<String> regions) {
}
