package io.github.md5sha256.realty.rest.module;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Outcome of resolving a player name through the module. Three cases, because the
 * caller must answer differently to each: a resolved player, a name nobody knows
 * (a client error), and a module that could not be asked (a gateway error).
 */
public sealed interface NameLookup {

    record Resolved(@NotNull UUID id, @NotNull String name) implements NameLookup {
    }

    record Unknown() implements NameLookup {
    }

    record Unavailable() implements NameLookup {
    }
}
