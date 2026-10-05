package io.github.md5sha256.realty.api;

import org.jetbrains.annotations.NotNull;

/**
 * The kind of a {@link Party}, exposed separately from {@link AccountKind} because a
 * {@code Party} can be a player rather than a Treasury account, or a named group backed
 * by one.
 */
public enum PartyKind {
    PERSONAL,
    BUSINESS,
    GOVERNMENT,
    SYSTEM,
    GROUP;

    /** The kind of a party backed by a Treasury account of {@code kind}. */
    public static @NotNull PartyKind of(@NotNull AccountKind kind) {
        return switch (kind) {
            case BUSINESS -> BUSINESS;
            case GOVERNMENT -> GOVERNMENT;
            case SYSTEM -> SYSTEM;
        };
    }
}
