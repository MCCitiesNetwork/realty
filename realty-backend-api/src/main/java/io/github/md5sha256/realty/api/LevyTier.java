package io.github.md5sha256.realty.api;

/**
 * A step in a levy rule.
 *
 * @param fromPlots the smallest plot count this tier applies to.
 * @param percent   the percentage charged at that count.
 */
public record LevyTier(int fromPlots, double percent) {}
