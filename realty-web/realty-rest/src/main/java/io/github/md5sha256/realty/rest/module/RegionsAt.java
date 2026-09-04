package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The module's answer to a block lookup: which test it ran, and the WorldGuard region ids it
 * matched. The ids are still unfiltered here -- WorldGuard knows regions Realty does not.
 *
 * @param test       {@code "point"} or {@code "column"}
 * @param regionIds  matching WorldGuard region ids, in the world's own region order
 */
public record RegionsAt(@NotNull String test, @NotNull List<String> regionIds) {
}
